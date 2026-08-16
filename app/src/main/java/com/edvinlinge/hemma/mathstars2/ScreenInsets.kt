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

/** Maps physical bar insets to layout-direction aware start/end values. */
internal fun screenInsetsFromSystemBars(
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
    edgeMargin: Int,
    layoutDirectionRtl: Boolean,
): ScreenInsets = ScreenInsets(
    start = if (layoutDirectionRtl) right else left,
    top = top,
    end = if (layoutDirectionRtl) left else right,
    bottom = bottom,
    edgeMargin = edgeMargin,
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
        onInsets(
            screenInsetsFromSystemBars(
                left = bars.left,
                top = bars.top,
                right = bars.right,
                bottom = bars.bottom,
                edgeMargin = edgeMargin,
                layoutDirectionRtl = view.layoutDirection == View.LAYOUT_DIRECTION_RTL,
            )
        )
        insets
    }
}
