package com.edvinlinge.hemma.mathstars2

import android.app.Activity
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * System bar and cutout insets, resolved to start/end rather than left/right so callers can
 * assign them to layout-direction aware margins. [edgeMargin] is the design gap between a
 * floating overlay and the screen edge, already converted from dp to pixels.
 */
internal class ScreenInsets(
    val start: Int,
    val top: Int,
    val end: Int,
    val bottom: Int,
    val edgeMargin: Int,
)

/**
 * Invokes [onInsets] whenever the window insets change, for screens that draw content edge to
 * edge behind the system bars and position floating overlays on top of it.
 */
internal fun Activity.doOnScreenInsets(onInsets: (ScreenInsets) -> Unit) {
    val edgeMargin = resources.getDimensionPixelSize(R.dimen.overlay_edge_margin)
    ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        val rtl = view.layoutDirection == View.LAYOUT_DIRECTION_RTL
        onInsets(
            ScreenInsets(
                start = if (rtl) bars.right else bars.left,
                top = bars.top,
                end = if (rtl) bars.left else bars.right,
                bottom = bars.bottom,
                edgeMargin = edgeMargin,
            )
        )
        insets
    }
}
