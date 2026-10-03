package com.example.nexora

import android.app.WallpaperManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
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
import androidx.core.net.toUri
import com.example.nexora.service.KnowledgeGraphWallpaperService
import com.example.nexora.ui.GraphCanvas
import com.example.nexora.ui.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkDefaultLauncher()

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF07090E),
                    surface = Color(0xFF0D1117),
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    GraphCanvas(
                        viewModel = viewModel,
                        onSetDefaultLauncher = { requestSetDefaultLauncher() },
                        onApplyLiveWallpaper = { requestApplyLiveWallpaper() },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkDefaultLauncher()
    }

    private fun checkDefaultLauncher() {
        val isDefault = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(ROLE_SERVICE) as? RoleManager
            roleManager?.isRoleHeld(RoleManager.ROLE_HOME) == true
        } else {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
            val resolveInfo = packageManager.resolveActivity(intent, 0)
            resolveInfo?.activityInfo?.packageName == packageName
        }
        viewModel.setIsDefaultLauncher(isDefault)
    }

    private fun requestSetDefaultLauncher() {
        val intentList = mutableListOf<Intent>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(ROLE_SERVICE) as? RoleManager
            if (roleManager?.isRoleAvailable(RoleManager.ROLE_HOME) == true) {
                intentList.add(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
            }
        }

        intentList.add(Intent(Settings.ACTION_HOME_SETTINGS))
        intentList.add(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))

        intentList.add(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = "package:$packageName".toUri()
            }
        )

        intentList.add(Intent(Settings.ACTION_SETTINGS))

        var launched = false
        for (intent in intentList) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                launched = true
                Toast.makeText(this, "Đang mở Cài đặt Màn hình chính...", Toast.LENGTH_SHORT).show()
                break
            } catch (_: Exception) {
                // Try next intent
            }
        }

        if (!launched) {
            Toast.makeText(
                this,
                "Vui lòng vào Cài đặt -> Ứng dụng -> Ứng dụng mặc định -> Màn hình chính -> Chọn NEXORA",
                Toast.LENGTH_LONG,
            ).show()
        }
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
