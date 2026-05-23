package com.example.toplutasima.ui.screens.bulkupdate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.toplutasima.viewmodel.BulkUpdateViewModel
import com.example.toplutasima.viewmodel.bulkupdate.BulkUpdateUiState

@Composable
internal fun RateLimitedContent(
    state: BulkUpdateUiState,
    viewModel: BulkUpdateViewModel
) {
    val progress = if (state.totalRows > 0) (state.currentIndex + 1f) / state.totalRows else 0f

    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth().height(8.dp),
        color = MaterialTheme.colorScheme.tertiary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant
    )

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "⏳ Bekleniyor... ${state.rateLimitCountdown}s",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text(
                state.rateLimitReason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
            )
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("✅ ${state.successCount}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        Text("❌ ${state.failCount}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }

    OutlinedButton(
        onClick = { viewModel.cancelUpdate() },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
    ) {
        Text("⏹ İptal")
    }
}
