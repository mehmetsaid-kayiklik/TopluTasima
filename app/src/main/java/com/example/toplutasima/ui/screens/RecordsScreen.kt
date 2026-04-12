package com.example.toplutasima.ui.screens

import android.app.DatePickerDialog
import android.util.Log
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koin.androidx.compose.koinViewModel
import com.example.toplutasima.network.FirestoreService
import com.example.toplutasima.ui.LocaleManager
import com.example.toplutasima.ui.S
import com.example.toplutasima.ui.WarningAmber
import com.example.toplutasima.usecase.RecordFilterState
import com.example.toplutasima.usecase.ExportFormat
import com.example.toplutasima.viewmodel.RecordsViewModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.example.toplutasima.model.SeatingStatus
import com.example.toplutasima.model.TicketStatus
import com.example.toplutasima.model.VehicleType
import com.example.toplutasima.model.PersonalTrip
import com.example.toplutasima.ui.components.PersonalTripCard
import com.example.toplutasima.viewmodel.PersonalTripViewModel
import com.example.toplutasima.viewmodel.PersonalTripUiState
import androidx.compose.foundation.lazy.items

// ── Vehicle type → emoji mapping ──
private fun typeEmoji(type: String): String = when (type) {
    VehicleType.BUS.key -> "🚌"
    VehicleType.SBAHN.key -> "🚆"
    VehicleType.UBAHN.key -> "🚇"
    VehicleType.RERB.key -> "🚂"
    VehicleType.FERNZUG.key -> "🚄"
    VehicleType.STRASSENBAHN.key -> "🚋"
    else -> "🚌"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RecordsScreen(
    modifier: Modifier = Modifier,
    viewModel: RecordsViewModel = koinViewModel(),
    onRestoreRecord: ((Map<String, Any>) -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsState()
    val lang = LocaleManager.currentLanguage
    val context = LocalContext.current
    val personalViewModel: PersonalTripViewModel = koinViewModel()
    val personalState by personalViewModel.uiState.collectAsState()
    var showPersonal by remember { mutableStateOf(false) }

    // Edit dialog state
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Physical back button: Kişisel moddan çık, sonra ay seçimini kapat
    BackHandler(enabled = showPersonal) { showPersonal = false }
    BackHandler(enabled = !showPersonal && state.selectedMonth != null) {
        viewModel.clearSelectedMonth()
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (showPersonal) {
            // ── KİŞİSEL KAYITLAR MODU ──
            PersonalRecordsContent(
                uiState = personalState,
                lang = lang,
                viewModel = personalViewModel,
                onBack = { showPersonal = false }
            )
        } else if (state.selectedMonth == null) {
            // ── LEVEL 1: Month List ──
            MonthListScreen(
                summaries = state.monthSummaries,
                isLoading = state.isLoading,
                errorMsg = state.errorMsg,
                lang = lang,
                onMonthClick = { viewModel.selectMonth(it) },
                onTogglePersonal = { showPersonal = true }
            )
        } else {
            // ── LEVEL 2: Day/Trip List ──
            DayListScreen(
                monthSummary = state.selectedMonth!!,
                dayGroups = state.filteredTrips,
                isLoading = state.isLoading,
                errorMsg = state.errorMsg,
                lang = lang,
                onBack = { viewModel.clearSelectedMonth() },
                onTripClick = { viewModel.setEditingRecord(it) },
                // Filter props
                filterState = state.filterState,
                isFilterPanelOpen = state.isFilterPanelOpen,
                filteredTotalCount = state.filteredTotalCount,
                unfilteredTotalCount = state.unfilteredTotalCount,
                onToggleFilterPanel = { viewModel.toggleFilterPanel() },
                onUpdateFilter = { viewModel.updateFilter(it) },
                onClearFilters = { viewModel.clearFilters() },
                // Incomplete records props
                incompleteRecords = state.incompleteRecords,
                isIncompleteExpanded = state.isIncompleteExpanded,
                onToggleIncomplete = { viewModel.toggleIncompleteExpanded() },
                onIncompleteClick = { viewModel.setEditingRecord(it) },
                // Export props
                showExportDialog = state.showExportDialog,
                isExporting = state.isExporting,
                onToggleExportDialog = { viewModel.toggleExportDialog() },
                onExport = { format -> viewModel.exportMonth(format, context) }
            )
        }

        // ── Status bar (only in transit mode) ──
        if (!showPersonal && state.saveMsg.isNotBlank()) {
            Text(
                state.saveMsg,
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }

    // ── Edit Dialog (Unchanged) ──
    state.editingRecord?.let { record ->
        EditRecordDialog(
            record = record,
            lang = lang,
            isSaving = state.isSaving,
            onDismiss = { viewModel.setEditingRecord(null) },
            onSave = { docId, fields -> viewModel.updateRecord(docId, fields) },
            onDelete = { showDeleteConfirm = true },
            onRestore = if (onRestoreRecord != null) {
                {
                    viewModel.setEditingRecord(null)
                    onRestoreRecord(record)
                }
            } else null
        )
    }

    // ── Delete Confirmation (Unchanged) ──
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(S.deleteRecord(lang)) },
            text = { Text(S.deleteConfirm(lang)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        val docId = state.editingRecord?.get("firestoreDocId")?.toString() ?: return@TextButton
                        viewModel.deleteRecord(docId)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(S.deleteRecord(lang)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(S.cancel(lang))
                }
            }
        )
    }
}

// ── LEVEL 1: Month List ──
@Composable
fun MonthListScreen(
    summaries: List<com.example.toplutasima.network.FirestoreService.MonthSummary>,
    isLoading: Boolean,
    errorMsg: String,
    lang: com.example.toplutasima.ui.AppLanguage,
    onMonthClick: (com.example.toplutasima.network.FirestoreService.MonthSummary) -> Unit,
    onTogglePersonal: () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Title + Kişisel toggle
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
                Text("🚗 ${S.modePersonal(lang)}", fontWeight = FontWeight.Bold)
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                isLoading -> {
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
                summaries.isEmpty() -> {
                    Text(
                        S.noRecords(lang),
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(summaries.size, key = { it -> summaries[it].sortKey }) { index ->
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

// ── LEVEL 2: Day List ──
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DayListScreen(
    monthSummary: com.example.toplutasima.network.FirestoreService.MonthSummary,
    dayGroups: List<com.example.toplutasima.viewmodel.DayGroup>,
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
    incompleteRecords: List<com.example.toplutasima.viewmodel.RecordRowUiModel>,
    isIncompleteExpanded: Boolean,
    onToggleIncomplete: () -> Unit,
    onIncompleteClick: (Map<String, Any>) -> Unit,
    // Export props
    showExportDialog: Boolean = false,
    isExporting: Boolean = false,
    onToggleExportDialog: () -> Unit = {},
    onExport: (ExportFormat) -> Unit = {}
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
                isLoading -> {
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

// ── Incomplete Records Banner ─────────────────────────────────────────────
@Composable
fun IncompleteRecordsBanner(
    incompleteRecords: List<com.example.toplutasima.viewmodel.RecordRowUiModel>,
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

// ── Filter Panel ──────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterPanel(
    filterState: RecordFilterState,
    onUpdateFilter: (RecordFilterState) -> Unit,
    onClearFilters: () -> Unit,
    lang: com.example.toplutasima.ui.AppLanguage
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    S.filterTitle(lang),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (filterState.hasActiveFilters) {
                    TextButton(
                        onClick = onClearFilters,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(S.filterClearAll(lang), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Search field
            OutlinedTextField(
                value = filterState.searchQuery,
                onValueChange = { onUpdateFilter(filterState.copy(searchQuery = it)) },
                placeholder = { Text(S.filterSearchHint(lang), style = MaterialTheme.typography.bodySmall) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                textStyle = MaterialTheme.typography.bodySmall,
                trailingIcon = {
                    if (filterState.searchQuery.isNotBlank()) {
                        IconButton(onClick = { onUpdateFilter(filterState.copy(searchQuery = "")) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            )

            // Vehicle Type dropdown
            var vehicleTypeExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = vehicleTypeExpanded,
                onExpandedChange = { vehicleTypeExpanded = it }
            ) {
                OutlinedTextField(
                    value = if (filterState.vehicleType.isBlank()) S.filterAll(lang) else filterState.vehicleType,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(S.filterVehicleType(lang), style = MaterialTheme.typography.labelSmall) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = vehicleTypeExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(10.dp),
                    textStyle = MaterialTheme.typography.bodySmall
                )
                ExposedDropdownMenu(
                    expanded = vehicleTypeExpanded,
                    onDismissRequest = { vehicleTypeExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(S.filterAll(lang)) },
                        onClick = {
                            onUpdateFilter(filterState.copy(vehicleType = ""))
                            vehicleTypeExpanded = false
                        }
                    )
                    VehicleType.allKeys.forEach { key ->
                        DropdownMenuItem(
                            text = { Text("${typeEmoji(key)} ${S.vehicleTypeName(key, lang)}") },
                            onClick = {
                                onUpdateFilter(filterState.copy(vehicleType = key))
                                vehicleTypeExpanded = false
                            }
                        )
                    }
                }
            }

            // Weather dropdown
            var weatherExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = weatherExpanded,
                onExpandedChange = { weatherExpanded = it }
            ) {
                val weatherDisplay = if (filterState.weather.isBlank()) S.filterAll(lang)
                    else {
                        val emoji = S.weatherOptions.find { it.first == filterState.weather }?.second ?: ""
                        "$emoji ${S.weatherName(filterState.weather, lang)}"
                    }
                OutlinedTextField(
                    value = weatherDisplay,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(S.filterWeather(lang), style = MaterialTheme.typography.labelSmall) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = weatherExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(10.dp),
                    textStyle = MaterialTheme.typography.bodySmall
                )
                ExposedDropdownMenu(
                    expanded = weatherExpanded,
                    onDismissRequest = { weatherExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(S.filterAll(lang)) },
                        onClick = {
                            onUpdateFilter(filterState.copy(weather = ""))
                            weatherExpanded = false
                        }
                    )
                    S.weatherOptions.forEach { (key, emoji) ->
                        DropdownMenuItem(
                            text = { Text("$emoji ${S.weatherName(key, lang)}") },
                            onClick = {
                                onUpdateFilter(filterState.copy(weather = key))
                                weatherExpanded = false
                            }
                        )
                    }
                }
            }

            // Stop name search
            OutlinedTextField(
                value = filterState.stopName,
                onValueChange = { onUpdateFilter(filterState.copy(stopName = it)) },
                placeholder = { Text(S.filterStopNameHint(lang), style = MaterialTheme.typography.bodySmall) },
                label = { Text(S.filterStopName(lang), style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                textStyle = MaterialTheme.typography.bodySmall,
                trailingIcon = {
                    if (filterState.stopName.isNotBlank()) {
                        IconButton(onClick = { onUpdateFilter(filterState.copy(stopName = "")) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            )

            // Delay range
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = filterState.minDelay?.toString() ?: "",
                    onValueChange = { onUpdateFilter(filterState.copy(minDelay = it.toIntOrNull())) },
                    label = { Text(S.filterDelayMin(lang), style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.weight(1f).height(56.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = filterState.maxDelay?.toString() ?: "",
                    onValueChange = { onUpdateFilter(filterState.copy(maxDelay = it.toIntOrNull())) },
                    label = { Text(S.filterDelayMax(lang), style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.weight(1f).height(56.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.bodySmall
                )
            }

            // Seated + Ticket toggles
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Seated filter chip
                val seatedOptions = listOf<Pair<String, Boolean?>>(
                    S.filterAll(lang) to null,
                    S.filterSeatedYes(lang) to true,
                    S.filterSeatedNo(lang) to false
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(S.filterSeated(lang), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        seatedOptions.forEach { (label, value) ->
                            FilterChip(
                                selected = filterState.seatedFilter == value,
                                onClick = { onUpdateFilter(filterState.copy(seatedFilter = value)) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val ticketOptions = listOf<Pair<String, Boolean?>>(
                    S.filterAll(lang) to null,
                    S.filterTicketYes(lang) to true,
                    S.filterTicketNo(lang) to false
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(S.filterTicket(lang), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ticketOptions.forEach { (label, value) ->
                            FilterChip(
                                selected = filterState.ticketFilter == value,
                                onClick = { onUpdateFilter(filterState.copy(ticketFilter = value)) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Export Format Button ──────────────────────────────────────────────────
@Composable
fun ExportFormatButton(emoji: String, label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(emoji, fontSize = 20.sp)
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
    }
}

// ── Active Filter Bar with chips ──────────────────────────────────────────
@Composable
fun ActiveFilterBar(
    filterState: RecordFilterState,
    filteredCount: Int,
    onUpdateFilter: (RecordFilterState) -> Unit,
    onClearAll: () -> Unit,
    lang: com.example.toplutasima.ui.AppLanguage
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        // Result count
        Text(
            S.filterResultCount(filteredCount, lang),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        // Active filter chips
        @Composable
        fun ChipItem(label: String, onRemove: () -> Unit) {
            InputChip(
                selected = true,
                onClick = onRemove,
                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                trailingIcon = {
                    Icon(Icons.Filled.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp))
                },
                modifier = Modifier.height(28.dp),
                shape = RoundedCornerShape(8.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (filterState.searchQuery.isNotBlank()) {
                ChipItem("\"${filterState.searchQuery}\"") {
                    onUpdateFilter(filterState.copy(searchQuery = ""))
                }
            }
            if (filterState.vehicleType.isNotBlank()) {
                ChipItem(S.vehicleTypeName(filterState.vehicleType, lang)) {
                    onUpdateFilter(filterState.copy(vehicleType = ""))
                }
            }
            if (filterState.weather.isNotBlank()) {
                ChipItem(S.weatherName(filterState.weather, lang)) {
                    onUpdateFilter(filterState.copy(weather = ""))
                }
            }
            if (filterState.seatedFilter != null) {
                val lbl = if (filterState.seatedFilter == true) S.filterSeatedYes(lang) else S.filterSeatedNo(lang)
                ChipItem(lbl) { onUpdateFilter(filterState.copy(seatedFilter = null)) }
            }
            if (filterState.ticketFilter != null) {
                val lbl = if (filterState.ticketFilter == true) S.filterTicketYes(lang) else S.filterTicketNo(lang)
                ChipItem(lbl) { onUpdateFilter(filterState.copy(ticketFilter = null)) }
            }
            if (filterState.minDelay != null || filterState.maxDelay != null) {
                val min = filterState.minDelay?.toString() ?: "0"
                val max = filterState.maxDelay?.toString() ?: "∞"
                ChipItem("$min-$max dk") {
                    onUpdateFilter(filterState.copy(minDelay = null, maxDelay = null))
                }
            }
            if (filterState.stopName.isNotBlank()) {
                ChipItem("📍 ${filterState.stopName}") {
                    onUpdateFilter(filterState.copy(stopName = ""))
                }
            }
            // Clear all
            TextButton(
                onClick = onClearAll,
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Text("✕", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun TripCard(trip: com.example.toplutasima.viewmodel.RecordRowUiModel, onClick: () -> Unit) {
    val lang = LocaleManager.currentLanguage
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    "${trip.typeDisplay} ${trip.line} (${trip.direction})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Show delay if present
                val delayNum = trip.delay.toIntOrNull() ?: 0
                if (delayNum > 0) {
                    Text(
                        "+${delayNum}dk",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.width(110.dp)) {
                    Text(
                        "${trip.plannedDep} → ${trip.plannedArr}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (trip.actualDep.isNotBlank() || trip.actualArr.isNotBlank()) {
                        val delayNum = trip.delay.toIntOrNull() ?: 0
                        val actualColor = if (delayNum > 0) MaterialTheme.colorScheme.error
                                          else androidx.compose.ui.graphics.Color(0xFF4CAF50)
                        Text(
                            "${trip.actualDep} → ${trip.actualArr}",
                            style = MaterialTheme.typography.bodySmall,
                            color = actualColor
                        )
                    }
                }
                Text(
                    "${trip.boardingStop} → ${trip.alightingStop}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            if (trip.stopCount.isNotBlank() || trip.distance.isNotBlank() || trip.plannedDuration.isNotBlank() || trip.actualDuration.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                val extraInfo = mutableListOf<String>()
                if (trip.stopCount.isNotBlank()) extraInfo.add("${trip.stopCount} durak")
                if (trip.distance.isNotBlank()) extraInfo.add(trip.distance)
                
                if (trip.plannedDuration.isNotBlank() && trip.plannedDuration != "0") {
                    extraInfo.add("${S.plannedDurationLabel(lang)}: ${trip.plannedDuration} dk")
                }
                if (trip.actualDuration.isNotBlank() && trip.actualDuration != "0") {
                    extraInfo.add("${S.actualDurationLabel(lang)}: ${trip.actualDuration} dk")
                }

                Text(
                    extraInfo.joinToString(" • "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Helper: strip seconds from time string "HH:MM:SS" → "HH:MM" ──
private fun stripSeconds(time: String): String {
    val trimmed = time.trim()
    if (trimmed.isBlank()) return trimmed
    val parts = trimmed.split(":")
    return if (parts.size >= 3) "${parts[0]}:${parts[1]}" else trimmed
}

// ── Edit Dialog ──
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditRecordDialog(
    record: Map<String, Any>,
    lang: com.example.toplutasima.ui.AppLanguage,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Map<String, Any?>) -> Unit,
    onDelete: () -> Unit,
    onRestore: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val docId = record["firestoreDocId"]?.toString() ?: ""
    Log.d("EditDialog", "docId='$docId' record keys=${record.keys}")

    // BUG 3: Editable date with derived gun / gununTipi
    var tarih by remember(record) { mutableStateOf(record["tarih"]?.toString() ?: "") }
    var gun by remember(record) { mutableStateOf(record["gun"]?.toString() ?: "") }
    var gununTipi by remember(record) { mutableStateOf(record["gununTipi"]?.toString() ?: "") }

    var binisDuragi by remember(record) { mutableStateOf(record["binisDuragi"]?.toString() ?: "") }
    var inisDuragi by remember(record) { mutableStateOf(record["inisDuragi"]?.toString() ?: "") }
    // BUG 4: Strip seconds from time fields
    var planlananBinis by remember(record) { mutableStateOf(stripSeconds(record["planlananBinis"]?.toString() ?: "")) }
    var gercekBinis by remember(record) { mutableStateOf(stripSeconds(record["gercekBinis"]?.toString() ?: "")) }
    var planlananInis by remember(record) { mutableStateOf(stripSeconds(record["planlananInis"]?.toString() ?: "")) }
    var gercekInis by remember(record) { mutableStateOf(stripSeconds(record["gercekInis"]?.toString() ?: "")) }
    var havaDurumu by remember(record) { mutableStateOf(record["havaDurumu"]?.toString() ?: "") }
    var mesafe by remember(record) { mutableStateOf(record["mesafe"]?.toString() ?: "") }
    var durakSayisi by remember(record) { mutableStateOf(record["durakSayisi"]?.toString() ?: "") }
    var not by remember(record) { mutableStateOf(record["not"]?.toString() ?: "") }
    var oturabildim by remember(record) {
        mutableStateOf(record["oturabildimMi"]?.toString() == SeatingStatus.YES.key)
    }
    var biletKontrolu by remember(record) {
        mutableStateOf(record["biletKontrolü"]?.toString() == TicketStatus.HAPPENED.key)
    }

    // Helper to open DatePicker
    fun openDatePicker() {
        val parts = tarih.split(".")
        val day = parts.getOrNull(0)?.toIntOrNull() ?: 1
        val month = parts.getOrNull(1)?.toIntOrNull() ?: 1
        val year = parts.getOrNull(2)?.toIntOrNull() ?: 2024
        DatePickerDialog(context, { _, y, m, d ->
            val newDate = String.format("%02d.%02d.%04d", d, m + 1, y)
            tarih = newDate
            gun = FirestoreService.computeGun(newDate)
            gununTipi = FirestoreService.computeGununTipi(newDate)
        }, year, month - 1, day).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(modifier = Modifier.clickable { openDatePicker() }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${S.editRecord(lang)}: $tarih",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("📅", style = MaterialTheme.typography.titleMedium)
                }
                if (gun.isNotBlank()) {
                    Text(
                        "$gun • $gununTipi",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Info row (read only)
                Text(
                    "${record["tur"] ?: ""} • ${record["hat"] ?: ""} • ${record["yon"] ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider()

                OutlinedTextField(
                    value = binisDuragi,
                    onValueChange = { binisDuragi = it },
                    label = { Text(S.boardingStop(lang)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = inisDuragi,
                    onValueChange = { inisDuragi = it },
                    label = { Text(S.alightingStop(lang)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = planlananBinis,
                        onValueChange = { planlananBinis = it },
                        label = { Text("Plan. ${S.departure(lang)}") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = gercekBinis,
                        onValueChange = { gercekBinis = it },
                        label = { Text("Gerçek ${S.departure(lang)}") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = planlananInis,
                        onValueChange = { planlananInis = it },
                        label = { Text("Plan. ${S.arrival(lang)}") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = gercekInis,
                        onValueChange = { gercekInis = it },
                        label = { Text("Gerçek ${S.arrival(lang)}") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                // Weather Dropdown
                var weatherExpanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = weatherExpanded,
                    onExpandedChange = { weatherExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val currentDisplay = if (havaDurumu.isNotBlank()) {
                        val emoji = S.weatherOptions.find { it.first == havaDurumu }?.second ?: "❓"
                        "$emoji ${S.weatherName(havaDurumu, lang)}"
                    } else ""

                    OutlinedTextField(
                        value = currentDisplay,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(S.weatherLabel(lang)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = weatherExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = weatherExpanded,
                        onDismissRequest = { weatherExpanded = false }
                    ) {
                        S.weatherOptions.forEach { (optionKey, optionEmoji) ->
                            DropdownMenuItem(
                                text = { Text("$optionEmoji ${S.weatherName(optionKey, lang)}") },
                                onClick = {
                                    havaDurumu = optionKey
                                    weatherExpanded = false
                                }
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = mesafe,
                        onValueChange = { mesafe = it },
                        label = { Text("Mesafe") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = durakSayisi,
                        onValueChange = { durakSayisi = it },
                        label = { Text("Durak Sayısı") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                OutlinedTextField(
                    value = not,
                    onValueChange = { not = it },
                    label = { Text(S.noteOptional(lang)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(S.seatedToggle(lang), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = oturabildim, onCheckedChange = { oturabildim = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(S.ticketControl(lang), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = biletKontrolu, onCheckedChange = { biletKontrolu = it })
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                // Restore button (icon only to save space)
                if (onRestore != null) {
                    IconButton(onClick = onRestore) { Text("🔄", fontSize = 18.sp) }
                }

                // Delete button
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    enabled = !isSaving
                ) { Text(S.deleteRecord(lang)) }

                // Save button
                Button(
                    onClick = {
                        val fields = mapOf<String, Any?>(
                            "tarih" to tarih,
                            "gun" to gun,
                            "gununTipi" to gununTipi,
                            "binisDuragi" to binisDuragi,
                            "inisDuragi" to inisDuragi,
                            "planlananBinis" to planlananBinis,
                            "gercekBinis" to gercekBinis,
                            "planlananInis" to planlananInis,
                            "gercekInis" to gercekInis,
                            "havaDurumu" to havaDurumu,
                            "mesafe" to mesafe,
                            "durakSayisi" to durakSayisi,
                            "not" to not,
                            "oturabildimMi" to SeatingStatus.fromBoolean(oturabildim).key,
                            "biletKontrolü" to TicketStatus.fromBoolean(biletKontrolu).key
                        )
                        onSave(docId, fields)
                    },
                    enabled = !isSaving && docId.isNotBlank(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(S.save(lang))
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(S.cancel(lang)) }
        }
    )
}

// ── Kişisel Kayıtlar İçeriği (RecordsScreen içinde gösterilir) ───────────────
@Composable
fun PersonalRecordsContent(
    uiState: PersonalTripUiState,
    lang: com.example.toplutasima.ui.AppLanguage,
    viewModel: PersonalTripViewModel,
    onBack: () -> Unit
) {
    val trips = uiState.trips
    val months = remember(trips) {
        trips.map { it.yearMonth }.filter { it.isNotBlank() }.distinct().sortedDescending()
    }
    val doneTrips = remember(trips) { trips.filter { it.durum == PersonalTrip.DURUM_TAMAMLANDI } }
    val totalDist = remember(doneTrips) {
        doneTrips.sumOf { t -> t.mesafe.replace(" km","").replace(",",".").toDoubleOrNull() ?: 0.0 }
    }
    val topVehicle = remember(doneTrips) {
        doneTrips.groupingBy { it.aracTuru }.eachCount().maxByOrNull { it.value }?.key ?: "—"
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

                        // Özet şerit
                        if (doneTrips.isNotEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        PersonalStatChip(label = S.personalSummaryTotal(lang), value = "${trips.size}")
                                        PersonalStatChip(label = S.personalSummaryTopType(lang), value = topVehicle)
                                        PersonalStatChip(
                                            label = S.personalSummaryTotalDist(lang),
                                            value = if (totalDist > 0) String.format("%.0f km", totalDist) else "—"
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
}

@Composable
private fun PersonalStatChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}
