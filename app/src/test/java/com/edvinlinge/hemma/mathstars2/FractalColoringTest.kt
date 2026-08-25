package com.edvinlinge.hemma.mathstars2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FractalColoringTest {

    @Test
    fun `interior points and immediate escapes encode as black`() {
        assertEquals(0, FractalColoring.escapeAlpha(100, 100))
        assertEquals(0, FractalColoring.escapeAlpha(150, 100))
        assertEquals(0, FractalColoring.escapeAlpha(0, 100))
        assertEquals(0, FractalColoring.escapeGrayArgb(100, 100) and 0x00FFFFFF)
    }

    @Test
    fun `escape alpha scales linearly with iteration count`() {
        assertEquals(127, FractalColoring.escapeAlpha(50, 100))
        assertEquals((99 * 255) / 100, FractalColoring.escapeAlpha(99, 100))
        assertEquals((25 * 255) / 100, FractalColoring.escapeAlpha(25, 100))
    }

    @Test
    fun `gray argb stores the sample in every channel`() {
        val color = FractalColoring.grayArgb(80)
        assertEquals(0xFF, color ushr 24)
        assertEquals(80, (color shr 16) and 0xFF)
        assertEquals(80, (color shr 8) and 0xFF)
        assertEquals(80, color and 0xFF)
    }

    @Test
    fun `escape alpha returns zero when max iterations is non-positive`() {
        assertEquals(0, FractalColoring.escapeAlpha(50, 0))
        assertEquals(0, FractalColoring.escapeAlpha(50, -10))
    }

    @Test
    fun `gray argb clamps alpha to byte range`() {
        assertEquals(0xFF000000.toInt(), FractalColoring.grayArgb(-5))
        assertEquals(0xFFFFFFFF.toInt(), FractalColoring.grayArgb(300))
    }

    @Test
    fun `silver matrix leaves grayscale unchanged`() {
        val matrix = FractalColoring.colorMatrixValues(FractalPalette.SILVER)
        val out = FractalColoring.applyColorMatrix(matrix, 100f, 100f, 100f, 255f)
        assertEquals(100f, out[0], 1e-4f)
        assertEquals(100f, out[1], 1e-4f)
        assertEquals(100f, out[2], 1e-4f)
        assertEquals(255f, out[3], 1e-4f)
    }

    @Test
    fun `palette matrix matches hsv value scaling`() {
        val alpha = 80
        val t = alpha / 255f
        for (palette in FractalPalette.entries) {
            val expectedV = (t * FractalColoring.valueScale(palette)).coerceAtMost(1f)
            val expected = FractalColoring.hsvToRgb(
                FractalColoring.hue(palette),
                FractalColoring.saturation(palette),
                expectedV,
            )
            val out = FractalColoring.applyColorMatrix(
                FractalColoring.colorMatrixValues(palette),
                alpha.toFloat(),
                alpha.toFloat(),
                alpha.toFloat(),
                255f,
            )
            assertEquals(palette.name, expected[0] * 255f, out[0], 0.75f)
            assertEquals(palette.name, expected[1] * 255f, out[1], 0.75f)
            assertEquals(palette.name, expected[2] * 255f, out[2], 0.75f)
        }
    }

    @Test
    fun `golden boost saturates at full brightness`() {
        val matrix = FractalColoring.colorMatrixValues(FractalPalette.GOLDEN)
        val full = FractalColoring.hsvToRgb(45f, 0.8f, 1f)
        val out = FractalColoring.applyColorMatrix(matrix, 200f, 200f, 200f, 255f)
        assertTrue(out[0] >= full[0] * 255f - 0.5f)
        assertTrue(out[1] >= full[1] * 255f - 0.5f)
        assertTrue(out[2] >= full[2] * 255f - 0.5f)
    }

    @Test
    fun `color matrix has twenty entries`() {
        assertEquals(20, FractalColoring.colorMatrixValues(FractalPalette.BLUE).size)
    }
}
