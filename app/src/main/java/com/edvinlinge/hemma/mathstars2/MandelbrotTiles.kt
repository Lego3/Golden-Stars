package com.edvinlinge.hemma.mathstars2

import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Discrete zoom-step tiling for the Mandelbrot explorer.
 *
 * The on-screen zoom stays continuous so a pinch still feels analogue, but cached images live on
 * power-of-two zoom steps. Nearby steps are close enough that scaling a cached tile looks almost
 * correct while a matching render is in flight, and parent/child tiles share aligned edges so a
 * coarser tile can fill in for four finer ones (and the reverse).
 */
internal object MandelbrotTiles {

    const val PREVIEW_DOWNSCALE = 4
    const val MAX_ANCESTOR_LEVELS = 8
    const val MAX_VISIBLE_BATCH = 4

    /** Prefer a handful of large tiles over a sea of tiny ones so a first frame is one bbox. */
    const val LARGE_TILE_MIN_EDGE = 900
    const val LARGE_TILE_PIXELS = 512
    const val SMALL_TILE_PIXELS = 256

    data class TileKey(
        val zoomStep: Int,
        val tileX: Long,
        val tileY: Long,
        val paletteOrdinal: Int,
        val tilePixelSize: Int,
        val viewMinEdge: Int,
        val preview: Boolean,
    )

    data class TileRange(
        val x0: Long,
        val x1: Long,
        val y0: Long,
        val y1: Long,
    ) {
        val tileCount: Long
            get() {
                val width = (x1 - x0 + 1L).coerceAtLeast(0L)
                val height = (y1 - y0 + 1L).coerceAtLeast(0L)
                return width * height
            }

        fun forEach(action: (tileX: Long, tileY: Long) -> Unit) {
            var y = y0
            while (y <= y1) {
                var x = x0
                while (x <= x1) {
                    action(x, y)
                    x++
                }
                y++
            }
        }
    }

