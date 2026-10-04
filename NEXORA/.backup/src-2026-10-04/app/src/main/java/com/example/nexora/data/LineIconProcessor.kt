package com.example.nexora.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Turns an app icon into single-colour line art: the outlines of the icon's
 * shapes, drawn with an even, round-capped stroke in the app's brand colour
 * (a filled YouTube logo becomes the outlined rounded rect + play triangle).
 *
 *  1. Render the icon. For an adaptive icon with a plain background only the
 *     foreground is kept, so the outline follows the logo, not the launcher mask.
 *  2. Segment the pixels: transparent vs. a few colour clusters (k-means),
 *     tiny clusters (anti-aliasing blends) merged away, speckles smoothed.
 *  3. Region boundaries become edge pixels; specks of edge are dropped.
 *  4. A distance transform turns the edges into an anti-aliased stroke.
 */
object LineIconProcessor {

    const val SIZE = 160
    private const val STROKE_HALF = 2.6f

    /** Stroke width as a share of the icon's width (the traces match it). */
    const val STROKE_SHARE = 2f * STROKE_HALF / SIZE
    private const val K = 4
    private const val MIN_CLUSTER_SHARE = 0.03f
    private const val MERGE_DISTANCE = 48f
    private const val MIN_EDGE_COMPONENT = 12

    class Result(val bitmap: Bitmap, val color: Int)

    fun process(drawable: Drawable): Result {
        var background: Int? = null
        var backgroundShare = 0
        val pixels: IntArray
        if (drawable is AdaptiveIconDrawable && drawable.foreground != null) {
            val bg = drawable.background?.let { drawLayer(it) }
            if (bg == null || isUniform(bg)) {
                pixels = drawLayer(drawable.foreground)
                background = bg?.let { averageColor(it) }
                backgroundShare = pixels.count { Color.alpha(it) < 128 }
            } else {
                pixels = drawFull(drawable)
            }
        } else {
            pixels = drawFull(drawable)
        }

        val color = lineColor(pixels, background, backgroundShare)
        val labels = segment(pixels)
        val edges = edgePixels(labels)
        dropSpecks(edges)
        return Result(stroke(edges, color), color)
    }

    // ---- 1. Rendering

    /** An adaptive layer is 108dp of which the middle 72dp show: crop to that. */
    private fun drawLayer(layer: Drawable): IntArray {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        layer.setBounds(-SIZE / 4, -SIZE / 4, SIZE + SIZE / 4, SIZE + SIZE / 4)
        layer.draw(Canvas(bitmap))
        return pixelsOf(bitmap)
    }

