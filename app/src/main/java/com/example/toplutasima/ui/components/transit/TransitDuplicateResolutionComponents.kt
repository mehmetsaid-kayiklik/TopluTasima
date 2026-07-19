package com.example.toplutasima.ui.components.transit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.toplutasima.data.local.entity.TripEntity
import com.example.toplutasima.domain.transit.health.TransitHealthIssue
import com.example.toplutasima.domain.transit.health.TransitHealthSeverity
import com.example.toplutasima.domain.transit.validation.TransitValidationField
import com.example.toplutasima.domain.transit.validation.ValidationIssueCode
import com.example.toplutasima.transit.duplicate.TransitDuplicateCandidate
import com.example.toplutasima.transit.duplicate.TransitDuplicateMergePreview
import com.example.toplutasima.transit.duplicate.TransitDuplicateMergeSelection
import com.example.toplutasima.transit.duplicate.TransitDuplicateReason
import com.example.toplutasima.transit.duplicate.TransitMergeValueSource
import com.example.toplutasima.transit.provenance.TransitFieldProvenance
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.LocaleManager

/**
 * Presentation-only input for a duplicate candidate. The component never reads a DAO, repository
 * or persistence store. Keeping the entity and its derived metadata together also makes it harder
 * for a caller to accidentally show provenance or health information from the other record.
 */
data class TransitDuplicateRecordUiModel(
    val record: TripEntity,
    val provenanceByField: Map<String, TransitFieldProvenance> = emptyMap(),
    val healthIssues: List<TransitHealthIssue> = emptyList()
)

/** Small transit-only entry point intended for the data-health section. */
@Composable
fun TransitDuplicateOverviewCard(
    candidateCount: Int,
    isLoading: Boolean,
    onReviewCandidates: () -> Unit,
    modifier: Modifier = Modifier,
    lang: AppLanguage = LocaleManager.currentLanguage
) {
    if (candidateCount <= 0 && !isLoading) return
    val summary = TransitDuplicatePresentationText.overview(candidateCount, lang)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = summary },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.padding(2.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Outlined.Warning, contentDescription = null)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        TransitDuplicatePresentationText.overviewTitle(lang),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(summary, style = MaterialTheme.typography.bodySmall)
                }
            }
            Button(
                onClick = onReviewCandidates,
                enabled = candidateCount > 0 && !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(TransitDuplicatePresentationText.reviewCandidates(lang))
            }
        }
    }
}

