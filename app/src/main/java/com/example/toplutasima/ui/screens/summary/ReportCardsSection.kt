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
@Composable
internal fun ReportCardsSection(
    reportCards: TravelReportCards,
    reportLabel: String,
    lang: AppLanguage
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            S.reportCards(lang),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "${S.monthlyReport(lang)} - $reportLabel",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ReportMetric(
                        label = S.totalTrips(lang),
                        value = reportCards.monthly.totalTrips.toString(),
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    ReportMetric(
                        label = S.avgDelay(lang),
                        value = String.format(java.util.Locale.US, "%.1f ${S.minutesShort(lang)}", reportCards.monthly.avgDelay),
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ReportMetric(
                        label = S.reportTopLine(lang),
                        value = reportCards.monthly.topLine,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    ReportMetric(
                        label = S.reportBusiestDay(lang),
                        value = "${reportCards.monthly.busiestDay} (${reportCards.monthly.busiestDayTrips})",
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                if (reportCards.monthly.totalDistanceKm > 0.0) {
                    ReportMetric(
                        label = S.totalDistance(lang),
                        value = String.format(java.util.Locale.US, "%.2f km", reportCards.monthly.totalDistanceKm),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        if (reportCards.weeks.isNotEmpty()) {
            var selectedWeekIndex by remember(reportLabel, reportCards.weeks.size) {
                mutableStateOf(
                    reportCards.weeks
                        .withIndex()
                        .maxByOrNull { it.value.trips }
                        ?.index ?: 0
                )
            }
            val weekly = reportCards.weeks[selectedWeekIndex.coerceIn(reportCards.weeks.indices)]

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "${S.weeklyReport(lang)} - ${S.reportWeekRange(weekly.startDay, weekly.endDay, lang)}",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    selectedWeekIndex =
                                        if (selectedWeekIndex == 0) reportCards.weeks.lastIndex else selectedWeekIndex - 1
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = "Onceki hafta",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            FilledTonalButton(
                                onClick = {
                                    selectedWeekIndex = (selectedWeekIndex + 1) % reportCards.weeks.size
                                },
                                modifier = Modifier.height(32.dp),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    "${selectedWeekIndex + 1}/${reportCards.weeks.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(
                                onClick = {
                                    selectedWeekIndex = (selectedWeekIndex + 1) % reportCards.weeks.size
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Sonraki hafta",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ReportMetric(
                            label = S.reportWeekTrips(lang),
                            value = "${weekly.trips} ${S.tripsShort(lang)}",
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        ReportMetric(
                            label = S.reportActiveDays(lang),
                            value = "${weekly.activeDays}/${weekly.endDay - weekly.startDay + 1}",
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ReportMetric(
                            label = S.avgDelay(lang),
                            value = String.format(java.util.Locale.US, "%.1f ${S.minutesShort(lang)}", weekly.avgDelay),
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        ReportMetric(
                            label = S.reportBusiestDay(lang),
                            value = "${S.reportDayNumber(weekly.busiestDay, lang)} (${weekly.busiestDayTrips})",
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    if (weekly.totalDistance > 0.0) {
                        ReportMetric(
                            label = S.totalDistance(lang),
                            value = String.format(java.util.Locale.US, "%.2f km", weekly.totalDistance),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}
