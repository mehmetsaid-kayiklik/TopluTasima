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
internal fun LazyListScope.InsightSection(
    s: SummaryData,
    lang: AppLanguage
) {
                            val worstLine = s.lineReliability.minByOrNull { it.punctualityRate }
                            val slowestRoute = s.routePairs.maxByOrNull { it.avgDelay }
                            val busiestSlot = s.timeSlotStats.maxByOrNull { it.trips }
                            if (worstLine != null || slowestRoute != null || busiestSlot != null) {
                                item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text(S.smartInsights(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        worstLine?.let {
                                            Text(
                                                "${S.insightWeakLine(lang)}: ${it.line} - %${it.punctualityRate}, +${String.format(java.util.Locale.US, "%.1f", it.avgDelay)} ${S.minutesShort(lang)}",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                        busiestSlot?.let {
                                            Text(
                                                "${S.insightBusySlot(lang)}: ${S.timeSlotName(it.key, lang)} - ${it.trips} ${S.tripsShort(lang)}",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                        slowestRoute?.let {
                                            Text(
                                                "${S.insightSlowRoute(lang)}: ${it.fromStop} → ${it.toStop} - +${String.format(java.util.Locale.US, "%.1f", it.avgDelay)} ${S.minutesShort(lang)}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                }
                            }
}

internal fun LazyListScope.ComparisonInsightSection(
    state: SummaryUiState,
    lang: AppLanguage
) {
                            // ── Tab 2: Monthly Comparison ──
                            item {
                                Text(S.comparisonTitle(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }

                            if (state.isComparisonLoading) {
                                item {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Text(S.comparisonLoading(lang), style = MaterialTheme.typography.bodyMedium)
                                }
                                }
                            } else if (state.comparisonDeltas.isEmpty()) {
                                item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Text(
                                        S.comparisonNoData(lang),
                                        modifier = Modifier.padding(20.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                }
                            } else {
                                // Previous month label
                                item {
                                    Text(
                                        "${S.comparisonPrevMonth(lang)}: ${state.previousMonthName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                item { Spacer(Modifier.height(8.dp)) }

                                // Delta rows
                                for (delta in state.comparisonDeltas) {
                                    item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(delta.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(delta.currentValue, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                val deltaColor = when {
                                                    delta.isNeutral -> MaterialTheme.colorScheme.onSurfaceVariant
                                                    delta.isPositive -> SuccessGreen
                                                    else -> ErrorRed
                                                }
                                                val arrow = when {
                                                    delta.isNeutral -> "→"
                                                    delta.deltaPercent > 0 -> "↑"
                                                    else -> "↓"
                                                }
                                                Text(
                                                    "$arrow ${delta.deltaText}",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = deltaColor
                                                )
                                                if (!delta.isNeutral) {
                                                    Text(
                                                        String.format(java.util.Locale.US, "%.0f%%", delta.deltaPercent * 100),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = deltaColor.copy(alpha = 0.7f)
                                                    )
                                                }
                                                Text(
                                                    delta.previousValue,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                    }
                                }
                            }
}
