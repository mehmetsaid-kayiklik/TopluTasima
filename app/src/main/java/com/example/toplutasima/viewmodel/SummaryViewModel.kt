package com.example.toplutasima.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.toplutasima.TopluTasimaApp
import com.example.toplutasima.data.local.entity.TripEntity
import com.example.toplutasima.data.repository.LocalTripRepository
import com.example.toplutasima.data.repository.toMap
import com.example.toplutasima.model.MonthSummary
import com.example.toplutasima.model.SummaryData
import com.example.toplutasima.model.WeekdayWeekendStats
import com.example.toplutasima.transit.TransitFeatureFlags
import com.example.toplutasima.transit.summary.TransitSummaryDataQuality
import com.example.toplutasima.transit.summary.TransitSummaryEngine
import com.example.toplutasima.transit.summary.TransitSummaryRequest
import com.example.toplutasima.ui.LocaleManager
import com.example.toplutasima.ui.S
import com.example.toplutasima.usecase.HeatmapData
import com.example.toplutasima.usecase.HeatmapUtils
import com.example.toplutasima.usecase.LineDetailStats
import com.example.toplutasima.usecase.MonthComparisonUtils
import com.example.toplutasima.usecase.MonthDelta
import com.example.toplutasima.usecase.ReportCardUtils
import com.example.toplutasima.usecase.SummaryCalculator
import com.example.toplutasima.usecase.TravelReportCards
import com.example.toplutasima.viewmodel.summary.LocalTransitSummaryDataSource
import com.example.toplutasima.viewmodel.summary.TransitSummaryDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth

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
    val reportSheetName: String = "",
    val selectedLineDetail: LineDetailStats? = null,
    val weekdayWeekendStats: WeekdayWeekendStats = WeekdayWeekendStats(),
    val dataQuality: TransitSummaryDataQuality? = null,
    val usingLiveRoomFlow: Boolean = false
)

