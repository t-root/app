package com.example.nexora.space

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.asAndroidBitmap
import com.example.nexora.data.LineIconProcessor
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Draws a [SpaceScene] the way Code Universe does: black space, group names
 * as billboarded text with a [BADGE] under them, apps as their single-colour
 * line icons, and the focus's circuit traces with a streak of current.
 * Plain android.graphics, so the launcher screen and the live wallpaper share
 * it. Writes each body's projection and hit box back into it.
 */
class SpaceRenderer(private val density: Float, typeface: Typeface) {

    /** Draw each app's name under its icon (user option). */
    var showAppNames = false

    /**
     * Space kept clear at the screen's edges (px): nothing is drawn past it —
     * whatever would fall outside, or behind the camera, is pushed in to the edge.
     */
    var insetLeft = 12f * density
    var insetTop = 24f * density
    var insetRight = 12f * density
    var insetBottom = 24f * density

    private val tmp = FloatArray(3)
    private val tmpB = FloatArray(3)
    private val pts = Array(Circuit.MAX_POINTS) { Vec3() }
    private val cut = Array(Circuit.MAX_POINTS * 2) { Vec3() }
    private val p0 = Vec3()
    private val p1 = Vec3()
    private val rect = RectF()
    private val order = ArrayList<SpaceBody>()
    private val farFirst = Comparator<SpaceBody> { a, b -> b.depth.compareTo(a.depth) }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        this.typeface = typeface
        textAlign = Paint.Align.CENTER
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val tracePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val measurePaint = Paint().apply {
        this.typeface = typeface
        textSize = 100f
    }

    fun draw(canvas: Canvas, scene: SpaceScene, camera: SpaceCamera, width: Float, height: Float, time: Float) {
        canvas.drawColor(SpaceColors.BG)
        camera.beginFrame(width, height)

        for (body in scene.bodies) {
            if (body is SpaceHub && body.widthEm == 0f) body.widthEm = measurePaint.measureText(body.label) / 100f
            place(body, camera, width, height)
        }

        scene.center?.let { center ->
            for (hub in scene.hubs) drawCenterLink(canvas, scene, center, hub, camera, time)
        }
        scene.focused?.let { hub ->
            scene.members[hub]?.forEach { member -> drawMemberLink(canvas, scene, hub, member, camera, time) }
        }

        order.clear()
        for (body in scene.bodies) if (body.visible) order.add(body)
        order.sortWith(farFirst)
        for (body in order) {
            when (body) {
                is SpaceHub -> drawLabel(canvas, body)
                is SpaceNode -> drawIcon(canvas, body)
            }
        }
    }

