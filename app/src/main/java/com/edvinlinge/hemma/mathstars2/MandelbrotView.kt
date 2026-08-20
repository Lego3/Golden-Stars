package com.edvinlinge.hemma.mathstars2

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MandelbrotView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    /** Bilinear filter so scaled "near correct" tiles stay smooth while a sharper tile loads. */
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val srcRect = Rect()
    private val destRect = RectF()

    private val tileCache = sharedCache(context.applicationContext)
    private val bitmaps = HashMap<MandelbrotTiles.TileKey, Bitmap>()

    private var zoom = DEFAULT_ZOOM
    private var offsetX = DEFAULT_OFFSET_X
    private var offsetY = DEFAULT_OFFSET_Y
    private var focusComplexX = DEFAULT_OFFSET_X
    private var focusComplexY = DEFAULT_OFFSET_Y
    private var panSignX = 0
    private var panSignY = 0
    private var zoomSign = 0

    private var colorPalette = Palette.GOLDEN

    /**
     * A coroutine scope is valid only while the view is attached. A View instance can be detached
     * and later reattached, so this must be recreated rather than permanently cancelled once.
     */
    private var renderScope: CoroutineScope? = null
    private var workJob: Job? = null
    private var currentWorkIsPrefetch = false
    private var workEpoch = 0L

    private var isInteracting = false
    private var zoomCallback: ((Double) -> Unit)? = null
    private var renderingStateCallback: ((Boolean) -> Unit)? = null
    private var spinnerVisible = false

    enum class Palette {
        GOLDEN, SILVER, BLUE, GREEN
    }

    init {
        contentDescription = context.getString(R.string.mandelbrot_description_a11y)
        tileCache.onEvicted = ::onTileEvicted
    }

    private val scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            isInteracting = true
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoomAround(detector.focusX, detector.focusY, detector.scaleFactor.toDouble())
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            isInteracting = true
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (scaleGestureDetector.isInProgress) return false
            val unitsPerPixel = unitsPerPixel(zoom, width, height)
            offsetX += distanceX * unitsPerPixel
            offsetY += distanceY * unitsPerPixel
            panSignX = signOf(distanceX)
            panSignY = signOf(distanceY)
            invalidate()
            requestPreviewRender()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            zoomAround(e.x, e.y, DOUBLE_TAP_ZOOM_FACTOR)
            return true
        }
    })

    fun setColorPalette(palette: Palette) {
        if (palette == colorPalette) return
        colorPalette = palette
        invalidate()
        requestFullRender()
    }

    fun setOnZoomChangedListener(callback: (Double) -> Unit) {
        zoomCallback = callback
        callback(zoom)
    }

    fun setOnRenderingStateChangedListener(callback: (Boolean) -> Unit) {
        renderingStateCallback = callback
    }

    fun resetZoomAndPan() {
        zoom = DEFAULT_ZOOM
        offsetX = DEFAULT_OFFSET_X
        offsetY = DEFAULT_OFFSET_Y
        focusComplexX = DEFAULT_OFFSET_X
        focusComplexY = DEFAULT_OFFSET_Y
        panSignX = 0
        panSignY = 0
        zoomSign = 0
        zoomCallback?.invoke(zoom)
        invalidate()
        requestFullRender()
    }

    /**
     * Scales by [factor] while keeping the complex number under the given screen point fixed, so
     * a pinch zooms towards the fingers rather than towards the middle of the view.
     */
    private fun zoomAround(focusX: Float, focusY: Float, factor: Double) {
        val target = MandelbrotMath.clampedZoom(zoom, factor, MIN_ZOOM, MAX_ZOOM)
        if (target == zoom) return

        focusComplexX = MandelbrotMath.complexXAtScreen(focusX, offsetX, zoom, width, height)
        focusComplexY = MandelbrotMath.complexYAtScreen(focusY, offsetY, zoom, width, height)
        zoomSign = if (target > zoom) 1 else -1

        val oldZoom = zoom
        val (newOffsetX, newOffsetY) = MandelbrotMath.offsetAfterZoomChange(
            focusX = focusX,
            focusY = focusY,
            oldZoom = oldZoom,
            newZoom = target,
            offsetX = offsetX,
            offsetY = offsetY,
            viewWidth = width,
            viewHeight = height,
        )
        zoom = target
        offsetX = newOffsetX
        offsetY = newOffsetY

        zoomCallback?.invoke(zoom)
        invalidate()
        requestPreviewRender()
    }

    /**
     * Size of one pixel in the complex plane. Derived from the shorter view edge so pixels stay
     * square and the set is not stretched by the screen's aspect ratio.
     */
    private fun unitsPerPixel(zoomLevel: Double, viewWidth: Int, viewHeight: Int): Double =
        MandelbrotMath.unitsPerPixel(zoomLevel, viewWidth, viewHeight)

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            if (isInteracting) {
                isInteracting = false
                if (!visibleTilesComplete()) {
                    requestFullRender()
                } else {
                    ensureWorkScheduled()
                }
            }
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                performClick()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        recycleBitmaps()
        requestFullRender()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)
        if (width <= 0 || height <= 0) return

        val draws = MandelbrotTiles.composeDrawList(
            zoom = zoom,
            offsetX = offsetX,
            offsetY = offsetY,
            viewWidth = width,
            viewHeight = height,
            tilePixelSize = MandelbrotTiles.tilePixelSize(width, height),
            paletteOrdinal = colorPalette.ordinal,
            available = tileCache.keys,
        )
        for (draw in draws) {
            val bmp = bitmapFor(draw.key) ?: continue
            srcRect.set(draw.src.left, draw.src.top, draw.src.right, draw.src.bottom)
            destRect.set(draw.dest.left, draw.dest.top, draw.dest.right, draw.dest.bottom)
            canvas.drawBitmap(bmp, srcRect, destRect, bitmapPaint)
        }
    }

    /**
     * Renders a low resolution preview, unless work is already running. Skipping rather than
     * restarting stops a continuous gesture from cancelling the render it just started.
     */
    private fun requestPreviewRender() {
        ensureWorkScheduled(cancelPrefetch = true)
    }

    private fun requestFullRender() {
        ensureWorkScheduled(cancelPrefetch = true)
    }

    private fun ensureWorkScheduled(cancelPrefetch: Boolean = false) {
        val scope = renderScope ?: return
        if (width <= 0 || height <= 0) return
        if (workJob?.isActive == true) {
            if (cancelPrefetch && currentWorkIsPrefetch) {
                workEpoch++
                workJob?.cancel()
            } else {
                return
            }
        }
        val epoch = workEpoch
        workJob = scope.launch {
            try {
                processQueue(epoch)
            } finally {
                if (epoch == workEpoch) {
                    currentWorkIsPrefetch = false
                    // A gesture can land in the window between nextWorkItem() returning null
                    // and this job completing; without a reschedule that frame would stick.
                    if (nextWorkItem() != null) {
                        ensureWorkScheduled()
                    } else {
                        updateRenderingState(forceIdle = true)
                    }
                }
            }
        }
    }

    private suspend fun processQueue(epoch: Long) {
        while (currentCoroutineContext().isActive && epoch == workEpoch) {
            val item = nextWorkItem() ?: break
            currentWorkIsPrefetch = item.prefetch
            updateRenderingState()
            renderWork(item)
        }
    }

    private fun nextWorkItem(): WorkItem? {
        val viewWidth = width
        val viewHeight = height
        if (viewWidth <= 0 || viewHeight <= 0) return null
        val plan = MandelbrotTiles.renderPlan(
            zoom = zoom,
            offsetX = offsetX,
            offsetY = offsetY,
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            tilePixelSize = MandelbrotTiles.tilePixelSize(viewWidth, viewHeight),
            paletteOrdinal = colorPalette.ordinal,
            panSignX = panSignX,
            panSignY = panSignY,
            zoomSign = zoomSign,
            focusX = focusComplexX,
            focusY = focusComplexY,
            minZoom = MIN_ZOOM,
            maxZoom = MAX_ZOOM,
            isCached = { tileCache.contains(it) },
        )
        if (isInteracting) {
            if (plan.visiblePreview.isNotEmpty()) {
                return WorkItem(plan.visiblePreview.take(MAX_PREVIEW_BATCH), preview = true, prefetch = false)
            }
            return null
        }
        if (plan.visibleFull.isNotEmpty()) {
            return WorkItem(
                plan.visibleFull.take(MandelbrotTiles.MAX_VISIBLE_BATCH),
                preview = false,
                prefetch = false,
            )
        }
        if (plan.prefetch.isNotEmpty() && tileCache.memoryBytes < tileCache.maxMemoryBytes) {
            return WorkItem(listOf(plan.prefetch.first()), preview = false, prefetch = true)
        }
        return null
    }

    private suspend fun renderWork(item: WorkItem) {
        val missing = ArrayList<MandelbrotTiles.TileKey>(item.keys.size)
        for (key in item.keys) {
            if (tileCache.contains(key) || tileCache.contains(key.copy(preview = false))) {
                continue
            }
            val fromDisk = withContext(Dispatchers.IO) { tileCache.loadFromDisk(key) }
            if (fromDisk != null && fromDisk.pixels.size == MandelbrotTiles.pixelSize(key) *
                MandelbrotTiles.pixelSize(key)
            ) {
                tileCache.put(key, fromDisk.pixels, fromDisk.preview, visibleCacheKeys())
                invalidate()
                continue
            }
            missing += key
        }
        if (missing.isEmpty()) {
            invalidate()
            return
        }
        computeTiles(missing, item.preview)
    }

    private suspend fun computeTiles(keys: List<MandelbrotTiles.TileKey>, preview: Boolean) {
        if (keys.isEmpty()) return
        val bbox = MandelbrotTiles.bboxOf(keys) ?: return
        val dense = bbox.tileCount <= keys.size * 2L
        if (!dense || keys.size == 1) {
            for (key in keys) {
                currentCoroutineContext().ensureActive()
                computeBBox(
                    range = MandelbrotTiles.TileRange(key.tileX, key.tileX, key.tileY, key.tileY),
                    zoomStep = key.zoomStep,
                    tilePixelSize = key.tilePixelSize,
                    preview = preview,
                    palette = colorPalette,
                )
            }
            return
        }
        computeBBox(
            range = bbox,
            zoomStep = keys.first().zoomStep,
            tilePixelSize = keys.first().tilePixelSize,
            preview = preview,
            palette = colorPalette,
        )
    }

    private suspend fun computeBBox(
        range: MandelbrotTiles.TileRange,
        zoomStep: Int,
        tilePixelSize: Int,
        preview: Boolean,
        palette: Palette,
    ) {
        val viewWidth = width
        val viewHeight = height
        if (viewWidth <= 0 || viewHeight <= 0) return

        val downscale = if (preview) MandelbrotTiles.PREVIEW_DOWNSCALE else 1
        val tilesX = (range.x1 - range.x0 + 1L).toInt().coerceAtLeast(1)
        val tilesY = (range.y1 - range.y0 + 1L).toInt().coerceAtLeast(1)
        val outSize = if (preview) {
            (tilePixelSize / downscale).coerceAtLeast(1)
        } else {
            tilePixelSize
        }
        val renderWidth = tilesX * outSize
        val renderHeight = tilesY * outSize
        val world = MandelbrotTiles.tileWorldSize(zoomStep, tilePixelSize, viewWidth, viewHeight)
        val sampleStep = (world / tilePixelSize) * downscale
        val xMin = range.x0 * world
        val yMin = range.y0 * world
        val maxIterations = MandelbrotMath.iterationsFor(MandelbrotTiles.discreteZoom(zoomStep))
        val pixels = IntArray(renderWidth * renderHeight)

        withContext(Dispatchers.Default) {
            computeMandelbrot(
                pixels = pixels,
                renderWidth = renderWidth,
                renderHeight = renderHeight,
                xMin = xMin,
                yMin = yMin,
                sampleStep = sampleStep,
                maxIterations = maxIterations,
                palette = palette,
            )
        }
        currentCoroutineContext().ensureActive()

        val minEdge = MandelbrotTiles.viewMinEdge(viewWidth, viewHeight)
        var ty = range.y0
        var row = 0
        while (ty <= range.y1) {
            var tx = range.x0
            var col = 0
            while (tx <= range.x1) {
                val key = MandelbrotTiles.TileKey(
                    zoomStep = zoomStep,
                    tileX = tx,
                    tileY = ty,
                    paletteOrdinal = palette.ordinal,
                    tilePixelSize = tilePixelSize,
                    viewMinEdge = minEdge,
                    preview = preview,
                )
                val fullKey = key.copy(preview = false)
                if (preview && tileCache.contains(fullKey)) {
                    col++
                    tx++
                    continue
                }
                val tilePixels = IntArray(outSize * outSize)
                copySubgrid(
                    source = pixels,
                    sourceWidth = renderWidth,
                    srcX = col * outSize,
                    srcY = row * outSize,
                    size = outSize,
                    dest = tilePixels,
                )
                tileCache.put(key, tilePixels, preview, visibleCacheKeys())
                if (!preview) {
                    val toSave = tilePixels
                    renderScope?.launch(Dispatchers.IO) {
                        tileCache.saveToDisk(key, toSave)
                    }
                }
                col++
                tx++
            }
            row++
            ty++
        }
        invalidate()
    }

    private suspend fun computeMandelbrot(
        pixels: IntArray,
        renderWidth: Int,
        renderHeight: Int,
        xMin: Double,
        yMin: Double,
        sampleStep: Double,
        maxIterations: Int,
        palette: Palette,
    ) = coroutineScope {
        val cores = Runtime.getRuntime().availableProcessors()
        val rowsPerChunk = (renderHeight / cores).coerceAtLeast(1)

        (0 until renderHeight step rowsPerChunk).map { startRow ->
            async {
                val endRow = (startRow + rowsPerChunk).coerceAtMost(renderHeight)
                for (y in startRow until endRow) {
                    if (!isActive) return@async
                    val ci = yMin + y * sampleStep
                    var index = y * renderWidth
                    for (x in 0 until renderWidth) {
                        val cr = xMin + x * sampleStep
                        val iteration = MandelbrotMath.escapeIterations(cr, ci, maxIterations)
                        pixels[index++] = if (iteration == maxIterations) {
                            Color.BLACK
                        } else {
                            colorFor(iteration, maxIterations, palette)
                        }
                    }
                }
            }
        }.awaitAll()
    }

    private fun colorFor(iterations: Int, maxIterations: Int, palette: Palette): Int {
        val t = iterations.toFloat() / maxIterations
        return when (palette) {
            Palette.GOLDEN -> Color.HSVToColor(floatArrayOf(45f, 0.8f, (t * 1.5f).coerceAtMost(1f)))
            Palette.SILVER -> Color.HSVToColor(floatArrayOf(0f, 0f, t))
            Palette.BLUE -> Color.HSVToColor(floatArrayOf(200f, 0.7f, t))
            Palette.GREEN -> Color.HSVToColor(floatArrayOf(120f, 0.7f, t))
        }
    }

    private fun copySubgrid(
        source: IntArray,
        sourceWidth: Int,
        srcX: Int,
        srcY: Int,
        size: Int,
        dest: IntArray,
    ) {
        var dst = 0
        for (y in 0 until size) {
            val srcRow = (srcY + y) * sourceWidth + srcX
            source.copyInto(dest, dst, srcRow, srcRow + size)
            dst += size
        }
    }

    private fun bitmapFor(key: MandelbrotTiles.TileKey): Bitmap? {
        bitmaps[key]?.let { bmp -> if (!bmp.isRecycled) return bmp }
        val entry = tileCache.get(key) ?: return null
        installBitmap(key, entry.pixels)
        return bitmaps[key]
    }

    private fun installBitmap(key: MandelbrotTiles.TileKey, pixels: IntArray) {
        val size = MandelbrotTiles.pixelSize(key)
        if (pixels.size != size * size) return
        val existing = bitmaps[key]
        val bmp = if (existing != null && !existing.isRecycled && existing.width == size && existing.height == size) {
            existing
        } else {
            existing?.recycle()
            createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmaps[key] = it }
        }
        bmp.setPixels(pixels, 0, size, 0, 0, size, size)
    }

    private fun onTileEvicted(key: MandelbrotTiles.TileKey) {
        bitmaps.remove(key)?.let { bmp ->
            if (!bmp.isRecycled) bmp.recycle()
        }
    }

    private fun recycleBitmaps() {
        for (bmp in bitmaps.values) {
            if (!bmp.isRecycled) bmp.recycle()
        }
        bitmaps.clear()
    }

    private fun visibleCacheKeys(): Set<MandelbrotTiles.TileKey> {
        if (width <= 0 || height <= 0) return emptySet()
        val tilePx = MandelbrotTiles.tilePixelSize(width, height)
        val minEdge = MandelbrotTiles.viewMinEdge(width, height)
        val step = MandelbrotTiles.zoomStep(zoom)
        val range = MandelbrotTiles.visibleTileRange(
            offsetX, offsetY, zoom, width, height, step, tilePx,
        )
        val keys = HashSet<MandelbrotTiles.TileKey>(range.tileCount.toInt().coerceAtLeast(0) * 2)
        range.forEach { x, y ->
            keys += MandelbrotTiles.TileKey(step, x, y, colorPalette.ordinal, tilePx, minEdge, false)
            keys += MandelbrotTiles.TileKey(step, x, y, colorPalette.ordinal, tilePx, minEdge, true)
        }
        return keys
    }

    private fun visibleTilesComplete(): Boolean {
        if (width <= 0 || height <= 0) return false
        return MandelbrotTiles.visibleTilesComplete(
            zoom = zoom,
            offsetX = offsetX,
            offsetY = offsetY,
            viewWidth = width,
            viewHeight = height,
            tilePixelSize = MandelbrotTiles.tilePixelSize(width, height),
            paletteOrdinal = colorPalette.ordinal,
            isCached = { tileCache.contains(it) },
        )
    }

    private fun updateRenderingState(forceIdle: Boolean = false) {
        val busy = !forceIdle && workJob?.isActive == true && !currentWorkIsPrefetch
        if (busy == spinnerVisible) return
        spinnerVisible = busy
        renderingStateCallback?.invoke(busy)
    }

    private fun signOf(value: Float): Int = when {
        value > 0f -> 1
        value < 0f -> -1
        else -> 0
    }

    // The palette is owned by MandelbrotActivity, which reapplies it before this state is
    // restored, so only the viewport is saved here.
    override fun onSaveInstanceState(): Parcelable {
        val savedState = SavedState(super.onSaveInstanceState())
        savedState.zoom = zoom
        savedState.offsetX = offsetX
        savedState.offsetY = offsetY
        return savedState
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is SavedState) {
            super.onRestoreInstanceState(state.superState)
            zoom = MandelbrotMath.coercedZoom(state.zoom, MIN_ZOOM, MAX_ZOOM)
            offsetX = state.offsetX
            offsetY = state.offsetY
            focusComplexX = offsetX
            focusComplexY = offsetY
            zoomCallback?.invoke(zoom)
            workEpoch++
            requestFullRender()
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    internal class SavedState : BaseSavedState {
        var zoom: Double = 1.0
        var offsetX: Double = -0.5
        var offsetY: Double = 0.0

        constructor(superState: Parcelable?) : super(superState)

        constructor(source: Parcel) : super(source) {
            zoom = source.readDouble()
            offsetX = source.readDouble()
            offsetY = source.readDouble()
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeDouble(zoom)
            out.writeDouble(offsetX)
            out.writeDouble(offsetY)
        }

        companion object {
            @JvmField
            val CREATOR = object : Parcelable.Creator<SavedState> {
                override fun createFromParcel(source: Parcel): SavedState = SavedState(source)

                override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        tileCache.onEvicted = ::onTileEvicted
        renderScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        // A palette or viewport change may have happened while detached, and cached tiles from a
        // previous visit can paint immediately. Always refresh when an existing view is reattached.
        requestFullRender()
    }

    override fun onDetachedFromWindow() {
        workEpoch++
        workJob?.cancel()
        workJob = null
        renderScope?.cancel()
        renderScope = null
        currentWorkIsPrefetch = false
        tileCache.onEvicted = null
        recycleBitmaps()
        if (spinnerVisible) {
            spinnerVisible = false
            renderingStateCallback?.invoke(false)
        }
        super.onDetachedFromWindow()
    }

    private data class WorkItem(
        val keys: List<MandelbrotTiles.TileKey>,
        val preview: Boolean,
        val prefetch: Boolean,
    )

    private companion object {
        const val DEFAULT_ZOOM = 1.0
        const val DEFAULT_OFFSET_X = -0.5
        const val DEFAULT_OFFSET_Y = 0.0
        const val MIN_ZOOM = 0.5

        /** Double precision runs out around here, so zooming further only adds noise. */
        const val MAX_ZOOM = 1.0e13
        const val DOUBLE_TAP_ZOOM_FACTOR = 2.0
        const val MAX_PREVIEW_BATCH = 8
        const val MIN_MEMORY_BYTES = 16L * 1024L * 1024L
        const val MAX_MEMORY_BYTES = 64L * 1024L * 1024L
        const val DISK_CACHE_BYTES = 128L * 1024L * 1024L

        private val cacheLock = Any()
        private var processCache: MandelbrotTileCache? = null

        fun sharedCache(context: Context): MandelbrotTileCache {
            synchronized(cacheLock) {
                processCache?.let { return it }
                val maxMemory = (Runtime.getRuntime().maxMemory() / 6L)
                    .coerceIn(MIN_MEMORY_BYTES, MAX_MEMORY_BYTES)
                return MandelbrotTileCache(
                    maxMemoryBytes = maxMemory,
                    diskDir = File(context.cacheDir, "mandelbrot_tiles"),
                    maxDiskBytes = DISK_CACHE_BYTES,
                ).also { processCache = it }
            }
        }
    }
}
