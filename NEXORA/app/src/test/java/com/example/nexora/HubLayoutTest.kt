package com.example.nexora

import com.example.nexora.space.HubLayout
import com.example.nexora.space.SpaceScene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HubLayoutTest {

    private val names = listOf("MXH", "AI", "Music", "Bank", "Google", "Basic", "Game", "Terminal", "Driver", "Test")
    private val widths = names.map { HubLayout.widthEm(it) }

    @Test
    fun sameSalt_sameLayout_otherSalt_otherLayout() {
        val a = HubLayout.pick(widths, 1)
        val b = HubLayout.pick(widths, 1)
        val c = HubLayout.pick(widths, 2)
        for (i in a.indices) assertEquals(0f, a[i].distanceTo(b[i]), 0f)
        assertTrue("two salts gave the same layout", a.indices.any { a[it].distanceTo(c[it]) > 0.5f })
    }

    @Test
    fun everyGroupGetsAHome_everyTime() {
        for (n in listOf(0, 1, 2, 5, 12, 30)) {
            val homes = HubLayout.pick(List(n) { 2f }, n * 7)
            assertEquals(n, homes.size)
        }
    }

    @Test
    fun pickedLayouts_overlapMuchLessThanAnArbitraryDraw() {
        var single = 0f
        var picked = 0f
        val runs = 40
        for (salt in 0 until runs) {
            single += HubLayout.overlap(HubLayout.draw(widths.size, salt.toLong() * 1_000_003L), widths)
            picked += HubLayout.overlap(HubLayout.pick(widths, salt), widths)
        }
        assertTrue("picked $picked vs single $single", picked < single * 0.6f)
    }

    @Test
    fun manyGroups_stillPickedFromTheBest() {
        val many = List(20) { 4f }
        var single = 0f
        var picked = 0f
        for (salt in 0 until 20) {
            single += HubLayout.overlap(HubLayout.draw(many.size, salt.toLong() * 1_000_003L), many)
            picked += HubLayout.overlap(HubLayout.pick(many, salt), many)
        }
        assertTrue("picked $picked vs single $single", picked < single)
    }
}

class MemberLayoutTest {

    // A phone held upright: the focus 6.2 away, 68° across the width.
    private val halfW = 4.2f
    private val halfH = 9.3f
    private val boost = 2f

    @Test
    fun bestSeed_overlapsLessThanTheFirst() {
        var first = 0f
        var best = 0f
        for (count in listOf(4, 6, 8, 10, 12, 16, 20)) {
            for (hubSeed in listOf(3f, 41f, 90f, 500f, 7777f)) {
                val metrics = SpaceScene.ringMetrics(count, halfW, halfH, boost)
                val scores = List(24) { SpaceScene.satelliteOverlap(count, hubSeed + it * 37.31f, metrics, boost) }
                first += scores[0]
                best += scores.min()
            }
        }
        assertTrue("best $best vs first $first", best < first * 0.7f || first == 0f)
    }
}
