package com.example.nexora.graph

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.nexora.model.AppNode
import kotlin.math.sqrt
import kotlin.random.Random

data class GridSlot(
    val col: Int,
    val row: Int,
    val center: Offset,
    val appNode: AppNode?
)

data class CircuitTrace(
    val fromCol: Int,
    val fromRow: Int,
    val toCol: Int,
    val toRow: Int,
    val pathPoints: List<Offset>,
    val color: Color,
    val speed: Float,
    val totalLength: Float
)

object CircuitEngine {

    val NEON_COLORS = listOf(
        Color(0xFF00E5FF), // Cyan
        Color(0xFFFF007F), // Magenta
        Color(0xFF00FF66), // Lime Green
        Color(0xFFFF9100), // Bright Orange
        Color(0xFFFFEE00), // Yellow
        Color(0xFFB000FF), // Purple
        Color(0xFF0099FF)  // Blue
    )

    fun layoutGrid(
        nodes: List<AppNode>,
        width: Float,
        height: Float,
        columns: Int = 5
    ): Pair<List<GridSlot>, List<CircuitTrace>> {
        if (width <= 0f || height <= 0f) return Pair(emptyList(), emptyList())

        val topPadding = 180f
        val bottomPadding = 120f
        val usableHeight = height - topPadding - bottomPadding
        val slotWidth = width / columns
        val rows = (usableHeight / (slotWidth * 1.2f)).toInt().coerceIn(4, 8)
        val slotHeight = usableHeight / rows

        val slots = mutableListOf<GridSlot>()

        for (r in 0 until rows) {
            for (c in 0 until columns) {
                val index = r * columns + c
                val appNode = if (index < nodes.size) nodes[index] else null

                val cx = c * slotWidth + (slotWidth / 2f)
                val cy = topPadding + r * slotHeight + (slotHeight / 2f)

                if (appNode != null) {
                    appNode.position = Offset(cx, cy)
                }

                slots.add(GridSlot(col = c, row = r, center = Offset(cx, cy), appNode = appNode))
            }
        }

        // Generate orthogonal circuit traces between active app slots only
        val traces = mutableListOf<CircuitTrace>()
        val random = Random(nodes.size)

        for (r in 0 until rows) {
            for (c in 0 until columns) {
                val currentIndex = r * columns + c
                if (currentIndex >= nodes.size) continue // Skip empty slot!

                val currentSlot = slots[currentIndex]

                // Connect to adjacent right slot if filled
                if (c + 1 < columns) {
                    val rightIndex = r * columns + (c + 1)
                    if (rightIndex < nodes.size) {
                        val rightSlot = slots[rightIndex]
                        traces.add(buildOrthogonalTrace(currentSlot, rightSlot, random))
                    }
                }

                // Connect to adjacent bottom slot if filled
                if (r + 1 < rows) {
                    val bottomIndex = (r + 1) * columns + c
                    if (bottomIndex < nodes.size) {
                        val bottomSlot = slots[bottomIndex]
                        traces.add(buildOrthogonalTrace(currentSlot, bottomSlot, random))
                    }
                }

                // Random diagonal/jump connection to another active slot
                if (random.nextFloat() < 0.25f) {
                    val targetCol = (c + random.nextInt(-1, 2)).coerceIn(0, columns - 1)
                    val targetRow = (r + random.nextInt(1, 3)).coerceIn(0, rows - 1)
                    val targetIndex = targetRow * columns + targetCol
                    if (targetIndex != currentIndex && targetIndex < nodes.size) {
                        val targetSlot = slots[targetIndex]
                        traces.add(buildOrthogonalTrace(currentSlot, targetSlot, random))
                    }
                }
            }
        }

        return Pair(slots, traces)
    }

    private fun buildOrthogonalTrace(
        from: GridSlot,
        to: GridSlot,
        random: Random
    ): CircuitTrace {
        val p1 = from.center
        val p2 = to.center

        val points = mutableListOf<Offset>()
        points.add(p1)

        val midY = (p1.y + p2.y) / 2f
        if (p1.x != p2.x && p1.y != p2.y) {
            points.add(Offset(p1.x, midY))
            points.add(Offset(p2.x, midY))
        } else if (p1.x != p2.x) {
            points.add(Offset((p1.x + p2.x) / 2f, p1.y))
        } else {
            points.add(Offset(p1.x, midY))
        }
        points.add(p2)

        var totalLen = 0f
        for (i in 0 until points.size - 1) {
            val dx = points[i + 1].x - points[i].x
            val dy = points[i + 1].y - points[i].y
            totalLen += sqrt(dx * dx + dy * dy)
        }

        val traceColor = NEON_COLORS.random(random)
        val speed = random.nextFloat() * 120f + 80f

        return CircuitTrace(
            fromCol = from.col,
            fromRow = from.row,
            toCol = to.col,
            toRow = to.row,
            pathPoints = points,
            color = traceColor,
            speed = speed,
            totalLength = maxOf(1f, totalLen)
        )
    }
}
