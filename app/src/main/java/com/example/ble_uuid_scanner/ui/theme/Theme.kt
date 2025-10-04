package com.example.ble_uuid_scanner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Defines the color palette for the dark theme.
private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

// Defines the color palette for the light theme, for completeness.
private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun BLE_UUID_SCANNERTheme(
    darkTheme: Boolean = true, // Default to dark theme as requested.
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        // FIX: Use the renamed AppTypography object from Typography.kt directly.
        // This removes the naming conflict with MaterialTheme's own 'typography' parameter.
        typography = AppTypography,
        content = content
    )
}
