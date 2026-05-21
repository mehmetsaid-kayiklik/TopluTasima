package com.example.toplutasima.ui.screens.maintenance

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight

@Composable
internal fun MaintenanceResultBanner(result: String) {
    if (result.isBlank()) return

    Text(
        result,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = if (result.contains("✅")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    )
}
