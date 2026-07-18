package com.example.toplutasima.ui.screens.rmvlog

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import com.example.toplutasima.model.VehicleType
import com.example.toplutasima.transit.TransitFeatureFlags
import com.example.toplutasima.transit.provenance.TransitFieldProvenanceUseCase
import com.example.toplutasima.ui.ErrorRed
import com.example.toplutasima.ui.S
import com.example.toplutasima.ui.SuccessGreen
import com.example.toplutasima.ui.WarningAmber
import com.example.toplutasima.ui.components.TimeVisualTransformation
import com.example.toplutasima.ui.components.transit.TransitSourceBadge
import com.example.toplutasima.ui.util.withoutEmojiCharacters
import com.example.toplutasima.viewmodel.rmvlog.RmvLogUiState

// ── Manual Log Form Composable ──
@Composable
internal fun ManualLogForm(
    state: RmvLogUiState,
    viewModel: com.example.toplutasima.viewmodel.RmvLogViewModel,
    lang: com.example.toplutasima.ui.AppLanguage
) {
    val provenanceUseCase = remember { TransitFieldProvenanceUseCase() }
    val manualEditedAt = remember(state.manual) { System.currentTimeMillis() }
    // 1. Tarih & Araç Card
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(S.dateTime(lang) + " & " + S.vehicleTypes(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (TransitFeatureFlags.PROVENANCE_BADGES) {
                TransitSourceBadge(
                    provenance = provenanceUseCase.manual(
                        fieldId = "manual-transit-record",
                        editedAtEpochMillis = manualEditedAt
                    ),
                    lang = lang,
                    fieldLabel = S.statusLabel(lang)
                )
            }
            
            OutlinedTextField(
                value = state.date,
                onValueChange = { viewModel.updateDate(it) },
                label = { Text(S.date(lang)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Box {
                FilledTonalButton(
                    onClick = { viewModel.setManualTypeMenuOpen(true) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(S.vehicleTypeName(state.manualTypeTr, lang), fontWeight = FontWeight.SemiBold)
                }
                DropdownMenu(expanded = state.manualTypeMenuOpen, onDismissRequest = { viewModel.setManualTypeMenuOpen(false) }) {
                    VehicleType.allKeys.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(S.vehicleTypeName(type, lang)) },
                            onClick = { viewModel.updateManualField("type", type) }
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.manualLine, onValueChange = { viewModel.updateManualField("line", it) },
                    label = { Text(S.colLine(lang)) }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = state.manualDirection, onValueChange = { viewModel.updateManualField("direction", it) },
                    label = { Text(S.directionLabel(lang)) }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp)
                )
            }
        }
    }

    // 2. Duraklar & Saatler
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(S.stopSelection(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            
            OutlinedTextField(
                value = state.manualBoardingStop, onValueChange = { viewModel.updateManualField("boardingStop", it) },
                label = { Text(S.boardingStop(lang)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = state.manualAlightingStop, onValueChange = { viewModel.updateManualField("alightingStop", it) },
                label = { Text(S.alightingStop(lang)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp)
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.manualPlannedDep, onValueChange = { viewModel.updateManualField("plannedDep", it) },
                    label = { Text("Plan. ${S.departure(lang)}") }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), visualTransformation = TimeVisualTransformation()
                )
                OutlinedTextField(
                    value = state.manualActualDep, onValueChange = { viewModel.updateManualField("actualDep", it) },
                    label = { Text("Gerçek ${S.departure(lang)}") }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), visualTransformation = TimeVisualTransformation()
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.manualPlannedArr, onValueChange = { viewModel.updateManualField("plannedArr", it) },
                    label = { Text("Plan. ${S.arrival(lang)}") }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), visualTransformation = TimeVisualTransformation()
                )
                OutlinedTextField(
                    value = state.manualActualArr, onValueChange = { viewModel.updateManualField("actualArr", it) },
                    label = { Text("Gerçek ${S.arrival(lang)}") }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), visualTransformation = TimeVisualTransformation()
                )
            }
        }
    }

    // 3. Ekstra
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(S.additionalInfo(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.manualDistance, onValueChange = { viewModel.updateManualField("distance", it) },
                    label = { Text(S.colDistance(lang)) }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = state.manualStopCount, onValueChange = { viewModel.updateManualField("stopCount", it) },
                    label = { Text(S.colStops(lang)) }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            Box {
                FilledTonalButton(
                    onClick = { viewModel.setManualWeatherMenuOpen(true) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Text("${S.weatherLabel(lang)}: ${S.weatherName(state.manualWeather, lang)}", fontWeight = FontWeight.SemiBold)
                }
                DropdownMenu(expanded = state.manualWeatherMenuOpen, onDismissRequest = { viewModel.setManualWeatherMenuOpen(false) }) {
                    S.weatherOptions.forEach { (key, _) ->
                        DropdownMenuItem(
                            text = { Text(S.weatherName(key, lang)) },
                            onClick = { viewModel.updateManualField("weather", key) }
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(S.seatedToggle(lang), style = MaterialTheme.typography.bodyMedium)
                Switch(checked = state.manualOturabildim, onCheckedChange = { viewModel.updateManualOtur(it) })
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(S.ticketControl(lang), style = MaterialTheme.typography.bodyMedium)
                Switch(checked = state.manualBiletKontrolu, onCheckedChange = { viewModel.updateManualBilet(it) })
            }

            var profileDropdownExpanded by remember { mutableStateOf(false) }
            val curProfileId = state.manual.profileId
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
                        Text("${S.profileSelectionLabel(lang)}: $profileLabel", style = MaterialTheme.typography.bodyMedium)
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
                            viewModel.updateManualField("profileId", "")
                            profileDropdownExpanded = false
                        }
                    )
                    state.activeProfiles.forEach { profile ->
                        DropdownMenuItem(
                            text = { Text(profile.displayName) },
                            onClick = {
                                viewModel.updateManualField("profileId", profile.id)
                                profileDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            if (curProfileId.isNotEmpty()) {
                OutlinedTextField(
                    value = state.manual.seatmateNote,
                    onValueChange = { viewModel.updateManualField("seatmateNote", it) },
                    label = { Text(S.profileSeatmateNoteLabel(lang)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            OutlinedTextField(
                value = state.manualNote, onValueChange = { viewModel.updateManualField("note", it) },
                label = { Text(S.noteOptional(lang)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
            )
        }
    }

    // 4. Buttons
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { viewModel.saveManualRecord() },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(vertical = 14.dp),
            enabled = state.manualLine.isNotBlank() && state.manualBoardingStop.isNotBlank() && state.manualAlightingStop.isNotBlank()
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

    // Status
    val cleanStatus = state.status.withoutEmojiCharacters().ifBlank { S.statusReady(lang) }
    val statusColor = when {
        state.status.contains("Hata") || state.status.contains("Fehler") || state.status.contains("Error") -> ErrorRed
        state.status.contains("edilemedi", ignoreCase = true) -> ErrorRed
        state.status.contains("kayded", ignoreCase = true) -> SuccessGreen
        state.status.contains("başar", ignoreCase = true) -> SuccessGreen
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
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(statusColor))
            Column {
                Text(S.statusLabel(lang), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                Text(cleanStatus, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
