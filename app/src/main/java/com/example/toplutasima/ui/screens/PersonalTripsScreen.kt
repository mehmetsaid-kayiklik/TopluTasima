package com.example.toplutasima.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toplutasima.model.PersonalTrip
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.S
import com.example.toplutasima.ui.SuccessGreen
import com.example.toplutasima.ui.WarningAmber
import com.example.toplutasima.ui.components.AddPersonalTripDialog
import com.example.toplutasima.ui.components.PersonalTripCard
import com.example.toplutasima.viewmodel.PersonalTripUiState
import com.example.toplutasima.viewmodel.PersonalTripViewModel

/**
 * Logging ekranındaki kişisel araç formu — sadece yeni kayıt girme formu.
 * Kayıt listesi Kayıtlar sekmesinde (RecordsScreen) gösterilir.
 */
@Composable
fun PersonalTripsContent(
    uiState: PersonalTripUiState,
    lang: AppLanguage,
    viewModel: PersonalTripViewModel
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {

        // ── Yeni Biniş Formu ──────────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "🚗  ${S.personalAdd(lang)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // Araç Türü + Plaka
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        val emoji = S.personalVehicleOptions.find { it.first == uiState.formAracTuru }?.second ?: "🚗"
                        OutlinedButton(
                            onClick = { viewModel.updateFormField("aracMenu", "true") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("$emoji  ${uiState.formAracTuru}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                        DropdownMenu(
                            expanded = uiState.formAracMenuOpen,
                            onDismissRequest = { viewModel.updateFormField("aracMenu", "false") }
                        ) {
                            S.personalVehicleOptions.forEach { (name, em) ->
                                DropdownMenuItem(
                                    text = { Text("$em  $name") },
                                    onClick = { viewModel.updateFormField("aracTuru", name) }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = uiState.formPlaka,
                        onValueChange = { viewModel.updateFormField("plaka", it) },
                        label = { Text(S.personalPlate(lang)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        placeholder = { Text("34 ABC 123", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    )
                }

                if (uiState.readyPlates.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        uiState.readyPlates.forEach { plate ->
                            AssistChip(
                                onClick = { viewModel.updateFormField("plaka", plate) },
                                label = { Text(plate, fontSize = 12.sp, maxLines = 1) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }

                // Hava + Tarih
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        val havaEmoji = S.weatherOptions.find { it.first == uiState.formHavaDurumu }?.second ?: "❓"
                        FilledTonalButton(
                            onClick = { viewModel.updateFormField("havaMenu", "true") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("$havaEmoji  ${S.weatherName(uiState.formHavaDurumu, lang)}", fontSize = 13.sp) }
                        DropdownMenu(
                            expanded = uiState.formHavaMenuOpen,
                            onDismissRequest = { viewModel.updateFormField("havaMenu", "false") }
                        ) {
                            S.weatherOptions.forEach { (key, em) ->
                                DropdownMenuItem(
                                    text = { Text("$em  ${S.weatherName(key, lang)}") },
                                    onClick = { viewModel.updateFormField("hava", key) }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = uiState.formTarih,
                        onValueChange = { viewModel.updateFormField("tarih", it) },
                        label = { Text(S.date(lang)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        placeholder = { Text("GG.AA.YYYY") }
                    )
                }



                // Not
                OutlinedTextField(
                    value = uiState.formNot,
                    onValueChange = { viewModel.updateFormField("not", it) },
                    label = { Text(S.noteOptional(lang)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 3
                )

                // Kaydet
                Button(
                    onClick = { viewModel.saveFromInlineForm() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 13.dp),
                    enabled = uiState.formAracTuru.isNotBlank() && uiState.formTarih.isNotBlank()
                ) {
                    Text(S.save(lang), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }

        // ── Durum Kartı — her zaman görünür ──────────────────────────────────
        val displayMsg = uiState.statusMessage.ifBlank { "Hazır" }
        val dotColor = when {
            uiState.statusMessage.isBlank()                          -> SuccessGreen
            uiState.statusMessage.contains("✅")                     -> SuccessGreen
            uiState.statusMessage.contains("...")                    -> WarningAmber
            uiState.statusMessage.contains("aydedil", ignoreCase = true)
                && !uiState.statusMessage.contains("✅")             -> MaterialTheme.colorScheme.error
            else                                                      -> MaterialTheme.colorScheme.primary
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        S.statusLabel(lang),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        displayMsg,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // ── Konum alınıyor ────────────────────────────────────────────────────
        AnimatedVisibility(visible = uiState.isResolvingLocation) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(S.personalLocating(lang), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // ── Aktif / Beklemedeki kayıtlar ─────────────────────────────────────
        val activeTrips = uiState.trips.filter {
            it.durum == PersonalTrip.DURUM_BEKLEMEDE || it.durum == PersonalTrip.DURUM_AKTIF
        }
        activeTrips.forEach { trip ->
            PersonalTripCard(
                trip = trip,
                liveDistanceKm = if (trip.durum == PersonalTrip.DURUM_AKTIF) uiState.liveDistanceKm else 0.0,
                lang = lang,
                viewModel = viewModel
            )
        }

        Spacer(Modifier.height(24.dp))
    }

    // Düzenleme diyaloğu
    if (uiState.showAddDialog) {
        AddPersonalTripDialog(
            editingTrip = uiState.editingTrip,
            lang = lang,
            viewModel = viewModel,
            readyPlates = uiState.readyPlates,
            onDismiss = { viewModel.closeDialog() }
        )
    }
}
