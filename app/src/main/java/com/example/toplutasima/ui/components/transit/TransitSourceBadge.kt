package com.example.toplutasima.ui.components.transit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.toplutasima.transit.provenance.TransitFieldFreshness
import com.example.toplutasima.transit.provenance.TransitFieldProvenance
import com.example.toplutasima.transit.provenance.TransitFieldSource
import com.example.toplutasima.ui.AppLanguage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Compact, transit-only provenance badge. Tapping it opens a textual explanation, so source and
 * freshness never rely on color alone.
 */
@Composable
fun TransitSourceBadge(
    provenance: TransitFieldProvenance,
    lang: AppLanguage,
    modifier: Modifier = Modifier,
    fieldLabel: String? = null,
    nowEpochMillis: Long = System.currentTimeMillis()
) {
    var showDetails by remember { mutableStateOf(false) }
    val sourceLabel = TransitProvenanceText.source(provenance.source, lang)
    val freshnessLabel = TransitProvenanceText.freshness(provenance.freshness, lang)
    val badgeLabel = "$sourceLabel · $freshnessLabel"
    val accessibilityText = TransitProvenanceText.accessibilityDescription(
        provenance = provenance,
        lang = lang,
        fieldLabel = fieldLabel,
        nowEpochMillis = nowEpochMillis
    )
    val badgeColor = provenanceColor(provenance.freshness)

    Surface(
        onClick = { showDetails = true },
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = accessibilityText
            stateDescription = freshnessLabel
        },
        shape = RoundedCornerShape(50),
        color = badgeColor.copy(alpha = 0.12f),
        contentColor = badgeColor,
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = badgeLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    if (showDetails) {
        TransitProvenanceDialog(
            provenance = provenance,
            lang = lang,
            fieldLabel = fieldLabel,
            nowEpochMillis = nowEpochMillis,
            onDismiss = { showDetails = false }
        )
    }
}

@Composable
private fun TransitProvenanceDialog(
    provenance: TransitFieldProvenance,
    lang: AppLanguage,
    fieldLabel: String?,
    nowEpochMillis: Long,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = fieldLabel?.takeIf { it.isNotBlank() }
                    ?: TransitProvenanceText.detailsTitle(lang)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailLine(
                    label = TransitProvenanceText.sourceLabel(lang),
                    value = TransitProvenanceText.source(provenance.source, lang)
                )
                provenance.backingSource?.let { backingSource ->
                    DetailLine(
                        label = TransitProvenanceText.cachedSourceLabel(lang),
                        value = TransitProvenanceText.source(backingSource, lang)
                    )
                }
                DetailLine(
                    label = TransitProvenanceText.lastUpdatedLabel(lang),
                    value = TransitProvenanceText.lastUpdated(
                        provenance.lastUpdatedAtEpochMillis,
                        nowEpochMillis,
                        lang
                    )
                )
                DetailLine(
                    label = TransitProvenanceText.fallbackLabel(lang),
                    value = TransitProvenanceText.fallback(provenance, lang)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(TransitProvenanceText.close(lang))
            }
        }
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun provenanceColor(freshness: TransitFieldFreshness): Color = when (freshness) {
    TransitFieldFreshness.FRESH -> MaterialTheme.colorScheme.primary
    TransitFieldFreshness.AGING -> MaterialTheme.colorScheme.tertiary
    TransitFieldFreshness.STALE -> MaterialTheme.colorScheme.error
    TransitFieldFreshness.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
}

internal object TransitProvenanceText {
    private val absoluteTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

    fun source(source: TransitFieldSource, lang: AppLanguage): String = when (source) {
        TransitFieldSource.LIVE_RMV -> tr(lang, "Canlı RMV", "RMV Echtzeit", "Live RMV")
        TransitFieldSource.PLANNED_RMV -> tr(lang, "Planlanan RMV", "RMV Fahrplan", "Planned RMV")
        TransitFieldSource.TRANSIT_LOCATION -> tr(lang, "Transit konumu", "Transit-Standort", "Transit location")
        TransitFieldSource.RMV_DISTANCE -> tr(lang, "RMV mesafesi", "RMV-Distanz", "RMV distance")
        TransitFieldSource.ORS_DISTANCE -> tr(lang, "ORS mesafesi", "ORS-Distanz", "ORS distance")
        TransitFieldSource.CACHE -> tr(lang, "Önbellek", "Cache", "Cache")
        TransitFieldSource.MANUAL -> tr(lang, "Manuel giriş", "Manuelle Eingabe", "Manual entry")
        TransitFieldSource.UNKNOWN -> tr(lang, "Kaynak bilinmiyor", "Quelle unbekannt", "Unknown source")
    }

