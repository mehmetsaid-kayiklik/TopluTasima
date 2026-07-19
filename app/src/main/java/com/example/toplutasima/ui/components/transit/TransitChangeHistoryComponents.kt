package com.example.toplutasima.ui.components.transit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.toplutasima.transit.history.TransitChangeEvent
import com.example.toplutasima.transit.history.TransitFieldChange
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.LocaleManager

/** Caller decides whether undo remains safe after validation, later edits and tombstone checks. */
data class TransitHistoryUndoUiState(
    val enabled: Boolean,
    val disabledReason: String? = null
) {
    companion object {
        val Disabled = TransitHistoryUndoUiState(enabled = false)
    }
}

/**
 * Compact transit-only entry point. [recordId] is deliberately sufficient, so a deleted record can
 * expose its ledger from a deletion receipt even when no current Room row exists.
 */
@Composable
fun TransitChangeHistorySection(
    recordId: String,
    events: List<TransitChangeEvent>,
    onOpenHistory: (recordId: String) -> Unit,
    modifier: Modifier = Modifier,
    lang: AppLanguage = LocaleManager.currentLanguage
) {
    val scopedEvents = events.filter { it.recordId == recordId }
    val latest = scopedEvents.maxByOrNull { it.occurredAtEpochMillis }
    val countLabel = historyCountLabel(scopedEvents.size, lang)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Outlined.History, contentDescription = null)
                Column(modifier = Modifier.weight(1f)) {
                    Text(historyTitle(lang), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(countLabel, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(
                    onClick = { onOpenHistory(recordId) },
                    enabled = recordId.isNotBlank(),
                    modifier = Modifier.semantics {
                        contentDescription = openHistoryDescription(countLabel, lang)
                    }
                ) {
                    Text(openLabel(lang))
                }
            }
            latest?.let {
                Text(
                    TransitChangeHistoryUiMapper.shortSummary(it, lang),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2
                )
            }
        }
    }
}

