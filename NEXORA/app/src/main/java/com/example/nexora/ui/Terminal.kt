package com.example.nexora.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.example.nexora.R

// Code Universe's look: green terminal text on black, Square-VN font.
val TerminalGreen = Color(0xFF00FF66)
val TerminalDim = Color(0xFF00A845)
val TerminalHighlight = Color(0xFFD9FFE6)

val SquareFont = FontFamily(Font(R.font.square_vn))

/** Material typography with every style in the Square font. */
fun terminalTypography(): Typography {
    val base = Typography()
    fun TextStyle.square() = copy(fontFamily = SquareFont)
    return Typography(
        displayLarge = base.displayLarge.square(),
        displayMedium = base.displayMedium.square(),
        displaySmall = base.displaySmall.square(),
        headlineLarge = base.headlineLarge.square(),
        headlineMedium = base.headlineMedium.square(),
        headlineSmall = base.headlineSmall.square(),
        titleLarge = base.titleLarge.square(),
        titleMedium = base.titleMedium.square(),
        titleSmall = base.titleSmall.square(),
        bodyLarge = base.bodyLarge.square(),
        bodyMedium = base.bodyMedium.square(),
        bodySmall = base.bodySmall.square(),
        labelLarge = base.labelLarge.square(),
        labelMedium = base.labelMedium.square(),
        labelSmall = base.labelSmall.square(),
    )
}
