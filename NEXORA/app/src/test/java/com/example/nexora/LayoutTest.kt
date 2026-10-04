package com.example.nexora

import com.example.nexora.space.Circuit
import com.example.nexora.space.SpaceScene
import com.example.nexora.space.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutTest {

    @Test
    fun hubHome_isTheSameEveryTime() {
        val a = SpaceScene.hubHome(2, 5, 1234f)
        val b = SpaceScene.hubHome(2, 5, 1234f)
        assertEquals(a.x, b.x, 0f)
        assertEquals(a.y, b.y, 0f)
        assertEquals(a.z, b.z, 0f)
    }

    @Test
    fun hubHome_differsWithTheSeed() {
        val a = SpaceScene.hubHome(1, 4, 100f)
        val b = SpaceScene.hubHome(1, 4, 5000f)
        assertTrue(a.distanceTo(b) > 0.1f)
    }

    @Test
    fun hubHomes_keepApart() {
        val homes = List(8) { SpaceScene.hubHome(it, 8, (it * 8191f) % 65535f) }
        for (i in homes.indices) for (j in i + 1 until homes.size) {
            assertTrue("groups $i and $j too close", homes[i].distanceTo(homes[j]) > 1.2f)
        }
    }

    @Test
    fun route_variesAndAlwaysReachesTheEnd() {
        val from = Vec3(0f, 0f, 0f)
        val to = Vec3(3f, -2f, 4f)
        val shapes = HashSet<String>()
        for (k in 0 until 40) {
            val pts = Array(Circuit.MAX_POINTS) { Vec3() }
            val n = Circuit.route(pts, from, to, Circuit.hash01(k * 31f, 0.5f))
            assertTrue(n in 2..Circuit.MAX_POINTS)
            assertEquals(to.x, pts[n - 1].x, 1e-4f)
            assertEquals(to.y, pts[n - 1].y, 1e-4f)
            assertEquals(to.z, pts[n - 1].z, 1e-4f)
            shapes += (0 until n).joinToString { "%.2f,%.2f,%.2f".format(pts[it].x, pts[it].y, pts[it].z) }
        }
        assertTrue("routes look alike: ${shapes.size} shapes", shapes.size >= 6)
    }
}

class SatelliteLayoutTest {

    private fun ring(count: Int, seed: Float) = List(count) { i ->
        Vec3().also { SpaceScene.satellitePosition(it, i, count, 0f, Vec3(), 1f, 1f, 1f, seed) }
    }

    @Test
    fun fourApps_areNotAPlusSign() {
        for (seed in listOf(10f, 777f, 4242f, 60000f)) {
            val angles = ring(4, seed).map { Math.atan2(it.z.toDouble(), it.x.toDouble()) }.sorted()
            val gaps = angles.zipWithNext { a, b -> b - a } + (angles.first() + 2 * Math.PI - angles.last())
            assertTrue("seed $seed still evenly spaced: $gaps", gaps.max() - gaps.min() > 0.15)
        }
    }

    @Test
    fun apps_stayFixedAndApart() {
        for (count in listOf(1, 2, 3, 4, 6, 9, 14, 30)) {
            val a = ring(count, 321f)
            val b = ring(count, 321f)
            for (i in a.indices) assertEquals(0f, a[i].distanceTo(b[i]), 0f)
            for (i in a.indices) for (j in i + 1 until a.size) {
                assertTrue("count $count: $i and $j overlap", a[i].distanceTo(a[j]) > 0.6f)
            }
        }
    }
}
