package com.edvinlinge.hemma.mathstars2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MandelbrotMathTest {

    @Test
    fun `units per pixel shrinks as zoom increases`() {
        val atZoom1 = MandelbrotMath.unitsPerPixel(zoomLevel = 1.0, viewWidth = 800, viewHeight = 600)
        val atZoom10 = MandelbrotMath.unitsPerPixel(zoomLevel = 10.0, viewWidth = 800, viewHeight = 600)
        assertTrue(atZoom10 < atZoom1)
        assertEquals(atZoom1 / 10.0, atZoom10, 1e-12)
    }

    @Test
    fun `units per pixel uses the shorter view edge so pixels stay square`() {
        val portrait = MandelbrotMath.unitsPerPixel(zoomLevel = 1.0, viewWidth = 400, viewHeight = 800)
        val landscape = MandelbrotMath.unitsPerPixel(zoomLevel = 1.0, viewWidth = 800, viewHeight = 400)
        assertEquals(portrait, landscape, 1e-12)
        assertEquals(MandelbrotMath.VIEWPORT_SPAN / 400.0, portrait, 1e-12)
    }

    @Test
    fun `units per pixel handles zero-sized views without dividing by zero`() {
        assertEquals(MandelbrotMath.VIEWPORT_SPAN, MandelbrotMath.unitsPerPixel(1.0, 0, 0), 1e-12)
    }

    @Test
    fun `iterations stay at the base count until zoom exceeds one`() {
        assertEquals(MandelbrotMath.BASE_ITERATIONS, MandelbrotMath.iterationsFor(0.5))
        assertEquals(MandelbrotMath.BASE_ITERATIONS, MandelbrotMath.iterationsFor(1.0))
    }

    @Test
    fun `iterations grow with zoom but never exceed the cap`() {
        assertEquals(300, MandelbrotMath.iterationsFor(10.0))
        assertEquals(500, MandelbrotMath.iterationsFor(100.0))
        assertEquals(MandelbrotMath.MAX_ITERATIONS, MandelbrotMath.iterationsFor(1.0e13))
        assertEquals(MandelbrotMath.MAX_ITERATIONS, MandelbrotMath.iterationsFor(1.0e20))
    }

    @Test
    fun `origin is inside the set and does not escape`() {
        assertEquals(MandelbrotMath.MAX_ITERATIONS, MandelbrotMath.escapeIterations(0.0, 0.0))
    }

    @Test
    fun `points clearly outside the set escape quickly`() {
        assertTrue(MandelbrotMath.escapeIterations(2.0, 0.0) < 10)
        assertTrue(MandelbrotMath.escapeIterations(0.0, 2.0) < 10)
        assertTrue(MandelbrotMath.escapeIterations(-2.0, 0.5) < 10)
    }

    @Test
    fun `points closer to the boundary take longer to escape`() {
        val farExterior = MandelbrotMath.escapeIterations(1.0, 0.0, maxIterations = 200)
        val nearBoundary = MandelbrotMath.escapeIterations(0.26, 0.0, maxIterations = 200)
        assertTrue(farExterior < nearBoundary)
    }

    @Test
    fun `escape radius of two matches the classic Mandelbrot definition`() {
        // c = -2 is on the real axis tip; it should not escape within a modest budget.
        assertEquals(100, MandelbrotMath.escapeIterations(-2.0, 0.0, maxIterations = 100))
        // c = 0.25 is the cusp of the main cardioid; orbit stays bounded for many steps.
        assertEquals(100, MandelbrotMath.escapeIterations(0.25, 0.0, maxIterations = 100))
    }
}