    /**
     * Projects [body] and keeps it inside the view: size capped, then the
     * position clamped to the insets. Something behind the camera goes to the
     * edge in its direction. The push is kept so traces can bend to follow.
     */
    private fun place(body: SpaceBody, camera: SpaceCamera, width: Float, height: Float) {
        camera.toCamera(body.pos, tmp)
        val x = tmp[0]
        val y = tmp[1]
        val d = tmp[2]
        val inFront = d >= NEAR
        val cx = width / 2f
        val cy = height / 2f
        val roomW = width - insetLeft - insetRight
        val roomH = height - insetTop - insetBottom

        var size = body.scale * camera.focal / maxOf(abs(d), NEAR)
        size = if (body is SpaceHub) {
            minOf(size, roomW * MAX_TITLE_SHARE / maxOf(body.widthEm, 0.1f), roomH * 0.08f)
        } else {
            minOf(size, roomW * MAX_ICON_SHARE / SpaceScene.ICON_EM)
        }
        body.sizePx = size

        // Extents round the anchor point: a title with its badge below, or an
        // icon (with its name below when names are shown).
        val halfW: Float
        val up: Float
        val down: Float
        if (body is SpaceHub) {
            halfW = body.widthEm * size / 2f
            up = size * 0.6f
            down = size * 1.0f
        } else {
            halfW = SpaceScene.ICON_EM * size / 2f
            up = halfW
            down = halfW + if (showAppNames) size * NAME_EM * 1.4f else 0f
        }
        val minX = insetLeft + halfW
        val maxX = width - insetRight - halfW
        val minY = insetTop + up
        val maxY = height - insetBottom - down

        var sx: Float
        var sy: Float
        if (inFront) {
            sx = cx + x * camera.focal / d
            sy = cy - y * camera.focal / d
        } else {
            // Behind the camera: out from the middle in its direction, to the edge.
            var dx = x
            var dy = -y
            val len = sqrt(dx * dx + dy * dy)
            if (len < 1e-4f) {
                dx = 1f
                dy = 0f
            } else {
                dx /= len
                dy /= len
            }
            val tx = if (abs(dx) > 1e-4f) (roomW / 2f) / abs(dx) else Float.MAX_VALUE
            val ty = if (abs(dy) > 1e-4f) (roomH / 2f) / abs(dy) else Float.MAX_VALUE
            val t = minOf(tx, ty)
            sx = cx + dx * t
            sy = cy + dy * t
        }
        val rawX = sx
        val rawY = sy
        sx = if (minX <= maxX) sx.coerceIn(minX, maxX) else cx
        sy = if (minY <= maxY) sy.coerceIn(minY, maxY) else cy

        body.screenX = sx
        body.screenY = sy
        body.pushX = if (inFront) sx - rawX else 0f
        body.pushY = if (inFront) sy - rawY else 0f
        body.depth = abs(d)
        body.visible = body.opacity > 0.01f && size > 1.5f
    }

    private fun drawLabel(canvas: Canvas, hub: SpaceHub) {
        val size = hub.sizePx
        // anchorY middle: centre the title's em box on the point.
        labelPaint.textSize = size
        labelPaint.color = withAlpha(hub.color, hub.opacity)
        val fm = labelPaint.fontMetrics
        canvas.drawText(hub.label, hub.screenX, hub.screenY - (fm.ascent + fm.descent) / 2f, labelPaint)

        val halfW = hub.widthEm * size / 2f
        hub.hitLeft = hub.screenX - halfW
        hub.hitRight = hub.screenX + halfW
        hub.hitTop = hub.screenY - size * 0.6f
        hub.hitBottom = hub.screenY + size * 1.0f

        // The badge: 0.32 em, 0.7 em below, at 75% of the title's opacity.
        val badgeSize = size * 0.32f
        if (badgeSize < 3f) return
        labelPaint.textSize = badgeSize
        labelPaint.color = withAlpha(hub.color, hub.opacity * 0.75f)
        val bm = labelPaint.fontMetrics
        canvas.drawText(hub.badge, hub.screenX, hub.screenY + size * 0.7f - (bm.ascent + bm.descent) / 2f, labelPaint)
    }

    private fun drawIcon(canvas: Canvas, node: SpaceNode) {
        val half = SpaceScene.ICON_EM * node.sizePx / 2f
        rect.set(node.screenX - half, node.screenY - half, node.screenX + half, node.screenY + half)
        iconPaint.alpha = (node.opacity * 255).toInt().coerceIn(0, 255)
        canvas.drawBitmap(node.app.iconBitmap.asAndroidBitmap(), null, rect, iconPaint)
        node.hitLeft = rect.left
        node.hitTop = rect.top
        node.hitRight = rect.right
        node.hitBottom = rect.bottom

        if (!showAppNames) return
        val textSize = node.sizePx * NAME_EM
        if (textSize < 5f) return
        labelPaint.textSize = textSize
        labelPaint.color = withAlpha(node.color, node.opacity * 0.85f)
        canvas.drawText(node.app.appName, node.screenX, rect.bottom + textSize * 1.05f, labelPaint)
    }

