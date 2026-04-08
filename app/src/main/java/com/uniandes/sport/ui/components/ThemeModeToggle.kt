package com.uniandes.sport.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import com.uniandes.sport.ui.theme.ThemeMode

@Composable
fun ThemeModeToggle(
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val (showThemeMenu, setShowThemeMenu) = remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(onClick = { setShowThemeMenu(true) }) {
            Icon(
                imageVector = when (themeMode) {
                    ThemeMode.LIGHT -> Icons.Default.LightMode
                    ThemeMode.DARK -> Icons.Default.DarkMode
                    ThemeMode.SYSTEM -> Icons.Default.SettingsBrightness
                },
                contentDescription = "Theme options"
                ,
                tint = MaterialTheme.colorScheme.onBackground
            )
        }

        DropdownMenu(
            expanded = showThemeMenu,
            onDismissRequest = { setShowThemeMenu(false) }
        ) {
            DropdownMenuItem(
                text = { Text("System Theme") },
                onClick = { onThemeChange(ThemeMode.SYSTEM); setShowThemeMenu(false) },
                leadingIcon = { Icon(Icons.Default.SettingsBrightness, null, tint = MaterialTheme.colorScheme.onSurface) }
            )
            DropdownMenuItem(
                text = { Text("Light Theme") },
                onClick = { onThemeChange(ThemeMode.LIGHT); setShowThemeMenu(false) },
                leadingIcon = { Icon(Icons.Default.LightMode, null, tint = MaterialTheme.colorScheme.onSurface) }
            )
            DropdownMenuItem(
                text = { Text("Dark Theme") },
                onClick = { onThemeChange(ThemeMode.DARK); setShowThemeMenu(false) },
                leadingIcon = { Icon(Icons.Default.DarkMode, null, tint = MaterialTheme.colorScheme.onSurface) }
            )
        }
    }
}