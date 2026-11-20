package com.qcenaman.francesinha.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFFB5541B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBC9),
    onPrimaryContainer = Color(0xFF3A1400),
    secondary = Color(0xFF6D5A00),
    secondaryContainer = Color(0xFFFFE17A),
    tertiary = Color(0xFF3E6838),
    tertiaryContainer = Color(0xFFBFF0B1),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB68F),
    onPrimary = Color(0xFF5A2300),
    primaryContainer = Color(0xFF7F3A08),
    onPrimaryContainer = Color(0xFFFFDBC9),
    secondary = Color(0xFFE3C55A),
    secondaryContainer = Color(0xFF524300),
    tertiary = Color(0xFFA3D397),
    tertiaryContainer = Color(0xFF265022),
)

@Composable
fun FrancesinhaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
