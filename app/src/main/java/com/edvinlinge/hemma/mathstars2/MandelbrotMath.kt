package com.edvinlinge.hemma.mathstars2

import kotlin.math.log10

/**
 * Pure Kotlin math for the Mandelbrot renderer, free of Android types so it can be covered by
 * fast JVM unit tests. [MandelbrotView] delegates viewport sizing and escape-time iteration here.
 */
internal object MandelbrotMath {

    /** Width of the viewport in the complex plane at zoom 1, across the shorter view edge. */
    const val VIEWPORT_SPAN = 4.0
    const val BASE_ITERATIONS = 100
    const val ITERATIONS_PER_DECADE = 200
    const val MAX_ITERATIONS = 1500
    const val ESCAPE_RADIUS_SQUARED = 4.0

    /**
     * Size of one pixel in the complex plane. Derived from the shorter view edge so pixels stay
     * square and the set is not stretched by the screen's aspect ratio.
     */
    fun clampedZoom(currentZoom: Double, factor: Double, minZoom: Double, maxZoom: Double): Double =
        (currentZoom * factor).coerceIn(minZoom, maxZoom)

    fun unitsPerPixel(zoomLevel: Double, viewWidth: Int, viewHeight: Int): Double =
        (VIEWPORT_SPAN / zoomLevel) / minOf(viewWidth, viewHeight).coerceAtLeast(1)

    /**
     * Deeper zoom needs more iterations to keep the boundary detailed, but the count is capped so
     * a single frame cannot grow into an unbounded amount of work.
     */
    fun iterationsFor(zoomLevel: Double): Int =
        (BASE_ITERATIONS + log10(zoomLevel).coerceAtLeast(0.0) * ITERATIONS_PER_DECADE)
            .toInt()
            .coerceIn(BASE_ITERATIONS, MAX_ITERATIONS)

    /**
     * Iterations until the orbit escapes the escape radius, or [maxIterations] if it stays bounded.
     */
    /** Complex-plane x coordinate under a screen point at the current viewport. */
    fun complexXAtScreen(
        screenX: Float,
        offsetX: Double,
        zoomLevel: Double,
        viewWidth: Int,
        viewHeight: Int,
    ): Double = offsetX + (screenX - viewWidth / 2.0) * unitsPerPixel(zoomLevel, viewWidth, viewHeight)

    /** Complex-plane y coordinate under a screen point at the current viewport. */
    fun complexYAtScreen(
        screenY: Float,
        offsetY: Double,
        zoomLevel: Double,
        viewWidth: Int,
        viewHeight: Int,
    ): Double = offsetY + (screenY - viewHeight / 2.0) * unitsPerPixel(zoomLevel, viewWidth, viewHeight)

    /**
     * Adjusts the viewport center after a zoom change so the complex number under
     * ([focusX], [focusY]) stays fixed on screen.
     */
    fun offsetAfterZoomChange(
        focusX: Float,
        focusY: Float,
        oldZoom: Double,
        newZoom: Double,
        offsetX: Double,
        offsetY: Double,
        viewWidth: Int,
        viewHeight: Int,
    ): Pair<Double, Double> {
        val focusRealBefore = complexXAtScreen(focusX, offsetX, oldZoom, viewWidth, viewHeight)
        val focusImagBefore = complexYAtScreen(focusY, offsetY, oldZoom, viewWidth, viewHeight)
        val newOffsetX = offsetX + focusRealBefore -
            complexXAtScreen(focusX, offsetX, newZoom, viewWidth, viewHeight)
        val newOffsetY = offsetY + focusImagBefore -
            complexYAtScreen(focusY, offsetY, newZoom, viewWidth, viewHeight)
        return newOffsetX to newOffsetY
    }

    /**
     * Screen-space scale and pan that approximate the current viewport using a bitmap rendered for
     * a different zoom or center. Used while a new render is in flight so gestures stay responsive.
     */
    fun staleBitmapDrawTransform(
        zoom: Double,
        bitmapZoom: Double,
        offsetX: Double,
        offsetY: Double,
        bitmapOffsetX: Double,
        bitmapOffsetY: Double,
        viewWidth: Int,
        viewHeight: Int,
    ): Triple<Float, Float, Float> {
        val scale = (zoom / bitmapZoom).toFloat()
        val units = unitsPerPixel(zoom, viewWidth, viewHeight)
        val dx = ((bitmapOffsetX - offsetX) / units).toFloat()
        val dy = ((bitmapOffsetY - offsetY) / units).toFloat()
        return Triple(scale, dx, dy)
    }

    /** True when the on-screen bitmap no longer matches the current viewport and needs a rerender. */
    fun needsFullRender(
        hasRenderedOnce: Boolean,
        bitmapIsPreview: Boolean,
        zoom: Double,
        bitmapZoom: Double,
        offsetX: Double,
        bitmapOffsetX: Double,
        offsetY: Double,
        bitmapOffsetY: Double,
    ): Boolean =
        !hasRenderedOnce ||
            bitmapIsPreview ||
            zoom != bitmapZoom ||
            offsetX != bitmapOffsetX ||
            offsetY != bitmapOffsetY

    /** Preview downscale never yields a zero-sized render target. */
    fun renderDimensions(viewWidth: Int, viewHeight: Int, downscale: Int): Pair<Int, Int> =
        (viewWidth / downscale).coerceAtLeast(1) to (viewHeight / downscale).coerceAtLeast(1)

    fun escapeIterations(
        cr: Double,
        ci: Double,
        maxIterations: Int = MAX_ITERATIONS,
        escapeRadiusSquared: Double = ESCAPE_RADIUS_SQUARED,
    ): Int {
        var zr = 0.0
        var zi = 0.0
        var iteration = 0
        while (zr * zr + zi * zi <= escapeRadiusSquared && iteration < maxIterations) {
            val nextZr = zr * zr - zi * zi + cr
            zi = 2.0 * zr * zi + ci
            zr = nextZr
            iteration++
        }
        return iteration
    }
}
