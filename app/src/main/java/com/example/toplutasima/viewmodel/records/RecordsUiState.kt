package com.example.toplutasima.viewmodel.records

import com.example.toplutasima.model.MonthSummary
import com.example.toplutasima.domain.transit.health.TransitHealthCorrection
import com.example.toplutasima.domain.transit.health.TransitHealthIssue
import com.example.toplutasima.transit.provenance.TransitFieldProvenance
import com.example.toplutasima.transit.duplicate.TransitDuplicateCandidate
import com.example.toplutasima.transit.duplicate.TransitDuplicateMergePreview
import com.example.toplutasima.transit.duplicate.TransitMergeValueSource
import com.example.toplutasima.transit.export.PreparedTransitExport
import com.example.toplutasima.transit.export.TransitSafCreateDocumentRequest
import com.example.toplutasima.transit.history.TransitChangeEvent
import com.example.toplutasima.usecase.RecordFilterState

data class RecordRowUiModel(
    val id: String,
    val date: String,
    val day: String,
    val type: String,
    val typeDisplay: String,
    val line: String,
    val direction: String,
    val boardingStop: String,
    val plannedDep: String,
    val actualDep: String,
    val delay: String,
    val alightingStop: String,
    val plannedArr: String,
    val actualArr: String,
    val dayType: String,
    val weather: String,
    val seated: String,
    val plannedDuration: String,
    val actualDuration: String,
    val note: String,
    val ticketControl: String,
    val distance: String,
    val orsDistance: String,
    val rmvDistance: String,
    val rmvDistanceStatus: String,
    val stopCount: String,
    val originalRecord: Map<String, Any>,
    val profileId: String = "",
    val profileName: String = "",
    val seatmateNote: String = "",
    /** Canonical Room record ID; sync receipts and in-memory provenance are keyed by this value. */
    val localRecordId: String = id,
    val firestoreDocumentId: String = "",
    val healthIssues: List<TransitHealthIssue> = emptyList(),
    val provenanceByField: Map<String, TransitFieldProvenance> = emptyMap()
)

data class DayGroup(
    val date: String,
    val dayName: String,
    val trips: List<RecordRowUiModel>
)

data class TransitHistoryUndoUiModel(
    val enabled: Boolean,
    val requiresWarningConfirmation: Boolean = false,
    val disabledReason: String? = null
)

data class RecordsUiState(
    val monthSummaries: List<MonthSummary> = emptyList(),
    val selectedMonth: MonthSummary? = null,
    val selectedMonthTrips: List<DayGroup> = emptyList(),
    val isLoading: Boolean = false,
    val errorMsg: String = "",
    val editingRecord: Map<String, Any>? = null,
    val isSaving: Boolean = false,
    val saveMsg: String = "",
    val filterState: RecordFilterState = RecordFilterState(),
    val isFilterPanelOpen: Boolean = false,
    val filteredTrips: List<DayGroup> = emptyList(),
    val filteredTotalCount: Int = 0,
    val unfilteredTotalCount: Int = 0,
    val incompleteRecords: List<RecordRowUiModel> = emptyList(),
    val isIncompleteExpanded: Boolean = false,
    // ── Export state ──
    val isExporting: Boolean = false,
    val exportResult: String = "",
    val showExportDialog: Boolean = false,
    /** Ay listesinden tüm kayıtlarda serbest metin arama sonuçları */
    val globalSearchLoading: Boolean = false,
    val globalSearchError: String = "",
    val globalSearchResults: List<RecordRowUiModel> = emptyList(),
    val activeProfiles: List<com.example.toplutasima.data.local.entity.ProfileEntity> = emptyList(),
    val healthIssuesByRecordId: Map<String, List<TransitHealthIssue>> = emptyMap(),
    val healthCorrections: List<TransitHealthCorrection> = emptyList(),
    val selectedHealthRecordId: String? = null,
    val isHealthScanning: Boolean = false,
    val isApplyingHealthCorrections: Boolean = false,
    val fullHealthScanMessage: String = "",
    val fullHealthIssueCount: Int = 0,
    val duplicateCandidates: List<TransitDuplicateCandidate> = emptyList(),
    val selectedDuplicateCandidate: TransitDuplicateCandidate? = null,
    val duplicateFieldSelections: Map<String, TransitMergeValueSource> = emptyMap(),
    val duplicateMergePreview: TransitDuplicateMergePreview? = null,
    val isResolvingDuplicate: Boolean = false,
    val duplicateResolutionMessage: String = "",
    val selectedHistoryRecordId: String? = null,
    val selectedHistoryEvents: List<TransitChangeEvent> = emptyList(),
    val selectedHistoryEventId: String? = null,
    val historyUndoByEventId: Map<String, TransitHistoryUndoUiModel> = emptyMap(),
    val isHistoryLoading: Boolean = false,
    val historyMessage: String = "",
    val preparedTransitExport: PreparedTransitExport? = null,
    val transitExportDocumentRequest: TransitSafCreateDocumentRequest? = null
)
