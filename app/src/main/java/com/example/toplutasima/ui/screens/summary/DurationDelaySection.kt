package com.example.toplutasima.ui.screens.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.toplutasima.model.SummaryData
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.ErrorRed
import com.example.toplutasima.ui.S
import com.example.toplutasima.ui.SuccessGreen
import com.example.toplutasima.ui.WarningAmber
import com.example.toplutasima.ui.components.SummaryCard
import com.example.toplutasima.ui.components.formatMin

internal fun LazyListScope.DurationDelaySection(
    s: SummaryData,
    lang: AppLanguage
) {
    val typeEntries = summaryVehicleTypeEntries

    if (s.delayDistribution.isNotEmpty()) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        S.delayDistribution(lang),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    val bucketMax = s.delayDistribution.maxOfOrNull { it.count }?.toFloat() ?: 1f
                    s.delayDistribution.forEach { bucket ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    S.delayBucketName(bucket.key, lang),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "${bucket.count}",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { if (bucketMax > 0) bucket.count / bucketMax else 0f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = when (bucket.key) {
                                    "zero" -> SuccessGreen
                                    "low" -> MaterialTheme.colorScheme.primary
                                    "medium" -> WarningAmber
                                    else -> ErrorRed
                                },
                                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                            )
                        }
                    }
                }
            }
        }
    }

    item {
        Text(
            S.punctualityRates(lang),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
    item {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                typeEntries.forEach { (typeKey, emoji) ->
                    val rate = s.punctualityRates[typeKey] ?: 0
                    Column {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "$emoji  ${S.vehicleTypeName(typeKey, lang)}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "%$rate",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (rate > 80) SuccessGreen else if (rate > 50) WarningAmber else ErrorRed
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { rate / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (rate > 80) SuccessGreen else if (rate > 50) WarningAmber else ErrorRed,
                            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        )
                    }
                }
            }
        }
    }

    if (s.lineReliability.isNotEmpty()) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        S.lineReliability(lang),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    s.lineReliability.forEach { line ->
                        Column(modifier = Modifier.padding(vertical = 5.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    line.line,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "%${line.punctualityRate}",
                                    fontWeight = FontWeight.Bold,
                                    color = if (line.punctualityRate > 80) {
                                        SuccessGreen
                                    } else if (line.punctualityRate > 50) {
                                        WarningAmber
                                    } else {
                                        ErrorRed
                                    }
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { line.punctualityRate / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = if (line.punctualityRate > 80) {
                                    SuccessGreen
                                } else if (line.punctualityRate > 50) {
                                    WarningAmber
                                } else {
                                    ErrorRed
                                },
                                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                            )
                            Text(
                                "${line.trips} ${S.tripsShort(lang)} \u2022 " +
                                    "${S.avgShort(lang)} +${String.format(java.util.Locale.US, "%.1f", line.avgDelay)} " +
                                    "${S.minutesShort(lang)} \u2022 ${S.maxShort(lang)} +${line.maxDelay} " +
                                    S.minutesShort(lang),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    item { SummaryCard(S.totalPlanned(lang), formatMin(s.totalPlannedMin, lang)) }
    item { SummaryCard(S.totalActual(lang), formatMin(s.totalActualMin, lang)) }
    item { SummaryCard(S.totalDelay(lang), "${s.totalDelay} ${S.minutes(lang)}") }
    item {
        SummaryCard(
            S.avgDelay(lang),
            String.format(java.util.Locale.US, "%.1f ${S.minutes(lang)}", s.avgDelay)
        )
    }
}
