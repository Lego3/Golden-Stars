package com.edvinlinge.hemma.mathstars2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StarMathTest {

    @Test
    fun `gcd of coprime numbers is one`() {
        assertEquals(1, StarMath.gcd(5, 2))
        assertEquals(1, StarMath.gcd(7, 3))
    }

    @Test
    fun `gcd finds the common factor`() {
        assertEquals(2, StarMath.gcd(6, 2))
        assertEquals(3, StarMath.gcd(9, 6))
        assertEquals(6, StarMath.gcd(12, 18))
    }

    @Test
    fun `gcd with zero returns the other operand`() {
        assertEquals(5, StarMath.gcd(5, 0))
        assertEquals(5, StarMath.gcd(0, 5))
    }

    @Test
    fun `every dot is visited when the skip is coprime with the dot count`() {
        assertEquals(5, StarMath.visitedDotCount(dots = 5, skips = 2))
        assertEquals(7, StarMath.visitedDotCount(dots = 7, skips = 3))
        assertEquals(12, StarMath.visitedDotCount(dots = 12, skips = 5))
    }

    @Test
    fun `a shared factor closes the figure early`() {
        assertEquals(3, StarMath.visitedDotCount(dots = 6, skips = 2))
        assertEquals(2, StarMath.visitedDotCount(dots = 6, skips = 3))
        assertEquals(4, StarMath.visitedDotCount(dots = 12, skips = 3))
    }

    @Test
    fun `digons cannot be filled because they have no area`() {
        // Even dot counts with skip = dots/2 only visit two opposite points.
        assertFalse(StarMath.canFill(dots = 6, skips = 3))
        assertFalse(StarMath.canFill(dots = 8, skips = 4))
        assertFalse(StarMath.canFill(dots = 10, skips = 5))
        for (dots in 6..40 step 2) {
            assertFalse(
                "dots=$dots skips=${dots / 2} is a digon and must not fill",
                StarMath.canFill(dots, dots / 2),
            )
        }
    }

    @Test
    fun `figures with three or more points can be filled`() {
        assertTrue(StarMath.canFill(dots = 5, skips = 2))
        assertTrue(StarMath.canFill(dots = 6, skips = 2))
        assertTrue(StarMath.canFill(dots = 7, skips = 3))
        assertTrue(StarMath.canFill(dots = 12, skips = 5))
    }

    @Test
    fun `a skip of one walks the whole polygon`() {
        assertEquals(6, StarMath.visitedDotCount(dots = 6, skips = 1))
        assertTrue(StarMath.isSingleStroke(dots = 6, skips = 1))
        // ...but it is not one of the stars, which is why the two are reported separately.
        assertTrue(StarMath.starSkips(6).isEmpty())
    }

    @Test
    fun `a skip that wraps to a full turn stays on the first dot`() {
        assertEquals(1, StarMath.visitedDotCount(dots = 6, skips = 6))
        assertEquals(1, StarMath.visitedDotCount(dots = 6, skips = 12))
    }

    @Test
    fun `degenerate dot counts do not blow up`() {
        assertEquals(0, StarMath.visitedDotCount(dots = 0, skips = 2))
        assertFalse(StarMath.isSingleStroke(dots = 0, skips = 2))
        assertTrue(StarMath.starSkips(0).isEmpty())
        assertTrue(StarMath.starSkips(2).isEmpty())
    }

    @Test
    fun `star skips exclude the polygon and mirrored duplicates`() {
        assertEquals(listOf(2), StarMath.starSkips(5))
        assertEquals(listOf(2, 3), StarMath.starSkips(7))
        assertEquals(listOf(3), StarMath.starSkips(8))
        assertEquals(listOf(5), StarMath.starSkips(12))
    }

    @Test
    fun `a prime dot count admits every skip up to the halfway point`() {
        val dots = 11
        assertEquals(listOf(2, 3, 4, 5), StarMath.starSkips(dots))
        assertTrue(StarMath.starSkips(dots).all { StarMath.isSingleStroke(dots, it) })
    }

    @Test
    fun `no star exists when every skip shares a factor with the dot count`() {
        assertTrue(StarMath.starSkips(6).isEmpty())
        assertTrue(StarMath.starSkips(4).isEmpty())
    }

    @Test
    fun `star skips always close in one stroke`() {
        for (dots in 3..60) {
            for (skips in StarMath.starSkips(dots)) {
                assertTrue(
                    "dots=$dots skips=$skips should be drawable in one stroke",
                    StarMath.isSingleStroke(dots, skips),
                )
            }
        }
    }

    @Test
    fun `primality`() {
        assertFalse(StarMath.isPrime(0))
        assertFalse(StarMath.isPrime(1))
        assertTrue(StarMath.isPrime(2))
        assertTrue(StarMath.isPrime(3))
        assertFalse(StarMath.isPrime(4))
        assertTrue(StarMath.isPrime(5))
        assertFalse(StarMath.isPrime(9))
        assertFalse(StarMath.isPrime(25))
        assertTrue(StarMath.isPrime(29))
        assertFalse(StarMath.isPrime(-7))
    }

    @Test
    fun `max skips never drops below the slider minimum`() {
        assertEquals(2, StarMath.maxSkipsFor(dots = 3))
        assertEquals(2, StarMath.maxSkipsFor(dots = 4))
        assertEquals(2, StarMath.maxSkipsFor(dots = 5))
    }

    @Test
    fun `max skips tracks half the dot count for larger figures`() {
        assertEquals(5, StarMath.maxSkipsFor(dots = 10))
        assertEquals(15, StarMath.maxSkipsFor(dots = 30))
        assertEquals(30, StarMath.maxSkipsFor(dots = 60))
    }

    @Test
    fun `max skips stays at least two for degenerate dot counts`() {
        assertEquals(2, StarMath.maxSkipsFor(dots = 0))
        assertEquals(2, StarMath.maxSkipsFor(dots = 1))
        assertEquals(2, StarMath.maxSkipsFor(dots = 2))
    }

    @Test
    fun `classic five pointed star visits every dot once before closing`() {
        assertEquals(listOf(0, 2, 4, 1, 3, 0), StarMath.starPathVertexIndices(dots = 5, skips = 2))
    }

    @Test
    fun `digons only visit two opposite dots`() {
        assertEquals(listOf(0, 3, 0), StarMath.starPathVertexIndices(dots = 6, skips = 3))
        assertEquals(listOf(0, 4, 0), StarMath.starPathVertexIndices(dots = 8, skips = 4))
    }

    @Test
    fun `partial figures stop when a dot would be revisited`() {
        assertEquals(listOf(0, 2, 4, 0), StarMath.starPathVertexIndices(dots = 6, skips = 2))
        assertEquals(listOf(0, 3, 6, 9, 0), StarMath.starPathVertexIndices(dots = 12, skips = 3))
    }

    @Test
    fun `path indices are empty for invalid geometry`() {
        assertTrue(StarMath.starPathVertexIndices(dots = 0, skips = 2).isEmpty())
        assertTrue(StarMath.starPathVertexIndices(dots = 5, skips = 0).isEmpty())
        assertTrue(StarMath.starPathVertexIndices(dots = -1, skips = 2).isEmpty())
    }

    @Test
    fun `should fill respects the filled toggle and digon geometry`() {
        assertTrue(StarMath.shouldFill(filled = true, dots = 5, skips = 2))
        assertFalse(StarMath.shouldFill(filled = false, dots = 5, skips = 2))
        assertFalse(StarMath.shouldFill(filled = true, dots = 6, skips = 3))
        assertFalse(StarMath.shouldFill(filled = true, dots = 8, skips = 4))
    }

    @Test
    fun `coerced skips stay within slider bounds when dot count shrinks`() {
        assertEquals(2, StarMath.coercedSkips(dots = 5, skips = 2))
        assertEquals(3, StarMath.coercedSkips(dots = 7, skips = 5))
        assertEquals(2, StarMath.coercedSkips(dots = 4, skips = 3))
        assertEquals(2, StarMath.coercedSkips(dots = 3, skips = 10))
    }

    @Test
    fun `coerced skips never drop below the slider minimum`() {
        assertEquals(2, StarMath.coercedSkips(dots = 60, skips = 1))
        assertEquals(2, StarMath.coercedSkips(dots = 0, skips = 5))
    }

    @Test
    fun `path length matches visited dot count plus the starting dot`() {
        for (dots in 3..20) {
            for (skips in 1..dots / 2) {
                val path = StarMath.starPathVertexIndices(dots, skips)
                val visitedDots = StarMath.visitedDotCount(dots, skips)
                assertEquals(
                    "dots=$dots skips=$skips",
                    visitedDots + 1,
                    path.size,
                )
            }
        }
    }
}
