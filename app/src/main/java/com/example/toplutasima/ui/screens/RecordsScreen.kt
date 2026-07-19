package com.example.toplutasima.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.toplutasima.ui.LocaleManager
import com.example.toplutasima.ui.S
import com.example.toplutasima.ui.screens.records.DayListScreen
import com.example.toplutasima.ui.screens.records.DeleteRecordConfirmDialog
import com.example.toplutasima.ui.screens.records.EditRecordDialog
import com.example.toplutasima.ui.screens.records.MonthListScreen
import com.example.toplutasima.ui.screens.records.PersonalRecordsContent
import com.example.toplutasima.ui.components.transit.TransitDeleteReceiptHost
import com.example.toplutasima.ui.components.transit.TransitHealthIssuesSheet
import com.example.toplutasima.ui.components.transit.TransitExportDialog
import com.example.toplutasima.ui.components.transit.TransitDuplicateRecordUiModel
import com.example.toplutasima.ui.components.transit.TransitDuplicateResolutionSheet
import com.example.toplutasima.ui.components.transit.TransitChangeHistorySheet
import com.example.toplutasima.ui.components.transit.TransitHistoryUndoUiState
import com.example.toplutasima.domain.transit.health.TransitHealthSeverity
import com.example.toplutasima.transit.TransitFeatureFlags
import com.example.toplutasima.transit.duplicate.TransitDuplicateMergeSelection
import com.example.toplutasima.data.repository.toEntity
import com.example.toplutasima.ui.util.withoutEmojiCharacters
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
    var showCorrectionConfirm by remember { mutableStateOf(false) }
    var correctionRecordId by remember { mutableStateOf<String?>(null) }
    var pendingHistoryUndoEventId by remember { mutableStateOf<String?>(null) }

    val csvExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> viewModel.completeTransitExport(uri) }
    val jsonExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> viewModel.completeTransitExport(uri) }
    val exportDocumentRequest = state.transitExportDocumentRequest
    LaunchedEffect(exportDocumentRequest, showPersonal) {
        val request = exportDocumentRequest ?: return@LaunchedEffect
        if (showPersonal || !TransitFeatureFlags.TRANSIT_EXPORT) return@LaunchedEffect
        if (request.mimeType == "text/csv") {
            csvExportLauncher.launch(request.suggestedFileName)
        } else {
            jsonExportLauncher.launch(request.suggestedFileName)
        }
        viewModel.onTransitExportPickerLaunched()
    }

    val visibleRowsById = state.selectedMonthTrips.asSequence()
        .flatMap { it.trips.asSequence() }
        .associateBy { it.localRecordId }
    val visibleDuplicateCandidates = state.duplicateCandidates.filter {
        it.firstRecordId in visibleRowsById && it.secondRecordId in visibleRowsById
    }

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
            Box(modifier = Modifier.weight(1f)) {
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
                TransitDeleteReceiptHost(
                    onRetry = viewModel::retryDelete,
                    onKeepLocalOnly = viewModel::keepDeleteLocalOnly,
                    onOpenHistory = viewModel::openChangeHistory,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)
                )
            }
        } else {
            Box(modifier = Modifier.weight(1f)) {
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
                onRefresh = { viewModel.syncAndReload() },
                healthIssueCount = state.fullHealthIssueCount,
                healthCriticalCount = state.healthIssuesByRecordId.values.flatten()
                    .count { it.severity == TransitHealthSeverity.CRITICAL },
                healthCorrectionCount = state.healthCorrections.size,
                isHealthScanning = state.isHealthScanning,
                healthScanMessage = state.fullHealthScanMessage,
                onScanHealth = viewModel::scanAllTransitHistory,
                onApplyHealthCorrections = {
                    correctionRecordId = null
                    showCorrectionConfirm = true
                },
                onHealthClick = { viewModel.showHealthIssues(it.localRecordId) },
                duplicateCandidateCount = visibleDuplicateCandidates.size,
                onReviewDuplicates = {
                    visibleDuplicateCandidates.firstOrNull()?.let(viewModel::openDuplicateResolution)
                }
            )
                TransitDeleteReceiptHost(
                    onRetry = viewModel::retryDelete,
                    onKeepLocalOnly = viewModel::keepDeleteLocalOnly,
                    onOpenHistory = viewModel::openChangeHistory,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)
                )
            }
        }

        // ── Status bar (only in transit mode) ──
        if (!showPersonal && state.saveMsg.isNotBlank()) {
            val cleanSaveMsg = state.saveMsg.withoutEmojiCharacters().ifBlank { S.editDone(lang) }
            val isError = cleanSaveMsg.contains("hata", ignoreCase = true) ||
                cleanSaveMsg.contains("error", ignoreCase = true) ||
                cleanSaveMsg.contains("başarısız", ignoreCase = true) ||
                cleanSaveMsg.contains("bulunamadı", ignoreCase = true)
            Text(
                cleanSaveMsg,
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }
        if (!showPersonal && state.exportResult.isNotBlank()) {
            Text(
                state.exportResult,
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = if (
                    state.exportResult.contains("başarı", ignoreCase = true) ||
                    state.exportResult.contains("güvenle", ignoreCase = true)
                ) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
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
            } else null,
            historyEvents = state.selectedHistoryEvents.filter {
                it.recordId == record["id"]?.toString().orEmpty()
            },
            onOpenHistory = viewModel::openChangeHistory
        )
    }

    // ── Delete Confirmation (Unchanged) ──
    if (showDeleteConfirm) {
        DeleteRecordConfirmDialog(
            lang = lang,
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                showDeleteConfirm = false
                state.editingRecord?.let { record ->
                    val id = record["firestoreDocId"]?.toString()?.takeIf { it.isNotBlank() }
                        ?: record["id"]?.toString().orEmpty()
                    viewModel.deleteRecord(id)
                }
            }
        )
    }

    if (!showPersonal && TransitFeatureFlags.POST_SAVE_DATA_HEALTH) {
        val selectedId = state.selectedHealthRecordId
        val issues = selectedId?.let(state.healthIssuesByRecordId::get).orEmpty()
        if (selectedId != null && issues.isNotEmpty()) {
            TransitHealthIssuesSheet(
                issues = issues,
                corrections = state.healthCorrections.filter { it.localRecordId == selectedId },
                onDismiss = { viewModel.showHealthIssues(null) },
                onOpenRecord = viewModel::openSelectedHealthRecord,
                onApplyCorrections = {
                    correctionRecordId = selectedId
                    showCorrectionConfirm = true
                }
            )
        }
    }

    if (
        !showPersonal &&
        TransitFeatureFlags.TRANSIT_EXPORT &&
        state.showExportDialog
    ) {
        TransitExportDialog(
            selectedMonthAvailable = state.selectedMonth != null,
            insightsAvailable = TransitFeatureFlags.TRANSIT_INSIGHTS,
            healthAvailable = TransitFeatureFlags.POST_SAVE_DATA_HEALTH,
            onDismiss = viewModel::toggleExportDialog,
            onExport = viewModel::prepareTransitExport
        )
    }

    if (
        !showPersonal &&
        TransitFeatureFlags.POST_SAVE_DATA_HEALTH &&
        TransitFeatureFlags.TRANSIT_DUPLICATE_RESOLUTION
    ) {
        state.selectedDuplicateCandidate?.let { candidate ->
            val firstRow = visibleRowsById[candidate.firstRecordId]
            val secondRow = visibleRowsById[candidate.secondRecordId]
            if (firstRow != null && secondRow != null) {
                val firstMap = firstRow.originalRecord
                val secondMap = secondRow.originalRecord
                TransitDuplicateResolutionSheet(
                    candidate = candidate,
                    first = TransitDuplicateRecordUiModel(
                        record = firstMap.toEntity(firstMap["userId"]?.toString().orEmpty()),
                        provenanceByField = firstRow.provenanceByField,
                        healthIssues = firstRow.healthIssues
                    ),
                    second = TransitDuplicateRecordUiModel(
                        record = secondMap.toEntity(secondMap["userId"]?.toString().orEmpty()),
                        provenanceByField = secondRow.provenanceByField,
                        healthIssues = secondRow.healthIssues
                    ),
                    selection = TransitDuplicateMergeSelection(state.duplicateFieldSelections),
                    mergePreview = state.duplicateMergePreview,
                    showProvenance = TransitFeatureFlags.PROVENANCE_BADGES,
                    isWorking = state.isResolvingDuplicate,
                    onSelectField = viewModel::selectDuplicateField,
                    onKeepSeparate = viewModel::keepDuplicateSeparate,
                    onKeepFirst = viewModel::keepFirstDuplicate,
                    onKeepSecond = viewModel::keepSecondDuplicate,
                    onMerge = viewModel::mergeDuplicateRecords,
                    onReviewLater = viewModel::reviewDuplicateLater,
                    onDismiss = viewModel::dismissDuplicateResolution,
                    message = state.duplicateResolutionMessage.takeIf { it.isNotBlank() }
                )
            }
        }
    }

    if (!showPersonal && TransitFeatureFlags.TRANSIT_CHANGE_HISTORY) {
        state.selectedHistoryRecordId?.let { recordId ->
            TransitChangeHistorySheet(
                recordId = recordId,
                events = state.selectedHistoryEvents,
                onDismiss = viewModel::dismissChangeHistory,
                onUndo = { event ->
                    val undo = state.historyUndoByEventId[event.eventId]
                    if (undo?.requiresWarningConfirmation == true) {
                        pendingHistoryUndoEventId = event.eventId
                    } else {
                        viewModel.undoHistoryEvent(event.eventId)
                    }
                },
                undoStateFor = { event ->
                    state.historyUndoByEventId[event.eventId]?.let {
                        TransitHistoryUndoUiState(
                            enabled = it.enabled,
                            disabledReason = it.disabledReason
                        )
                    } ?: TransitHistoryUndoUiState.Disabled
                }
            )
        }
    }

    if (!showPersonal && pendingHistoryUndoEventId != null) {
        AlertDialog(
            onDismissRequest = { pendingHistoryUndoEventId = null },
            title = { Text("Değişikliği geri al?") },
            text = { Text("Geri alma doğrulama uyarısı içeriyor. Eski değer yalnız bu onayla uygulanacak.") },
            dismissButton = {
                TextButton(onClick = { pendingHistoryUndoEventId = null }) { Text("Vazgeç") }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val eventId = pendingHistoryUndoEventId
                        pendingHistoryUndoEventId = null
                        if (eventId != null) viewModel.undoHistoryEvent(eventId, acknowledgeWarnings = true)
                    }
                ) { Text("Uyarıyla devam et") }
            }
        )
    }

    if (!showPersonal && showCorrectionConfirm) {
        AlertDialog(
            onDismissRequest = { showCorrectionConfirm = false },
            title = { Text("Güvenli düzeltmeleri uygula?") },
            text = { Text("Yalnız deterministik öneriler uygulanır. Kayıtlar kullanıcı onayı olmadan değiştirilmez.") },
            dismissButton = {
                TextButton(onClick = { showCorrectionConfirm = false }) { Text("Vazgeç") }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCorrectionConfirm = false
                        viewModel.applySafeHealthCorrections(correctionRecordId)
                    }
                ) { Text("Uygula") }
            }
        )
    }
}