    /** The focus wired to each of its apps: the trace is the app's own line. */
    private fun drawMemberLink(canvas: Canvas, scene: SpaceScene, hub: SpaceHub, member: MemberNode, camera: SpaceCamera, time: Float) {
        val start = scene.linkStart(member.index) ?: return
        // As thick as the icon's stroke where the app is, so the icon reads as wired in.
        val lineWidth = maxOf(density, SpaceScene.ICON_EM * member.sizePx * LineIconProcessor.STROKE_SHARE)
        val lineAlpha = if (member.arrived) member.opacity else TRACE_ALPHA_GROWING
        drawTrace(canvas, hub, member, start, member.color, lineAlpha, lineWidth, Circuit.hash01(hub.seed, member.index + 1f), camera, time)
    }

    /** The centre app wired to a group, in the group's colour. */
    private fun drawCenterLink(canvas: Canvas, scene: SpaceScene, center: CenterNode, hub: SpaceHub, camera: SpaceCamera, time: Float) {
        val start = scene.centerLinkStart(hub) ?: return
        if (!center.visible && !hub.visible) return
        val lineWidth = maxOf(density, SpaceScene.ICON_EM * center.sizePx * LineIconProcessor.STROKE_SHARE * 0.8f)
        val lineAlpha = minOf(center.opacity, maxOf(hub.opacity, 0.3f))
        // The current runs out from the centre to the group.
        drawTrace(canvas, center, hub, start, hub.group.color, lineAlpha, lineWidth, Circuit.hash01(hub.seed, 0.5f), camera, time)
    }

    /**
     * SatelliteLinks: the trace grows out of [a], then a streak keeps running
     * down it. If either end was pushed in from the edge, the wire bends along
     * its length to meet it there.
     */
    private fun drawTrace(
        canvas: Canvas,
        a: SpaceBody,
        b: SpaceBody,
        start: Float,
        color: Int,
        lineAlpha: Float,
        lineWidth: Float,
        h: Float,
        camera: SpaceCamera,
        time: Float,
    ) {
        val grow = ((time - start) / SpaceScene.GROW_TIME).coerceAtMost(1f)
        if (grow <= 0f || lineAlpha <= 0.01f) return
        val n = Circuit.chamfer(cut, pts, Circuit.route(pts, a.pos, b.pos, h))
        val length = Circuit.polylineLength(cut, n)
        if (length <= 0f) return
        val drawn = length * grow
        warpAx = a.pushX
        warpAy = a.pushY
        warpBx = b.pushX
        warpBy = b.pushY
        traceLength = length

        tracePaint.strokeWidth = lineWidth
        tracePaint.color = withAlpha(color, lineAlpha)
        var run = 0f
        for (j in 0 until n - 1) {
            if (run >= drawn) break
            val len = cut[j].distanceTo(cut[j + 1])
            p1.copy(cut[j + 1])
            val end = minOf(run + len, drawn)
            if (run + len > drawn) p1.lerp(cut[j], cut[j + 1], (drawn - run) / len)
            segment(canvas, cut[j], run, p1, end, camera)
            run += len
        }

        // The current: a near-white core with a halo of the line's colour,
        // fading out along its tail, and a bright spark at its head.
        val core = whiten(color, STREAK_WHITEN)
        val from: Float
        val span: Float
        if (grow < 1f) {
            // The spark riding the growing tip.
            from = maxOf(0f, drawn - TIP)
            span = minOf(TIP, drawn)
        } else {
            val trail = minOf(STREAK_LENGTH, length * 0.7f)
            val total = length + trail
            val t = (time * Circuit.STREAK_SPEED) / total + h
            val head = (t % 1f) * total
            from = head - trail
            span = trail
        }
        for (pass in 0..1) {
            val halo = pass == 0
            tracePaint.strokeWidth = lineWidth * if (halo) HALO_WIDTH else CORE_WIDTH
            Circuit.pointAlong(p0, cut, n, from)
            var at0 = from
            for (k in 1..Circuit.STREAK_STEPS) {
                val at1 = from + span * k / Circuit.STREAK_STEPS
                Circuit.pointAlong(p1, cut, n, at1)
                val fade = lineAlpha * k / Circuit.STREAK_STEPS
                tracePaint.color = if (halo) withAlpha(color, fade * HALO_ALPHA) else withAlpha(core, fade)
                segment(canvas, p0, at0, p1, at1, camera)
                p0.copy(p1)
                at0 = at1
            }
        }
        // The head, while it is still on the wire.
        if (from + span in 0f..length && projectWarped(p1, from + span, camera, tmp)) {
            sparkPaint.color = withAlpha(color, lineAlpha * HALO_ALPHA)
            canvas.drawCircle(tmp[0], tmp[1], lineWidth * HALO_WIDTH * 0.75f, sparkPaint)
            sparkPaint.color = withAlpha(0xFFFFFFFF.toInt(), lineAlpha)
            canvas.drawCircle(tmp[0], tmp[1], lineWidth * CORE_WIDTH * 0.75f, sparkPaint)
        }
    }

