package com.example.toplutasima.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.toplutasima.BuildConfig
import com.example.toplutasima.model.MonthSummary
import com.example.toplutasima.usecase.RecordFilterState
import com.example.toplutasima.usecase.RecordFilterUtils
import com.example.toplutasima.usecase.RecordRowMapper
import com.example.toplutasima.viewmodel.records.DayGroup
import com.example.toplutasima.viewmodel.records.RecordRowUiModel
import com.example.toplutasima.viewmodel.records.RecordsUiState

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.flow.firstOrNull
import com.example.toplutasima.TopluTasimaApp
import com.example.toplutasima.data.repository.LocalTripRepository
import com.example.toplutasima.data.repository.toEntity
import com.example.toplutasima.data.repository.toMap
import kotlinx.coroutines.CancellationException

class RecordsViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(RecordsUiState())
    val uiState: StateFlow<RecordsUiState> = _uiState.asStateFlow()

    private val tripRepository: LocalTripRepository

    private var allProfilesMap = emptyMap<String, com.example.toplutasima.data.local.entity.ProfileEntity>()
    private var allLinksMap = emptyMap<String, com.example.toplutasima.data.local.entity.TripProfileLinkEntity>()

    init {
        val app = application as TopluTasimaApp
        tripRepository = LocalTripRepository(application, app.database.tripDao())

        loadProfileData()
        loadMonthSummaries()
    }

    fun loadProfileData() {
        viewModelScope.launch {
            try {
                val db = com.example.toplutasima.data.local.AppDatabase.getDatabase(getApplication())
                val profiles = db.profileDao().getAllProfiles()
                val links = db.tripProfileLinkDao().getAllLinks()

                allProfilesMap = profiles.associateBy { it.id }
                allLinksMap = links.associateBy { it.tripStableKey }

                _uiState.value = _uiState.value.copy(
                    activeProfiles = profiles.filter { !it.archived }
                )

                _uiState.value.selectedMonth?.let { selectMonth(it) }
            } catch (_: Exception) {}
        }
    }

    fun loadMonthSummaries() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMsg = "", saveMsg = "")
            try {
                val summaries = withContext(Dispatchers.IO) {
                    try {
                        tripRepository.syncFromFirestore(fullSync = false)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // Firestore unavailable: keep showing the local cache.
                    }
                    tripRepository.getMonthSummaries()
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

    public fun syncAndReload() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMsg = "")
            try {
                withContext(Dispatchers.IO) {
                    tripRepository.syncFromFirestore(fullSync = false)
                }
                val updatedSummaries = tripRepository.getMonthSummaries()
                val currentMonth = _uiState.value.selectedMonth
                if (currentMonth != null) {
                    val monthStillExists = updatedSummaries.any { it.sortKey == currentMonth.sortKey }
                    if (monthStillExists) {
                        selectMonth(currentMonth)
                    } else {
                        clearSelectedMonth()
                    }
                }
                _uiState.value = _uiState.value.copy(
                    monthSummaries = updatedSummaries,
                    isLoading = false,
                    saveMsg = "✅ Senkronize edildi"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    saveMsg = "⚠️ Firebase sync başarısız"
                )
            }
        }
    }

    fun selectMonth(month: MonthSummary) {
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
                    val yearMonth = "${month.sortKey.substring(0, 4)}-${month.sortKey.substring(4, 6)}"
                    val rawEntities = tripRepository.getTripsForMonth(yearMonth).firstOrNull() ?: emptyList()
                    val rawTrips = rawEntities.map { it.toMap() }

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

                        val rowModel = mapFirestoreRecordToRow(rec as Map<String, Any>)

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
                val rawEntities = withContext(Dispatchers.IO) { tripRepository.searchTrips(q) }
                val raw = rawEntities.map { it.toMap() }
                val rows = raw
                    .map { mapFirestoreRecordToRow(it as Map<String, Any>) }
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
                val recEntity = withContext(Dispatchers.IO) { tripRepository.getAllTrips().firstOrNull()?.firstOrNull() }
                val rec = recEntity?.toMap()
                val lang = com.example.toplutasima.ui.LocaleManager.currentLanguage
                if (rec == null) {
                    _uiState.value = _uiState.value.copy(
                        saveMsg = com.example.toplutasima.ui.S.noRecords(lang)
                    )
                } else {
                    setEditingRecord(rec as Map<String, Any>)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(saveMsg = "❌ ${e.message}")
            }
        }
    }

    private fun mapFirestoreRecordToRow(rec: Map<String, Any>): RecordRowUiModel {
        val tripId = rec["firestoreDocId"]?.toString()?.takeIf { it.isNotBlank() }
            ?: rec["id"]?.toString()
            ?: ""
        val link = allLinksMap[tripId]
        val profile = link?.profileId?.let { allProfilesMap[it] }

        return RecordRowMapper.fromFirestoreRecord(
            rec = rec,
            profileId = link?.profileId ?: "",
            profileName = profile?.displayName ?: "",
            seatmateNote = link?.seatmateNote ?: ""
        )
    }

    fun setEditingRecord(record: Map<String, Any>?) {
        val enriched = record?.let { rec ->
            val tripId = rec["firestoreDocId"]?.toString()?.takeIf { it.isNotBlank() }
                ?: rec["id"]?.toString()
                ?: ""
            val link = allLinksMap[tripId]
            rec + mapOf(
                "profileId" to (link?.profileId ?: ""),
                "seatmateNote" to (link?.seatmateNote ?: "")
            )
        }
        _uiState.value = _uiState.value.copy(editingRecord = enriched, saveMsg = "")
    }

    fun updateRecord(docId: String, fields: Map<String, Any?>, profileId: String? = null, seatmateNote: String? = null) {
        if (BuildConfig.DEBUG) Log.d("UpdateRecord", "docId='$docId' fields=${fields.keys} profileId='$profileId'")
        if (docId.isBlank()) {
            _uiState.value = _uiState.value.copy(saveMsg = "❌ Kayıt ID bulunamadı")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, saveMsg = "")
            try {
                val ok = withContext(Dispatchers.IO) {
                    val existingEntity = tripRepository.getTripByFirestoreDocId(docId)
                        ?: tripRepository.getTripById(docId)
                    if (existingEntity != null) {
                        val mergedMap = existingEntity.toMap().toMutableMap()
                        mergedMap.putAll(fields)
                        mergedMap["firestoreDocId"] = existingEntity.firestoreDocId?.takeIf { it.isNotBlank() } ?: docId
                        tripRepository.saveTrip(mergedMap.toEntity())

                        // Update local-only profile link
                        if (profileId != null) {
                            val db = com.example.toplutasima.data.local.AppDatabase.getDatabase(getApplication())
                            val linkDao = db.tripProfileLinkDao()
                            if (profileId.isBlank()) {
                                linkDao.deleteLinksForTrip(docId, existingEntity.firestoreDocId ?: "")
                            } else {
                                val tripKey = existingEntity.firestoreDocId?.takeIf { it.isNotBlank() } ?: docId
                                val existingLinks = if (existingEntity.firestoreDocId.orEmpty().isNotBlank()) {
                                    linkDao.getLinksForTrip(existingEntity.firestoreDocId.orEmpty())
                                } else {
                                    linkDao.getLinksForTrip(docId)
                                }
                                if (existingLinks.isNotEmpty()) {
                                    val existing = existingLinks.first()
                                    linkDao.upsert(
                                        existing.copy(
                                            profileId = profileId,
                                            seatmateNote = seatmateNote,
                                            updatedAt = System.currentTimeMillis()
                                        )
                                    )
                                } else {
                                    linkDao.upsert(
                                        com.example.toplutasima.data.local.entity.TripProfileLinkEntity(
                                            id = java.util.UUID.randomUUID().toString(),
                                            tripStableKey = tripKey,
                                            profileId = profileId,
                                            seatmateNote = seatmateNote,
                                            createdAt = System.currentTimeMillis(),
                                            updatedAt = System.currentTimeMillis()
                                        )
                                    )
                                }
                            }
                        }
                        true
                    } else {
                        false
                    }
                }
                val currentMonth = _uiState.value.selectedMonth
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    saveMsg = if (ok) "✅" else "❌",
                    editingRecord = null
                )
                if (ok) {
                    loadProfileData() // will refresh maps and selectMonth automatically!
                    loadMonthSummaries()
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
                    tripRepository.deleteTrip(docId)
                    true
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
