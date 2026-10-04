package com.example.nexora

import com.example.nexora.model.AppGroup
import com.example.nexora.space.SpaceScene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HubSlotTest {

    private fun groups(vararg names: String) = names.mapIndexed { i, n -> AppGroup("id$i", n, 0, emptyList()) }

    private fun slots(groups: List<AppGroup>) =
        SpaceScene.hubSlots(groups.indices.map { SpaceScene.hubSeed(groups, it) })

    @Test
    fun slots_areAPermutationAndStable() {
        val g = groups("Games", "Work", "Music", "Bank", "AI", "Misc", "Tools")
        val a = slots(g)
        assertEquals((0 until g.size).toList(), a.sorted())
        assertEquals(a, slots(g))
    }

    @Test
    fun slots_followTheNamesNotTheirOrder() {
        // The same names listed the other way round keep their spots.
        val g = groups("Games", "Work", "Music", "Bank", "AI")
        val reversed = g.reversed()
        val a = slots(g)
        val b = slots(reversed)
        for (name in g.map { it.name }) {
            assertEquals(a[g.indexOfFirst { it.name == name }], b[reversed.indexOfFirst { it.name == name }])
        }
    }

    @Test
    fun highestGroup_isNotAlwaysTheFirstInTheList() {
        val topIndexes = HashSet<Int>()
        for (k in 0 until 40) {
            val g = groups("A$k", "B$k", "C$k", "D$k", "E$k", "F$k")
            topIndexes += slots(g).indexOf(0) // spot 0 is the highest
        }
        assertTrue("always the same position at the top: $topIndexes", topIndexes.size >= 4)
    }
}
