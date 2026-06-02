package com.example.toplutasima.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.toplutasima.model.PersonalTrip
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.S
import com.example.toplutasima.ui.theme.AccentDark
import com.example.toplutasima.ui.theme.AccentLight
import com.example.toplutasima.ui.theme.SurfaceD2
import com.example.toplutasima.ui.theme.SurfaceL2
import com.example.toplutasima.ui.theme.TextHighDark
import com.example.toplutasima.ui.theme.TextHighLight
import com.example.toplutasima.ui.theme.TextMidDark
import com.example.toplutasima.ui.theme.TextMidLight
import com.example.toplutasima.ui.theme.isAppInDarkTheme
import com.example.toplutasima.viewmodel.PersonalTripViewModel
import org.koin.androidx.compose.koinViewModel
import java.util.Locale

@Composable
private fun isDark() = isAppInDarkTheme()

@Composable
fun PersonalSummaryContent(lang: AppLanguage) {
    val vm: PersonalTripViewModel = koinViewModel()
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val trips = remember(uiState.trips) {
        uiState.trips.filter { it.durum == PersonalTrip.DURUM_TAMAMLANDI }
    }
    val dark = isDark()
    val cardSurface = if (dark) SurfaceD2 else SurfaceL2
    val accent = if (dark) AccentDark else AccentLight
    val textHigh = if (dark) TextHighDark else TextHighLight
    val textMid = if (dark) TextMidDark else TextMidLight

    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (trips.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                S.noRecords(lang),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
                color = textMid
            )
        }
        return
    }

    val totalTrips = trips.size
    val totalDist = remember(trips) { trips.sumOf { it.mesafe.replace(" km", "").replace(",", ".").toDoubleOrNull() ?: 0.0 } }
    val avgDuration = remember(trips) {
        val d = trips.mapNotNull { it.yolSuresi.toDoubleOrNull() }
        if (d.isNotEmpty()) d.average() else 0.0
    }
    val weatherCounts = remember(trips) { trips.groupingBy { it.havaDurumu }.eachCount().toList().sortedByDescending { it.second } }
    val vehicleCounts = remember(trips) { trips.groupingBy { it.aracTuru }.eachCount().toList().sortedByDescending { it.second } }
    val monthlyCounts = remember(trips) { trips.groupingBy { it.yearMonth }.eachCount().toList().filter { it.first.isNotBlank() }.sortedByDescending { it.first } }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── Hero Card ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(2.dp),
            colors = CardDefaults.cardColors(containerColor = cardSurface)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(S.personalSummaryTotal(lang), style = MaterialTheme.typography.labelLarge, color = textMid)
                    Spacer(Modifier.height(4.dp))
                    Text("$totalTrips", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = textHigh)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        PersonalSummaryMetric(
                            icon = Icons.Outlined.Straighten,
                            value = if (totalDist > 0) String.format(Locale.US, "%.1f km", totalDist) else "—",
                            label = S.personalSummaryTotalDist(lang),
                            accent = accent,
                            textHigh = textHigh,
                            textMid = textMid
                        )
                        PersonalSummaryMetric(
                            icon = Icons.Outlined.AccessTime,
                            value = if (avgDuration > 0) String.format(Locale.US, "%.0f dk", avgDuration) else "—",
                            label = S.personalSummaryAvgDuration(lang),
                            accent = accent,
                            textHigh = textHigh,
                            textMid = textMid
                        )
                    }
                }
            }
        }

        // ── Araç Türleri ──
        if (vehicleCounts.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = cardSurface)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(S.personalVehicleType(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    val maxC = vehicleCounts.maxOfOrNull { it.second }?.toFloat() ?: 1f
                    vehicleCounts.forEach { (type, count) ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(type, style = MaterialTheme.typography.bodyMedium)
                                Text("$count", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { count / maxC },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            )
                        }
                    }
                }
            }
        }

        // ── Hava Durumu ──
        if (weatherCounts.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = cardSurface)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(S.weatherStats(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    val wMax = weatherCounts.maxOfOrNull { it.second }?.toFloat() ?: 1f
                    weatherCounts.forEach { (key, count) ->
                        val weatherLabel = S.weatherOptions.find { it.first == key }?.second
                            ?.let { "$it  ${S.weatherName(key, lang)}" }
                            ?: S.weatherName(key, lang)
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(weatherLabel, style = MaterialTheme.typography.bodyMedium)
                                Text("$count", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                            }
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { count / wMax },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.tertiary,
                                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            )
                        }
                    }
                }
            }
        }

        // ── Aylık Kırılım ──
        if (monthlyCounts.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = cardSurface)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(S.personalSummaryMonthly(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    val mMax = monthlyCounts.maxOfOrNull { it.second }?.toFloat() ?: 1f
                    monthlyCounts.forEach { (ym, count) ->
                        Column(modifier = Modifier.padding(vertical = 3.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(ym, style = MaterialTheme.typography.bodyMedium)
                                Text("$count", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
                            }
                            Spacer(Modifier.height(3.dp))
                            LinearProgressIndicator(
                                progress = { count / mMax },
                                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.secondary,
                                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PersonalSummaryMetric(
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
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(value, fontWeight = FontWeight.Bold, color = textHigh)
        Text(label, style = MaterialTheme.typography.labelSmall, color = textMid)
    }
}