/**
 * Responsive duplicate comparison. Records are intentionally stacked rather than placed in a
 * fixed two-column table, so long stops and TalkBack focus remain usable on narrow devices.
 * Warning acknowledgement is an explicit second interaction; critical previews cannot be merged.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransitDuplicateResolutionSheet(
    candidate: TransitDuplicateCandidate,
    first: TransitDuplicateRecordUiModel,
    second: TransitDuplicateRecordUiModel,
    selection: TransitDuplicateMergeSelection,
    mergePreview: TransitDuplicateMergePreview?,
    showProvenance: Boolean,
    isWorking: Boolean,
    onSelectField: (fieldId: String, source: TransitMergeValueSource) -> Unit,
    onKeepSeparate: () -> Unit,
    onKeepFirst: () -> Unit,
    onKeepSecond: () -> Unit,
    onMerge: (acknowledgedWarningIds: Set<String>) -> Unit,
    onReviewLater: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    lang: AppLanguage = LocaleManager.currentLanguage
) {
    var confirmWarnings by remember(candidate.decisionFingerprint) { mutableStateOf(false) }
    val signal = TransitDuplicatePresentationText.similaritySignal(
        candidate.similarityScore,
        candidate.reasons.size,
        lang
    )
    val comparisonDescription = TransitDuplicatePresentationText.comparisonDescription(
        first.record,
        second.record,
        signal,
        lang
    )

    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .semantics { contentDescription = comparisonDescription },
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                TransitDuplicatePresentationText.sheetTitle(lang),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            SimilarityEvidence(candidate = candidate, signal = signal, lang = lang)
            DuplicateRecordSnapshot(
                label = TransitDuplicatePresentationText.firstRecord(lang),
                model = first,
                showProvenance = showProvenance,
                lang = lang
            )
            DuplicateRecordSnapshot(
                label = TransitDuplicatePresentationText.secondRecord(lang),
                model = second,
                showProvenance = showProvenance,
                lang = lang
            )
            HorizontalDivider()
            Text(
                TransitDuplicatePresentationText.fieldMergeTitle(lang),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                TransitDuplicatePresentationText.fieldMergeExplanation(lang),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            duplicateFields.forEach { field ->
                DuplicateFieldChoice(
                    field = field,
                    first = first,
                    second = second,
                    selectedSource = selection.valueSourceByField[field.id]
                        ?: TransitMergeValueSource.FIRST,
                    showProvenance = showProvenance,
                    enabled = !isWorking,
                    onSelected = { onSelectField(field.id, it) },
                    lang = lang
                )
            }
            mergePreview?.let { ValidationPreview(it, lang) }
            message?.takeIf { it.isNotBlank() }?.let {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(it, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
            DuplicateActions(
                preview = mergePreview,
                isWorking = isWorking,
                onKeepSeparate = onKeepSeparate,
                onKeepFirst = onKeepFirst,
                onKeepSecond = onKeepSecond,
                onRequestMerge = {
                    if (mergePreview?.pendingWarnings.isNullOrEmpty()) {
                        onMerge(mergePreview?.acknowledgedWarningIds.orEmpty())
                    } else {
                        confirmWarnings = true
                    }
                },
                onReviewLater = onReviewLater,
                lang = lang
            )
        }
    }

    if (confirmWarnings && mergePreview != null) {
        val warningIds = mergePreview.pendingWarnings.mapTo(
            mergePreview.acknowledgedWarningIds.toMutableSet()
        ) { it.id }
        AlertDialog(
            onDismissRequest = { confirmWarnings = false },
            title = { Text(TransitDuplicatePresentationText.warningConfirmationTitle(lang)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(TransitDuplicatePresentationText.warningConfirmationBody(lang))
                    mergePreview.pendingWarnings.forEach {
                        Text("• ${TransitDuplicatePresentationText.validationIssue(it.code, lang)}")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmWarnings = false
                        onMerge(warningIds)
                    }
                ) {
                    Text(TransitDuplicatePresentationText.continueWithWarnings(lang))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmWarnings = false }) {
                    Text(TransitDuplicatePresentationText.cancel(lang))
                }
            }
        )
    }
}

@Composable
private fun SimilarityEvidence(
    candidate: TransitDuplicateCandidate,
    signal: String,
    lang: AppLanguage
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Info, contentDescription = null)
                Text(signal, fontWeight = FontWeight.SemiBold)
            }
            Text(
                TransitDuplicatePresentationText.notCertainty(lang),
                style = MaterialTheme.typography.bodySmall
            )
            candidate.reasons.forEach { reason ->
                Text("• ${TransitDuplicatePresentationText.reason(reason, lang)}")
            }
        }
    }
}

@Composable
private fun DuplicateRecordSnapshot(
    label: String,
    model: TransitDuplicateRecordUiModel,
    showProvenance: Boolean,
    lang: AppLanguage
) {
    val health = TransitDuplicatePresentationText.healthSummary(model.healthIssues, lang)
    Card(
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = "$label. ${TransitDuplicatePresentationText.recordSummary(model.record, lang)}. $health"
        }
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(
                listOfNotNull(model.record.hat, model.record.tur).filter { it.isNotBlank() }.joinToString(" · ")
                    .ifBlank { TransitDuplicatePresentationText.unknown(lang) },
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                "${model.record.binisDuragi.orUnknown(lang)} → ${model.record.inisDuragi.orUnknown(lang)}",
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${model.record.tarih.orUnknown(lang)} · ${model.record.planlananBinis.orUnknown(lang)}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                health,
                style = MaterialTheme.typography.labelSmall,
                color = when (model.healthIssues.maxSeverity()) {
                    TransitHealthSeverity.CRITICAL -> MaterialTheme.colorScheme.error
                    TransitHealthSeverity.WARNING -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.primary
                }
            )
            if (showProvenance && model.provenanceByField.isNotEmpty()) {
                Text(
                    TransitDuplicatePresentationText.provenanceSummary(model.provenanceByField, lang),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DuplicateFieldChoice(
    field: DuplicateField,
    first: TransitDuplicateRecordUiModel,
    second: TransitDuplicateRecordUiModel,
    selectedSource: TransitMergeValueSource,
    showProvenance: Boolean,
    enabled: Boolean,
    onSelected: (TransitMergeValueSource) -> Unit,
    lang: AppLanguage
) {
    val firstValue = field.value(first.record).orUnknown(lang)
    val secondValue = field.value(second.record).orUnknown(lang)
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(field.label(lang), fontWeight = FontWeight.SemiBold)
        DuplicateValueChoice(
            recordLabel = TransitDuplicatePresentationText.firstRecord(lang),
            fieldLabel = field.label(lang),
            value = firstValue,
            provenance = first.provenanceByField[field.id],
            health = first.healthIssues.forField(field.id),
            selected = selectedSource == TransitMergeValueSource.FIRST,
            showProvenance = showProvenance,
            enabled = enabled,
            onClick = { onSelected(TransitMergeValueSource.FIRST) },
            lang = lang
        )
        DuplicateValueChoice(
            recordLabel = TransitDuplicatePresentationText.secondRecord(lang),
            fieldLabel = field.label(lang),
            value = secondValue,
            provenance = second.provenanceByField[field.id],
            health = second.healthIssues.forField(field.id),
            selected = selectedSource == TransitMergeValueSource.SECOND,
            showProvenance = showProvenance,
            enabled = enabled,
            onClick = { onSelected(TransitMergeValueSource.SECOND) },
            lang = lang
        )
    }
}

@Composable
private fun DuplicateValueChoice(
    recordLabel: String,
    fieldLabel: String,
    value: String,
    provenance: TransitFieldProvenance?,
    health: List<TransitHealthIssue>,
    selected: Boolean,
    showProvenance: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    lang: AppLanguage
) {
    val provenanceText = if (showProvenance) {
        provenance?.let {
            "${TransitProvenanceText.source(it.source, lang)}, ${TransitProvenanceText.freshness(it.freshness, lang)}"
        } ?: TransitDuplicatePresentationText.provenanceUnknown(lang)
    } else null
    val healthText = TransitDuplicatePresentationText.healthSummary(health, lang)
    val selectedText = if (selected) {
        TransitDuplicatePresentationText.selected(lang)
    } else {
        TransitDuplicatePresentationText.notSelected(lang)
    }
    val accessibility = listOfNotNull(
        fieldLabel,
        recordLabel,
        value,
        provenanceText,
        healthText,
        selectedText
    ).joinToString(". ")

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick
            )
            .semantics {
                contentDescription = accessibility
                stateDescription = selectedText
            },
        shape = RoundedCornerShape(10.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RadioButton(selected = selected, onClick = null, enabled = enabled)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(recordLabel, style = MaterialTheme.typography.labelMedium)
                Text(value, style = MaterialTheme.typography.bodyMedium)
                provenanceText?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall)
                }
                Text(healthText, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ValidationPreview(preview: TransitDuplicateMergePreview, lang: AppLanguage) {
    if (preview.validationIssues.isEmpty()) return
    val hasCritical = preview.criticalIssues.isNotEmpty()
    Surface(
        color = if (hasCritical) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = TransitDuplicatePresentationText.validationSummary(
                preview.criticalIssues.size,
                preview.pendingWarnings.size,
                lang
            )
        }
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (hasCritical) Icons.Outlined.ErrorOutline else Icons.Outlined.Warning,
                    contentDescription = null
                )
                Text(
                    TransitDuplicatePresentationText.validationSummary(
                        preview.criticalIssues.size,
                        preview.pendingWarnings.size,
                        lang
                    ),
                    fontWeight = FontWeight.SemiBold
                )
            }
            preview.validationIssues.forEach { issue ->
                Text("• ${TransitDuplicatePresentationText.validationIssue(issue.code, lang)}")
            }
        }
    }
}

@Composable
private fun DuplicateActions(
    preview: TransitDuplicateMergePreview?,
    isWorking: Boolean,
    onKeepSeparate: () -> Unit,
    onKeepFirst: () -> Unit,
    onKeepSecond: () -> Unit,
    onRequestMerge: () -> Unit,
    onReviewLater: () -> Unit,
    lang: AppLanguage
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onRequestMerge,
            enabled = !isWorking && preview != null && preview.criticalIssues.isEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (preview?.pendingWarnings.isNullOrEmpty()) {
                    TransitDuplicatePresentationText.mergeSelectedFields(lang)
                } else {
                    TransitDuplicatePresentationText.reviewWarningsAndMerge(lang)
                }
            )
        }
        OutlinedButton(onClick = onKeepFirst, enabled = !isWorking, modifier = Modifier.fillMaxWidth()) {
            Text(TransitDuplicatePresentationText.keepFirst(lang))
        }
        OutlinedButton(onClick = onKeepSecond, enabled = !isWorking, modifier = Modifier.fillMaxWidth()) {
            Text(TransitDuplicatePresentationText.keepSecond(lang))
        }
        TextButton(onClick = onKeepSeparate, enabled = !isWorking, modifier = Modifier.fillMaxWidth()) {
            Text(TransitDuplicatePresentationText.keepSeparate(lang))
        }
        TextButton(onClick = onReviewLater, enabled = !isWorking, modifier = Modifier.fillMaxWidth()) {
            Text(TransitDuplicatePresentationText.reviewLater(lang))
        }
    }
}

private data class DuplicateField(
    val id: String,
    val label: (AppLanguage) -> String,
    val value: (TripEntity) -> String?
)

private val duplicateFields: List<DuplicateField> = listOf(
    duplicateField("tarih", "Tarih", "Datum", "Date") { it.tarih },
    duplicateField("tur", "Kayıt türü", "Eintragstyp", "Record type") { it.tur },
    duplicateField("hat", "Hat", "Linie", "Line") { it.hat },
    duplicateField("yon", "Yön", "Richtung", "Direction") { it.yon },
    duplicateField("binisDuragi", "Biniş durağı", "Einstieg", "Boarding stop") { it.binisDuragi },
    duplicateField("inisDuragi", "İniş durağı", "Ausstieg", "Alighting stop") { it.inisDuragi },
    duplicateField("planlananBinis", "Planlanan kalkış", "Geplante Abfahrt", "Planned departure") { it.planlananBinis },
    duplicateField("planlananInis", "Planlanan varış", "Geplante Ankunft", "Planned arrival") { it.planlananInis },
    duplicateField("gercekBinis", "Gerçek kalkış", "Tatsächliche Abfahrt", "Actual departure") { it.gercekBinis },
    duplicateField("gercekInis", "Gerçek varış", "Tatsächliche Ankunft", "Actual arrival") { it.gercekInis },
    duplicateField("havaDurumu", "Hava durumu", "Wetter", "Weather") { it.havaDurumu },
    duplicateField("oturabildimMi", "Oturma durumu", "Sitzplatz", "Seat status") { it.oturabildimMi },
    duplicateField("biletKontrolü", "Bilet kontrolü", "Fahrkartenkontrolle", "Ticket inspection") { it.biletKontrolu },
    duplicateField("mesafe", "Mesafe", "Distanz", "Distance") { it.mesafe },
    duplicateField("orsMesafeKm", "ORS mesafesi", "ORS-Distanz", "ORS distance") { it.orsMesafeKm?.toString() },
    duplicateField("rmvMesafeKm", "RMV mesafesi", "RMV-Distanz", "RMV distance") { it.rmvMesafeKm?.toString() },
    duplicateField("durakSayisi", "Durak sayısı", "Haltestellenanzahl", "Stop count") { it.durakSayisi },
    duplicateField("not", "Not", "Notiz", "Note") { it.not },
    duplicateField("fromStopId", "Başlangıç durak kimliği", "Start-Haltestellen-ID", "Origin stop ID") { it.fromStopId },
    duplicateField("toStopId", "Varış durak kimliği", "Ziel-Haltestellen-ID", "Destination stop ID") { it.toStopId },
    duplicateField("journeyRef", "RMV yolculuk referansı", "RMV-Fahrtreferenz", "RMV journey reference") { it.journeyRef }
)

private fun duplicateField(
    id: String,
    tr: String,
    de: String,
    en: String,
    value: (TripEntity) -> String?
) = DuplicateField(id = id, label = { lang -> t(lang, tr, de, en) }, value = value)

private fun List<TransitHealthIssue>.forField(fieldId: String): List<TransitHealthIssue> = filter { issue ->
    issue.target.fieldId == fieldId || issue.target.field.toFieldId() == fieldId
}

private fun TransitValidationField.toFieldId(): String? = when (this) {
    TransitValidationField.BOARDING_STOP -> "binisDuragi"
    TransitValidationField.ALIGHTING_STOP -> "inisDuragi"
    TransitValidationField.PLANNED_DEPARTURE -> "planlananBinis"
    TransitValidationField.PLANNED_ARRIVAL -> "planlananInis"
    TransitValidationField.ACTUAL_DEPARTURE -> "gercekBinis"
    TransitValidationField.ACTUAL_ARRIVAL -> "gercekInis"
    TransitValidationField.DISTANCE -> "mesafe"
    TransitValidationField.SEGMENTS, TransitValidationField.RECORD -> null
}

private fun List<TransitHealthIssue>.maxSeverity(): TransitHealthSeverity? =
    maxByOrNull { it.severity.ordinal }?.severity

private fun String?.orUnknown(lang: AppLanguage): String =
    this?.takeIf { it.isNotBlank() } ?: TransitDuplicatePresentationText.unknown(lang)

/** Pure text mapper kept internal so JVM tests can verify non-probabilistic and accessible labels. */
internal object TransitDuplicatePresentationText {
    fun overviewTitle(lang: AppLanguage) = t(lang, "Olası tekrar kayıtlar", "Mögliche doppelte Einträge", "Possible duplicate records")

