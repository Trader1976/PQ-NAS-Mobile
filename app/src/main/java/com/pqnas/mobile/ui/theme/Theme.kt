package com.pqnas.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val PQNASDarkColorScheme = darkColorScheme(
    primary = CpunkPrimary,
    onPrimary = CpunkBlack,
    secondary = CpunkSecondary,
    onSecondary = CpunkWhite,
    tertiary = CpunkAccent,
    onTertiary = CpunkBlack,

    background = CpunkBg,
    onBackground = CpunkText,

    surface = CpunkSurface,
    onSurface = CpunkText,

    surfaceVariant = CpunkSurface2,
    onSurfaceVariant = CpunkTextDim,

    outline = CpunkBorder,
    error = CpunkError,
    onError = CpunkWhite
)

@Composable
fun PQNASTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = PQNASDarkColorScheme,
        typography = Typography,
        content = content
    )
}