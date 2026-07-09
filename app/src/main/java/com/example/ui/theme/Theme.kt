package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = AthleticPrimary,
    onPrimary = AthleticOnPrimary,
    secondary = AthleticSecondary,
    onSecondary = AthleticOnSecondary,
    tertiary = AthleticTertiary,
    background = AthleticBackground,
    onBackground = AthleticOnBackground,
    surface = AthleticSurface,
    onSurface = AthleticOnSurface,
    surfaceVariant = AthleticSurfaceVariant
)

private val LightColorScheme = lightColorScheme(
    primary = AthleticLightPrimary,
    secondary = AthleticLightSecondary,
    tertiary = AthleticLightTertiary,
    background = AthleticLightBg,
    onBackground = Color(0xFF0F0F11),
    surface = AthleticLightSurface,
    onSurface = Color(0xFF0F0F11)
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false, // Set to false to enforce our premium athletic brand colors
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> DarkColorScheme // Force Dark Mode as the primary brand experience for fitness/performance
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
