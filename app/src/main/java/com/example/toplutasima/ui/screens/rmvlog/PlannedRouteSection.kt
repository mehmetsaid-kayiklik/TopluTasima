package com.example.toplutasima.ui.screens.rmvlog

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.S
import com.example.toplutasima.ui.WarningAmber
import com.example.toplutasima.transit.TransitFeatureFlags
import com.example.toplutasima.transit.provenance.TransitFieldProvenanceUseCase
import com.example.toplutasima.ui.components.transit.TransitSourceBadge
import com.example.toplutasima.viewmodel.RmvLogViewModel
import com.example.toplutasima.viewmodel.rmvlog.RmvLogUiState

@Composable
internal fun PlannedRouteSection(
    state: RmvLogUiState,
    viewModel: RmvLogViewModel,
    lang: AppLanguage
) {
            val provenanceUseCase = remember { TransitFieldProvenanceUseCase() }
            // --- PLAN BİLGİSİ ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(S.plannedRoute(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (state.trip != null) {
                            TextButton(onClick = { viewModel.setEditingTimes(!state.isEditingTimes) }) {
                                Text(
                                    if (state.isEditingTimes) S.editDone(lang) else S.editEdit(lang),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    val t = state.trip
                    if (t != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(S.departure(lang), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(t.overallDep, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(S.arrival(lang), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(t.overallArr, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(S.duration(lang), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${t.durationMin} ${S.minutesShort(lang)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        if (state.transitAlertsLoading) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text("Hat duyurulari kontrol ediliyor", style = MaterialTheme.typography.bodySmall)
                            }
                        } else if (state.transitAlerts.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                state.transitAlerts.forEach { alert ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = WarningAmber.copy(alpha = 0.16f)
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                                            Text(
                                                alert.title,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (alert.detail.isNotBlank() && alert.detail != alert.title) {
                                                Text(
                                                    alert.detail.take(220),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 3,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (state.isEditingTimes) {
                            t.segments.forEachIndexed { idx, s ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                ) {
                                    Text(s.line, modifier = Modifier.weight(0.4f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    OutlinedTextField(
                                        value = s.dep,
                                        onValueChange = { viewModel.updateSegmentDep(idx, it) },
                                        label = { Text(S.departure(lang)) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    OutlinedTextField(
                                        value = s.arr,
                                        onValueChange = { viewModel.updateSegmentArr(idx, it) },
                                        label = { Text(S.arrival(lang)) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                            }
                        } else {
                            t.segments.forEachIndexed { idx, s ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        val displayedDistanceKm = s.distanceKm.takeIf { it > 0.0 }
                                            ?: s.polyDistanceKm?.takeIf { it > 0.0 }
                                        Text("${S.vehicleTypeName(s.typeTr, lang)} • ${s.line}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        Text("${s.fromStop} ${s.dep} → ${s.toStop} ${s.arr}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (displayedDistanceKm != null || s.stopCount > 0) {
                                            Text(
                                                buildString {
                                                    if (s.stopCount > 0) append("${s.stopCount} ${S.stops(lang)}")
                                                    if (displayedDistanceKm != null) {
                                                        if (s.stopCount > 0) append("  •  ")
                                                        append("${String.format(java.util.Locale.US, "%.2f", displayedDistanceKm)} km")
                                                    }
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (TransitFeatureFlags.PROVENANCE_BADGES && displayedDistanceKm != null) {
                                            TransitSourceBadge(
                                                provenance = provenanceUseCase.segmentDistance(
                                                    fieldId = "segment-$idx-distance",
                                                    segment = s,
                                                    observedAtEpochMillis = state.tripUpdatedAtEpochMillis
                                                        ?: System.currentTimeMillis()
                                                ),
                                                lang = lang,
                                                fieldLabel = "Mesafe",
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }
                                    }
                                    // Change stop button — segmentIds doluysa her zaman göster
                                    if (state.segmentIds.isNotEmpty()) {
                                        IconButton(
                                            onClick = {
                                                if (!state.isLoadingStopsForEdit) {
                                                    viewModel.fetchStopsForChangeStop(idx)
                                                }
                                            },
                                            modifier = Modifier.size(32.dp),
                                            enabled = !state.isLoadingStopsForEdit
                                        ) {
                                            if (state.isLoadingStopsForEdit) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Outlined.Edit,
                                                    contentDescription = S.editEdit(lang),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                if (idx < t.segments.size - 1) {
                                    Spacer(Modifier.height(4.dp))
                                }
                            }
                        }
                    } else {
                        Text(S.noRouteYet(lang), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
}

@Composable
internal fun PersistentStopsSection(
    state: RmvLogUiState,
    lang: AppLanguage
) {
            // --- DURAK LİSTESİ ---
            val persistentStops = state.persistentStops
            if (persistentStops.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            S.allStopsTitle(lang),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        persistentStops.forEach { seg ->
                            if (seg.stopNames.isNotEmpty()) {
                                Text(
                                    S.lineStops(seg.line, lang),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                // Sadece biniş→iniş arasındaki durakları göster
                                val fromIdx = seg.stopFromIdx.coerceIn(0, seg.stopNames.lastIndex)
                                val toIdx = (seg.stopToIdx.takeIf { it >= 0 } ?: seg.stopNames.lastIndex)
                                    .coerceIn(0, seg.stopNames.lastIndex)
                                val visibleStops = if (toIdx >= fromIdx) {
                                    seg.stopNames.subList(fromIdx, toIdx + 1)
                                } else {
                                    seg.stopNames.subList(toIdx, fromIdx + 1).asReversed()
                                }
                                visibleStops.forEachIndexed { idx, name ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(start = 8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                        )
                                        Text(
                                            "${idx + 1}. $name",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
}
