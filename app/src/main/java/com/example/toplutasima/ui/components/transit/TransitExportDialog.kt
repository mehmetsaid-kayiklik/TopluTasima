package com.example.toplutasima.ui.components.transit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.toplutasima.transit.export.TransitExportFormat
import com.example.toplutasima.transit.export.TransitExportScopeType
import com.example.toplutasima.transit.export.TransitExportSection
import com.example.toplutasima.viewmodel.records.TransitExportUiOptions
import java.time.LocalDate

@Composable
fun TransitExportDialog(
    selectedMonthAvailable: Boolean,
    insightsAvailable: Boolean,
    healthAvailable: Boolean,
    onDismiss: () -> Unit,
    onExport: (TransitExportUiOptions) -> Unit
) {
    var format by remember { mutableStateOf(TransitExportFormat.CSV) }
    var scope by remember {
        mutableStateOf(
            if (selectedMonthAvailable) TransitExportScopeType.SELECTED_MONTH
            else TransitExportScopeType.ALL_TRANSIT
        )
    }
    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }
    var sections by remember {
        mutableStateOf<Set<TransitExportSection>>(
            setOf(
                TransitExportSection.RECORDS,
                TransitExportSection.SUMMARY,
                TransitExportSection.DATA_HEALTH
            ).filterTo(linkedSetOf()) { it != TransitExportSection.DATA_HEALTH || healthAvailable }
        )
    }
    val validRange = scope != TransitExportScopeType.DATE_RANGE || runCatching {
        !LocalDate.parse(endDate).isBefore(LocalDate.parse(startDate))
    }.getOrDefault(false)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transit verilerini dışa aktar") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Biçim")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TransitExportFormat.entries.forEach { value ->
                        FilterChip(
                            selected = format == value,
                            onClick = { format = value },
                            label = { Text(value.name) }
                        )
                    }
                }
                Text("Kapsam")
                TransitExportScopeType.entries.forEach { value ->
                    if (value != TransitExportScopeType.SELECTED_MONTH || selectedMonthAvailable) {
                        FilterChip(
                            selected = scope == value,
                            onClick = { scope = value },
                            label = {
                                Text(
                                    when (value) {
                                        TransitExportScopeType.SELECTED_MONTH -> "Seçili ay"
                                        TransitExportScopeType.DATE_RANGE -> "Özel tarih aralığı"
                                        TransitExportScopeType.ALL_TRANSIT -> "Tüm transit kayıtları"
                                        TransitExportScopeType.FILTERED -> "Yalnız filtrelenmiş kayıtlar"
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                if (scope == TransitExportScopeType.DATE_RANGE) {
                    OutlinedTextField(
                        value = startDate,
                        onValueChange = { startDate = it.trim() },
                        label = { Text("Başlangıç (YYYY-MM-DD)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = endDate,
                        onValueChange = { endDate = it.trim() },
                        label = { Text("Bitiş (YYYY-MM-DD)") },
                        singleLine = true,
                        isError = !validRange
                    )
                }
                Text("İçerik")
                val availableSections = buildList {
                    add(TransitExportSection.RECORDS to "Kayıtlar")
                    add(TransitExportSection.SUMMARY to "Summary özeti")
                    if (insightsAvailable) add(TransitExportSection.INSIGHTS to "Akıllı içgörüler")
                    if (healthAvailable) add(TransitExportSection.DATA_HEALTH to "Veri sağlığı özeti")
                }
                availableSections.forEach { (section, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                contentDescription = "$label, ${if (section in sections) "seçili" else "seçili değil"}"
                            },
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(label, modifier = Modifier.padding(top = 12.dp))
                        Checkbox(
                            checked = section in sections,
                            onCheckedChange = { checked ->
                                sections = if (checked) sections + section else sections - section
                            }
                        )
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
        confirmButton = {
            TextButton(
                enabled = sections.isNotEmpty() && validRange,
                onClick = {
                    onExport(
                        TransitExportUiOptions(
                            format = format,
                            scopeType = scope,
                            startDateIso = startDate.takeIf { it.isNotBlank() },
                            endDateIso = endDate.takeIf { it.isNotBlank() },
                            sections = sections
                        )
                    )
                }
            ) { Text("Dosya seç") }
        }
    )
}
