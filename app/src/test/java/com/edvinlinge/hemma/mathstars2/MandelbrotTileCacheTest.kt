package com.edvinlinge.hemma.mathstars2

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

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
    fun `corrupt disk tile is discarded instead of crashing the cache`() {
        val dir = Files.createTempDirectory("mandelbrot-tiles-corrupt").toFile()
        try {
            val cache = MandelbrotTileCache(
                maxMemoryBytes = 1024 * 1024,
                diskDir = dir,
                maxDiskBytes = 1024 * 1024,
            )
            val key = tileKey(42)
            val corrupt = File(dir, "e600_s0_x42_y0_t256.tile")
            dir.mkdirs()
            corrupt.writeBytes(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 1))

            assertNull(cache.loadFromDisk(key))
            assertFalse(corrupt.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `stale MBT1 disk tile is discarded after the greyscale format change`() {
        val dir = Files.createTempDirectory("mandelbrot-tiles-mbt1").toFile()
        try {
            val cache = MandelbrotTileCache(
                maxMemoryBytes = 1024 * 1024,
                diskDir = dir,
                maxDiskBytes = 1024 * 1024,
            )
            val key = tileKey(42)
            val stale = File(dir, "e600_s0_x42_y0_t256.tile")
            writeCompressedTile(stale, magic = 0x4D425431, pixelCount = 4, pixels = byteArrayOf(1, 2, 3, 4))

            assertNull(cache.loadFromDisk(key))
            assertFalse(stale.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `disk tile with truncated pixel payload is discarded`() {
        val dir = Files.createTempDirectory("mandelbrot-tiles-truncated").toFile()
        try {
            val cache = MandelbrotTileCache(
                maxMemoryBytes = 1024 * 1024,
                diskDir = dir,
                maxDiskBytes = 1024 * 1024,
            )
            val key = tileKey(42)
            val truncated = File(dir, "e600_s0_x42_y0_t256.tile")
            writeCompressedTile(
                truncated,
                magic = 0x4D425432,
                pixelCount = 16,
                pixels = byteArrayOf(1, 2, 3, 4),
            )

            assertNull(cache.loadFromDisk(key))
            assertFalse(truncated.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `disk tile with an impossible pixel count is discarded`() {
        val dir = Files.createTempDirectory("mandelbrot-tiles-bad-count").toFile()
        try {
            val cache = MandelbrotTileCache(
                maxMemoryBytes = 1024 * 1024,
                diskDir = dir,
                maxDiskBytes = 1024 * 1024,
            )
            val key = tileKey(42)
            val emptyCount = File(dir, "e600_s0_x42_y0_t256.tile")
            writeCompressedTile(emptyCount, magic = 0x4D425432, pixelCount = 0)

            assertNull(cache.loadFromDisk(key))
            assertFalse(emptyCount.exists())

            val hugeCount = File(dir, "e600_s0_x42_y0_t256.tile")
            writeCompressedTile(hugeCount, magic = 0x4D425432, pixelCount = 512 * 512 * 16 + 1)

            assertNull(cache.loadFromDisk(key))
            assertFalse(hugeCount.exists())
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
    fun `putting the same key again updates memory byte accounting`() {
        val cache = MandelbrotTileCache(maxMemoryBytes = 1024, diskDir = null, maxDiskBytes = 0)
        val key = tileKey(0)
        cache.put(key, ByteArray(100))
        assertEquals(100L, cache.memoryBytes)
        assertEquals(1, cache.memorySize)

        cache.put(key, ByteArray(200))
        assertEquals(200L, cache.memoryBytes)
        assertEquals(1, cache.memorySize)
        assertArrayEquals(ByteArray(200), cache.get(key)?.pixels)

        cache.put(key, ByteArray(50))
        assertEquals(50L, cache.memoryBytes)
        assertEquals(1, cache.memorySize)
        assertArrayEquals(ByteArray(50), cache.get(key)?.pixels)
    }

    @Test
    fun `put overwrite triggers eviction with correct byte totals`() {
        val cache = MandelbrotTileCache(maxMemoryBytes = 150, diskDir = null, maxDiskBytes = 0)
        val evicted = mutableListOf<MandelbrotTiles.TileKey>()
        cache.onEvicted = { evicted += it }

        val first = tileKey(1)
        val second = tileKey(2)
        cache.put(first, ByteArray(100))
        cache.put(second, ByteArray(100))
        assertEquals(listOf(first), evicted)

        evicted.clear()
        cache.put(first, ByteArray(80))
        assertEquals(listOf(second), evicted)
        assertEquals(80L, cache.memoryBytes)
        assertNotNull(cache.get(first))
        assertNull(cache.get(second))
    }

    @Test
    fun `clear memory evicts every cached key through the callback`() {
        val cache = MandelbrotTileCache(maxMemoryBytes = 1024 * 1024, diskDir = null, maxDiskBytes = 0)
        val evicted = mutableListOf<MandelbrotTiles.TileKey>()
        cache.onEvicted = { evicted += it }

        val first = tileKey(1)
        val second = tileKey(2)
        cache.put(first, byteArrayOf(1, 2, 3, 4))
        cache.put(second, byteArrayOf(5, 6, 7, 8))
        assertEquals(2, cache.memorySize)

        cache.clearMemory()

        assertEquals(0, cache.memorySize)
        assertEquals(0L, cache.memoryBytes)
        assertEquals(setOf(first, second), evicted.toSet())
        assertNull(cache.get(first))
        assertNull(cache.get(second))
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

    @Test
    fun `disk access flush waits until the touch interval has elapsed`() {
        val interval = MandelbrotTileCache.TOUCH_MIN_INTERVAL_MS
        assertFalse(MandelbrotTileCache.shouldFlushDiskAccessTime(10_000L, 0L, interval))
        assertFalse(MandelbrotTileCache.shouldFlushDiskAccessTime(29_999L, 0L, interval))
        assertTrue(MandelbrotTileCache.shouldFlushDiskAccessTime(30_000L, 0L, interval))
        assertTrue(MandelbrotTileCache.shouldFlushDiskAccessTime(60_000L, 30_000L, interval))
        assertFalse(MandelbrotTileCache.shouldFlushDiskAccessTime(59_999L, 30_000L, interval))
    }

    private fun writeCompressedTile(
        file: File,
        magic: Int,
        pixelCount: Int,
        pixels: ByteArray = ByteArray(0),
    ) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { fileStream ->
            DeflaterOutputStream(fileStream, Deflater(Deflater.BEST_SPEED)).use { deflated ->
                DataOutputStream(deflated).use { output ->
                    output.writeInt(magic)
                    output.writeInt(pixelCount)
                    if (pixels.isNotEmpty()) {
                        output.write(pixels)
                    }
                }
            }
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
