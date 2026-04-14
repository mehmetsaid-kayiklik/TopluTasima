package com.example.toplutasima.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toplutasima.model.PersonalTrip
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.S
import com.example.toplutasima.viewmodel.PersonalTripViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Yeni kişisel biniş eklemek veya mevcut kaydı düzenlemek için BottomSheet.
 * Kalkış/varış konumu veya saati bu formda girilmez — bunlar Bindim/İndim ile alınır.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPersonalTripDialog(
    editingTrip: PersonalTrip?,
    lang: AppLanguage,
    viewModel: PersonalTripViewModel,
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

    var aracMenuOpen  by remember { mutableStateOf(false) }
    var havaMenuOpen  by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Başlık
            Text(
                if (isEditing) S.editEdit(lang) else S.personalAdd(lang),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            HorizontalDivider()

            // ── Araç Türü ───────────────────────────────────────────────────
            Text(S.personalVehicleType(lang), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Box {
                val selectedEmoji = S.personalVehicleOptions.find { it.first == aracTuru }?.second ?: "🚗"
                OutlinedButton(
                    onClick = { aracMenuOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("$selectedEmoji  $aracTuru", fontWeight = FontWeight.SemiBold)
                }
                DropdownMenu(expanded = aracMenuOpen, onDismissRequest = { aracMenuOpen = false }) {
                    S.personalVehicleOptions.forEach { (name, emoji) ->
                        DropdownMenuItem(
                            text = { Text("$emoji  $name", fontSize = 15.sp) },
                            onClick = { aracTuru = name; aracMenuOpen = false }
                        )
                    }
                }
            }

            // ── Plaka ───────────────────────────────────────────────────────
            OutlinedTextField(
                value = plaka,
                onValueChange = { plaka = it.uppercase() },
                label = { Text(S.personalPlate(lang)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                singleLine = true,
                placeholder = { Text("34 ABC 123", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            )

            // ── Hava Durumu ─────────────────────────────────────────────────
            Text(S.weatherLabel(lang), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Box {
                val havaEmoji = S.weatherOptions.find { it.first == havaDurumu }?.second ?: "❓"
                FilledTonalButton(
                    onClick = { havaMenuOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("$havaEmoji  ${S.weatherName(havaDurumu, lang)}")
                }
                DropdownMenu(expanded = havaMenuOpen, onDismissRequest = { havaMenuOpen = false }) {
                    S.weatherOptions.forEach { (key, emoji) ->
                        DropdownMenuItem(
                            text = { Text("$emoji  ${S.weatherName(key, lang)}") },
                            onClick = { havaDurumu = key; havaMenuOpen = false }
                        )
                    }
                }
            }

            // ── Tarih ───────────────────────────────────────────────────────
            OutlinedTextField(
                value = tarih,
                onValueChange = { tarih = it },
                label = { Text(S.date(lang)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                placeholder = { Text("GG.AA.YYYY") }
            )



            // ── Not ─────────────────────────────────────────────────────────
            OutlinedTextField(
                value = not,
                onValueChange = { not = it },
                label = { Text(S.noteOptional(lang)) },
                modifier = Modifier.fillMaxWidth().height(100.dp),
                shape = RoundedCornerShape(12.dp),
                maxLines = 4
            )

            // ── Kaydet ─────────────────────────────────────────────────────
            Button(
                onClick = {
                    if (isEditing) {
                        viewModel.updateTrip(
                            editingTrip!!.copy(
                                aracTuru = aracTuru,
                                plaka = plaka.uppercase(),
                                havaDurumu = havaDurumu,
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
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(vertical = 14.dp),
                enabled = aracTuru.isNotBlank() && tarih.isNotBlank()
            ) {
                Text(
                    if (isEditing) S.save(lang) else S.personalAdd(lang),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
