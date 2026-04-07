package com.getsolace.ai.chat.ui.screens.ai

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.getsolace.ai.chat.ui.theme.*

// ─── Toast ────────────────────────────────────────────────────────────────────

@Composable
fun RunMateToast(message: String, isSuccess: Boolean) {
    Surface(
        shape           = RoundedCornerShape(AppRadius.xxl),
        color           = if (isSuccess) SuccessGreen.copy(alpha = 0.95f) else ErrorRed.copy(alpha = 0.95f),
        shadowElevation = 8.dp
    ) {
        Text(
            text     = message,
            modifier = Modifier.padding(horizontal = AppSpacing.xl, vertical = AppSpacing.md),
            style    = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(
                color      = Color.White,
                fontWeight = FontWeight.Medium
            )
        )
    }
}
