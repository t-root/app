package com.example.nexora

import com.example.nexora.space.ScreenTrace
import java.util.Random
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenTraceTest {

    private val out = FloatArray(ScreenTrace.MAX_POINTS * 2)

    /** Every stroke of the route is across, up and down, or exactly 45°. */
    private fun assertOnlyStraightAnd45(n: Int, what: String) {
        for (j in 0 until n - 1) {
            val dx = abs(out[2 * j + 2] - out[2 * j])
            val dy = abs(out[2 * j + 3] - out[2 * j + 1])
            val ok = dx < 1e-3f || dy < 1e-3f || abs(dx - dy) < 1e-2f * maxOf(dx, dy, 1f)
            assertTrue("$what: stroke $j is slanted ($dx × $dy)", ok)
        }
    }

    @Test
    fun routes_useOnlyStraightLegsAnd45DegreeCorners() {
        val random = Random(7)
        repeat(2000) {
            val ax = random.nextFloat() * 1200f
            val ay = random.nextFloat() * 2600f
            val bx = random.nextFloat() * 1200f
            val by = random.nextFloat() * 2600f
            val h = random.nextFloat()
            val chamfer = 10f + random.nextFloat() * 80f
            val n = ScreenTrace.route(out, ax, ay, bx, by, h, chamfer)
            assertTrue(n in 2..ScreenTrace.MAX_POINTS)
            assertEquals(ax, out[0], 1e-3f)
            assertEquals(ay, out[1], 1e-3f)
            assertEquals(bx, out[2 * n - 2], 1e-3f)
            assertEquals(by, out[2 * n - 1], 1e-3f)
            assertOnlyStraightAnd45(n, "a=($ax,$ay) b=($bx,$by) h=$h")
        }
    }

    @Test
    fun alignedEnds_runStraight() {
        assertEquals(2, ScreenTrace.route(out, 100f, 100f, 100f, 900f, 0.3f, 40f))
        assertEquals(2, ScreenTrace.route(out, 100f, 100f, 800f, 100f, 0.8f, 40f))
    }

    @Test
    fun hVariesTheShape() {
        val shapes = HashSet<String>()
        for (k in 0 until 40) {
            val n = ScreenTrace.route(out, 100f, 200f, 700f, 1500f, k / 40f, 40f)
            shapes += (0 until 2 * n).joinToString { "%.1f".format(out[it]) }
        }
        assertTrue("routes look alike: ${shapes.size}", shapes.size >= 8)
    }

    @Test
    fun pointAlong_followsTheCorners() {
        val n = ScreenTrace.route(out, 0f, 0f, 400f, 400f, 0.1f, 50f)
        val length = ScreenTrace.length(out, n)
        val at = FloatArray(2)
        ScreenTrace.pointAlong(at, out, n, 0f)
        assertEquals(0f, at[0], 1e-3f)
        ScreenTrace.pointAlong(at, out, n, length)
        assertEquals(400f, at[0], 1e-3f)
        assertEquals(400f, at[1], 1e-3f)
        // Corners are cut, so the way is shorter than across + down (800) yet longer than the straight line.
        assertTrue(length < 800f && length > 400f * 1.414f)
    }
}
