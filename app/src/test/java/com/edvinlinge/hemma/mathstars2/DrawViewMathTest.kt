package com.edvinlinge.hemma.mathstars2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawViewMathTest {

    @Test
    fun `clamped zoom respects min and max bounds`() {
        assertEquals(0.5f, DrawViewMath.clampedZoom(1.0f, 0.1f, 0.5f, 10.0f), 1e-6f)
        assertEquals(10.0f, DrawViewMath.clampedZoom(8.0f, 2.0f, 0.5f, 10.0f), 1e-6f)
        assertEquals(4.0f, DrawViewMath.clampedZoom(2.0f, 2.0f, 0.5f, 10.0f), 1e-6f)
    }

    @Test
    fun `zooming in keeps the focus point fixed on screen`() {
        val focusX = 240f
        val focusY = 180f
        val oldZoom = 1.0f
        val newZoom = 2.5f
        val offsetX = 30f
        val offsetY = -20f
        val viewWidth = 800
        val viewHeight = 600

        val contentX = (focusX - viewWidth / 2f - offsetX) / oldZoom
        val contentY = (focusY - viewHeight / 2f - offsetY) / oldZoom

        val (newOffsetX, newOffsetY) = DrawViewMath.offsetAfterZoomChange(
            focusX, focusY, oldZoom, newZoom, offsetX, offsetY, viewWidth, viewHeight,
        )

        assertEquals(focusX - viewWidth / 2f - newZoom * contentX, newOffsetX, 1e-5f)
        assertEquals(focusY - viewHeight / 2f - newZoom * contentY, newOffsetY, 1e-5f)
    }

    @Test
    fun `zooming out also keeps the focus point fixed`() {
        val focusX = 640f
        val focusY = 420f
        val oldZoom = 4.0f
        val newZoom = 1.0f
        val offsetX = -50f
        val offsetY = 75f
        val viewWidth = 800
        val viewHeight = 600

        val contentX = (focusX - viewWidth / 2f - offsetX) / oldZoom
        val contentY = (focusY - viewHeight / 2f - offsetY) / oldZoom

        val (newOffsetX, newOffsetY) = DrawViewMath.offsetAfterZoomChange(
            focusX, focusY, oldZoom, newZoom, offsetX, offsetY, viewWidth, viewHeight,
        )

        assertEquals(focusX - viewWidth / 2f - newZoom * contentX, newOffsetX, 1e-5f)
        assertEquals(focusY - viewHeight / 2f - newZoom * contentY, newOffsetY, 1e-5f)
    }

    @Test
    fun `zoom round trip restores the original offset`() {
        val focusX = 100f
        val focusY = 500f
        val startZoom = 1.5f
        val endZoom = 6.0f
        val startOffsetX = 12f
        val startOffsetY = -8f
        val viewWidth = 800
        val viewHeight = 600

        val (midOffsetX, midOffsetY) = DrawViewMath.offsetAfterZoomChange(
            focusX, focusY, startZoom, endZoom, startOffsetX, startOffsetY, viewWidth, viewHeight,
        )
        val (restoredOffsetX, restoredOffsetY) = DrawViewMath.offsetAfterZoomChange(
            focusX, focusY, endZoom, startZoom, midOffsetX, midOffsetY, viewWidth, viewHeight,
        )

        assertEquals(startOffsetX, restoredOffsetX, 1e-4f)
        assertEquals(startOffsetY, restoredOffsetY, 1e-4f)
    }

    @Test
    fun `unity zoom change leaves offsets unchanged`() {
        val (offsetX, offsetY) = DrawViewMath.offsetAfterZoomChange(
            focusX = 200f,
            focusY = 300f,
            oldZoom = 2.0f,
            newZoom = 2.0f,
            offsetX = 15f,
            offsetY = -25f,
            viewWidth = 800,
            viewHeight = 600,
        )
        assertEquals(15f, offsetX, 1e-6f)
        assertEquals(-25f, offsetY, 1e-6f)
    }

    @Test
    fun `focus at view center with zero offset stays centered when zoom changes`() {
        val viewWidth = 800
        val viewHeight = 600

        val (offsetX, offsetY) = DrawViewMath.offsetAfterZoomChange(
            focusX = viewWidth / 2f,
            focusY = viewHeight / 2f,
            oldZoom = 1.0f,
            newZoom = 3.0f,
            offsetX = 0f,
            offsetY = 0f,
            viewWidth = viewWidth,
            viewHeight = viewHeight,
        )

        assertEquals(0f, offsetX, 1e-6f)
        assertEquals(0f, offsetY, 1e-6f)
    }

    @Test
    fun `animation duration scales inversely with speed multiplier`() {
        assertEquals(5000L, DrawViewMath.animationDurationMs(speedMultiplier = 1.0f))
        assertEquals(2500L, DrawViewMath.animationDurationMs(speedMultiplier = 2.0f))
        assertEquals(10_000L, DrawViewMath.animationDurationMs(speedMultiplier = 0.5f))
    }

    @Test
    fun `non positive speed multipliers leave duration unchanged`() {
        assertNull(DrawViewMath.animationDurationMs(speedMultiplier = 0f))
        assertNull(DrawViewMath.animationDurationMs(speedMultiplier = -1f))
    }

    @Test
    fun `instant render kicks in at the speed slider maximum`() {
        assertFalse(DrawViewMath.shouldRenderInstantly(speed = 3.9f))
        assertTrue(DrawViewMath.shouldRenderInstantly(speed = 4.0f))
        assertTrue(DrawViewMath.shouldRenderInstantly(speed = 5.0f))
    }

    @Test
    fun `coerced phase clamps out of range values`() {
        assertEquals(0f, DrawViewMath.coercedPhase(-0.5f), 1e-6f)
        assertEquals(0f, DrawViewMath.coercedPhase(0f), 1e-6f)
        assertEquals(0.25f, DrawViewMath.coercedPhase(0.25f), 1e-6f)
        assertEquals(1f, DrawViewMath.coercedPhase(1f), 1e-6f)
        assertEquals(1f, DrawViewMath.coercedPhase(2f), 1e-6f)
    }

    @Test
    fun `revealed segment length shrinks as the phase approaches complete`() {
        val pathLength = 100f
        assertEquals(100f, DrawViewMath.revealedSegmentLength(pathLength, phase = 0f), 1e-6f)
        assertEquals(50f, DrawViewMath.revealedSegmentLength(pathLength, phase = 0.5f), 1e-6f)
        assertEquals(0f, DrawViewMath.revealedSegmentLength(pathLength, phase = 1f), 1e-6f)
    }

    @Test
    fun `revealed segment length is zero for empty paths`() {
        assertEquals(0f, DrawViewMath.revealedSegmentLength(pathLength = 0f, phase = 0f), 1e-6f)
        assertEquals(0f, DrawViewMath.revealedSegmentLength(pathLength = -1f, phase = 0f), 1e-6f)
    }

    @Test
    fun `remaining animation duration scales with the current phase`() {
        assertEquals(5000L, DrawViewMath.remainingAnimationDurationMs(currentPhase = 1f, animationDurationMs = 5000L))
        assertEquals(2500L, DrawViewMath.remainingAnimationDurationMs(currentPhase = 0.5f, animationDurationMs = 5000L))
        assertEquals(0L, DrawViewMath.remainingAnimationDurationMs(currentPhase = 0f, animationDurationMs = 5000L))
    }

    @Test
    fun `remaining animation duration clamps out of range phases`() {
        assertEquals(5000L, DrawViewMath.remainingAnimationDurationMs(currentPhase = 2f, animationDurationMs = 5000L))
        assertEquals(0L, DrawViewMath.remainingAnimationDurationMs(currentPhase = -0.5f, animationDurationMs = 5000L))
    }

    @Test
    fun `reveal restore shows complete when already finished or instant`() {
        assertEquals(
            RevealRestoreAction.SHOW_COMPLETE,
            DrawViewMath.revealRestoreAction(currentPhase = 0f, instantRender = false, pathLength = 100f),
        )
        assertEquals(
            RevealRestoreAction.SHOW_COMPLETE,
            DrawViewMath.revealRestoreAction(currentPhase = -0.5f, instantRender = false, pathLength = 100f),
        )
        assertEquals(
            RevealRestoreAction.SHOW_COMPLETE,
            DrawViewMath.revealRestoreAction(currentPhase = 0.5f, instantRender = true, pathLength = 100f),
        )
    }

    @Test
    fun `reveal restore skips when geometry is not ready yet`() {
        assertEquals(
            RevealRestoreAction.SKIP,
            DrawViewMath.revealRestoreAction(currentPhase = 0.5f, instantRender = false, pathLength = 0f),
        )
        assertEquals(
            RevealRestoreAction.SKIP,
            DrawViewMath.revealRestoreAction(currentPhase = 0.5f, instantRender = false, pathLength = -1f),
        )
    }

    @Test
    fun `reveal restore resumes a mid animation phase after configuration change`() {
        assertEquals(
            RevealRestoreAction.RESUME,
            DrawViewMath.revealRestoreAction(currentPhase = 0.5f, instantRender = false, pathLength = 100f),
        )
        assertEquals(
            RevealRestoreAction.RESUME,
            DrawViewMath.revealRestoreAction(currentPhase = 2f, instantRender = false, pathLength = 100f),
        )
    }
}
