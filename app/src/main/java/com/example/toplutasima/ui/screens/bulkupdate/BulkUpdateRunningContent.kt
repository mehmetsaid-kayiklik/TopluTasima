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
internal fun RunningContent(
    state: BulkUpdateUiState,
    viewModel: BulkUpdateViewModel
) {
    val progress = if (state.totalRows > 0) (state.currentIndex + 1f) / state.totalRows else 0f
    val completed = state.successCount + state.failCount + state.skipCount
    val remaining = state.totalRows - completed
    val etaMs = if (state.avgMsPerRow > 0) state.avgMsPerRow * remaining else 0L
    val etaMin = (etaMs / 60000).toInt()
    val etaSec = ((etaMs % 60000) / 1000).toInt()

    // Progress bar
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth().height(8.dp),
        trackColor = MaterialTheme.colorScheme.surfaceVariant
    )

    // Stats row
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            "${state.currentIndex + 1} / ${state.totalRows}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold
        )
        if (etaMs > 0) {
            Text(
                "⏱ ~${etaMin}dk ${etaSec}s kaldı",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // Current row
    AnimatedVisibility(visible = state.currentRowInfo.isNotBlank(), enter = fadeIn(), exit = fadeOut()) {
        Text(
            "🚌 ${state.currentRowInfo}",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }

    // Counters
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("✅ ${state.successCount}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        Text("❌ ${state.failCount}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        if (state.skipCount > 0) {
            Text("⏭ ${state.skipCount}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    // Control buttons
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = { viewModel.pauseUpdate() },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("⏸ Duraklat")
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
