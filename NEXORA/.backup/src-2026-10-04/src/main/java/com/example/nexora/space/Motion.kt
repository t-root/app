package com.example.nexora.space

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Small mutable vector, reused every frame so the scene allocates nothing while animating. */
class Vec3(var x: Float = 0f, var y: Float = 0f, var z: Float = 0f) {
    fun set(x: Float, y: Float, z: Float): Vec3 {
        this.x = x
        this.y = y
        this.z = z
        return this
    }

    fun copy(o: Vec3) = set(o.x, o.y, o.z)
    fun add(o: Vec3) = set(x + o.x, y + o.y, z + o.z)
    fun addScaled(o: Vec3, s: Float) = set(x + o.x * s, y + o.y * s, z + o.z * s)
    fun lerp(a: Vec3, b: Vec3, t: Float) = set(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t)

    fun distanceTo(o: Vec3): Float {
        val dx = x - o.x
        val dy = y - o.y
        val dz = z - o.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}

/** Port of Code Universe's utils/animations.js. */
object Motion {

    fun damp(current: Float, target: Float, lambda: Float, dt: Float): Float =
        current + (target - current) * (1f - exp(-lambda * dt))

    fun dampVec(current: Vec3, target: Vec3, lambda: Float, dt: Float) {
        current.set(
            damp(current.x, target.x, lambda, dt),
            damp(current.y, target.y, lambda, dt),
            damp(current.z, target.z, lambda, dt),
        )
    }

    /** Layered slow drift around an anchor — never still, never repeating. */
    fun floatingOffset(out: Vec3, time: Float, phase: Float, ampX: Float, ampY: Float, ampZ: Float): Vec3 {
        val slow = 0.18f
        val med = 0.41f
        val fast = 0.77f
        return out.set(
            (sin(time * slow + phase) +
                sin(time * med + phase * 1.7f) * 0.45f +
                sin(time * fast + phase * 0.3f) * 0.18f) * ampX,
            (cos(time * slow * 0.9f + phase * 1.3f) +
                cos(time * med + phase * 0.6f) * 0.5f +
                sin(time * fast * 1.1f + phase * 2.1f) * 0.18f) * ampY,
            (sin(time * slow * 0.8f + phase * 0.7f) +
                cos(time * med + phase * 1.4f) * 0.45f +
                sin(time * fast + phase * 0.4f) * 0.18f) * ampZ,
        )
    }

    fun seededUnit(seed: Double): Double {
        val value = sin(seed * 12.9898 + 78.233) * 43758.5453
        return value - floor(value)
    }

    /** Travels smoothly between seeded random 3D waypoints. */
    fun wanderingOffset(out: Vec3, time: Float, seed: Float, ampX: Float, ampY: Float, ampZ: Float): Vec3 {
        val s = seed.toDouble()
        val segmentDuration = 2.2 + seededUnit(s * 3.1) * 1.7
        val segment = floor(time / segmentDuration)
        val progress = time / segmentDuration - segment
        val t = progress * progress * (3 - 2 * progress)
        fun point(step: Double, axis: Int) = seededUnit(s * 19.19 + step * 17.71 + axis * 53.17) * 2 - 1
        fun axis(a: Int) = (point(segment, a) + (point(segment + 1, a) - point(segment, a)) * t).toFloat()
        return out.set(axis(0) * ampX, axis(1) * ampY, axis(2) * ampZ)
    }
}

/** Port of Code Universe's utils/circuit.js: PCB-style traces with a streak of current. */
object Circuit {
    const val MAX_POINTS = 5
    const val STREAK_SPEED = 1.1f
    const val STREAK_STEPS = 10
    private const val CHAMFER = 0.12f

    fun hash01(a: Float, b: Float): Float {
        val x = sin(a * 127.1 + b * 311.7) * 43758.5453
        return (x - floor(x)).toFloat()
    }

    // The orders the three axes (x, y, depth) can be run in.
    private val ORDERS = arrayOf(
        intArrayOf(2, 0, 1), intArrayOf(2, 1, 0), intArrayOf(0, 2, 1),
        intArrayOf(1, 2, 0), intArrayOf(0, 1, 2), intArrayOf(1, 0, 2),
    )

