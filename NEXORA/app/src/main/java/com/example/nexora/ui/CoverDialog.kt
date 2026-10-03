package com.example.nexora.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import com.example.nexora.model.CoverSettings
import com.example.nexora.service.ScreenCover
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Sets the cover block: where it goes and how big it is (dp) and how dark.
 * Every change is saved at once.
 */
@Composable
fun CoverDialog(
    cover: CoverSettings,
    onChange: (CoverSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val (screenW, screenH) = remember { ScreenCover.screenSizeDp(context) }

    // Re-checked while open: the user may come back from the permission screen.
    var canDraw by remember { mutableStateOf(ScreenCover.canDraw(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            canDraw = ScreenCover.canDraw(context)
            delay(1000)
        }
    }

    fun update(next: CoverSettings) {
        // Keep the block on the screen.
        val width = next.width.coerceIn(0, screenW)
        val height = next.height.coerceIn(0, screenH)
        onChange(
            next.copy(
                width = width,
                height = height,
                x = next.x.coerceIn(0, screenW - width),
                y = next.y.coerceIn(0, screenH - height),
            )
        )
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(16.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Khối che",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = TerminalGreen, contentColor = Color.Black),
                    ) { Text("Xong") }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        "Một khối đen nổi trên màn hình chính để che icon của launcher hệ thống. " +
                            "Chỉ hiện khi đang dùng hình nền NEXORA, tự ẩn khi mở ứng dụng khác. " +
                            "Chạm vào khối che: nó ẩn ${"%.1f".format(ScreenCover.PEEK_MS / 1000f).replace('.', ',')} giây để bạn bấm được những gì bên dưới, rồi hiện lại.",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )

                    if (!canDraw) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFFFF5370))
                                .padding(12.dp),
                        ) {
                            Text(
                                "Cần quyền \"Hiển thị trên các ứng dụng khác\".",
                                color = Color(0xFFFF5370),
                                fontSize = 13.sp,
                            )
                            TextButton(onClick = {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    "package:${context.packageName}".toUri(),
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            }) { Text("Cấp quyền", color = TerminalGreen) }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Bật khối che", color = Color.White, modifier = Modifier.weight(1f))
                        Switch(
                            checked = cover.enabled,
                            onCheckedChange = { update(cover.copy(enabled = it)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = TerminalGreen),
                        )
                    }

                    CoverPreview(cover, screenW, screenH)

                    Row(modifier = Modifier.padding(vertical = 4.dp)) {
                        TextButton(onClick = {
                            update(cover.copy(enabled = true, x = 0, y = screenH - DOCK_HEIGHT, width = screenW, height = DOCK_HEIGHT))
                        }) { Text("Che thanh dock", color = TerminalGreen) }
                        TextButton(onClick = {
                            update(cover.copy(x = (screenW - cover.width) / 2))
                        }) { Text("Căn giữa ngang", color = TerminalGreen) }
                    }

                    DpSlider("X (cách trái)", cover.x, screenW) { update(cover.copy(x = it)) }
                    DpSlider("Y (cách trên)", cover.y, screenH) { update(cover.copy(y = it)) }
                    DpSlider("Chiều rộng", cover.width, screenW) { update(cover.copy(width = it)) }
                    DpSlider("Chiều cao", cover.height, screenH) { update(cover.copy(height = it)) }

                    Text("Độ đậm: ${(cover.alpha * 100).roundToInt()}%", color = Color.White, fontSize = 13.sp)
                    Slider(
                        value = cover.alpha,
                        onValueChange = { update(cover.copy(alpha = it)) },
                        valueRange = 0.2f..1f,
                        colors = SliderDefaults.colors(thumbColor = TerminalGreen, activeTrackColor = TerminalGreen),
                    )

                    // Room to scroll the last row clear of the bottom edge.
                    Spacer(Modifier.height(48.dp))
                }
            }
        }
    }
}

/** The screen in miniature with the block on it. */
@Composable
private fun CoverPreview(cover: CoverSettings, screenW: Int, screenH: Int) {
    var boxWidthPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .height(240.dp)
                .aspectRatio(screenW.toFloat() / screenH)
                .border(1.dp, TerminalDim)
                .onSizeChanged { boxWidthPx = it.width },
        ) {
            if (boxWidthPx > 0 && cover.width > 0 && cover.height > 0) {
                val k = boxWidthPx / screenW.toFloat() // px per screen dp
                with(density) {
                    Box(
                        modifier = Modifier
                            .offset { IntOffset((cover.x * k).roundToInt(), (cover.y * k).roundToInt()) }
                            .size((cover.width * k).toDp(), (cover.height * k).toDp())
                            .background(TerminalGreen.copy(alpha = if (cover.enabled) 0.6f * cover.alpha else 0.2f)),
                    )
                }
            }
            Text(
                "${screenW}×${screenH} dp",
                color = TerminalDim,
                fontSize = 9.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 2.dp),
            )
        }
    }
}

/** A dp value with a slider and -/+ steps for the fine part. */
@Composable
private fun DpSlider(label: String, value: Int, max: Int, onChange: (Int) -> Unit) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$label: $value dp", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { onChange(value - 1) }) { Text("−", color = TerminalGreen, fontSize = 18.sp) }
            TextButton(onClick = { onChange(value + 1) }) { Text("+", color = TerminalGreen, fontSize = 18.sp) }
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = 0f..max.toFloat(),
            colors = SliderDefaults.colors(thumbColor = TerminalGreen, activeTrackColor = TerminalGreen),
        )
    }
}

private const val DOCK_HEIGHT = 130
