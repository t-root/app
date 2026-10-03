package com.example.nexora.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.graphics.toColorInt
import com.example.nexora.data.AppRepository
import com.example.nexora.graph.CircuitEngine
import com.example.nexora.graph.CircuitTrace
import com.example.nexora.graph.GridSlot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class KnowledgeGraphWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return KnowledgeGraphEngine()
    }

    inner class KnowledgeGraphEngine : Engine() {

        private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        private val handler = Handler(Looper.getMainLooper())
        private val repository = AppRepository(applicationContext)

        private var visible = false
        private var slots = listOf<GridSlot>()
        private var traces = listOf<CircuitTrace>()
        private var time = 0f
        private var lastTime = System.currentTimeMillis()

        private var touchDownTime = 0L
        private var touchDownX = 0f
        private var touchDownY = 0f
        private var touchPackage: String? = null

        private val drawRunnable = object : Runnable {
            override fun run() {
                val now = System.currentTimeMillis()
                val delta = (now - lastTime) / 1000f
                lastTime = now
                time += delta

                drawFrame()
                if (visible) {
                    handler.postDelayed(this, 16)
                }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            serviceScope.launch {
                val rawNodes = repository.getInstalledApps()
                val (gSlots, gTraces) = CircuitEngine.layoutGrid(rawNodes, width.toFloat(), height.toFloat())
                slots = gSlots
                traces = gTraces
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                lastTime = System.currentTimeMillis()
                handler.post(drawRunnable)
            } else {
                handler.removeCallbacks(drawRunnable)
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            handler.removeCallbacks(drawRunnable)
            serviceScope.cancel()
        }

        override fun onTouchEvent(event: MotionEvent) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownTime = System.currentTimeMillis()
                    touchDownX = event.x
                    touchDownY = event.y

                    val target = slots.firstOrNull { slot ->
                        slot.appNode != null && (sqrt((event.x - slot.center.x) * (event.x - slot.center.x) + (event.y - slot.center.y) * (event.y - slot.center.y)) <= 70f)
                    }
                    touchPackage = target?.appNode?.packageName
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val duration = System.currentTimeMillis() - touchDownTime
                    val dx = event.x - touchDownX
                    val dy = event.y - touchDownY
                    val dist = sqrt((dx * dx) + (dy * dy))

                    if ((duration < 300) && (dist < 20f)) {
                        touchPackage?.let { pkg ->
                            repository.launchApp(pkg)
                        }
                    }

                    touchPackage = null
                }
            }
        }

        private fun drawFrame() {
            val holder = surfaceHolder ?: return
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    renderGraph(canvas)
                }
            } finally {
                canvas?.let {
                    try {
                        holder.unlockCanvasAndPost(it)
                    } catch (_: Exception) { }
                }
            }
        }

        private fun renderGraph(canvas: Canvas) {
            // Dark PCB Background
            canvas.drawColor("#06080D".toColorInt())

            if (slots.isEmpty()) return

            val tracePaint = Paint().apply {
                strokeWidth = 4f
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                pathEffect = CornerPathEffect(12f)
            }

            val glowPaint = Paint().apply {
                strokeWidth = 10f
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                pathEffect = CornerPathEffect(12f)
            }

            val pulseDotPaint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.FILL
            }

            // 1. Draw Orthogonal Circuit Traces & Animated Energy Pulses
            for (trace in traces) {
                if (trace.pathPoints.isEmpty()) continue

                val path = Path().apply {
                    moveTo(trace.pathPoints[0].x, trace.pathPoints[0].y)
                    for (i in 1 until trace.pathPoints.size) {
                        lineTo(trace.pathPoints[i].x, trace.pathPoints[i].y)
                    }
                }

                val argb = trace.color.toArgb()
                tracePaint.color = (argb and 0x00FFFFFF) or 0x77000000
                glowPaint.color = (argb and 0x00FFFFFF) or 0x22000000

                canvas.drawPath(path, glowPaint)
                canvas.drawPath(path, tracePaint)

                // Animated energy pulse dot
                val measure = PathMeasure(path, false)
                val len = measure.length
                if (len > 0f) {
                    val pos = FloatArray(2)
                    val dist = (time * trace.speed * 25f) % len
                    measure.getPosTan(dist, pos, null)

                    pulseDotPaint.color = Color.WHITE
                    canvas.drawCircle(pos[0], pos[1], 4f, pulseDotPaint)

                    pulseDotPaint.color = argb
                    canvas.drawCircle(pos[0], pos[1], 9f, pulseDotPaint)
                }
            }

            // 2. Draw IC Microchips
            val chipSize = 100f
            val halfChip = chipSize / 2f
            val pinCount = 5

            val chipBodyPaint = Paint().apply {
                color = "#10141D".toColorInt()
                style = Paint.Style.FILL
                isAntiAlias = true
            }

            val chipFramePaint = Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = 2.5f
                isAntiAlias = true
            }

            val pinPaint = Paint().apply {
                color = "#C8A232".toColorInt()
                strokeWidth = 4f
                isAntiAlias = true
            }

            val textPaint = Paint().apply {
                color = Color.WHITE
                textSize = 24f
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
                setShadowLayer(4f, 0f, 2f, Color.BLACK)
            }

            for (slot in slots) {
                val pos = slot.center

                if (slot.appNode != null) {
                    val node = slot.appNode

                    // Pin Teeth
                    val pinLen = 10f
                    for (i in 0 until pinCount) {
                        val step = chipSize / (pinCount + 1)
                        val offsetPos = -halfChip + step * (i + 1)

                        canvas.drawLine(pos.x + offsetPos, pos.y - halfChip, pos.x + offsetPos, pos.y - halfChip - pinLen, pinPaint)
                        canvas.drawLine(pos.x + offsetPos, pos.y + halfChip, pos.x + offsetPos, pos.y + halfChip + pinLen, pinPaint)
                        canvas.drawLine(pos.x - halfChip, pos.y + offsetPos, pos.x - halfChip - pinLen, pos.y + offsetPos, pinPaint)
                        canvas.drawLine(pos.x + halfChip, pos.y + offsetPos, pos.x + halfChip + pinLen, pos.y + offsetPos, pinPaint)
                    }

                    // Body
                    canvas.drawRoundRect(pos.x - halfChip, pos.y - halfChip, pos.x + halfChip, pos.y + halfChip, 14f, 14f, chipBodyPaint)

                    // Frame
                    chipFramePaint.color = node.tintColor.toArgb()
                    canvas.drawRoundRect(pos.x - halfChip, pos.y - halfChip, pos.x + halfChip, pos.y + halfChip, 14f, 14f, chipFramePaint)

                    // Icon
                    val iconDisplaySize = (chipSize * 0.68f).toInt().coerceAtLeast(1)
                    val bitmap = node.iconBitmap.asAndroidBitmap()
                    val scaledBitmap = Bitmap.createScaledBitmap(bitmap, iconDisplaySize, iconDisplaySize, true)
                    canvas.drawBitmap(scaledBitmap, pos.x - (iconDisplaySize / 2f), pos.y - (iconDisplaySize / 2f), null)

                    // Text
                    canvas.drawText(node.appName, pos.x, pos.y + halfChip + 26f, textPaint)
                } else {
                    // Empty socket placeholder
                    chipFramePaint.color = "#121620".toColorInt()
                    chipFramePaint.strokeWidth = 1.5f
                    canvas.drawRoundRect(pos.x - halfChip, pos.y - halfChip, pos.x + halfChip, pos.y + halfChip, 12f, 12f, chipFramePaint)
                }
            }
        }

        private fun androidx.compose.ui.graphics.Color.toArgb(): Int {
            return ((alpha * 255).toInt() shl 24) or
                    ((red * 255).toInt() shl 16) or
                    ((green * 255).toInt() shl 8) or
                    (blue * 255).toInt()
        }
    }
}
