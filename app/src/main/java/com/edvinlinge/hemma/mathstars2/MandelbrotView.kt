package com.edvinlinge.hemma.mathstars2

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withSave
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MandelbrotView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** The image on screen. Always matches the view size. */
    private var bitmap: Bitmap? = null

    /** Scratch bitmap used to upscale a low resolution preview into [bitmap]. */
    private var previewScratch: Bitmap? = null
    private var fullPixels: IntArray? = null
    private var previewPixels: IntArray? = null

    private var zoom = DEFAULT_ZOOM
    private var offsetX = DEFAULT_OFFSET_X
    private var offsetY = DEFAULT_OFFSET_Y

    // The viewport [bitmap] was rendered for. While a new render is in flight the existing image
    // is scaled and shifted to approximate the current viewport, so gestures stay responsive.
    private var bitmapZoom = DEFAULT_ZOOM
    private var bitmapOffsetX = DEFAULT_OFFSET_X
    private var bitmapOffsetY = DEFAULT_OFFSET_Y
    private var hasRenderedOnce = false
    private var bitmapIsPreview = false

    private var colorPalette = Palette.GOLDEN

    /**
     * A coroutine scope is valid only while the view is attached. A View instance can be detached
     * and later reattached, so this must be recreated rather than permanently cancelled once.
     */
    private var renderScope: CoroutineScope? = null
    private var renderJob: Job? = null
    /** Last job that touched the shared pixel buffers; kept across detach so a reattach can join it. */
    private var bufferJob: Job? = null
    private var renderGeneration = 0L

    private var isInteracting = false
    private var zoomCallback: ((Double) -> Unit)? = null
    private var renderingStateCallback: ((Boolean) -> Unit)? = null

    enum class Palette {
        GOLDEN, SILVER, BLUE, GREEN
    }

    init {
        contentDescription = context.getString(R.string.mandelbrot_description_a11y)
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
            val unitsPerPixel = unitsPerPixel(zoom, width, height)
            offsetX += distanceX * unitsPerPixel
            offsetY += distanceY * unitsPerPixel
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
        zoomCallback?.invoke(zoom)
        invalidate()
        requestFullRender()
    }

    /**
     * Scales by [factor] while keeping the complex number under the given screen point fixed, so
     * a pinch zooms towards the fingers rather than towards the middle of the view.
     */
    private fun zoomAround(focusX: Float, focusY: Float, factor: Double) {
        val target = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (target == zoom) return

        val focusRealBefore = screenToComplexX(focusX)
        val focusImaginaryBefore = screenToComplexY(focusY)
        zoom = target
        offsetX += focusRealBefore - screenToComplexX(focusX)
        offsetY += focusImaginaryBefore - screenToComplexY(focusY)

        zoomCallback?.invoke(zoom)
        invalidate()
        requestPreviewRender()
    }

    private fun screenToComplexX(screenX: Float): Double =
        offsetX + (screenX - width / 2.0) * unitsPerPixel(zoom, width, height)

    private fun screenToComplexY(screenY: Float): Double =
        offsetY + (screenY - height / 2.0) * unitsPerPixel(zoom, width, height)

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
                if (needsFullRender()) {
                    requestFullRender()
                }
            }
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                performClick()
            }
        }
        return true
    }

    /** False when the bitmap already holds a full resolution render of the current viewport. */
    private fun needsFullRender(): Boolean =
        !hasRenderedOnce ||
            bitmapIsPreview ||
            zoom != bitmapZoom ||
            offsetX != bitmapOffsetX ||
            offsetY != bitmapOffsetY

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return

        renderGeneration++
        renderJob?.cancel()
        renderJob = null
        bitmap?.recycle()
        previewScratch?.recycle()
        previewScratch = null
        bitmap = createBitmap(w, h, Bitmap.Config.ARGB_8888)
        hasRenderedOnce = false
        requestFullRender()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = bitmap ?: return
        if (!hasRenderedOnce) return

        val imageScale = (zoom / bitmapZoom).toFloat()
        val unitsPerPixel = unitsPerPixel(zoom, width, height)
        val dx = ((bitmapOffsetX - offsetX) / unitsPerPixel).toFloat()
        val dy = ((bitmapOffsetY - offsetY) / unitsPerPixel).toFloat()

        canvas.withSave {
            translate(width / 2f + dx, height / 2f + dy)
            scale(imageScale, imageScale)
            translate(-width / 2f, -height / 2f)
            drawBitmap(bmp, 0f, 0f, paint)
        }
    }

    /**
     * Renders a low resolution preview, unless one is already running. Skipping rather than
     * restarting stops a continuous gesture from cancelling the render it just started.
     */
    private fun requestPreviewRender() {
        if (renderJob?.isActive == true) return
        startRender(preview = true)
    }

    private fun requestFullRender() {
        startRender(preview = false)
    }

    private fun startRender(preview: Boolean) {
        val viewWidth = width
        val viewHeight = height
        if (viewWidth <= 0 || viewHeight <= 0) return
        val scope = renderScope ?: return

        val previousBufferJob = bufferJob
        previousBufferJob?.cancel()

        val generation = ++renderGeneration
        val renderZoom = zoom
        val renderOffsetX = offsetX
        val renderOffsetY = offsetY
        val palette = colorPalette
        val downscale = if (preview) PREVIEW_DOWNSCALE else 1
        val renderWidth = (viewWidth / downscale).coerceAtLeast(1)
        val renderHeight = (viewHeight / downscale).coerceAtLeast(1)
        val maxIterations = MandelbrotMath.iterationsFor(renderZoom)

        val unitsPerPixel = unitsPerPixel(renderZoom, viewWidth, viewHeight)
        val sampleStep = unitsPerPixel * downscale
        val xMin = renderOffsetX - unitsPerPixel * viewWidth / 2.0
        val yMin = renderOffsetY - unitsPerPixel * viewHeight / 2.0

        renderingStateCallback?.invoke(true)

        val job = scope.launch {
            try {
                // Wait for the cancelled render to release the shared pixel buffers. Join under
                // NonCancellable so a newer render cancelling *this* job cannot break the chain:
                // otherwise job C would join cancelled job B while B's predecessor A is still
                // writing, and both would corrupt the same IntArray.
                withContext(NonCancellable) {
                    previousBufferJob?.join()
                }
                ensureActive()

                val pixels = pixelBuffer(preview, renderWidth * renderHeight)
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

                if (generation != renderGeneration) return@launch
                val target = bitmap ?: return@launch
                if (target.width != viewWidth || target.height != viewHeight) return@launch

                if (preview) {
                    val scratch = previewScratchOf(renderWidth, renderHeight)
                    scratch.setPixels(pixels, 0, renderWidth, 0, 0, renderWidth, renderHeight)
                    Canvas(target).drawBitmap(
                        scratch,
                        Rect(0, 0, renderWidth, renderHeight),
                        Rect(0, 0, viewWidth, viewHeight),
                        paint,
                    )
                } else {
                    target.setPixels(pixels, 0, renderWidth, 0, 0, renderWidth, renderHeight)
                }

                // Record what the image now shows, including previews, otherwise onDraw would
                // transform an already up to date image a second time.
                bitmapZoom = renderZoom
                bitmapOffsetX = renderOffsetX
                bitmapOffsetY = renderOffsetY
                bitmapIsPreview = preview
                hasRenderedOnce = true
                invalidate()
            } finally {
                if (generation == renderGeneration) {
                    renderingStateCallback?.invoke(false)
                }
            }
        }
        bufferJob = job
        renderJob = job
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

    private fun pixelBuffer(preview: Boolean, size: Int): IntArray {
        val cached = if (preview) previewPixels else fullPixels
        if (cached != null && cached.size == size) return cached
        return IntArray(size).also {
            if (preview) previewPixels = it else fullPixels = it
        }
    }

    private fun previewScratchOf(width: Int, height: Int): Bitmap {
        val cached = previewScratch
        if (cached != null && cached.width == width && cached.height == height) return cached
        previewScratch?.recycle()
        return createBitmap(width, height, Bitmap.Config.ARGB_8888).also { previewScratch = it }
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
            zoom = state.zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
            offsetX = state.offsetX
            offsetY = state.offsetY
            zoomCallback?.invoke(zoom)
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
        renderScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        // A palette or viewport change may have happened while detached, and a cancelled render
        // may never have populated the bitmap. Always refresh when an existing view is reattached.
        requestFullRender()
    }

    override fun onDetachedFromWindow() {
        // Invalidate the generation before cancellation so the old job cannot update callbacks or
        // a bitmap after a rapid detach/reattach cycle.
        renderGeneration++
        renderJob?.cancel()
        renderJob = null
        renderScope?.cancel()
        renderScope = null
        renderingStateCallback?.invoke(false)
        super.onDetachedFromWindow()
    }

    private companion object {
        const val DEFAULT_ZOOM = 1.0
        const val DEFAULT_OFFSET_X = -0.5
        const val DEFAULT_OFFSET_Y = 0.0
        const val MIN_ZOOM = 0.5

        /** Double precision runs out around here, so zooming further only adds noise. */
        const val MAX_ZOOM = 1.0e13
        const val DOUBLE_TAP_ZOOM_FACTOR = 2.0

        /** Linear downscale factor for the preview rendered during gestures. */
        const val PREVIEW_DOWNSCALE = 4
    }
}
