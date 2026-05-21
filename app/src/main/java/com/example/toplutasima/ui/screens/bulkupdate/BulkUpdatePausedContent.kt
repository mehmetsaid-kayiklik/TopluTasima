package com.example.toplutasima.ui.screens.bulkupdate

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toplutasima.viewmodel.BulkUpdateViewModel
import com.example.toplutasima.viewmodel.bulkupdate.BulkUpdateMode
import com.example.toplutasima.viewmodel.bulkupdate.BulkUpdateUiState
@Composable
internal fun PausedContent(
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

    Text(
        "⏸ Duraklatıldı — ${state.currentIndex + 1} / ${state.totalRows}",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium
    )

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("✅ ${state.successCount}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        Text("❌ ${state.failCount}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = { viewModel.resumeUpdate() },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("▶ Devam Et")
        }
        OutlinedButton(
            onClick = { viewModel.cancelUpdate() },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Text("⏹ İptal")
        }
    }
}
