package com.example.nexora.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import com.example.nexora.model.AppNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class AppRepository(private val context: Context) {

    suspend fun getInstalledApps(): List<AppNode> = withContext(Dispatchers.Default) {
        val packageManager = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        // NEXORA itself is listed too: tapping it on the live wallpaper opens
        // the app to manage groups.
        val resolveInfoList = packageManager.queryIntentActivities(mainIntent, 0)
            .distinctBy { it.activityInfo.packageName }

        coroutineScope {
            resolveInfoList.map { resolveInfo ->
                async {
                    val packageName = resolveInfo.activityInfo.packageName
                    val updated = try {
                        packageManager.getPackageInfo(packageName, 0).lastUpdateTime
                    } catch (_: Exception) {
                        0L
                    }
                    val icon = lineIcon(packageName, updated) {
                        LineIconProcessor.process(resolveInfo.loadIcon(packageManager))
                    }
                    AppNode(
                        packageName = packageName,
                        appName = resolveInfo.loadLabel(packageManager).toString(),
                        iconBitmap = icon.bitmap.asImageBitmap(),
                        tintColor = Color(icon.color),
                    )
                }
            }.awaitAll()
        }
    }

    fun launchApp(packageName: String) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        }
    }

    /**
     * Line icons take a while to compute, so they are kept in memory (shared by
     * the launcher and the wallpaper) and on disk, keyed by the app's update time.
     * The colour rides along in the file name.
     */
    private fun lineIcon(packageName: String, updated: Long, compute: () -> LineIconProcessor.Result): LineIconProcessor.Result {
        val key = "$packageName@$updated@v$CACHE_VERSION"
        memory[key]?.let { return it }

        val dir = File(context.cacheDir, "line_icons").apply { mkdirs() }
        dir.listFiles { f -> f.name.startsWith("$key@") }?.firstOrNull()?.let { file ->
            val color = file.nameWithoutExtension.substringAfterLast('@').toLongOrNull(16)?.toInt()
            val bitmap = BitmapFactory.decodeFile(file.path)
            if (color != null && bitmap != null) {
                return LineIconProcessor.Result(bitmap, color).also { memory[key] = it }
            }
        }

        val result = compute()
        // Drop older versions of this app's icon, then store the new one.
        dir.listFiles { f -> f.name.startsWith("$packageName@") }?.forEach { it.delete() }
        try {
            File(dir, "$key@${Integer.toHexString(result.color)}.png").outputStream().use {
                result.bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } catch (_: Exception) {
            // Cache only; the icon still works.
        }
        memory[key] = result
        return result
    }

    private companion object {
        const val CACHE_VERSION = 1
        val memory = ConcurrentHashMap<String, LineIconProcessor.Result>()
    }
}
