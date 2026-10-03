package com.example.nexora.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.abs

object IconProcessor {

    private const val ICON_SIZE = 144 // Crisp icon resolution in pixels

    fun generateUniqueColor(packageName: String): Color {
        val hash = abs(packageName.hashCode())
        val hue = (hash % 360).toFloat()
        val hsv = floatArrayOf(hue, 0.88f, 0.95f)
        val androidColor = AndroidColor.HSVToColor(hsv)
        return Color(androidColor)
    }

    fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            if (drawable.bitmap.width == ICON_SIZE && drawable.bitmap.height == ICON_SIZE) {
                return drawable.bitmap
            }
        }

        val bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    fun processIcon(drawable: Drawable, packageName: String): Pair<ImageBitmap, Color> {
        val tintColor = generateUniqueColor(packageName)
        val originalBitmap = drawableToBitmap(drawable)
        val width = originalBitmap.width
        val height = originalBitmap.height

        val pixels = IntArray(width * height)
        originalBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val tR = (tintColor.red * 255).toInt()
        val tG = (tintColor.green * 255).toInt()
        val tB = (tintColor.blue * 255).toInt()

        for (i in pixels.indices) {
            val color = pixels[i]
            val a = (color shr 24) and 0xFF
            if (a == 0) continue

            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF

            // Calculate luminance (grayscale value 0..1)
            val luminance = (0.299f * r + 0.587f * g + 0.114f * b) / 255.0f

            val outR = (tR * luminance).toInt().coerceIn(0, 255)
            val outG = (tG * luminance).toInt().coerceIn(0, 255)
            val outB = (tB * luminance).toInt().coerceIn(0, 255)

            pixels[i] = (a shl 24) or (outR shl 16) or (outG shl 8) or outB
        }

        val processedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        processedBitmap.setPixels(pixels, 0, width, 0, 0, width, height)

        return Pair(processedBitmap.asImageBitmap(), tintColor)
    }
}
