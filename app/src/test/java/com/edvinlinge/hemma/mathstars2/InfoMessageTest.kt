package com.edvinlinge.hemma.mathstars2

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InfoMessageTest {

    @Test
    fun `plain help text is not parsed as html`() {
        val help = "First paragraph.\n\nSecond paragraph."
        assertFalse(shouldParseInfoMessageAsHtml(help))
    }

    @Test
    fun `star details html is parsed as html`() {
        val details = "Congratulations!<br><br>It is possible to draw 1 different 5-pointed stars"
        assertTrue(shouldParseInfoMessageAsHtml(details))
    }

    @Test
    fun `empty message is shown as plain text`() {
        assertFalse(shouldParseInfoMessageAsHtml(""))
    }

    @Test
    fun `single line plain text without tags is not parsed as html`() {
        assertFalse(shouldParseInfoMessageAsHtml("Move 2 dots while drawing."))
    }
}
