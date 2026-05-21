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
internal fun DoneContent(
    state: BulkUpdateUiState,
    viewModel: BulkUpdateViewModel
) {
    val elapsedMin = (state.elapsedMs / 60000).toInt()
    val elapsedSec = ((state.elapsedMs % 60000) / 1000).toInt()

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (state.failCount == 0)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Güncelleme Tamamlandı",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("✅ ${state.successCount} başarılı", style = MaterialTheme.typography.bodyMedium)
                Text("❌ ${state.failCount} başarısız", style = MaterialTheme.typography.bodyMedium)
            }
            if (state.skipCount > 0) {
                Text("⏭ ${state.skipCount} atlandı", style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                "⏱ Süre: ${elapsedMin}dk ${elapsedSec}s",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    Button(
        onClick = { viewModel.resetState() },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(vertical = 14.dp)
    ) {
        Text("🔄 Yeniden Başlat", fontWeight = FontWeight.SemiBold)
    }
}
