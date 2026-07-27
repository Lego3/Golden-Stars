package com.edvinlinge.hemma.mathstars2

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.*
import android.text.Html
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import kotlin.math.*
import android.animation.Animator
import android.animation.AnimatorListenerAdapter

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


    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        viewWidth = w.toFloat()
        viewHeight = h.toFloat()

        val centerX = viewWidth / 2f
        val centerY = viewHeight / 2f
        val radius = min(viewWidth, viewHeight) * 0.4f

        for (i in 0 until dots) {
            val angle = (2 * Math.PI * i / dots).toFloat()
            val x = centerX + radius * cos(angle)
            val y = centerY + radius * sin(angle)
            points.add(Pair(x, y))
        }


        tryPathWithSkips(path, skips)

        val measure = PathMeasure(path, false)
        pathLength = measure.length

        paint.color = ContextCompat.getColor(context, R.color.gold)
        paint.strokeWidth = 8f
        paint.style = Paint.Style.STROKE
        paint.flags = Paint.ANTI_ALIAS_FLAG

        startAnimation()
    }

    fun setDotsAndSkips(dots: Int, skips: Int) {
        this.dots = dots
        this.skips = skips
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (width == 0 || height == 0 || dots == 0 || skips == 0) {
            return
        }
        canvas.drawPath(path, paint)
    }

    private fun startAnimation() {
        val animator = ObjectAnimator.ofFloat(this, "phase", 1f, 0f)
        animator.setDuration(5000)

        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                paint.style = Paint.Style.FILL
                invalidate()
            }
        })

        animator.start()
    }

    fun setPhase(phase: Float) {
        paint.setPathEffect(
            DashPathEffect(
                floatArrayOf(pathLength, pathLength),
                (phase * pathLength).coerceAtLeast(0.0f)
            ))
        invalidate()
    }

    fun showDetails(context: Context) {
        val stringParts = mutableListOf<String>()

        val possibleVariants = allSuccessSkips()

        path = Path()
        val resultVisits = tryPathWithSkips(path, skips)

        if (possibleVariants.size == 0) {
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

        if (possibleVariants.size > 0) {
            stringParts.add(context.getString(R.string.details_info, dots, possibleVariants.size))
            stringParts.add(context.getString(R.string.details_help, dots))
            stringParts.add(possibleVariants.joinToString(", "))
        }

        val message = Html.fromHtml(
            stringParts.joinToString("<br><br>"),
            Html.FROM_HTML_MODE_LEGACY
        )
            AlertDialog.Builder(context)
                .setTitle(R.string.more_info_button)
                .setMessage(message)
                .setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
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