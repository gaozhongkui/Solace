package com.getsolace.ai.chat.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RunMateColorScheme = darkColorScheme(
    primary          = AccentPrimary,
    onPrimary        = TextPrimary,
    primaryContainer = CardBg,
    onPrimaryContainer = TextPrimary,

    secondary        = GlowCyan,
    onSecondary      = BgDeep,

    background       = BgDeep,
    onBackground     = TextPrimary,

    surface          = CardBg,
    onSurface        = TextPrimary,
    surfaceVariant   = CardBgAlt,
    onSurfaceVariant = TextSecondary,

    outline          = DividerColor,
    outlineVariant   = SurfaceOverlay,

    error            = ErrorRed,
    onError          = Color.White,
)

@Composable
fun SolaceTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = RunMateColorScheme,
        typography  = Typography,
        content     = content
    )
}
