package com.edvinlinge.hemma.mathstars2

import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomFormatTest {

    @Test
    fun `small factors keep one decimal`() {
        assertEquals("1.0x", formatZoom(1.0))
        assertEquals("2.5x", formatZoom(2.5))
        assertEquals("999.9x", formatZoom(999.94))
    }

    @Test
    fun `large factors switch to a suffix`() {
        assertEquals("1.0k x", formatZoom(1_000.0))
        assertEquals("1.5M x", formatZoom(1_500_000.0))
        assertEquals("2.0G x", formatZoom(2e9))
        assertEquals("3.0T x", formatZoom(3e12))
    }

    @Test
    fun `formatting is locale independent`() {
        // Locale.US keeps the decimal point, so the label cannot come out as "1,5M x" on a device
        // configured for a comma-decimal locale.
        val original = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            assertEquals("1.5M x", formatZoom(1_500_000.0))
        } finally {
            java.util.Locale.setDefault(original)
        }
    }
}
