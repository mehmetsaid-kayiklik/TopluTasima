package com.example.toplutasima.ui.screens.records

import android.app.DatePickerDialog
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toplutasima.model.SeatingStatus
import com.example.toplutasima.model.TicketStatus
import com.example.toplutasima.ui.S
import com.example.toplutasima.usecase.TransitRecordCalculations
import com.example.toplutasima.usecase.TransitTimeUtils

// ── Edit Dialog ──
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditRecordDialog(
    record: Map<String, Any>,
    lang: com.example.toplutasima.ui.AppLanguage,
    isSaving: Boolean,
    activeProfiles: List<com.example.toplutasima.data.local.entity.ProfileEntity>,
    onDismiss: () -> Unit,
    onSave: (String, Map<String, Any?>, String?, String?) -> Unit,
    onDelete: () -> Unit,
    onRestore: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val docId = record["firestoreDocId"]?.toString() ?: ""
    if (com.example.toplutasima.BuildConfig.DEBUG) {
        Log.d("EditDialog", "docId='$docId' record keys=${record.keys}")
    }

    // BUG 3: Editable date with derived gun / gununTipi
    var tarih by remember(record) { mutableStateOf(record["tarih"]?.toString() ?: "") }
    var gun by remember(record) { mutableStateOf(record["gun"]?.toString() ?: "") }
    var gununTipi by remember(record) { mutableStateOf(record["gununTipi"]?.toString() ?: "") }

    var binisDuragi by remember(record) { mutableStateOf(record["binisDuragi"]?.toString() ?: "") }
    var inisDuragi by remember(record) { mutableStateOf(record["inisDuragi"]?.toString() ?: "") }
    // BUG 4: Strip seconds from time fields
    var planlananBinis by remember(record) { mutableStateOf(TransitTimeUtils.stripSeconds(record["planlananBinis"]?.toString() ?: "")) }
    var gercekBinis by remember(record) { mutableStateOf(TransitTimeUtils.stripSeconds(record["gercekBinis"]?.toString() ?: "")) }
    var planlananInis by remember(record) { mutableStateOf(TransitTimeUtils.stripSeconds(record["planlananInis"]?.toString() ?: "")) }
    var gercekInis by remember(record) { mutableStateOf(TransitTimeUtils.stripSeconds(record["gercekInis"]?.toString() ?: "")) }
    var havaDurumu by remember(record) { mutableStateOf(record["havaDurumu"]?.toString() ?: "") }
    var mesafe by remember(record) { mutableStateOf(record["mesafe"]?.toString() ?: "") }
    var durakSayisi by remember(record) { mutableStateOf(record["durakSayisi"]?.toString() ?: "") }
    var not by remember(record) { mutableStateOf(record["not"]?.toString() ?: "") }
    var oturabildim by remember(record) {
        mutableStateOf(record["oturabildimMi"]?.toString() == SeatingStatus.YES.key)
    }
    var biletKontrolu by remember(record) {
        mutableStateOf(record["biletKontrolü"]?.toString() == TicketStatus.HAPPENED.key)
    }
    var profileId by remember(record) { mutableStateOf(record["profileId"]?.toString() ?: "") }
    var seatmateNote by remember(record) { mutableStateOf(record["seatmateNote"]?.toString() ?: "") }

    // Helper to open DatePicker
    fun openDatePicker() {
        val parts = tarih.split(".")
        val day = parts.getOrNull(0)?.toIntOrNull() ?: 1
        val month = parts.getOrNull(1)?.toIntOrNull() ?: 1
        val year = parts.getOrNull(2)?.toIntOrNull() ?: 2024
        DatePickerDialog(context, { _, y, m, d ->
            val newDate = String.format(java.util.Locale.US, "%02d.%02d.%04d", d, m + 1, y)
            tarih = newDate
            gun = TransitRecordCalculations.computeGun(newDate)
            gununTipi = TransitRecordCalculations.computeGununTipi(newDate)
        }, year, month - 1, day).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(modifier = Modifier.clickable { openDatePicker() }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${S.editRecord(lang)}: $tarih",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("📅", style = MaterialTheme.typography.titleMedium)
                }
                if (gun.isNotBlank()) {
                    Text(
                        "$gun • $gununTipi",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Info row (read only)
                Text(
                    "${record["tur"] ?: ""} • ${record["hat"] ?: ""} • ${record["yon"] ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider()

                OutlinedTextField(
                    value = binisDuragi,
                    onValueChange = { binisDuragi = it },
                    label = { Text(S.boardingStop(lang)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = inisDuragi,
                    onValueChange = { inisDuragi = it },
                    label = { Text(S.alightingStop(lang)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = planlananBinis,
                        onValueChange = { planlananBinis = it },
                        label = { Text("Plan. ${S.departure(lang)}") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = gercekBinis,
                        onValueChange = { gercekBinis = it },
                        label = { Text("Gerçek ${S.departure(lang)}") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = planlananInis,
                        onValueChange = { planlananInis = it },
                        label = { Text("Plan. ${S.arrival(lang)}") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = gercekInis,
                        onValueChange = { gercekInis = it },
                        label = { Text("Gerçek ${S.arrival(lang)}") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                // Weather Dropdown
                var weatherExpanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = weatherExpanded,
                    onExpandedChange = { weatherExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val currentDisplay = if (havaDurumu.isNotBlank()) {
                        val emoji = S.weatherOptions.find { it.first == havaDurumu }?.second ?: "❓"
                        "$emoji ${S.weatherName(havaDurumu, lang)}"
                    } else ""

                    OutlinedTextField(
                        value = currentDisplay,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(S.weatherLabel(lang)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = weatherExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = weatherExpanded,
                        onDismissRequest = { weatherExpanded = false }
                    ) {
                        S.weatherOptions.forEach { (optionKey, optionEmoji) ->
                            DropdownMenuItem(
                                text = { Text("$optionEmoji ${S.weatherName(optionKey, lang)}") },
                                onClick = {
                                    havaDurumu = optionKey
                                    weatherExpanded = false
                                }
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = mesafe,
                        onValueChange = { mesafe = it },
                        label = { Text("Mesafe") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = durakSayisi,
                        onValueChange = { durakSayisi = it },
                        label = { Text("Durak Sayısı") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                // Profile Selection Dropdown
                var profileExpanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = profileExpanded,
                    onExpandedChange = { profileExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val currentProfile = activeProfiles.find { it.id == profileId }
                    val currentDisplay = currentProfile?.displayName ?: S.profileNone(lang)

                    OutlinedTextField(
                        value = currentDisplay,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("👤 " + S.profileSelectionLabel(lang)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = profileExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = profileExpanded,
                        onDismissRequest = { profileExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(S.profileNone(lang)) },
                            onClick = {
                                profileId = ""
                                profileExpanded = false
                            }
                        )
                        activeProfiles.forEach { profile ->
                            DropdownMenuItem(
                                text = { Text(profile.displayName) },
                                onClick = {
                                    profileId = profile.id
                                    profileExpanded = false
                                }
                            )
                        }
                    }
                }

                if (profileId.isNotEmpty()) {
                    OutlinedTextField(
                        value = seatmateNote,
                        onValueChange = { seatmateNote = it },
                        label = { Text("📝 " + S.profileSeatmateNoteLabel(lang)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                OutlinedTextField(
                    value = not,
                    onValueChange = { not = it },
                    label = { Text(S.noteOptional(lang)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(S.seatedToggle(lang), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = oturabildim, onCheckedChange = { oturabildim = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(S.ticketControl(lang), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = biletKontrolu, onCheckedChange = { biletKontrolu = it })
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                // Restore button (icon only to save space)
                if (onRestore != null) {
                    IconButton(onClick = onRestore) { Text("🔄", fontSize = 18.sp) }
                }

                // Delete button
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    enabled = !isSaving
                ) { Text(S.deleteRecord(lang)) }

                // Save button
                Button(
                    onClick = {
                        val fields = mapOf<String, Any?>(
                            "tarih" to tarih,
                            "gun" to gun,
                            "gununTipi" to gununTipi,
                            "binisDuragi" to binisDuragi,
                            "inisDuragi" to inisDuragi,
                            "planlananBinis" to planlananBinis,
                            "gercekBinis" to gercekBinis,
                            "planlananInis" to planlananInis,
                            "gercekInis" to gercekInis,
                            "havaDurumu" to havaDurumu,
                            "mesafe" to mesafe,
                            "durakSayisi" to durakSayisi,
                            "not" to not,
                            "oturabildimMi" to SeatingStatus.fromBoolean(oturabildim).key,
                            "biletKontrolü" to TicketStatus.fromBoolean(biletKontrolu).key
                        )
                        onSave(docId, fields, profileId, seatmateNote)
                    },
                    enabled = !isSaving && docId.isNotBlank(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(S.save(lang))
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(S.cancel(lang)) }
        }
    )
}

@Composable
internal fun DeleteRecordConfirmDialog(
    lang: com.example.toplutasima.ui.AppLanguage,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(S.deleteRecord(lang)) },
        text = { Text(S.deleteConfirm(lang)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text(S.deleteRecord(lang)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(S.cancel(lang))
            }
        }
    )
}
