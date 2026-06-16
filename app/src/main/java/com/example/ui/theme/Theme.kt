package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.graphics.Color

private val CrimsonColorScheme = darkColorScheme(
    primary = CrimsonPrimary,
    secondary = CrimsonSecondary,
    background = CrimsonBackground,
    surface = CrimsonSurface,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color(0xFFF1F1F1),
    onSurface = Color(0xFFF1F1F1)
)

private val CyberColorScheme = darkColorScheme(
    primary = CyberPink,
    secondary = CyberCyan,
    background = CyberBackground,
    surface = CyberSurface,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color(0xFFECEFF1),
    onSurface = Color(0xFFECEFF1)
)

private val VioletColorScheme = darkColorScheme(
    primary = VioletPrimary,
    secondary = VioletSecondary,
    background = VioletBackground,
    surface = VioletSurface,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color(0xFFE8E5EE),
    onSurface = Color(0xFFE8E5EE)
)

private val EmeraldColorScheme = darkColorScheme(
    primary = EmeraldPrimary,
    secondary = EmeraldSecondary,
    background = EmeraldBackground,
    surface = EmeraldSurface,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color(0xFFE2F0E5),
    onSurface = Color(0xFFE2F0E5)
)

private val GeometricColorScheme = lightColorScheme(
    primary = GeometricPrimary,
    secondary = GeometricSecondary,
    background = GeometricBackground,
    surface = GeometricSurface,
    surfaceVariant = GeometricSurfaceVariant,
    onPrimary = Color.White,
    onSecondary = Color(0xFF21005D),
    onBackground = GeometricOnSurface,
    onSurface = GeometricOnSurface,
    onSurfaceVariant = GeometricOnSurfaceVariant,
    outline = GeometricOutline
)

@Composable
fun MyApplicationTheme(
  themeName: String = "Geometric Balance",
  content: @Composable () -> Unit,
) {
  val colorScheme = when (themeName) {
    "Crimson Cardio" -> CrimsonColorScheme
    "Cyberpunk Neon" -> CyberColorScheme
    "Electric Violet" -> VioletColorScheme
    "Emerald Athlete" -> EmeraldColorScheme
    else -> GeometricColorScheme
  }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
