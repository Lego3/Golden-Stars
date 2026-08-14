package com.edvinlinge.hemma.mathstars2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class SpirographMathTest {

    @Test
    fun `deltoid parameters close after one turn with three cusps`() {
        val params = SpirographMath.Params(fixedRadius = 3, rollingRadius = 1, penOffset = 1, inside = true)
        assertEquals(1, SpirographMath.gcd(3, 1))
        assertEquals(1, SpirographMath.periodTurns(3, 1))
        assertEquals(3, SpirographMath.lobeCount(3, 1))
        val details = SpirographMath.details(params)
        assertTrue(details.hypocycloid)
        assertFalse(details.epicycloid)
        assertFalse(details.circle)
    }

    @Test
    fun `classic three lobe defaults share a gcd of 32`() {
        assertEquals(32, SpirographMath.gcd(96, 32))
        assertEquals(1, SpirographMath.periodTurns(96, 32))
        assertEquals(3, SpirographMath.lobeCount(96, 32))
        val details = SpirographMath.details(
            SpirographMath.normalized(
                SpirographMath.DEFAULT_FIXED,
                SpirographMath.DEFAULT_ROLLING,
                SpirographMath.DEFAULT_PEN,
                SpirographMath.DEFAULT_INSIDE,
            ),
        )
        assertTrue(details.hypocycloid)
        assertEquals(3, details.lobes)
    }

    @Test
    fun `coprime radii take as many turns as the rolling wheel`() {
        assertEquals(1, SpirographMath.gcd(100, 31))
        assertEquals(31, SpirographMath.periodTurns(100, 31))
        assertEquals(100, SpirographMath.lobeCount(100, 31))
    }

    @Test
    fun `hypotrochoid starts at R minus r plus d on the x axis`() {
        val (x, y) = SpirographMath.pointAt(
            t = 0.0,
            fixedRadius = 96.0,
            rollingRadius = 32.0,
            penOffset = 32.0,
            inside = true,
        )
        assertEquals(96.0, x, 1e-12)
        assertEquals(0.0, y, 1e-12)
    }

    @Test
    fun `epitrochoid starts at R plus r minus d on the x axis`() {
        val (x, y) = SpirographMath.pointAt(
            t = 0.0,
            fixedRadius = 60.0,
            rollingRadius = 20.0,
            penOffset = 10.0,
            inside = false,
        )
        assertEquals(70.0, x, 1e-12)
        assertEquals(0.0, y, 1e-12)
    }

    @Test
    fun `sampled hypotrochoid returns to its start`() {
        val params = SpirographMath.Params(96, 32, 32, inside = true)
        val points = SpirographMath.curvePoints(params)
        val first = points.first()
        val last = points.last()
        assertEquals(first.first, last.first, 1e-9)
        assertEquals(first.second, last.second, 1e-9)
        assertTrue(points.size > SpirographMath.MIN_SAMPLES)
    }

    @Test
    fun `sampled epitrochoid returns to its start`() {
        val params = SpirographMath.Params(80, 30, 20, inside = false)
        val points = SpirographMath.curvePoints(params)
        val first = points.first()
        val last = points.last()
        assertEquals(first.first, last.first, 1e-9)
        assertEquals(first.second, last.second, 1e-9)
    }

    @Test
    fun `pen at the wheel centre traces a circle`() {
        val params = SpirographMath.Params(90, 30, 0, inside = true)
        assertTrue(SpirographMath.details(params).circle)
        val points = SpirographMath.curvePoints(params)
        val radius = hypot(points.first().first, points.first().second)
        assertEquals(60.0, radius, 1e-9)
        for ((x, y) in points) {
            assertEquals(radius, hypot(x, y), 1e-6)
        }
    }

    @Test
    fun `inside rolling radius stays smaller than the fixed ring`() {
        val rolling = SpirographMath.coercedRolling(fixedRadius = 40, rollingRadius = 90, inside = true)
        assertEquals(39, rolling)
        val params = SpirographMath.normalized(10, 5, -3, inside = true)
        assertEquals(SpirographMath.MIN_FIXED, params.fixedRadius)
        assertTrue(params.rollingRadius < params.fixedRadius)
        assertEquals(SpirographMath.MIN_PEN, params.penOffset)
    }

    @Test
    fun `outside rolling radius can exceed the fixed ring`() {
        val rolling = SpirographMath.coercedRolling(fixedRadius = 40, rollingRadius = 90, inside = false)
        assertEquals(SpirographMath.MAX_ROLLING, rolling)
    }

    @Test
    fun `fit to view returns empty list for no points`() {
        assertTrue(
            SpirographMath.fitToView(emptyList(), centerX = 100f, centerY = 80f, radius = 40f).isEmpty(),
        )
    }

    @Test
    fun `fit to view centres the curve and respects the target radius`() {
        val points = listOf(2.0 to 0.0, 0.0 to 2.0, -2.0 to 0.0, 0.0 to -2.0)
        val fitted = SpirographMath.fitToView(points, centerX = 100f, centerY = 80f, radius = 40f)
        val distances = fitted.map { (x, y) ->
            hypot((x - 100f).toDouble(), (y - 80f).toDouble())
        }
        assertTrue(distances.all { abs(it - 40.0) < 1e-4 })
    }

    @Test
    fun `sample count is clamped for very simple and very complex curves`() {
        assertEquals(SpirographMath.MIN_SAMPLES, SpirographMath.sampleCount(1))
        assertEquals(SpirographMath.MAX_SAMPLES, SpirographMath.sampleCount(200))
        assertEquals(10 * SpirographMath.SAMPLES_PER_TURN, SpirographMath.sampleCount(10))
    }

    @Test
    fun `rim pen on the outside is an epicycloid`() {
        val details = SpirographMath.details(SpirographMath.Params(50, 20, 20, inside = false))
        assertTrue(details.epicycloid)
        assertFalse(details.hypocycloid)
        assertEquals(10, details.gcd)
        assertEquals(5, details.lobes)
        assertEquals(2, details.periodTurns)
    }
}
