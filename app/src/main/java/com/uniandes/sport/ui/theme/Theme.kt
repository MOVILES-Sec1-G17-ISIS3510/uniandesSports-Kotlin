package com.uniandes.sport.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = Color.White,
    secondary = SecondaryUniandes,
    onSecondary = PrimaryUniandes,
    tertiary = TertiaryUniandes,
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = Color(0xFF374151),
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFF9CA3AF)
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryUniandes,
    onPrimary = Color.White,
    secondary = SecondaryUniandes,
    onSecondary = PrimaryUniandes,
    tertiary = TertiaryUniandes,
    background = BackgroundLight,
    surface = BackgroundLight,
    surfaceVariant = MutedLight,
    onBackground = ForegroundLight,
    onSurface = ForegroundLight,
    onSurfaceVariant = Color(0xFF64748B)
)

enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}

// Global state for theme
var LocalThemeMode = androidx.compose.runtime.compositionLocalOf { ThemeMode.SYSTEM }

@Composable
fun UniandesSportsKotlinTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalThemeMode provides themeMode) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}