    fun overview(count: Int, lang: AppLanguage) = t(
        lang,
        "$count aday çift kullanıcı incelemesi bekliyor.",
        "$count Kandidatenpaare warten auf Prüfung.",
        "$count candidate pairs are waiting for review."
    )

    fun reviewCandidates(lang: AppLanguage) = t(lang, "Adayları incele", "Kandidaten prüfen", "Review candidates")
    fun sheetTitle(lang: AppLanguage) = t(lang, "Tekrar kayıt karşılaştırması", "Duplikate vergleichen", "Compare duplicate records")
    fun firstRecord(lang: AppLanguage) = t(lang, "Birinci kayıt", "Erster Eintrag", "First record")
    fun secondRecord(lang: AppLanguage) = t(lang, "İkinci kayıt", "Zweiter Eintrag", "Second record")
    fun fieldMergeTitle(lang: AppLanguage) = t(lang, "Alan bazında sonuç", "Ergebnis nach Feld", "Field-by-field result")
    fun fieldMergeExplanation(lang: AppLanguage) = t(
        lang,
        "Her alan için korunacak değeri seçin. Gerçek ve planlanan saatler ayrı alanlardır.",
        "Wählen Sie für jedes Feld den Wert aus. Ist- und Planzeiten bleiben getrennt.",
        "Choose the value to keep for each field. Actual and planned times remain separate."
    )

