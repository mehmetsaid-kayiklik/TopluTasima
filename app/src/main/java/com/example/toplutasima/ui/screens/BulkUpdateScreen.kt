package com.example.toplutasima.ui.screens

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
import com.example.toplutasima.viewmodel.BulkUpdateMode
import com.example.toplutasima.viewmodel.BulkUpdatePhase
import com.example.toplutasima.viewmodel.BulkUpdateViewModel

@Composable
fun BulkUpdateSection(viewModel: BulkUpdateViewModel) {
    val state by viewModel.uiState.collectAsState()

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

@Composable
private fun IdleContent(
    state: com.example.toplutasima.viewmodel.BulkUpdateUiState,
    viewModel: BulkUpdateViewModel
) {
    if (state.errorMessage.isNotBlank()) {
        Text(
            state.errorMessage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }

    if (state.totalRows > 0) {
        val modeLabel = when (state.mode) {
            BulkUpdateMode.DISTANCE_STOPS -> "📍 Mesafe & Durak"
            BulkUpdateMode.STOP_NAMES -> "🚏 Durak Adı & Yön"
        }
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "📋 ${state.totalRows} satır güncelleme bekliyor ($modeLabel)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Button(
            onClick = { viewModel.startUpdate() },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(vertical = 14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("▶ Güncellemeyi Başlat", fontWeight = FontWeight.SemiBold)
        }

        OutlinedButton(
            onClick = { viewModel.resetState() },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("↩ Sıfırla")
        }
    } else {
        var showDistanceDialog by remember { mutableStateOf(false) }
        var showStopsDialog by remember { mutableStateOf(false) }

        // Two distinct buttons for the two modes
        Button(
            onClick = { showDistanceDialog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            Text("📍 Mesafe & Durak Güncelle", fontWeight = FontWeight.SemiBold)
        }
        Text(
            "Sadece boş mesafe/durak satırlarını doldurur",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

        Button(
            onClick = { showStopsDialog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(vertical = 14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            )
        ) {
            Text("🚏 Durak Adı & Yön Güncelle", fontWeight = FontWeight.SemiBold)
        }
        Text(
            "Tüm satırlarda Biniş Durağı, İniş Durağı ve Yön bilgisini günceller",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

        var showResetDialog by remember { mutableStateOf(false) }

        Button(
            onClick = { showResetDialog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(vertical = 14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text("🗑️ Sıfırla (Sil)", fontWeight = FontWeight.SemiBold)
        }
        Text(
            "Tüm kayıtlardaki mesafe ve durak sayısı tamamen silinir (sıfırlanır).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (showDistanceDialog) {
            AlertDialog(
                onDismissRequest = { showDistanceDialog = false },
                title = { Text("Toplu Güncelleme") },
                text = { Text("Boş mesafe alanları doldurulacak ve tüm durak sayıları güncellenecek. Devam etmek istiyor musunuz?") },
                confirmButton = {
                    TextButton(onClick = {
                        showDistanceDialog = false
                        viewModel.loadPendingRows()
                    }) {
                        Text("Evet, Güncelle")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDistanceDialog = false }) {
                        Text("İptal")
                    }
                }
            )
        }

        if (showStopsDialog) {
            AlertDialog(
                onDismissRequest = { showStopsDialog = false },
                title = { Text("Toplu Güncelleme") },
                text = { Text("Tüm kayıtlardaki biniş/iniş durak adları ve yön bilgileri güncellenecek. Devam etmek istiyor musunuz?") },
                confirmButton = {
                    TextButton(onClick = {
                        showStopsDialog = false
                        viewModel.loadAllRows()
                    }) {
                        Text("Evet, Güncelle")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showStopsDialog = false }) {
                        Text("İptal")
                    }
                }
            )
        }

        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Text("Mesafe & Durak Sıfırlama") },
                text = { Text("Tüm kayıtlardaki mesafe ve durak sayısı silinecek. Emin misiniz?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showResetDialog = false
                            viewModel.resetAllDistanceAndStops()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Evet, Sil")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) {
                        Text("İptal")
                    }
                }
            )
        }
    }
}

@Composable
private fun LoadingContent() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        Text("Eksik satırlar yükleniyor...", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RunningContent(
    state: com.example.toplutasima.viewmodel.BulkUpdateUiState,
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

@Composable
private fun PausedContent(
    state: com.example.toplutasima.viewmodel.BulkUpdateUiState,
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

@Composable
private fun RateLimitedContent(
    state: com.example.toplutasima.viewmodel.BulkUpdateUiState,
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

@Composable
private fun DoneContent(
    state: com.example.toplutasima.viewmodel.BulkUpdateUiState,
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
