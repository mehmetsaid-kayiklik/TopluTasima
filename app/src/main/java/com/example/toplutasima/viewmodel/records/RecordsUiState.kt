package com.example.toplutasima.viewmodel.records

import com.example.toplutasima.model.MonthSummary
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
    val seatmateNote: String = ""
)

data class DayGroup(
    val date: String,
    val dayName: String,
    val trips: List<RecordRowUiModel>
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
    val activeProfiles: List<com.example.toplutasima.data.local.entity.ProfileEntity> = emptyList()
)
