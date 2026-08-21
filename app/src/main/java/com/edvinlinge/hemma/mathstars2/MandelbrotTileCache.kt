package com.edvinlinge.hemma.mathstars2

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.LinkedHashMap
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

/**
 * LRU pixel cache for Mandelbrot tiles. The memory map is the live working set; the optional disk
 * directory keeps recently used tiles across view recreation and process death.
 *
 * Disk eviction follows last *use*, not first write. A tile that stays in RAM (the default 1× view
 * is the usual case) still counts as used, so a long zooming session does not delete it just because
 * its file is old. Access times are flushed onto file lastModified so the same policy survives
 * process death.
 *
 * Access is not synchronized. The view uses this from the main thread except for the disk helpers,
 * which only read/write byte streams and then hop back to main to [put].
 */
internal class MandelbrotTileCache(
    val maxMemoryBytes: Long,
    private val diskDir: File?,
    private val maxDiskBytes: Long,
    private val currentTimeMs: () -> Long = { System.currentTimeMillis() },
) {
    data class Entry(
        val pixels: IntArray,
        val preview: Boolean,
    ) {
        val byteCount: Int get() = pixels.size * Int.SIZE_BYTES
    }

    var onEvicted: ((MandelbrotTiles.TileKey) -> Unit)? = null

    private val memory = LinkedHashMap<MandelbrotTiles.TileKey, Entry>(16, 0.75f, true)
    private var usedBytes = 0L
    private val lastAccessMs = HashMap<String, Long>()
    private val lastTouchMs = HashMap<String, Long>()

    val memorySize: Int get() = memory.size
    val memoryBytes: Long get() = usedBytes
    val keys: Set<MandelbrotTiles.TileKey> get() = memory.keys.toSet()

    fun get(key: MandelbrotTiles.TileKey): Entry? {
        val entry = memory[key] ?: return null
        recordAccess(key)
        return entry
    }

    fun contains(key: MandelbrotTiles.TileKey): Boolean = memory.containsKey(key)

    fun put(
        key: MandelbrotTiles.TileKey,
        pixels: IntArray,
        preview: Boolean = key.preview,
        protectedKeys: Set<MandelbrotTiles.TileKey> = emptySet(),
    ) {
        val entry = Entry(pixels, preview)
        memory.remove(key)?.let { usedBytes -= it.byteCount }
        memory[key] = entry
        usedBytes += entry.byteCount
        recordAccess(key)
        evictIfNeeded(protectedKeys + key)
    }

    fun loadFromDisk(key: MandelbrotTiles.TileKey): Entry? {
        val file = diskFile(key) ?: return null
        if (!file.isFile) return null
        return try {
            FileInputStream(file).use { fileStream ->
                InflaterInputStream(fileStream, Inflater()).use { inflated ->
                    DataInputStream(inflated).use { input ->
                        val magic = input.readInt()
                        if (magic != MAGIC) return null
                        val count = input.readInt()
                        if (count <= 0 || count > MAX_PIXELS) return null
                        val pixels = IntArray(count)
                        for (i in 0 until count) {
                            pixels[i] = input.readInt()
                        }
                        recordAccess(key)
                        touchFile(file)
                        Entry(pixels, key.preview)
                    }
                }
            }
        } catch (_: Exception) {
            file.delete()
            null
        }
    }

    fun saveToDisk(key: MandelbrotTiles.TileKey, pixels: IntArray) {
        val dir = diskDir ?: return
        if (key.preview) return
        dir.mkdirs()
        val file = diskFile(key) ?: return
        val tmp = File(file.parentFile, file.name + ".tmp")
        try {
            FileOutputStream(tmp).use { fileStream ->
                DeflaterOutputStream(fileStream, Deflater(Deflater.BEST_SPEED)).use { deflated ->
                    DataOutputStream(deflated).use { output ->
                        output.writeInt(MAGIC)
                        output.writeInt(pixels.size)
                        for (pixel in pixels) {
                            output.writeInt(pixel)
                        }
                    }
                }
            }
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
            recordAccess(key)
            flushAccessTimesToFiles()
            pruneDisk()
        } catch (_: Exception) {
            tmp.delete()
            file.delete()
        }
    }

    fun clearMemory() {
        val evicted = memory.keys.toList()
        memory.clear()
        usedBytes = 0L
        evicted.forEach { onEvicted?.invoke(it) }
    }

    private fun evictIfNeeded(protectedKeys: Set<MandelbrotTiles.TileKey> = emptySet()) {
        val iterator = memory.entries.iterator()
        while (usedBytes > maxMemoryBytes && iterator.hasNext()) {
            val eldest = iterator.next()
            if (eldest.key in protectedKeys) continue
            iterator.remove()
            usedBytes -= eldest.value.byteCount
            onEvicted?.invoke(eldest.key)
        }
        if (usedBytes < 0L) usedBytes = 0L
    }

    private fun recordAccess(key: MandelbrotTiles.TileKey) {
        val file = diskFile(key) ?: return
        lastAccessMs[file.name] = currentTimeMs()
    }

    /**
     * Copy in-memory last-use times onto the files so a later process still evicts by last use.
     * Skips files touched recently to avoid extra filesystem work during a gesture.
     */
    private fun flushAccessTimesToFiles() {
        val dir = diskDir ?: return
        val now = currentTimeMs()
        for ((name, accessed) in lastAccessMs) {
            val touched = lastTouchMs[name] ?: 0L
            if (accessed - touched < TOUCH_MIN_INTERVAL_MS) continue
            val file = File(dir, name)
            if (file.isFile) touchFile(file, accessed.coerceAtMost(now))
        }
    }

    private fun touchFile(file: File, atMs: Long = currentTimeMs()) {
        if (file.setLastModified(atMs)) {
            lastTouchMs[file.name] = atMs
        }
    }

    private fun diskFile(key: MandelbrotTiles.TileKey): File? {
        val dir = diskDir ?: return null
        val name = buildString {
            append("p").append(key.paletteOrdinal)
            append("_e").append(key.viewMinEdge)
            append("_s").append(key.zoomStep)
            append("_x").append(key.tileX)
            append("_y").append(key.tileY)
            append("_t").append(key.tilePixelSize)
            if (key.preview) append("_q")
            append(".tile")
        }
        return File(dir, name)
    }

    private fun pruneDisk() {
        val dir = diskDir ?: return
        val files = dir.listFiles { file -> file.isFile && file.name.endsWith(".tile") } ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxDiskBytes) return
        val leastRecentlyUsedFirst = files.sortedBy { file ->
            lastAccessMs[file.name] ?: file.lastModified()
        }
        for (file in leastRecentlyUsedFirst) {
            if (total <= maxDiskBytes) break
            val size = file.length()
            if (file.delete()) {
                total -= size
                lastAccessMs.remove(file.name)
                lastTouchMs.remove(file.name)
            }
        }
    }

    companion object {
        private const val MAGIC = 0x4D425431 // "MBT1"
        private const val MAX_PIXELS = 512 * 512 * 16
        private const val TOUCH_MIN_INTERVAL_MS = 30_000L
    }
}
