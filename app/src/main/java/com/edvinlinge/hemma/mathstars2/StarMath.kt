package com.edvinlinge.hemma.mathstars2

/**
 * The arithmetic behind the star drawing, free of Android types so it can be covered by fast JVM
 * unit tests. Everything here follows from one fact: stepping `skips` dots at a time around
 * `dots` evenly spaced points revisits the start after `dots / gcd(dots, skips)` steps.
 */
internal object StarMath {

    /** Number of distinct dots reached by repeatedly stepping [skips] dots around [dots]. */
    fun visitedDotCount(dots: Int, skips: Int): Int {
        if (dots <= 0) return 0
        val step = skips.mod(dots)
        if (step == 0) return 1
        return dots / gcd(dots, step)
    }

    /** True when every dot is reached, so the figure closes without lifting the pen. */
    fun isSingleStroke(dots: Int, skips: Int): Boolean =
        dots > 0 && visitedDotCount(dots, skips) == dots

    /**
     * True when the figure has enough distinct points to fill. A digon (for example 6 dots with
     * skip 3) is only a line segment; [android.graphics.Paint.Style.FILL] would make it vanish.
     */
    fun canFill(dots: Int, skips: Int): Boolean = visitedDotCount(dots, skips) >= 3

    /** True when [filled] is on and the figure has enough distinct points to fill safely. */
    fun shouldFill(filled: Boolean, dots: Int, skips: Int): Boolean =
        filled && canFill(dots, skips)

    /** Clamps [skips] to the usable range for [dots], matching the settings slider bounds. */
    fun coercedSkips(dots: Int, skips: Int, minSkips: Int = 2): Int =
        skips.coerceIn(minSkips, maxSkipsFor(dots))

    /**
     * Skip counts that trace a genuine star polygon in one stroke.
     *
     * Starts at two because a skip of one traces the convex polygon rather than a star, and stops
     * at half of [dots] because any larger skip only mirrors a smaller one.
     */
    fun starSkips(dots: Int): List<Int> = (2..dots / 2).filter { gcd(dots, it) == 1 }

    /**
     * Highest usable skip count for [dots]. Kept at two or more so it can never collide with
     * the skips slider's lower bound, which Material rejects with an exception.
     */
    fun maxSkipsFor(dots: Int): Int = (dots / 2).coerceAtLeast(2)

    fun isPrime(number: Int): Boolean {
        if (number < 2) return false
        if (number < 4) return true
        if (number % 2 == 0) return false
        var divisor = 3
        while (divisor * divisor <= number) {
            if (number % divisor == 0) return false
            divisor += 2
        }
        return true
    }

    fun gcd(a: Int, b: Int): Int {
        var first = a
        var second = b
        while (second != 0) {
            val remainder = first % second
            first = second
            second = remainder
        }
        return first
    }

    /**
     * Dot indices visited when tracing the star, starting at zero. Matches [DrawView] path order;
     * the figure closes when the walk returns to a previously visited dot.
     */
    fun starPathVertexIndices(dots: Int, skips: Int): List<Int> {
        if (dots <= 0 || skips <= 0) return emptyList()
        val vertices = mutableListOf(0)
        var next = skips % dots
        val visited = HashSet<Int>(dots)
        while (visited.add(next)) {
            vertices.add(next)
            next = (next + skips) % dots
        }
        return vertices
    }
}
