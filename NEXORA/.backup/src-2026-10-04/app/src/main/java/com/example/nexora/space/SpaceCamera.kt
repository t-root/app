package com.example.nexora.space

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/**
 * Like Code Universe's CameraController: orbits horizontally at eye level
 * around a look-at point — the origin, or while a group is focused that
 * group, so the focus stays dead centre while it turns. Horizontal drag
 * turns it, vertical swipe or pinch changes the radius; idle it keeps
 * turning slowly, and a new focus swings it round to face the focus.
 */
class SpaceCamera {
    var theta = 0.4f
    var radius = IDLE_RADIUS

    private var idleTime = 1f

    private var camX = 0f
    private var camY = 0f
    private var camZ = 0f

    // What it orbits and looks at; eased toward lookGoal.
    private val look = Vec3()
    private val lookGoal = Vec3()
    private var rightX = 1f
    private var rightZ = 0f
    private var fwdX = 0f
    private var fwdZ = -1f
    private var centerX = 0f
    private var centerY = 0f
    private var viewWidth = 1f
    private var viewHeight = 1f

    /** Screen height / width (portrait > 1). */
    var aspect = 2f
        private set

    // A manual zoom wins over auto-framing until the next focus / unfocus.
    private var userZoomed = false

    /**
     * Closest the camera may come to what it orbits: just outside the content
     * (set by the scene every frame), so zooming in never puts the camera
     * among the groups, where they balloon and their wires smear.
     */
    var contentRadius = 0f
        set(value) {
            field = value
            minRadius = max(MIN_RADIUS, value)
        }
    private var minRadius = MIN_RADIUS

    var focal = 1000f
        private set

    /** Only turns on its own while nothing is focused (web: idle mode). */
    var autoRotate = true

    // Where a new focus / going back to idle swings the camera (eased; a drag cancels).
    private var goalTheta: Float? = null
    private var goalRadius: Float? = null

    /** Swing round to face the focus (+Z) at the web's focus framing radius. */
    fun frameFocus() {
        val half = (PI / 2).toFloat()
        val turn = (2 * PI).toFloat()
        goalTheta = half + Math.round((theta - half) / turn) * turn
        goalRadius = FOCUS_DISTANCE
        userZoomed = false
    }

    /** Orbit [p] (followed every frame, so it stays centred on screen). */
    fun lookAt(p: Vec3) {
        lookGoal.copy(p)
    }

    fun frameIdle() {
        goalTheta = null
        goalRadius = null
        userZoomed = false
    }

    /**
     * Eases the radius so content reaching [halfWidth] across and [halfHeight]
     * up / down from the centre fills the screen (unless zoomed by hand).
     */
    fun frameToFit(halfWidth: Float, halfHeight: Float, dt: Float) {
        if (userZoomed || goalRadius != null || viewWidth <= 1f) return
        val fill = 0.46f
        val byWidth = halfWidth * focal / (fill * viewWidth)
        val byHeight = halfHeight * focal / (fill * viewHeight)
        val want = max(byWidth, byHeight).coerceIn(minRadius, MAX_RADIUS)
        radius = Motion.damp(radius, want, 2f, dt)
    }

    fun tick(dt: Float) {
        idleTime += dt
        goalTheta?.let { goal ->
            theta = Motion.damp(theta, goal, 3f, dt)
            if (abs(goal - theta) < 0.002f) goalTheta = null
        }
        goalRadius?.let { goal ->
            radius = Motion.damp(radius, goal, 3f, dt)
            if (abs(goal - radius) < 0.01f) goalRadius = null
        }
        if (autoRotate && idleTime > 0.3f) theta += dt * 0.04f
        // The content grew (or the camera was in close): ease back out.
        if (radius < minRadius) radius = Motion.damp(radius, minRadius, 6f, dt)
        Motion.dampVec(look, lookGoal, 5f, dt)
    }

    /** One-finger drag in dp: across turns, up / down moves in and out. */
    fun dragBy(dxDp: Float, dyDp: Float) {
        idleTime = 0f
        goalTheta = null
        goalRadius = null
        theta += dxDp * 0.005f
        // A mostly vertical swipe is a zoom.
        if (abs(dyDp) > abs(dxDp)) userZoomed = true
        radius = (radius - dyDp * 0.018f).coerceIn(minRadius, MAX_RADIUS)
    }

    fun zoomBy(factor: Float) {
        if (factor <= 0f) return
        idleTime = 0f
        goalRadius = null
        userZoomed = true
        radius = (radius / factor).coerceIn(minRadius, MAX_RADIUS)
    }

    fun position(out: Vec3): Vec3 {
        val t = theta
        return out.set(look.x + radius * cos(t), look.y, look.z + radius * sin(t))
    }

    fun beginFrame(width: Float, height: Float) {
        val t = theta
        camX = look.x + radius * cos(t)
        camY = look.y
        camZ = look.z + radius * sin(t)
        // Looking at the look-at point: forward = toward it, right = forward x up.
        fwdX = -cos(t)
        fwdZ = -sin(t)
        rightX = sin(t)
        rightZ = -cos(t)
        centerX = width / 2f
        centerY = height / 2f
        // The web's 68° field of view, across the screen's short side (the web
        // always runs landscape, so that is its vertical).
        focal = (min(width, height) / 2f) / tan(FOV_RAD / 2f)
        viewWidth = width
        viewHeight = height
        aspect = if (width > 0f) height / width else 1f
    }

    /** Visible world width at [distance] in front of the camera. */
    fun visibleWidthAt(distance: Float): Float = viewWidth * distance / focal

    /** Visible world height at [distance] in front of the camera. */
    fun visibleHeightAt(distance: Float): Float = viewHeight * distance / focal

    /** [p] in camera space: out = [right, up, depth in front] (depth < 0 behind). */
    fun toCamera(p: Vec3, out: FloatArray) {
        val vx = p.x - camX
        val vz = p.z - camZ
        out[0] = vx * rightX + vz * rightZ
        out[1] = p.y - camY
        out[2] = vx * fwdX + vz * fwdZ
    }

    /** Projects [p]: out = [x, y, depth]. False when it is behind the camera. */
    fun project(p: Vec3, out: FloatArray): Boolean {
        val vx = p.x - camX
        val vy = p.y - camY
        val vz = p.z - camZ
        val d = vx * fwdX + vz * fwdZ
        if (d < NEAR) return false
        val x = vx * rightX + vz * rightZ
        out[0] = centerX + x * focal / d
        out[1] = centerY - vy * focal / d
        out[2] = d
        return true
    }

    private companion object {
        const val NEAR = 0.1f
        const val IDLE_RADIUS = 8f
        // The web frames its focus 6.2 in front of the camera.
        const val FOCUS_DISTANCE = 6.2f
        const val MIN_RADIUS = 4f
        const val MAX_RADIUS = 36f
        val FOV_RAD = (68.0 * PI / 180.0).toFloat()
    }
}
