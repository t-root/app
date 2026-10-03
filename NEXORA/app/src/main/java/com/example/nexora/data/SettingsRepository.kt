package com.example.nexora.data

import android.content.Context
import com.example.nexora.model.CoverSettings

/** Display options, shared by the launcher screen and the live wallpaper. */
class SettingsRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val selfPackage = context.packageName

    var showAppNames: Boolean
        get() = prefs.getBoolean(KEY_SHOW_APP_NAMES, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_APP_NAMES, value).apply()

    /**
     * The centre app: in no group, at the middle of the space, wired to every
     * group. NEXORA itself until the user picks another; null = none.
     */
    var centerPackage: String?
        get() = prefs.getString(KEY_CENTER, selfPackage)?.ifBlank { null }
        set(value) = prefs.edit().putString(KEY_CENTER, value ?: "").apply()

    /** Which group palette the stored group colours come from (for one-time migration). */
    var paletteVersion: Int
        get() = prefs.getInt(KEY_PALETTE_VERSION, 1)
        set(value) = prefs.edit().putInt(KEY_PALETTE_VERSION, value).apply()

    var cover: CoverSettings
        get() = CoverSettings(
            enabled = prefs.getBoolean(KEY_COVER_ENABLED, false),
            x = prefs.getInt(KEY_COVER_X, 0),
            y = prefs.getInt(KEY_COVER_Y, 0),
            width = prefs.getInt(KEY_COVER_W, 0),
            height = prefs.getInt(KEY_COVER_H, 0),
            alpha = prefs.getFloat(KEY_COVER_ALPHA, 1f),
        )
        set(value) = prefs.edit()
            .putBoolean(KEY_COVER_ENABLED, value.enabled)
            .putInt(KEY_COVER_X, value.x)
            .putInt(KEY_COVER_Y, value.y)
            .putInt(KEY_COVER_W, value.width)
            .putInt(KEY_COVER_H, value.height)
            .putFloat(KEY_COVER_ALPHA, value.alpha)
            .apply()

    private companion object {
        const val PREFS_NAME = "nexora_settings"
        const val KEY_SHOW_APP_NAMES = "show_app_names"
        const val KEY_CENTER = "center_package"
        const val KEY_PALETTE_VERSION = "palette_version"
        const val KEY_COVER_ENABLED = "cover_enabled"
        const val KEY_COVER_X = "cover_x"
        const val KEY_COVER_Y = "cover_y"
        const val KEY_COVER_W = "cover_w"
        const val KEY_COVER_H = "cover_h"
        const val KEY_COVER_ALPHA = "cover_alpha"
    }
}
