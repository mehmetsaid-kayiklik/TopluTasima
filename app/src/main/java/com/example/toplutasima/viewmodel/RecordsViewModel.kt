package com.example.toplutasima.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.toplutasima.BuildConfig
import com.example.toplutasima.model.VehicleType
import com.example.toplutasima.network.FirestoreService
import com.example.toplutasima.usecase.RecordFilterState
import com.example.toplutasima.usecase.RecordFilterUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

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
    val stopCount: String,
    val originalRecord: Map<String, Any>
)

data class DayGroup(
    val date: String,
    val dayName: String,
    val trips: List<RecordRowUiModel>
)

data class RecordsUiState(
    val monthSummaries: List<FirestoreService.MonthSummary> = emptyList(),
    val selectedMonth: FirestoreService.MonthSummary? = null,
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
    val globalSearchResults: List<RecordRowUiModel> = emptyList()
)

class RecordsViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(RecordsUiState())
    val uiState: StateFlow<RecordsUiState> = _uiState.asStateFlow()

    init {
        loadMonthSummaries()
    }

    fun loadMonthSummaries() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMsg = "", saveMsg = "")
            try {
                val summaries = withContext(Dispatchers.IO) {
                    FirestoreService.fetchMonthSummaries()
                }
                _uiState.value = _uiState.value.copy(
                    monthSummaries = summaries,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMsg = "Hata: ${e.message}"
                )
            }
        }
    }

    fun selectMonth(month: FirestoreService.MonthSummary) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                selectedMonth = month,
                isLoading = true,
                errorMsg = "",
                saveMsg = "",
                filterState = RecordFilterState(),
                isFilterPanelOpen = false,
                globalSearchResults = emptyList(),
                globalSearchError = "",
                globalSearchLoading = false
            )
            try {
                val groups = withContext(Dispatchers.IO) {
                    val rawTrips = FirestoreService.fetchTripsForMonth(month.monthName, month.year)

                    val withDateTime = rawTrips.mapNotNull { rec ->
                        val tarih = rec["tarih"]?.toString() ?: return@mapNotNull null
                        val parts = tarih.split(".")
                        if (parts.size < 3) return@mapNotNull null
                        try {
                            val date = LocalDate.of(parts[2].toInt(), parts[1].toInt(), parts[0].toInt())
                            val timeParts = rec["planlananBinis"]?.toString()?.split(":")
                            val time = if (timeParts != null && timeParts.size >= 2) {
                                LocalTime.of(timeParts[0].toInt(), timeParts[1].toInt())
                            } else {
                                LocalTime.MIN
                            }
                            LocalDateTime.of(date, time) to rec
                        } catch (_: Exception) {
                            null
                        }
                    }

                    val sortedRecords = withDateTime.sortedBy { it.first }.map { it.second }

                    val dayGroupsMap = mutableMapOf<String, MutableList<RecordRowUiModel>>()
                    val dateToDayName = mutableMapOf<String, String>()

                    for (rec in sortedRecords) {
                        val date = rec["tarih"]?.toString() ?: continue
                        val dayName = rec["gun"]?.toString() ?: ""
                        dateToDayName[date] = dayName

                        val rowModel = mapFirestoreRecordToRow(rec)

                        if (!dayGroupsMap.containsKey(date)) {
                            dayGroupsMap[date] = mutableListOf()
                        }
                        dayGroupsMap[date]!!.add(rowModel)
                    }

                    dayGroupsMap.entries.map { entry ->
                        DayGroup(
                            date = entry.key,
                            dayName = dateToDayName[entry.key] ?: "",
                            trips = entry.value
                        )
                    }
                }

                val totalCount = RecordFilterUtils.countFilteredRecords(groups)
                val incomplete = RecordFilterUtils.findIncompleteRecords(groups)

                _uiState.value = _uiState.value.copy(
                    selectedMonthTrips = groups,
                    filteredTrips = groups,
                    filteredTotalCount = totalCount,
                    unfilteredTotalCount = totalCount,
                    incompleteRecords = incomplete,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMsg = "Ay kayıtları yüklenemedi: ${e.message}"
                )
            }
        }
    }

    fun clearSelectedMonth() {
        _uiState.value = _uiState.value.copy(
            selectedMonth = null,
            selectedMonthTrips = emptyList(),
            filteredTrips = emptyList(),
            filteredTotalCount = 0,
            unfilteredTotalCount = 0,
            incompleteRecords = emptyList(),
            isIncompleteExpanded = false,
            filterState = RecordFilterState(),
            isFilterPanelOpen = false,
            globalSearchResults = emptyList(),
            globalSearchError = "",
            globalSearchLoading = false
        )
    }

    fun clearGlobalSearch() {
        _uiState.value = _uiState.value.copy(
            globalSearchResults = emptyList(),
            globalSearchError = "",
            globalSearchLoading = false
        )
    }

    fun runGlobalSearch(query: String) {
        val q = query.trim()
        if (q.isEmpty()) {
            clearGlobalSearch()
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                globalSearchLoading = true,
                globalSearchError = "",
                globalSearchResults = emptyList()
            )
            try {
                val raw = withContext(Dispatchers.IO) { FirestoreService.fetchTrips() }
                val filter = RecordFilterState(searchQuery = q)
                val rows = raw
                    .map { mapFirestoreRecordToRow(it) }
                    .filter { RecordFilterUtils.matchesFilter(it, filter) }
                _uiState.value = _uiState.value.copy(
                    globalSearchLoading = false,
                    globalSearchResults = rows
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    globalSearchLoading = false,
                    globalSearchError = e.message ?: "Error"
                )
            }
        }
    }

    fun openLatestTransitRecord() {
        viewModelScope.launch {
            try {
                val rec = withContext(Dispatchers.IO) { FirestoreService.fetchTrips().firstOrNull() }
                val lang = com.example.toplutasima.ui.LocaleManager.currentLanguage
                if (rec == null) {
                    _uiState.value = _uiState.value.copy(
                        saveMsg = com.example.toplutasima.ui.S.noRecords(lang)
                    )
                } else {
                    setEditingRecord(rec)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(saveMsg = "❌ ${e.message}")
            }
        }
    }

    private fun mapFirestoreRecordToRow(rec: Map<String, Any>): RecordRowUiModel {
        val date = rec["tarih"]?.toString() ?: ""
        val dayName = rec["gun"]?.toString() ?: ""
        val turValue = rec["tur"]?.toString() ?: ""
        val typeDisplay = "${typeEmoji(turValue)} $turValue"
        return RecordRowUiModel(
            id = rec["firestoreDocId"]?.toString() ?: java.util.UUID.randomUUID().toString(),
            date = date,
            day = dayName,
            type = turValue,
            typeDisplay = typeDisplay,
            line = rec["hat"]?.toString() ?: "",
            direction = rec["yon"]?.toString() ?: "",
            boardingStop = rec["binisDuragi"]?.toString() ?: "",
            plannedDep = rec["planlananBinis"]?.toString() ?: "",
            actualDep = rec["gercekBinis"]?.toString() ?: "",
            delay = rec["gecikme"]?.toString() ?: "",
            alightingStop = rec["inisDuragi"]?.toString() ?: "",
            plannedArr = rec["planlananInis"]?.toString() ?: "",
            actualArr = rec["gercekInis"]?.toString() ?: "",
            dayType = rec["gununTipi"]?.toString() ?: "",
            weather = rec["havaDurumu"]?.toString() ?: "",
            seated = rec["oturabildimMi"]?.toString() ?: "",
            plannedDuration = rec["planlananYolSuresi"]?.toString() ?: "",
            actualDuration = rec["gercekYolSuresi"]?.toString() ?: "",
            note = rec["not"]?.toString() ?: "",
            ticketControl = rec["biletKontrolü"]?.toString() ?: "",
            distance = rec["mesafe"]?.toString() ?: "",
            stopCount = rec["durakSayisi"]?.toString() ?: "",
            originalRecord = rec
        )
    }

    private fun typeEmoji(type: String): String = when (type) {
        VehicleType.BUS.key -> "\uD83D\uDE8C"
        VehicleType.SBAHN.key -> "\uD83D\uDE86"
        VehicleType.UBAHN.key -> "\uD83D\uDE87"
        VehicleType.RERB.key -> "\uD83D\uDE82"
        VehicleType.STRASSENBAHN.key -> "\uD83D\uDE8B"
        VehicleType.FERNZUG.key -> "\uD83D\uDE84"
        else -> "\uD83D\uDE8C"
    }

    fun setEditingRecord(record: Map<String, Any>?) {
        _uiState.value = _uiState.value.copy(editingRecord = record, saveMsg = "")
    }

    fun updateRecord(docId: String, fields: Map<String, Any?>) {
        if (BuildConfig.DEBUG) Log.d("UpdateRecord", "docId='$docId' fields=${fields.keys}")
        if (docId.isBlank()) {
            _uiState.value = _uiState.value.copy(saveMsg = "❌ Kayıt ID bulunamadı")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, saveMsg = "")
            try {
                val ok = withContext(Dispatchers.IO) {
                    FirestoreService.updateTrip(docId, fields)
                }
                val currentMonth = _uiState.value.selectedMonth
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    saveMsg = if (ok) "✅" else "❌",
                    editingRecord = null
                )
                if (ok) {
                    loadMonthSummaries()
                    currentMonth?.let { selectMonth(it) }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    saveMsg = "❌ ${e.message}"
                )
            }
        }
    }

    fun deleteRecord(docId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, saveMsg = "")
            try {
                val ok = withContext(Dispatchers.IO) {
                    FirestoreService.deleteTrip(docId)
                }
                val currentMonth = _uiState.value.selectedMonth
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    saveMsg = if (ok) "✅ Silindi" else "❌",
                    editingRecord = null
                )
                if (ok) {
                    loadMonthSummaries()
                    currentMonth?.let { selectMonth(it) }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    saveMsg = "❌ ${e.message}"
                )
            }
        }
    }

    fun toggleFilterPanel() {
        _uiState.value = _uiState.value.copy(isFilterPanelOpen = !_uiState.value.isFilterPanelOpen)
    }

    fun updateFilter(newFilter: RecordFilterState) {
        val filtered = RecordFilterUtils.filterDayGroups(_uiState.value.selectedMonthTrips, newFilter)
        _uiState.value = _uiState.value.copy(
            filterState = newFilter,
            filteredTrips = filtered,
            filteredTotalCount = RecordFilterUtils.countFilteredRecords(filtered)
        )
    }

    fun clearFilters() {
        _uiState.value = _uiState.value.copy(
            filterState = RecordFilterState(),
            filteredTrips = _uiState.value.selectedMonthTrips,
            filteredTotalCount = _uiState.value.unfilteredTotalCount
        )
    }

    fun toggleIncompleteExpanded() {
        _uiState.value = _uiState.value.copy(isIncompleteExpanded = !_uiState.value.isIncompleteExpanded)
    }

    // ── Export functions ──────────────────────────────────────────────────

    fun toggleExportDialog() {
        _uiState.value = _uiState.value.copy(showExportDialog = !_uiState.value.showExportDialog)
    }

    fun exportMonth(format: com.example.toplutasima.usecase.ExportFormat, context: android.content.Context) {
        val lang = com.example.toplutasima.ui.LocaleManager.currentLanguage
        val state = _uiState.value
        val dayGroups = state.filteredTrips
        val monthTitle = "${com.example.toplutasima.ui.S.monthName(state.selectedMonth?.monthName ?: "", lang)} ${state.selectedMonth?.year ?: ""}"

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true, showExportDialog = false, exportResult = "")
            try {
                val file = withContext(Dispatchers.IO) {
                    when (format) {
                        com.example.toplutasima.usecase.ExportFormat.CSV -> {
                            val csv = com.example.toplutasima.usecase.ExportUseCase.generateCsv(dayGroups, lang)
                            com.example.toplutasima.usecase.ExportUseCase.writeToCache(context, csv, "csv")
                        }
                        com.example.toplutasima.usecase.ExportFormat.JSON -> {
                            val json = com.example.toplutasima.usecase.ExportUseCase.generateJson(dayGroups)
                            com.example.toplutasima.usecase.ExportUseCase.writeToCache(context, json, "json")
                        }
                        com.example.toplutasima.usecase.ExportFormat.PDF -> {
                            com.example.toplutasima.usecase.ExportUseCase.generatePdf(context, dayGroups, monthTitle, lang)
                        }
                    }
                }

                // Share via FileProvider
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val mimeType = when (format) {
                    com.example.toplutasima.usecase.ExportFormat.CSV -> "text/csv"
                    com.example.toplutasima.usecase.ExportFormat.JSON -> "application/json"
                    com.example.toplutasima.usecase.ExportFormat.PDF -> "application/pdf"
                }
                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    putExtra(android.content.Intent.EXTRA_SUBJECT, monthTitle)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(android.content.Intent.createChooser(shareIntent, com.example.toplutasima.ui.S.exportShare(lang)))

                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    exportResult = com.example.toplutasima.ui.S.exportSuccess(lang)
                )
            } catch (e: Exception) {
                Log.e("Export", "Export failed", e)
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    exportResult = "${com.example.toplutasima.ui.S.exportFailed(lang)}: ${e.message}"
                )
            }
        }
    }

    fun dismissExportResult() {
        _uiState.value = _uiState.value.copy(exportResult = "")
    }
}
