package com.example.toplutasima.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.animateContentSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import com.example.toplutasima.ui.ErrorRed
import com.example.toplutasima.ui.LocaleManager
import com.example.toplutasima.ui.S
import com.example.toplutasima.ui.SuccessGreen
import com.example.toplutasima.ui.WarningAmber
import com.example.toplutasima.ui.components.RmvFooter
import com.example.toplutasima.ui.components.SummaryCard
import com.example.toplutasima.ui.components.formatMin
import com.example.toplutasima.ui.components.HeatmapCalendar
import com.example.toplutasima.model.VehicleType
import com.example.toplutasima.usecase.HeatmapMetric
import com.example.toplutasima.viewmodel.SummaryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    modifier: Modifier = Modifier,
    viewModel: SummaryViewModel = koinViewModel(),
    showPersonal: Boolean = false,
    onTogglePersonal: (Boolean) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lang = LocaleManager.currentLanguage
    var heatmapMetric by remember(state.selectedSheet) { mutableStateOf(HeatmapMetric.TRIPS) }

    // Translate sheet display name (month names + "Tümü")
    fun displaySheet(name: String): String {
        if (name == "Tümü") return S.sheetAll(lang)
        val parts = name.split(" ", limit = 2)
        return if (parts.size >= 2) "${S.monthName(parts[0], lang)} ${parts[1]}"
        else S.monthName(name, lang)
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Ay seçici + Kişisel butonu
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Ay seçici (~75%)
            Box(modifier = Modifier.weight(3f)) {
                FilledTonalButton(
                    onClick = { if (!showPersonal) viewModel.setSheetMenuOpen(true) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !showPersonal
                ) {
                    Text("📅  ${displaySheet(state.selectedSheet)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                DropdownMenu(
                    expanded = state.sheetMenuOpen,
                    onDismissRequest = { viewModel.setSheetMenuOpen(false) }
                ) {
                    state.sheetNames.forEach { name ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    displaySheet(name),
                                    fontWeight = if (name == state.selectedSheet) FontWeight.Bold else FontWeight.Normal,
                                    color = if (name == state.selectedSheet) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = { viewModel.loadData(name) }
                        )
                    }
                }
            }
            // Kişisel butonu (~25%)
            FilledTonalButton(
                onClick = { onTogglePersonal(!showPersonal) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = if (showPersonal) ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) else ButtonDefaults.filledTonalButtonColors()
            ) {
                Text("🚗", style = MaterialTheme.typography.titleSmall)
            }
        }
        if (showPersonal) {
            // ── KİŞİSEL ÖZET ──
            PersonalSummaryContent(lang = lang)
        } else {
        // Modern tab row
        val tabCount = if (state.selectedSheet != "T\u00fcm\u00fc") 3 else 2
        TabRow(
            selectedTabIndex = state.selectedInnerTab.coerceAtMost(tabCount - 1),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Tab(
                selected = state.selectedInnerTab == 0,
                onClick = { viewModel.setSelectedInnerTab(0) },
                text = { Text(S.tabTripsRecords(lang), fontWeight = if (state.selectedInnerTab == 0) FontWeight.Bold else FontWeight.Normal) }
            )
            Tab(
                selected = state.selectedInnerTab == 1,
                onClick = { viewModel.setSelectedInnerTab(1) },
                text = { Text(S.tabDurationDelay(lang), fontWeight = if (state.selectedInnerTab == 1) FontWeight.Bold else FontWeight.Normal) }
            )
            if (state.selectedSheet != "T\u00fcm\u00fc") {
                Tab(
                    selected = state.selectedInnerTab == 2,
                    onClick = { viewModel.setSelectedInnerTab(2) },
                    text = { Text(S.tabComparison(lang), fontWeight = if (state.selectedInnerTab == 2) FontWeight.Bold else FontWeight.Normal) }
                )
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (state.isLoading && state.summary == null) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp
                )
            } else if (state.errorMsg.isNotBlank() && state.summary == null) {
                Card(
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        "⚠️ ${state.errorMsg}",
                        modifier = Modifier.padding(20.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center
                    )
                }
            } else if (state.summary != null) {
                val s = state.summary!!
                PullToRefreshBox(
                    isRefreshing = state.isLoading,
                    onRefresh = { viewModel.refreshData() },
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        val typeEntries = listOf(
                            VehicleType.BUS.key to "🚌",
                            VehicleType.SBAHN.key to "🚆",
                            VehicleType.UBAHN.key to "🚇",
                            VehicleType.RERB.key to "🚂",
                            VehicleType.FERNZUG.key to "🚄",
                            VehicleType.STRASSENBAHN.key to "🚋"
                        )

                        if (state.selectedInnerTab == 0) {
                            // HERO CARD
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(brush = Brush.linearGradient(colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)))
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(S.totalTrips(lang), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.85f))
                                        Spacer(Modifier.height(4.dp))
                                        Text("${s.totalTrips}", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Black, color = Color.White)
                                        Spacer(Modifier.height(12.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text("💺", fontSize = 20.sp)
                                                Text("${s.seatedCount}", fontWeight = FontWeight.Bold, color = Color.White)
                                                Text(S.seated(lang), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                                            }
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text("🎫", fontSize = 20.sp)
                                                Text("${s.ticketControlCount}", fontWeight = FontWeight.Bold, color = Color.White)
                                                Text(S.control(lang), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                                            }
                                        }
                                    }
                                }
                            }

                            // Araç Türleri
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(S.vehicleTypes(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(12.dp))
                                    val maxCount = s.types.values.maxOrNull()?.toFloat() ?: 1f
                                    typeEntries.forEach { (typeKey, emoji) ->
                                        val count = s.types[typeKey] ?: 0
                                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("$emoji  ${S.vehicleTypeName(typeKey, lang)}", style = MaterialTheme.typography.bodyMedium)
                                                Text("$count", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            }
                                            Spacer(Modifier.height(4.dp))
                                            LinearProgressIndicator(
                                                progress = { if (maxCount > 0) count / maxCount else 0f },
                                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                                color = MaterialTheme.colorScheme.primary,
                                                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                            )
                                        }
                                    }
                                }
                            }

                            // Günlere Göre Sefer
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(S.tripsByDay(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(12.dp))
                                    val dayMax = s.days.values.maxOrNull()?.toFloat() ?: 1f
                                    val dayKeys = listOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar")
                                    dayKeys.forEach { dayKey ->
                                        val count = s.days[dayKey] ?: 0
                                        Column(modifier = Modifier.padding(vertical = 3.dp)) {
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text(S.dayName(dayKey, lang), style = MaterialTheme.typography.bodyMedium)
                                                Text("$count", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                            }
                                            Spacer(Modifier.height(3.dp))
                                            LinearProgressIndicator(
                                                progress = { if (dayMax > 0) count / dayMax else 0f },
                                                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                                                color = MaterialTheme.colorScheme.secondary,
                                                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                            )
                                        }
                                    }
                                }
                            }

                            // Saat Dilimi Analizi
                            if (s.timeSlotStats.isNotEmpty()) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(S.timeSlotAnalysis(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.height(12.dp))
                                        val slotMax = s.timeSlotStats.maxOfOrNull { it.trips }?.toFloat() ?: 1f
                                        s.timeSlotStats.forEach { slot ->
                                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text(S.timeSlotName(slot.key, lang), style = MaterialTheme.typography.bodyMedium)
                                                    Text("${slot.trips} ${S.tripsShort(lang)}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                                }
                                                Spacer(Modifier.height(3.dp))
                                                LinearProgressIndicator(
                                                    progress = { if (slotMax > 0) slot.trips / slotMax else 0f },
                                                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                                )
                                                Text(
                                                    "${S.avgShort(lang)} +${String.format(java.util.Locale.US, "%.1f", slot.avgDelay)} ${S.minutesShort(lang)} • ${S.punctualShort(lang)} %${slot.punctualityRate}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Hatlara Göre Sefer (Top 7)
                            if (s.topLines.isNotEmpty()) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(S.tripsByLine(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.height(12.dp))
                                        val lineMax = s.topLines.values.maxOrNull()?.toFloat() ?: 1f
                                        s.topLines.forEach { (lineName, count) ->
                                            Column(modifier = Modifier.padding(vertical = 3.dp)) {
                                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text(lineName, style = MaterialTheme.typography.bodyMedium)
                                                    Text("$count", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.tertiary)
                                                }
                                                Spacer(Modifier.height(3.dp))
                                                LinearProgressIndicator(
                                                    progress = { if (lineMax > 0) count / lineMax else 0f },
                                                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                                                    color = MaterialTheme.colorScheme.tertiary,
                                                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Durak Çifti Analizi
                            if (s.routePairs.isNotEmpty()) {
                                var expandedRoutePairKeys by remember(s.routePairs) { mutableStateOf(setOf<String>()) }
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(S.routePairAnalysis(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                                                            if (expanded) expandedRoutePairKeys - routeKey
                                                            else expandedRoutePairKeys + routeKey
                                                    }
                                                    .animateContentSize()
                                                    .padding(vertical = 5.dp)
                                            ) {
                                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text(
                                                        "${route.fromStop} → ${route.toStop}",
                                                        modifier = Modifier.weight(1f),
                                                        maxLines = if (expanded) Int.MAX_VALUE else 1,
                                                        overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                    Text("${route.trips}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
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
                                                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                                                    color = MaterialTheme.colorScheme.tertiary,
                                                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                                )
                                                Text(
                                                    "${S.avgShort(lang)} ${formatMin(route.avgDurationMin, lang)} • +${String.format(java.util.Locale.US, "%.1f", route.avgDelay)} ${S.minutesShort(lang)} • ${route.fastestMin}-${route.slowestMin} ${S.minutesShort(lang)}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Hava Durumu İstatistikleri
                            if (s.weatherCounts.isNotEmpty()) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(S.weatherStats(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.height(12.dp))
                                        val weatherMax = s.weatherCounts.values.maxOrNull()?.toFloat() ?: 1f
                                        S.weatherOptions.forEach { (key, emoji) ->
                                            val count = s.weatherCounts[key] ?: 0
                                            if (count > 0) {
                                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                        Text("$emoji  ${S.weatherName(key, lang)}", style = MaterialTheme.typography.bodyMedium)
                                                        Text("$count", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                    }
                                                    Spacer(Modifier.height(4.dp))
                                                    LinearProgressIndicator(
                                                        progress = { if (weatherMax > 0) count / weatherMax else 0f },
                                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                                        color = MaterialTheme.colorScheme.tertiary,
                                                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                                    )
                                                }
                                            }
                                        }
                                        // Show any other weather keys not in the predefined list
                                        val predefinedKeys = S.weatherOptions.map { it.first }.toSet()
                                        s.weatherCounts.filter { it.key !in predefinedKeys && it.value > 0 }.forEach { (key, count) ->
                                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text(key, style = MaterialTheme.typography.bodyMedium)
                                                    Text("$count", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Toplam Mesafe
                            if (s.totalDistanceKm > 0) {
                                SummaryCard(S.totalDistance(lang), String.format(java.util.Locale.US, "%.2f km", s.totalDistanceKm))
                            }

                            // Kişisel Rekorlar
                            Text(S.personalRecords(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                            SummaryCard(S.recordLongestDay(lang), "${s.recordLongestDay} (${formatMin(s.recordLongestDayMin, lang)})")
                            SummaryCard(S.recordMostTripsDay(lang), "${s.recordMostTripsDay} (${s.recordMostTripsCount} ${S.tripsCount(lang)})")
                            SummaryCard(S.recordMostDelayed(lang), "${s.recordMostDelayedLine} (${s.recordMostDelayedLineMin} ${S.minutesShort(lang)})")
                            SummaryCard(S.recordTotalDelayed(lang), "${s.recordTotalDelayLine} (${s.recordTotalDelayLineMin} ${S.minutesShort(lang)})")
                            SummaryCard(S.recordFreqLine(lang), s.freqLine)
                            SummaryCard(S.recordFreqFrom(lang), s.freqFrom)
                            SummaryCard(S.recordFreqTo(lang), s.freqTo)
                            if (s.recordShortestTripMin > 0) {
                                SummaryCard(S.recordShortestTrip(lang), "${s.recordShortestTrip} (${formatMin(s.recordShortestTripMin, lang)})")
                            }
                            if (s.recordLongestTripMin > 0) {
                                SummaryCard(S.recordLongestTrip(lang), "${s.recordLongestTrip} (${formatMin(s.recordLongestTripMin, lang)})")
                            }
                            if (s.recordLongestDistanceKm > 0) {
                                SummaryCard(S.recordLongestDistance(lang), "${s.recordLongestDistanceTrip} (${String.format(java.util.Locale.US, "%.2f km", s.recordLongestDistanceKm)})")
                            }

                            // ── Heatmap Calendar ──
                            if (state.heatmapData != null) {
                                val hm = state.heatmapData!!
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text(S.heatmapTitle(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            listOf(
                                                HeatmapMetric.TRIPS to S.heatmapMetricTrips(lang),
                                                HeatmapMetric.AVG_DELAY to S.heatmapMetricDelay(lang),
                                                HeatmapMetric.TICKET_CONTROL to S.heatmapMetricTicket(lang),
                                                HeatmapMetric.SEATED to S.heatmapMetricSeated(lang)
                                            ).forEach { (metric, label) ->
                                                FilterChip(
                                                    selected = heatmapMetric == metric,
                                                    onClick = { heatmapMetric = metric },
                                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                        HeatmapCalendar(data = hm, metric = heatmapMetric, lang = lang, modifier = Modifier.fillMaxWidth())
                                        Spacer(Modifier.height(4.dp))
                                        // Streak cards
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                            Card(
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(12.dp),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(S.streakCurrent(lang), style = MaterialTheme.typography.labelSmall)
                                                    Text("${hm.currentStreak}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                                    Text(S.streakDays(lang), style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                            Card(
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(12.dp),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(S.streakLongest(lang), style = MaterialTheme.typography.labelSmall)
                                                    Text("${hm.longestStreak}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                                    Text(S.streakDays(lang), style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                            Card(
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(12.dp),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(S.streakActiveDays(lang), style = MaterialTheme.typography.labelSmall)
                                                    Text("${hm.activeDays}/${hm.totalDays}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                                    Text(S.streakDays(lang), style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            val worstLine = s.lineReliability.minByOrNull { it.punctualityRate }
                            val slowestRoute = s.routePairs.maxByOrNull { it.avgDelay }
                            val busiestSlot = s.timeSlotStats.maxByOrNull { it.trips }
                            if (worstLine != null || slowestRoute != null || busiestSlot != null) {
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

                        } else if (state.selectedInnerTab == 1) {
                            // Gecikme Dağılımı
                            if (s.delayDistribution.isNotEmpty()) {
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
                            Text(S.punctualityRates(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                            if (s.lineReliability.isNotEmpty()) {
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

                            SummaryCard(S.totalPlanned(lang), formatMin(s.totalPlannedMin, lang))
                            SummaryCard(S.totalActual(lang), formatMin(s.totalActualMin, lang))
                            SummaryCard(S.totalDelay(lang), "${s.totalDelay} ${S.minutes(lang)}")
                            SummaryCard(S.avgDelay(lang), String.format(java.util.Locale.US, "%.1f ${S.minutes(lang)}", s.avgDelay))

                        } else if (state.selectedInnerTab == 2) {
                            // ── Tab 2: Monthly Comparison ──
                            Text(S.comparisonTitle(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                            if (state.isComparisonLoading) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Text(S.comparisonLoading(lang), style = MaterialTheme.typography.bodyMedium)
                                }
                            } else if (state.comparisonDeltas.isEmpty()) {
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
                            } else {
                                // Previous month label
                                Text(
                                    "${S.comparisonPrevMonth(lang)}: ${state.previousMonthName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))

                                // Delta rows
                                for (delta in state.comparisonDeltas) {
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

                        // Refresh button
                        Button(
                            onClick = { viewModel.refreshData() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isLoading,
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(vertical = 14.dp)
                        ) {
                            Text(
                                if (state.isLoading) S.refreshing(lang) else S.refreshData(lang),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        }

                        Spacer(Modifier.height(4.dp))
                        RmvFooter()
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
        } // else (transit summary)
    }
}
