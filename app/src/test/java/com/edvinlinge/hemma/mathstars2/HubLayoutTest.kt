package com.edvinlinge.hemma.mathstars2

import org.junit.Assert.assertEquals
import org.junit.Test

class HubLayoutTest {

    @Test
    fun `scroll bottom padding clears the version label and its margin`() {
        assertEquals(
            72,
            hubScrollBottomPadding(versionHeight = 48, versionBottomMargin = 16, extraGap = 8),
        )
    }

    @Test
    fun `scroll bottom padding ignores zero height until layout completes`() {
        assertEquals(
            24,
            hubScrollBottomPadding(versionHeight = 0, versionBottomMargin = 16, extraGap = 8),
        )
    }
}
