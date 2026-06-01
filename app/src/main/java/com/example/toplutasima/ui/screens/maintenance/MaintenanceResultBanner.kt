package com.example.toplutasima.ui.screens.maintenance

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.example.toplutasima.ui.util.withoutEmojiCharacters

@Composable
internal fun MaintenanceResultBanner(result: String) {
    if (result.isBlank()) return

    val cleanResult = result.withoutEmojiCharacters()
    val isError = cleanResult.contains("hata", ignoreCase = true) ||
        cleanResult.contains("error", ignoreCase = true) ||
        cleanResult.contains("fehler", ignoreCase = true) ||
        cleanResult.contains("başarısız", ignoreCase = true) ||
        cleanResult.contains("failed", ignoreCase = true)

    Text(
        cleanResult,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    )
}
