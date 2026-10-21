package com.simha.puzzlealarm.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Indigo = Color(0xFF3D4FD1)
private val Amber = Color(0xFFFFC94D)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9C3FF),
    onPrimary = Color(0xFF0B1A7A),
    primaryContainer = Color(0xFF26369E),
    secondary = Amber,
    onSecondary = Color(0xFF3F2E00),
    background = Color(0xFF0E1018),
    surface = Color(0xFF0E1018),
    surfaceVariant = Color(0xFF1C2030),
)

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFF8A6100),
)

@Composable
fun PuzzleAlarmTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
