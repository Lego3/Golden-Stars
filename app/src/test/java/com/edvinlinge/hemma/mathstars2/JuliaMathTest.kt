package com.edvinlinge.hemma.mathstars2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JuliaMathTest {

    @Test
    fun `preset index is clamped to the known list`() {
        assertEquals(0, JuliaMath.coercedPresetIndex(-3))
        assertEquals(0, JuliaMath.coercedPresetIndex(0))
        assertEquals(JuliaMath.PRESETS.lastIndex, JuliaMath.coercedPresetIndex(99))
    }

    @Test
    fun `default preset is the Douady rabbit`() {
        val rabbit = JuliaMath.presetAt(JuliaMath.DEFAULT_PRESET_INDEX)
        assertEquals(-0.123, rabbit.real, 1e-12)
        assertEquals(0.745, rabbit.imag, 1e-12)
    }

    @Test
    fun `origin stays bounded when c is zero, matching the unit disk Julia set`() {
        assertEquals(
            MandelbrotMath.MAX_ITERATIONS,
            JuliaMath.escapeIterations(0.0, 0.0, 0.0, 0.0),
        )
        assertEquals(
            MandelbrotMath.MAX_ITERATIONS,
            JuliaMath.escapeIterations(0.5, 0.0, 0.0, 0.0),
        )
        assertTrue(JuliaMath.escapeIterations(2.0, 0.0, 0.0, 0.0) < 10)
    }

    @Test
    fun `Julia at z0 equals zero matches Mandelbrot membership of c`() {
        val samples = listOf(
            0.0 to 0.0,
            -0.75 to 0.0,
            -1.0 to 0.0,
            0.355 to 0.355,
            -0.123 to 0.745,
            2.0 to 0.0,
        )
        for ((cr, ci) in samples) {
            assertEquals(
                MandelbrotMath.escapeIterations(cr, ci, maxIterations = 200),
                JuliaMath.escapeIterations(0.0, 0.0, cr, ci, maxIterations = 200),
            )
        }
    }

    @Test
    fun `connected presets keep the critical point bounded`() {
        for (index in 0..5) {
            val preset = JuliaMath.presetAt(index)
            assertTrue(
                "preset $index c=${preset.real}+${preset.imag}i should look connected",
                JuliaMath.isLikelyConnected(preset.real, preset.imag),
            )
        }
    }

    @Test
    fun `dust presets let the critical point escape`() {
        for (index in 6..7) {
            val preset = JuliaMath.presetAt(index)
            assertFalse(
                "preset $index c=${preset.real}+${preset.imag}i should look disconnected",
                JuliaMath.isLikelyConnected(preset.real, preset.imag),
            )
        }
    }

    @Test
    fun `points far from a connected Julia set escape quickly`() {
        val rabbit = JuliaMath.presetAt(0)
        assertTrue(
            JuliaMath.escapeIterations(2.0, 2.0, rabbit.real, rabbit.imag, maxIterations = 200) < 10,
        )
    }

    @Test
    fun `render result applies only when viewport constant and palette still match`() {
        val viewport = arrayOf(
            2.0, -0.5, 0.1,
            2.0, -0.5, 0.1,
        )
        val rabbit = JuliaMath.presetAt(0)
        assertTrue(
            JuliaMath.shouldApplyRenderResult(
                zoom = viewport[0],
                offsetX = viewport[1],
                offsetY = viewport[2],
                renderZoom = viewport[3],
                renderOffsetX = viewport[4],
                renderOffsetY = viewport[5],
                renderCReal = rabbit.real,
                renderCImag = rabbit.imag,
                cReal = rabbit.real,
                cImag = rabbit.imag,
                renderPalette = "golden",
                currentPalette = "golden",
            ),
        )
        assertFalse(
            JuliaMath.shouldApplyRenderResult(
                zoom = viewport[0],
                offsetX = viewport[1],
                offsetY = viewport[2],
                renderZoom = viewport[3],
                renderOffsetX = viewport[4],
                renderOffsetY = viewport[5],
                renderCReal = rabbit.real,
                renderCImag = rabbit.imag,
                cReal = -0.75,
                cImag = 0.0,
                renderPalette = "golden",
                currentPalette = "golden",
            ),
        )
        assertFalse(
            JuliaMath.shouldApplyRenderResult(
                zoom = viewport[0],
                offsetX = viewport[1],
                offsetY = viewport[2],
                renderZoom = viewport[3],
                renderOffsetX = viewport[4],
                renderOffsetY = viewport[5],
                renderCReal = rabbit.real,
                renderCImag = rabbit.imag,
                cReal = rabbit.real,
                cImag = rabbit.imag,
                renderPalette = "golden",
                currentPalette = "silver",
            ),
        )
        assertFalse(
            JuliaMath.shouldApplyRenderResult(
                zoom = 4.0,
                offsetX = viewport[1],
                offsetY = viewport[2],
                renderZoom = viewport[3],
                renderOffsetX = viewport[4],
                renderOffsetY = viewport[5],
                renderCReal = rabbit.real,
                renderCImag = rabbit.imag,
                cReal = rabbit.real,
                cImag = rabbit.imag,
                renderPalette = "golden",
                currentPalette = "golden",
            ),
        )
    }

    @Test
    fun `format constant uses a plus or minus before the imaginary part`() {
        assertEquals("-0.123 + 0.745i", JuliaMath.formatConstant(-0.123, 0.745))
        assertEquals("0 + 1i", JuliaMath.formatConstant(0.0, 1.0))
        assertEquals("-0.75 + 0i", JuliaMath.formatConstant(-0.75, 0.0))
        assertEquals("0 + 0i", JuliaMath.formatConstant(0.0, 0.0))
        assertEquals("-0.391 - 0.587i", JuliaMath.formatConstant(-0.391, -0.587))
        assertEquals("0.355 + 0.355i", JuliaMath.formatConstant(0.355, 0.355))
    }
}
