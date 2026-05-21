package com.example.toplutasima.ui.screens.summary

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toplutasima.model.SummaryData
import com.example.toplutasima.model.VehicleType
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.ErrorRed
import com.example.toplutasima.ui.S
import com.example.toplutasima.ui.SuccessGreen
import com.example.toplutasima.ui.WarningAmber
import com.example.toplutasima.ui.components.HeatmapCalendar
import com.example.toplutasima.ui.components.RmvFooter
import com.example.toplutasima.ui.components.SummaryCard
import com.example.toplutasima.ui.components.formatMin
import com.example.toplutasima.usecase.HeatmapMetric
import com.example.toplutasima.usecase.TravelReportCards
import com.example.toplutasima.viewmodel.SummaryUiState
internal fun LazyListScope.DurationDelaySection(
    s: SummaryData,
    lang: AppLanguage
) {
    val typeEntries = summaryVehicleTypeEntries
                            // Gecikme Dağılımı
                            if (s.delayDistribution.isNotEmpty()) {
                                item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(S.delayDistribution(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.height(12.dp))
                                        val bucketMax = s.delayDistribution.maxOfOrNull { it.count }?.toFloat() ?: 1f
                                        s.delayDistribution.forEach { bucket ->
                                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text(S.delayBucketName(bucket.key, lang), style = MaterialTheme.typography.bodyMedium)
                                                    Text("${bucket.count}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                }
                                                Spacer(Modifier.height(4.dp))
                                                LinearProgressIndicator(
                                                    progress = { if (bucketMax > 0) bucket.count / bucketMax else 0f },
                                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
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

                            // Dakiklik oranları
                                }
                            item {
                                Text(S.punctualityRates(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    typeEntries.forEach { (typeKey, emoji) ->
                                        val rate = s.punctualityRates[typeKey] ?: 0
                                        Column {
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("$emoji  ${S.vehicleTypeName(typeKey, lang)}", style = MaterialTheme.typography.bodyMedium)
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
                                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                                color = if (rate > 80) SuccessGreen else if (rate > 50) WarningAmber else ErrorRed,
                                                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                            )
                                        }
                                    }
                                }
                            }

                            // Hat Güvenilirliği
                            }
                            if (s.lineReliability.isNotEmpty()) {
                                item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(S.lineReliability(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.height(12.dp))
                                        s.lineReliability.forEach { line ->
                                            Column(modifier = Modifier.padding(vertical = 5.dp)) {
                                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text(line.line, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                                    Text("%${line.punctualityRate}", fontWeight = FontWeight.Bold, color = if (line.punctualityRate > 80) SuccessGreen else if (line.punctualityRate > 50) WarningAmber else ErrorRed)
                                                }
                                                Spacer(Modifier.height(4.dp))
                                                LinearProgressIndicator(
                                                    progress = { line.punctualityRate / 100f },
                                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                                    color = if (line.punctualityRate > 80) SuccessGreen else if (line.punctualityRate > 50) WarningAmber else ErrorRed,
                                                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                                )
                                                Text(
                                                    "${line.trips} ${S.tripsShort(lang)} • ${S.avgShort(lang)} +${String.format(java.util.Locale.US, "%.1f", line.avgDelay)} ${S.minutesShort(lang)} • ${S.maxShort(lang)} +${line.maxDelay} ${S.minutesShort(lang)}",
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
                            item { SummaryCard(S.avgDelay(lang), String.format(java.util.Locale.US, "%.1f ${S.minutes(lang)}", s.avgDelay)) }
}