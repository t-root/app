package com.example.nexora.space

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Where the group names rest round the centre app. Drawn at random — each
 * draw (from a salt) gives a different arrangement — and of many draws the
 * one whose names overlap least, seen from every side the camera turns to.
 */
object HubLayout {
    const val TRIES = 160

    // Sides the camera is looked from, spread round the circle.
    private const val YAWS = 16

    // The idle camera sits this many times the content's reach from the middle
    // (see SpaceCamera.frameToFit: 0.46 of the width at the focal length).
    private const val CAMERA_REACH = 1.61f

    // On a tall screen the names are stretched up and down (SpaceScene.stretchY).
    private const val STRETCH_Y = 1.7f

    // Room kept round each name, as a share of its size.
    private const val PADDING = 0.2f

    /** A name's width in em, roughly: the pixel font runs about 0.62 em a letter. */
    fun widthEm(name: String): Float = max(1, name.length) * 0.62f

    /** The best of [tries] draws from [salt]: a home for each of the [widthsEm] names. */
    fun pick(widthsEm: List<Float>, salt: Int, tries: Int = TRIES): List<Vec3> {
        val n = widthsEm.size
        if (n == 0) return emptyList()
        var best: List<Vec3> = emptyList()
        var bestScore = Float.MAX_VALUE
        for (c in 0 until tries) {
            val homes = draw(n, salt.toLong() * 1_000_003L + c)
            val score = overlap(homes, widthsEm)
            if (score < bestScore) {
                best = homes
                bestScore = score
            }
            if (score == 0f) break
        }
        return best
    }

    /** One draw: every group gets a random seed, and from it its spot and its nudges. */
    internal fun draw(count: Int, key: Long): List<Vec3> {
        val random = Random(key)
        val seeds = List(count) { random.nextInt(65536).toFloat() }
        val slots = SpaceScene.hubSlots(seeds)
        return List(count) { SpaceScene.hubHome(slots[it], count, seeds[it]) }
    }

    /**
     * How much the names (and the centre app) overlap on screen, summed over the
     * sides the camera is looked from; box areas in units of the screen's width
     * at the focal length. 0 = clear from everywhere.
     */
    fun overlap(homes: List<Vec3>, widthsEm: List<Float>, stretchY: Float = STRETCH_Y): Float {
        val n = homes.size
        if (n == 0) return 0f
        var across = 0f
        for (i in 0 until n) {
            across = max(across, sqrt(homes[i].x * homes[i].x + homes[i].z * homes[i].z) + widthsEm[i] * SpaceScene.GROUP_TITLE / 2f)
        }
        val reach = max(across * CAMERA_REACH, across + SpaceScene.CAMERA_MARGIN)

        val left = FloatArray(n + 1)
        val right = FloatArray(n + 1)
        val bottom = FloatArray(n + 1)
        val top = FloatArray(n + 1)
        // The centre app, always in the middle.
        val centreHalf = SpaceScene.ICON_EM * CENTRE_SCALE / 2f / reach
        left[n] = -centreHalf
        right[n] = centreHalf
        bottom[n] = -centreHalf
        top[n] = centreHalf

        var total = 0f
        for (k in 0 until YAWS) {
            val theta = 2f * PI.toFloat() * (k + 0.3f) / YAWS
            val c = cos(theta)
            val s = sin(theta)
            for (i in 0 until n) {
                val p = homes[i]
                val depth = max(0.5f, reach - (p.x * c + p.z * s))
                val u = (p.x * s - p.z * c) / depth
                val v = p.y * stretchY / depth
                // A name shrinks when it comes close (SpaceScene.settle's closeRange).
                val size = SpaceScene.GROUP_TITLE * (depth / CLOSE_RANGE).coerceIn(0.12f, 1f) / depth
                val halfW = widthsEm[i] * size / 2f + PADDING * size
                left[i] = u - halfW
                right[i] = u + halfW
                // The badge hangs below the name: 0.6 em above its middle, 1.0 below.
                bottom[i] = v - (1f + PADDING) * size
                top[i] = v + (0.6f + PADDING) * size
            }
            for (i in 0 until n) {
                for (j in i + 1..n) {
                    val w = min(right[i], right[j]) - max(left[i], left[j])
                    val h = min(top[i], top[j]) - max(bottom[i], bottom[j])
                    if (w > 0f && h > 0f) total += w * h * (if (j == n) 2f else 1f)
                }
            }
        }
        return total
    }

    private const val CENTRE_SCALE = 0.55f
    private const val CLOSE_RANGE = 6f
}
