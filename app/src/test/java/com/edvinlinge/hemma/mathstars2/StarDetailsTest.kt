package com.edvinlinge.hemma.mathstars2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StarDetailsTest {

    @Test
    fun `classic five pointed star is a success with one possible skip`() {
        val details = starDetailsParagraphs(dots = 5, skips = 2)

        assertFalse(details.impossible)
        assertEquals(PrimaryDetail.Success(dots = 5, notePrime = true), details.primary)
        assertEquals(listOf(2), details.possibleSkips)
    }

    @Test
    fun `skip of one is reported as a polygon not a star`() {
        val details = starDetailsParagraphs(dots = 7, skips = 1)

        assertFalse(details.impossible)
        assertEquals(PrimaryDetail.Polygon(dots = 7), details.primary)
        assertEquals(listOf(2, 3), details.possibleSkips)
    }

    @Test
    fun `composite dot counts with no star skips are impossible`() {
        val details = starDetailsParagraphs(dots = 6, skips = 2)

        assertTrue(details.impossible)
        assertEquals(PrimaryDetail.Fair(dots = 6, skips = 2, visited = 3), details.primary)
        assertTrue(details.possibleSkips.isEmpty())
    }

    @Test
    fun `partial figures report visited dot count`() {
        val details = starDetailsParagraphs(dots = 12, skips = 3)

        assertFalse(details.impossible)
        assertEquals(PrimaryDetail.Fair(dots = 12, skips = 3, visited = 4), details.primary)
        assertEquals(listOf(5), details.possibleSkips)
    }

    @Test
    fun `prime success omits the prime note for composite dot counts`() {
        val details = starDetailsParagraphs(dots = 12, skips = 5)

        assertEquals(PrimaryDetail.Success(dots = 12, notePrime = false), details.primary)
    }

    @Test
    fun `prime success includes the prime note`() {
        val details = starDetailsParagraphs(dots = 11, skips = 4)

        assertEquals(PrimaryDetail.Success(dots = 11, notePrime = true), details.primary)
        assertEquals(listOf(2, 3, 4, 5), details.possibleSkips)
    }
}