    fun similaritySignal(score: Double, reasonCount: Int, lang: AppLanguage): String {
        val level = when {
            score >= 0.80 && reasonCount >= 6 -> t(lang, "Güçlü", "Starkes", "Strong")
            score >= 0.60 && reasonCount >= 4 -> t(lang, "Orta", "Mittleres", "Moderate")
            else -> t(lang, "Sınırlı", "Begrenztes", "Limited")
        }
        return t(lang, "$level benzerlik sinyali", "$level Ähnlichkeitssignal", "$level similarity signal")
    }

    fun notCertainty(lang: AppLanguage) = t(
        lang,
        "Bu sinyal kesin eşleşme veya doğruluk olasılığı değildir; kararı siz verirsiniz.",
        "Dieses Signal ist keine sichere Übereinstimmung oder Wahrscheinlichkeit; Sie entscheiden.",
        "This signal is not a confirmed match or probability; you make the decision."
    )

    fun reason(reason: TransitDuplicateReason, lang: AppLanguage): String = when (reason) {
        TransitDuplicateReason.SAME_DATE -> t(lang, "Aynı tarih", "Gleiches Datum", "Same date")
        TransitDuplicateReason.NEAR_PLANNED_DEPARTURE -> t(lang, "Yakın kalkış zamanı", "Nahe Abfahrtszeit", "Nearby departure time")
        TransitDuplicateReason.SAME_BOARDING_STOP -> t(lang, "Aynı başlangıç durağı", "Gleiche Einstiegshaltestelle", "Same boarding stop")
        TransitDuplicateReason.SAME_ALIGHTING_STOP -> t(lang, "Aynı varış durağı", "Gleiche Ausstiegshaltestelle", "Same alighting stop")
        TransitDuplicateReason.SAME_LINE -> t(lang, "Aynı hat", "Gleiche Linie", "Same line")
        TransitDuplicateReason.SIMILAR_PLANNED_TIMES -> t(lang, "Benzer planlanan saatler", "Ähnliche Planzeiten", "Similar planned times")
        TransitDuplicateReason.SIMILAR_ACTUAL_TIMES -> t(lang, "Benzer gerçek saatler", "Ähnliche Ist-Zeiten", "Similar actual times")
        TransitDuplicateReason.SIMILAR_DISTANCE -> t(lang, "Benzer mesafe", "Ähnliche Distanz", "Similar distance")
        TransitDuplicateReason.SAME_SEGMENT_FINGERPRINT -> t(lang, "Aynı segment izi", "Gleicher Segment-Fingerabdruck", "Same segment fingerprint")
        TransitDuplicateReason.SAME_RMV_JOURNEY -> t(lang, "Aynı RMV yolculuğu", "Gleiche RMV-Fahrt", "Same RMV journey")
        TransitDuplicateReason.MANUAL_AND_AUTOMATIC_PAIR -> t(lang, "Manuel ve otomatik kayıt çifti", "Manueller und automatischer Eintrag", "Manual and automatic record pair")
        TransitDuplicateReason.COMPLEMENTARY_COMPLETENESS -> t(lang, "Kayıtlar birbirini tamamlayan alanlar içeriyor", "Einträge enthalten ergänzende Felder", "Records contain complementary fields")
    }

