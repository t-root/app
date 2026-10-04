package com.example.nexora.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Tells the cover block where other apps' floating windows are, so the block
 * can leave them uncovered. Reads only the window list (type, bounds, owning
 * package) — never any text or content.
 */
class CoverAccessibilityService : AccessibilityService() {

    private val bounds = Rect()
    private var lastLog = ""
    private var launcherPackage: String? = null
    private val debuggable get() = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        launcherPackage = findLauncher()
        refresh()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = refresh()

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        ScreenCover.setAvoid(emptyList())
        return super.onUnbind(intent)
    }

    private fun refresh() {
        val cover = ScreenCover.coverRectPx(this)
        if (cover == null) {
            ScreenCover.setAvoid(emptyList())
            return
        }
        val list = try {
            windows
        } catch (_: Exception) {
            return // can't tell right now: keep what we have
        }
        val launcher = launcherPackage ?: findLauncher().also { launcherPackage = it }
        val screen = resources.displayMetrics
        val screenArea = screen.widthPixels.toLong() * screen.heightPixels
        val avoid = ArrayList<PxRect>()
        val log = StringBuilder()
        for (window in list) {
            window.getBoundsInScreen(bounds)
            val rect = PxRect(bounds.left, bounds.top, bounds.right, bounds.bottom)
            val pkg = window.root?.packageName?.toString()
            val other = isOtherAppsFloatingWindow(window.type, pkg, launcher, rect, cover, screenArea)
            if (debuggable) log.append("type=${window.type} pkg=$pkg $rect avoid=$other\n")
            if (other) avoid.add(rect)
        }
        if (debuggable && log.toString() != lastLog) {
            lastLog = log.toString()
            Log.d(TAG, "cover=$cover\n$lastLog")
        }
        ScreenCover.setAvoid(avoid)
    }

    /**
     * A window of another app that floats over the cover: an overlay
     * ("display over other apps" comes as a system window) or an app window
     * that doesn't fill the screen (a chat bubble, a small window).
     */
    private fun isOtherAppsFloatingWindow(
        type: Int,
        pkg: String?,
        launcher: String?,
        rect: PxRect,
        cover: PxRect,
        screenArea: Long,
    ): Boolean {
        if (type != AccessibilityWindowInfo.TYPE_SYSTEM && type != AccessibilityWindowInfo.TYPE_APPLICATION) return false
        // Unknown owner: can't tell it from our own cover pieces; leave it.
        // The launcher's own windows are what the cover sits over, not what it avoids.
        if (pkg == null || pkg == packageName || pkg == launcher || pkg == SYSTEM_UI) return false
        // A screen-wide window (a dimmer, a full-screen panel or app) isn't something to cut around.
        if (rect.width.toLong() * rect.height > screenArea / 2) return false
        return rect.intersects(cover)
    }

    private fun findLauncher(): String? {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    }

    private companion object {
        const val TAG = "NexoraAvoid"
        const val SYSTEM_UI = "com.android.systemui"
    }
}
