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
internal fun IdleContent(
    state: BulkUpdateUiState,
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
