package com.edvinlinge.hemma.mathstars2

import org.junit.Assert.assertEquals
import org.junit.Test

class FractalPaletteTest {

    @Test
    fun `palette index maps to the matching swatch order`() {
        assertEquals(FractalPalette.GOLDEN, fractalPaletteFor(0))
        assertEquals(FractalPalette.SILVER, fractalPaletteFor(1))
        assertEquals(FractalPalette.BLUE, fractalPaletteFor(2))
        assertEquals(FractalPalette.GREEN, fractalPaletteFor(3))
    }

    @Test
    fun `normalized fractal color index clamps to swatch bounds`() {
        assertEquals(0, normalizedFractalColorIndex(-1))
        assertEquals(0, normalizedFractalColorIndex(-99))
        assertEquals(3, normalizedFractalColorIndex(4))
        assertEquals(3, normalizedFractalColorIndex(99))
        assertEquals(2, normalizedFractalColorIndex(2))
    }

    @Test
    fun `out of range palette indices clamp instead of crashing`() {
        assertEquals(FractalPalette.GOLDEN, fractalPaletteFor(-1))
        assertEquals(FractalPalette.GOLDEN, fractalPaletteFor(-99))
        assertEquals(FractalPalette.GREEN, fractalPaletteFor(4))
        assertEquals(FractalPalette.GREEN, fractalPaletteFor(99))
    }
}
