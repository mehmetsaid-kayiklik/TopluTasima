package com.example.toplutasima.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.toplutasima.model.SummaryData
import com.example.toplutasima.TopluTasimaApp
import com.example.toplutasima.data.repository.TripRepository
import com.example.toplutasima.data.repository.toMap
import com.example.toplutasima.ui.LocaleManager
import com.example.toplutasima.ui.S
import com.example.toplutasima.usecase.HeatmapData
import com.example.toplutasima.usecase.HeatmapUtils
import com.example.toplutasima.usecase.MonthComparisonUtils
import com.example.toplutasima.usecase.MonthDelta
import com.example.toplutasima.usecase.ReportCardUtils
import com.example.toplutasima.usecase.TravelReportCards
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.firstOrNull

private const val ALL_SHEET = "Tümü"

data class SummaryUiState(
    val summary: SummaryData? = null,
    val isLoading: Boolean = false,
    val errorMsg: String = "",
    val sheetNames: List<String> = listOf(ALL_SHEET),
    val selectedSheet: String = ALL_SHEET,
    val sheetMenuOpen: Boolean = false,
    val selectedInnerTab: Int = 0,
    // ── Comparison state ──
    val previousSummary: SummaryData? = null,
    val comparisonDeltas: List<MonthDelta> = emptyList(),
    val isComparisonLoading: Boolean = false,
    val previousMonthName: String = "",
    // ── Heatmap state ──
    val heatmapData: HeatmapData? = null,
    val reportCards: TravelReportCards? = null,
    val reportSheetName: String = ""
)

class SummaryViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val repository: TripRepository = TripRepository(
        application,
        (application as TopluTasimaApp).database.tripDao()
    )
    private val _uiState = MutableStateFlow(SummaryUiState())
    val uiState: StateFlow<SummaryUiState> = _uiState.asStateFlow()

    private fun lang() = LocaleManager.currentLanguage

    private var loadedSheet: String? = null
    private var loadedComparisonSheet: String? = null

    init {
        loadData()
    }

    fun loadData(sheetName: String? = null) {
        val targetSheet = sheetName ?: _uiState.value.selectedSheet

        if (sheetName == null && loadedSheet == targetSheet && _uiState.value.summary != null) {
            return
        }

        if (sheetName != null) {
            _uiState.value = _uiState.value.copy(
                selectedSheet = targetSheet,
                sheetMenuOpen = false,
                // Reset comparison when sheet changes
                previousSummary = null,
                comparisonDeltas = emptyList(),
                previousMonthName = ""
            )
            loadedComparisonSheet = null
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMsg = "")
            try {
                val (data, names) = withContext(Dispatchers.IO) {
                    repository.getSummaryStats(targetSheet)
                }
                loadedSheet = targetSheet

                // Build report cards from the selected month, or the latest month for "Tümü".
                val reportSheet = if (targetSheet != ALL_SHEET) {
                    targetSheet
                } else {
                    names.lastOrNull()
                }
                val heatmap = if (reportSheet != null) {
                    buildHeatmapForSheet(reportSheet)
                } else null
                val reportSummary = when {
                    reportSheet == null -> null
                    reportSheet == targetSheet -> data
                    else -> withContext(Dispatchers.IO) {
                        repository.getSummaryStats(reportSheet).first
                    }
                }
                val reportCards = reportSummary?.let { ReportCardUtils.build(it, heatmap) }

                _uiState.value = _uiState.value.copy(
                    summary = data,
                    sheetNames = listOf(ALL_SHEET) + names,
                    selectedSheet = targetSheet,
                    isLoading = false,
                    heatmapData = heatmap,
                    reportCards = reportCards,
                    reportSheetName = reportSheet.orEmpty()
                )

                // Auto-reload comparison if user is on tab 2
                if (_uiState.value.selectedInnerTab == 2) {
                    loadComparisonIfNeeded()
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMsg = e.message ?: S.unknownError(lang()),
                    isLoading = false
                )
            }
        }
    }

    fun setSheetMenuOpen(open: Boolean) {
        _uiState.value = _uiState.value.copy(sheetMenuOpen = open)
    }

    fun refreshData() {
        loadedSheet = null
        loadedComparisonSheet = null
        loadData()
    }

    fun setSelectedInnerTab(tab: Int) {
        _uiState.value = _uiState.value.copy(selectedInnerTab = tab)
        // Lazy load comparison data when tab 2 is selected
        if (tab == 2) {
            loadComparisonIfNeeded()
        }
    }

    // ── Comparison ──────────────────────────────────────────────────────────

    private fun loadComparisonIfNeeded() {
        val currentSheet = _uiState.value.selectedSheet
        if (currentSheet == ALL_SHEET) return
        if (loadedComparisonSheet == currentSheet) return // already loaded

        val prevKey = MonthComparisonUtils.previousMonthKey(currentSheet, _uiState.value.sheetNames) ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isComparisonLoading = true)
            try {
                val (prevData, _) = withContext(Dispatchers.IO) {
                    repository.getSummaryStats(prevKey)
                }
                loadedComparisonSheet = currentSheet
                // Use fresh state to get the current summary (not stale capture)
                val currentSummary = _uiState.value.summary ?: return@launch
                val deltas = MonthComparisonUtils.computeDeltas(currentSummary, prevData, lang())
                _uiState.value = _uiState.value.copy(
                    previousSummary = prevData,
                    comparisonDeltas = deltas,
                    isComparisonLoading = false,
                    previousMonthName = prevKey
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isComparisonLoading = false)
            }
        }
    }

    // ── Heatmap ─────────────────────────────────────────────────────────────

    private suspend fun buildHeatmapForSheet(sheetName: String): HeatmapData? {
        return try {
            val parts = sheetName.split(" ")
            if (parts.size < 2) return null
            val monthNames = mapOf(
                "Ocak" to 1, "Şubat" to 2, "Mart" to 3, "Nisan" to 4,
                "Mayıs" to 5, "Haziran" to 6, "Temmuz" to 7, "Ağustos" to 8,
                "Eylül" to 9, "Ekim" to 10, "Kasım" to 11, "Aralık" to 12
            )
            val month = monthNames[parts[0]] ?: return null
            val year = parts[1].toIntOrNull() ?: return null
            val trips = withContext(Dispatchers.IO) {
                val monthStr = String.format(java.util.Locale.US, "%02d", month)
                val yearMonth = "$year-$monthStr"
                repository.getTripsForMonth(yearMonth).firstOrNull()?.map { it.toMap() as Map<String, Any> } ?: emptyList()
            }
            HeatmapUtils.buildHeatmapData(trips, year, month)
        } catch (_: Exception) {
            null
        }
    }
}
