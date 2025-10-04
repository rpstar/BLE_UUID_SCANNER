package com.example.ble_uuid_scanner.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// FIX: Rename the Typography object to AppTypography to avoid naming conflicts.
// The "Overload resolution ambiguity" error was caused by a clash with MaterialTheme's own
// 'typography' parameter. Giving our app's typography a unique name resolves this.
val AppTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)
