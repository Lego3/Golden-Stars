package com.edvinlinge.hemma.mathstars2

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenInsetsTest {

    @Test
    fun `ltr layout maps physical left and right to start and end`() {
        val insets = screenInsetsFromSystemBars(
            left = 10,
            top = 20,
            right = 30,
            bottom = 40,
            edgeMargin = 8,
            layoutDirectionRtl = false,
        )

        assertEquals(10, insets.start)
        assertEquals(20, insets.top)
        assertEquals(30, insets.end)
        assertEquals(40, insets.bottom)
        assertEquals(8, insets.edgeMargin)
    }

    @Test
    fun `rtl layout swaps start and end for overlay margins`() {
        val insets = screenInsetsFromSystemBars(
            left = 10,
            top = 20,
            right = 30,
            bottom = 40,
            edgeMargin = 8,
            layoutDirectionRtl = true,
        )

        assertEquals(30, insets.start)
        assertEquals(20, insets.top)
        assertEquals(10, insets.end)
        assertEquals(40, insets.bottom)
        assertEquals(8, insets.edgeMargin)
    }
}
