package com.edvinlinge.hemma.mathstars2

/**
 * Structured spirograph-details content for the info sheet, free of Android types so it can be
 * covered by fast JVM unit tests. [SpirographView.getDetailsHtml] maps these values to localized
 * strings.
 */
internal sealed class SpirographCurveKind {
    data object Circle : SpirographCurveKind()
    data object Hypocycloid : SpirographCurveKind()
    data object Epicycloid : SpirographCurveKind()
    data object General : SpirographCurveKind()
}

internal data class SpirographDetailsParagraphs(
    val inside: Boolean,
    val fixedRadius: Int,
    val rollingRadius: Int,
    val penOffset: Int,
    val curveKind: SpirographCurveKind,
    val periodTurns: Int,
    val gcd: Int,
    val lobes: Int,
)

internal fun spirographDetailsParagraphs(params: SpirographMath.Params): SpirographDetailsParagraphs {
    val details = SpirographMath.details(params)
    val curveKind = when {
        details.circle -> SpirographCurveKind.Circle
        details.hypocycloid -> SpirographCurveKind.Hypocycloid
        details.epicycloid -> SpirographCurveKind.Epicycloid
        else -> SpirographCurveKind.General
    }
    return SpirographDetailsParagraphs(
        inside = details.params.inside,
        fixedRadius = details.params.fixedRadius,
        rollingRadius = details.params.rollingRadius,
        penOffset = details.params.penOffset,
        curveKind = curveKind,
        periodTurns = details.periodTurns,
        gcd = details.gcd,
        lobes = details.lobes,
    )
}
