package com.edvinlinge.hemma.mathstars2

/**
 * Screen-space pan/zoom math for [DrawView], free of Android types so it can be covered by fast
 * JVM unit tests. Pinch and double-tap zoom keep the drawing under the focus point fixed.
 */
/** What to do with a reveal animation after saved view state is restored. */
internal enum class RevealRestoreAction {
    SHOW_COMPLETE,
    SKIP,
    RESUME,
}

internal object DrawViewMath {

    /** At and above this speed the star is drawn instantly instead of animated. */
    const val INSTANT_SPEED_THRESHOLD = 4.0f
    const val BASE_ANIMATION_DURATION_MS = 5000f

    fun clampedZoom(currentZoom: Float, factor: Float, minZoom: Float, maxZoom: Float): Float =
        (currentZoom * factor).coerceIn(minZoom, maxZoom)

    /** Clamps a restored viewport zoom to the allowed range after configuration changes. */
    fun coercedZoom(zoom: Float, minZoom: Float, maxZoom: Float): Float =
        zoom.coerceIn(minZoom, maxZoom)

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

    /** Elapsed time into a linear 1→0 reveal that is currently at [currentPhase]. */
    fun animationPlayTimeMs(currentPhase: Float, animationDurationMs: Long): Long =
        ((1f - coercedPhase(currentPhase)) * animationDurationMs).toLong()

    /**
     * True when a speed change should restart the in-progress reveal from the current
     * phase, so the new duration applies immediately instead of on the next replay.
     */
    fun shouldRetargetRevealSpeed(
        isRevealing: Boolean,
        instantRender: Boolean,
        currentPhase: Float,
    ): Boolean = isRevealing && !instantRender && coercedPhase(currentPhase) > 0f

    /**
     * Chooses how to resume the reveal animation after [onRestoreInstanceState], when
     * [onSizeChanged] may already have started a fresh animation at phase 1.
     */
    fun revealRestoreAction(
        currentPhase: Float,
        instantRender: Boolean,
        pathLength: Float,
    ): RevealRestoreAction = when {
        coercedPhase(currentPhase) <= 0f || instantRender -> RevealRestoreAction.SHOW_COMPLETE
        pathLength <= 0f -> RevealRestoreAction.SKIP
        else -> RevealRestoreAction.RESUME
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