    fun recordSummary(record: TripEntity, lang: AppLanguage): String = listOf(
        record.tarih.orUnknown(lang),
        record.hat.orUnknown(lang),
        "${record.binisDuragi.orUnknown(lang)} → ${record.inisDuragi.orUnknown(lang)}",
        record.planlananBinis.orUnknown(lang)
    ).joinToString(". ")

    fun comparisonDescription(
        first: TripEntity,
        second: TripEntity,
        signal: String,
        lang: AppLanguage
    ): String = "$signal. ${notCertainty(lang)} ${firstRecord(lang)}: ${recordSummary(first, lang)}. " +
        "${secondRecord(lang)}: ${recordSummary(second, lang)}."

    fun healthSummary(issues: List<TransitHealthIssue>, lang: AppLanguage): String {
        if (issues.isEmpty()) return t(lang, "Sağlık sorunu yok", "Keine Datenprobleme", "No data health issues")
        val severity = when (issues.maxSeverity()) {
            TransitHealthSeverity.CRITICAL -> t(lang, "kritik", "kritisch", "critical")
            TransitHealthSeverity.WARNING -> t(lang, "uyarı", "Warnung", "warning")
            else -> t(lang, "bilgi", "Hinweis", "information")
        }
        return t(
            lang,
            "${issues.size} sağlık bulgusu, en yüksek seviye $severity",
            "${issues.size} Datenhinweise, höchste Stufe $severity",
            "${issues.size} data health findings, highest level $severity"
        )
    }

