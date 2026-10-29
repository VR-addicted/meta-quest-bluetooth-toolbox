package com.quest.systemsettings.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = AccentCyan,
    secondary = AccentGreen,
    tertiary = AccentPurple,
    background = VrDarkBackground,
    surface = VrCardBg,
    surfaceVariant = VrCardBgElevated,
    onPrimary = TextDark,
    onSecondary = TextDark,
    onTertiary = TextDark,
    onBackground = TextWhite,
    onSurface = TextWhite,
    onSurfaceVariant = TextLight,
    outline = BorderDefault,
    error = AccentRed
)

@Composable
fun QuestSystemSettingsTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
