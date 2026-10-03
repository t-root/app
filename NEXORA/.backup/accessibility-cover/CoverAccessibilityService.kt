package com.example.nexora.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Lets the cover block be a trusted accessibility overlay (fully opaque even
 * when touches pass through; an ordinary overlay is capped at 80%), and tells
 * it when the home screen is really in front: the top app window is the
 * launcher and no notification shade / system panel / other app's popup is
 * over it. Reads only the window list (type, bounds, owning package) — never
 * any text or content.
 */
class CoverAccessibilityService : AccessibilityService() {

    private val bounds = Rect()
    private var launcherPackage: String? = null

    // Debounce: the window list is briefly unsettled during transitions, so a
    // new state must hold a moment before the cover follows it.
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Boolean? = null
    private val commit = Runnable {
        pending?.let(ScreenCover::setHomeClear)
        pending = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        launcherPackage = findLauncher()
        homeState()?.let(ScreenCover::setHomeClear)
        ScreenCover.refresh()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val state = homeState() ?: return // unsettled: keep what we have
        if (state == ScreenCover.homeClear) {
            handler.removeCallbacks(commit)
            pending = null
            return
        }
        if (state == pending) return
        pending = state
        handler.removeCallbacks(commit)
        // Step aside fast (a shade is being pulled), come back a bit later.
        handler.postDelayed(commit, if (state) SHOW_DELAY_MS else HIDE_DELAY_MS)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        disconnect()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    private fun disconnect() {
        handler.removeCallbacks(commit)
        pending = null
        if (instance === this) {
            instance = null
            ScreenCover.refresh()
        }
    }

    /**
     * True: the home screen is in front with nothing over it. False: a shade,
     * panel, popup or another app is. Null: can't tell right now (mid-transition).
     */
    private fun homeState(): Boolean? {
        val launcher = launcherPackage ?: findLauncher().also { launcherPackage = it } ?: return null
        val list = try {
            windows
        } catch (_: Exception) {
            return null
        }
        val screenHeight = resources.displayMetrics.heightPixels
        var topApp: AccessibilityWindowInfo? = null
        for (window in list) {
            when (window.type) {
                AccessibilityWindowInfo.TYPE_APPLICATION ->
                    if (topApp == null || window.layer > topApp.layer) topApp = window
                // The status / navigation bars are thin; a tall system window is the
                // notification shade, quick settings or a system popup.
                AccessibilityWindowInfo.TYPE_SYSTEM -> {
                    window.getBoundsInScreen(bounds)
                    if (bounds.height() > screenHeight / 3) return false
                }
                else -> Unit // keyboard, divider, accessibility overlays (the cover itself)
            }
        }
        val app = topApp ?: return null
        val root = app.root ?: return null
        val pkg = root.packageName?.toString()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            @Suppress("DEPRECATION")
            root.recycle()
        }
        return pkg == launcher
    }

    private fun findLauncher(): String? {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    }

    companion object {
        private const val HIDE_DELAY_MS = 60L
        private const val SHOW_DELAY_MS = 250L

        /** The running service, or null when it's off in Accessibility settings. */
        @Volatile
        var instance: CoverAccessibilityService? = null
            private set
    }
}
