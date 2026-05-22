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
import com.example.toplutasima.model.MonthSummary
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
// ── LEVEL 2: Day List ──
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun DayListScreen(
    monthSummary: MonthSummary,
    dayGroups: List<DayGroup>,
    isLoading: Boolean,
    errorMsg: String,
    lang: com.example.toplutasima.ui.AppLanguage,
    onBack: () -> Unit,
    onTripClick: (Map<String, Any>) -> Unit,
    // Filter props
    filterState: RecordFilterState,
    isFilterPanelOpen: Boolean,
    filteredTotalCount: Int,
    unfilteredTotalCount: Int,
    onToggleFilterPanel: () -> Unit,
    onUpdateFilter: (RecordFilterState) -> Unit,
    onClearFilters: () -> Unit,
    // Incomplete records props
    incompleteRecords: List<RecordRowUiModel>,
    isIncompleteExpanded: Boolean,
    onToggleIncomplete: () -> Unit,
    onIncompleteClick: (Map<String, Any>) -> Unit,
    // Export props
    showExportDialog: Boolean = false,
    isExporting: Boolean = false,
    onToggleExportDialog: () -> Unit = {},
    onExport: (ExportFormat) -> Unit = {},
    onRefresh: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // App Bar equivalent
        Row(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Back", modifier = Modifier.rotate(-90f))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "${S.monthName(monthSummary.monthName, lang)} ${monthSummary.year}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            // Export button
            IconButton(
                onClick = onToggleExportDialog,
                enabled = !isExporting && dayGroups.isNotEmpty()
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("📤", fontSize = 20.sp)
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            // Filter toggle button
            FilledTonalButton(
                onClick = onToggleFilterPanel,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    if (isFilterPanelOpen) S.filterHide(lang) else S.filterShow(lang),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (filterState.activeFilterCount > 0) {
                    Spacer(Modifier.width(6.dp))
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Text("${filterState.activeFilterCount}")
                    }
                }
            }
        }
        Divider()

        // ── Export Format Dialog ──
        if (showExportDialog) {
            AlertDialog(
                onDismissRequest = onToggleExportDialog,
                title = { Text(S.exportChooseFormat(lang), fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExportFormatButton("📊", S.exportCsv(lang)) { onExport(ExportFormat.CSV) }
                        ExportFormatButton("{ }", S.exportJson(lang)) { onExport(ExportFormat.JSON) }
                        ExportFormatButton("📄", S.exportPdf(lang)) { onExport(ExportFormat.PDF) }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = onToggleExportDialog) { Text(S.cancel(lang)) }
                }
            )
        }


        // ── Incomplete Records Banner ──
        if (incompleteRecords.isNotEmpty()) {
            IncompleteRecordsBanner(
                incompleteRecords = incompleteRecords,
                isExpanded = isIncompleteExpanded,
                onToggle = onToggleIncomplete,
                onRecordClick = onIncompleteClick,
                lang = lang
            )
        }

        // ── Filter Panel ──
        AnimatedVisibility(
            visible = isFilterPanelOpen,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            FilterPanel(
                filterState = filterState,
                onUpdateFilter = onUpdateFilter,
                onClearFilters = onClearFilters,
                lang = lang
            )
        }

        // ── Active filter chips + result count ──
        if (filterState.hasActiveFilters) {
            ActiveFilterBar(
                filterState = filterState,
                filteredCount = filteredTotalCount,
                onUpdateFilter = onUpdateFilter,
                onClearAll = onClearFilters,
                lang = lang
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                isLoading && dayGroups.isEmpty() -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                }
                errorMsg.isNotBlank() -> {
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
                dayGroups.isEmpty() -> {
                    // Empty state — different message if filters are active
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            if (filterState.hasActiveFilters) "🔍" else "📋",
                            fontSize = 48.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (filterState.hasActiveFilters) S.filterNoResults(lang) else S.noRecords(lang),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        if (filterState.hasActiveFilters) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                S.filterNoResultsHint(lang),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(16.dp))
                            OutlinedButton(
                                onClick = onClearFilters,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(S.filterClearAll(lang))
                            }
                        }
                    }
                }
                else -> {
                    PullToRefreshBox(
                        isRefreshing = isLoading,
                        onRefresh = onRefresh,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            dayGroups.forEach { group ->
                                stickyHeader(key = group.date) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f))
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        "${group.dayName}, ${group.date}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                            items(group.trips.size, key = { group.trips[it].id }) { i ->
                                val trip = group.trips[i]
                                TripCard(trip = trip, onClick = { onTripClick(trip.originalRecord) })
                            }
                        }
                    }
                }
            }
        }
    }
}
}

// ── Incomplete Records Banner ─────────────────────────────────────────────
@Composable
internal fun IncompleteRecordsBanner(
    incompleteRecords: List<RecordRowUiModel>,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onRecordClick: (Map<String, Any>) -> Unit,
    lang: com.example.toplutasima.ui.AppLanguage
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = WarningAmber.copy(alpha = 0.12f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onToggle() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Badge with count
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(WarningAmber),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${incompleteRecords.size}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.surface
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        S.incompleteTitle(lang),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        S.incompleteDesc(lang),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (isExpanded) S.incompleteHide(lang) else S.incompleteShowAll(lang),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Expanded: show list of incomplete records
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val maxShow = minOf(incompleteRecords.size, 10) // Don't overflow
                    incompleteRecords.take(maxShow).forEach { record ->
                        val missingLabel = when {
                            record.actualDep.isBlank() && record.actualArr.isBlank() -> S.incompleteMissingBoth(lang)
                            record.actualDep.isBlank() -> S.incompleteMissingDep(lang)
                            else -> S.incompleteMissingArr(lang)
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onRecordClick(record.originalRecord) },
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${record.typeDisplay} ${record.line} • ${record.date}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "${record.boardingStop} → ${record.alightingStop}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    missingLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = WarningAmber,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    if (incompleteRecords.size > maxShow) {
                        Text(
                            "+${incompleteRecords.size - maxShow} ...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp, top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