    fun provenanceSummary(values: Map<String, TransitFieldProvenance>, lang: AppLanguage): String {
        val known = values.values.count { it.source.name != "UNKNOWN" }
        val stale = values.values.count { it.freshness.name == "STALE" }
        return t(
            lang,
            "Kaynak: $known bilinen alan, $stale eski alan",
            "Quelle: $known bekannte Felder, $stale veraltete Felder",
            "Source: $known known fields, $stale stale fields"
        )
    }

    fun provenanceUnknown(lang: AppLanguage) = t(lang, "Kaynak ve güncellik bilinmiyor", "Quelle und Aktualität unbekannt", "Source and freshness unknown")
    fun unknown(lang: AppLanguage) = t(lang, "Bilinmiyor", "Unbekannt", "Unknown")
    fun selected(lang: AppLanguage) = t(lang, "Seçildi", "Ausgewählt", "Selected")
    fun notSelected(lang: AppLanguage) = t(lang, "Seçilmedi", "Nicht ausgewählt", "Not selected")

    fun validationSummary(critical: Int, warnings: Int, lang: AppLanguage) = t(
        lang,
        "$critical kritik sorun, $warnings onay bekleyen uyarı",
        "$critical kritische Probleme, $warnings Warnungen warten auf Bestätigung",
        "$critical critical issues, $warnings warnings awaiting confirmation"
    )

