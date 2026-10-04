package com.example.nexora.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap

data class AppNode(
    val packageName: String,
    val appName: String,
    val iconBitmap: ImageBitmap,
    val tintColor: Color,
    var position: Offset = Offset.Zero,
    var velocity: Offset = Offset.Zero
)

data class GraphEdge(
    val fromPackage: String,
    val toPackage: String
)
