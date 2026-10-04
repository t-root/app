package com.example.nexora

import com.example.nexora.model.AppGroup
import com.example.nexora.space.SpaceScene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class HubSeedTest {

    private fun group(id: String, name: String) = AppGroup(id, name, 0, emptyList())

    @Test
    fun sameGroupsWithDifferentIds_getTheSameSeeds() {
        val first = listOf(group("a1", "Games"), group("b1", "Work"))
        val second = listOf(group("zz", "Games"), group("yy", "Work")) // imported again: new random ids
        for (i in first.indices) {
            assertEquals(SpaceScene.hubSeed(first, i), SpaceScene.hubSeed(second, i), 0f)
        }
    }

    @Test
    fun differentNames_getDifferentSeeds() {
        val groups = listOf(group("1", "Games"), group("2", "Work"))
        assertNotEquals(SpaceScene.hubSeed(groups, 0), SpaceScene.hubSeed(groups, 1))
    }

    @Test
    fun sameName_isToldApartByOrder() {
        val groups = listOf(group("1", "Misc"), group("2", "Misc"))
        assertNotEquals(SpaceScene.hubSeed(groups, 0), SpaceScene.hubSeed(groups, 1))
    }

    @Test
    fun seedStaysInRangeAndStable() {
        val groups = listOf(group("1", "Games"))
        val seed = SpaceScene.hubSeed(groups, 0)
        assertEquals(seed, SpaceScene.hubSeed(groups, 0), 0f)
        assert(seed in 0f..65535f)
    }
}