    data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int)

    data class ScreenRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        fun intersectsView(viewWidth: Int, viewHeight: Int): Boolean =
            right > 0f && bottom > 0f && left < viewWidth && top < viewHeight
    }

    /** One bitmap blit: a (possibly cropped) cached tile drawn into a screen rectangle. */
    data class TileDraw(
        val key: TileKey,
        val src: PixelRect,
        val dest: ScreenRect,
    )

    /**
     * Work ordered for a smooth frame: fill holes the user can see, then predict the next gesture.
     */
    data class RenderPlan(
        val visibleFull: List<TileKey>,
        val visiblePreview: List<TileKey>,
        val prefetch: List<TileKey>,
    )

    fun viewMinEdge(viewWidth: Int, viewHeight: Int): Int =
        minOf(viewWidth, viewHeight).coerceAtLeast(1)

    fun tilePixelSize(viewWidth: Int, viewHeight: Int): Int =
        if (viewMinEdge(viewWidth, viewHeight) >= LARGE_TILE_MIN_EDGE) {
            LARGE_TILE_PIXELS
        } else {
            SMALL_TILE_PIXELS
        }

    fun pixelSize(key: TileKey): Int =
        if (key.preview) {
            (key.tilePixelSize / PREVIEW_DOWNSCALE).coerceAtLeast(1)
        } else {
            key.tilePixelSize
        }

    /**
     * Nearest power-of-two zoom, matching the double-tap factor so a 2× jump lands on a cached
     * step when prefetch has kept up.
     */
    fun zoomStep(zoom: Double): Int {
        val safe = zoom.coerceAtLeast(Double.MIN_VALUE)
        return (ln(safe) / LN_2).roundToInt()
    }

    fun discreteZoom(zoomStep: Int): Double = pow2(zoomStep)

    fun tileWorldSize(
        zoomStep: Int,
        tilePixelSize: Int,
        viewWidth: Int,
        viewHeight: Int,
    ): Double = tilePixelSize * MandelbrotMath.unitsPerPixel(
        discreteZoom(zoomStep),
        viewWidth,
        viewHeight,
    )

    fun visibleTileRange(
        offsetX: Double,
        offsetY: Double,
        zoom: Double,
        viewWidth: Int,
        viewHeight: Int,
        zoomStep: Int,
        tilePixelSize: Int,
    ): TileRange {
        val world = tileWorldSize(zoomStep, tilePixelSize, viewWidth, viewHeight)
        if (world <= 0.0 || !world.isFinite()) {
            return TileRange(0, 0, 0, 0)
        }
        val upp = MandelbrotMath.unitsPerPixel(zoom, viewWidth, viewHeight)
        val xMin = offsetX - upp * viewWidth / 2.0
        val xMax = offsetX + upp * viewWidth / 2.0
        val yMin = offsetY - upp * viewHeight / 2.0
        val yMax = offsetY + upp * viewHeight / 2.0
        val eps = world * 1e-9
        val x0 = floor(xMin / world).toLong()
        val x1 = floor((xMax - eps) / world).toLong().coerceAtLeast(x0)
        val y0 = floor(yMin / world).toLong()
        val y1 = floor((yMax - eps) / world).toLong().coerceAtLeast(y0)
        return TileRange(x0, x1, y0, y1)
    }

    fun parentTileX(tileX: Long): Long = tileX.floorDiv(2L)

    fun parentTileY(tileY: Long): Long = tileY.floorDiv(2L)

    fun childTileX(parentX: Long, localX: Int): Long = parentX * 2 + localX

    fun childTileY(parentY: Long, localY: Int): Long = parentY * 2 + localY

    /**
     * Pixel rectangle inside [ancestor] that covers the descendant tile. Integer floor-division
     * keeps the mapping stable for negative tile indices.
     */
    fun sourceRectInAncestor(
        tileX: Long,
        tileY: Long,
        tileStep: Int,
        ancestorX: Long,
        ancestorY: Long,
        ancestorStep: Int,
        ancestorPixelSize: Int,
    ): PixelRect {
        val levels = tileStep - ancestorStep
        if (levels <= 0 || ancestorPixelSize <= 0) {
            return PixelRect(0, 0, ancestorPixelSize, ancestorPixelSize)
        }
        val divisions = 1L shl levels.coerceAtMost(30)
        val srcSize = (ancestorPixelSize / divisions.toInt()).coerceAtLeast(1)
        val localX = (tileX - ancestorX * divisions).toInt().coerceIn(0, divisions.toInt() - 1)
        val localY = (tileY - ancestorY * divisions).toInt().coerceIn(0, divisions.toInt() - 1)
        val left = localX * srcSize
        val top = localY * srcSize
        return PixelRect(left, top, left + srcSize, top + srcSize)
    }

    fun tileScreenRect(
        tileX: Long,
        tileY: Long,
        zoomStep: Int,
        tilePixelSize: Int,
        zoom: Double,
        offsetX: Double,
        offsetY: Double,
        viewWidth: Int,
        viewHeight: Int,
    ): ScreenRect {
        val world = tileWorldSize(zoomStep, tilePixelSize, viewWidth, viewHeight)
        val upp = MandelbrotMath.unitsPerPixel(zoom, viewWidth, viewHeight)
        val x0 = tileX * world
        val y0 = tileY * world
        val left = ((x0 - offsetX) / upp + viewWidth / 2.0).toFloat()
        val top = ((y0 - offsetY) / upp + viewHeight / 2.0).toFloat()
        val right = ((x0 + world - offsetX) / upp + viewWidth / 2.0).toFloat()
        val bottom = ((y0 + world - offsetY) / upp + viewHeight / 2.0).toFloat()
        return ScreenRect(left, top, right, bottom)
    }

    /**
     * How many samples from [key] cover one target tile at [targetStep]. Higher is sharper.
     * A parent full-resolution tile beats a same-step preview because it contributes more pixels.
     */
    fun coverageSamples(key: TileKey, targetStep: Int): Int {
        val samples = pixelSize(key)
        val levels = targetStep - key.zoomStep
        return when {
            levels > 0 -> samples shr levels.coerceAtMost(30)
            levels < 0 -> samples shl (-levels).coerceAtMost(12)
            else -> samples
        }
    }

    fun composeDrawList(
        zoom: Double,
        offsetX: Double,
        offsetY: Double,
        viewWidth: Int,
        viewHeight: Int,
        tilePixelSize: Int,
        paletteOrdinal: Int,
        available: Set<TileKey>,
    ): List<TileDraw> {
        if (viewWidth <= 0 || viewHeight <= 0 || available.isEmpty()) return emptyList()
        val step = zoomStep(zoom)
        val minEdge = viewMinEdge(viewWidth, viewHeight)
        val range = visibleTileRange(
            offsetX, offsetY, zoom, viewWidth, viewHeight, step, tilePixelSize,
        )
        val draws = ArrayList<TileDraw>(range.tileCount.toInt().coerceAtMost(256) * 2)
        range.forEach { tx, ty ->
            val dest = tileScreenRect(
                tx, ty, step, tilePixelSize, zoom, offsetX, offsetY, viewWidth, viewHeight,
            )
            if (!dest.intersectsView(viewWidth, viewHeight)) return@forEach

            bestCoveringSource(
                tileX = tx,
                tileY = ty,
                tileStep = step,
                paletteOrdinal = paletteOrdinal,
                tilePixelSize = tilePixelSize,
                viewMinEdge = minEdge,
                available = available,
            )?.let { draws += it.copy(dest = dest) }

            for (ly in 0..1) {
                for (lx in 0..1) {
                    val cx = childTileX(tx, lx)
                    val cy = childTileY(ty, ly)
                    val childFull = TileKey(step + 1, cx, cy, paletteOrdinal, tilePixelSize, minEdge, false)
                    val childPreview = childFull.copy(preview = true)
                    val child = when {
                        childFull in available -> childFull
                        childPreview in available -> childPreview
                        else -> null
                    } ?: continue
                    val childDest = tileScreenRect(
                        cx, cy, step + 1, tilePixelSize, zoom, offsetX, offsetY, viewWidth, viewHeight,
                    )
                    if (!childDest.intersectsView(viewWidth, viewHeight)) continue
                    val size = pixelSize(child)
                    draws += TileDraw(child, PixelRect(0, 0, size, size), childDest)
                }
            }
        }
        // Coarser (and preview) images go down first so sharper tiles paint on top.
        return draws.sortedWith(
            compareBy<TileDraw> { it.key.zoomStep }
                .thenBy { if (it.key.preview) 0 else 1 },
        )
    }

    fun renderPlan(
        zoom: Double,
        offsetX: Double,
        offsetY: Double,
        viewWidth: Int,
        viewHeight: Int,
        tilePixelSize: Int,
        paletteOrdinal: Int,
        panSignX: Int,
        panSignY: Int,
        zoomSign: Int,
        focusX: Double,
        focusY: Double,
        minZoom: Double,
        maxZoom: Double,
        isCached: (TileKey) -> Boolean,
    ): RenderPlan {
        val minEdge = viewMinEdge(viewWidth, viewHeight)
        val step = zoomStep(zoom)
        val visibleRange = visibleTileRange(
            offsetX, offsetY, zoom, viewWidth, viewHeight, step, tilePixelSize,
        )
        val visibleFull = keysForRange(
            visibleRange, step, paletteOrdinal, tilePixelSize, minEdge, preview = false,
        ).let { sortTowardFocus(it, focusX, focusY, step, tilePixelSize, viewWidth, viewHeight) }

        val visiblePreview = visibleFull.map { it.copy(preview = true) }
            .filter { !isCached(it) && !isCached(it.copy(preview = false)) }

        val prefetch = LinkedHashSet<TileKey>()

        fun addMissing(keys: List<TileKey>) {
            for (key in keys) {
                if (!isCached(key) && !isCached(key.copy(preview = false))) {
                    prefetch += key.copy(preview = false)
                }
            }
        }

        val panRange = expandRange(visibleRange, panSignX, panSignY, extra = 1)
        addMissing(
            keysForRange(panRange, step, paletteOrdinal, tilePixelSize, minEdge, preview = false),
        )

        val ring = expandRange(visibleRange, extra = 1)
        addMissing(
            keysForRange(ring, step, paletteOrdinal, tilePixelSize, minEdge, preview = false),
        )

        val zoomInFirst = zoomSign >= 0
        val zoomInKeys = zoomStepKeys(
            zoom = MandelbrotMath.clampedZoom(zoom, 2.0, minZoom, maxZoom),
            offsetX = offsetX,
            offsetY = offsetY,
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            tilePixelSize = tilePixelSize,
            paletteOrdinal = paletteOrdinal,
            minEdge = minEdge,
            focusX = focusX,
            focusY = focusY,
        )
        val zoomOutKeys = zoomStepKeys(
            zoom = MandelbrotMath.clampedZoom(zoom, 0.5, minZoom, maxZoom),
            offsetX = offsetX,
            offsetY = offsetY,
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            tilePixelSize = tilePixelSize,
            paletteOrdinal = paletteOrdinal,
            minEdge = minEdge,
            focusX = focusX,
            focusY = focusY,
        )
        if (zoomInFirst) {
            addMissing(zoomInKeys)
            addMissing(zoomOutKeys)
        } else {
            addMissing(zoomOutKeys)
            addMissing(zoomInKeys)
        }

        val visibleFullKeys = visibleFull.filter { !isCached(it) }
        // Prefetch must not steal the tiles the current frame still needs.
        val visibleSet = visibleFull.toSet()
        prefetch.removeAll(visibleSet)

        return RenderPlan(
            visibleFull = visibleFullKeys,
            visiblePreview = visiblePreview,
            prefetch = prefetch.toList(),
        )
    }

    fun expandRange(range: TileRange, panSignX: Int = 0, panSignY: Int = 0, extra: Int = 1): TileRange {
        val pad = extra.toLong()
        val extraLeft = if (panSignX < 0) pad else 0L
        val extraRight = if (panSignX > 0) pad else 0L
        val extraUp = if (panSignY < 0) pad else 0L
        val extraDown = if (panSignY > 0) pad else 0L
        val uniform = if (panSignX == 0 && panSignY == 0) pad else 0L
        return TileRange(
            x0 = range.x0 - extraLeft - uniform,
            x1 = range.x1 + extraRight + uniform,
            y0 = range.y0 - extraUp - uniform,
            y1 = range.y1 + extraDown + uniform,
        )
    }

    fun keysForRange(
        range: TileRange,
        zoomStep: Int,
        paletteOrdinal: Int,
        tilePixelSize: Int,
        viewMinEdge: Int,
        preview: Boolean,
    ): List<TileKey> {
        val keys = ArrayList<TileKey>(range.tileCount.toInt().coerceAtLeast(0).coerceAtMost(4096))
        range.forEach { x, y ->
            keys += TileKey(zoomStep, x, y, paletteOrdinal, tilePixelSize, viewMinEdge, preview)
        }
        return keys
    }

    fun sortTowardFocus(
        keys: List<TileKey>,
        focusX: Double,
        focusY: Double,
        zoomStep: Int,
        tilePixelSize: Int,
        viewWidth: Int,
        viewHeight: Int,
    ): List<TileKey> {
        if (keys.size <= 1) return keys
        val world = tileWorldSize(zoomStep, tilePixelSize, viewWidth, viewHeight)
        if (world <= 0.0) return keys
        val focusTx = focusX / world
        val focusTy = focusY / world
        return keys.sortedBy { key ->
            hypot(key.tileX + 0.5 - focusTx, key.tileY + 0.5 - focusTy)
        }
    }

    fun bboxOf(keys: List<TileKey>): TileRange? {
        if (keys.isEmpty()) return null
        var x0 = Long.MAX_VALUE
        var x1 = Long.MIN_VALUE
        var y0 = Long.MAX_VALUE
        var y1 = Long.MIN_VALUE
        for (key in keys) {
            if (key.tileX < x0) x0 = key.tileX
            if (key.tileX > x1) x1 = key.tileX
            if (key.tileY < y0) y0 = key.tileY
            if (key.tileY > y1) y1 = key.tileY
        }
        return TileRange(x0, x1, y0, y1)
    }

    /**
     * True when every on-screen tile at the current zoom step already has a full-resolution cache
     * entry, so the spinner can hide even if prefetch is still running.
     */
    fun visibleTilesComplete(
        zoom: Double,
        offsetX: Double,
        offsetY: Double,
        viewWidth: Int,
        viewHeight: Int,
        tilePixelSize: Int,
        paletteOrdinal: Int,
        isCached: (TileKey) -> Boolean,
    ): Boolean {
        val step = zoomStep(zoom)
        val minEdge = viewMinEdge(viewWidth, viewHeight)
        val range = visibleTileRange(
            offsetX, offsetY, zoom, viewWidth, viewHeight, step, tilePixelSize,
        )
        var complete = true
        range.forEach { x, y ->
            val key = TileKey(step, x, y, paletteOrdinal, tilePixelSize, minEdge, preview = false)
            if (!isCached(key)) complete = false
        }
        return complete
    }

    private fun bestCoveringSource(
        tileX: Long,
        tileY: Long,
        tileStep: Int,
        paletteOrdinal: Int,
        tilePixelSize: Int,
        viewMinEdge: Int,
        available: Set<TileKey>,
    ): TileDraw? {
        var ax = tileX
        var ay = tileY
        var best: TileKey? = null
        var bestScore = Int.MIN_VALUE
        for (level in 0..MAX_ANCESTOR_LEVELS) {
            val step = tileStep - level
            val full = TileKey(step, ax, ay, paletteOrdinal, tilePixelSize, viewMinEdge, false)
            val preview = full.copy(preview = true)
            if (full in available) {
                val score = coverageSamples(full, tileStep)
                if (score > bestScore) {
                    best = full
                    bestScore = score
                }
            }
            if (preview in available) {
                val score = coverageSamples(preview, tileStep)
                if (score > bestScore) {
                    best = preview
                    bestScore = score
                }
            }
            ax = parentTileX(ax)
            ay = parentTileY(ay)
        }
        val key = best ?: return null
        val size = pixelSize(key)
        val src = sourceRectInAncestor(
            tileX = tileX,
            tileY = tileY,
            tileStep = tileStep,
            ancestorX = key.tileX,
            ancestorY = key.tileY,
            ancestorStep = key.zoomStep,
            ancestorPixelSize = size,
        )
        return TileDraw(key, src, ScreenRect(0f, 0f, 0f, 0f))
    }

    private fun zoomStepKeys(
        zoom: Double,
        offsetX: Double,
        offsetY: Double,
        viewWidth: Int,
        viewHeight: Int,
        tilePixelSize: Int,
        paletteOrdinal: Int,
        minEdge: Int,
        focusX: Double,
        focusY: Double,
    ): List<TileKey> {
        val step = zoomStep(zoom)
        val range = visibleTileRange(
            offsetX, offsetY, zoom, viewWidth, viewHeight, step, tilePixelSize,
        )
        return sortTowardFocus(
            keysForRange(range, step, paletteOrdinal, tilePixelSize, minEdge, preview = false),
            focusX,
            focusY,
            step,
            tilePixelSize,
            viewWidth,
            viewHeight,
        )
    }

    private fun pow2(step: Int): Double = when {
        step >= 0 -> (1L shl step.coerceAtMost(62)).toDouble()
        else -> 1.0 / (1L shl (-step).coerceAtMost(62)).toDouble()
    }

    private val LN_2 = ln(2.0)
}
