package com.example.toplutasima.ui.components.transit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.toplutasima.transit.provenance.TransitFieldProvenance
import com.example.toplutasima.transit.provenance.TransitFieldSource
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.LocaleManager

/** Record-level entry point whose dialog preserves provenance separately for every field. */
@Composable
fun TransitRecordProvenanceChip(
    provenanceByField: Map<String, TransitFieldProvenance>,
    modifier: Modifier = Modifier,
    lang: AppLanguage = LocaleManager.currentLanguage
) {
    if (provenanceByField.isEmpty()) return
    var showDetails by remember(provenanceByField) { mutableStateOf(false) }
    val knownSources = provenanceByField.values.map { it.source }
        .filterNot { it == TransitFieldSource.UNKNOWN }
        .distinct()
    val summary = when (knownSources.size) {
        0 -> recordUnknownLabel(lang)
        1 -> TransitProvenanceText.source(knownSources.single(), lang)
        else -> mixedSourcesLabel(lang)
    }
    val unknownCount = provenanceByField.count { it.value.source == TransitFieldSource.UNKNOWN }
    val accessibility = buildString {
        append(recordSourcesTitle(lang))
        append(": ")
        append(summary)
        if (unknownCount > 0) append(". ${unknownFieldsLabel(unknownCount, lang)}")
    }

    Surface(
        onClick = { showDetails = true },
        modifier = modifier.semantics { contentDescription = accessibility },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(summary, style = MaterialTheme.typography.labelMedium)
        }
    }

    if (showDetails) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            title = { Text(recordSourcesTitle(lang)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    provenanceByField.entries.forEachIndexed { index, (fieldId, provenance) ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(fieldLabel(fieldId, lang), fontWeight = FontWeight.SemiBold)
                            Text(
                                "${TransitProvenanceText.source(provenance.source, lang)} · " +
                                    TransitProvenanceText.freshness(provenance.freshness, lang),
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (provenance.isFallback) {
                                Text(
                                    TransitProvenanceText.fallback(provenance, lang),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (index != provenanceByField.size - 1) HorizontalDivider()
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetails = false }) {
                    Text(TransitProvenanceText.close(lang))
                }
            }
        )
    }
}

private fun fieldLabel(fieldId: String, lang: AppLanguage): String = when (fieldId) {
    "tarih" -> trp(lang, "Tarih", "Datum", "Date")
    "hat" -> trp(lang, "Hat", "Linie", "Line")
    "binisDuragi" -> trp(lang, "Biniş durağı", "Einstieg", "Boarding stop")
    "inisDuragi" -> trp(lang, "İniş durağı", "Ausstieg", "Alighting stop")
    "planlananBinis" -> trp(lang, "Planlanan biniş", "Geplante Abfahrt", "Planned departure")
    "planlananInis" -> trp(lang, "Planlanan iniş", "Geplante Ankunft", "Planned arrival")
    "gercekBinis" -> trp(lang, "Gerçek biniş", "Tatsächliche Abfahrt", "Actual departure")
    "gercekInis" -> trp(lang, "Gerçek iniş", "Tatsächliche Ankunft", "Actual arrival")
    "mesafe" -> trp(lang, "Mesafe", "Distanz", "Distance")
    "orsMesafeKm" -> trp(lang, "ORS mesafesi", "ORS-Distanz", "ORS distance")
    "rmvMesafeKm" -> trp(lang, "RMV mesafesi", "RMV-Distanz", "RMV distance")
    else -> fieldId
}

private fun recordSourcesTitle(lang: AppLanguage) = trp(lang, "Alan bazlı veri kaynakları", "Datenquellen je Feld", "Field data sources")
private fun mixedSourcesLabel(lang: AppLanguage) = trp(lang, "Karma kaynak", "Gemischte Quellen", "Mixed sources")
private fun recordUnknownLabel(lang: AppLanguage) = trp(lang, "Kaynak bilinmiyor", "Quelle unbekannt", "Unknown source")
private fun unknownFieldsLabel(count: Int, lang: AppLanguage) = trp(lang, "$count alan bilinmiyor", "$count Felder unbekannt", "$count fields unknown")

private fun trp(lang: AppLanguage, tr: String, de: String, en: String): String = when (lang) {
    AppLanguage.TR -> tr
    AppLanguage.DE -> de
    AppLanguage.EN -> en
}
