package com.edvinlinge.hemma.mathstars2

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Parametric hypotrochoid and epitrochoid curves, free of Android types so they can be covered
 * by fast JVM unit tests. Integer radii behave like Spirograph gears: the trace closes after
 * `rolling / gcd(fixed, rolling)` turns, with `fixed / gcd` lobes.
 */
internal object SpirographMath {

    const val MIN_FIXED = 20
    const val MAX_FIXED = 180
    const val MIN_ROLLING = 8
    const val MAX_ROLLING = 90
    const val MIN_PEN = 0
    const val MAX_PEN = 100

    const val DEFAULT_FIXED = 96
    const val DEFAULT_ROLLING = 32
    const val DEFAULT_PEN = 32
    const val DEFAULT_INSIDE = true

    const val SAMPLES_PER_TURN = 240
    const val MIN_SAMPLES = 720
    const val MAX_SAMPLES = 8000

    data class Params(
        val fixedRadius: Int,
        val rollingRadius: Int,
        val penOffset: Int,
        val inside: Boolean,
    )

    data class Details(
        val params: Params,
        val gcd: Int,
        val lobes: Int,
        val periodTurns: Int,
        val hypocycloid: Boolean,
        val epicycloid: Boolean,
        val circle: Boolean,
    )

    fun normalized(
        fixedRadius: Int,
        rollingRadius: Int,
        penOffset: Int,
        inside: Boolean,
    ): Params {
        val fixed = fixedRadius.coerceIn(MIN_FIXED, MAX_FIXED)
        val rolling = coercedRolling(fixed, rollingRadius, inside)
        val pen = penOffset.coerceIn(MIN_PEN, MAX_PEN)
        return Params(fixed, rolling, pen, inside)
    }

    fun coercedRolling(fixedRadius: Int, rollingRadius: Int, inside: Boolean): Int {
        val maxRolling = if (inside) {
            (fixedRadius - 1).coerceIn(MIN_ROLLING, MAX_ROLLING)
        } else {
            MAX_ROLLING
        }
        return rollingRadius.coerceIn(MIN_ROLLING, maxRolling)
    }

    fun gcd(fixedRadius: Int, rollingRadius: Int): Int =
        StarMath.gcd(fixedRadius.coerceAtLeast(1), rollingRadius.coerceAtLeast(1))

    /** Revolutions of the rolling wheel until the pen returns to its start. */
    fun periodTurns(fixedRadius: Int, rollingRadius: Int): Int {
        val rolling = rollingRadius.coerceAtLeast(1)
        return rolling / gcd(fixedRadius, rolling)
    }

    /** Number of lobes (or cusps, when the pen sits on the rim). */
    fun lobeCount(fixedRadius: Int, rollingRadius: Int): Int {
        val fixed = fixedRadius.coerceAtLeast(1)
        val rolling = rollingRadius.coerceAtLeast(1)
        return fixed / gcd(fixed, rolling)
    }

    fun details(params: Params): Details {
        val divisor = gcd(params.fixedRadius, params.rollingRadius)
        val onRim = params.penOffset == params.rollingRadius
        return Details(
            params = params,
            gcd = divisor,
            lobes = lobeCount(params.fixedRadius, params.rollingRadius),
            periodTurns = periodTurns(params.fixedRadius, params.rollingRadius),
            hypocycloid = params.inside && onRim,
            epicycloid = !params.inside && onRim,
            circle = params.penOffset == 0,
        )
    }

    /**
     * Point on the curve at angle [t] radians, in the curve's own units (not screen pixels).
     * At t = 0 a hypotrochoid sits at `((R - r) + d, 0)` and an epitrochoid at `((R + r) - d, 0)`.
     */
    fun pointAt(
        t: Double,
        fixedRadius: Double,
        rollingRadius: Double,
        penOffset: Double,
        inside: Boolean,
    ): Pair<Double, Double> {
        val rolling = rollingRadius.coerceAtLeast(1e-9)
        return if (inside) {
            val k = (fixedRadius - rolling) / rolling
            val x = (fixedRadius - rolling) * cos(t) + penOffset * cos(k * t)
            val y = (fixedRadius - rolling) * sin(t) - penOffset * sin(k * t)
            x to y
        } else {
            val k = (fixedRadius + rolling) / rolling
            val x = (fixedRadius + rolling) * cos(t) - penOffset * cos(k * t)
            val y = (fixedRadius + rolling) * sin(t) - penOffset * sin(k * t)
            x to y
        }
    }

    fun sampleCount(periodTurns: Int): Int =
        (periodTurns * SAMPLES_PER_TURN).coerceIn(MIN_SAMPLES, MAX_SAMPLES)

    /** Evenly spaced points covering one full closed period, including the repeated start point. */
    fun curvePoints(params: Params): List<Pair<Double, Double>> {
        val turns = periodTurns(params.fixedRadius, params.rollingRadius)
        val samples = sampleCount(turns)
        val tMax = 2.0 * PI * turns
        val R = params.fixedRadius.toDouble()
        val r = params.rollingRadius.toDouble()
        val d = params.penOffset.toDouble()
        return List(samples + 1) { index ->
            val t = tMax * index / samples
            pointAt(t, R, r, d, params.inside)
        }
    }

    /**
     * Scales curve coordinates into the view so the figure sits in a circle of [radius]
     * around ([centerX], [centerY]).
     */
    fun fitToView(
        points: List<Pair<Double, Double>>,
        centerX: Float,
        centerY: Float,
        radius: Float,
    ): List<Pair<Float, Float>> {
        if (points.isEmpty()) return emptyList()
        val maxRadius = points.maxOf { (x, y) -> hypot(x, y) }.coerceAtLeast(1e-9)
        val scale = radius / maxRadius
        return points.map { (x, y) ->
            (centerX + (x * scale).toFloat()) to (centerY + (y * scale).toFloat())
        }
    }
}
