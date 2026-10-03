package com.example.nexora.graph

import androidx.compose.ui.geometry.Offset
import com.example.nexora.model.AppNode
import com.example.nexora.model.GraphEdge
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

object GraphEngine {

    fun generateGraph(
        nodes: List<AppNode>,
        width: Float,
        height: Float,
    ): Pair<List<AppNode>, List<GraphEdge>> {
        if (nodes.isEmpty()) return Pair(emptyList(), emptyList())

        val padding = 120f
        val usableWidth = max(200f, width - (padding * 2))
        val usableHeight = max(200f, height - (padding * 2))

        val combinedHash = nodes.asSequence().map { it.packageName }.sorted().joinToString(",").hashCode()
        val random = Random(combinedHash)

        val count = nodes.size
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = min(usableWidth, usableHeight) * 0.4f

        nodes.forEachIndexed { index, node ->
            if (node.position == Offset.Zero) {
                val angle = (2.0 * Math.PI * index / count) + (random.nextFloat() * 0.3 - 0.15)
                val distance = radius * (0.3f + (0.7f * random.nextFloat()))
                val x = (centerX + distance * cos(angle)).toFloat().coerceIn(padding, width - padding)
                val y = (centerY + distance * sin(angle)).toFloat().coerceIn(padding, height - padding)
                node.position = Offset(x, y)
            }
        }

        val edges = mutableListOf<GraphEdge>()
        val connectedSet = mutableSetOf<String>()

        if (count > 1) {
            connectedSet.add(nodes[0].packageName)
            for (i in 1 until count) {
                val currentNode = nodes[i]
                val targetPackage = connectedSet.minByOrNull { pkg ->
                    val other = nodes.first { it.packageName == pkg }
                    val dx = currentNode.position.x - other.position.x
                    val dy = currentNode.position.y - other.position.y
                    (dx * dx) + (dy * dy)
                } ?: connectedSet.random(random)

                edges.add(GraphEdge(currentNode.packageName, targetPackage))
                connectedSet.add(currentNode.packageName)
            }

            val extraEdgeCount = (count * 0.25f).toInt().coerceIn(1, 15)
            repeat(extraEdgeCount) {
                val n1 = nodes.random(random)
                val n2 = nodes.random(random)
                if (n1.packageName != n2.packageName) {
                    val exists = edges.any {
                        (it.fromPackage == n1.packageName && it.toPackage == n2.packageName) ||
                                (it.fromPackage == n2.packageName && it.toPackage == n1.packageName)
                    }
                    if (!exists) {
                        edges.add(GraphEdge(n1.packageName, n2.packageName))
                    }
                }
            }
        }

        return Pair(nodes, edges)
    }

    fun applyForceStep(
        nodes: List<AppNode>,
        edges: List<GraphEdge>,
        width: Float,
        height: Float,
        draggedNodePackage: String? = null,
    ) {
        if (nodes.isEmpty()) return

        val padding = 100f
        val k = sqrt((width * height) / nodes.size.toFloat()).coerceIn(150f, 350f)

        for (i in nodes.indices) {
            val nodeA = nodes[i]
            for (j in i + 1 until nodes.size) {
                val nodeB = nodes[j]

                var dx = nodeA.position.x - nodeB.position.x
                var dy = nodeA.position.y - nodeB.position.y
                var dist = sqrt((dx * dx) + (dy * dy))
                if (dist < 1f) {
                    dx = 1f
                    dy = 1f
                    dist = 1.414f
                }

                if (dist < k * 2.5f) {
                    val force = (k * k) / dist
                    val fx = (dx / dist) * force * 0.05f
                    val fy = (dy / dist) * force * 0.05f

                    if (nodeA.packageName != draggedNodePackage) {
                        nodeA.velocity = Offset(nodeA.velocity.x + fx, nodeA.velocity.y + fy)
                    }
                    if (nodeB.packageName != draggedNodePackage) {
                        nodeB.velocity = Offset(nodeB.velocity.x - fx, nodeB.velocity.y - fy)
                    }
                }
            }
        }

        val nodeMap = nodes.associateBy { it.packageName }
        for (edge in edges) {
            val nodeA = nodeMap[edge.fromPackage] ?: continue
            val nodeB = nodeMap[edge.toPackage] ?: continue

            val dx = nodeA.position.x - nodeB.position.x
            val dy = nodeA.position.y - nodeB.position.y
            var dist = sqrt((dx * dx) + (dy * dy))
            if (dist < 1f) dist = 1f

            val force = (dist * dist) / k
            val fx = (dx / dist) * force * 0.02f
            val fy = (dy / dist) * force * 0.02f

            if (nodeA.packageName != draggedNodePackage) {
                nodeA.velocity = Offset(nodeA.velocity.x - fx, nodeA.velocity.y - fy)
            }
            if (nodeB.packageName != draggedNodePackage) {
                nodeB.velocity = Offset(nodeB.velocity.x + fx, nodeB.velocity.y + fy)
            }
        }

        val damping = 0.85f
        for (node in nodes) {
            if (node.packageName == draggedNodePackage) {
                node.velocity = Offset.Zero
                continue
            }

            var vx = node.velocity.x * damping
            var vy = node.velocity.y * damping

            val speed = sqrt((vx * vx) + (vy * vy))
            if (speed > 15f) {
                vx = (vx / speed) * 15f
                vy = (vy / speed) * 15f
            }

            var newX = node.position.x + vx
            var newY = node.position.y + vy

            if (newX < padding) { newX = padding; vx = -vx * 0.5f }
            if (newX > width - padding) { newX = width - padding; vx = -vx * 0.5f }
            if (newY < padding) { newY = padding; vy = -vy * 0.5f }
            if (newY > height - padding) { newY = height - padding; vy = -vy * 0.5f }

            node.position = Offset(newX, newY)
            node.velocity = Offset(vx, vy)
        }
    }
}
