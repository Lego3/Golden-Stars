package com.edvinlinge.hemma.mathstars2

/**
 * Structured star-details content for the info sheet, free of Android types so it can be covered
 * by fast JVM unit tests. [DrawView.getDetailsHtml] maps these values to localized strings.
 */
internal data class StarDetailsParagraphs(
    /** True when no single-stroke star exists for this dot count. */
    val impossible: Boolean,
    val primary: PrimaryDetail,
    /** Distinct star skip counts for [dots]; empty when [impossible]. */
    val possibleSkips: List<Int>,
)

internal sealed class PrimaryDetail {
    data class Polygon(val dots: Int) : PrimaryDetail()
    data class Success(val dots: Int, val notePrime: Boolean) : PrimaryDetail()
    data class Fair(val dots: Int, val skips: Int, val visited: Int) : PrimaryDetail()
}

internal fun starDetailsParagraphs(dots: Int, skips: Int): StarDetailsParagraphs {
    val possibleSkips = StarMath.starSkips(dots)
    val visited = StarMath.visitedDotCount(dots, skips)
    val primary = when {
        skips <= 1 -> PrimaryDetail.Polygon(dots)
        visited == dots -> PrimaryDetail.Success(dots, StarMath.isPrime(dots))
        else -> PrimaryDetail.Fair(dots, skips, visited)
    }
    return StarDetailsParagraphs(
        impossible = possibleSkips.isEmpty(),
        primary = primary,
        possibleSkips = possibleSkips,
    )
}