    fun validationIssue(code: ValidationIssueCode, lang: AppLanguage): String = when (code) {
        ValidationIssueCode.SAME_STOP -> t(lang, "Biniş ve iniş durağı aynı", "Ein- und Ausstieg sind identisch", "Boarding and alighting stops are the same")
        ValidationIssueCode.INVALID_PLANNED_TIME -> t(lang, "Planlanan saat geçersiz", "Planzeit ist ungültig", "Planned time is invalid")
        ValidationIssueCode.INVALID_ACTUAL_TIME -> t(lang, "Gerçek saat geçersiz", "Ist-Zeit ist ungültig", "Actual time is invalid")
        ValidationIssueCode.INCOMPLETE_PLANNED_TIMES -> t(lang, "Planlanan saatlerden biri eksik", "Eine Planzeit fehlt", "One planned time is missing")
        ValidationIssueCode.INCOMPLETE_ACTUAL_TIMES -> t(lang, "Gerçek saatlerden biri eksik", "Eine Ist-Zeit fehlt", "One actual time is missing")
        ValidationIssueCode.MISSING_ACTUAL_TIMES -> t(lang, "Gerçek saatler eksik", "Ist-Zeiten fehlen", "Actual times are missing")
        ValidationIssueCode.PLANNED_TIME_ORDER -> t(lang, "Planlanan saat sırası geçersiz", "Reihenfolge der Planzeiten ist ungültig", "Planned time order is invalid")
        ValidationIssueCode.ACTUAL_TIME_ORDER -> t(lang, "Gerçek saat sırası geçersiz", "Reihenfolge der Ist-Zeiten ist ungültig", "Actual time order is invalid")
        ValidationIssueCode.NEGATIVE_DURATION -> t(lang, "Negatif yolculuk süresi", "Negative Fahrtdauer", "Negative trip duration")
        ValidationIssueCode.UNUSUAL_DURATION -> t(lang, "Olağan dışı yolculuk süresi", "Ungewöhnliche Fahrtdauer", "Unusual trip duration")
        ValidationIssueCode.STORED_DURATION_MISMATCH -> t(lang, "Kayıtlı süre saatlerle uyuşmuyor", "Gespeicherte Dauer passt nicht zu den Zeiten", "Stored duration does not match the times")
        ValidationIssueCode.INVALID_DISTANCE -> t(lang, "Mesafe geçersiz", "Distanz ist ungültig", "Distance is invalid")
        ValidationIssueCode.INCONSISTENT_DISTANCE -> t(lang, "Mesafe kaynakları tutarsız", "Distanzquellen sind inkonsistent", "Distance sources are inconsistent")
        ValidationIssueCode.PLANNED_SEGMENT_OVERLAP -> t(lang, "Planlanan segmentler çakışıyor", "Plansegmente überschneiden sich", "Planned segments overlap")
        ValidationIssueCode.ACTUAL_SEGMENT_OVERLAP -> t(lang, "Gerçek segmentler çakışıyor", "Ist-Segmente überschneiden sich", "Actual segments overlap")
    }

