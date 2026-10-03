package com.example.nexora.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.example.nexora.model.CoverSettings
import kotlin.math.roundToInt

/**
 * The cover block: a black window drawn above the system launcher. The
 * wallpaper shows it while it is visible — i.e. on the home screen — and
 * removes it as soon as anything else is opened.
 *
 * With NEXORA's accessibility service on it is a trusted accessibility
 * overlay (fully opaque even when touches pass through); otherwise an
 * ordinary "display over other apps" overlay. Main thread only.
 */
object ScreenCover {

    /**
     * Android 12+ caps an ordinary overlay that lets touches through at this
     * opacity (untrusted-touch rule). Accessibility overlays are exempt.
     */
    const val MAX_PASS_THROUGH_ALPHA = 0.8f

    private var view: View? = null
    private var viewTrusted = false
    private var appContext: Context? = null

    // What the wallpaper last asked for (null = hidden), re-applied when the
    // accessibility service comes or goes.
    private var wanted: CoverSettings? = null

    // From the accessibility service: the launcher is in front with no shade,
    // panel or popup over it. (Without the service only the wallpaper decides.)
    var homeClear = true
        private set

    fun setHomeClear(clear: Boolean) {
        if (clear == homeClear) return
        homeClear = clear
        apply()
    }

    fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun accessibilityOn(): Boolean = CoverAccessibilityService.instance != null

    /** Whether it can be shown at all: accessibility service or overlay permission. */
    fun available(context: Context): Boolean = accessibilityOn() || canDraw(context)

    /** The opacity the cover really gets. */
    fun effectiveAlpha(cover: CoverSettings): Float =
        if (cover.passTouches && !accessibilityOn() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            minOf(cover.alpha, MAX_PASS_THROUGH_ALPHA)
        } else {
            cover.alpha
        }

    fun show(context: Context, cover: CoverSettings) {
        appContext = context.applicationContext
        wanted = cover
        apply()
    }

    fun hide(context: Context) {
        appContext = context.applicationContext
        wanted = null
        apply()
    }

    /** The accessibility service connected or went away: redo the window. */
    fun refresh() = apply()

    private fun apply() {
        val cover = wanted
        val service = CoverAccessibilityService.instance
        val app = appContext ?: service?.applicationContext ?: return
        if (cover == null || !cover.enabled || cover.width <= 0 || cover.height <= 0 || !available(app)) {
            removeView()
            return
        }
        // Something over the home screen (shade, popup): the window stays but
        // goes see-through and untouchable — removing and re-adding it is what
        // made it blink.
        val stepAside = service != null && !homeClear
        val trusted = service != null
        // A trusted window must be added through the service itself.
        val owner: Context = service ?: app
        if (view != null && viewTrusted != trusted) removeView()
        val wm = owner.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val params = layoutParams(owner, cover, trusted, stepAside)
        try {
            val current = view
            if (current == null) {
                // Solid black; the opacity is the window's, so 100% is truly opaque.
                val block = View(owner).apply { setBackgroundColor(Color.BLACK) }
                wm.addView(block, params)
                view = block
                viewTrusted = trusted
            } else {
                wm.updateViewLayout(current, params)
            }
        } catch (_: Exception) {
            // Permission revoked or window rejected: just don't cover.
            view = null
        }
    }

    private fun removeView() {
        val current = view ?: return
        view = null
        try {
            (current.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(current)
        } catch (_: Exception) {
        }
    }

    private fun layoutParams(
        context: Context,
        cover: CoverSettings,
        trusted: Boolean,
        stepAside: Boolean,
    ): WindowManager.LayoutParams {
        val density = context.resources.displayMetrics.density
        val alpha = if (stepAside) 0f else effectiveAlpha(cover)
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (cover.passTouches || stepAside) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        val type = if (trusted) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        }
        return WindowManager.LayoutParams(
            (cover.width * density).roundToInt(),
            (cover.height * density).roundToInt(),
            type,
            flags,
            if (alpha >= 1f) PixelFormat.OPAQUE else PixelFormat.TRANSLUCENT,
        ).apply {
            this.alpha = alpha
            // No system fade when it appears / goes.
            windowAnimations = 0
            gravity = Gravity.TOP or Gravity.START
            x = (cover.x * density).roundToInt()
            y = (cover.y * density).roundToInt()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    /** The whole screen in dp (status and navigation bars included). */
    fun screenSizeDp(context: Context): Pair<Int, Int> {
        val wm = context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val density = context.resources.displayMetrics.density
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.maximumWindowMetrics.bounds
            (bounds.width() / density).roundToInt() to (bounds.height() / density).roundToInt()
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            (metrics.widthPixels / density).roundToInt() to (metrics.heightPixels / density).roundToInt()
        }
    }
}
