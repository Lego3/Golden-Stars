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
