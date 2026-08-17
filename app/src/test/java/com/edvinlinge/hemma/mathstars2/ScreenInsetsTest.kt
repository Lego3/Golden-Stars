package com.edvinlinge.hemma.mathstars2

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenInsetsTest {

    @Test
    fun `ltr maps left and right to start and end`() {
        val insets = screenInsetsFromSystemBars(
            left = 12,
            top = 24,
            right = 16,
            bottom = 48,
            rtl = false,
            edgeMargin = 8,
        )
        assertEquals(12, insets.start)
        assertEquals(24, insets.top)
        assertEquals(16, insets.end)
        assertEquals(48, insets.bottom)
        assertEquals(8, insets.edgeMargin)
    }

    @Test
    fun `rtl swaps left and right for start and end`() {
        val insets = screenInsetsFromSystemBars(
            left = 12,
            top = 24,
            right = 16,
            bottom = 48,
            rtl = true,
            edgeMargin = 8,
        )
        assertEquals(16, insets.start)
        assertEquals(12, insets.end)
        assertEquals(24, insets.top)
        assertEquals(48, insets.bottom)
    }
}
