package com.example.toplutasima.ui.components.transit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.toplutasima.transit.insights.TransitInsight
import com.example.toplutasima.transit.insights.TransitInsightConfidence
import com.example.toplutasima.transit.insights.TransitInsightConfidenceFactorType
import com.example.toplutasima.transit.insights.TransitInsightType
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.LocaleManager

/** Standalone, accessible section; the Summary screen only has to place it behind its gate. */
@Composable
fun TransitInsightsSection(
    insights: List<TransitInsight>,
    modifier: Modifier = Modifier,
    lang: AppLanguage = LocaleManager.currentLanguage
) {
    if (insights.isEmpty()) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Outlined.Analytics, contentDescription = null)
            Text(
                text = localized(lang, "İçgörüler", "Einblicke", "Insights"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        insights.forEach { insight ->
            TransitInsightCard(insight = insight, lang = lang)
        }
    }
}

@Composable
fun TransitInsightCard(
    insight: TransitInsight,
    modifier: Modifier = Modifier,
    lang: AppLanguage = LocaleManager.currentLanguage
) {
    var showExplanation by remember(insight.semanticKey, insight.explanation) { mutableStateOf(false) }
    val title = insightTitle(insight.type, lang)
    val result = insightResult(insight, lang)
    val confidence = confidencePresentation(insight.confidence.level, lang)
    val accessibility = localized(
        lang,
        "$title. $result. Dönem ${insight.periodLabel}. Güven ${confidence.label}.",
        "$title. $result. Zeitraum ${insight.periodLabel}. Vertrauen ${confidence.label}.",
        "$title. $result. Period ${insight.periodLabel}. Confidence ${confidence.label}."
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = accessibility },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(result, style = MaterialTheme.typography.bodyLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    insight.periodLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = confidence.container,
                    contentColor = confidence.content
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(confidence.icon, contentDescription = null, modifier = Modifier.size(14.dp))
                        Text(confidence.label, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            TextButton(onClick = { showExplanation = true }) {
                Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(
                    text = localized(
                        lang,
                        "Bu neden gösteriliyor?",
                        "Warum wird das angezeigt?",
                        "Why is this shown?"
                    ),
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
    }

    if (showExplanation) {
        TransitInsightExplanationDialog(
            insight = insight,
            title = title,
            onDismiss = { showExplanation = false },
            lang = lang
        )
    }
}

@Composable
private fun TransitInsightExplanationDialog(
    insight: TransitInsight,
    title: String,
    onDismiss: () -> Unit,
    lang: AppLanguage
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExplanationLine(
                    localized(lang, "Dönem", "Zeitraum", "Period"),
                    insight.explanation.periodLabel
                )
                insight.explanation.comparisonPeriodLabel?.let {
                    ExplanationLine(
                        localized(lang, "Karşılaştırma", "Vergleich", "Comparison"),
                        it
                    )
                }
                ExplanationLine(
                    localized(lang, "Kullanılan kayıt", "Verwendete Einträge", "Records used"),
                    insight.explanation.recordCount.toString()
                )
                ExplanationLine(
                    localized(lang, "Hesaplama", "Berechnung", "Calculation"),
                    insight.explanation.calculation
                )
                if (insight.explanation.evidence.isNotEmpty()) {
                    HorizontalDivider()
                    insight.explanation.evidence.forEach { evidence -> Text(evidence) }
                }
                if (insight.confidence.factors.isNotEmpty()) {
                    HorizontalDivider()
                    Text(
                        localized(lang, "Güveni etkileyenler", "Vertrauensfaktoren", "Confidence factors"),
                        fontWeight = FontWeight.SemiBold
                    )
                    insight.confidence.factors.forEach { factor ->
                        Text(
                            "${factorLabel(factor.type, lang)}: ${factor.affectedRecordCount} " +
                                localized(lang, "kayıt", "Einträge", "records"),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(localized(lang, "Kapat", "Schließen", "Close"))
            }
        }
    )
}

@Composable
private fun ExplanationLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private data class ConfidencePresentation(
    val label: String,
    val icon: ImageVector,
    val content: Color,
    val container: Color
)

@Composable
private fun confidencePresentation(
    confidence: TransitInsightConfidence,
    lang: AppLanguage
): ConfidencePresentation = when (confidence) {
    TransitInsightConfidence.HIGH -> ConfidencePresentation(
        localized(lang, "Yüksek", "Hoch", "High"),
        Icons.Outlined.CheckCircleOutline,
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.primaryContainer
    )
    TransitInsightConfidence.MEDIUM -> ConfidencePresentation(
        localized(lang, "Orta", "Mittel", "Medium"),
        Icons.Outlined.Info,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.tertiaryContainer
    )
    TransitInsightConfidence.LOW -> ConfidencePresentation(
        localized(lang, "Düşük", "Niedrig", "Low"),
        Icons.Outlined.WarningAmber,
        MaterialTheme.colorScheme.error,
        MaterialTheme.colorScheme.errorContainer
    )
    TransitInsightConfidence.INSUFFICIENT_DATA -> ConfidencePresentation(
        localized(lang, "Yetersiz veri", "Zu wenig Daten", "Insufficient data"),
        Icons.Outlined.HelpOutline,
        MaterialTheme.colorScheme.onSurfaceVariant,
        MaterialTheme.colorScheme.surfaceVariant
    )
}

private fun insightTitle(type: TransitInsightType, lang: AppLanguage): String = when (type) {
    TransitInsightType.MOST_USED_LINE -> localized(lang, "En sık kullanılan hat", "Häufigste Linie", "Most used line")
    TransitInsightType.MOST_USED_ORIGIN -> localized(lang, "En sık başlangıç", "Häufigster Start", "Most used origin")
    TransitInsightType.MOST_USED_DESTINATION -> localized(lang, "En sık varış", "Häufigstes Ziel", "Most used destination")
    TransitInsightType.MOST_USED_TRANSFER -> localized(lang, "En sık aktarma", "Häufigster Umstieg", "Most used transfer")
    TransitInsightType.BUSIEST_TRAVEL_DAY -> localized(lang, "En yoğun yolculuk günü", "Reisestärkster Tag", "Busiest travel day")
    TransitInsightType.BUSIEST_TIME_SLOT -> localized(lang, "En yoğun saat aralığı", "Stärkstes Zeitfenster", "Busiest time slot")
    TransitInsightType.AVERAGE_TRIP_DURATION -> localized(lang, "Ortalama yolculuk süresi", "Durchschnittliche Fahrtdauer", "Average trip duration")
    TransitInsightType.AVERAGE_WAITING_TIME -> localized(lang, "Ortalama bekleme", "Durchschnittliche Wartezeit", "Average waiting time")
    TransitInsightType.AVERAGE_DELAY -> localized(lang, "Ortalama gecikme", "Durchschnittliche Verspätung", "Average delay")
    TransitInsightType.MOST_DELAYED_LINE -> localized(lang, "En çok geciken hat", "Linie mit größter Verspätung", "Most delayed line")
    TransitInsightType.MOST_RELIABLE_LINE -> localized(lang, "En düzenli hat", "Zuverlässigste Linie", "Most reliable line")
    TransitInsightType.ROUTE_OFTEN_LONGER_THAN_PLANNED -> localized(lang, "Sık planı aşan rota", "Oft länger als geplant", "Route often longer than planned")
    TransitInsightType.USAGE_CHANGE -> localized(lang, "Aylık kullanım değişimi", "Monatliche Nutzungsänderung", "Monthly usage change")
    TransitInsightType.DELAY_CHANGE -> localized(lang, "Aylık gecikme değişimi", "Monatliche Verspätungsänderung", "Monthly delay change")
    TransitInsightType.DISTANCE_CHANGE -> localized(lang, "Aylık mesafe değişimi", "Monatliche Distanzänderung", "Monthly distance change")
    TransitInsightType.WEEKDAY_WEEKEND_DIFFERENCE -> localized(lang, "Hafta içi ve hafta sonu", "Werktag und Wochenende", "Weekday and weekend")
    TransitInsightType.MORNING_EVENING_DIFFERENCE -> localized(lang, "Sabah ve akşam", "Morgen und Abend", "Morning and evening")
    TransitInsightType.ROUTE_LINE_COMPARISON -> localized(lang, "Aynı rotadaki hatlar", "Linien auf derselben Route", "Lines on the same route")
    TransitInsightType.LINE_OR_ROUTE_TREND -> localized(lang, "Hat kullanım eğilimi", "Liniennutzung im Zeitverlauf", "Line usage trend")
    TransitInsightType.DATA_QUALITY_NOTICE -> localized(lang, "Veri kalitesi uyarısı", "Hinweis zur Datenqualität", "Data quality notice")
    TransitInsightType.INSUFFICIENT_DATA -> localized(lang, "Henüz yeterli veri yok", "Noch nicht genug Daten", "Not enough data yet")
}

private fun insightResult(insight: TransitInsight, lang: AppLanguage): String {
    val subject = insight.subject?.takeIf { it.isNotBlank() }
    val value = when (insight.type) {
        TransitInsightType.AVERAGE_TRIP_DURATION,
        TransitInsightType.AVERAGE_WAITING_TIME,
        TransitInsightType.AVERAGE_DELAY,
        TransitInsightType.MOST_DELAYED_LINE,
        TransitInsightType.ROUTE_LINE_COMPARISON -> "${insight.value} min"
        TransitInsightType.MOST_RELIABLE_LINE,
        TransitInsightType.ROUTE_OFTEN_LONGER_THAN_PLANNED -> "${insight.value}%"
        TransitInsightType.DATA_QUALITY_NOTICE -> localized(
            lang,
            "Güven puanı ${insight.value}/100",
            "Vertrauenswert ${insight.value}/100",
            "Confidence score ${insight.value}/100"
        )
        TransitInsightType.INSUFFICIENT_DATA -> localized(
            lang,
            "${insight.value} kayıt bulundu",
            "${insight.value} Einträge gefunden",
            "${insight.value} records found"
        )
        else -> insight.value
    }
    return if (subject == null) value else "$subject · $value"
}

private fun factorLabel(type: TransitInsightConfidenceFactorType, lang: AppLanguage): String = when (type) {
    TransitInsightConfidenceFactorType.SAMPLE_SIZE -> localized(lang, "Örnek sayısı", "Stichprobengröße", "Sample size")
    TransitInsightConfidenceFactorType.DATA_HEALTH -> localized(lang, "Veri sağlığı", "Datenqualität", "Data health")
    TransitInsightConfidenceFactorType.MISSING_ACTUAL_TIMES -> localized(lang, "Eksik gerçek saat", "Fehlende Ist-Zeit", "Missing actual times")
    TransitInsightConfidenceFactorType.UNKNOWN_PROVENANCE -> localized(lang, "Bilinmeyen kaynak", "Unbekannte Quelle", "Unknown provenance")
    TransitInsightConfidenceFactorType.PERIOD_IMBALANCE -> localized(lang, "Dönem farkı", "Periodenunterschied", "Period imbalance")
    TransitInsightConfidenceFactorType.OUTLIER_INFLUENCE -> localized(lang, "Aşırı değerler", "Ausreißer", "Outliers")
}

private fun localized(lang: AppLanguage, tr: String, de: String, en: String): String = when (lang) {
    AppLanguage.TR -> tr
    AppLanguage.DE -> de
    AppLanguage.EN -> en
}