    /** A piece of the current trace; [atA] / [atB] = distance along it, for the bend. */
    /**
     * A piece of the current trace; [atA] / [atB] = distance along it, for the
     * bend. A piece reaching behind the camera is cut where it crosses the near
     * plane — projecting that end as-is would fling the line across the screen.
     */
    private fun segment(canvas: Canvas, a: Vec3, atA: Float, b: Vec3, atB: Float, camera: SpaceCamera) {
        camera.toCamera(a, tmp)
        val da = tmp[2]
        camera.toCamera(b, tmpB)
        val db = tmpB[2]
        if (da < CLIP_NEAR && db < CLIP_NEAR) return
        var pa = a
        var pb = b
        var sa = atA
        var sb = atB
        if (da < CLIP_NEAR || db < CLIP_NEAR) {
            val t = (CLIP_NEAR - da) / (db - da)
            clipPoint.lerp(a, b, t)
            val at = atA + (atB - atA) * t
            if (da < CLIP_NEAR) {
                pa = clipPoint
                sa = at
            } else {
                pb = clipPoint
                sb = at
            }
        }
        if (projectWarped(pa, sa, camera, tmp) && projectWarped(pb, sb, camera, tmpB)) {
            canvas.drawLine(tmp[0], tmp[1], tmpB[0], tmpB[1], tracePaint)
        }
    }

    private val clipPoint = Vec3()

    // The current trace's end pushes, blended along its length.
    private var warpAx = 0f
    private var warpAy = 0f
    private var warpBx = 0f
    private var warpBy = 0f
    private var traceLength = 1f

    private fun projectWarped(p: Vec3, at: Float, camera: SpaceCamera, out: FloatArray): Boolean {
        if (!camera.project(p, out)) return false
        val f = (at / traceLength).coerceIn(0f, 1f)
        out[0] += warpAx * (1f - f) + warpBx * f
        out[1] += warpAy * (1f - f) + warpBy * f
        return true
    }

    private fun withAlpha(color: Int, alpha: Float): Int =
        (color and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24)

    private fun whiten(color: Int, k: Float): Int {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return (0xFF shl 24) or
            ((r + (255 - r) * k).toInt() shl 16) or
            ((g + (255 - g) * k).toInt() shl 8) or
            (b + (255 - b) * k).toInt()
    }

    private companion object {
        const val TIP = 0.5f
        const val NEAR = 0.3f
        // Wires are cut this far in front of the camera.
        const val CLIP_NEAR = 0.6f
        // A group name is never wider than this share of the screen.
        const val MAX_TITLE_SHARE = 0.45f
        // An icon is never wider than this share of the screen.
        const val MAX_ICON_SHARE = 0.2f
        // App name size, in the same em as the icon (ICON_EM wide).
        const val NAME_EM = 0.5f
        // Before its app has popped in, the growing trace still shows.
        const val TRACE_ALPHA_GROWING = 0.85f
        // The current must stand out on a wire of its own colour.
        const val STREAK_WHITEN = 0.75f
        const val STREAK_LENGTH = 1.3f
        const val CORE_WIDTH = 1.4f
        const val HALO_WIDTH = 3.6f
        const val HALO_ALPHA = 0.4f
    }
}
