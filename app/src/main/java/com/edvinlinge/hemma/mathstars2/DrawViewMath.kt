package com.edvinlinge.hemma.mathstars2

/**
 * Screen-space pan/zoom math for [DrawView], free of Android types so it can be covered by fast
 * JVM unit tests. Pinch and double-tap zoom keep the drawing under the focus point fixed.
 */
internal object DrawViewMath {

    /** At and above this speed the star is drawn instantly instead of animated. */
    const val INSTANT_SPEED_THRESHOLD = 4.0f
    const val BASE_ANIMATION_DURATION_MS = 5000f

    fun clampedZoom(currentZoom: Float, factor: Float, minZoom: Float, maxZoom: Float): Float =
        (currentZoom * factor).coerceIn(minZoom, maxZoom)

    /** Returns null when [speedMultiplier] is not positive; otherwise duration in milliseconds. */
    fun animationDurationMs(
        speedMultiplier: Float,
        baseDurationMs: Float = BASE_ANIMATION_DURATION_MS,
    ): Long? {
        if (speedMultiplier <= 0f) return null
        return (baseDurationMs / speedMultiplier).toLong()
    }

    fun shouldRenderInstantly(speed: Float, threshold: Float = INSTANT_SPEED_THRESHOLD): Boolean =
        speed >= threshold

    /** Clamps reveal progress to the closed interval from fully hidden to complete. */
    fun coercedPhase(phase: Float): Float = phase.coerceIn(0f, 1f)

    /** Length of the path segment revealed at [phase], given the full [pathLength]. */
    fun revealedSegmentLength(pathLength: Float, phase: Float): Float {
        if (pathLength <= 0f) return 0f
        return (1f - coercedPhase(phase)) * pathLength
    }

    /** Remaining reveal animation time when resuming from [currentPhase]. */
    fun remainingAnimationDurationMs(currentPhase: Float, animationDurationMs: Long): Long =
        (coercedPhase(currentPhase) * animationDurationMs).toLong()

    /** What to do after restoring reveal progress from a saved instance state. */
    enum class RevealRestoreAction {
        /** Animation already finished or instant render was requested. */
        ShowComplete,
        /** Path not built yet; wait for the next layout pass. */
        NoOp,
        /** Resume the reveal animator from [remainingAnimationDurationMs]. */
        ResumeAnimation,
    }

    fun revealRestoreAction(
        currentPhase: Float,
        instantRender: Boolean,
        pathLength: Float,
    ): RevealRestoreAction = when {
        currentPhase <= 0f || instantRender -> RevealRestoreAction.ShowComplete
        pathLength <= 0f -> RevealRestoreAction.NoOp
        else -> RevealRestoreAction.ResumeAnimation
    }

    /**
     * Adjusts pan offsets after a zoom change so the content under ([focusX], [focusY]) stays
     * fixed on screen.
     */
    fun offsetAfterZoomChange(
        focusX: Float,
        focusY: Float,
        oldZoom: Float,
        newZoom: Float,
        offsetX: Float,
        offsetY: Float,
        viewWidth: Int,
        viewHeight: Int,
    ): Pair<Float, Float> {
        val growth = newZoom / oldZoom
        val newOffsetX = offsetX + (focusX - viewWidth / 2f - offsetX) * (1f - growth)
        val newOffsetY = offsetY + (focusY - viewHeight / 2f - offsetY) * (1f - growth)
        return newOffsetX to newOffsetY
    }
}
