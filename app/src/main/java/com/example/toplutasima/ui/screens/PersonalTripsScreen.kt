package com.example.toplutasima.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.S
import com.example.toplutasima.ui.components.AddPersonalTripDialog
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

                // Sürücü seçimi
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(S.personalDriver(lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(null to "—", true to "✅ Sürücü", false to "🪑 Yolcu").forEach { (v, lbl) ->
                            FilterChip(
                                selected = uiState.formSurucu == v,
                                onClick = { viewModel.updateFormSurucu(v) },
                                label = { Text(lbl, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                // Yolcu sayısı (sadece yolcu modunda)
                AnimatedVisibility(visible = uiState.formSurucu == false) {
                    OutlinedTextField(
                        value = uiState.formYolcuSayisi,
                        onValueChange = { viewModel.updateFormField("yolcuSayisi", it.filter { c -> c.isDigit() }.take(2)) },
                        label = { Text(S.personalPassengerCount(lang)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
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
                    Text("💾  ${S.save(lang)}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }

        // ── Durum Mesajı ──────────────────────────────────────────────────────
        if (uiState.statusMessage.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(uiState.statusMessage, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { viewModel.clearStatusMessage() }) { Text("✕", fontSize = 14.sp) }
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

        // ── Kayıtlar sekmesine yönlendirme ipucu ────────────────────────────
        Text(
            "📋  ${S.personalRecordsHint(lang)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )

        Spacer(Modifier.height(24.dp))
    }

    // Düzenleme diyaloğu
    if (uiState.showAddDialog) {
        AddPersonalTripDialog(
            editingTrip = uiState.editingTrip,
            lang = lang,
            viewModel = viewModel,
            onDismiss = { viewModel.closeDialog() }
        )
    }
}
