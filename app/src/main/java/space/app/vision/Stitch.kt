package space.app.vision

/**
 * Long screenshots: joins screens taken while scrolling down. Rows that stay put on every screen
 * (status bar, toolbars, nav bar) are kept once; the scrolling part is joined at its overlap.
 */
fun stitch(screens: List<Rgb>): Rgb {
    require(screens.isNotEmpty() && screens.all { it.width == screens[0].width && it.height == screens[0].height })
    val width = screens[0].width
    val height = screens[0].height
    val hashes = screens.map { s -> IntArray(height) { y -> s.pixels.copyOfRange(y * width, (y + 1) * width).contentHashCode() } }

    // Fixed rows: the same on the first two screens, at the top and at the bottom.
    var top = 0
    var bottom = 0
    if (screens.size > 1) {
        while (top < height && hashes[0][top] == hashes[1][top]) top++
        if (top == height) return screens[0] // didn't scroll at all
        while (bottom < height - top && hashes[0][height - 1 - bottom] == hashes[1][height - 1 - bottom]) bottom++
    }
    val content = height - top - bottom

    val out = ArrayList<Pair<Int, Int>>() // which screen and row each output row comes from
    for (y in 0 until height - bottom) out += 0 to y
    for (i in 1 until screens.size) {
        val previous = hashes[i - 1]
        val next = hashes[i]
        // The smallest scroll d where the new screen's content starts d rows into the previous one's.
        val d = (1 until content).firstOrNull { d ->
            val overlap = content - d
            var same = 0
            for (k in 0 until overlap) if (next[top + k] == previous[top + d + k]) same++
            same >= overlap * 0.95
        } ?: content
        if (d == content && next.contentEquals(previous)) break // reached the end of the page
        for (y in top + content - d until top + content) out += i to y
    }
    for (y in height - bottom until height) out += screens.lastIndex to y

    val pixels = IntArray(width * out.size)
    out.forEachIndexed { row, (screen, y) -> System.arraycopy(screens[screen].pixels, y * width, pixels, row * width, width) }
    return Rgb(width, out.size, pixels)
}
