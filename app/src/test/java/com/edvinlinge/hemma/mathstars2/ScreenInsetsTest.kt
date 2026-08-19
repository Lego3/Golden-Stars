package com.edvinlinge.hemma.mathstars2

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenInsetsTest {

    @Test
    fun `ltr layout maps left and right to start and end`() {
        val insets = screenInsetsFromSystemBars(
            left = 10,
            top = 20,
            right = 30,
            bottom = 40,
            layoutDirectionRtl = false,
            edgeMargin = 8,
        )
        assertEquals(10, insets.start)
        assertEquals(20, insets.top)
        assertEquals(30, insets.end)
        assertEquals(40, insets.bottom)
        assertEquals(8, insets.edgeMargin)
    }

    @Test
    fun `rtl layout swaps left and right for start and end`() {
        val insets = screenInsetsFromSystemBars(
            left = 10,
            top = 20,
            right = 30,
            bottom = 40,
            layoutDirectionRtl = true,
            edgeMargin = 8,
        )
        assertEquals(30, insets.start)
        assertEquals(20, insets.top)
        assertEquals(10, insets.end)
        assertEquals(40, insets.bottom)
    }
}
