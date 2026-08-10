package com.edvinlinge.hemma.mathstars2

/**
 * Chooses how an info-sheet message is rendered. Plain help text keeps its newline paragraph
 * breaks; HTML messages (for example star details from [DrawView.getDetailsHtml]) use tags.
 */
internal fun shouldParseInfoMessageAsHtml(message: String): Boolean = message.contains('<')
