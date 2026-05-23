package com.example.toplutasima.ui.screens.rmvlog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.ErrorRed
import com.example.toplutasima.ui.S
import com.example.toplutasima.ui.SuccessGreen
import com.example.toplutasima.ui.WarningAmber
import com.example.toplutasima.ui.util.vehicleIcon
import com.example.toplutasima.viewmodel.RmvLogViewModel
import com.example.toplutasima.viewmodel.rmvlog.RmvLogUiState

@Composable
internal fun AdditionalInfoSection(
    state: RmvLogUiState,
    viewModel: RmvLogViewModel,
    lang: AppLanguage
) {
            // --- EK BİLGİLER CARD ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Title row with inline segment selector
                    val ekTrip = state.trip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(S.additionalInfo(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (ekTrip != null && ekTrip.segments.size > 1) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                IconButton(
                                    onClick = { viewModel.prevSegment() },
                                    enabled = state.selectedSegmentIndex > 0,
                                    modifier = Modifier.size(28.dp)
                                ) { Text("←", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (state.selectedSegmentIndex > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) }
                                Text(
                                    "${state.selectedSegmentIndex + 1}/${ekTrip.segments.size}",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                IconButton(
                                    onClick = { viewModel.nextSegment() },
                                    enabled = state.selectedSegmentIndex < ekTrip.segments.size - 1,
                                    modifier = Modifier.size(28.dp)
                                ) { Text("→", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (state.selectedSegmentIndex < ekTrip.segments.size - 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) }
                            }
                        }
                    }
                    // Show current segment info & transfer point
                    if (ekTrip != null && ekTrip.segments.size > 1) {
                        val ekSeg = ekTrip.segments.getOrNull(state.selectedSegmentIndex)
                        if (ekSeg != null) {
                            val ekEmoji = vehicleIcon(ekSeg.typeTr)
                            Text(
                                "$ekEmoji ${ekSeg.line} → ${ekSeg.toStop}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            // Transfer point info
                            val nextSeg = ekTrip.segments.getOrNull(state.selectedSegmentIndex + 1)
                            if (nextSeg != null) {
                                Text(
                                    "🔄 ${ekSeg.toStop} (${ekSeg.arr} → ${nextSeg.dep})",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }

                    // Per-segment values
                    val curHava = state.segmentHavaDurumu[state.selectedSegmentIndex] ?: "Bilinmiyor"
                    val curOtur = state.segmentOturabildim[state.selectedSegmentIndex] ?: false
                    val curBilet = state.segmentBiletKontrolu[state.selectedSegmentIndex] ?: false
                    val curNote = state.segmentNote[state.selectedSegmentIndex] ?: ""

                    // Hava Durumu
                    Box {
                        FilledTonalButton(
                            onClick = { viewModel.setHavaMenuOpen(true) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            val havaEmoji = S.weatherOptions.find { it.first == curHava }?.second ?: "❓"
                            Text("$havaEmoji  ${S.weatherLabel(lang)}: ${S.weatherName(curHava, lang)}", fontWeight = FontWeight.SemiBold)
                        }
                        DropdownMenu(expanded = state.havaMenuOpen, onDismissRequest = { viewModel.setHavaMenuOpen(false) }) {
                            S.weatherOptions.forEach { (key, emoji) ->
                                DropdownMenuItem(
                                    text = { Text("$emoji  ${S.weatherName(key, lang)}") },
                                    onClick = {
                                        viewModel.updateHavaDurumu(key)
                                        viewModel.setHavaMenuOpen(false)
                                    }
                                )
                            }
                        }
                    }

                    // Toggles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(S.seatedToggle(lang), style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = curOtur,
                            onCheckedChange = { viewModel.updateOturabildim(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(S.ticketControl(lang), style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = curBilet,
                            onCheckedChange = { viewModel.updateBiletKontrolu(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }

                    var profileDropdownExpanded by remember { mutableStateOf(false) }
                    val curProfileId = state.segmentProfileId[state.selectedSegmentIndex] ?: ""
                    val curProfile = state.activeProfiles.find { it.id == curProfileId }
                    val profileLabel = curProfile?.displayName ?: S.profileNone(lang)

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { profileDropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("👤 ${S.profileSelectionLabel(lang)}: $profileLabel", style = MaterialTheme.typography.bodyMedium)
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = profileDropdownExpanded,
                            onDismissRequest = { profileDropdownExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(S.profileNone(lang)) },
                                onClick = {
                                    viewModel.updateSegmentProfile(state.selectedSegmentIndex, "")
                                    profileDropdownExpanded = false
                                }
                            )
                            state.activeProfiles.forEach { profile ->
                                DropdownMenuItem(
                                    text = { Text(profile.displayName) },
                                    onClick = {
                                        viewModel.updateSegmentProfile(state.selectedSegmentIndex, profile.id)
                                        profileDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    if (curProfileId.isNotEmpty()) {
                        val curSeatmateNote = state.segmentSeatmateNote[state.selectedSegmentIndex] ?: ""
                        OutlinedTextField(
                            value = curSeatmateNote,
                            onValueChange = { viewModel.updateSegmentSeatmateNote(state.selectedSegmentIndex, it) },
                            label = { Text("📝 ${S.profileSeatmateNoteLabel(lang)}") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    OutlinedTextField(
                        value = curNote,
                        onValueChange = { viewModel.updateNote(it) },
                        label = { Text(S.noteOptional(lang)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
}

@Composable
internal fun SaveClearActionsSection(
    state: RmvLogUiState,
    viewModel: RmvLogViewModel,
    lang: AppLanguage,
    onSaveClick: () -> Unit
) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    enabled = (state.trip != null),
                    onClick = onSaveClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    val btnText = if (state.segmentIds.isEmpty()) S.saveToSheets(lang) else S.updateRecord(lang)
                    Text(btnText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                OutlinedButton(
                    onClick = { viewModel.clearForm() },
                    modifier = Modifier.weight(0.5f),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    Text(S.clearFormButton(lang), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
}

@Composable
internal fun RmvStatusCard(
    state: RmvLogUiState,
    lang: AppLanguage
) {
            // --- DURUM KARTI ---
            val statusColor = when {
                state.status.contains("✅") -> SuccessGreen
                state.status.contains("Hata") || state.status.contains("Fehler") || state.status.contains("Error") -> ErrorRed
                state.status.contains("...") -> WarningAmber
                else -> MaterialTheme.colorScheme.secondary
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Column {
                        Text(S.statusLabel(lang), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                        Text(state.status, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
}
