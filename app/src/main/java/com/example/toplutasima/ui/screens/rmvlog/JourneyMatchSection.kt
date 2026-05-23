package com.example.toplutasima.ui.screens.rmvlog

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import com.example.toplutasima.data.PrefsManager
import com.example.toplutasima.viewmodel.RmvLogViewModel
import com.example.toplutasima.viewmodel.rmvlog.RmvLogUiState

@Composable
internal fun JourneyMatchSection(
    state: RmvLogUiState,
    viewModel: RmvLogViewModel,
    hasLocationPermission: Boolean,
    onRequestLocationPermission: () -> Unit
) {
            // --- KALKIŞLAR CARD ---
            if (PrefsManager.gpsJourneyMatchEnabled) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("GPS ile Sefer Esleştir", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    state.journeyMatchMessage.ifBlank { "Kisa GPS iziyle olasi seferi bul" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = {
                                    if (hasLocationPermission) viewModel.startJourneyMatch()
                                    else onRequestLocationPermission()
                                },
                                enabled = !state.journeyMatchLoading,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                if (state.journeyMatchLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Baslat")
                                }
                            }
                        }
                        state.journeyMatchCandidates.forEach { candidate ->
                            AssistChip(
                                onClick = { viewModel.confirmJourneyMatch(candidate) },
                                label = {
                                    Text("${candidate.line} ${candidate.direction}".trim().ifBlank { candidate.line })
                                },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }
            }
}