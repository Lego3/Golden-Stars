package com.edvinlinge.hemma.mathstars2

/**
 * Layout math for the hub screen, kept free of Android view types so it can be covered by fast
 * JVM unit tests.
 */
internal fun hubScrollBottomPadding(
    versionHeight: Int,
    versionBottomMargin: Int,
    extraGap: Int,
): Int = versionHeight + versionBottomMargin + extraGap
