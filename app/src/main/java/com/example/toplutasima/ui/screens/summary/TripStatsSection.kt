package com.example.toplutasima.ui.screens.summary

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.EventSeat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.toplutasima.model.SummaryData
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.S
import com.example.toplutasima.ui.components.HeatmapCalendar
import com.example.toplutasima.ui.components.SummaryCard
import com.example.toplutasima.ui.components.formatMin
import com.example.toplutasima.ui.theme.AccentDark
import com.example.toplutasima.ui.theme.AccentLight
import com.example.toplutasima.ui.theme.SurfaceD2
import com.example.toplutasima.ui.theme.SurfaceL2
import com.example.toplutasima.ui.theme.TextHighDark
import com.example.toplutasima.ui.theme.TextHighLight
import com.example.toplutasima.ui.theme.TextMidDark
import com.example.toplutasima.ui.theme.TextMidLight
import com.example.toplutasima.usecase.HeatmapMetric
import com.example.toplutasima.viewmodel.SummaryUiState

@Composable
private fun isDark() = isSystemInDarkTheme()

@Composable
private fun summarySurface() = if (isDark()) SurfaceD2 else SurfaceL2

internal fun LazyListScope.TripStatsSection(
    state: SummaryUiState,
    s: SummaryData,
    heatmapMetric: HeatmapMetric,
    onHeatmapMetricChange: (HeatmapMetric) -> Unit,
    displaySheet: (String) -> String,
    lang: AppLanguage
) {
    val typeEntries = summaryVehicleTypeEntries

    item {
        val dark = isDark()
        val surface = if (dark) SurfaceD2 else SurfaceL2
        val textHigh = if (dark) TextHighDark else TextHighLight
        val textMid = if (dark) TextMidDark else TextMidLight
        val accent = if (dark) AccentDark else AccentLight

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(containerColor = surface)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        S.totalTrips(lang),
                        style = MaterialTheme.typography.labelLarge,
                        color = textMid
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${s.totalTrips}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = textHigh
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        SummaryIndicator(
                            icon = Icons.Outlined.EventSeat,
                            value = "${s.seatedCount}",
                            label = S.seated(lang),
                            accent = accent,
                            textHigh = textHigh,
                            textMid = textMid
                        )
                        SummaryIndicator(
                            icon = Icons.Outlined.ConfirmationNumber,
                            value = "${s.ticketControlCount}",
                            label = S.control(lang),
                            accent = accent,
                            textHigh = textHigh,
                            textMid = textMid
                        )
                    }
                }
            }
        }
    }

    item {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = summarySurface())
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    S.vehicleTypes(lang),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                val maxCount = s.types.values.maxOrNull()?.toFloat() ?: 1f
                typeEntries.forEach { (typeKey, _) ->
                    val count = s.types[typeKey] ?: 0
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                S.vehicleTypeName(typeKey, lang),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "$count",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { if (maxCount > 0) count / maxCount else 0f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )
                    }
                }
            }
        }
    }

    item {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = summarySurface())
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    S.tripsByDay(lang),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                val dayMax = s.days.values.maxOrNull()?.toFloat() ?: 1f
                val dayKeys = listOf(
                    "Pazartesi",
                    "Sal\u0131",
                    "\u00C7ar\u015Famba",
                    "Per\u015Fembe",
                    "Cuma",
                    "Cumartesi",
                    "Pazar"
                )
                dayKeys.forEach { dayKey ->
                    val count = s.days[dayKey] ?: 0
                    Column(modifier = Modifier.padding(vertical = 3.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                S.dayName(dayKey, lang),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "$count",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(3.dp))
                        LinearProgressIndicator(
                            progress = { if (dayMax > 0) count / dayMax else 0f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        )
                    }
                }
            }
        }
    }

    if (state.weekdayWeekendStats.weekday.trips > 0 || state.weekdayWeekendStats.weekend.trips > 0) {
        item {
            WeekdayWeekendCard(
                stats = state.weekdayWeekendStats,
                lang = lang
            )
        }
    }

    if (s.timeSlotStats.isNotEmpty()) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = summarySurface())
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        S.timeSlotAnalysis(lang),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    val slotMax = s.timeSlotStats.maxOfOrNull { it.trips }?.toFloat() ?: 1f
                    s.timeSlotStats.forEach { slot ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    S.timeSlotName(slot.key, lang),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "${slot.trips} ${S.tripsShort(lang)}",
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.height(3.dp))
                            LinearProgressIndicator(
                                progress = { if (slotMax > 0) slot.trips / slotMax else 0f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                            )
                            Text(
                                "${S.avgShort(lang)} +${String.format(java.util.Locale.US, "%.1f", slot.avgDelay)} " +
                                    "${S.minutesShort(lang)} \u2022 ${S.punctualShort(lang)} %${slot.punctualityRate}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (s.topLines.isNotEmpty()) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = summarySurface())
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        S.tripsByLine(lang),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    val lineMax = s.topLines.values.maxOrNull()?.toFloat() ?: 1f
                    s.topLines.forEach { (lineName, count) ->
                        Column(modifier = Modifier.padding(vertical = 3.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(lineName, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "$count",
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                            Spacer(Modifier.height(3.dp))
                            LinearProgressIndicator(
                                progress = { if (lineMax > 0) count / lineMax else 0f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.tertiary,
                                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                            )
                        }
                    }
                }
            }
        }
    }

    if (s.routePairs.isNotEmpty()) {
        item {
            var expandedRoutePairKeys by remember(s.routePairs) { mutableStateOf(setOf<String>()) }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = summarySurface())
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        S.routePairAnalysis(lang),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    val routeMax = s.routePairs.maxOfOrNull { it.trips }?.toFloat() ?: 1f
                    s.routePairs.forEach { route ->
                        val routeKey = "${route.fromStop}\u0000${route.toStop}"
                        val expanded = routeKey in expandedRoutePairKeys
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    expandedRoutePairKeys =
                                        if (expanded) {
                                            expandedRoutePairKeys - routeKey
                                        } else {
                                            expandedRoutePairKeys + routeKey
                                        }
                                }
                                .animateContentSize()
                                .padding(vertical = 5.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "${route.fromStop} \u2192 ${route.toStop}",
                                    modifier = Modifier.weight(1f),
                                    maxLines = if (expanded) Int.MAX_VALUE else 1,
                                    overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "${route.trips}",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                            if (expanded) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "${S.boardingStop(lang)}: ${route.fromStop}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "${S.alightingStop(lang)}: ${route.toStop}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(3.dp))
                            LinearProgressIndicator(
                                progress = { if (routeMax > 0) route.trips / routeMax else 0f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.tertiary,
                                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                            )
                            Text(
                                "${S.avgShort(lang)} ${formatMin(route.avgDurationMin, lang)} \u2022 " +
                                    "+${String.format(java.util.Locale.US, "%.1f", route.avgDelay)} " +
                                    "${S.minutesShort(lang)} \u2022 ${route.fastestMin}-${route.slowestMin} " +
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

    if (s.weatherCounts.isNotEmpty()) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = summarySurface())
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        S.weatherStats(lang),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    val weatherMax = s.weatherCounts.values.maxOrNull()?.toFloat() ?: 1f
                    S.weatherOptions.forEach { (key, _) ->
                        val count = s.weatherCounts[key] ?: 0
                        if (count > 0) {
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        S.weatherName(key, lang),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        "$count",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { if (weatherMax > 0) count / weatherMax else 0f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = MaterialTheme.colorScheme.tertiary,
                                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                )
                            }
                        }
                    }
                    val predefinedKeys = S.weatherOptions.map { it.first }.toSet()
                    s.weatherCounts
                        .filter { it.key !in predefinedKeys && it.value > 0 }
                        .forEach { (key, count) ->
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(key, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "$count",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                }
            }
        }
    }

    if (s.totalDistanceKm > 0) {
        item {
            SummaryCard(S.totalDistance(lang), formatDistanceKm(s.totalDistanceKm))
        }
    }

    item {
        Text(
            S.personalRecords(lang),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
    item {
        SummaryCard(
            S.recordLongestDay(lang),
            "${s.recordLongestDay} (${formatMin(s.recordLongestDayMin, lang)})"
        )
    }
    item {
        SummaryCard(
            S.recordMostTripsDay(lang),
            "${s.recordMostTripsDay} (${s.recordMostTripsCount} ${S.tripsCount(lang)})"
        )
    }
    item {
        SummaryCard(
            S.recordMostDelayed(lang),
            "${s.recordMostDelayedLine} (${s.recordMostDelayedLineMin} ${S.minutesShort(lang)})"
        )
    }
    item {
        SummaryCard(
            S.recordTotalDelayed(lang),
            "${s.recordTotalDelayLine} (${s.recordTotalDelayLineMin} ${S.minutesShort(lang)})"
        )
    }
    item { SummaryCard(S.recordFreqLine(lang), s.freqLine) }
    item { SummaryCard(S.recordFreqFrom(lang), s.freqFrom) }
    item { SummaryCard(S.recordFreqTo(lang), s.freqTo) }

    if (s.recordShortestTripMin > 0) {
        item {
            SummaryCard(
                S.recordShortestTrip(lang),
                "${s.recordShortestTrip} (${formatMin(s.recordShortestTripMin, lang)})"
            )
        }
    }
    if (s.recordLongestTripMin > 0) {
        item {
            SummaryCard(
                S.recordLongestTrip(lang),
                "${s.recordLongestTrip} (${formatMin(s.recordLongestTripMin, lang)})"
            )
        }
    }
    if (s.recordLongestDistanceKm > 0) {
        item {
            SummaryCard(
                S.recordLongestDistance(lang),
                "${s.recordLongestDistanceTrip} (${formatDistanceKm(s.recordLongestDistanceKm)})"
            )
        }
    }

    if (s.monthlyTrend.isNotEmpty()) {
        item {
            MonthlyTrendCard(
                trendData = s.monthlyTrend,
                lang = lang
            )
        }
    }

    state.reportCards?.let { reportCards ->
        if (state.reportSheetName.isNotBlank()) {
            item {
                ReportCardsSection(
                    reportCards = reportCards,
                    reportLabel = displaySheet(state.reportSheetName),
                    lang = lang
                )
            }
        }
    }

    state.heatmapData?.let { hm ->
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = summarySurface())
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        S.heatmapTitle(lang),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            HeatmapMetric.TRIPS to S.heatmapMetricTrips(lang),
                            HeatmapMetric.AVG_DELAY to S.heatmapMetricDelay(lang),
                            HeatmapMetric.TICKET_CONTROL to S.heatmapMetricTicket(lang),
                            HeatmapMetric.SEATED to S.heatmapMetricSeated(lang)
                        ).forEach { (metric, label) ->
                            FilterChip(
                                selected = heatmapMetric == metric,
                                onClick = { onHeatmapMetricChange(metric) },
                                label = {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    HeatmapCalendar(
                        data = hm,
                        metric = heatmapMetric,
                        lang = lang,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    S.streakCurrent(lang),
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(
                                    "${hm.currentStreak}",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    S.streakDays(lang),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    S.streakLongest(lang),
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(
                                    "${hm.longestStreak}",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    S.streakDays(lang),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    S.streakActiveDays(lang),
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(
                                    "${hm.activeDays}/${hm.totalDays}",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    S.streakDays(lang),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryIndicator(
    icon: ImageVector,
    value: String,
    label: String,
    accent: androidx.compose.ui.graphics.Color,
    textHigh: androidx.compose.ui.graphics.Color,
    textMid: androidx.compose.ui.graphics.Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.height(18.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            fontWeight = FontWeight.Bold,
            color = textHigh
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = textMid
        )
    }
}
