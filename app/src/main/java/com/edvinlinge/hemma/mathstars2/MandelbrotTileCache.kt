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
 * Access is not synchronized. The view uses this from the main thread except for the disk helpers,
 * which only read/write byte streams and then hop back to main to [put].
 */
internal class MandelbrotTileCache(
    val maxMemoryBytes: Long,
    private val diskDir: File?,
    private val maxDiskBytes: Long,
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

    val memorySize: Int get() = memory.size
    val memoryBytes: Long get() = usedBytes
    val keys: Set<MandelbrotTiles.TileKey> get() = memory.keys.toSet()

    fun get(key: MandelbrotTiles.TileKey): Entry? = memory[key]

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
                        file.setLastModified(System.currentTimeMillis())
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
        val oldestFirst = files.sortedBy { it.lastModified() }
        for (file in oldestFirst) {
            if (total <= maxDiskBytes) break
            val size = file.length()
            if (file.delete()) total -= size
        }
    }

    companion object {
        private const val MAGIC = 0x4D425431 // "MBT1"
        private const val MAX_PIXELS = 512 * 512 * 16
    }
}
