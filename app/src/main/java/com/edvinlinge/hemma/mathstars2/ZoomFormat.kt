package com.edvinlinge.hemma.mathstars2

import java.util.Locale

/** Formats a zoom factor compactly, for example `2.5x`, `1.4k x` or `3.0M x`. */
internal fun formatZoom(zoom: Double): String = when {
    zoom >= 1e12 -> String.format(Locale.US, "%.1fT x", zoom / 1e12)
    zoom >= 1e9 -> String.format(Locale.US, "%.1fG x", zoom / 1e9)
    zoom >= 1e6 -> String.format(Locale.US, "%.1fM x", zoom / 1e6)
    zoom >= 1e3 -> String.format(Locale.US, "%.1fk x", zoom / 1e3)
    else -> String.format(Locale.US, "%.1fx", zoom)
}
