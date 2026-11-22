package com.moyu.novel.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val MoyuBlue = Color(0xFF4D6BFE)
val MoyuBlueSoft = Color(0xFF6E86FF)
val MoyuBg = Color(0xFF121317)
val MoyuSurface = Color(0xFF1B1C21)
val MoyuSurfaceElevated = Color(0xFF24262D)
val MoyuSurfaceHigh = Color(0xFF2D2F38)
val MoyuText = Color(0xFFECECEF)
val MoyuTextDim = Color(0xFF9B9CA6)
val MoyuBorder = Color(0xFF2E3038)
val MoyuError = Color(0xFFFF6B6B)
val MoyuGreen = Color(0xFF4ADE80)

private val MoyuColors = darkColorScheme(
    primary = MoyuBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2A3560),
    onPrimaryContainer = Color(0xFFDCE2FF),
    secondary = MoyuBlueSoft,
    onSecondary = Color.White,
    background = MoyuBg,
    onBackground = MoyuText,
    surface = MoyuSurface,
    onSurface = MoyuText,
    surfaceVariant = MoyuSurfaceElevated,
    onSurfaceVariant = MoyuTextDim,
    outline = MoyuBorder,
    outlineVariant = MoyuBorder,
    error = MoyuError,
    onError = Color.White,
    scrim = Color.Black,
)

@Composable
fun MoyuTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MoyuColors,
        content = content,
    )
}
