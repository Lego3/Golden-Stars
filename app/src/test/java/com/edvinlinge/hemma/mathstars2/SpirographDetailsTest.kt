package com.edvinlinge.hemma.mathstars2

import org.junit.Assert.assertEquals
import org.junit.Test

class SpirographDetailsTest {

    @Test
    fun `classic defaults describe a three lobe hypotrochoid`() {
        val details = spirographDetailsParagraphs(
            SpirographMath.Params(
                fixedRadius = SpirographMath.DEFAULT_FIXED,
                rollingRadius = SpirographMath.DEFAULT_ROLLING,
                penOffset = SpirographMath.DEFAULT_PEN,
                inside = SpirographMath.DEFAULT_INSIDE,
            ),
        )

        assertEquals(true, details.inside)
        assertEquals(96, details.fixedRadius)
        assertEquals(32, details.rollingRadius)
        assertEquals(32, details.penOffset)
        assertEquals(SpirographCurveKind.Hypocycloid, details.curveKind)
        assertEquals(1, details.periodTurns)
        assertEquals(32, details.gcd)
        assertEquals(3, details.lobes)
    }

    @Test
    fun `pen at the wheel centre is reported as a circle`() {
        val details = spirographDetailsParagraphs(
            SpirographMath.Params(fixedRadius = 90, rollingRadius = 30, penOffset = 0, inside = true),
        )

        assertEquals(SpirographCurveKind.Circle, details.curveKind)
        assertEquals(0, details.penOffset)
    }

    @Test
    fun `rim pen outside the ring is an epicycloid`() {
        val details = spirographDetailsParagraphs(
            SpirographMath.Params(fixedRadius = 50, rollingRadius = 20, penOffset = 20, inside = false),
        )

        assertEquals(false, details.inside)
        assertEquals(SpirographCurveKind.Epicycloid, details.curveKind)
        assertEquals(10, details.gcd)
        assertEquals(5, details.lobes)
        assertEquals(2, details.periodTurns)
    }

    @Test
    fun `general curves omit a special case label`() {
        val details = spirographDetailsParagraphs(
            SpirographMath.Params(fixedRadius = 80, rollingRadius = 30, penOffset = 20, inside = false),
        )

        assertEquals(SpirographCurveKind.General, details.curveKind)
        assertEquals(10, details.gcd)
    }

    @Test
    fun `deltoid parameters close after one turn with three lobes`() {
        val details = spirographDetailsParagraphs(
            SpirographMath.Params(fixedRadius = 3, rollingRadius = 1, penOffset = 1, inside = true),
        )

        assertEquals(SpirographCurveKind.Hypocycloid, details.curveKind)
        assertEquals(1, details.periodTurns)
        assertEquals(3, details.lobes)
    }
}
