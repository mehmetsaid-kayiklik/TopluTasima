package com.example.toplutasima.ui.components.transit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.toplutasima.domain.transit.health.TransitHealthCorrection
import com.example.toplutasima.domain.transit.health.TransitHealthIssue
import com.example.toplutasima.domain.transit.health.TransitHealthIssueCode
import com.example.toplutasima.domain.transit.health.TransitHealthSeverity
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.LocaleManager

@Composable
fun TransitHealthStatusChip(
    issues: List<TransitHealthIssue>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    lang: AppLanguage = LocaleManager.currentLanguage
) {
    if (issues.isEmpty()) return
    val severity = issues.maxBy { it.severity.ordinal }.severity
    val presentation = healthPresentation(severity, lang)
    val accessibility = healthCountLabel(issues.size, severity, lang)
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = accessibility },
        shape = RoundedCornerShape(8.dp),
        color = presentation.container,
        contentColor = presentation.content
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(presentation.icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(accessibility, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun TransitHealthOverviewCard(
    issueCount: Int,
    criticalCount: Int,
    correctionCount: Int,
    isScanning: Boolean,
    scanMessage: String,
    onScanAll: () -> Unit,
    onApplySafeCorrections: () -> Unit,
    modifier: Modifier = Modifier,
    lang: AppLanguage = LocaleManager.currentLanguage
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isScanning) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        if (criticalCount > 0) Icons.Outlined.ErrorOutline else Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = if (criticalCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                    )
                }
                Text(
                    healthOverviewLabel(issueCount, criticalCount, lang),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            scanMessage.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onScanAll, enabled = !isScanning) {
                    Text(scanAllLabel(lang))
                }
                if (correctionCount > 0) {
                    Button(onClick = onApplySafeCorrections, enabled = !isScanning) {
                        Text(applyCorrectionsLabel(correctionCount, lang))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransitHealthIssuesSheet(
    issues: List<TransitHealthIssue>,
    corrections: List<TransitHealthCorrection>,
    onDismiss: () -> Unit,
    onOpenRecord: () -> Unit,
    onApplyCorrections: () -> Unit,
    lang: AppLanguage = LocaleManager.currentLanguage
) {
    if (issues.isEmpty()) return
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(healthDetailsTitle(lang), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            issues.forEachIndexed { index, issue ->
                HealthIssueRow(issue = issue, lang = lang)
                if (index != issues.lastIndex) HorizontalDivider()
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                TextButton(onClick = onOpenRecord) { Text(openRecordLabel(lang)) }
                if (corrections.isNotEmpty()) {
                    Button(onClick = onApplyCorrections) {
                        Text(applyCorrectionsLabel(corrections.size, lang))
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthIssueRow(issue: TransitHealthIssue, lang: AppLanguage) {
    val presentation = healthPresentation(issue.severity, lang)
    Row(
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = "${presentation.label}. ${healthIssueLabel(issue.code, lang)}. ${issue.detail}"
        },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(presentation.icon, contentDescription = null, tint = presentation.content)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(healthIssueLabel(issue.code, lang), fontWeight = FontWeight.SemiBold)
            Text(presentation.label, style = MaterialTheme.typography.labelSmall, color = presentation.content)
            issue.detail.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private data class HealthPresentation(
    val label: String,
    val icon: ImageVector,
    val content: Color,
    val container: Color
)

@Composable
private fun healthPresentation(severity: TransitHealthSeverity, lang: AppLanguage): HealthPresentation = when (severity) {
    TransitHealthSeverity.INFO -> HealthPresentation(
        tr(lang, "Bilgi", "Hinweis", "Information"),
        Icons.Outlined.Info,
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.primaryContainer
    )
    TransitHealthSeverity.WARNING -> HealthPresentation(
        tr(lang, "Uyarı", "Warnung", "Warning"),
        Icons.Outlined.Warning,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.tertiaryContainer
    )
    TransitHealthSeverity.CRITICAL -> HealthPresentation(
        tr(lang, "Kritik", "Kritisch", "Critical"),
        Icons.Outlined.ErrorOutline,
        MaterialTheme.colorScheme.error,
        MaterialTheme.colorScheme.errorContainer
    )
}

private fun healthIssueLabel(code: TransitHealthIssueCode, lang: AppLanguage): String = when (code) {
    TransitHealthIssueCode.POSSIBLE_DUPLICATE -> tr(lang, "Olası yinelenen kayıt", "Mögliches Duplikat", "Possible duplicate")
    TransitHealthIssueCode.OVERLAPPING_SEGMENT -> tr(lang, "Çakışan yolculuk", "Überlappende Fahrt", "Overlapping trip")
    TransitHealthIssueCode.SAME_STOP -> tr(lang, "Aynı başlangıç ve varış", "Gleicher Start und Ziel", "Same origin and destination")
    TransitHealthIssueCode.INVALID_PLANNED_TIME -> tr(lang, "Geçersiz planlanan saat", "Ungültige Planzeit", "Invalid planned time")
    TransitHealthIssueCode.INVALID_ACTUAL_TIME -> tr(lang, "Geçersiz gerçek saat", "Ungültige Ist-Zeit", "Invalid actual time")
    TransitHealthIssueCode.MISSING_ACTUAL_TIME -> tr(lang, "Eksik gerçek saat", "Fehlende Ist-Zeit", "Missing actual time")
    TransitHealthIssueCode.PLANNED_TIME_ORDER -> tr(lang, "Planlanan saat sırası", "Reihenfolge der Planzeiten", "Planned time order")
    TransitHealthIssueCode.ACTUAL_TIME_ORDER -> tr(lang, "Gerçek saat sırası", "Reihenfolge der Ist-Zeiten", "Actual time order")
    TransitHealthIssueCode.NEGATIVE_DURATION -> tr(lang, "Negatif süre", "Negative Dauer", "Negative duration")
    TransitHealthIssueCode.UNUSUAL_DURATION -> tr(lang, "Olağan dışı süre", "Ungewöhnliche Dauer", "Unusual duration")
    TransitHealthIssueCode.STORED_DURATION_MISMATCH -> tr(lang, "Süre yeniden hesaplanmalı", "Dauer neu berechnen", "Duration needs recalculation")
    TransitHealthIssueCode.INVALID_DISTANCE -> tr(lang, "Geçersiz mesafe", "Ungültige Distanz", "Invalid distance")
    TransitHealthIssueCode.EXTREME_DISTANCE -> tr(lang, "Olağan dışı mesafe", "Ungewöhnliche Distanz", "Unusual distance")
    TransitHealthIssueCode.ROUTE_DISTANCE_MISMATCH -> tr(lang, "Rota mesafeleri tutarsız", "Routendistanzen inkonsistent", "Route distances disagree")
    TransitHealthIssueCode.UNKNOWN_PROVENANCE -> tr(lang, "Veri kaynağı bilinmiyor", "Datenquelle unbekannt", "Unknown data source")
}

private fun healthCountLabel(count: Int, severity: TransitHealthSeverity, lang: AppLanguage): String =
    tr(lang, "$count sağlık bulgusu", "$count Datenhinweise", "$count data health findings") +
        " · " + when (severity) {
            TransitHealthSeverity.INFO -> tr(lang, "bilgi", "Hinweis", "info")
            TransitHealthSeverity.WARNING -> tr(lang, "uyarı", "Warnung", "warning")
            TransitHealthSeverity.CRITICAL -> tr(lang, "kritik", "kritisch", "critical")
        }

private fun healthOverviewLabel(count: Int, critical: Int, lang: AppLanguage): String = when {
    critical > 0 -> tr(lang, "$count bulgu, $critical kritik", "$count Hinweise, $critical kritisch", "$count findings, $critical critical")
    else -> tr(lang, "$count veri sağlığı bulgusu", "$count Datenhinweise", "$count data health findings")
}

private fun scanAllLabel(lang: AppLanguage) = tr(lang, "Tüm geçmişi tara", "Gesamten Verlauf prüfen", "Scan all history")
private fun applyCorrectionsLabel(count: Int, lang: AppLanguage) =
    tr(lang, "$count güvenli düzeltme", "$count sichere Korrekturen", "$count safe corrections")
private fun healthDetailsTitle(lang: AppLanguage) = tr(lang, "Kayıt sağlığı", "Datenqualität", "Record health")
private fun openRecordLabel(lang: AppLanguage) = tr(lang, "Kaydı aç", "Eintrag öffnen", "Open record")

private fun tr(lang: AppLanguage, tr: String, de: String, en: String): String = when (lang) {
    AppLanguage.TR -> tr
    AppLanguage.DE -> de
    AppLanguage.EN -> en
}
