package com.edvinlinge.hemma.mathstars2

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.*
import android.os.Parcel
import android.os.Parcelable

class DrawView
    (context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val paint = Paint()

    private var dots = 0
    private var skips = 0
    private val points = mutableListOf<Pair<Float, Float>>()

    private var viewWidth = 0f
    private var viewHeight = 0f

    private var path = Path()
    private var pathLength = 0f

    private var currentPhase = 1f
    private var animator: ValueAnimator? = null

    private var drawColor = Color.YELLOW
    private var strokeWidth = 8f
    private var animationDuration = 5000L
    private var isFilled = true
    private var instantRender = false

    private var zoom = 1.0f
    private var offsetX = 0f
    private var offsetY = 0f

    private val scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoom *= detector.scaleFactor
            zoom = zoom.coerceIn(0.5f, 10.0f)
            invalidate()
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            offsetX -= distanceX
            offsetY -= distanceY
            invalidate()
            return true
        }
    })

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        if (event.action == MotionEvent.ACTION_UP) {
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

        viewWidth = w.toFloat()
        viewHeight = h.toFloat()

        points.clear()
        val centerX = viewWidth / 2f
        val centerY = viewHeight / 2f
        val radius = min(viewWidth, viewHeight) * 0.4f

        for (i in 0 until dots) {
            val angle = (2 * Math.PI * i.toDouble() / dots).toFloat()
            val x = centerX + radius * cos(angle)
            val y = centerY + radius * sin(angle)
            points.add(Pair(x, y))
        }


        path.reset()
        tryPathWithSkips(path, skips)

        val measure = PathMeasure(path, false)
        pathLength = measure.length

        paint.color = drawColor
        paint.strokeWidth = strokeWidth
        paint.style = Paint.Style.STROKE
        paint.flags = Paint.ANTI_ALIAS_FLAG

        startAnimation()
    }

    fun setDotsAndSkips(dots: Int, skips: Int) {
        this.dots = dots
        this.skips = skips
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

    fun getStrokeWidth(): Float = strokeWidth

    fun setFilled(filled: Boolean) {
        this.isFilled = filled
        if (currentPhase <= 0f) {
            paint.style = if (isFilled) Paint.Style.FILL else Paint.Style.STROKE
        }
        invalidate()
    }

    fun isFilled(): Boolean = isFilled

    fun setInstant(instant: Boolean) {
        this.instantRender = instant
        if (instantRender) {
            currentPhase = 0f
            animator?.cancel()
            paint.style = if (isFilled) Paint.Style.FILL else Paint.Style.STROKE
            paint.pathEffect = null
            invalidate()
        }
    }

    fun isInstant(): Boolean = instantRender

    fun setAnimationSpeed(speedMultiplier: Float) {
        // speedMultiplier = 1.0 is default (5000ms)
        // 2.0 is faster (2500ms)
        // 0.5 is slower (10000ms)
        this.animationDuration = (5000 / speedMultiplier).toLong()
    }

    fun replay() {
        currentPhase = 1f
        startAnimation()
    }

    fun updatePointsAndPath(dots: Int, skips: Int) {
        this.dots = dots
        this.skips = skips
        
        points.clear()
        val centerX = viewWidth / 2f
        val centerY = viewHeight / 2f
        val radius = min(viewWidth, viewHeight) * 0.4f

        for (i in 0 until dots) {
            val angle = (2 * Math.PI * i.toDouble() / dots).toFloat()
            val x = centerX + radius * cos(angle)
            val y = centerY + radius * sin(angle)
            points.add(Pair(x, y))
        }

        path.reset()
        tryPathWithSkips(path, skips)
        
        val measure = PathMeasure(path, false)
        pathLength = measure.length
        
        if (instantRender) {
            currentPhase = 0f
            animator?.cancel()
            paint.style = if (isFilled) Paint.Style.FILL else Paint.Style.STROKE
            paint.pathEffect = null
            invalidate()
        } else {
            replay()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (width == 0 || height == 0 || dots == 0 || skips == 0) {
            return
        }
        
        canvas.save()
        canvas.translate(width / 2f + offsetX, height / 2f + offsetY)
        canvas.scale(zoom, zoom)
        canvas.translate(-width / 2f, -height / 2f)
        
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    private fun startAnimation() {
        animator?.cancel()
        if (currentPhase <= 0f || instantRender) {
            paint.style = if (isFilled) Paint.Style.FILL else Paint.Style.STROKE
            paint.pathEffect = null
            invalidate()
            return
        }

        paint.style = Paint.Style.STROKE
        animator = ValueAnimator.ofFloat(currentPhase, 0f).apply {
            duration = (currentPhase * animationDuration).toLong()
            addUpdateListener { animation ->
                setPhase(animation.animatedValue as Float)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    paint.style = if (isFilled) Paint.Style.FILL else Paint.Style.STROKE
                    paint.pathEffect = null
                    invalidate()
                }
            })
            start()
        }
    }

    fun setPhase(phase: Float) {
        this.currentPhase = phase
        paint.setPathEffect(
            DashPathEffect(
                floatArrayOf(pathLength, pathLength),
                (phase * pathLength).coerceAtLeast(0.0f)
            ))
        invalidate()
    }

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        val savedState = SavedState(superState)
        savedState.phase = this.currentPhase
        savedState.dots = this.dots
        savedState.skips = this.skips
        return savedState
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is SavedState) {
            super.onRestoreInstanceState(state.superState)
            this.currentPhase = state.phase
            this.dots = state.dots
            this.skips = state.skips
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    internal class SavedState : BaseSavedState {
        var phase: Float = 1f
        var dots: Int = 0
        var skips: Int = 0

        constructor(superState: Parcelable?) : super(superState)

        constructor(source: Parcel) : super(source) {
            phase = source.readFloat()
            dots = source.readInt()
            skips = source.readInt()
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeFloat(phase)
            out.writeInt(dots)
            out.writeInt(skips)
        }

        companion object {
            @JvmField
            val CREATOR = object : Parcelable.Creator<SavedState> {
                override fun createFromParcel(source: Parcel): SavedState {
                    return SavedState(source)
                }

                override fun newArray(size: Int): Array<SavedState?> {
                    return arrayOfNulls(size)
                }
            }
        }
    }

    fun getDetailsHtml(context: Context): String {
        val stringParts = mutableListOf<String>()

        val possibleVariants = allSuccessSkips()

        path = Path()
        val resultVisits = tryPathWithSkips(path, skips)

        if (possibleVariants.isEmpty()) {
            stringParts.add(context.getString(R.string.details_fail, dots))
        }

        if (resultVisits == points.size) {
            stringParts.add(context.getString(R.string.details_success, dots))
            if (isPrime(dots)) {
                stringParts.add(" ${context.getString(R.string.details_is_prime, dots)}")
            }
        }
        else {
            stringParts.add(context.getString(R.string.details_fair, dots, skips, resultVisits))
        }

        if (possibleVariants.isNotEmpty()) {
            stringParts.add(context.getString(R.string.details_info, dots, possibleVariants.size))
            stringParts.add(context.getString(R.string.details_help, dots))
            stringParts.add(possibleVariants.joinToString(", "))
        }

        return stringParts.joinToString("<br><br>")
    }

    private fun isPrime(number: Int): Boolean {
        if (number <= 1) {
            return false
        }

        for (i in 2..sqrt(number.toDouble()).toInt()) {
            if (number % i == 0) {
                return false
            }
        }

        return true
    }

    private fun tryPathWithSkips(path: Path, skips: Int) : Int {
        path.moveTo(points[0].first, points[0].second)
        val visitedPoints = mutableListOf<Int>()
        path.moveTo(points[0].first, points[0].second)
        var nextIndex = skips % dots
        while (nextIndex !in visitedPoints) {
            visitedPoints.add(nextIndex)
            path.lineTo(points[nextIndex].first, points[nextIndex].second)
            nextIndex = (nextIndex + skips) % dots
        }
        return visitedPoints.size
    }

    private fun allSuccessSkips() : MutableList<Int> {
        val successSkips = mutableListOf<Int>()
        for (i in 2..dots/2) {
            path = Path()
            if (tryPathWithSkips(path, i) == dots) {
                successSkips.add(i)
            }
        }
        return successSkips
    }
}