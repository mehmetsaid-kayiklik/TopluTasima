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
// ── LEVEL 1: Month List ──
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MonthListScreen(
    summaries: List<com.example.toplutasima.network.FirestoreService.MonthSummary>,
    isLoading: Boolean,
    errorMsg: String,
    lang: com.example.toplutasima.ui.AppLanguage,
    onMonthClick: (com.example.toplutasima.network.FirestoreService.MonthSummary) -> Unit,
    onTogglePersonal: () -> Unit = {},
    onRefresh: () -> Unit,
    globalSearchLoading: Boolean = false,
    globalSearchError: String = "",
    globalSearchResults: List<RecordRowUiModel> = emptyList(),
    onRunGlobalSearch: (String) -> Unit = {},
    onClearGlobalSearch: () -> Unit = {},
    onGlobalResultClick: (RecordRowUiModel) -> Unit = {},
    onOpenLatestTransitRecord: () -> Unit = {}
) {
    var searchField by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                S.recordsTitle(lang),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            TextButton(
                onClick = onTogglePersonal,
                colors = ButtonDefaults.textButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("🚗", fontWeight = FontWeight.Bold)
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchField,
                    onValueChange = { searchField = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(S.recordsGlobalSearchHint(lang), style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onRunGlobalSearch(searchField) }),
                    trailingIcon = {
                        if (searchField.isNotBlank()) {
                            IconButton(onClick = {
                                searchField = ""
                                onClearGlobalSearch()
                            }) {
                                Icon(Icons.Filled.Close, contentDescription = S.clear(lang))
                            }
                        }
                    }
                )
                FilledTonalButton(
                    onClick = { onRunGlobalSearch(searchField) },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Filled.Search, contentDescription = S.recordsSearchRun(lang))
                }
            }
            TextButton(
                onClick = onOpenLatestTransitRecord,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(S.recordsLastRecordQuick(lang), fontWeight = FontWeight.SemiBold)
            }
            if (globalSearchLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (globalSearchError.isNotBlank()) {
                Text(
                    globalSearchError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (globalSearchResults.isNotEmpty()) {
                Text(
                    S.recordsGlobalSearchResults(lang),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(globalSearchResults.size, key = { globalSearchResults[it].id }) { idx ->
                            val row = globalSearchResults[idx]
                            Card(
                                onClick = { onGlobalResultClick(row) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Text(
                                        "${row.line} • ${row.date}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "${row.boardingStop} → ${row.alightingStop}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = onRefresh,
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            when {
                isLoading && summaries.isEmpty() -> {
                    Box(Modifier.fillMaxSize()) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp
                        )
                    }
                }
                errorMsg.isNotBlank() -> {
                    Box(Modifier.fillMaxSize()) {
                        Card(
                            modifier = Modifier.align(Alignment.Center).padding(32.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                            "⚠️ ${errorMsg}",
                                modifier = Modifier.padding(20.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                summaries.isEmpty() -> {
                    Box(Modifier.fillMaxSize()) {
                        Text(
                            S.noRecords(lang),
                            modifier = Modifier.align(Alignment.Center),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(summaries.size, key = { idx -> summaries[idx].sortKey }) { index ->
                            val s = summaries[index]
                            Card(
                                onClick = { onMonthClick(s) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "${S.monthName(s.monthName, lang)} ${s.year}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "${s.count} ${S.tripsCount(lang)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
