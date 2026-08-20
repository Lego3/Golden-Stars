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
        val pixels = intArrayOf(1, 2, 3, 4)
        cache.put(key, pixels)
        assertArrayEquals(pixels, cache.get(key)?.pixels)
        assertTrue(cache.contains(key))
    }

    @Test
    fun `least recently used tiles are evicted when the byte budget is exceeded`() {
        val cache = MandelbrotTileCache(
            maxMemoryBytes = 40,
            diskDir = null,
            maxDiskBytes = 0,
        )
        val evicted = mutableListOf<MandelbrotTiles.TileKey>()
        cache.onEvicted = { evicted += it }

        val first = tileKey(1)
        val second = tileKey(2)
        val third = tileKey(3)
        cache.put(first, intArrayOf(1, 2, 3, 4))
        cache.put(second, intArrayOf(5, 6, 7, 8))
        cache.get(first)
        cache.put(third, intArrayOf(9, 10, 11, 12))

        assertNull(cache.get(second))
        assertNotNull(cache.get(first))
        assertNotNull(cache.get(third))
        assertEquals(listOf(second), evicted)
        assertEquals(32, cache.memoryBytes)
    }

    @Test
    fun `protected keys are not evicted even when over the byte budget`() {
        val cache = MandelbrotTileCache(maxMemoryBytes = 40, diskDir = null, maxDiskBytes = 0)
        val first = tileKey(1)
        val second = tileKey(2)
        val third = tileKey(3)
        cache.put(first, intArrayOf(1, 2, 3, 4))
        cache.put(second, intArrayOf(5, 6, 7, 8), protectedKeys = setOf(first, second))
        cache.put(
            third,
            intArrayOf(9, 10, 11, 12),
            protectedKeys = setOf(first, second, third),
        )
        assertNotNull(cache.get(first))
        assertNotNull(cache.get(second))
        assertNotNull(cache.get(third))
        assertEquals(3, cache.memorySize)
        assertTrue(cache.memoryBytes > 40)
    }

    @Test
    fun `preview and full tiles are stored as distinct entries`() {
        val cache = MandelbrotTileCache(maxMemoryBytes = 1024, diskDir = null, maxDiskBytes = 0)
        val full = tileKey(0, preview = false)
        val preview = tileKey(0, preview = true)
        cache.put(full, intArrayOf(1, 1))
        cache.put(preview, intArrayOf(2, 2))
        assertEquals(1, cache.get(full)?.pixels?.first())
        assertEquals(2, cache.get(preview)?.pixels?.first())
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
            val pixels = IntArray(16) { it * 3 }
            cache.saveToDisk(key, pixels)
            cache.clearMemory()
            assertNull(cache.get(key))

            val loaded = cache.loadFromDisk(key)
            assertNotNull(loaded)
            assertArrayEquals(pixels, loaded!!.pixels)

            val preview = tileKey(7, preview = true)
            cache.saveToDisk(preview, intArrayOf(9, 9, 9, 9))
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
            cache.saveToDisk(tileKey(1), IntArray(64) { it * 1_000_003 })
            cache.saveToDisk(tileKey(2), IntArray(64) { it * 1_000_033 })
            val remaining = dir.listFiles { file -> file.extension == "tile" }?.toList().orEmpty()
            assertTrue(remaining.size <= 1)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun tileKey(id: Int, preview: Boolean = false) = MandelbrotTiles.TileKey(
        zoomStep = 0,
        tileX = id.toLong(),
        tileY = 0,
        paletteOrdinal = 0,
        tilePixelSize = 256,
        viewMinEdge = 600,
        preview = preview,
    )
}
