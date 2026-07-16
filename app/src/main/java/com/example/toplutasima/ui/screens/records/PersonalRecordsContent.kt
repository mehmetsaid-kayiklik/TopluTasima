package com.example.toplutasima.ui.screens.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toplutasima.model.PersonalTrip
import com.example.toplutasima.ui.S
import com.example.toplutasima.ui.components.AddPersonalTripDialog
import com.example.toplutasima.ui.components.PersonalTripCard
import com.example.toplutasima.viewmodel.PersonalTripViewModel
import com.example.toplutasima.viewmodel.personaltrip.PersonalTripUiState

// ── Kişisel Kayıtlar İçeriği (RecordsScreen içinde gösterilir) ───────────────
@Composable
internal fun PersonalRecordsContent(
    uiState: PersonalTripUiState,
    lang: com.example.toplutasima.ui.AppLanguage,
    viewModel: PersonalTripViewModel,
    onBack: () -> Unit
) {
    val trips = uiState.trips.filter { it.durum == PersonalTrip.DURUM_TAMAMLANDI }
    val months = remember(trips) {
        trips.map { it.yearMonth }.filter { it.isNotBlank() }.distinct().sortedDescending()
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Geri", modifier = Modifier.rotate(-90f))
            }
            Text(
                S.personalTitle(lang),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }
        Divider()

        // Body
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                trips.isEmpty() -> Text(
                    S.noRecords(lang),
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Ay filtreleri
                        if (months.size > 1) {
                            item {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                ) {
                                    item {
                                        FilterChip(
                                            selected = uiState.selectedYearMonth == null,
                                            onClick = { viewModel.setMonthFilter(null) },
                                            label = { Text(S.all(lang), fontSize = 12.sp) }
                                        )
                                    }
                                    items(months) { ym ->
                                        FilterChip(
                                            selected = uiState.selectedYearMonth == ym,
                                            onClick = { viewModel.setMonthFilter(ym) },
                                            label = { Text(ym, fontSize = 12.sp) }
                                        )
                                    }
                                }
                            }
                        }

                        // Kayıt kartları
                        items(trips, key = { it.id }) { trip ->
                            PersonalTripCard(
                                trip = trip,
                                liveDistanceKm = if (trip.durum == PersonalTrip.DURUM_AKTIF) uiState.liveDistanceKm else 0.0,
                                lang = lang,
                                viewModel = viewModel
                            )
                        }
                    }
                }
            }
        }
    }

    // Düzenleme diyaloğu — Records ekranındaki kartlara tıklayınca burada açılır
    if (uiState.showAddDialog) {
        AddPersonalTripDialog(
            editingTrip = uiState.editingTrip,
            lang = lang,
            viewModel = viewModel,
            readyPlateSuggestions = uiState.readyPlateSuggestions,
            onDismiss = { viewModel.closeDialog() }
        )
    }
}
