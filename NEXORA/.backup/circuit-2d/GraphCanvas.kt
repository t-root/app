package com.example.nexora.ui

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun GraphCanvas(
    viewModel: MainViewModel,
    onSetDefaultLauncher: () -> Unit,
    onApplyLiveWallpaper: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLoading by viewModel.isLoading.collectAsState()
    val isDefaultLauncher by viewModel.isDefaultLauncher.collectAsState()
    val slots by viewModel.slots.collectAsState()
    val traces by viewModel.traces.collectAsState()
    val time by viewModel.time.collectAsState()

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF06080D))
            .onSizeChanged { size ->
                if ((size.width > 0) && (size.height > 0)) {
                    viewModel.loadApps(size.width.toFloat(), size.height.toFloat())
                }
            }
    ) {
        // Continuous animation loop for flowing circuit energy pulses
        LaunchedEffect(slots) {
            if (slots.isNotEmpty()) {
                var lastTime = System.currentTimeMillis()
                while (true) {
                    val now = System.currentTimeMillis()
                    val delta = (now - lastTime) / 1000f
                    lastTime = now
                    viewModel.tickAnimation(delta)
                    delay(16.milliseconds)
                }
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Color(0xFF00E5FF))
            }
        } else {
            val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
                scale = (scale * zoomChange).coerceIn(0.7f, 2.5f)
                offset += panChange
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .transformable(state = transformableState)
                    .pointerInput(slots) {
                        detectTapGestures { tapOffset ->
                            val adjustedTap = (tapOffset - offset) / scale
                            val clickedSlot = slots.firstOrNull { slot ->
                                slot.appNode != null && (sqrt(
                                    (adjustedTap.x - slot.center.x) * (adjustedTap.x - slot.center.x) +
                                            (adjustedTap.y - slot.center.y) * (adjustedTap.y - slot.center.y)
                                ) <= 70f)
                            }
                            clickedSlot?.appNode?.let {
                                viewModel.launchApp(it.packageName)
                            }
                        }
                    }
                    .pointerInput(slots) {
                        var activePackage: String? = null
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                val adjustedStart = (startOffset - offset) / scale
                                val target = slots.firstOrNull { slot ->
                                    slot.appNode != null && (sqrt(
                                        (adjustedStart.x - slot.center.x) * (adjustedStart.x - slot.center.x) +
                                                (adjustedStart.y - slot.center.y) * (adjustedStart.y - slot.center.y)
                                    ) <= 80f)
                                }
                                target?.appNode?.let {
                                    activePackage = it.packageName
                                    viewModel.onNodeDragStart(it.packageName)
                                }
                            },
                            onDrag = { change, dragAmount ->
                                activePackage?.let { pkg ->
                                    change.consume()
                                    viewModel.onNodeDrag(pkg, dragAmount / scale)
                                }
                            },
                            onDragEnd = {
                                activePackage = null
                                viewModel.onNodeDragEnd()
                            },
                            onDragCancel = {
                                activePackage = null
                                viewModel.onNodeDragEnd()
                            }
                        )
                    }
            ) {
                val canvasContext = drawContext.canvas.nativeCanvas

                // 1. Draw PCB Circuit Traces (Orthogonal Lines)
                traces.forEach { trace ->
                    val path = Path().apply {
                        if (trace.pathPoints.isNotEmpty()) {
                            val startP = (trace.pathPoints[0] * scale) + offset
                            moveTo(startP.x, startP.y)
                            for (i in 1 until trace.pathPoints.size) {
                                val p = (trace.pathPoints[i] * scale) + offset
                                lineTo(p.x, p.y)
                            }
                        }
                    }

                    // Base Trace Line
                    drawPath(
                        path = path,
                        color = trace.color.copy(alpha = 0.45f),
                        style = Stroke(width = 3.5f * scale, cap = StrokeCap.Round)
                    )

                    // Outer Glow Line
                    drawPath(
                        path = path,
                        color = trace.color.copy(alpha = 0.15f),
                        style = Stroke(width = 9f * scale, cap = StrokeCap.Round)
                    )

                    // Animated Flowing Energy Pulse
                    val pathMeasure = PathMeasure()
                    pathMeasure.setPath(path, false)
                    val pathLength = pathMeasure.length
                    if (pathLength > 0f) {
                        val distance = ((time * trace.speed * scale * 25f) % pathLength)
                        val pulsePos = pathMeasure.getPosition(distance)

                        // Glowing energy dot
                        drawCircle(
                            color = Color.White,
                            radius = 4f * scale,
                            center = pulsePos
                        )
                        drawCircle(
                            color = trace.color,
                            radius = 9f * scale,
                            center = pulsePos,
                            alpha = 0.85f
                        )
                    }
                }

                // 2. Draw IC Microchips & App Icons
                val chipSize = 100f * scale
                val halfChip = chipSize / 2f
                val pinCount = 5

                slots.forEach { slot ->
                    val pos = (slot.center * scale) + offset

                    if (slot.appNode != null) {
                        val node = slot.appNode

                        // IC Pin Teeth (Golden metallic pins around border)
                        val pinLen = 10f * scale
                        val pinThickness = 4f * scale
                        val pinColor = Color(0xFFC8A232)

                        for (i in 0 until pinCount) {
                            val step = chipSize / (pinCount + 1)
                            val offsetPos = -halfChip + step * (i + 1)

                            // Top pins
                            drawLine(pinColor, Offset(pos.x + offsetPos, pos.y - halfChip), Offset(pos.x + offsetPos, pos.y - halfChip - pinLen), pinThickness)
                            // Bottom pins
                            drawLine(pinColor, Offset(pos.x + offsetPos, pos.y + halfChip), Offset(pos.x + offsetPos, pos.y + halfChip + pinLen), pinThickness)
                            // Left pins
                            drawLine(pinColor, Offset(pos.x - halfChip, pos.y + offsetPos), Offset(pos.x - halfChip - pinLen, pos.y + offsetPos), pinThickness)
                            // Right pins
                            drawLine(pinColor, Offset(pos.x + halfChip, pos.y + offsetPos), Offset(pos.x + halfChip + pinLen, pos.y + offsetPos), pinThickness)
                        }

                        // Outer Chip Glow Ring
                        drawRoundRect(
                            color = node.tintColor.copy(alpha = 0.3f),
                            topLeft = Offset(pos.x - halfChip - 3f, pos.y - halfChip - 3f),
                            size = Size(chipSize + 6f, chipSize + 6f),
                            cornerRadius = CornerRadius(16f * scale, 16f * scale)
                        )

                        // Dark Microchip Body
                        drawRoundRect(
                            color = Color(0xFF10141D),
                            topLeft = Offset(pos.x - halfChip, pos.y - halfChip),
                            size = Size(chipSize, chipSize),
                            cornerRadius = CornerRadius(14f * scale, 14f * scale)
                        )

                        // Chip Border Frame
                        drawRoundRect(
                            color = node.tintColor,
                            topLeft = Offset(pos.x - halfChip, pos.y - halfChip),
                            size = Size(chipSize, chipSize),
                            cornerRadius = CornerRadius(14f * scale, 14f * scale),
                            style = Stroke(width = 2.5f * scale)
                        )

                        // App Icon Bitmap
                        val iconDisplaySize = (chipSize * 0.68f).toInt().coerceAtLeast(1)
                        val iconLeft = pos.x - (iconDisplaySize / 2f)
                        val iconTop = pos.y - (iconDisplaySize / 2f)

                        val bitmap = node.iconBitmap.asAndroidBitmap()
                        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, iconDisplaySize, iconDisplaySize, true)

                        canvasContext.drawBitmap(scaledBitmap, iconLeft, iconTop, null)

                        // App Label underneath
                        val paint = Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = 24f * scale
                            textAlign = Paint.Align.CENTER
                            isAntiAlias = true
                            setShadowLayer(4f * scale, 0f, 2f * scale, android.graphics.Color.BLACK)
                        }

                        canvasContext.drawText(
                            node.appName,
                            pos.x,
                            pos.y + halfChip + 26f * scale,
                            paint
                        )
                    } else {
                        // Empty slot socket placeholder (Dim empty chip socket)
                        drawRoundRect(
                            color = Color(0xFF121620),
                            topLeft = Offset(pos.x - halfChip, pos.y - halfChip),
                            size = Size(chipSize, chipSize),
                            cornerRadius = CornerRadius(12f * scale, 12f * scale),
                            style = Stroke(width = 1.5f * scale)
                        )
                    }
                }
            }

            // Top Control Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .zIndex(10f),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                if (!isDefaultLauncher) {
                    Button(
                        onClick = onSetDefaultLauncher,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00E5FF),
                            contentColor = Color.Black,
                        ),
                    ) {
                        Text(
                            text = "⚙️ Đặt Màn hình chính",
                            fontSize = 12.sp,
                        )
                    }
                }

                Button(
                    onClick = onApplyLiveWallpaper,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF7C4DFF),
                        contentColor = Color.White,
                    ),
                ) {
                    Text(
                        text = "🖼️ Đặt Hình nền / Theme hệ thống",
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}
