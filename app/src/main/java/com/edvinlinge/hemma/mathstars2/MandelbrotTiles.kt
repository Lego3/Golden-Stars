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
    /**
     * Visible missing tiles render as one pass so the full picture uses the same row-parallel
     * kernel as a single bitmap. The cap is only a safety bound for an unusually large view.
     */
    const val MAX_VISIBLE_BATCH = 64
    /** Prefetch a handful of neighbours at once so idle cores stay busy without delaying cancel. */
    const val MAX_PREFETCH_BATCH = 4

    /** Prefer a handful of large tiles over a sea of tiny ones so a first frame is one bbox. */
    const val LARGE_TILE_MIN_EDGE = 900
    const val LARGE_TILE_PIXELS = 512
    const val SMALL_TILE_PIXELS = 256

    data class TileKey(
        val zoomStep: Int,
        val tileX: Long,
        val tileY: Long,
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

    /** Keys to render next, with whether they are preview tiles or off-screen prefetch. */
    data class WorkSelection(
        val keys: List<TileKey>,
        val preview: Boolean,
        val prefetch: Boolean,
    )

    /** How many on-screen tiles from a queued set have finished, of how many were queued. */
    data class VisibleTileProgress(val finished: Int, val queued: Int)

    fun viewMinEdge(viewWidth: Int, viewHeight: Int): Int =
        minOf(viewWidth, viewHeight).coerceAtLeast(1)

    /**
     * True when the live view still matches the dimensions an in-flight tile render sampled.
     * [TileKey.viewMinEdge] and tile sampling both derive from these sizes, so a mismatch would
     * store pixels under keys that imply a different world grid.
     */
    fun viewGeometryMatches(
        renderViewWidth: Int,
        renderViewHeight: Int,
        viewWidth: Int,
        viewHeight: Int,
    ): Boolean = renderViewWidth == viewWidth && renderViewHeight == viewHeight

    fun tilePixelSize(viewWidth: Int, viewHeight: Int): Int =
        if (viewMinEdge(viewWidth, viewHeight) >= LARGE_TILE_MIN_EDGE) {
            LARGE_TILE_PIXELS
        } else {
            SMALL_TILE_PIXELS
        }

    fun renderedOutputSize(tilePixelSize: Int, preview: Boolean): Int =
        if (preview) {
            (tilePixelSize / PREVIEW_DOWNSCALE).coerceAtLeast(1)
        } else {
            tilePixelSize
        }

    fun pixelSize(key: TileKey): Int = renderedOutputSize(key.tilePixelSize, key.preview)

    fun renderedRangeDimensions(
        range: TileRange,
        tilePixelSize: Int,
        preview: Boolean,
    ): Pair<Int, Int> {
        val outSize = renderedOutputSize(tilePixelSize, preview)
        val tilesX = (range.x1 - range.x0 + 1L).toInt().coerceAtLeast(1)
        val tilesY = (range.y1 - range.y0 + 1L).toInt().coerceAtLeast(1)
        return tilesX * outSize to tilesY * outSize
    }

    fun renderedRangePixelCount(
        range: TileRange,
        tilePixelSize: Int,
        preview: Boolean,
    ): Int {
        val (width, height) = renderedRangeDimensions(range, tilePixelSize, preview)
        return width * height
    }

    fun hasEnoughPixelsForRange(
        range: TileRange,
        tilePixelSize: Int,
        preview: Boolean,
        pixels: ByteArray,
    ): Boolean = pixels.size >= renderedRangePixelCount(range, tilePixelSize, preview)

    /** True when [pixels] is a square payload sized for [key]. */
    fun acceptsTilePixelPayload(key: TileKey, pixels: ByteArray): Boolean {
        val size = pixelSize(key)
        return pixels.size == size * size
    }

    /**
     * Guards tile install after an async render: the live view must still match the sampled
     * geometry and the pixel buffer must cover the full requested tile range.
     */
    fun shouldInstallRenderedRange(
        renderViewWidth: Int,
        renderViewHeight: Int,
        viewWidth: Int,
        viewHeight: Int,
        range: TileRange,
        tilePixelSize: Int,
        preview: Boolean,
        pixels: ByteArray,
    ): Boolean {
        if (viewWidth <= 0 || viewHeight <= 0) return false
        if (!viewGeometryMatches(renderViewWidth, renderViewHeight, viewWidth, viewHeight)) {
            return false
        }
        return hasEnoughPixelsForRange(range, tilePixelSize, preview, pixels)
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
                tilePixelSize = tilePixelSize,
                viewMinEdge = minEdge,
                available = available,
            )?.let { draws += it.copy(dest = dest) }

            for (ly in 0..1) {
                for (lx in 0..1) {
                    val cx = childTileX(tx, lx)
                    val cy = childTileY(ty, ly)
                    val childFull = TileKey(step + 1, cx, cy, tilePixelSize, minEdge, false)
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
            visibleRange, step, tilePixelSize, minEdge, preview = false,
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
            keysForRange(panRange, step, tilePixelSize, minEdge, preview = false),
        )

        val ring = expandRange(visibleRange, extra = 1)
        addMissing(
            keysForRange(ring, step, tilePixelSize, minEdge, preview = false),
        )

        val zoomInFirst = zoomSign >= 0
        val zoomInKeys = zoomStepKeys(
            zoom = MandelbrotMath.clampedZoom(zoom, 2.0, minZoom, maxZoom),
            offsetX = offsetX,
            offsetY = offsetY,
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            tilePixelSize = tilePixelSize,
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
        tilePixelSize: Int,
        viewMinEdge: Int,
        preview: Boolean,
    ): List<TileKey> {
        val keys = ArrayList<TileKey>(range.tileCount.toInt().coerceAtLeast(0).coerceAtMost(4096))
        range.forEach { x, y ->
            keys += TileKey(zoomStep, x, y, tilePixelSize, viewMinEdge, preview)
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
     * A contiguous visible range can render as one bbox; scattered keys need per-tile passes.
     * Matches the dense/sparse split in [com.edvinlinge.hemma.mathstars2.MandelbrotView].
     */
    fun isDenseTileBatch(keys: List<TileKey>): Boolean {
        val bbox = bboxOf(keys) ?: return false
        return bbox.tileCount <= keys.size * 2L
    }

    /** Copies a square from a row-major [source] grid into [dest]. */
    fun copySubgrid(
        source: ByteArray,
        sourceWidth: Int,
        srcX: Int,
        srcY: Int,
        size: Int,
        dest: ByteArray,
    ) {
        var dst = 0
        for (y in 0 until size) {
            val srcRow = (srcY + y) * sourceWidth + srcX
            source.copyInto(dest, dst, srcRow, srcRow + size)
            dst += size
        }
    }

    /**
     * Picks the next render batch from a [renderPlan]: visible full-res while idle, previews
     * during gestures when the viewport still has holes, then homogeneous prefetch when memory
     * allows.
     */
    fun selectNextWork(
        plan: RenderPlan,
        isInteracting: Boolean,
        viewportCovered: Boolean,
        memoryBytes: Long,
        maxMemoryBytes: Long,
    ): WorkSelection? {
        if (isInteracting) {
            if (plan.visiblePreview.isNotEmpty() && !viewportCovered) {
                return WorkSelection(
                    keys = plan.visiblePreview.take(MAX_VISIBLE_BATCH),
                    preview = true,
                    prefetch = false,
                )
            }
            return null
        }
        if (plan.visibleFull.isNotEmpty()) {
            return WorkSelection(
                keys = plan.visibleFull.take(MAX_VISIBLE_BATCH),
                preview = false,
                prefetch = false,
            )
        }
        if (plan.prefetch.isNotEmpty() && memoryBytes < maxMemoryBytes) {
            val first = plan.prefetch.first()
            val batch = plan.prefetch.takeWhile {
                it.zoomStep == first.zoomStep && it.tilePixelSize == first.tilePixelSize
            }.take(MAX_PREFETCH_BATCH)
            return WorkSelection(keys = batch, preview = false, prefetch = true)
        }
        return null
    }

    /**
     * Full-resolution keys that cover the current viewport at its discrete zoom step. These are
     * the tiles that will sharpen what the user is looking at; prefetch neighbours are excluded.
     */
    fun visibleFullKeys(
        zoom: Double,
        offsetX: Double,
        offsetY: Double,
        viewWidth: Int,
        viewHeight: Int,
        tilePixelSize: Int,
    ): List<TileKey> {
        if (viewWidth <= 0 || viewHeight <= 0) return emptyList()
        val step = zoomStep(zoom)
        val minEdge = viewMinEdge(viewWidth, viewHeight)
        val range = visibleTileRange(
            offsetX, offsetY, zoom, viewWidth, viewHeight, step, tilePixelSize,
        )
        return keysForRange(range, step, tilePixelSize, minEdge, preview = false)
    }

    /**
     * Keeps finished tiles that are still on screen so progress can read 4/10, drops tiles that
     * panned away, and adds newly missing visible tiles after a pan or zoom.
     */
    fun mergeVisibleTileQueue(
        tracked: Set<TileKey>,
        visibleKeys: Collection<TileKey>,
        isCached: (TileKey) -> Boolean,
    ): Set<TileKey> {
        val visibleSet = if (visibleKeys is Set) visibleKeys else visibleKeys.toSet()
        val merged = LinkedHashSet<TileKey>(tracked.size + visibleSet.size)
        for (key in tracked) {
            if (key in visibleSet) merged += key
        }
        for (key in visibleKeys) {
            if (!isCached(key)) merged += key
        }
        return merged
    }

    fun visibleTileProgress(
        queuedKeys: Set<TileKey>,
        isCached: (TileKey) -> Boolean,
    ): VisibleTileProgress {
        var finished = 0
        for (key in queuedKeys) {
            if (isCached(key)) finished++
        }
        return VisibleTileProgress(finished = finished, queued = queuedKeys.size)
    }

    /**
     * True when every on-screen tile at the current zoom step already has a full-resolution cache
     * entry, so prefetch can start and the loading circle can hide.
     */
    fun visibleTilesComplete(
        zoom: Double,
        offsetX: Double,
        offsetY: Double,
        viewWidth: Int,
        viewHeight: Int,
        tilePixelSize: Int,
        isCached: (TileKey) -> Boolean,
    ): Boolean {
        val keys = visibleFullKeys(
            zoom, offsetX, offsetY, viewWidth, viewHeight, tilePixelSize,
        )
        if (keys.isEmpty()) return false
        for (key in keys) {
            if (!isCached(key)) return false
        }
        return true
    }

    /**
     * True when every visible tile can already be painted from cache: an exact tile, a preview,
     * a scaled ancestor, or a complete set of children. Used to skip preview work during a
     * gesture when something is already on screen; the loading circle uses [visibleTilesComplete]
     * instead so sharpening still shows progress.
     */
    fun visibleViewportCovered(
        zoom: Double,
        offsetX: Double,
        offsetY: Double,
        viewWidth: Int,
        viewHeight: Int,
        tilePixelSize: Int,
        isCached: (TileKey) -> Boolean,
    ): Boolean {
        if (viewWidth <= 0 || viewHeight <= 0) return false
        val step = zoomStep(zoom)
        val minEdge = viewMinEdge(viewWidth, viewHeight)
        val range = visibleTileRange(
            offsetX, offsetY, zoom, viewWidth, viewHeight, step, tilePixelSize,
        )
        var covered = true
        range.forEach { x, y ->
            if (!tileIsCovered(x, y, step, tilePixelSize, minEdge, isCached)) {
                covered = false
            }
        }
        return covered
    }

    /**
     * Cache keys the current frame may blit: exact tiles, previews, ancestors that stand in, and
     * one level of children. LRU eviction must keep these so a zoom does not delete the scaled
     * image that is still on screen.
     */
    fun protectableKeys(
        zoom: Double,
        offsetX: Double,
        offsetY: Double,
        viewWidth: Int,
        viewHeight: Int,
        tilePixelSize: Int,
    ): Set<TileKey> {
        if (viewWidth <= 0 || viewHeight <= 0) return emptySet()
        val step = zoomStep(zoom)
        val minEdge = viewMinEdge(viewWidth, viewHeight)
        val range = visibleTileRange(
            offsetX, offsetY, zoom, viewWidth, viewHeight, step, tilePixelSize,
        )
        val keys = HashSet<TileKey>(range.tileCount.toInt().coerceAtLeast(0) * 12)
        range.forEach { x, y ->
            var ax = x
            var ay = y
            for (level in 0..MAX_ANCESTOR_LEVELS) {
                val full = TileKey(step - level, ax, ay, tilePixelSize, minEdge, false)
                keys += full
                keys += full.copy(preview = true)
                ax = parentTileX(ax)
                ay = parentTileY(ay)
            }
            for (ly in 0..1) {
                for (lx in 0..1) {
                    val child = TileKey(
                        step + 1,
                        childTileX(x, lx),
                        childTileY(y, ly),
                        tilePixelSize,
                        minEdge,
                        preview = false,
                    )
                    keys += child
                    keys += child.copy(preview = true)
                }
            }
        }
        return keys
    }

    fun tileIsCovered(
        tileX: Long,
        tileY: Long,
        tileStep: Int,
        tilePixelSize: Int,
        viewMinEdge: Int,
        isCached: (TileKey) -> Boolean,
    ): Boolean {
        if (hasCoveringSource(tileX, tileY, tileStep, tilePixelSize, viewMinEdge, isCached)) {
            return true
        }
        var childCount = 0
        for (ly in 0..1) {
            for (lx in 0..1) {
                val child = TileKey(
                    tileStep + 1,
                    childTileX(tileX, lx),
                    childTileY(tileY, ly),
                    tilePixelSize,
                    viewMinEdge,
                    preview = false,
                )
                if (isCached(child) || isCached(child.copy(preview = true))) childCount++
            }
        }
        return childCount == 4
    }

    private fun hasCoveringSource(
        tileX: Long,
        tileY: Long,
        tileStep: Int,
        tilePixelSize: Int,
        viewMinEdge: Int,
        isCached: (TileKey) -> Boolean,
    ): Boolean {
        var ax = tileX
        var ay = tileY
        for (level in 0..MAX_ANCESTOR_LEVELS) {
            val full = TileKey(
                tileStep - level,
                ax,
                ay,
                tilePixelSize,
                viewMinEdge,
                preview = false,
            )
            if (isCached(full) || isCached(full.copy(preview = true))) return true
            ax = parentTileX(ax)
            ay = parentTileY(ay)
        }
        return false
    }

    private fun bestCoveringSource(
        tileX: Long,
        tileY: Long,
        tileStep: Int,
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
            val full = TileKey(step, ax, ay, tilePixelSize, viewMinEdge, false)
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
        minEdge: Int,
        focusX: Double,
        focusY: Double,
    ): List<TileKey> {
        val step = zoomStep(zoom)
        val range = visibleTileRange(
            offsetX, offsetY, zoom, viewWidth, viewHeight, step, tilePixelSize,
        )
        return sortTowardFocus(
            keysForRange(range, step, tilePixelSize, minEdge, preview = false),
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
