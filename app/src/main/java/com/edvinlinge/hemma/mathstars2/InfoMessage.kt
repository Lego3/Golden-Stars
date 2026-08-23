package com.edvinlinge.hemma.mathstars2

/**
 * Chooses how an info-sheet message is rendered. Plain help text keeps its newline paragraph
 * breaks; HTML messages (for example star or Spirograph details) use tags.
 */
internal fun shouldParseInfoMessageAsHtml(message: String): Boolean = message.contains('<')

/** Falls back to [defaultTitle] when the host did not supply a sheet title. */
internal fun infoSheetTitle(titleArg: String?, defaultTitle: String): String =
    titleArg ?: defaultTitle