    fun mergeSelectedFields(lang: AppLanguage) = t(lang, "Seçilen alanları birleştir", "Ausgewählte Felder zusammenführen", "Merge selected fields")
    fun reviewWarningsAndMerge(lang: AppLanguage) = t(lang, "Uyarıları incele ve birleştir", "Warnungen prüfen und zusammenführen", "Review warnings and merge")
    fun keepFirst(lang: AppLanguage) = t(lang, "Birinci kaydı koru", "Ersten Eintrag behalten", "Keep first record")
    fun keepSecond(lang: AppLanguage) = t(lang, "İkinci kaydı koru", "Zweiten Eintrag behalten", "Keep second record")
    fun keepSeparate(lang: AppLanguage) = t(lang, "Kayıtları ayrı tut", "Einträge getrennt halten", "Keep records separate")
    fun reviewLater(lang: AppLanguage) = t(lang, "Daha sonra incele", "Später prüfen", "Review later")
    fun warningConfirmationTitle(lang: AppLanguage) = t(lang, "Uyarılarla devam edilsin mi?", "Mit Warnungen fortfahren?", "Continue with warnings?")
    fun warningConfirmationBody(lang: AppLanguage) = t(
        lang,
        "Birleştirme kaydedilmeden önce bu uyarıları bilinçli olarak onaylamanız gerekir.",
        "Bestätigen Sie diese Warnungen ausdrücklich, bevor die Zusammenführung gespeichert wird.",
        "You must explicitly acknowledge these warnings before the merge is saved."
    )
    fun continueWithWarnings(lang: AppLanguage) = t(lang, "Uyarıları onayla", "Warnungen bestätigen", "Acknowledge warnings")
    fun cancel(lang: AppLanguage) = t(lang, "İptal", "Abbrechen", "Cancel")
}

private fun t(lang: AppLanguage, tr: String, de: String, en: String): String = when (lang) {
    AppLanguage.TR -> tr
    AppLanguage.DE -> de
    AppLanguage.EN -> en
}
