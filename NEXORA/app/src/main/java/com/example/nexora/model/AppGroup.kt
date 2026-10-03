package com.example.nexora.model

/**
 * A user-defined group of apps. In the 3D space every group becomes a hub
 * with its apps bound around it; apps in no group float freely.
 * An app belongs to at most one group.
 */
data class AppGroup(
    val id: String,
    val name: String,
    val color: Int, // ARGB
    val packages: List<String>,
)

object GroupColors {
    /**
     * 20 colours picked to tell apart at a glance (Sasha Trubetskoy's list of
     * distinct colours, the darkest ones lifted to read on black, navy swapped
     * for white). Ordered so groups made one after another contrast.
     */
    val PALETTE = listOf(
        0xFFE6194B.toInt(), // Red
        0xFF42D4F4.toInt(), // Cyan
        0xFFFFE119.toInt(), // Yellow
        0xFFB04DE0.toInt(), // Purple
        0xFF3CB44B.toInt(), // Green
        0xFFF58231.toInt(), // Orange
        0xFF4363D8.toInt(), // Blue
        0xFFF032E6.toInt(), // Magenta
        0xFFBFEF45.toInt(), // Lime
        0xFFFFFFFF.toInt(), // White
        0xFF3FB4A8.toInt(), // Teal
        0xFFFABED4.toInt(), // Pink
        0xFFB07A3C.toInt(), // Brown
        0xFFDCBEFF.toInt(), // Lavender
        0xFFA3A314.toInt(), // Olive
        0xFFAAFFC3.toInt(), // Mint
        0xFFC0392B.toInt(), // Maroon
        0xFFFFD8B1.toInt(), // Apricot
        0xFFA9A9A9.toInt(), // Grey
        0xFFFFFAC8.toInt(), // Beige
    )

    /** The palette before 2026-10-02 (several near-identical colours). */
    val OLD_PALETTE = setOf(
        0xFF00E5FF.toInt(), 0xFFFF007F.toInt(), 0xFF00FF66.toInt(), 0xFFFF9100.toInt(),
        0xFFFFEE00.toInt(), 0xFFB000FF.toInt(), 0xFF0099FF.toInt(), 0xFFFF3B30.toInt(),
        0xFFFF6EC7.toInt(), 0xFF1DE9B6.toInt(), 0xFFB2FF59.toInt(), 0xFFFFC400.toInt(),
        0xFFFF6E40.toInt(), 0xFF7C4DFF.toInt(), 0xFF40C4FF.toInt(), 0xFF69F0AE.toInt(),
        0xFFFFD740.toInt(), 0xFFFF4081.toInt(), 0xFF536DFE.toInt(), 0xFFE0E0E0.toInt(),
    )
}
