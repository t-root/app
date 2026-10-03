package com.example.nexora.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.example.nexora.model.CoverSettings
import kotlin.math.roundToInt

/**
 * The cover block: a "display over other apps" window above the system
 * launcher. The wallpaper shows it while it is visible — i.e. on the home
 * screen — and removes it as soon as anything else is opened.
 *
 * It hides the launcher's icons but not NEXORA's space: it draws the part of
 * the space it sits on (black with the floating apps on top, via [content]),
 * so the wallpaper reads as one piece. Its touches go to the space too; a tap
 * on nothing there makes it step aside for [PEEK_MS] — see-through and
 * untouchable — so what's under it can be used. Main thread only.
 */
object ScreenCover {

    const val PEEK_MS = 500L

    /** After a tap that may have opened an app: long enough for its opening animation. */
    private const val LAUNCH_GRACE_MS = 800L

    private var view: View? = null
    private var appContext: Context? = null

    // What the wallpaper last asked for (null = hidden).
    private var wanted: CoverSettings? = null

    private var peeking = false

    // Why the launcher needs the cover out of the way right now (recents,
    // a folder, a zoom transition) — set from the wallpaper's signals.
    private val asideReasons = HashSet<String>()

    fun setAside(reason: String, aside: Boolean) {
        val changed = if (aside) asideReasons.add(reason) else asideReasons.remove(reason)
        if (changed) apply()
    }

    private val handler = Handler(Looper.getMainLooper())
    private val endPeek = Runnable {
        peeking = false
        apply()
    }

    /** What the cover shows and where its touches go: the wallpaper's space. */
    interface Content {
        /** Draws the whole screen; the canvas is already shifted to screen coordinates. */
        fun drawCover(canvas: Canvas)

        /** A touch on the cover, in screen coordinates. */
        fun touchCover(event: MotionEvent)
    }

    var content: Content? = null

    fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** The space moved: redraw the part under the cover. */
    fun invalidate() {
        view?.invalidate()
    }

    /** A tap on an empty spot of the cover: let the launcher under it be used. */
    fun peekNow() = peek()

    fun show(context: Context, cover: CoverSettings) {
        appContext = context.applicationContext
        wanted = cover
        apply()
    }

    fun hide(context: Context) {
        appContext = context.applicationContext
        wanted = null
        handler.removeCallbacks(endPeek)
        peeking = false
        asideReasons.clear()
        apply()
    }

    /** An app is being opened from the home screen: gone now, back next time the home screen shows. */
    fun hideForLaunch(context: Context) = hide(context)

    /**
     * A tap reached the home screen. If the cover was stepped aside it may have
     * opened an app under it, so it stays out of the way through the opening
     * animation instead of coming back on top of the app.
     */
    fun onHomeTap() {
        if (peeking) stepAside(LAUNCH_GRACE_MS)
    }

    /** Touched: out of the way for a moment. */
    private fun peek() = stepAside(PEEK_MS)

    private fun stepAside(ms: Long) {
        peeking = true
        apply()
        handler.removeCallbacks(endPeek)
        handler.postDelayed(endPeek, ms)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun apply() {
        val cover = wanted
        val app = appContext ?: return
        if (cover == null || !cover.enabled || cover.width <= 0 || cover.height <= 0 || !canDraw(app)) {
            removeView()
            return
        }
        val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val params = layoutParams(app, cover)
        try {
            val current = view
            if (current == null) {
                // Opaque; the opacity is the window's, so 100% truly hides the launcher.
                val block = CoverView(app)
                wm.addView(block, params)
                view = block
            } else {
                // Changing the window in place (not removing / re-adding it) keeps it from blinking.
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

    private fun layoutParams(context: Context, cover: CoverSettings): WindowManager.LayoutParams {
        val density = context.resources.displayMetrics.density
        val aside = peeking || asideReasons.isNotEmpty()
        val alpha = if (aside) 0f else cover.alpha
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (aside) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        return WindowManager.LayoutParams(
            (cover.width * density).roundToInt(),
            (cover.height * density).roundToInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
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

    /** Black, with the part of the space behind it drawn on top. */
    private class CoverView(context: Context) : View(context) {
        private val location = IntArray(2)

        init {
            setBackgroundColor(Color.BLACK)
        }

        override fun onDraw(canvas: Canvas) {
            val source = content ?: return
            getLocationOnScreen(location)
            canvas.save()
            canvas.translate(-location[0].toFloat(), -location[1].toFloat())
            source.drawCover(canvas)
            canvas.restore()
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            val target = content
            if (target == null) {
                if (event.actionMasked == MotionEvent.ACTION_DOWN) peek()
                return true
            }
            getLocationOnScreen(location)
            val onScreen = MotionEvent.obtain(event)
            onScreen.offsetLocation(location[0].toFloat(), location[1].toFloat())
            target.touchCover(onScreen)
            onScreen.recycle()
            return true
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