class SummaryViewModel internal constructor(
    application: Application,
    private val dataSource: TransitSummaryDataSource,
    private val summaryEngine: TransitSummaryEngine,
    private val liveSummariesEnabled: Boolean,
    autoLoad: Boolean
) : AndroidViewModel(application) {
    constructor(application: Application) : this(
        application = application,
        dataSource = LocalTransitSummaryDataSource(
            LocalTripRepository(
                application,
                (application as TopluTasimaApp).database.tripDao()
            )
        ),
        summaryEngine = TransitSummaryEngine(),
        liveSummariesEnabled = TransitFeatureFlags.LIVE_ROOM_FLOWS &&
            TransitFeatureFlags.LIVE_TRANSIT_SUMMARIES,
        autoLoad = true
    )

    private val _uiState = MutableStateFlow(SummaryUiState())
    val uiState: StateFlow<SummaryUiState> = _uiState.asStateFlow()

    private fun lang() = LocaleManager.currentLanguage

    private var loadedSheet: String? = null
    private var loadedComparisonSheet: String? = null
    private var currentTripRows: List<Map<String, Any>> = emptyList()
    private var summaryJob: Job? = null
    private var latestSummaryRequestId: Long = 0L

    init {
        if (autoLoad) loadData()
    }

    fun loadData(sheetName: String? = null) {
        if (liveSummariesEnabled) {
            loadLiveData(sheetName)
        } else {
            loadLegacyData(sheetName)
        }
    }

    private fun loadLegacyData(sheetName: String? = null) {
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
                previousMonthName = "",
                selectedLineDetail = null
            )
            loadedComparisonSheet = null
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMsg = "")
            try {
                val (data, names, tripRows) = withContext(Dispatchers.IO) {
                    try {
                        dataSource.syncFromFirestore(fullSync = false)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // Firestore unavailable: keep showing the local cache.
                    }
                    val stats = dataSource.getLegacySummaryStats(targetSheet)
                    @Suppress("UNCHECKED_CAST")
                    val rows = dataSource.getLegacyAllTrips().firstOrNull()
                        ?.map { it.toMap() as Map<String, Any> }
                        ?: emptyList()
                    Triple(stats.first, stats.second, rows)
                }
                loadedSheet = targetSheet
                currentTripRows = tripRows

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
                        dataSource.getLegacySummaryStats(reportSheet).first
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
                    reportSheetName = reportSheet.orEmpty(),
                    selectedLineDetail = null,
                    weekdayWeekendStats = data.weekdayWeekendStats
                )

                // Auto-reload comparison if user is on tab 2
                if (_uiState.value.selectedInnerTab == 2) {
                    loadComparisonIfNeeded()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMsg = e.message ?: S.unknownError(lang()),
                    isLoading = false
                )
            }
        }
    }

    private fun loadLiveData(sheetName: String? = null) {
        val targetSheet = sheetName ?: _uiState.value.selectedSheet
        if (sheetName == null && loadedSheet == targetSheet && summaryJob?.isActive == true) return

        if (sheetName != null) {
            _uiState.value = _uiState.value.copy(
                selectedSheet = targetSheet,
                sheetMenuOpen = false,
                previousSummary = null,
                comparisonDeltas = emptyList(),
                previousMonthName = "",
                selectedLineDetail = null
            )
            loadedComparisonSheet = null
        }

        summaryJob?.cancel()
        val requestId = ++latestSummaryRequestId
        summaryJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMsg = "",
                usingLiveRoomFlow = true
            )
            try {
                try {
                    withContext(Dispatchers.IO) {
                        dataSource.syncFromFirestore(fullSync = false)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // A network failure must not hide the live Room cache.
                }

                liveBundleFlow(targetSheet)
                    .flowOn(Dispatchers.IO)
                    .mapLatest { bundle -> computeLiveSummary(targetSheet, bundle) }
                    .collectLatest { computation ->
                        if (requestId != latestSummaryRequestId) return@collectLatest
                        loadedSheet = targetSheet
                        currentTripRows = computation.selectedRows
                        _uiState.value = _uiState.value.copy(
                            summary = computation.summary,
                            sheetNames = listOf(ALL_SHEET) + computation.sheetNames,
                            selectedSheet = targetSheet,
                            isLoading = false,
                            heatmapData = computation.heatmap,
                            reportCards = computation.reportCards,
                            reportSheetName = computation.reportSheetName,
                            selectedLineDetail = null,
                            weekdayWeekendStats = computation.summary.weekdayWeekendStats,
                            dataQuality = computation.dataQuality,
                            usingLiveRoomFlow = true
                        )

                        if (_uiState.value.selectedInnerTab == 2) {
                            loadedComparisonSheet = null
                            loadComparisonIfNeeded()
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (requestId == latestSummaryRequestId) {
                    _uiState.value = _uiState.value.copy(
                        errorMsg = e.message ?: S.unknownError(lang()),
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun liveBundleFlow(sheetName: String): Flow<LiveSummaryBundle> {
        val monthSummaries = dataSource.observeMonthSummaries().distinctUntilChanged()
        if (sheetName == ALL_SHEET) {
            return combine(
                dataSource.observeAllTrips().distinctUntilChanged(),
                monthSummaries
            ) { records, months ->
                LiveSummaryBundle(
                    selectedRecords = records,
                    boundedTrendRecords = records,
                    monthSummaries = months
                )
            }.distinctUntilChanged()
        }

        val selectedYearMonth = requireNotNull(yearMonthForSheet(sheetName)) {
            "Unsupported summary month: $sheetName"
        }
        val startYearMonth = selectedYearMonth.minusMonths(LIVE_TREND_MONTHS - 1L)
        return combine(
            dataSource.observeTripsForMonth(selectedYearMonth.toString()).distinctUntilChanged(),
            dataSource.observeTripsForMonthRange(
                startYearMonth = startYearMonth.toString(),
                endYearMonth = selectedYearMonth.toString()
            ).distinctUntilChanged(),
            monthSummaries
        ) { selected, trend, months ->
            LiveSummaryBundle(
                selectedRecords = selected,
                boundedTrendRecords = trend,
                monthSummaries = months
            )
        }.distinctUntilChanged()
    }

    private suspend fun computeLiveSummary(
        selectedSheet: String,
        bundle: LiveSummaryBundle
    ): LiveSummaryComputation {
        val selectedRows = withContext(Dispatchers.Default) {
            bundle.selectedRecords.map(TripEntity::toSummaryMap)
        }
        val trendRows = if (bundle.boundedTrendRecords === bundle.selectedRecords) {
            selectedRows
        } else {
            withContext(Dispatchers.Default) {
                bundle.boundedTrendRecords.map(TripEntity::toSummaryMap)
            }
        }
        val sheetNames = bundle.monthSummaries.map { "${it.monthName} ${it.year}" }
        val result = summaryEngine.calculate(
            TransitSummaryRequest(
                selectedRecords = selectedRows,
                boundedTrendRecords = trendRows,
                availableSheetNames = sheetNames
            )
        )

        return withContext(Dispatchers.Default) {
            val reportSheet = if (selectedSheet != ALL_SHEET) selectedSheet else sheetNames.lastOrNull()
            val reportYearMonth = reportSheet?.let(::yearMonthForSheet)
            val reportRows = when {
                reportSheet == null -> emptyList()
                reportSheet == selectedSheet -> selectedRows
                reportYearMonth != null -> trendRows.filter { rowYearMonth(it) == reportYearMonth.toString() }
                else -> emptyList()
            }
            val heatmap = reportYearMonth?.let { period ->
                HeatmapUtils.buildHeatmapData(reportRows, period.year, period.monthValue)
            }
            val reportSummary = when {
                reportSheet == null -> null
                reportSheet == selectedSheet -> result.summary
                else -> SummaryCalculator.computeSummary(reportRows).first
            }

            LiveSummaryComputation(
                summary = result.summary,
                sheetNames = result.availableSheetNames,
                selectedRows = selectedRows,
                heatmap = heatmap,
                reportCards = reportSummary?.let { ReportCardUtils.build(it, heatmap) },
                reportSheetName = reportSheet.orEmpty(),
                dataQuality = result.selectedDataQuality
            )
        }
    }

    fun setSheetMenuOpen(open: Boolean) {
        _uiState.value = _uiState.value.copy(sheetMenuOpen = open)
    }

    fun refreshData() {
        if (liveSummariesEnabled) {
            summaryJob?.cancel()
            loadedSheet = null
            loadedComparisonSheet = null
            loadData()
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMsg = "")
            try {
                withContext(Dispatchers.IO) {
                    dataSource.syncFromFirestore(fullSync = false)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                // sync başarısız, lokal veriyle devam et
            }
            loadedSheet = null
            loadedComparisonSheet = null
            loadData()
        }
    }

    fun setSelectedInnerTab(tab: Int) {
        _uiState.value = _uiState.value.copy(selectedInnerTab = tab)
        // Lazy load comparison data when tab 2 is selected
        if (tab == 2) {
            loadComparisonIfNeeded()
        }
    }

    fun showLineDetail(lineName: String) {
        val detail = SummaryCalculator.computeLineDetail(
            allDocs = currentTripRows,
            sheetName = _uiState.value.selectedSheet,
            lineName = lineName
        ) ?: return
        _uiState.value = _uiState.value.copy(selectedLineDetail = detail)
    }

    fun dismissLineDetail() {
        _uiState.value = _uiState.value.copy(selectedLineDetail = null)
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
                    dataSource.getLegacySummaryStats(prevKey)
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
            } catch (e: CancellationException) {
                throw e
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
                dataSource.getTripsForMonthSnapshot(yearMonth).map { it.toMap() as Map<String, Any> }
            }
            HeatmapUtils.buildHeatmapData(trips, year, month)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
}

private data class LiveSummaryBundle(
    val selectedRecords: List<TripEntity>,
    val boundedTrendRecords: List<TripEntity>,
    val monthSummaries: List<MonthSummary>
)

private data class LiveSummaryComputation(
    val summary: SummaryData,
    val sheetNames: List<String>,
    val selectedRows: List<Map<String, Any>>,
    val heatmap: HeatmapData?,
    val reportCards: TravelReportCards?,
    val reportSheetName: String,
    val dataQuality: TransitSummaryDataQuality
)

@Suppress("UNCHECKED_CAST")
private fun TripEntity.toSummaryMap(): Map<String, Any> = toMap() as Map<String, Any>

private fun rowYearMonth(row: Map<String, Any>): String =
    row["yearMonth"]?.toString()?.takeIf { it.isNotBlank() }
        ?: com.example.toplutasima.usecase.TransitRecordCalculations.computeYearMonth(
            row["tarih"]?.toString().orEmpty()
        )

private fun yearMonthForSheet(sheetName: String): YearMonth? {
    val parts = sheetName.trim().split(Regex("\\s+"))
    if (parts.size < 2) return null
    val month = SUMMARY_MONTH_NUMBERS[parts.first()] ?: return null
    val year = parts.last().toIntOrNull() ?: return null
    return runCatching { YearMonth.of(year, month) }.getOrNull()
}

private val SUMMARY_MONTH_NUMBERS = mapOf(
    "Ocak" to 1,
    "Şubat" to 2,
    "Mart" to 3,
    "Nisan" to 4,
    "Mayıs" to 5,
    "Haziran" to 6,
    "Temmuz" to 7,
    "Ağustos" to 8,
    "Eylül" to 9,
    "Ekim" to 10,
    "Kasım" to 11,
    "Aralık" to 12
)

private const val LIVE_TREND_MONTHS = 6L
