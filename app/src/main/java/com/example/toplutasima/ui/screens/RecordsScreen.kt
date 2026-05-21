package com.example.toplutasima.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.toplutasima.ui.LocaleManager
import com.example.toplutasima.ui.screens.records.DayListScreen
import com.example.toplutasima.ui.screens.records.DeleteRecordConfirmDialog
import com.example.toplutasima.ui.screens.records.EditRecordDialog
import com.example.toplutasima.ui.screens.records.MonthListScreen
import com.example.toplutasima.ui.screens.records.PersonalRecordsContent
import com.example.toplutasima.viewmodel.PersonalTripViewModel
import com.example.toplutasima.viewmodel.RecordsViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun RecordsScreen(
    modifier: Modifier = Modifier,
    viewModel: RecordsViewModel = koinViewModel(),
    showPersonal: Boolean = false,
    onTogglePersonal: (Boolean) -> Unit = {},
    onRestoreRecord: ((Map<String, Any>) -> Unit)? = null,
    isActive: Boolean = true
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lang = LocaleManager.currentLanguage
    val context = LocalContext.current
    val personalViewModel: PersonalTripViewModel = koinViewModel()
    val personalState by personalViewModel.uiState.collectAsStateWithLifecycle()

    // Edit dialog state
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadProfileData()
    }

    // Physical back button: sadece bu sekme aktifken çalışsın (Crossfade'de eski sekme arka planda kalır)
    BackHandler(enabled = isActive && showPersonal) { onTogglePersonal(false) }
    BackHandler(enabled = isActive && !showPersonal && state.selectedMonth != null) {
        viewModel.clearSelectedMonth()
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (showPersonal) {
            // ── KİŞİSEL KAYITLAR MODU ──
            PersonalRecordsContent(
                uiState = personalState,
                lang = lang,
                viewModel = personalViewModel,
                onBack = { onTogglePersonal(false) }
            )
        } else if (state.selectedMonth == null) {
            // ── LEVEL 1: Month List ──
            MonthListScreen(
                summaries = state.monthSummaries,
                isLoading = state.isLoading,
                errorMsg = state.errorMsg,
                lang = lang,
                onMonthClick = { viewModel.selectMonth(it) },
                onTogglePersonal = { onTogglePersonal(true) },
                onRefresh = { viewModel.syncAndReload() },
                globalSearchLoading = state.globalSearchLoading,
                globalSearchError = state.globalSearchError,
                globalSearchResults = state.globalSearchResults,
                onRunGlobalSearch = { viewModel.runGlobalSearch(it) },
                onClearGlobalSearch = { viewModel.clearGlobalSearch() },
                onGlobalResultClick = { viewModel.setEditingRecord(it.originalRecord) },
                onOpenLatestTransitRecord = { viewModel.openLatestTransitRecord() }
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
                onExport = { format -> viewModel.exportMonth(format, context) },
                onRefresh = { viewModel.syncAndReload() }
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
            activeProfiles = state.activeProfiles,
            onDismiss = { viewModel.setEditingRecord(null) },
            onSave = { docId, fields, profileId, seatmateNote -> viewModel.updateRecord(docId, fields, profileId, seatmateNote) },
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
        DeleteRecordConfirmDialog(
            lang = lang,
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                showDeleteConfirm = false
                state.editingRecord?.get("firestoreDocId")?.toString()?.let(viewModel::deleteRecord)
            }
        )
    }
}
