package com.example.nexora.service

import android.graphics.Canvas
import android.graphics.Typeface
import android.os.Handler
import android.os.Bundle
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import androidx.core.content.res.ResourcesCompat
import com.example.nexora.R
import com.example.nexora.data.AppRepository
import com.example.nexora.data.GroupRepository
import com.example.nexora.data.SettingsRepository
import com.example.nexora.model.AppGroup
import com.example.nexora.model.AppNode
import com.example.nexora.space.SpaceCamera
import com.example.nexora.space.SpaceHub
import com.example.nexora.space.SpaceNode
import com.example.nexora.space.SpaceRenderer
import com.example.nexora.space.SpaceScene
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class KnowledgeGraphWallpaperService : WallpaperService() {

    private companion object {
        // ~30 fps: the space drifts slowly, so this looks the same as 60 for
        // about half the battery. Nothing is drawn while the wallpaper is hidden.
        const val FRAME_MS = 33L
        const val LONG_PRESS_MS = 500L
        const val ASIDE_LONG_PRESS = "long-press"
    }

    override fun onCreateEngine(): Engine {
        return SpaceWallpaperEngine()
    }

    /** Same 3D space as the launcher screen, turning on its own; tap an app to open it. */
    inner class SpaceWallpaperEngine : Engine() {

        private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        private val handler = Handler(Looper.getMainLooper())
        private val appRepository = AppRepository(applicationContext)
        private val groupRepository = GroupRepository(applicationContext)
        private val settings = SettingsRepository(applicationContext)
        private val camera = SpaceCamera()
        private val renderer = SpaceRenderer(
            resources.displayMetrics.density,
            ResourcesCompat.getFont(applicationContext, R.font.square_vn) ?: Typeface.DEFAULT,
        ).apply {
            // Clear of the status bar on top. Apps may float down over the dock:
            // the cover block draws them on top of itself.
            val d = resources.displayMetrics.density
            insetTop = 40f * d
            insetBottom = 24f * d
        }

        // The cover block shows (and takes touches for) the space under it.
        private val coverContent = object : ScreenCover.Content {
            override fun drawCover(canvas: Canvas) {
                val current = scene
                if (current == null) {
                    canvas.drawColor(android.graphics.Color.BLACK)
                } else {
                    renderer.draw(canvas, current, camera, surfaceWidth, surfaceHeight, time)
                }
            }

            override fun touchCover(event: MotionEvent) = handleTouch(event, fromCover = true)
        }

        // Touched only on the main thread.
        private var visible = false
        private var apps = listOf<AppNode>()
        private var groups = listOf<AppGroup>()
        private var centerPackage: String? = null
        private var scene: SpaceScene? = null
        private var surfaceWidth = 0f
        private var surfaceHeight = 0f
        private var time = 0f
        private var lastTime = System.currentTimeMillis()

        private var touchDownTime = 0L
        private var touchDownX = 0f
        private var touchDownY = 0f
        private var lastX = 0f
        private var lastY = 0f
        private var lastPinch = -1f
        private var multiTouch = false

        // A long press on the home screen opens MIUI's edit mode or an icon's
        // menu — neither tells the wallpaper, so the cover steps aside on the
        // press itself and comes back on the next tap / offsets update.
        private val longPress = Runnable {
            if (!isPreview) ScreenCover.setAside(ASIDE_LONG_PRESS, true)
        }
        private val drawRunnable = object : Runnable {
            override fun run() {
                val now = System.currentTimeMillis()
                val delta = ((now - lastTime) / 1000f).coerceAtMost(0.1f)
                lastTime = now
                time += delta
                camera.tick(delta)
                scene?.update(time, delta, camera)

                drawFrame()
                if (!isPreview) ScreenCover.invalidate()
                if (visible) {
                    handler.postDelayed(this, FRAME_MS)
                }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
            if (!isPreview) ScreenCover.content = coverContent
            groups = groupRepository.load()
            centerPackage = settings.centerPackage
            serviceScope.launch {
                val loaded = appRepository.getInstalledApps()
                handler.post {
                    apps = loaded
                    rebuildScene()
                }
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            surfaceWidth = width.toFloat()
            surfaceHeight = height.toFloat()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            handler.removeCallbacks(drawRunnable)
            if (visible) {
                // Groups and options may have been changed in the launcher meanwhile.
                renderer.showAppNames = settings.showAppNames
                // Built anew every time the home screen shows: the group names land
                // in new places each time.
                groups = groupRepository.load()
                centerPackage = settings.centerPackage
                rebuildScene()
                lastTime = System.currentTimeMillis()
                handler.post(drawRunnable)
            }
            // The cover block sits over the launcher only while the home screen
            // (this wallpaper) shows; never for the wallpaper picker's preview.
            if (visible && !isPreview) {
                ScreenCover.show(applicationContext, settings.cover)
            } else if (!isPreview) {
                ScreenCover.hide(applicationContext)
            }
        }

        // What the launcher tells the wallpaper, used to get the cover out of
        // its way. Measured on MIUI (2026-10-02): resting home zoom = 1.0
        // (AOSP: 0), recents = 0.7, returning from an app = 0.3 for the
        // animation; recents and folders also come as commands. The shade
        // sends nothing, but the cover sits under it anyway.
        override fun onZoomChanged(zoom: Float) {
            super.onZoomChanged(zoom)
            if (!isPreview) ScreenCover.setAside("zoom", zoom > 0.05f && zoom < 0.95f)
        }

        override fun onCommand(action: String?, x: Int, y: Int, z: Int, extras: Bundle?, resultRequested: Boolean): Bundle? {
            if (!isPreview) {
                when (action) {
                    "action_open_recent" -> ScreenCover.setAside("recents", true)
                    "action_close_recent" -> ScreenCover.setAside("recents", false)
                    "action_open_folder" -> ScreenCover.setAside("folder", true)
                    "action_close_folder" -> ScreenCover.setAside("folder", false)
                }
            }
            return super.onCommand(action, x, y, z, extras, resultRequested)
        }

        // Leaving MIUI's edit mode sends an offsets update (measured 2026-10-02).
        override fun onOffsetsChanged(xOffset: Float, yOffset: Float, xStep: Float, yStep: Float, xPx: Int, yPx: Int) {
            if (!isPreview) ScreenCover.setAside(ASIDE_LONG_PRESS, false)
        }

        override fun onDestroy() {
            super.onDestroy()
            if (!isPreview) ScreenCover.hide(applicationContext)
            if (ScreenCover.content === coverContent) ScreenCover.content = null
            handler.removeCallbacks(longPress)
            handler.removeCallbacks(drawRunnable)
            serviceScope.cancel()
        }

        override fun onTouchEvent(event: MotionEvent) = handleTouch(event, fromCover = false)

        /**
         * Same gestures as the launcher screen: drag turns / moves in and out,
         * pinch zooms, tap opens. [fromCover]: it came through the cover block,
         * where a tap on nothing steps the cover aside instead.
         */
        private fun handleTouch(event: MotionEvent, fromCover: Boolean) {
            val density = resources.displayMetrics.density
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (!fromCover) handler.postDelayed(longPress, LONG_PRESS_MS)
                    touchDownTime = System.currentTimeMillis()
                    touchDownX = event.x
                    touchDownY = event.y
                    lastX = event.x
                    lastY = event.y
                    lastPinch = -1f
                    multiTouch = false
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    handler.removeCallbacks(longPress)
                    multiTouch = true
                    if (event.pointerCount == 2) lastPinch = pinchSpacing(event)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (event.pointerCount >= 2) {
                        val spacing = pinchSpacing(event)
                        if (lastPinch > 0f && spacing > 0f) camera.zoomBy(spacing / lastPinch)
                        lastPinch = spacing
                    } else {
                        val mx = event.x - touchDownX
                        val my = event.y - touchDownY
                        if (sqrt((mx * mx) + (my * my)) > 10f * density) handler.removeCallbacks(longPress)
                        camera.dragBy((event.x - lastX) / density, (event.y - lastY) / density)
                        lastX = event.x
                        lastY = event.y
                    }
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    // Carry on dragging from the finger that is still down.
                    lastPinch = -1f
                    val remaining = if (event.actionIndex == 0) 1 else 0
                    lastX = event.getX(remaining)
                    lastY = event.getY(remaining)
                }
                MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(longPress)
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPress)
                    val duration = System.currentTimeMillis() - touchDownTime
                    val dx = event.x - touchDownX
                    val dy = event.y - touchDownY
                    if (!multiTouch && (duration < 300) && (sqrt((dx * dx) + (dy * dy)) < 10f * density)) {
                        if (!isPreview && !fromCover) {
                            // The tap that closes edit mode / an icon menu.
                            ScreenCover.setAside(ASIDE_LONG_PRESS, false)
                            ScreenCover.onHomeTap()
                        }
                        val current = scene ?: return
                        when (val hit = current.hitTest(event.x, event.y, 20f * density)) {
                            is SpaceNode -> {
                                // Out of the way before the app starts opening, not after.
                                if (!isPreview) ScreenCover.hideForLaunch(applicationContext)
                                appRepository.launchApp(hit.app.packageName)
                            }
                            is SpaceHub -> current.focus(hit, camera)
                            else -> if (fromCover) ScreenCover.peekNow() else current.unfocus(camera)
                        }
                    }
                }
            }
        }

        private fun pinchSpacing(event: MotionEvent): Float {
            val dx = event.getX(0) - event.getX(1)
            val dy = event.getY(0) - event.getY(1)
            return sqrt((dx * dx) + (dy * dy))
        }

        private fun rebuildScene() {
            val hadFocus = scene?.focused != null
            val built = SpaceScene.build(apps, groups, centerPackage, scene)
            if (hadFocus && built.focused == null) camera.frameIdle()
            scene = built
        }

        private fun drawFrame() {
            val holder = surfaceHolder ?: return
            val current = scene
            var canvas: Canvas? = null
            try {
                canvas = try {
                    holder.lockHardwareCanvas()
                } catch (_: Exception) {
                    holder.lockCanvas()
                }
                if (canvas != null) {
                    if (current == null) {
                        canvas.drawColor(android.graphics.Color.BLACK)
                    } else {
                        renderer.draw(canvas, current, camera, surfaceWidth, surfaceHeight, time)
                    }
                }
            } finally {
                canvas?.let {
                    try {
                        holder.unlockCanvasAndPost(it)
                    } catch (_: Exception) { }
                }
            }
        }
    }
}