    private fun drawFull(drawable: Drawable): IntArray {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, SIZE, SIZE)
        drawable.draw(Canvas(bitmap))
        return pixelsOf(bitmap)
    }

    private fun pixelsOf(bitmap: Bitmap): IntArray {
        val out = IntArray(SIZE * SIZE)
        bitmap.getPixels(out, 0, SIZE, 0, 0, SIZE, SIZE)
        bitmap.recycle()
        return out
    }

    private fun isUniform(pixels: IntArray): Boolean {
        var n = 0
        var sr = 0.0
        var sg = 0.0
        var sb = 0.0
        var sq = 0.0
        for (p in pixels) {
            if (Color.alpha(p) < 250) return false
            val r = Color.red(p).toDouble()
            val g = Color.green(p).toDouble()
            val b = Color.blue(p).toDouble()
            sr += r
            sg += g
            sb += b
            sq += r * r + g * g + b * b
            n++
        }
        if (n == 0) return true
        val mean2 = (sr * sr + sg * sg + sb * sb) / (n.toDouble() * n)
        val variance = sq / n - mean2
        return variance < 3 * 14.0 * 14.0
    }

    private fun averageColor(pixels: IntArray): Int {
        var r = 0L
        var g = 0L
        var b = 0L
        for (p in pixels) {
            r += Color.red(p)
            g += Color.green(p)
            b += Color.blue(p)
        }
        val n = pixels.size.coerceAtLeast(1)
        return Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }

    // ---- Colour: the most present saturated hue, lifted to read on black.

    private fun lineColor(pixels: IntArray, background: Int?, backgroundShare: Int): Int {
        val bins = 24
        val weight = FloatArray(bins)
        val sumR = FloatArray(bins)
        val sumG = FloatArray(bins)
        val sumB = FloatArray(bins)
        val hsv = FloatArray(3)
        fun add(c: Int, count: Float) {
            Color.colorToHSV(c, hsv)
            val w = hsv[1] * hsv[2] * count
            if (hsv[1] < 0.25f || hsv[2] < 0.25f) return
            val bin = ((hsv[0] / 360f) * bins).toInt().coerceIn(0, bins - 1)
            weight[bin] += w
            sumR[bin] += Color.red(c) * w
            sumG[bin] += Color.green(c) * w
            sumB[bin] += Color.blue(c) * w
        }
        for (p in pixels) if (Color.alpha(p) >= 128) add(p, 1f)
        if (background != null) add(background, backgroundShare.toFloat())

        var best = 0
        for (i in 1 until bins) if (weight[i] > weight[best]) best = i
        val total = weight.sum()
        if (total < SIZE * SIZE * 0.02f) return Color.rgb(230, 230, 230) // a grey icon: light grey lines

        val w = weight[best]
        val base = Color.rgb((sumR[best] / w).toInt(), (sumG[best] / w).toInt(), (sumB[best] / w).toInt())
        Color.colorToHSV(base, hsv)
        hsv[1] = min(hsv[1], 0.95f)
        hsv[2] = max(hsv[2], 0.92f)
        return Color.HSVToColor(hsv)
    }

    // ---- 2. Segmentation: label 0 = transparent, 1.. = colour clusters.

    private fun segment(pixels: IntArray): IntArray {
        val n = pixels.size
        val labels = IntArray(n)
        val opaque = (0 until n).filter { Color.alpha(pixels[it]) >= 128 }
        if (opaque.isEmpty()) return labels

        // k-means++-ish init: start anywhere, then the farthest pixel each time.
        val cr = FloatArray(K)
        val cg = FloatArray(K)
        val cb = FloatArray(K)
        var k = 0
        fun setCenter(i: Int, p: Int) {
            cr[i] = Color.red(p).toFloat()
            cg[i] = Color.green(p).toFloat()
            cb[i] = Color.blue(p).toFloat()
        }
        setCenter(k++, pixels[opaque[opaque.size / 2]])
        while (k < K) {
            var far = -1f
            var farIdx = opaque[0]
            for (i in opaque) {
                val d = nearestDistance(pixels[i], cr, cg, cb, k)
                if (d > far) {
                    far = d
                    farIdx = i
                }
            }
            if (far < MERGE_DISTANCE * MERGE_DISTANCE) break
            setCenter(k++, pixels[farIdx])
        }

        val assign = IntArray(n) { -1 }
        repeat(8) {
            val sr = FloatArray(k)
            val sg = FloatArray(k)
            val sb = FloatArray(k)
            val cnt = IntArray(k)
            for (i in opaque) {
                val c = nearestIndex(pixels[i], cr, cg, cb, k)
                assign[i] = c
                sr[c] += Color.red(pixels[i]).toFloat()
                sg[c] += Color.green(pixels[i]).toFloat()
                sb[c] += Color.blue(pixels[i]).toFloat()
                cnt[c]++
            }
            for (c in 0 until k) if (cnt[c] > 0) {
                cr[c] = sr[c] / cnt[c]
                cg[c] = sg[c] / cnt[c]
                cb[c] = sb[c] / cnt[c]
            }
        }

        // Merge clusters with near-identical colours, and drop tiny ones
        // (the blends anti-aliasing leaves between two colours).
        val counts = IntArray(k)
        for (i in opaque) counts[assign[i]]++
        val target = IntArray(k) { it }
        for (a in 0 until k) for (b in 0 until a) {
            val dr = cr[a] - cr[b]
            val dg = cg[a] - cg[b]
            val db = cb[a] - cb[b]
            if (dr * dr + dg * dg + db * db < MERGE_DISTANCE * MERGE_DISTANCE) target[a] = target[b]
        }
        val minCount = (opaque.size * MIN_CLUSTER_SHARE).toInt()
        for (a in 0 until k) {
            if (counts[a] >= minCount) continue
            var bestB = -1
            var bestD = Float.MAX_VALUE
            for (b in 0 until k) {
                if (b == a || counts[b] < minCount) continue
                val dr = cr[a] - cr[b]
                val dg = cg[a] - cg[b]
                val db = cb[a] - cb[b]
                val d = dr * dr + dg * dg + db * db
                if (d < bestD) {
                    bestD = d
                    bestB = b
                }
            }
            if (bestB >= 0) target[a] = target[bestB]
        }
        for (i in opaque) labels[i] = target[assign[i]] + 1

        return modeFilter(labels)
    }

    private fun distanceTo(p: Int, cr: FloatArray, cg: FloatArray, cb: FloatArray, c: Int): Float {
        val dr = Color.red(p) - cr[c]
        val dg = Color.green(p) - cg[c]
        val db = Color.blue(p) - cb[c]
        return dr * dr + dg * dg + db * db
    }

    private fun nearestIndex(p: Int, cr: FloatArray, cg: FloatArray, cb: FloatArray, k: Int): Int {
        var best = 0
        var bestD = Float.MAX_VALUE
        for (c in 0 until k) {
            val d = distanceTo(p, cr, cg, cb, c)
            if (d < bestD) {
                bestD = d
                best = c
            }
        }
        return best
    }

    private fun nearestDistance(p: Int, cr: FloatArray, cg: FloatArray, cb: FloatArray, k: Int): Float {
        var bestD = Float.MAX_VALUE
        for (c in 0 until k) bestD = min(bestD, distanceTo(p, cr, cg, cb, c))
        return bestD
    }

    /** 3x3 majority vote: removes single-pixel speckles along boundaries. */
    private fun modeFilter(labels: IntArray): IntArray {
        val out = labels.copyOf()
        val votes = IntArray(K + 1)
        for (y in 1 until SIZE - 1) for (x in 1 until SIZE - 1) {
            votes.fill(0)
            for (dy in -1..1) for (dx in -1..1) votes[labels[(y + dy) * SIZE + x + dx]]++
            var best = labels[y * SIZE + x]
            for (l in votes.indices) if (votes[l] > votes[best]) best = l
            if (votes[best] >= 5) out[y * SIZE + x] = best
        }
        return out
    }

    // ---- 3. Edges

    private fun edgePixels(labels: IntArray): BooleanArray {
        val edges = BooleanArray(labels.size)
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            val i = y * SIZE + x
            val l = labels[i]
            if (l == 0) continue
            edges[i] = x == 0 || y == 0 || x == SIZE - 1 || y == SIZE - 1 ||
                labels[i - 1] != l || labels[i + 1] != l || labels[i - SIZE] != l || labels[i + SIZE] != l
        }
        return edges
    }

    private fun dropSpecks(edges: BooleanArray) {
        val seen = BooleanArray(edges.size)
        val stack = IntArray(edges.size)
        val component = IntArray(edges.size)
        for (start in edges.indices) {
            if (!edges[start] || seen[start]) continue
            var top = 0
            var size = 0
            stack[top++] = start
            seen[start] = true
            while (top > 0) {
                val i = stack[--top]
                component[size++] = i
                val x = i % SIZE
                val y = i / SIZE
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= SIZE || ny >= SIZE) continue
                    val j = ny * SIZE + nx
                    if (edges[j] && !seen[j]) {
                        seen[j] = true
                        stack[top++] = j
                    }
                }
            }
            if (size < MIN_EDGE_COMPONENT) for (c in 0 until size) edges[component[c]] = false
        }
    }

    // ---- 4. Stroke: distance to the nearest edge pixel -> anti-aliased line.

    private fun stroke(edges: BooleanArray, color: Int): Bitmap {
        val inf = 1e6f
        val dist = FloatArray(edges.size) { if (edges[it]) 0f else inf }
        val diag = sqrt(2f)
        // Two-pass chamfer distance (1, sqrt 2).
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            val i = y * SIZE + x
            var d = dist[i]
            if (x > 0) d = min(d, dist[i - 1] + 1f)
            if (y > 0) {
                d = min(d, dist[i - SIZE] + 1f)
                if (x > 0) d = min(d, dist[i - SIZE - 1] + diag)
                if (x < SIZE - 1) d = min(d, dist[i - SIZE + 1] + diag)
            }
            dist[i] = d
        }
        for (y in SIZE - 1 downTo 0) for (x in SIZE - 1 downTo 0) {
            val i = y * SIZE + x
            var d = dist[i]
            if (x < SIZE - 1) d = min(d, dist[i + 1] + 1f)
            if (y < SIZE - 1) {
                d = min(d, dist[i + SIZE] + 1f)
                if (x < SIZE - 1) d = min(d, dist[i + SIZE + 1] + diag)
                if (x > 0) d = min(d, dist[i + SIZE - 1] + diag)
            }
            dist[i] = d
        }

        val rgb = color and 0x00FFFFFF
        val out = IntArray(edges.size)
        for (i in out.indices) {
            val a = ((STROKE_HALF + 0.75f - dist[i]) / 1.5f).coerceIn(0f, 1f)
            if (a > 0f) out[i] = ((a * 255).toInt() shl 24) or rgb
        }
        return Bitmap.createBitmap(out, SIZE, SIZE, Bitmap.Config.ARGB_8888)
    }
}
