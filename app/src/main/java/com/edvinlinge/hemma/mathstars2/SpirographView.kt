package com.edvinlinge.hemma.mathstars2

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.graphics.withSave
import kotlin.math.min

class SpirographView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var params = SpirographMath.normalized(
        SpirographMath.DEFAULT_FIXED,
        SpirographMath.DEFAULT_ROLLING,
        SpirographMath.DEFAULT_PEN,
        SpirographMath.DEFAULT_INSIDE,
    )

    /** The complete figure. */
    private val fullPath = Path()

    /** The portion revealed so far, rebuilt as the reveal animation progresses. */
    private val revealedPath = Path()
    private val pathMeasure = PathMeasure()
    private var pathLength = 0f

    /** Fraction of the figure still hidden: 1 is nothing drawn, 0 is complete. */
    private var currentPhase = 1f
    private var isRevealing = false
    private var animator: ValueAnimator? = null

    private var drawColor = Color.YELLOW
    private var strokeWidth = 8f
    private var animationDuration = 5000L
    private var instantRender = false

    private var zoom = DEFAULT_ZOOM
    private var offsetX = 0f
    private var offsetY = 0f
    private var zoomCallback: ((Float) -> Unit)? = null

    private val scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoomAround(detector.focusX, detector.focusY, detector.scaleFactor)
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (scaleGestureDetector.isInProgress) return false
            offsetX -= distanceX
            offsetY -= distanceY
            invalidate()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            zoomAround(e.x, e.y, DOUBLE_TAP_ZOOM_FACTOR)
            return true
        }
    })

    init {
        paint.style = Paint.Style.STROKE
        paint.color = drawColor
        paint.strokeWidth = strokeWidth
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeCap = Paint.Cap.ROUND
        updateContentDescription()
    }

    fun setOnZoomChangedListener(callback: (Float) -> Unit) {
        this.zoomCallback = callback
        callback(zoom)
    }

    fun resetZoomAndPan() {
        zoom = DEFAULT_ZOOM
        offsetX = 0f
        offsetY = 0f
        zoomCallback?.invoke(zoom)
        invalidate()
    }

    /**
     * Scales by [factor] while keeping the drawing under the given screen point in place, so a
     * pinch zooms towards the fingers instead of towards the middle of the view.
     */
    private fun zoomAround(focusX: Float, focusY: Float, factor: Float) {
        val target = DrawViewMath.clampedZoom(zoom, factor, MIN_ZOOM, MAX_ZOOM)
        if (target == zoom) return

        val (newOffsetX, newOffsetY) = DrawViewMath.offsetAfterZoomChange(
            focusX = focusX,
            focusY = focusY,
            oldZoom = zoom,
            newZoom = target,
            offsetX = offsetX,
            offsetY = offsetY,
            viewWidth = width,
            viewHeight = height,
        )
        offsetX = newOffsetX
        offsetY = newOffsetY
        zoom = target

        zoomCallback?.invoke(zoom)
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildGeometry()
        startAnimation()
    }

    /** Sets the geometry before the view is laid out; the path is built once a size is known. */
    fun setParams(fixedRadius: Int, rollingRadius: Int, penOffset: Int, inside: Boolean) {
        params = SpirographMath.normalized(fixedRadius, rollingRadius, penOffset, inside)
        updateContentDescription()
    }

    /**
     * Changes the geometry of an already laid out view. Pass `false` for [animate] while a slider
     * is being dragged, so the figure updates live without restarting the reveal on every step.
     */
    fun setGeometry(
        fixedRadius: Int,
        rollingRadius: Int,
        penOffset: Int,
        inside: Boolean,
        animate: Boolean,
    ) {
        params = SpirographMath.normalized(fixedRadius, rollingRadius, penOffset, inside)
        updateContentDescription()
        rebuildGeometry()

        if (animate && !instantRender) {
            replay()
        } else {
            showComplete()
        }
    }

    fun replay() {
        currentPhase = 1f
        startAnimation()
    }

    private fun updateContentDescription() {
        contentDescription = context.getString(
            R.string.spirograph_description_a11y,
            params.fixedRadius,
            params.rollingRadius,
            params.penOffset,
        )
    }

    private fun rebuildGeometry() {
        fullPath.reset()
        revealedPath.reset()
        pathLength = 0f

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        if (viewWidth <= 0f || viewHeight <= 0f) return

        val radius = min(viewWidth, viewHeight) * RADIUS_FRACTION
        val screenPoints = SpirographMath.fitToView(
            points = SpirographMath.curvePoints(params),
            centerX = viewWidth / 2f,
            centerY = viewHeight / 2f,
            radius = radius,
        )
        if (screenPoints.size < 2) return

        fullPath.moveTo(screenPoints.first().first, screenPoints.first().second)
        for (index in 1 until screenPoints.size) {
            val (x, y) = screenPoints[index]
            fullPath.lineTo(x, y)
        }

        pathMeasure.setPath(fullPath, false)
        pathLength = pathMeasure.length
    }

    fun setDrawColor(color: Int) {
        this.drawColor = color
        paint.color = color
        invalidate()
    }

    fun setStrokeWidth(width: Float) {
        this.strokeWidth = width
        paint.strokeWidth = width
        invalidate()
    }

    fun setInstant(instant: Boolean) {
        this.instantRender = instant
        if (instantRender) {
            showComplete()
        }
    }

    fun setAnimationSpeed(speedMultiplier: Float) {
        animationDuration = DrawViewMath.animationDurationMs(speedMultiplier) ?: return
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        canvas.withSave {
            translate(width / 2f + offsetX, height / 2f + offsetY)
            scale(zoom, zoom)
            translate(-width / 2f, -height / 2f)
            drawPath(if (isRevealing) revealedPath else fullPath, paint)
        }
    }

    private fun startAnimation() {
        animator?.cancel()
        if (currentPhase <= 0f || instantRender) {
            showComplete()
            return
        }

        isRevealing = true
        setPhase(currentPhase)

        animator = ValueAnimator.ofFloat(currentPhase, 0f).apply {
            duration = DrawViewMath.remainingAnimationDurationMs(currentPhase, animationDuration)
            addUpdateListener { animation -> setPhase(animation.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    isRevealing = false
                    invalidate()
                }
            })
            start()
        }
    }

    private fun showComplete() {
        animator?.cancel()
        currentPhase = 0f
        isRevealing = false
        invalidate()
    }

    /**
     * Reveals the figure up to `1 - phase` of its length. Extracting a path segment avoids a
     * per-frame path-effect allocation and keeps the draw on the hardware canvas.
     */
    fun setPhase(phase: Float) {
        currentPhase = DrawViewMath.coercedPhase(phase)
        revealedPath.reset()
        val segmentLength = DrawViewMath.revealedSegmentLength(pathLength, currentPhase)
        if (segmentLength > 0f) {
            pathMeasure.getSegment(0f, segmentLength, revealedPath, true)
        }
        invalidate()
    }

    fun getDetailsHtml(context: Context): String {
        val details = spirographDetailsParagraphs(params)
        val parts = mutableListOf<String>()

        parts.add(
            context.getString(
                if (details.inside) R.string.spirograph_details_hypo else R.string.spirograph_details_epi,
                details.rollingRadius,
                details.fixedRadius,
                details.penOffset,
            ),
        )

        when (details.curveKind) {
            SpirographCurveKind.Circle ->
                parts.add(context.getString(R.string.spirograph_details_circle))
            SpirographCurveKind.Hypocycloid ->
                parts.add(context.getString(R.string.spirograph_details_hypocycloid))
            SpirographCurveKind.Epicycloid ->
                parts.add(context.getString(R.string.spirograph_details_epicycloid))
            SpirographCurveKind.General -> Unit
        }

        parts.add(
            context.getString(
                R.string.spirograph_details_close,
                details.periodTurns,
                details.fixedRadius,
                details.rollingRadius,
                details.gcd,
            ),
        )
        parts.add(context.getString(R.string.spirograph_details_lobes, details.lobes))

        return parts.joinToString("<br><br>")
    }

    // Only the viewport and animation progress are saved here. Geometry and styling are owned
    // by SpirographActivity, which reapplies them before this state is restored.
    override fun onSaveInstanceState(): Parcelable {
        val savedState = SavedState(super.onSaveInstanceState())
        savedState.phase = this.currentPhase
        savedState.zoom = this.zoom
        savedState.offsetX = this.offsetX
        savedState.offsetY = this.offsetY
        return savedState
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is SavedState) {
            super.onRestoreInstanceState(state.superState)
            this.currentPhase = state.phase
            this.zoom = state.zoom
            this.offsetX = state.offsetX
            this.offsetY = state.offsetY
            zoomCallback?.invoke(zoom)
            // onSizeChanged runs before state restore and starts a fresh reveal at phase 1.
            resumeAnimationFromRestoredPhase()
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    /** Re-syncs the reveal animator after [onRestoreInstanceState] overrides an eager [startAnimation]. */
    private fun resumeAnimationFromRestoredPhase() {
        animator?.cancel()
        when (
            DrawViewMath.revealRestoreDecision(
                currentPhase = currentPhase,
                instantRender = instantRender,
                pathLength = pathLength,
                animationDurationMs = animationDuration,
            ).action
        ) {
            DrawViewMath.RevealRestoreAction.ShowComplete -> {
                showComplete()
                return
            }
            DrawViewMath.RevealRestoreAction.Skip -> return
            DrawViewMath.RevealRestoreAction.Resume -> Unit
        }

        isRevealing = true
        setPhase(currentPhase)

        animator = ValueAnimator.ofFloat(currentPhase, 0f).apply {
            duration = DrawViewMath.remainingAnimationDurationMs(currentPhase, animationDuration)
            addUpdateListener { animation -> setPhase(animation.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    isRevealing = false
                    invalidate()
                }
            })
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    internal class SavedState : BaseSavedState {
        var phase: Float = 1f
        var zoom: Float = 1f
        var offsetX: Float = 0f
        var offsetY: Float = 0f

        constructor(superState: Parcelable?) : super(superState)

        constructor(source: Parcel) : super(source) {
            phase = source.readFloat()
            zoom = source.readFloat()
            offsetX = source.readFloat()
            offsetY = source.readFloat()
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeFloat(phase)
            out.writeFloat(zoom)
            out.writeFloat(offsetX)
            out.writeFloat(offsetY)
        }

        companion object {
            @JvmField
            val CREATOR = object : Parcelable.Creator<SavedState> {
                override fun createFromParcel(source: Parcel): SavedState = SavedState(source)

                override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
            }
        }
    }

    private companion object {
        const val DEFAULT_ZOOM = 1.0f
        const val MIN_ZOOM = 0.5f
        const val MAX_ZOOM = 10.0f
        const val DOUBLE_TAP_ZOOM_FACTOR = 2.0f

        /** Radius of the fitted curve as a fraction of the shorter view edge. */
        const val RADIUS_FRACTION = 0.4f
    }
}