    fun freshness(freshness: TransitFieldFreshness, lang: AppLanguage): String = when (freshness) {
        TransitFieldFreshness.FRESH -> tr(lang, "Güncel", "Aktuell", "Fresh")
        TransitFieldFreshness.AGING -> tr(lang, "Güncelliği azalıyor", "Wird älter", "Aging")
        TransitFieldFreshness.STALE -> tr(lang, "Eski veri", "Veraltet", "Stale")
        TransitFieldFreshness.UNKNOWN -> tr(lang, "Güncellik bilinmiyor", "Aktualität unbekannt", "Freshness unknown")
    }

    fun accessibilityDescription(
        provenance: TransitFieldProvenance,
        lang: AppLanguage,
        fieldLabel: String?,
        nowEpochMillis: Long
    ): String {
        val prefix = fieldLabel?.takeIf { it.isNotBlank() }?.let { "$it. " }.orEmpty()
        return prefix + tr(
            lang,
            "Kaynak: ${source(provenance.source, lang)}. Güncellik: ${freshness(provenance.freshness, lang)}. " +
                "Son güncelleme: ${lastUpdated(provenance.lastUpdatedAtEpochMillis, nowEpochMillis, lang)}. " +
                "Fallback: ${fallback(provenance, lang)}.",
            "Quelle: ${source(provenance.source, lang)}. Aktualität: ${freshness(provenance.freshness, lang)}. " +
                "Letzte Aktualisierung: ${lastUpdated(provenance.lastUpdatedAtEpochMillis, nowEpochMillis, lang)}. " +
                "Fallback: ${fallback(provenance, lang)}.",
            "Source: ${source(provenance.source, lang)}. Freshness: ${freshness(provenance.freshness, lang)}. " +
                "Last updated: ${lastUpdated(provenance.lastUpdatedAtEpochMillis, nowEpochMillis, lang)}. " +
                "Fallback: ${fallback(provenance, lang)}."
        )
    }

    fun lastUpdated(updatedAt: Long?, nowEpochMillis: Long, lang: AppLanguage): String {
        if (updatedAt == null) return tr(lang, "Bilinmiyor", "Unbekannt", "Unknown")
        val absolute = runCatching {
            Instant.ofEpochMilli(updatedAt)
                .atZone(ZoneId.systemDefault())
                .format(absoluteTimeFormatter)
        }.getOrNull() ?: return tr(lang, "Bilinmiyor", "Unbekannt", "Unknown")
        val ageMinutes = ((nowEpochMillis - updatedAt).coerceAtLeast(0L) / 60_000L)
        val relative = when {
            ageMinutes == 0L -> tr(lang, "şimdi", "gerade eben", "just now")
            ageMinutes < 60L -> tr(lang, "$ageMinutes dk önce", "vor $ageMinutes Min.", "$ageMinutes min ago")
            else -> {
                val hours = ageMinutes / 60L
                tr(lang, "$hours sa önce", "vor $hours Std.", "$hours hr ago")
            }
        }
        return "$absolute ($relative)"
    }

    fun fallback(provenance: TransitFieldProvenance, lang: AppLanguage): String {
        if (!provenance.isFallback) return tr(lang, "Kullanılmadı", "Nicht verwendet", "Not used")
        val preferred = provenance.fallbackFor?.let { source(it, lang) }
        return if (preferred == null) {
            tr(lang, "Kullanıldı", "Verwendet", "Used")
        } else {
            tr(
                lang,
                "Kullanıldı ($preferred yerine)",
                "Verwendet (statt $preferred)",
                "Used (instead of $preferred)"
            )
        }
    }

    fun detailsTitle(lang: AppLanguage): String = tr(lang, "Veri kaynağı", "Datenquelle", "Data source")
    fun sourceLabel(lang: AppLanguage): String = tr(lang, "Kaynak", "Quelle", "Source")
    fun cachedSourceLabel(lang: AppLanguage): String = tr(lang, "Önbellekteki kaynak", "Quelle im Cache", "Cached source")
    fun lastUpdatedLabel(lang: AppLanguage): String = tr(lang, "Son güncelleme", "Letzte Aktualisierung", "Last updated")
    fun fallbackLabel(lang: AppLanguage): String = tr(lang, "Fallback", "Fallback", "Fallback")
    fun close(lang: AppLanguage): String = tr(lang, "Tamam", "OK", "OK")

    private fun tr(lang: AppLanguage, tr: String, de: String, en: String): String = when (lang) {
        AppLanguage.TR -> tr
        AppLanguage.DE -> de
        AppLanguage.EN -> en
    }
}
