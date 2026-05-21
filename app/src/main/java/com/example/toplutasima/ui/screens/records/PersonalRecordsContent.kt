package com.example.toplutasima.ui.screens.records

import android.app.DatePickerDialog
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toplutasima.model.PersonalTrip
import com.example.toplutasima.model.SeatingStatus
import com.example.toplutasima.model.TicketStatus
import com.example.toplutasima.model.VehicleType
import com.example.toplutasima.network.FirestoreService
import com.example.toplutasima.ui.LocaleManager
import com.example.toplutasima.ui.S
import com.example.toplutasima.ui.WarningAmber
import com.example.toplutasima.ui.components.AddPersonalTripDialog
import com.example.toplutasima.ui.components.PersonalTripCard
import com.example.toplutasima.ui.util.vehicleIcon
import com.example.toplutasima.usecase.ExportFormat
import com.example.toplutasima.usecase.RecordFilterState
import com.example.toplutasima.usecase.TransitTimeUtils
import com.example.toplutasima.viewmodel.PersonalTripViewModel
import com.example.toplutasima.viewmodel.personaltrip.PersonalTripUiState
import com.example.toplutasima.viewmodel.records.DayGroup
import com.example.toplutasima.viewmodel.records.RecordRowUiModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
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
                    "🚗  ${S.noRecords(lang)}",
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
                                androidx.compose.foundation.lazy.LazyRow(
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
            readyPlates = uiState.readyPlates,
            onDismiss = { viewModel.closeDialog() }
        )
    }
}

@Composable
private fun PersonalStatChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}
