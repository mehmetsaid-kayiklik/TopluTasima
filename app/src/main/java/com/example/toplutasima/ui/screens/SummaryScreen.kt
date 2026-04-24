package com.example.toplutasima.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
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
import com.example.toplutasima.viewmodel.SummaryViewModel

@Composable
fun SummaryScreen(
    modifier: Modifier = Modifier,
    viewModel: SummaryViewModel = koinViewModel(),
    showPersonal: Boolean = false,
    onTogglePersonal: (Boolean) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val lang = LocaleManager.currentLanguage

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
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp
                )
            } else if (state.errorMsg.isNotBlank()) {
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
                            SummaryCard(S.totalDistance(lang), String.format("%.2f km", s.totalDistanceKm))
                        }

                        // Kişisel Rekorlar
                        Text(S.personalRecords(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                        SummaryCard(S.recordLongestDay(lang), "${s.recordLongestDay} (${formatMin(s.recordLongestDayMin, lang)})")
                        SummaryCard(S.recordMostTripsDay(lang), "${s.recordMostTripsDay} (${s.recordMostTripsCount} ${S.tripsCount(lang)})")
                        SummaryCard(S.recordMostDelayed(lang), "${s.recordMostDelayedLine} (${s.recordMostDelayedLineMin} ${S.minutesShort(lang)})")
                        SummaryCard(S.recordTotalDelayed(lang), "${s.recordTotalDelayLine} (${s.recordTotalDelayLineMin} ${S.minutesShort(lang)})")
                        SummaryCard(S.recordFreqLine(lang), s.freqLine)
                        SummaryCard(S.recordFreqFrom(lang), s.freqFrom)

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
                                    HeatmapCalendar(data = hm, modifier = Modifier.fillMaxWidth())
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

                    } else if (state.selectedInnerTab == 1) {
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

                        SummaryCard(S.totalPlanned(lang), formatMin(s.totalPlannedMin, lang))
                        SummaryCard(S.totalActual(lang), formatMin(s.totalActualMin, lang))
                        SummaryCard(S.totalDelay(lang), "${s.totalDelay} ${S.minutes(lang)}")
                        SummaryCard(S.avgDelay(lang), String.format("%.1f ${S.minutes(lang)}", s.avgDelay))
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
                                                    String.format("%.0f%%", delta.deltaPercent * 100),
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
        } // else (transit summary)
    }
}