    /**
     * Orthogonal 3D route from a to b, every bend 90°: one axis all the way,
     * then part of another, all of the third, and the rest of the second. Which
     * axis goes first and where the jog lies come from [h], so wires differ.
     */
    fun route(out: Array<Vec3>, a: Vec3, b: Vec3, h: Float): Int {
        val order = ORDERS[(floor(h * 6).toInt()).coerceIn(0, ORDERS.size - 1)]
        val lane = 0.2f + ((h * 7.13f) % 1f) * 0.6f
        val cur = floatArrayOf(a.x, a.y, a.z)
        val end = floatArrayOf(b.x, b.y, b.z)
        var n = 0
        fun push() {
            if (n > 0) {
                val p = out[n - 1]
                if (abs(p.x - cur[0]) + abs(p.y - cur[1]) + abs(p.z - cur[2]) < 1e-3f) return
            }
            out[n++].set(cur[0], cur[1], cur[2])
        }
        push()
        cur[order[0]] = end[order[0]]
        push()
        cur[order[1]] += (end[order[1]] - cur[order[1]]) * lane
        push()
        cur[order[2]] = end[order[2]]
        push()
        cur[order[1]] = end[order[1]]
        push()
        return n
    }

    /** Cuts every corner between two legs running across the screen at 45°. */
    fun chamfer(out: Array<Vec3>, pts: Array<Vec3>, n: Int): Int {
        var m = 0
        out[m++].copy(pts[0])
        for (j in 1 until n - 1) {
            val d0x = pts[j].x - pts[j - 1].x
            val d0y = pts[j].y - pts[j - 1].y
            val d0z = pts[j].z - pts[j - 1].z
            val d1x = pts[j + 1].x - pts[j].x
            val d1y = pts[j + 1].y - pts[j].y
            val d1z = pts[j + 1].z - pts[j].z
            val l0 = sqrt(d0x * d0x + d0y * d0y + d0z * d0z)
            val l1 = sqrt(d1x * d1x + d1y * d1y + d1z * d1z)
            val flat0 = l0 > 1e-4f && abs(d0z) < l0 * 0.5f
            val flat1 = l1 > 1e-4f && abs(d1z) < l1 * 0.5f
            val straight = l0 > 1e-4f && l1 > 1e-4f && (d0x * d1x + d0y * d1y + d0z * d1z) > 0.995f * l0 * l1
            if (straight || !flat0 || !flat1) {
                out[m++].copy(pts[j])
                continue
            }
            val r = min(CHAMFER, min(l0 * 0.45f, l1 * 0.45f))
            out[m++].set(pts[j].x - d0x * r / l0, pts[j].y - d0y * r / l0, pts[j].z - d0z * r / l0)
            out[m++].set(pts[j].x + d1x * r / l1, pts[j].y + d1y * r / l1, pts[j].z + d1z * r / l1)
        }
        out[m++].copy(pts[n - 1])
        return m
    }

    fun pointAlong(out: Vec3, pts: Array<Vec3>, n: Int, distance: Float): Vec3 {
        var dist = distance
        if (dist <= 0f) return out.copy(pts[0])
        for (j in 0 until n - 1) {
            val len = pts[j].distanceTo(pts[j + 1])
            if (dist <= len) return out.lerp(pts[j], pts[j + 1], if (len > 0f) dist / len else 0f)
            dist -= len
        }
        return out.copy(pts[n - 1])
    }

    fun polylineLength(pts: Array<Vec3>, n: Int): Float {
        var length = 0f
        for (j in 0 until n - 1) length += pts[j].distanceTo(pts[j + 1])
        return length
    }
}

/**
 * Port of animations.js createFlight: a quadratic Bezier arc from where a
 * body is to where it should be, lifted in the middle so it looks thrown
 * rather than slid. [end] may keep moving while in flight.
 */
class Flight(from: Vec3, to: Vec3, val delay: Float, duration: Float, arcHeight: Float, val startTime: Float) {
    private val start = Vec3().copy(from)
    private val ctrl = Vec3()
    val end = Vec3().copy(to)
    val duration = maxOf(0.15f, duration)

    init {
        ctrl.lerp(start, end, 0.5f)
        val dist = start.distanceTo(end)
        val lifted = arcHeight * minOf(1.5f, 0.4f + dist / 14f)
        if (kotlin.math.abs(end.y - start.y) / maxOf(0.001f, dist) < 0.85f) {
            ctrl.y += lifted
        } else {
            ctrl.x += lifted * 0.6f
            ctrl.z += lifted * 0.6f
        }
    }

    /** Writes the eased point at [time]; returns true once landed. */
    fun pointAt(out: Vec3, time: Float): Boolean {
        val t = ((time - startTime - delay) / duration).coerceIn(0f, 1f)
        if (time - startTime < delay) return false
        val e = 1f - (1f - t) * (1f - t) * (1f - t)
        val u = 1f - e
        out.set(
            start.x * u * u + ctrl.x * 2 * u * e + end.x * e * e,
            start.y * u * u + ctrl.y * 2 * u * e + end.y * e * e,
            start.z * u * u + ctrl.z * 2 * u * e + end.z * e * e,
        )
        return t >= 1f
    }
}
