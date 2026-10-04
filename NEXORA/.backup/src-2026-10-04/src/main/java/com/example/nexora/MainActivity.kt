package com.example.nexora

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.nexora.service.KnowledgeGraphWallpaperService
import com.example.nexora.ui.MainViewModel
import com.example.nexora.ui.SpaceScreen
import com.example.nexora.ui.TerminalGreen
import com.example.nexora.ui.terminalTypography

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = TerminalGreen,
                    background = Color.Black,
                    surface = Color.Black,
                ),
                typography = terminalTypography(),
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    SpaceScreen(
                        viewModel = viewModel,
                        onApplyLiveWallpaper = { requestApplyLiveWallpaper() },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshApps()
    }

    private fun requestApplyLiveWallpaper() {
        val intentList = mutableListOf<Intent>()

        intentList.add(
            Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(applicationContext, KnowledgeGraphWallpaperService::class.java),
                )
            }
        )

        intentList.add(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        intentList.add(Intent("com.samsung.android.wallpaper.LIVE_WALLPAPER_PICKER"))
        intentList.add(Intent(Settings.ACTION_SETTINGS))

        var launched = false
        for (intent in intentList) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                launched = true
                Toast.makeText(this, "Đang mở Cài đặt Hình nền...", Toast.LENGTH_SHORT).show()
                break
            } catch (_: Exception) {
                // Try next intent
            }
        }

        if (!launched) {
            Toast.makeText(
                this,
                "Vui lòng vào Cài đặt -> Hình nền & Phong cách -> Chọn NEXORA làm Hình nền động.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }
}
