package com.example.nexora.space

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/**
 * A PCB-style trace routed on the screen itself: its legs run only straight
 * across or straight up and down, and the only slanted strokes are the 45°
 * cuts at its corners. (Routed in space and then projected, a leg along the
 * depth axis comes out at any angle.)
 */
object ScreenTrace {
    /** Start, two corners cut in two, end. */
    const val MAX_POINTS = 6

    /**
     * Writes the route from ([ax], [ay]) to ([bx], [by]) into [out] as x, y
     * pairs and returns the number of points. [h] (0..1) picks across-first or
     * down-first and where the jog lies, so wires differ; [chamfer] is how far
     * each corner is cut (less where the legs are short).
     */
    fun route(out: FloatArray, ax: Float, ay: Float, bx: Float, by: Float, h: Float, chamfer: Float): Int {
        val dx = bx - ax
        val dy = by - ay
        if (dx == 0f || dy == 0f) return put(out, 0, ax, ay, bx, by)
        // Nearly in line: still no slant, just a jog too small to see.
        if (abs(dy) < EPS) return putPoint(out, put(out, 0, ax, ay, bx, ay), bx, by)
        if (abs(dx) < EPS) return putPoint(out, put(out, 0, ax, ay, ax, by), bx, by)
        val lane = 0.25f + ((h * 7.13f) % 1f) * 0.5f
        // Corner points of the Z: across, jog, across — or down, jog, down.
        val c1x: Float
        val c1y: Float
        val c2x: Float
        val c2y: Float
        if (h < 0.5f) {
            c1x = ax + dx * lane; c1y = ay
            c2x = c1x; c2y = by
        } else {
            c1x = ax; c1y = ay + dy * lane
            c2x = bx; c2y = c1y
        }
        var n = 0
        n = putPoint(out, n, ax, ay)
        n = cutCorner(out, n, ax, ay, c1x, c1y, c2x, c2y, chamfer)
        n = cutCorner(out, n, c1x, c1y, c2x, c2y, bx, by, chamfer)
        return putPoint(out, n, bx, by)
    }

    fun length(route: FloatArray, n: Int): Float {
        var total = 0f
        for (j in 0 until n - 1) {
            total += hypot(route[2 * j + 2] - route[2 * j], route[2 * j + 3] - route[2 * j + 1])
        }
        return total
    }

    /** Writes the point [distance] along the route into [out] (x, y). */
    fun pointAlong(out: FloatArray, route: FloatArray, n: Int, distance: Float) {
        var left = distance
        for (j in 0 until n - 1) {
            val x0 = route[2 * j]
            val y0 = route[2 * j + 1]
            val x1 = route[2 * j + 2]
            val y1 = route[2 * j + 3]
            val len = hypot(x1 - x0, y1 - y0)
            if (left <= len) {
                val t = if (len > 0f) left / len else 0f
                out[0] = x0 + (x1 - x0) * t
                out[1] = y0 + (y1 - y0) * t
                return
            }
            left -= len
        }
        out[0] = route[2 * n - 2]
        out[1] = route[2 * n - 1]
    }

    /** The corner at (bx, by), between the legs from a and to c, cut at 45°. */
    private fun cutCorner(
        out: FloatArray,
        n: Int,
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float,
        cx: Float,
        cy: Float,
        chamfer: Float,
    ): Int {
        val l0 = hypot(bx - ax, by - ay)
        val l1 = hypot(cx - bx, cy - by)
        val r = min(chamfer, min(l0, l1) * 0.45f)
        if (r < EPS) return putPoint(out, n, bx, by)
        var m = putPoint(out, n, bx - (bx - ax) / l0 * r, by - (by - ay) / l0 * r)
        m = putPoint(out, m, bx + (cx - bx) / l1 * r, by + (cy - by) / l1 * r)
        return m
    }

    private fun put(out: FloatArray, n: Int, ax: Float, ay: Float, bx: Float, by: Float): Int =
        putPoint(out, putPoint(out, n, ax, ay), bx, by)

    private fun putPoint(out: FloatArray, n: Int, x: Float, y: Float): Int {
        out[2 * n] = x
        out[2 * n + 1] = y
        return n + 1
    }

    private const val EPS = 0.5f
}
