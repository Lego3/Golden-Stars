package com.edvinlinge.hemma.mathstars2

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class MandelbrotTileCacheTest {

    @Test
    fun `memory get returns the pixels that were put`() {
        val cache = MandelbrotTileCache(maxMemoryBytes = 1024 * 1024, diskDir = null, maxDiskBytes = 0)
        val key = tileKey(0)
        val pixels = byteArrayOf(1, 2, 3, 4)
        cache.put(key, pixels)
        assertArrayEquals(pixels, cache.get(key)?.pixels)
        assertTrue(cache.contains(key))
    }

    @Test
    fun `least recently used tiles are evicted when the byte budget is exceeded`() {
        val cache = MandelbrotTileCache(
            maxMemoryBytes = 10,
            diskDir = null,
            maxDiskBytes = 0,
        )
        val evicted = mutableListOf<MandelbrotTiles.TileKey>()
        cache.onEvicted = { evicted += it }

        val first = tileKey(1)
        val second = tileKey(2)
        val third = tileKey(3)
        cache.put(first, byteArrayOf(1, 2, 3, 4))
        cache.put(second, byteArrayOf(5, 6, 7, 8))
        cache.get(first)
        cache.put(third, byteArrayOf(9, 10, 11, 12))

        assertNull(cache.get(second))
        assertNotNull(cache.get(first))
        assertNotNull(cache.get(third))
        assertEquals(listOf(second), evicted)
        assertEquals(8, cache.memoryBytes)
    }

    @Test
    fun `protected keys are not evicted even when over the byte budget`() {
        val cache = MandelbrotTileCache(maxMemoryBytes = 10, diskDir = null, maxDiskBytes = 0)
        val first = tileKey(1)
        val second = tileKey(2)
        val third = tileKey(3)
        cache.put(first, byteArrayOf(1, 2, 3, 4))
        cache.put(second, byteArrayOf(5, 6, 7, 8), protectedKeys = setOf(first, second))
        cache.put(
            third,
            byteArrayOf(9, 10, 11, 12),
            protectedKeys = setOf(first, second, third),
        )
        assertNotNull(cache.get(first))
        assertNotNull(cache.get(second))
        assertNotNull(cache.get(third))
        assertEquals(3, cache.memorySize)
        assertTrue(cache.memoryBytes > 10)
    }

    @Test
    fun `preview and full tiles are stored as distinct entries`() {
        val cache = MandelbrotTileCache(maxMemoryBytes = 1024, diskDir = null, maxDiskBytes = 0)
        val full = tileKey(0, preview = false)
        val preview = tileKey(0, preview = true)
        cache.put(full, byteArrayOf(1, 1))
        cache.put(preview, byteArrayOf(2, 2))
        assertEquals(1, cache.get(full)?.pixels?.first()?.toInt())
        assertEquals(2, cache.get(preview)?.pixels?.first()?.toInt())
        assertEquals(2, cache.memorySize)
    }

    @Test
    fun `disk round trip restores pixels and skips previews`() {
        val dir = Files.createTempDirectory("mandelbrot-tiles").toFile()
        try {
            val cache = MandelbrotTileCache(
                maxMemoryBytes = 1024 * 1024,
                diskDir = dir,
                maxDiskBytes = 1024 * 1024,
            )
            val key = tileKey(7)
            val pixels = ByteArray(16) { (it * 3).toByte() }
            cache.saveToDisk(key, pixels)
            val names = dir.listFiles { file -> file.extension == "tile" }!!.map { it.name }
            assertTrue(names.single().startsWith("e"))
            assertTrue(names.none { it.startsWith("p") })
            cache.clearMemory()
            assertNull(cache.get(key))

            val loaded = cache.loadFromDisk(key)
            assertNotNull(loaded)
            assertArrayEquals(pixels, loaded!!.pixels)

            val preview = tileKey(7, preview = true)
            cache.saveToDisk(preview, byteArrayOf(9, 9, 9, 9))
            assertNull(cache.loadFromDisk(preview))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `disk prune deletes files when over the size cap`() {
        val dir = Files.createTempDirectory("mandelbrot-tiles-prune").toFile()
        try {
            val cache = MandelbrotTileCache(
                maxMemoryBytes = 1024 * 1024,
                diskDir = dir,
                maxDiskBytes = 1,
            )
            cache.saveToDisk(tileKey(1), ByteArray(64) { (it * 17).toByte() })
            cache.saveToDisk(tileKey(2), ByteArray(64) { (it * 19).toByte() })
            val remaining = dir.listFiles { file -> file.extension == "tile" }?.toList().orEmpty()
            assertTrue(remaining.size <= 1)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `disk prune deletes the least recently used file not the one written first`() {
        val dir = Files.createTempDirectory("mandelbrot-tiles-lru").toFile()
        try {
            val pixels = ByteArray(256) { (it * 41 + 7).toByte() }
            var now = 1_000L
            MandelbrotTileCache(
                maxMemoryBytes = 1024 * 1024,
                diskDir = dir,
                maxDiskBytes = Long.MAX_VALUE,
                currentTimeMs = { now },
            ).saveToDisk(tileKey(0), pixels)
            val size = dir.listFiles { file -> file.extension == "tile" }!!.single().length()
            dir.listFiles()!!.forEach { it.delete() }

            val cache = MandelbrotTileCache(
                maxMemoryBytes = 1024 * 1024,
                diskDir = dir,
                maxDiskBytes = size * 2 + size / 2,
                currentTimeMs = { now },
            )
            val firstWritten = tileKey(1)
            val unusedLater = tileKey(2)
            val newest = tileKey(3)
            cache.saveToDisk(firstWritten, pixels)
            now = 2_000L
            cache.saveToDisk(unusedLater, pixels)
            now = 3_000L
            cache.put(firstWritten, pixels)
            cache.get(firstWritten)
            now = 4_000L
            cache.saveToDisk(newest, pixels)

            assertNotNull(cache.loadFromDisk(firstWritten))
            assertNull(cache.loadFromDisk(unusedLater))
            assertNotNull(cache.loadFromDisk(newest))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `concurrent disk saves keep access metadata consistent`() {
        val dir = Files.createTempDirectory("mandelbrot-tiles-concurrent").toFile()
        try {
            val cache = MandelbrotTileCache(
                maxMemoryBytes = 1024 * 1024,
                diskDir = dir,
                maxDiskBytes = 1024 * 1024,
            )
            val pixels = ByteArray(64) { (it * 1_000_003).toByte() }
            val threads = (0 until 32).map { id ->
                Thread {
                    cache.saveToDisk(tileKey(id), pixels)
                    cache.loadFromDisk(tileKey(id))
                }
            }
            threads.forEach { it.start() }
            threads.forEach { it.join() }
            val files = dir.listFiles { file -> file.extension == "tile" }.orEmpty()
            assertTrue(files.isNotEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun tileKey(id: Int, preview: Boolean = false) = MandelbrotTiles.TileKey(
        zoomStep = 0,
        tileX = id.toLong(),
        tileY = 0,
        tilePixelSize = 256,
        viewMinEdge = 600,
        preview = preview,
    )
}
