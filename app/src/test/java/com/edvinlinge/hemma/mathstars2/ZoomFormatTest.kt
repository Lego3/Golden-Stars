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

    @Test
    fun `suffix thresholds pick the next unit at exact powers of ten`() {
        assertEquals("999.9x", formatZoom(999.9))
        assertEquals("1.0k x", formatZoom(1_000.0))
        assertEquals("999.9k x", formatZoom(999_900.0))
        assertEquals("1.0M x", formatZoom(1_000_000.0))
        assertEquals("999.9M x", formatZoom(999_900_000.0))
        assertEquals("1.0G x", formatZoom(1_000_000_000.0))
        assertEquals("999.9G x", formatZoom(999_900_000_000.0))
        assertEquals("1.0T x", formatZoom(1_000_000_000_000.0))
    }

    @Test
    fun `minimum zoom label keeps one decimal place`() {
        assertEquals("0.5x", formatZoom(0.5))
        assertEquals("0.1x", formatZoom(0.1))
    }
}
