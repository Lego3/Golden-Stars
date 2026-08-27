package com.edvinlinge.hemma.mathstars2

import kotlin.math.roundToLong

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

/**
 * Canonical in-progress reveal that is not re-derived from the animator's quantized
 * play time on every speed-slider tick.
 *
 * [revealed] is 0 when nothing is drawn and 1 when the stroke is complete.
 * [lastPlayTimeMs] is the whole-millisecond seek last applied at [durationMs].
 */
internal data class RevealProgress(
    val revealed: Double,
    val lastPlayTimeMs: Long,
    val durationMs: Long,
)

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
    fun remainingAnimationDurationMs(currentPhase: Float, animationDurationMs: Long): Long {
        if (animationDurationMs <= 0L) return 0L
        return animationDurationMs - animationPlayTimeMs(currentPhase, animationDurationMs)
    }

    /**
     * Whole-millisecond play time for a linear 1→0 reveal at [revealed] of the stroke
     * (0 = hidden, 1 = complete). Rounds to nearest; the live speed slider must keep
     * [RevealProgress.revealed] as the source of truth so this quantization cannot
     * accumulate.
     */
    fun playTimeMs(revealed: Double, durationMs: Long): Long {
        if (durationMs <= 0L) return 0L
        return (revealed.coerceIn(0.0, 1.0) * durationMs).roundToLong().coerceIn(0L, durationMs)
    }

    /**
     * One-shot elapsed time into a linear 1→0 reveal at [currentPhase].
     * Live speed changes should use [retargetRevealProgress] instead of feeding the
     * resulting play time back into [currentPhase].
     */
    fun animationPlayTimeMs(currentPhase: Float, animationDurationMs: Long): Long =
        playTimeMs(1.0 - coercedPhase(currentPhase).toDouble(), animationDurationMs)

    /** Seeds [RevealProgress] from a 1→0 phase. Used once when a reveal starts. */
    fun revealProgressFromPhase(phase: Float, durationMs: Long): RevealProgress {
        val revealed = (1.0 - coercedPhase(phase).toDouble()).coerceIn(0.0, 1.0)
        return RevealProgress(
            revealed = revealed,
            lastPlayTimeMs = playTimeMs(revealed, durationMs),
            durationMs = durationMs.coerceAtLeast(0L),
        )
    }

    /**
     * Advances [progress] by the play-time delta since the last snapshot, at the
     * duration that was in effect for that interval.
     */
    fun advanceRevealProgress(progress: RevealProgress, currentPlayTimeMs: Long): RevealProgress {
        val duration = progress.durationMs
        if (duration <= 0L) {
            return progress.copy(revealed = progress.revealed.coerceIn(0.0, 1.0))
        }
        val delta = (currentPlayTimeMs - progress.lastPlayTimeMs).coerceAtLeast(0L)
        val revealed = (progress.revealed + delta.toDouble() / duration.toDouble()).coerceIn(0.0, 1.0)
        return RevealProgress(
            revealed = revealed,
            lastPlayTimeMs = currentPlayTimeMs.coerceIn(0L, duration),
            durationMs = duration,
        )
    }

    /**
     * Applies a new duration while keeping [RevealProgress.revealed] as a double.
     * The new play time is rounded for the animator; that rounding is not written
     * back into [RevealProgress.revealed], so slider ticks cannot accumulate error.
     */
    fun retargetRevealProgress(
        progress: RevealProgress,
        currentPlayTimeMs: Long,
        newDurationMs: Long,
    ): RevealProgress {
        val advanced = advanceRevealProgress(progress, currentPlayTimeMs)
        val duration = newDurationMs.coerceAtLeast(0L)
        return RevealProgress(
            revealed = advanced.revealed,
            lastPlayTimeMs = playTimeMs(advanced.revealed, duration),
            durationMs = duration,
        )
    }

    /**
     * True when a speed change should seek the in-progress reveal from stored
     * progress, so the new duration applies immediately instead of on the next replay.
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
