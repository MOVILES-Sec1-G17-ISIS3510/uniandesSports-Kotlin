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
    primary = PrimaryDark, // Slate 100
    onPrimary = BackgroundDark, 
    primaryContainer = ContainerDark, // Slate 800
    onPrimaryContainer = OnContainerDark, // Slate 300
    secondary = TertiaryDark, // Brand Teal
    onSecondary = Color.White,
    secondaryContainer = ContainerDark,
    onSecondaryContainer = OnSurfaceDark,
    tertiary = SecondaryDark, // Slate 400
    onTertiary = BackgroundDark,
    tertiaryContainer = ContainerDark, 
    onTertiaryContainer = OnSurfaceDark,
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onBackground = OnSurfaceDark,
    onSurface = OnSurfaceDark,
    onSurfaceVariant = SecondaryDark // Slate 400 for secondary text
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryUniandes,
    onPrimary = Color.White,
    primaryContainer = SecondaryUniandes,
    onPrimaryContainer = PrimaryUniandes,
    secondary = TertiaryUniandes, // Teal for CTAs
    onSecondary = Color.White,
    secondaryContainer = SecondaryUniandes, // Mint Green for tags/chips
    onSecondaryContainer = PrimaryUniandes,
    tertiary = TertiaryUniandes, // Teal for CTAs
    background = BackgroundLight,
    surface = BackgroundLight,
    surfaceVariant = MutedLight, // Gray for inactive/dividers
    onBackground = ForegroundLight,
    onSurface = ForegroundLight,
    onSurfaceVariant = MutedForegroundLight // Gray for metadata
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