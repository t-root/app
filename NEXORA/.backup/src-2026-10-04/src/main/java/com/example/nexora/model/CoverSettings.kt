package com.example.nexora.model

/**
 * A black block shown above the system launcher while the NEXORA wallpaper
 * is on screen, to hide the launcher's own icons (e.g. the dock). In dp,
 * from the screen's top-left corner.
 */
data class CoverSettings(
    val enabled: Boolean = false,
    val x: Int = 0,
    val y: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val alpha: Float = 1f,
)
