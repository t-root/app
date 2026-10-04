package com.example.nexora

import com.example.nexora.service.PxRect
import com.example.nexora.service.subtractRects
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverGeometryTest {

    private val base = PxRect(0, 100, 1000, 300)

    private fun area(pieces: List<PxRect>) = pieces.sumOf { it.width.toLong() * it.height }

    private fun noOverlap(pieces: List<PxRect>) =
        pieces.indices.all { i -> (i + 1 until pieces.size).none { j -> pieces[i].intersects(pieces[j]) } }

    @Test
    fun noHoles_keepsTheBlock() {
        assertEquals(listOf(base), subtractRects(base, emptyList()))
    }

    @Test
    fun holeOutside_keepsTheBlock() {
        assertEquals(listOf(base), subtractRects(base, listOf(PxRect(0, 400, 50, 500))))
    }

    @Test
    fun holeInside_leavesFourPiecesAroundIt() {
        val hole = PxRect(400, 150, 600, 250)
        val pieces = subtractRects(base, listOf(hole))
        assertEquals(4, pieces.size)
        assertEquals(area(listOf(base)) - area(listOf(hole)), area(pieces))
        assertTrue(noOverlap(pieces))
        assertFalse(pieces.any { it.intersects(hole) })
    }

    @Test
    fun holeOnTheEdge_leavesNoSliverOnThatSide() {
        val hole = PxRect(900, 100, 1000, 200) // the top-right corner
        val pieces = subtractRects(base, listOf(hole))
        assertEquals(area(listOf(base)) - area(listOf(hole)), area(pieces))
        assertFalse(pieces.any { it.intersects(hole) })
        assertTrue(pieces.none { it.isEmpty })
    }

    @Test
    fun holeSticksOutOfTheBlock_isClipped() {
        val hole = PxRect(-50, 50, 200, 200)
        val pieces = subtractRects(base, listOf(hole))
        assertEquals(area(listOf(base)) - 200L * 100, area(pieces))
        assertFalse(pieces.any { it.intersects(hole) })
    }

    @Test
    fun holeCoversTheBlock_leavesNothing() {
        assertTrue(subtractRects(base, listOf(PxRect(0, 0, 2000, 2000))).isEmpty())
    }

    @Test
    fun severalOverlappingHoles_areAllCutOut() {
        val holes = listOf(PxRect(100, 120, 300, 200), PxRect(250, 150, 500, 280), PxRect(800, 100, 900, 300))
        val pieces = subtractRects(base, holes)
        assertTrue(noOverlap(pieces))
        assertTrue(pieces.none { piece -> holes.any { it.intersects(piece) } })
        // The holes' union: first ∪ second overlap by (250..300)x(150..200).
        val union = 200L * 80 + 250L * 130 - 50L * 50 + 100L * 200
        assertEquals(area(listOf(base)) - union, area(pieces))
    }
}
