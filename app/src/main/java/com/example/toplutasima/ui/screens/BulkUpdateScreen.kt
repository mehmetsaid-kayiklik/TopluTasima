package com.example.toplutasima.ui.screens

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.toplutasima.ui.screens.bulkupdate.DoneContent
import com.example.toplutasima.ui.screens.bulkupdate.IdleContent
import com.example.toplutasima.ui.screens.bulkupdate.LoadingContent
import com.example.toplutasima.ui.screens.bulkupdate.PausedContent
import com.example.toplutasima.ui.screens.bulkupdate.RateLimitedContent
import com.example.toplutasima.ui.screens.bulkupdate.RunningContent
import com.example.toplutasima.viewmodel.BulkUpdateViewModel
import com.example.toplutasima.viewmodel.bulkupdate.BulkUpdatePhase

@Composable
fun BulkUpdateSection(viewModel: BulkUpdateViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("🔄", fontSize = 22.sp)
                Text(
                    "Toplu Güncelleme",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                "Eksik verileri otomatik doldurur veya mevcut verileri düzeltir",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            when (state.phase) {
                BulkUpdatePhase.IDLE -> IdleContent(state, viewModel)
                BulkUpdatePhase.LOADING -> LoadingContent()
                BulkUpdatePhase.RUNNING -> RunningContent(state, viewModel)
                BulkUpdatePhase.PAUSED -> PausedContent(state, viewModel)
                BulkUpdatePhase.RATE_LIMITED -> RateLimitedContent(state, viewModel)
                BulkUpdatePhase.DONE -> DoneContent(state, viewModel)
            }
        }
    }
}
