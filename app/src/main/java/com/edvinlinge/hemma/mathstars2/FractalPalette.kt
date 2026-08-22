package com.edvinlinge.hemma.mathstars2

/**
 * Shared colour palettes for the Mandelbrot and Julia explorers. Swatch order matches
 * [SettingsBottomSheet] colour chips.
 *
 * Cached images store a greyscale escape-time map. The palette is a draw-time
 * [android.graphics.ColorMatrixColorFilter], so changing colour does not invalidate tiles or
 * force a rerender.
 */
enum class FractalPalette {
    GOLDEN,
    SILVER,
    BLUE,
    GREEN,
}

/**
 * Palette-independent escape encoding and the colour-matrix that tints that map on screen.
 * Android-free so JVM unit tests can check the numbers the views feed to ColorMatrix.
 */
internal object FractalColoring {

    fun hue(palette: FractalPalette): Float = when (palette) {
        FractalPalette.GOLDEN -> 45f
        FractalPalette.SILVER -> 0f
        FractalPalette.BLUE -> 200f
        FractalPalette.GREEN -> 120f
    }

    fun saturation(palette: FractalPalette): Float = when (palette) {
        FractalPalette.GOLDEN -> 0.8f
        FractalPalette.SILVER -> 0f
        FractalPalette.BLUE -> 0.7f
        FractalPalette.GREEN -> 0.7f
    }

    /** Golden used to boost value by 1.5, clamped to 1. The colour matrix does the same. */
    fun valueScale(palette: FractalPalette): Float = when (palette) {
        FractalPalette.GOLDEN -> 1.5f
        FractalPalette.SILVER,
        FractalPalette.BLUE,
        FractalPalette.GREEN -> 1f
    }

    /**
     * 8-bit greyscale sample for one escape count. Interior points (and immediate escapes) are 0
     * so they stay black after the palette filter.
     */
    fun escapeAlpha(iterations: Int, maxIterations: Int): Int {
        if (maxIterations <= 0 || iterations >= maxIterations) return 0
        return ((iterations.toLong() * 255L) / maxIterations).toInt().coerceIn(0, 255)
    }

    fun grayArgb(alpha: Int): Int {
        val g = alpha.coerceIn(0, 255)
        return (0xFF shl 24) or (g shl 16) or (g shl 8) or g
    }

    fun escapeGrayArgb(iterations: Int, maxIterations: Int): Int =
        grayArgb(escapeAlpha(iterations, maxIterations))

    /**
     * 4×5 colour matrix that maps a greyscale RGB pixel onto [palette]. Input R=G=B is the
     * escape sample (0–255); output is the tinted colour. Values may exceed 1 so Android can
     * clamp the golden 1.5× boost the same way HSV value did.
     */
    fun colorMatrixValues(palette: FractalPalette): FloatArray {
        val rgb = hsvToRgb(hue(palette), saturation(palette), 1f)
        val scale = valueScale(palette)
        val sr = rgb[0] * scale
        val sg = rgb[1] * scale
        val sb = rgb[2] * scale
        return floatArrayOf(
            sr, 0f, 0f, 0f, 0f,
            0f, sg, 0f, 0f, 0f,
            0f, 0f, sb, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    fun hsvToRgb(h: Float, s: Float, v: Float): FloatArray {
        if (s <= 0f) return floatArrayOf(v, v, v)
        val hue = ((h % 360f) + 360f) % 360f
        val chroma = v * s
        val hPrime = hue / 60f
        val x = chroma * (1f - kotlin.math.abs(hPrime % 2f - 1f))
        val m = v - chroma
        val (rp, gp, bp) = when {
            hPrime < 1f -> Triple(chroma, x, 0f)
            hPrime < 2f -> Triple(x, chroma, 0f)
            hPrime < 3f -> Triple(0f, chroma, x)
            hPrime < 4f -> Triple(0f, x, chroma)
            hPrime < 5f -> Triple(x, 0f, chroma)
            else -> Triple(chroma, 0f, x)
        }
        return floatArrayOf(rp + m, gp + m, bp + m)
    }

    fun applyColorMatrix(matrix: FloatArray, r: Float, g: Float, b: Float, a: Float): FloatArray {
        fun row(offset: Int): Float =
            matrix[offset] * r +
                matrix[offset + 1] * g +
                matrix[offset + 2] * b +
                matrix[offset + 3] * a +
                matrix[offset + 4]
        return floatArrayOf(row(0), row(5), row(10), row(15))
    }
}
