package com.example.toplutasima.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
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
import com.example.toplutasima.ui.components.PlateCountryDropdown
import com.example.toplutasima.ui.util.withoutEmojiCharacters
import com.example.toplutasima.viewmodel.PersonalTripViewModel
import com.example.toplutasima.viewmodel.personaltrip.PersonalTripUiState

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
    val context = LocalContext.current
    val saveAndStartPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            viewModel.saveFromInlineForm(startTracking = true, context = context)
        } else {
            viewModel.noteLocationPermissionRequired()
        }
    }

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
                    S.personalAdd(lang),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // Araç Türü + Plaka
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { viewModel.updateFormField("aracMenu", "true") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text(uiState.formAracTuru, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                        DropdownMenu(
                            expanded = uiState.formAracMenuOpen,
                            onDismissRequest = { viewModel.updateFormField("aracMenu", "false") }
                        ) {
                            S.personalVehicleOptions.forEach { (name, _) ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = { viewModel.updateFormField("aracTuru", name) }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = uiState.formPlaka,
                        onValueChange = { viewModel.updateFormField("plaka", it) },
                        label = { Text(S.personalPlate(lang)) },
                        modifier = Modifier.weight(1.15f),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        placeholder = { Text("34 ABC 123", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    )
                    PlateCountryDropdown(
                        selectedCountry = uiState.formPlakaUlkesi,
                        onCountrySelected = { viewModel.updateFormField("plakaUlkesi", it) },
                        modifier = Modifier.weight(0.75f)
                    )
                }

                if (uiState.readyPlateSuggestions.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        uiState.readyPlateSuggestions.forEach { suggestion ->
                            AssistChip(
                                onClick = { viewModel.selectPlateSuggestion(suggestion) },
                                label = {
                                    Text(
                                        listOf(
                                            suggestion.plaka,
                                            suggestion.normalizedCountry,
                                            suggestion.displayName
                                        ).filter { it.isNotBlank() }.joinToString(" · "),
                                        fontSize = 12.sp,
                                        maxLines = 1
                                    )
                                },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }

                // Hava + Tarih
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        FilledTonalButton(
                            onClick = { viewModel.updateFormField("havaMenu", "true") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Text(
                                S.weatherName(uiState.formHavaDurumu, lang),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        DropdownMenu(
                            expanded = uiState.formHavaMenuOpen,
                            onDismissRequest = { viewModel.updateFormField("havaMenu", "false") }
                        ) {
                            S.weatherOptions.forEach { (key, _) ->
                                DropdownMenuItem(
                                    text = { Text(S.weatherName(key, lang)) },
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
                    onClick = {
                        if (viewModel.hasLocationPermission()) {
                            viewModel.saveFromInlineForm(startTracking = true, context = context)
                        } else {
                            saveAndStartPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    },
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
        val displayMsg = uiState.statusMessage.withoutEmojiCharacters().ifBlank { "Hazır" }
        val dotColor = when {
            uiState.statusMessage.isBlank()                          -> SuccessGreen
            uiState.statusMessage.contains("eklendi", ignoreCase = true) -> SuccessGreen
            uiState.statusMessage.contains("kaydedildi", ignoreCase = true) -> SuccessGreen
            uiState.statusMessage.contains("tamamlandı", ignoreCase = true) -> SuccessGreen
            uiState.statusMessage.contains("...")                    -> WarningAmber
            uiState.statusMessage.contains("edilemedi", ignoreCase = true) -> MaterialTheme.colorScheme.error
            uiState.statusMessage.contains("gerekli", ignoreCase = true) -> MaterialTheme.colorScheme.error
            uiState.statusMessage.contains("zorunlu", ignoreCase = true) -> MaterialTheme.colorScheme.error
            uiState.statusMessage.contains("alınamadı", ignoreCase = true) -> MaterialTheme.colorScheme.error
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
            readyPlateSuggestions = uiState.readyPlateSuggestions,
            onDismiss = { viewModel.closeDialog() }
        )
    }
}
