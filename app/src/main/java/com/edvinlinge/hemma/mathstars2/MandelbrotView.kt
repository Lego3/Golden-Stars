package com.edvinlinge.hemma.mathstars2

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
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

    private var colorPalette = FractalPalette.GOLDEN

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
    private var renderingStateCallback: ((Boolean, Int, Int) -> Unit)? = null
    private var spinnerVisible = false
    private var lastProgressFinished = -1
    private var lastProgressQueued = -1
    private var queuedVisibleKeys: Set<MandelbrotTiles.TileKey> = emptySet()

    init {
        contentDescription = context.getString(R.string.mandelbrot_description_a11y)
        tileCache.onEvicted = ::onTileEvicted
        bitmapPaint.colorFilter = colorFilterFor(FractalPalette.GOLDEN)
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
            updateRenderingState()
            requestPreviewRender()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            zoomAround(e.x, e.y, DOUBLE_TAP_ZOOM_FACTOR)
            return true
        }
    })

    fun setColorPalette(palette: FractalPalette) {
        if (palette == colorPalette) return
        colorPalette = palette
        bitmapPaint.colorFilter = colorFilterFor(palette)
        invalidate()
    }

    fun setOnZoomChangedListener(callback: (Double) -> Unit) {
        zoomCallback = callback
        callback(zoom)
    }

    fun setOnRenderingStateChangedListener(callback: (Boolean, Int, Int) -> Unit) {
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
        updateRenderingState()
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
        workEpoch++
        workJob?.cancel()
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
        when (
            MandelbrotTiles.activeWorkAction(
                workActive = workJob?.isActive == true,
                cancelPrefetch = cancelPrefetch,
                workIsPrefetch = currentWorkIsPrefetch,
            )
        ) {
            MandelbrotTiles.ActiveWorkAction.Skip -> return
            MandelbrotTiles.ActiveWorkAction.CancelPrefetchAndLaunch -> {
                workEpoch++
                workJob?.cancel()
            }
            MandelbrotTiles.ActiveWorkAction.Launch -> Unit
        }
        val epoch = workEpoch
        workJob = scope.launch {
            try {
                processQueue(epoch)
            } finally {
                if (epoch == workEpoch) {
                    currentWorkIsPrefetch = false
                    when (
                        MandelbrotTiles.postWorkAction(
                            epochMatches = true,
                            hasPendingWork = nextWorkItem() != null,
                        )
                    ) {
                        MandelbrotTiles.PostWorkAction.Reschedule -> ensureWorkScheduled()
                        MandelbrotTiles.PostWorkAction.ForceIdle ->
                            updateRenderingState(forceIdle = true)
                        MandelbrotTiles.PostWorkAction.None -> Unit
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
            panSignX = panSignX,
            panSignY = panSignY,
            zoomSign = zoomSign,
            focusX = focusComplexX,
            focusY = focusComplexY,
            minZoom = MIN_ZOOM,
            maxZoom = MAX_ZOOM,
            isCached = { tileCache.contains(it) },
        )
        val selection = MandelbrotTiles.selectNextWork(
            plan = plan,
            isInteracting = isInteracting,
            viewportCovered = visibleViewportCovered(),
            memoryBytes = tileCache.memoryBytes,
            maxMemoryBytes = tileCache.maxMemoryBytes,
        )
        return selection?.let { WorkItem(it.keys, it.preview, it.prefetch) }
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
                updateRenderingState()
                continue
            }
            missing += key
        }
        if (missing.isEmpty()) {
            invalidate()
            updateRenderingState()
            return
        }
        computeTiles(missing, item.preview)
    }

    private suspend fun computeTiles(keys: List<MandelbrotTiles.TileKey>, preview: Boolean) {
        if (keys.isEmpty()) return
        val groups = keys.groupBy { it.zoomStep to it.tilePixelSize }
        for (group in groups.values) {
            currentCoroutineContext().ensureActive()
            computeTileGroup(group, preview)
        }
    }

    private suspend fun computeTileGroup(keys: List<MandelbrotTiles.TileKey>, preview: Boolean) {
        if (keys.isEmpty()) return
        val bbox = MandelbrotTiles.bboxOf(keys) ?: return
        val dense = MandelbrotTiles.isDenseTileBatch(keys)
        if (dense) {
            computeBBox(
                range = bbox,
                zoomStep = keys.first().zoomStep,
                tilePixelSize = keys.first().tilePixelSize,
                preview = preview,
            )
            return
        }
        val viewWidth = width
        val viewHeight = height
        coroutineScope {
            val rendered = keys.map { key ->
                async(Dispatchers.Default) {
                    key to renderRangePixels(
                        range = MandelbrotTiles.TileRange(key.tileX, key.tileX, key.tileY, key.tileY),
                        zoomStep = key.zoomStep,
                        tilePixelSize = key.tilePixelSize,
                        preview = preview,
                        viewWidth = viewWidth,
                        viewHeight = viewHeight,
                        parallelRows = false,
                    )
                }
            }.awaitAll()
            currentCoroutineContext().ensureActive()
            for ((key, pixels) in rendered) {
                if (pixels.isEmpty()) continue
                installRenderedRange(
                    range = MandelbrotTiles.TileRange(key.tileX, key.tileX, key.tileY, key.tileY),
                    zoomStep = key.zoomStep,
                    tilePixelSize = key.tilePixelSize,
                    preview = preview,
                    pixels = pixels,
                    renderViewWidth = viewWidth,
                    renderViewHeight = viewHeight,
                )
                updateRenderingState()
            }
        }
        invalidate()
        updateRenderingState()
    }

    private suspend fun computeBBox(
        range: MandelbrotTiles.TileRange,
        zoomStep: Int,
        tilePixelSize: Int,
        preview: Boolean,
    ) {
        val viewWidth = width
        val viewHeight = height
        if (viewWidth <= 0 || viewHeight <= 0) return
        val pixels = withContext(Dispatchers.Default) {
            renderRangePixels(
                range = range,
                zoomStep = zoomStep,
                tilePixelSize = tilePixelSize,
                preview = preview,
                viewWidth = viewWidth,
                viewHeight = viewHeight,
                parallelRows = true,
            )
        }
        currentCoroutineContext().ensureActive()
        if (pixels.isEmpty()) return
        installRenderedRange(
            range = range,
            zoomStep = zoomStep,
            tilePixelSize = tilePixelSize,
            preview = preview,
            pixels = pixels,
            renderViewWidth = viewWidth,
            renderViewHeight = viewHeight,
        )
        invalidate()
        updateRenderingState()
    }

    private suspend fun renderRangePixels(
        range: MandelbrotTiles.TileRange,
        zoomStep: Int,
        tilePixelSize: Int,
        preview: Boolean,
        viewWidth: Int,
        viewHeight: Int,
        parallelRows: Boolean,
    ): ByteArray {
        if (viewWidth <= 0 || viewHeight <= 0) return ByteArray(0)
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
        val pixels = ByteArray(renderWidth * renderHeight)
        computeMandelbrot(
            pixels = pixels,
            renderWidth = renderWidth,
            renderHeight = renderHeight,
            xMin = xMin,
            yMin = yMin,
            sampleStep = sampleStep,
            maxIterations = maxIterations,
            parallelRows = parallelRows,
        )
        return pixels
    }

    private fun installRenderedRange(
        range: MandelbrotTiles.TileRange,
        zoomStep: Int,
        tilePixelSize: Int,
        preview: Boolean,
        pixels: ByteArray,
        renderViewWidth: Int,
        renderViewHeight: Int,
    ) {
        val viewWidth = width
        val viewHeight = height
        if (viewWidth <= 0 || viewHeight <= 0) return
        if (
            !MandelbrotTiles.viewGeometryMatches(
                renderViewWidth = renderViewWidth,
                renderViewHeight = renderViewHeight,
                viewWidth = viewWidth,
                viewHeight = viewHeight,
            )
        ) {
            return
        }
        val downscale = if (preview) MandelbrotTiles.PREVIEW_DOWNSCALE else 1
        val outSize = if (preview) {
            (tilePixelSize / downscale).coerceAtLeast(1)
        } else {
            tilePixelSize
        }
        val tilesX = (range.x1 - range.x0 + 1L).toInt().coerceAtLeast(1)
        val renderWidth = tilesX * outSize
        if (pixels.size < renderWidth * ((range.y1 - range.y0 + 1L).toInt().coerceAtLeast(1) * outSize)) {
            return
        }
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
                    tilePixelSize = tilePixelSize,
                    viewMinEdge = minEdge,
                    preview = preview,
                )
                val fullKey = key.copy(preview = false)
                if (tileCache.contains(fullKey) || (!preview && tileCache.contains(key))) {
                    col++
                    tx++
                    continue
                }
                val tilePixels = ByteArray(outSize * outSize)
                MandelbrotTiles.copySubgrid(
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
    }

    private suspend fun computeMandelbrot(
        pixels: ByteArray,
        renderWidth: Int,
        renderHeight: Int,
        xMin: Double,
        yMin: Double,
        sampleStep: Double,
        maxIterations: Int,
        parallelRows: Boolean,
    ) {
        if (renderWidth <= 0 || renderHeight <= 0) return
        if (!parallelRows) {
            fillMandelbrotRows(
                pixels = pixels,
                renderWidth = renderWidth,
                startRow = 0,
                endRow = renderHeight,
                xMin = xMin,
                yMin = yMin,
                sampleStep = sampleStep,
                maxIterations = maxIterations,
            )
            return
        }
        coroutineScope {
            val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
            val chunkCount = (cores * ROW_PARALLELISM_OVERSUB).coerceAtMost(renderHeight).coerceAtLeast(1)
            val rowsPerChunk = (renderHeight + chunkCount - 1) / chunkCount
            (0 until renderHeight step rowsPerChunk).map { startRow ->
                async {
                    fillMandelbrotRows(
                        pixels = pixels,
                        renderWidth = renderWidth,
                        startRow = startRow,
                        endRow = (startRow + rowsPerChunk).coerceAtMost(renderHeight),
                        xMin = xMin,
                        yMin = yMin,
                        sampleStep = sampleStep,
                        maxIterations = maxIterations,
                    )
                }
            }.awaitAll()
        }
    }

    private suspend fun fillMandelbrotRows(
        pixels: ByteArray,
        renderWidth: Int,
        startRow: Int,
        endRow: Int,
        xMin: Double,
        yMin: Double,
        sampleStep: Double,
        maxIterations: Int,
    ) {
        val ctx = currentCoroutineContext()
        for (y in startRow until endRow) {
            ctx.ensureActive()
            val ci = yMin + y * sampleStep
            var index = y * renderWidth
            for (x in 0 until renderWidth) {
                val cr = xMin + x * sampleStep
                val iteration = MandelbrotMath.escapeIterations(cr, ci, maxIterations)
                pixels[index++] = FractalColoring.escapeAlpha(iteration, maxIterations).toByte()
            }
        }
    }

    private fun bitmapFor(key: MandelbrotTiles.TileKey): Bitmap? {
        bitmaps[key]?.let { bmp -> if (!bmp.isRecycled) return bmp }
        val entry = tileCache.get(key) ?: return null
        installBitmap(key, entry.pixels)
        return bitmaps[key]
    }

    private fun installBitmap(key: MandelbrotTiles.TileKey, pixels: ByteArray) {
        val size = MandelbrotTiles.pixelSize(key)
        if (pixels.size != size * size) return
        val existing = bitmaps[key]
        val bmp = if (existing != null && !existing.isRecycled && existing.width == size && existing.height == size) {
            existing
        } else {
            existing?.recycle()
            createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmaps[key] = it }
        }
        val argb = IntArray(pixels.size) { i ->
            FractalColoring.grayArgb(pixels[i].toInt() and 0xFF)
        }
        bmp.setPixels(argb, 0, size, 0, 0, size, size)
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
        return MandelbrotTiles.protectableKeys(
            zoom = zoom,
            offsetX = offsetX,
            offsetY = offsetY,
            viewWidth = width,
            viewHeight = height,
            tilePixelSize = MandelbrotTiles.tilePixelSize(width, height),
        )
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
            isCached = { tileCache.contains(it) },
        )
    }

    private fun visibleViewportCovered(): Boolean {
        if (width <= 0 || height <= 0) return false
        return MandelbrotTiles.visibleViewportCovered(
            zoom = zoom,
            offsetX = offsetX,
            offsetY = offsetY,
            viewWidth = width,
            viewHeight = height,
            tilePixelSize = MandelbrotTiles.tilePixelSize(width, height),
            isCached = { tileCache.contains(it) },
        )
    }

    private fun colorFilterFor(palette: FractalPalette) =
        ColorMatrixColorFilter(ColorMatrix(FractalColoring.colorMatrixValues(palette)))

    private fun updateRenderingState(forceIdle: Boolean = false) {
        val visibleKeys = visibleFullKeys()
        queuedVisibleKeys = MandelbrotTiles.mergeVisibleTileQueue(
            tracked = queuedVisibleKeys,
            visibleKeys = visibleKeys,
            isCached = { tileCache.contains(it) },
        )
        val progress = MandelbrotTiles.visibleTileProgress(
            queuedKeys = queuedVisibleKeys,
            isCached = { tileCache.contains(it) },
        )
        val hud = MandelbrotTiles.spinnerHudState(
            forceIdle = forceIdle,
            workActive = workJob?.isActive == true,
            workIsPrefetch = currentWorkIsPrefetch,
            visibleTilesComplete = visibleTilesComplete(),
            progress = progress,
        )
        if (hud.clearTrackedQueue) {
            queuedVisibleKeys = emptySet()
        }
        if (hud.visible == spinnerVisible &&
            hud.finished == lastProgressFinished &&
            hud.queued == lastProgressQueued
        ) {
            return
        }
        spinnerVisible = hud.visible
        lastProgressFinished = hud.finished
        lastProgressQueued = hud.queued
        renderingStateCallback?.invoke(hud.visible, hud.finished, hud.queued)
    }

    private fun visibleFullKeys(): List<MandelbrotTiles.TileKey> {
        if (width <= 0 || height <= 0) return emptyList()
        return MandelbrotTiles.visibleFullKeys(
            zoom = zoom,
            offsetX = offsetX,
            offsetY = offsetY,
            viewWidth = width,
            viewHeight = height,
            tilePixelSize = MandelbrotTiles.tilePixelSize(width, height),
        )
    }

    private fun signOf(value: Float): Int = when {
        value > 0f -> 1
        value < 0f -> -1
        else -> 0
    }

    // The palette is owned by MandelbrotActivity, which reapplies the colour filter before
    // this state is restored, so only the viewport is saved here.
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
        // A viewport change may have happened while detached, and cached tiles from a previous
        // visit can paint immediately. Always refresh when an existing view is reattached.
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
        queuedVisibleKeys = emptySet()
        if (spinnerVisible || lastProgressQueued != 0) {
            spinnerVisible = false
            lastProgressFinished = 0
            lastProgressQueued = 0
            renderingStateCallback?.invoke(false, 0, 0)
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
        const val ROW_PARALLELISM_OVERSUB = 4
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
