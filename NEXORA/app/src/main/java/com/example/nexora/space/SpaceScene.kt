package com.example.nexora.space

import androidx.compose.ui.graphics.toArgb
import com.example.nexora.model.AppGroup
import com.example.nexora.model.AppNode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// The palette of Code Universe (utils/palette.js).
object SpaceColors {
    const val BG = 0xFF000000.toInt()
    const val FG = 0xFF00FF66.toInt()
    const val DIM = 0xFF00A845.toInt()
    const val HIGHLIGHT = 0xFFD9FFE6.toInt()
}

/**
 * Something in the space. The scene moves it (like the web's KeywordNode);
 * the renderer projects it and writes back where it landed on screen.
 */
abstract class SpaceBody(val key: String) {
    val pos = Vec3()
    val target = Vec3()

    /** World size: em of the title for a group, a fraction of the icon for an app. Eased. */
    var scale = 0.001f
    var opacity = 0f
    var arrivalPop = 0f

    // Width of the body in em: measured title for groups, ICON_EM for apps.
    var widthEm = 0f

    // Non-orbiting bodies fly an arc once per role change (createFlight).
    internal var flight: Flight? = null
    internal var roleKey: String? = null

    // Last projection, written by the renderer.
    var screenX = 0f
    var screenY = 0f
    var depth = 0f
    var sizePx = 0f
    var visible = false
    // How far it was pushed on screen to stay inside the view (traces follow).
    var pushX = 0f
    var pushY = 0f
    var hitLeft = 0f
    var hitTop = 0f
    var hitRight = 0f
    var hitBottom = 0f

    abstract val color: Int
}

/**
 * A group: idle it is just its name floating near the centre; tapped, it
 * becomes the web's focus — flies to the middle, grows its traces and its
 * apps come out on the rings around it.
 */
class SpaceHub(val group: AppGroup, val home: Vec3, val phase: Float, val seed: Float) : SpaceBody("hub:" + group.id) {
    var memberCount = 0
        internal set
    var focused = false
        internal set

    val label get() = group.name
    val badge get() = "+$memberCount"
    override val color get() = if (focused) SpaceColors.HIGHLIGHT else group.color
}

/** An app, drawn as its line icon. */
sealed class SpaceNode(val app: AppNode, val baseScale: Float) : SpaceBody("app:" + app.packageName) {
    override val color = app.tintColor.toArgb()

    init {
        widthEm = SpaceScene.ICON_EM
    }
}

/** App in a group: only shown while its group is focused, orbiting it. */
class MemberNode(app: AppNode, baseScale: Float, val hub: SpaceHub, val index: Int, val count: Int) :
    SpaceNode(app, baseScale) {
    var arrived = false
        internal set
}

/**
 * The centre app: in the middle of the space, wired to every group. While a
 * group is focused it fades away, so the focus shows only its own apps.
 */
class CenterNode(app: AppNode) : SpaceNode(app, 0.55f)

/** App in no group: floats on the shells like the web's idle keywords. */
class FreeNode(app: AppNode, baseScale: Float, val home: Vec3, val phase: Float, val seed: Float) :
    SpaceNode(app, baseScale)

