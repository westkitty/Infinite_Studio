package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val InfiniteStudioColorScheme = darkColorScheme(
    primary = StudioColors.SkyAccent,
    onPrimary = StudioColors.SkyDarkText,
    primaryContainer = StudioColors.FuturesCardActiveBg,
    onPrimaryContainer = StudioColors.SkySoft,
    secondary = StudioColors.IndigoAccent,
    onSecondary = StudioColors.TextPrimary,
    tertiary = StudioColors.AmberAccent,
    background = StudioColors.VoidBlack,
    onBackground = StudioColors.TextPrimary,
    surface = StudioColors.FuturesDialogBg,
    onSurface = StudioColors.TextPrimary,
    surfaceVariant = StudioColors.SurfaceElevated,
    onSurfaceVariant = StudioColors.TextMuted,
    outline = StudioColors.FuturesDialogBorder
)

@Composable
fun InfiniteStudioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = InfiniteStudioColorScheme,
        typography = StudioTypography,
        content = content
    )
}
