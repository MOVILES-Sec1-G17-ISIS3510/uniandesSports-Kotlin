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
    onPrimary = BackgroundDark, // Dark background on vibrant primary looks good
    primaryContainer = Color(0xFF1E1B4B), // Indigo 950
    onPrimaryContainer = Color(0xFFC7D2FE), // Indigo 200
    secondary = SecondaryDark,
    onSecondary = BackgroundDark,
    secondaryContainer = Color(0xFF0C4A6E), // Sky 950
    onSecondaryContainer = Color(0xFFBAE6FD), // Sky 200
    tertiary = TertiaryDark,
    onTertiary = BackgroundDark,
    tertiaryContainer = Color(0xFF134E4A), // Teal 950
    onTertiaryContainer = Color(0xFF99F6E4), // Teal 200
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onBackground = Color(0xFFF1F5F9), // Slate 100
    onSurface = Color(0xFFF1F5F9), // Slate 100
    onSurfaceVariant = Color(0xFF94A3B8) // Slate 400
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryUniandes,
    onPrimary = Color.White,
    primaryContainer = SecondaryUniandes,
    onPrimaryContainer = PrimaryUniandes,
    secondary = TertiaryUniandes,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1F5F9),
    onSecondaryContainer = Color(0xFF64748B),
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
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme
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