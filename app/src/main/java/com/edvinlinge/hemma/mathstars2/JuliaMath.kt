package com.edvinlinge.hemma.mathstars2

import java.util.Locale
import kotlin.math.abs

/**
 * Pure Kotlin math for the Julia renderer, free of Android types so it can be covered by fast
 * JVM unit tests. A Julia set holds *c* fixed and iterates *z → z² + c* from each pixel's *z*.
 */
internal object JuliaMath {

    data class Preset(val real: Double, val imag: Double)

    /**
     * Well-known values of *c*. Order matches the on-screen preset slider.
     *
     * The first six sit inside (or on) the Mandelbrot set and produce connected Julia sets.
     * The last two lie outside it and produce dust.
     */
    val PRESETS = listOf(
        Preset(-0.123, 0.745),
        Preset(-0.75, 0.0),
        Preset(0.0, 1.0),
        Preset(-0.391, -0.587),
        Preset(-1.0, 0.0),
        Preset(-1.75, 0.0),
        Preset(-0.4, 0.6),
        Preset(0.355, 0.355),
    )

    const val DEFAULT_PRESET_INDEX = 0

    fun coercedPresetIndex(index: Int): Int = index.coerceIn(0, PRESETS.lastIndex)

    fun presetAt(index: Int): Preset = PRESETS[coercedPresetIndex(index)]

    /**
     * Iterations until the orbit of *z₀* escapes, or [maxIterations] if it stays bounded.
     * When *z₀* is 0 this is the same test Mandelbrot uses for membership of *c*.
     */
    fun escapeIterations(
        zr0: Double,
        zi0: Double,
        cr: Double,
        ci: Double,
        maxIterations: Int = MandelbrotMath.MAX_ITERATIONS,
        escapeRadiusSquared: Double = MandelbrotMath.ESCAPE_RADIUS_SQUARED,
    ): Int {
        var zr = zr0
        var zi = zi0
        var iteration = 0
        while (zr * zr + zi * zi <= escapeRadiusSquared && iteration < maxIterations) {
            val nextZr = zr * zr - zi * zi + cr
            zi = 2.0 * zr * zi + ci
            zr = nextZr
            iteration++
        }
        return iteration
    }

    /** True when the critical orbit of *c* stays bounded, which suggests a connected Julia set. */
    fun isLikelyConnected(
        cr: Double,
        ci: Double,
        maxIterations: Int = MandelbrotMath.MAX_ITERATIONS,
    ): Boolean = escapeIterations(0.0, 0.0, cr, ci, maxIterations) == maxIterations

    /**
     * True when an in-flight Julia render result should be written to the bitmap. Besides the
     * viewport, the constant *c* and palette must still match what the job was started with.
     */
    fun <T> shouldApplyRenderResult(
        zoom: Double,
        offsetX: Double,
        offsetY: Double,
        renderZoom: Double,
        renderOffsetX: Double,
        renderOffsetY: Double,
        renderCReal: Double,
        renderCImag: Double,
        cReal: Double,
        cImag: Double,
        renderPalette: T,
        currentPalette: T,
    ): Boolean =
        MandelbrotMath.renderTargetMatchesViewport(
            zoom = zoom,
            offsetX = offsetX,
            offsetY = offsetY,
            renderZoom = renderZoom,
            renderOffsetX = renderOffsetX,
            renderOffsetY = renderOffsetY,
        ) &&
            renderCReal == cReal &&
            renderCImag == cImag &&
            renderPalette == currentPalette

    /** Formats *c* as `a + bi` / `a - bi` with trailing zeros stripped. */
    fun formatConstant(real: Double, imag: Double): String {
        val realPart = formatNumber(real)
        val imagAbs = formatNumber(abs(imag))
        val sign = if (imag < 0) "-" else "+"
        return "$realPart $sign ${imagAbs}i"
    }

    private fun formatNumber(value: Double): String {
        val formatted = String.format(Locale.US, "%.4f", value)
        return formatted.trimEnd('0').trimEnd('.')
    }
}
