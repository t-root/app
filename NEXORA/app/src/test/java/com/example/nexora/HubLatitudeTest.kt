package com.example.nexora

import com.example.nexora.space.SpaceScene
import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assert.assertTrue
import org.junit.Test

class HubLatitudeTest {

    @Test
    fun groupNames_neverSitOnTheCentresLevel() {
        for (count in 1..16) for (index in 0 until count) for (seed in listOf(0f, 77f, 4096f, 30000f, 65535f)) {
            val home = SpaceScene.hubHome(index, count, seed)
            val radius = sqrt(home.x * home.x + home.y * home.y + home.z * home.z)
            assertTrue(
                "group $index of $count (seed $seed) is on the equator: y=${home.y}, r=$radius",
                abs(home.y) / radius >= 0.25f,
            )
        }
    }
}
