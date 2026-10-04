package com.example.nexora.service

/** A rectangle in screen pixels; a plain class so the maths below runs in local unit tests. */
data class PxRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
    val isEmpty get() = right <= left || bottom <= top

    fun intersects(other: PxRect) =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom
}

/**
 * [base] with every one of [holes] cut out, as the fewest-looking set of
 * rectangles that don't overlap: each hole splits a piece into at most four
 * (above, below, left and right of the hole).
 */
fun subtractRects(base: PxRect, holes: List<PxRect>): List<PxRect> {
    var pieces = if (base.isEmpty) emptyList() else listOf(base)
    for (hole in holes) {
        if (hole.isEmpty) continue
        pieces = pieces.flatMap { cut(it, hole) }
    }
    return pieces
}

private fun cut(piece: PxRect, hole: PxRect): List<PxRect> {
    if (!piece.intersects(hole)) return listOf(piece)
    val top = maxOf(piece.top, hole.top)
    val bottom = minOf(piece.bottom, hole.bottom)
    return listOf(
        PxRect(piece.left, piece.top, piece.right, top), // above
        PxRect(piece.left, bottom, piece.right, piece.bottom), // below
        PxRect(piece.left, top, maxOf(piece.left, hole.left), bottom), // left
        PxRect(minOf(piece.right, hole.right), top, piece.right, bottom), // right
    ).filterNot { it.isEmpty }
}
