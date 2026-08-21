package com.edvinlinge.hemma.mathstars2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertTrue(MandelbrotMath.inMainCardioidOrPeriod2Bulb(0.0, 0.0))
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
        assertFalse(MandelbrotMath.inMainCardioidOrPeriod2Bulb(-2.0, 0.0))
        // c = 0.25 is the cusp of the main cardioid; orbit stays bounded for many steps.
        assertEquals(100, MandelbrotMath.escapeIterations(0.25, 0.0, maxIterations = 100))
        assertTrue(MandelbrotMath.inMainCardioidOrPeriod2Bulb(0.25, 0.0))
    }

    @Test
    fun `period-2 bulb is treated as interior`() {
        assertTrue(MandelbrotMath.inMainCardioidOrPeriod2Bulb(-1.0, 0.0))
        assertEquals(200, MandelbrotMath.escapeIterations(-1.0, 0.0, maxIterations = 200))
        assertTrue(MandelbrotMath.escapeIterations(2.0, 0.0) < 10)
    }

    @Test
    fun `render spinner stays hidden while sharpening a covered viewport`() {
        assertFalse(
            MandelbrotMath.showRenderSpinner(
                workActive = true,
                workIsPrefetch = false,
                viewportCovered = true,
            ),
        )
        assertFalse(
            MandelbrotMath.showRenderSpinner(
                workActive = true,
                workIsPrefetch = true,
                viewportCovered = false,
            ),
        )
        assertTrue(
            MandelbrotMath.showRenderSpinner(
                workActive = true,
                workIsPrefetch = false,
                viewportCovered = false,
            ),
        )
        assertFalse(
            MandelbrotMath.showRenderSpinner(
                workActive = false,
                workIsPrefetch = false,
                viewportCovered = false,
            ),
        )
    }

    @Test
    fun `view center maps to the viewport offset`() {
        assertEquals(-0.5, MandelbrotMath.complexXAtScreen(400f, -0.5, 1.0, 800, 600), 1e-12)
        assertEquals(0.25, MandelbrotMath.complexYAtScreen(300f, 0.25, 1.0, 800, 600), 1e-12)
    }

    @Test
    fun `screen coordinates scale linearly with distance from center`() {
        val units = MandelbrotMath.unitsPerPixel(1.0, 800, 600)
        val left = MandelbrotMath.complexXAtScreen(0f, 0.0, 1.0, 800, 600)
        val right = MandelbrotMath.complexXAtScreen(800f, 0.0, 1.0, 800, 600)
        assertEquals(-400.0 * units, left, 1e-9)
        assertEquals(400.0 * units, right, 1e-9)
    }

    @Test
    fun `zooming in keeps the focus point fixed in the complex plane`() {
        val focusX = 320f
        val focusY = 240f
        val oldZoom = 1.0
        val newZoom = 4.0
        val offsetX = -0.5
        val offsetY = 0.0
        val viewWidth = 800
        val viewHeight = 600

        val beforeX = MandelbrotMath.complexXAtScreen(
            focusX, offsetX, oldZoom, viewWidth, viewHeight,
        )
        val beforeY = MandelbrotMath.complexYAtScreen(
            focusY, offsetY, oldZoom, viewWidth, viewHeight,
        )
        val (newOffsetX, newOffsetY) = MandelbrotMath.offsetAfterZoomChange(
            focusX, focusY, oldZoom, newZoom, offsetX, offsetY, viewWidth, viewHeight,
        )
        val afterX = MandelbrotMath.complexXAtScreen(
            focusX, newOffsetX, newZoom, viewWidth, viewHeight,
        )
        val afterY = MandelbrotMath.complexYAtScreen(
            focusY, newOffsetY, newZoom, viewWidth, viewHeight,
        )

        assertEquals(beforeX, afterX, 1e-12)
        assertEquals(beforeY, afterY, 1e-12)
    }

    @Test
    fun `zooming out also keeps the focus point fixed`() {
        val focusX = 100f
        val focusY = 500f
        val oldZoom = 8.0
        val newZoom = 2.0
        val offsetX = -0.75
        val offsetY = 0.1
        val viewWidth = 800
        val viewHeight = 600

        val beforeX = MandelbrotMath.complexXAtScreen(
            focusX, offsetX, oldZoom, viewWidth, viewHeight,
        )
        val beforeY = MandelbrotMath.complexYAtScreen(
            focusY, offsetY, oldZoom, viewWidth, viewHeight,
        )
        val (newOffsetX, newOffsetY) = MandelbrotMath.offsetAfterZoomChange(
            focusX, focusY, oldZoom, newZoom, offsetX, offsetY, viewWidth, viewHeight,
        )
        val afterX = MandelbrotMath.complexXAtScreen(
            focusX, newOffsetX, newZoom, viewWidth, viewHeight,
        )
        val afterY = MandelbrotMath.complexYAtScreen(
            focusY, newOffsetY, newZoom, viewWidth, viewHeight,
        )

        assertEquals(beforeX, afterX, 1e-12)
        assertEquals(beforeY, afterY, 1e-12)
    }

    @Test
    fun `clamped zoom respects min and max bounds`() {
        assertEquals(0.5, MandelbrotMath.clampedZoom(1.0, 0.1, 0.5, 1.0e13), 1e-12)
        assertEquals(1.0e13, MandelbrotMath.clampedZoom(1.0e12, 20.0, 0.5, 1.0e13), 1e-9)
        assertEquals(4.0, MandelbrotMath.clampedZoom(2.0, 2.0, 0.5, 10.0), 1e-12)
    }

    @Test
    fun `clamped zoom leaves value unchanged when already at a bound`() {
        assertEquals(0.5, MandelbrotMath.clampedZoom(0.5, 0.5, 0.5, 1.0e13), 1e-12)
        assertEquals(1.0e13, MandelbrotMath.clampedZoom(1.0e13, 2.0, 0.5, 1.0e13), 1e-9)
    }

    @Test
    fun `coerced zoom clamps restored viewport values to the allowed range`() {
        assertEquals(0.5, MandelbrotMath.coercedZoom(0.1, minZoom = 0.5, maxZoom = 1.0e13), 1e-12)
        assertEquals(1.0e13, MandelbrotMath.coercedZoom(2.0e13, minZoom = 0.5, maxZoom = 1.0e13), 1e-9)
        assertEquals(4.0, MandelbrotMath.coercedZoom(4.0, minZoom = 0.5, maxZoom = 1.0e13), 1e-12)
    }

    @Test
    fun `stale bitmap transform is identity when viewport matches the bitmap`() {
        val (scale, dx, dy) = MandelbrotMath.staleBitmapDrawTransform(
            zoom = 2.0,
            bitmapZoom = 2.0,
            offsetX = -0.5,
            offsetY = 0.1,
            bitmapOffsetX = -0.5,
            bitmapOffsetY = 0.1,
            viewWidth = 800,
            viewHeight = 600,
        )
        assertEquals(1f, scale, 1e-6f)
        assertEquals(0f, dx, 1e-6f)
        assertEquals(0f, dy, 1e-6f)
    }

    @Test
    fun `stale bitmap transform scales when zoom changes before rerender`() {
        val (scale, _, _) = MandelbrotMath.staleBitmapDrawTransform(
            zoom = 4.0,
            bitmapZoom = 2.0,
            offsetX = -0.5,
            offsetY = 0.0,
            bitmapOffsetX = -0.5,
            bitmapOffsetY = 0.0,
            viewWidth = 800,
            viewHeight = 600,
        )
        assertEquals(2f, scale, 1e-6f)
    }

    @Test
    fun `stale bitmap transform pans when the viewport center moves before rerender`() {
        val units = MandelbrotMath.unitsPerPixel(1.0, 800, 600)
        val panRight = 0.01
        val (_, dx, _) = MandelbrotMath.staleBitmapDrawTransform(
            zoom = 1.0,
            bitmapZoom = 1.0,
            offsetX = -0.5 + panRight,
            offsetY = 0.0,
            bitmapOffsetX = -0.5,
            bitmapOffsetY = 0.0,
            viewWidth = 800,
            viewHeight = 600,
        )
        assertEquals((-panRight / units).toFloat(), dx, 1e-4f)
    }

    @Test
    fun `stale bitmap transform pans vertically when the viewport center moves before rerender`() {
        val units = MandelbrotMath.unitsPerPixel(1.0, 800, 600)
        val panDown = 0.01
        val (_, _, dy) = MandelbrotMath.staleBitmapDrawTransform(
            zoom = 1.0,
            bitmapZoom = 1.0,
            offsetX = -0.5,
            offsetY = 0.0 + panDown,
            bitmapOffsetX = -0.5,
            bitmapOffsetY = 0.0,
            viewWidth = 800,
            viewHeight = 600,
        )
        assertEquals((-panDown / units).toFloat(), dy, 1e-4f)
    }

    @Test
    fun `render target matches the live viewport only when all coordinates agree`() {
        assertTrue(
            MandelbrotMath.renderTargetMatchesViewport(
                zoom = 2.0,
                offsetX = -0.5,
                offsetY = 0.1,
                renderZoom = 2.0,
                renderOffsetX = -0.5,
                renderOffsetY = 0.1,
            ),
        )
        assertFalse(
            MandelbrotMath.renderTargetMatchesViewport(
                zoom = 4.0,
                offsetX = -0.5,
                offsetY = 0.1,
                renderZoom = 2.0,
                renderOffsetX = -0.5,
                renderOffsetY = 0.1,
            ),
        )
        assertFalse(
            MandelbrotMath.renderTargetMatchesViewport(
                zoom = 2.0,
                offsetX = -0.4,
                offsetY = 0.1,
                renderZoom = 2.0,
                renderOffsetX = -0.5,
                renderOffsetY = 0.1,
            ),
        )
        assertFalse(
            MandelbrotMath.renderTargetMatchesViewport(
                zoom = 2.0,
                offsetX = -0.5,
                offsetY = 0.2,
                renderZoom = 2.0,
                renderOffsetX = -0.5,
                renderOffsetY = 0.1,
            ),
        )
    }

    @Test
    fun `render result applies only when viewport and palette still match`() {
        val viewport = arrayOf(
            2.0, -0.5, 0.1,
            2.0, -0.5, 0.1,
        )
        assertTrue(
            MandelbrotMath.shouldApplyRenderResult(
                zoom = viewport[0],
                offsetX = viewport[1],
                offsetY = viewport[2],
                renderZoom = viewport[3],
                renderOffsetX = viewport[4],
                renderOffsetY = viewport[5],
                renderPalette = "golden",
                currentPalette = "golden",
            ),
        )
        assertFalse(
            MandelbrotMath.shouldApplyRenderResult(
                zoom = viewport[0],
                offsetX = viewport[1],
                offsetY = viewport[2],
                renderZoom = viewport[3],
                renderOffsetX = viewport[4],
                renderOffsetY = viewport[5],
                renderPalette = "golden",
                currentPalette = "blue",
            ),
        )
        assertFalse(
            MandelbrotMath.shouldApplyRenderResult(
                zoom = 4.0,
                offsetX = viewport[1],
                offsetY = viewport[2],
                renderZoom = viewport[3],
                renderOffsetX = viewport[4],
                renderOffsetY = viewport[5],
                renderPalette = "golden",
                currentPalette = "golden",
            ),
        )
    }

    @Test
    fun `needs full render when nothing has been drawn yet`() {
        assertTrue(
            MandelbrotMath.needsFullRender(
                hasRenderedOnce = false,
                bitmapIsPreview = false,
                zoom = 1.0,
                bitmapZoom = 1.0,
                offsetX = -0.5,
                bitmapOffsetX = -0.5,
                offsetY = 0.0,
                bitmapOffsetY = 0.0,
            ),
        )
    }

    @Test
    fun `needs full render after a preview or viewport mismatch`() {
        val settled = MandelbrotMath.needsFullRender(
            hasRenderedOnce = true,
            bitmapIsPreview = false,
            zoom = 1.0,
            bitmapZoom = 1.0,
            offsetX = -0.5,
            bitmapOffsetX = -0.5,
            offsetY = 0.0,
            bitmapOffsetY = 0.0,
        )
        assertFalse(settled)

        assertTrue(
            MandelbrotMath.needsFullRender(
                hasRenderedOnce = true,
                bitmapIsPreview = true,
                zoom = 1.0,
                bitmapZoom = 1.0,
                offsetX = -0.5,
                bitmapOffsetX = -0.5,
                offsetY = 0.0,
                bitmapOffsetY = 0.0,
            ),
        )
        assertTrue(
            MandelbrotMath.needsFullRender(
                hasRenderedOnce = true,
                bitmapIsPreview = false,
                zoom = 2.0,
                bitmapZoom = 1.0,
                offsetX = -0.5,
                bitmapOffsetX = -0.5,
                offsetY = 0.0,
                bitmapOffsetY = 0.0,
            ),
        )
        assertTrue(
            MandelbrotMath.needsFullRender(
                hasRenderedOnce = true,
                bitmapIsPreview = false,
                zoom = 1.0,
                bitmapZoom = 1.0,
                offsetX = -0.4,
                bitmapOffsetX = -0.5,
                offsetY = 0.0,
                bitmapOffsetY = 0.0,
            ),
        )
        assertTrue(
            MandelbrotMath.needsFullRender(
                hasRenderedOnce = true,
                bitmapIsPreview = false,
                zoom = 1.0,
                bitmapZoom = 1.0,
                offsetX = -0.5,
                bitmapOffsetX = -0.5,
                offsetY = 0.2,
                bitmapOffsetY = 0.0,
            ),
        )
    }

    @Test
    fun `render dimensions never collapse to zero`() {
        assertEquals(200 to 150, MandelbrotMath.renderDimensions(800, 600, downscale = 4))
        assertEquals(1 to 1, MandelbrotMath.renderDimensions(2, 2, downscale = 4))
        assertEquals(1 to 1, MandelbrotMath.renderDimensions(0, 0, downscale = 4))
    }
}