class SpaceScene(
    val hubs: List<SpaceHub>,
    val nodes: List<SpaceNode>,
    val center: CenterNode?,
) {
    val bodies: List<SpaceBody> = hubs + nodes
    val members: Map<SpaceHub, List<MemberNode>> = nodes.filterIsInstance<MemberNode>().groupBy { it.hub }

    /** The group shown as the focus, if any. */
    var focused: SpaceHub? = null
        private set

    /** When the focus landed and its traces started growing (null until then). */
    var linksStart: Float? = null
        private set

    /** When the centre's traces to every group started growing (idle only). */
    private var idleLinksStart: Float? = null

    /** Orbit angle of every ring (web: sceneState.orbitAngle). */
    var orbitAngle = 0f
        private set

    private var ringFit = 1f
    private var ringIconScale = FloatArray(1) { 0.3f }
    private var ringStretch = 1f
    private var tiltBoost = 1f
    private var stretchY = 1f
    private val offset = Vec3()
    private val camPos = Vec3()

    fun focus(hub: SpaceHub, camera: SpaceCamera) {
        if (focused == hub) return
        focused?.focused = false
        members[focused]?.forEach { it.arrived = false }
        focused = hub
        hub.focused = true
        linksStart = null
        idleLinksStart = null
        camera.frameFocus()
    }

    fun unfocus(camera: SpaceCamera) {
        val hub = focused ?: return
        hub.focused = false
        members[hub]?.forEach { it.arrived = false }
        focused = null
        linksStart = null
        idleLinksStart = null
        camera.frameIdle()
    }

    /** When link [index] of the focus starts growing (SatelliteLinks' stagger). */
    fun linkStart(index: Int): Float? = linksStart?.let { it + GROW_TIME * 0.5f + index * CHILD_STAGGER }

    /** When the centre's trace to [hub] starts growing: idle only, one group after another. */
    fun centerLinkStart(hub: SpaceHub): Float? {
        if (focused != null) return null
        return idleLinksStart?.let { it + hubs.indexOf(hub) * CHILD_STAGGER * 2f }
    }

    fun update(time: Float, dt: Float, camera: SpaceCamera) {
        orbitAngle += dt * 0.16f
        camera.position(camPos)
        camera.autoRotate = focused == null
        val focus = focused
        val idle = focus == null
        if (idle && idleLinksStart == null) idleLinksStart = time + 0.3f
        if (idle) frameIdleContent(camera, dt)
        // Focused, the camera orbits the group itself, so its name stays centred.
        camera.lookAt(focus?.pos ?: ORIGIN)

        center?.let { c ->
            c.target.copy(Motion.floatingOffset(offset, time, 1.3f, 0.12f, 0.12f, 0.09f))
            fly(c, "centre", time, dt, steady = true, delayIndex = 0)
            if (idle) {
                settle(c, c.baseScale, 1f, 3.2f, 0.45f, 4f, camera, dt)
            } else {
                // A focused group shows only itself and its apps: the centre fades away.
                c.opacity = Motion.damp(c.opacity, 0f, 9f, dt)
            }
        }

        // On a tall screen the rings tip further toward the viewer to use the height.
        tiltBoost = camera.aspect.coerceIn(1f, 2f)
        // Rings grow / shrink to the screen's width at the focus's depth, and
        // stretch up and down so they fill the height too.
        if (focus != null) {
            val count = members[focus]?.size ?: 0
            val outer = if (count == 0) 1f else ringSpec(count).last().r
            val distance = camPos.distanceTo(FOCUS_CENTER)
            val halfW = camera.visibleWidthAt(distance) / 2f
            val halfH = camera.visibleHeightAt(distance) / 2f
            ringFit = ((halfW * 0.85f - 0.6f) / outer).coerceIn(0.45f, 2.2f)
            // Never zoom in past the near side of the rings.
            camera.contentRadius = outer * ringFit + CAMERA_MARGIN
            val reach = outer * ringFit * sin(INNER_TILT * tiltBoost)
            ringStretch = if (reach > 0f) (halfH * 0.72f / reach).coerceIn(1f, 3.5f) else 1f

            // Icon size from the room each app gets on its ring: few apps, big
            // icons; a crowded ring, smaller ones — the ring stays filled.
            val rings = ringSpec(max(1, count))
            val maxIcon = min(1.1f, halfW * 0.3f)
            ringIconScale = FloatArray(rings.size) { k ->
                val a = rings[k].r * ringFit
                val tilt = (if (k % 2 == 0) INNER_TILT else OUTER_TILT) * tiltBoost
                val b = a * abs(sin(tilt)) * ringStretch
                val perApp = ellipsePerimeter(a, b) / rings[k].count
                (perApp * 0.4f).coerceIn(0.4f, maxIcon) / ICON_EM
            }
        }
        // Idle, the group names spread up and down to the screen's shape.
        stretchY = (camera.aspect * 0.85f).coerceIn(0.7f, 2f)

        for (hub in hubs) {
            if (hub == focus) {
                hub.target.copy(FOCUS_CENTER)
                fly(hub, "focus", time, dt, steady = true, delayIndex = 0)
                settle(hub, FOCUS_TITLE, 1f, 3.2f, 0.9f, 4f, camera, dt)
                // The traces grow once the focus has landed.
                if (linksStart == null && hub.flight == null && hub.scale > 0.3f) linksStart = time
            } else {
                drift(hub, time, if (idle) 0.35f else 0.12f, if (idle) 0.6f else 0.5f, idle)
                fly(hub, if (idle) "idle" else "away", time, dt, steady = false, delayIndex = abs(hub.seed.toInt()))
                if (idle) {
                    settle(hub, GROUP_TITLE, 0.9f, 6f, 0.6f, 4f, camera, dt)
                } else {
                    // Inside a group the other groups' names are hidden, not just dimmed.
                    hub.opacity = Motion.damp(hub.opacity, 0f, 9f, dt)
                }
            }
        }

        for (node in nodes) {
            when (node) {
                is MemberNode -> updateMember(node, time, dt, camera)
                is CenterNode -> Unit
                is FreeNode -> {
                    drift(node, time, if (idle) 0.4f else 0.12f, if (idle) 1.55f else 0.8f, idle)
                    fly(node, if (idle) "idle" else "away", time, dt, steady = false, delayIndex = abs(node.seed.toInt()))
                    val scale = if (idle) node.baseScale else node.baseScale * 0.9f
                    settle(node, scale, if (idle) 0.6f else 0.14f, 6f, 0.45f, 4f, camera, dt)
                }
            }
        }
    }

    /**
     * How far the idle content (centre app + group names) reaches, so the
     * camera can sit just far enough for it to fill the screen. The camera
     * orbits, so the horizontal reach is the radius round the vertical axis.
     */
    private fun frameIdleContent(camera: SpaceCamera, dt: Float) {
        var across = 0f
        var up = 0f
        for (hub in hubs) {
            val labelHalf = hub.widthEm * hub.scale / 2f
            across = max(across, sqrt(hub.target.x * hub.target.x + hub.target.z * hub.target.z) + labelHalf)
            up = max(up, abs(hub.target.y) + hub.scale * 1.2f)
        }
        center?.let { c ->
            val half = ICON_EM * c.scale / 2f
            across = max(across, half)
            up = max(up, half)
        }
        // Never zoom in among the groups.
        camera.contentRadius = across + CAMERA_MARGIN
        if (across == 0f && up == 0f) return
        camera.frameToFit(across, up, dt)
    }

    private fun updateMember(node: MemberNode, time: Float, dt: Float, camera: SpaceCamera) {
        val hub = node.hub
        if (hub != focused) {
            // Folded back into its group.
            node.target.copy(hub.pos)
            Motion.dampVec(node.pos, node.target, 4f, dt)
            node.scale = Motion.damp(node.scale, 0.001f, 6f, dt)
            node.opacity = Motion.damp(node.opacity, 0f, 9f, dt)
            return
        }

        satellitePosition(node.target, node.index, node.count, orbitAngle, hub.pos, ringFit, tiltBoost, ringStretch)
        Motion.dampVec(node.pos, node.target, 4f, dt)

        // Sized by the room on its ring; the perspective is undone so every icon
        // shows the same size (near ones would cover the rest); the far half fades.
        var scale = ringIconScale[ringIndex(node.index, node.count).coerceAtMost(ringIconScale.size - 1)]
        val ref = camPos.distanceTo(hub.pos)
        val dist = node.pos.distanceTo(camPos)
        scale *= dist / ref
        val back = ((dist - ref) / 2.5f).coerceIn(0f, 1f)
        scale *= 1f - 0.2f * back
        var opacity = 0.95f - 0.6f * back

        // Hidden until its trace reaches it, then it pops in.
        val start = linkStart(node.index)
        if (start == null || time < start + GROW_TIME) {
            opacity = 0f
        } else if (!node.arrived) {
            node.arrived = true
            node.arrivalPop = 1f
        }
        settle(node, scale, opacity, 3.2f, 0.45f, 9f, camera, dt)
    }

    /** Idle drift around the home spot; in focus mode it recedes into a rear layer. */
    private fun drift(body: SpaceBody, time: Float, amp: Float, wander: Float, idle: Boolean) {
        val home: Vec3
        val phase: Float
        val seed: Float
        when (body) {
            is SpaceHub -> {
                home = body.home; phase = body.phase; seed = body.seed
            }
            is FreeNode -> {
                home = body.home; phase = body.phase; seed = body.seed
            }
            else -> return
        }
        if (idle) {
            body.target.copy(home)
            if (body is SpaceHub) body.target.y *= stretchY
        } else {
            body.target.set(home.x * 1.08f, home.y * 1.08f, -abs(home.z) * 0.35f - 1f)
        }
        body.target.add(Motion.floatingOffset(offset, time, phase, amp, amp, amp * 0.75f))
        body.target.add(Motion.wanderingOffset(offset, time, phase + seed * 1.37f, wander, wander * 0.72f, wander * 0.9f))
    }

    /** Flies an arc once per role change, then follows its target closely. */
    private fun fly(body: SpaceBody, role: String, time: Float, dt: Float, steady: Boolean, delayIndex: Int) {
        if (role != body.roleKey) {
            val first = body.roleKey == null
            body.roleKey = role
            if (!first) {
                val distance = body.pos.distanceTo(body.target)
                body.flight = Flight(
                    from = body.pos,
                    to = body.target,
                    delay = if (steady) 0f else (delayIndex % 12) * 0.02f,
                    duration = min(1.6f, 0.25f + distance * 0.08f),
                    arcHeight = 0.6f + min(2.4f, distance * 0.18f),
                    startTime = time,
                )
            }
        }
        val f = body.flight
        if (f != null) {
            f.end.copy(body.target)
            if (f.pointAt(body.pos, time)) body.flight = null
        } else {
            Motion.dampVec(body.pos, body.target, 6f, dt)
        }
    }

    /** Shared tail of KeywordNode's frame: close-range shrink, width cap, pop, easing. */
    private fun settle(
        body: SpaceBody,
        wantScale: Float,
        wantOpacity: Float,
        closeRange: Float,
        widthShare: Float,
        opacityLambda: Float,
        camera: SpaceCamera,
        dt: Float,
    ) {
        var scale = wantScale
        val distance = body.pos.distanceTo(camPos)
        // Never let it loom over the camera.
        scale *= (distance / closeRange).coerceIn(0.12f, 1f)
        // Nor be wider than a share of the visible width at its depth.
        if (body.widthEm > 0f) {
            scale = min(scale, camera.visibleWidthAt(distance) * widthShare / body.widthEm)
        }
        if (body.arrivalPop > 0f) {
            scale *= 1f + 0.25f * body.arrivalPop
            body.arrivalPop = max(0f, body.arrivalPop - dt * 2.2f)
        }
        body.scale = Motion.damp(body.scale, scale, 5f, dt)
        body.opacity = if (wantOpacity == 0f) 0f else Motion.damp(body.opacity, wantOpacity, opacityLambda, dt)
    }

    /** Front-most body under the screen point, from the last rendered frame. */
    fun hitTest(x: Float, y: Float, minRadius: Float): SpaceBody? {
        var best: SpaceBody? = null
        for (body in bodies) {
            if (!body.visible || body.opacity < 0.3f) continue
            val cx = (body.hitLeft + body.hitRight) / 2f
            val cy = (body.hitTop + body.hitBottom) / 2f
            val halfW = max((body.hitRight - body.hitLeft) / 2f, minRadius)
            val halfH = max((body.hitBottom - body.hitTop) / 2f, minRadius)
            if (abs(x - cx) <= halfW && abs(y - cy) <= halfH && (best == null || body.depth < best.depth)) {
                best = body
            }
        }
        return best
    }

    companion object {
        /** An app icon is this many em wide (an em = the web's label size). */
        const val ICON_EM = 2.6f
        // How far outside the content the camera must stay.
        private const val CAMERA_MARGIN = 1.6f
        // Group name size (em, world units): idle, and as the focus.
        private const val GROUP_TITLE = 0.55f
        private const val FOCUS_TITLE = 0.6f
        const val GROW_TIME = 0.45f
        private const val CHILD_STAGGER = 0.06f
        private const val INNER_TILT = 0.42f
        private const val OUTER_TILT = -0.3f
        private const val SATELLITE_SPACING = 2.3f
        private const val RING_CAPACITY = 14
        private val GOLDEN_ANGLE = (PI * (3.0 - sqrt(5.0))).toFloat()

        // Where the web puts its focus: 6.2 in front of the framing camera.
        val FOCUS_CENTER = Vec3(0f, 0f, 4.8f)
        private val ORIGIN = Vec3()


        // Shells of the idle keywords (utils/positions.js).
        private val SHELL_R = floatArrayOf(10f, 16f, 23f)
        private val SHELL_Y_JITTER = floatArrayOf(0.45f, 0.75f, 1.1f)

        /**
         * Builds the space. Bodies already in [previous] keep their position,
         * size and motion, and the focus carries over while its group exists.
         */
        fun build(apps: List<AppNode>, groups: List<AppGroup>, centerPackage: String?, previous: SpaceScene?): SpaceScene {
            val byPackage = apps.associateBy { it.packageName }
            // The centre app is claimed first: never in a group, never floating.
            val centerApp = centerPackage?.let { byPackage[it] }
            val claimed = HashSet<String>().apply { centerApp?.let { add(it.packageName) } }
            val memberLists = groups.map { group ->
                group.packages.mapNotNull { pkg -> byPackage[pkg]?.takeIf { claimed.add(pkg) } }
            }
            val old = previous?.bodies?.associateBy { it.key } ?: emptyMap()

            // Group names spread over a sphere round the centre app (golden-angle
            // spiral, a little flattened), so its traces fan out every way.
            val hubs = groups.mapIndexed { i, group ->
                val r = 2.8f + groups.size * 0.12f
                val y = if (groups.size == 1) 0.3f else 1f - ((i + 0.5f) / groups.size) * 2f
                val ring = sqrt(max(0f, 1f - y * y))
                val theta = i * GOLDEN_ANGLE
                val home = Vec3(cos(theta) * ring * r, y * r, sin(theta) * ring * r)
                val seed = (group.id.hashCode() and 0xFFFF).toFloat()
                SpaceHub(group, home, (seed * 2.399f) % (2f * PI.toFloat()), seed % 97f)
            }

            val nodes = mutableListOf<SpaceNode>()
            hubs.forEachIndexed { i, hub ->
                carry(old[hub.key], hub, spawnAt = hub.home)
                val members = memberLists[i]
                hub.memberCount = members.size
                members.forEachIndexed { j, app ->
                    val node = MemberNode(app, baseScaleOf(app), hub, j, members.size)
                    // Comes out of its group (or flies over from where it floated).
                    carry(old["app:" + app.packageName], node, spawnAt = hub.home)
                    (old["app:" + app.packageName] as? MemberNode)?.let { node.arrived = it.arrived }
                    nodes += node
                }
            }

            val free = apps.filter { it.packageName !in claimed }.sortedBy { it.packageName }
            val homes = shellPositions(free.size, Random(1337))
            free.forEachIndexed { k, app ->
                val seed = (app.packageName.hashCode() and 0xFFFF).toFloat()
                val node = FreeNode(app, baseScaleOf(app), homes[k], (seed * 2.399f) % (2f * PI.toFloat()), seed % 97f)
                carry(old["app:" + app.packageName], node, spawnAt = homes[k])
                nodes += node
            }

            val center = centerApp?.let { app ->
                CenterNode(app).also { carry(old["app:" + app.packageName], it, spawnAt = Vec3()) }
            }
            if (center != null) nodes += center
            val scene = SpaceScene(hubs, nodes, center)
            if (previous != null) {
                scene.orbitAngle = previous.orbitAngle
                val keep = previous.focused?.let { f -> hubs.firstOrNull { it.group.id == f.group.id } }
                if (keep != null) {
                    scene.focused = keep
                    keep.focused = true
                    scene.linksStart = previous.linksStart
                } else {
                    // Already-grown centre traces stay grown.
                    scene.idleLinksStart = previous.idleLinksStart
                }
            }
            return scene
        }

        private fun carry(from: SpaceBody?, to: SpaceBody, spawnAt: Vec3) {
            if (from == null) {
                to.pos.copy(spawnAt)
                return
            }
            to.pos.copy(from.pos)
            to.scale = from.scale
            to.opacity = from.opacity
            to.roleKey = from.roleKey
            to.flight = from.flight
            if (from is SpaceHub && to is SpaceHub && from.label == to.label) to.widthEm = from.widthEm
        }

        // Web: (0.16 + importance / 10 * 0.23); apps get a stable pseudo-importance.
        private fun baseScaleOf(app: AppNode): Float {
            val importance = 3 + (app.packageName.hashCode() and 0x7FFFFFFF) % 5
            return 0.16f + importance / 10f * 0.23f
        }

        private class Ring(val start: Int, val count: Int, val r: Float)

        private fun roomFor(n: Int) = (n * SATELLITE_SPACING) / (PI.toFloat() * 2)

        private fun ringIndex(index: Int, count: Int): Int {
            val rings = ringSpec(count)
            var k = rings.size - 1
            while (k > 0 && index < rings[k].start) k--
            return k
        }

        // Ramanujan's approximation.
        private fun ellipsePerimeter(a: Float, b: Float): Float =
            PI.toFloat() * (3f * (a + b) - sqrt((3f * a + b) * (a + 3f * b)))

        // One tilted ring, or several crossing rings when there are many (sceneLayout.js).
        private fun ringSpec(count: Int): List<Ring> {
            if (count <= 10) return listOf(Ring(0, count, max(1.6f + count * 0.13f, roomFor(count))))
            val n = max(2, ceil(count / RING_CAPACITY.toFloat()).toInt())
            val rings = mutableListOf<Ring>()
            var start = 0
            var r = 0f
            for (k in 0 until n) {
                val c = count / n + if (k < count % n) 1 else 0
                r = if (k == 0) max(2.0f, roomFor(c)) else maxOf(if (k == 1) 3.1f else 0f, roomFor(c), r + 1.1f)
                rings += Ring(start, c, r)
                start += c
            }
            return rings
        }

        private fun satellitePosition(
            out: Vec3,
            index: Int,
            count: Int,
            angle: Float,
            center: Vec3,
            fit: Float,
            tiltBoost: Float,
            yStretch: Float,
        ) {
            val rings = ringSpec(count)
            var k = rings.size - 1
            while (k > 0 && index < rings[k].start) k--
            val ring = rings[k]
            val i = index - ring.start
            val r = ring.r * fit
            val even = k % 2 == 0
            val tilt = (if (even) INNER_TILT else OUTER_TILT) * tiltBoost
            val stagger = if (k >= 2) (PI.toFloat() / ring.count) * (k / 2) else 0f
            val a = (if (even) angle else -angle) + (i / ring.count.toFloat()) * PI.toFloat() * 2 + stagger
            val x = cos(a) * r
            val z = sin(a) * r
            out.set(center.x + x, center.y - z * sin(tilt) * yStretch, center.z + z * cos(tilt))
        }

        /** Round-robin over three shells, golden-angle spiral within each (positions.js). */
        private fun shellPositions(count: Int, rng: Random): List<Vec3> {
            val perShell = IntArray(3)
            return List(count) { index ->
                val shellIdx = index % 3
                val localIndex = perShell[shellIdx]++
                val shellTotal = ceil((count - shellIdx) / 3f)
                val yUnit = 1f - ((localIndex + 0.5f) / shellTotal) * 2f
                val horizontal = sqrt(max(0f, 1f - yUnit * yUnit))
                val theta = localIndex * GOLDEN_ANGLE + shellIdx * 1.71f + rng.nextFloat() * 0.18f
                val radius = SHELL_R[shellIdx] + (rng.nextFloat() - 0.5f) * 1.4f
                Vec3(
                    cos(theta) * horizontal * radius,
                    yUnit * radius + (rng.nextFloat() - 0.5f) * SHELL_Y_JITTER[shellIdx],
                    sin(theta) * horizontal * radius,
                )
            }
        }
    }
}
