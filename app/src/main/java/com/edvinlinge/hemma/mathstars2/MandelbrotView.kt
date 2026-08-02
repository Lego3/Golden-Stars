package com.edvinlinge.hemma.mathstars2

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.graphics.createBitmap

class MandelbrotView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var bitmap: Bitmap? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var zoom = 1.0
    private var offsetX = -0.5
    private var offsetY = 0.0
    private var needsRedraw = true

    private val maxIterations = 100
    private var colorPalette = Palette.GOLDEN

    private val scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoom *= detector.scaleFactor
            needsRedraw = true
            invalidate()
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            offsetX += (distanceX / width) * (4.0 / zoom)
            offsetY += (distanceY / height) * (4.0 / zoom)
            needsRedraw = true
            invalidate()
            return true
        }
    })

    enum class Palette {
        GOLDEN, SILVER, BLUE, GREEN
    }

    fun setColorPalette(palette: Palette) {
        this.colorPalette = palette
        needsRedraw = true
        invalidate()
    }

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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        if (bitmap == null || bitmap?.width != width || bitmap?.height != height) {
            bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
            needsRedraw = true
        }

        if (needsRedraw) {
            renderMandelbrot()
            needsRedraw = false
        }
        bitmap?.let { canvas.drawBitmap(it, 0f, 0f, paint) }
    }

    private fun renderMandelbrot() {
        val bmp = bitmap ?: return
        val w = bmp.width
        val h = bmp.height
        val pixels = IntArray(w * h)

        val xStep = 4.0 / (w * zoom)
        val yStep = 4.0 / (h * zoom)

        val xMin = offsetX - (2.0 / zoom)
        val yMin = offsetY - (2.0 / zoom)

        for (y in 0 until h) {
            val ci = yMin + y * yStep
            for (x in 0 until w) {
                val cr = xMin + x * xStep
                
                var zr = 0.0
                var zi = 0.0
                var iterations = 0
                
                while (zr * zr + zi * zi <= 4.0 && iterations < maxIterations) {
                    val temp = zr * zr - zi * zi + cr
                    zi = 2.0 * zr * zi + ci
                    zr = temp
                    iterations++
                }

                pixels[y * w + x] = if (iterations == maxIterations) {
                    Color.BLACK
                } else {
                    getColorForIteration(iterations)
                }
            }
        }
        bmp.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    private fun getColorForIteration(iterations: Int): Int {
        val t = iterations.toFloat() / maxIterations
        return when (colorPalette) {
            Palette.GOLDEN -> Color.HSVToColor(floatArrayOf(45f, 0.8f, t * 1.5f.coerceAtMost(1f)))
            Palette.SILVER -> Color.HSVToColor(floatArrayOf(0f, 0f, t))
            Palette.BLUE -> Color.HSVToColor(floatArrayOf(200f, 0.7f, t))
            Palette.GREEN -> Color.HSVToColor(floatArrayOf(120f, 0.7f, t))
        }
    }
}
