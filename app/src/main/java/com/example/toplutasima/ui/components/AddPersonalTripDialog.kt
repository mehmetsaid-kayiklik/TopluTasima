package com.example.toplutasima.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import com.example.toplutasima.model.PersonalTrip
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.S
import com.example.toplutasima.viewmodel.PersonalTripViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Kişisel araç kaydını düzenlemek veya yeni kayıt eklemek için AlertDialog.
 * Toplu taşıma düzenleme dialog'u ile aynı UX kalıbını kullanır.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPersonalTripDialog(
    editingTrip: PersonalTrip?,
    lang: AppLanguage,
    viewModel: PersonalTripViewModel,
    readyPlates: List<String> = emptyList(),
    onDismiss: () -> Unit
) {
    val todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
    val isEditing = (editingTrip != null)

    var aracTuru    by remember { mutableStateOf(editingTrip?.aracTuru ?: S.personalVehicleOptions.first().first) }
    var plaka       by remember { mutableStateOf(editingTrip?.plaka ?: "") }
    var havaDurumu  by remember { mutableStateOf(editingTrip?.havaDurumu ?: "Bilinmiyor") }
    var tarih       by remember { mutableStateOf(editingTrip?.tarih ?: todayStr) }
    var surucu      by remember { mutableStateOf(editingTrip?.surucu) }
    var yolcuSayisi by remember { mutableStateOf(editingTrip?.yolcuSayisi?.toString() ?: "") }
    var not         by remember { mutableStateOf(editingTrip?.not ?: "") }
    var kaldigiSaat by remember { mutableStateOf(editingTrip?.kaldigiSaat ?: "") }
    var varisSaat   by remember { mutableStateOf(editingTrip?.varisSaat ?: "") }

    var aracMenuOpen  by remember { mutableStateOf(false) }
    var havaMenuOpen  by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isEditing) S.editEdit(lang) else S.personalAdd(lang),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // ── Araç Türü ───────────────────────────────────────────
                Text(S.personalVehicleType(lang), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Box {
                    OutlinedButton(
                        onClick = { aracMenuOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(aracTuru, fontWeight = FontWeight.SemiBold)
                    }
                    DropdownMenu(expanded = aracMenuOpen, onDismissRequest = { aracMenuOpen = false }) {
                        S.personalVehicleOptions.forEach { (name, _) ->
                            DropdownMenuItem(
                                text = { Text(name, fontSize = 15.sp) },
                                onClick = { aracTuru = name; aracMenuOpen = false }
                            )
                        }
                    }
                }

                // ── Plaka ───────────────────────────────────────────────
                OutlinedTextField(
                    value = plaka,
                    onValueChange = { plaka = it.uppercase() },
                    label = { Text(S.personalPlate(lang)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    singleLine = true,
                    placeholder = { Text("34 ABC 123", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                )

                if (readyPlates.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        readyPlates.forEach { plate ->
                            AssistChip(
                                onClick = { plaka = plate },
                                label = { Text(plate, fontSize = 12.sp, maxLines = 1) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }

                // ── Tarih ───────────────────────────────────────────────
                OutlinedTextField(
                    value = tarih,
                    onValueChange = { tarih = it },
                    label = { Text(S.date(lang)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    placeholder = { Text("GG.AA.YYYY") }
                )

                // ── Kalkış / Varış Saati ─────────────────────────────────
                if (isEditing) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = kaldigiSaat,
                            onValueChange = { kaldigiSaat = it },
                            label = { Text(S.personalDepartureTime(lang)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            placeholder = { Text("HH:mm") }
                        )
                        OutlinedTextField(
                            value = varisSaat,
                            onValueChange = { varisSaat = it },
                            label = { Text(S.personalArrivalTime(lang)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            placeholder = { Text("HH:mm") }
                        )
                    }

                    // Kalkış / Varış yeri (sadece göster, read-only)
                    if (editingTrip?.kaldigiYer?.isNotBlank() == true) {
                        Text(
                            editingTrip.kaldigiYer,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (editingTrip?.varisYeri?.isNotBlank() == true) {
                        Text(
                            editingTrip.varisYeri,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                }

                // ── Hava Durumu ─────────────────────────────────────────
                Text(S.weatherLabel(lang), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Box {
                    FilledTonalButton(
                        onClick = { havaMenuOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Text(S.weatherName(havaDurumu, lang), fontWeight = FontWeight.SemiBold)
                    }
                    DropdownMenu(expanded = havaMenuOpen, onDismissRequest = { havaMenuOpen = false }) {
                        S.weatherOptions.forEach { (key, _) ->
                            DropdownMenuItem(
                                text = { Text(S.weatherName(key, lang)) },
                                onClick = { havaDurumu = key; havaMenuOpen = false }
                            )
                        }
                    }
                }

                // ── Not ─────────────────────────────────────────────────
                OutlinedTextField(
                    value = not,
                    onValueChange = { not = it },
                    label = { Text(S.noteOptional(lang)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sil butonu (sadece düzenlemede)
                if (isEditing && editingTrip != null) {
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text(S.delete(lang)) }
                }
                // Kaydet
                Button(
                    onClick = {
                        if (isEditing && editingTrip != null) {
                            viewModel.updateTrip(
                                editingTrip.copy(
                                    aracTuru = aracTuru,
                                    plaka = plaka.uppercase(),
                                    havaDurumu = havaDurumu,
                                    tarih = tarih,
                                    kaldigiSaat = kaldigiSaat,
                                    varisSaat = varisSaat,
                                    surucu = surucu,
                                    yolcuSayisi = yolcuSayisi.toIntOrNull(),
                                    not = not
                                )
                            )
                        } else {
                            viewModel.saveDraft(
                                aracTuru = aracTuru,
                                plaka = plaka.uppercase(),
                                havaDurumu = havaDurumu,
                                tarih = tarih,
                                surucu = surucu,
                                yolcuSayisi = yolcuSayisi.toIntOrNull(),
                                not = not
                            )
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    enabled = aracTuru.isNotBlank() && tarih.isNotBlank()
                ) {
                    Text(S.save(lang), fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(S.cancel(lang)) }
        }
    )

    // ── Silme onay diyaloğu ──────────────────────────────────────────────
    if (showDeleteConfirm && editingTrip != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(S.delete(lang), fontWeight = FontWeight.Bold) },
            text = { Text(S.personalDeleteConfirm(lang)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteTrip(editingTrip.firestoreDocId)
                        onDismiss()
                    }
                ) { Text(S.delete(lang), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(S.cancel(lang)) }
            }
        )
    }
}