/**
 * Complete history browser for an active or deleted transit record. The caller supplies events by
 * UID + [recordId], which keeps user isolation and persistence concerns out of the UI layer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransitChangeHistorySheet(
    recordId: String,
    events: List<TransitChangeEvent>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onUndo: ((TransitChangeEvent) -> Unit)? = null,
    undoStateFor: (TransitChangeEvent) -> TransitHistoryUndoUiState = { TransitHistoryUndoUiState.Disabled },
    lang: AppLanguage = LocaleManager.currentLanguage
) {
    var selectedEventId by remember(recordId) { mutableStateOf<String?>(null) }
    val scopedEvents = events.filter { it.recordId == recordId }
    val selectedEvent = selectedEventId?.let { id -> scopedEvents.firstOrNull { it.eventId == id } }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        if (selectedEvent == null) {
            TransitChangeHistoryList(
                recordId = recordId,
                events = scopedEvents,
                onOpenEvent = { selectedEventId = it.eventId },
                modifier = modifier,
                lang = lang
            )
        } else {
            TransitChangeHistoryDetail(
                event = selectedEvent,
                onBack = { selectedEventId = null },
                onUndo = onUndo,
                undoState = undoStateFor(selectedEvent),
                modifier = modifier,
                lang = lang
            )
        }
    }
}

@Composable
fun TransitChangeHistoryList(
    recordId: String,
    events: List<TransitChangeEvent>,
    onOpenEvent: (TransitChangeEvent) -> Unit,
    modifier: Modifier = Modifier,
    lang: AppLanguage = LocaleManager.currentLanguage
) {
    val scopedEvents = events.filter { it.recordId == recordId }
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Text(historyTitle(lang), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            historyRecordLabel(recordId, lang),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        if (scopedEvents.isEmpty()) {
            Text(emptyHistoryLabel(lang), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(24.dp))
        } else {
            LazyColumn {
                items(
                    items = scopedEvents.sortedWith(
                        compareByDescending<TransitChangeEvent> { it.occurredAtEpochMillis }
                            .thenByDescending { it.recordedAtEpochMillis }
                    ),
                    key = { it.eventId }
                ) { event ->
                    TransitChangeHistoryRow(
                        event = event,
                        onClick = { onOpenEvent(event) },
                        lang = lang
                    )
                    HorizontalDivider()
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun TransitChangeHistoryRow(
    event: TransitChangeEvent,
    onClick: () -> Unit,
    lang: AppLanguage
) {
    val accessibility = TransitChangeHistoryUiMapper.eventContentDescription(event, lang)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = accessibility
                role = Role.Button
            }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                TransitChangeHistoryUiMapper.operationLabel(event.operation, lang),
                fontWeight = FontWeight.SemiBold
            )
            Text(
                TransitChangeHistoryUiMapper.formatTimestamp(event.occurredAtEpochMillis),
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                sourcePrefix(TransitChangeHistoryUiMapper.sourceLabel(event.source, lang), lang),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                TransitChangeHistoryUiMapper.shortSummary(event, lang),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3
            )
        }
    }
}

@Composable
private fun TransitChangeHistoryDetail(
    event: TransitChangeEvent,
    onBack: () -> Unit,
    onUndo: ((TransitChangeEvent) -> Unit)?,
    undoState: TransitHistoryUndoUiState,
    modifier: Modifier,
    lang: AppLanguage
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = backLabel(lang)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    TransitChangeHistoryUiMapper.operationLabel(event.operation, lang),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    TransitChangeHistoryUiMapper.formatTimestamp(event.occurredAtEpochMillis),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        HistoryMetadata(event = event, lang = lang)
        Spacer(Modifier.height(12.dp))
        if (event.changes.isEmpty()) {
            Text(noFieldChangesLabel(lang), style = MaterialTheme.typography.bodyMedium)
        } else {
            event.changes.forEach { change ->
                FieldChangeCard(change = change, lang = lang)
                Spacer(Modifier.height(8.dp))
            }
        }
        if (onUndo != null) {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onUndo(event) },
                enabled = undoState.enabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.Undo, contentDescription = null)
                Text(undoLabel(lang), modifier = Modifier.padding(start = 8.dp))
            }
            if (!undoState.enabled) {
                undoState.disabledReason?.takeIf { it.isNotBlank() }?.let { reason ->
                    Text(
                        reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { contentDescription = reason }
                    )
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun HistoryMetadata(event: TransitChangeEvent, lang: AppLanguage) {
    Surface(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            contentDescription = TransitChangeHistoryUiMapper.eventContentDescription(event, lang)
        },
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                sourcePrefix(TransitChangeHistoryUiMapper.sourceLabel(event.source, lang), lang),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                TransitChangeHistoryUiMapper.syncStatusLabel(event.syncStatus, lang),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun FieldChangeCard(change: TransitFieldChange, lang: AppLanguage) {
    val label = TransitChangeHistoryUiMapper.fieldLabel(change.fieldId, lang)
    val oldValue = historyDisplayValue(change.oldValue, lang)
    val newValue = historyDisplayValue(change.newValue, lang)
    Card(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            contentDescription = TransitChangeHistoryUiMapper.changeDescription(change, lang)
        },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, fontWeight = FontWeight.SemiBold)
            ValueBlock(title = oldValueLabel(lang), value = oldValue)
            ValueBlock(title = newValueLabel(lang), value = newValue)
            val oldSource = change.oldProvenance?.source
            val newSource = change.newProvenance?.source
            if (oldSource != null || newSource != null) {
                Text(
                    provenanceComparisonLabel(
                        oldSource?.let { TransitChangeHistoryUiMapper.provenanceLabel(it, lang) },
                        newSource?.let { TransitChangeHistoryUiMapper.provenanceLabel(it, lang) },
                        lang
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ValueBlock(title: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun historyDisplayValue(
    value: com.example.toplutasima.transit.history.TransitHistoryValue,
    lang: AppLanguage
): String = value.displayValue(
    unknownLabel = unknownLabel(lang),
    emptyLabel = emptyLabel(lang)
)

private fun historyTitle(lang: AppLanguage) = tr(lang, "Değişiklik geçmişi", "Änderungsverlauf", "Change history")
private fun historyCountLabel(count: Int, lang: AppLanguage) = tr(
    lang,
    "$count geçmiş olayı",
    "$count Verlaufsereignisse",
    "$count history events"
)
private fun openLabel(lang: AppLanguage) = tr(lang, "Aç", "Öffnen", "Open")
private fun openHistoryDescription(count: String, lang: AppLanguage) = tr(
    lang,
    "Değişiklik geçmişini aç. $count",
    "Änderungsverlauf öffnen. $count",
    "Open change history. $count"
)
private fun historyRecordLabel(recordId: String, lang: AppLanguage): String {
    val shortId = recordId.take(16).ifBlank { "—" }
    return tr(lang, "Transit kaydı: $shortId", "Transiteintrag: $shortId", "Transit record: $shortId")
}
private fun emptyHistoryLabel(lang: AppLanguage) = tr(
    lang,
    "Bu transit kaydı için geçmiş olayı yok.",
    "Für diesen Transiteintrag gibt es keine Verlaufsereignisse.",
    "There are no history events for this transit record."
)
private fun sourcePrefix(source: String, lang: AppLanguage) = tr(lang, "Kaynak: $source", "Quelle: $source", "Source: $source")
private fun backLabel(lang: AppLanguage) = tr(lang, "Geçmiş listesine dön", "Zurück zum Verlauf", "Back to history")
private fun noFieldChangesLabel(lang: AppLanguage) = tr(
    lang,
    "Bu işlem alan değeri değiştirmedi.",
    "Dieser Vorgang hat keinen Feldwert geändert.",
    "This operation did not change a field value."
)
private fun oldValueLabel(lang: AppLanguage) = tr(lang, "Önceki değer", "Vorheriger Wert", "Previous value")
private fun newValueLabel(lang: AppLanguage) = tr(lang, "Yeni değer", "Neuer Wert", "New value")
private fun unknownLabel(lang: AppLanguage) = tr(lang, "Bilinmiyor", "Unbekannt", "Unknown")
private fun emptyLabel(lang: AppLanguage) = tr(lang, "Boş", "Leer", "Empty")
private fun undoLabel(lang: AppLanguage) = tr(lang, "Güvenli biçimde geri al", "Sicher rückgängig machen", "Undo safely")
private fun provenanceComparisonLabel(oldSource: String?, newSource: String?, lang: AppLanguage): String = tr(
    lang,
    "Veri kaynağı: ${oldSource ?: "Bilinmiyor"} → ${newSource ?: "Bilinmiyor"}",
    "Datenquelle: ${oldSource ?: "Unbekannt"} → ${newSource ?: "Unbekannt"}",
    "Data source: ${oldSource ?: "Unknown"} → ${newSource ?: "Unknown"}"
)

private fun tr(lang: AppLanguage, tr: String, de: String, en: String): String = when (lang) {
    AppLanguage.TR -> tr
    AppLanguage.DE -> de
    AppLanguage.EN -> en
}
