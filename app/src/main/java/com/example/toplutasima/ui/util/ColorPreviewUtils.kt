package com.example.toplutasima.ui.util

import androidx.compose.ui.graphics.Color

fun parsePreviewColor(hex: String): Color {
    return try {
        if (hex.isNotBlank()) Color(android.graphics.Color.parseColor(hex)) else Color.Transparent
    } catch (_: Exception) { Color.Transparent }
}
