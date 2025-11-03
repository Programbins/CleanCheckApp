package com.example.cleanchecknative.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = BlueSecondary,
    secondary = BlueContainer,
    tertiary = Color(0xFF99C5FF),
    background = Color(0xFF1A1C1E),
    surface = Color(0xFF1A1C1E),
    onPrimary = Color(0xFF00325A),
    onSecondary = OnBlueContainer,
    onTertiary = Color(0xFF00325A),
    onBackground = Color(0xFFE3E2E6),
    onSurface = Color(0xFFE3E2E6),
)

private val LightColorScheme = lightColorScheme(
    primary = BluePrimary,
    secondary = BlueSecondary,
    tertiary = Color(0xFF0061A4),
    background = Color(0xFFFDFBFF),
    surface = Color(0xFFFDFBFF),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1A1C1E),
    onSurface = Color(0xFF1A1C1E),
    primaryContainer = BlueContainer,
    onPrimaryContainer = OnBlueContainer
)

@Composable
fun CleanCheckNativeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
