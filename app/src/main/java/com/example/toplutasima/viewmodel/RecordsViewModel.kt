package com.example.toplutasima.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.toplutasima.BuildConfig
import com.example.toplutasima.TopluTasimaApp
import com.example.toplutasima.auth.CurrentUserProvider
import com.example.toplutasima.data.repository.LocalTripRepository
import com.example.toplutasima.data.repository.ProfileSyncRepository
import com.example.toplutasima.data.repository.toEntity
import com.example.toplutasima.data.repository.toMap
import com.example.toplutasima.data.local.entity.TripEntity
import com.example.toplutasima.model.MonthSummary
import com.example.toplutasima.domain.transit.health.TransitHealthCorrection
import com.example.toplutasima.domain.transit.health.TransitHealthIssue
import com.example.toplutasima.domain.transit.health.TransitHealthScanResult
import com.example.toplutasima.domain.transit.health.TransitHealthSeverity
import com.example.toplutasima.domain.transit.validation.TransitRecordSegmentInput
import com.example.toplutasima.domain.transit.validation.TransitRecordValidationInput
import com.example.toplutasima.transit.TransitFeatureFlags
import com.example.toplutasima.transit.duplicate.TransitDuplicateCandidate
import com.example.toplutasima.transit.duplicate.TransitDuplicateCandidateUseCase
import com.example.toplutasima.transit.duplicate.TransitDuplicateDecision
import com.example.toplutasima.transit.duplicate.TransitDuplicateDecisionStore
import com.example.toplutasima.transit.duplicate.TransitDuplicateMergeSelection
import com.example.toplutasima.transit.duplicate.TransitDuplicateMergeUseCase
import com.example.toplutasima.transit.duplicate.TransitMergeValueSource
import com.example.toplutasima.transit.duplicate.TransitDuplicateResolutionCoordinator
import com.example.toplutasima.transit.duplicate.TransitDuplicateResolutionResult
import com.example.toplutasima.transit.export.TransitExportFormat
import com.example.toplutasima.transit.export.TransitExportHealthSummary
import com.example.toplutasima.transit.export.TransitExportPreparationResult
import com.example.toplutasima.transit.export.TransitExportRecordMapper
import com.example.toplutasima.transit.export.TransitExportRequest
import com.example.toplutasima.transit.export.TransitExportScope
import com.example.toplutasima.transit.export.TransitExportScopeType
import com.example.toplutasima.transit.export.TransitExportSection
import com.example.toplutasima.transit.export.TransitExportUseCase
import com.example.toplutasima.transit.export.TransitSafExportCoordinator
import com.example.toplutasima.transit.export.TransitExportWriteResult
import com.example.toplutasima.transit.history.TransitChangeEventDraft
import com.example.toplutasima.transit.history.TransitChangeHistoryStore
import com.example.toplutasima.transit.history.TransitChangeOperation
import com.example.toplutasima.transit.history.TransitChangeSource
import com.example.toplutasima.transit.history.TransitHistorySyncStatus
import com.example.toplutasima.transit.history.TransitHistoryEvidenceDurability
import com.example.toplutasima.transit.history.TransitHistoryProvenanceEvidence
import com.example.toplutasima.transit.history.TransitHistoryProvenanceSource
import com.example.toplutasima.transit.history.TransitFieldChange
import com.example.toplutasima.transit.history.TransitHistoryValue
import com.example.toplutasima.transit.history.TransitHistoryUndoEligibilityUseCase
import com.example.toplutasima.transit.history.TransitRecordDiffUseCase
import com.example.toplutasima.transit.history.TransitUndoDecision
import com.example.toplutasima.transit.provenance.TransitRecordProvenanceResolver
import com.example.toplutasima.transit.provenance.TransitRecordProvenanceStore
import com.example.toplutasima.transit.sync.TransitSyncStatusStore
import com.example.toplutasima.transit.sync.TransitSyncPhase
import com.example.toplutasima.transit.summary.TransitSummaryEngine
import com.example.toplutasima.transit.summary.TransitSummaryRequest
import com.example.toplutasima.transit.insights.TransitInsightsEngine
import com.example.toplutasima.transit.insights.TransitInsightsRequest
import com.example.toplutasima.transit.export.TransitExportInsight
import com.example.toplutasima.transit.export.TransitExportMetric
import com.example.toplutasima.transit.export.TransitExportProvenanceEvidence
import com.example.toplutasima.usecase.RecordFilterState
import com.example.toplutasima.usecase.RecordFilterUtils
import com.example.toplutasima.usecase.RecordRowMapper
import com.example.toplutasima.usecase.TransitRecordCalculations
import com.example.toplutasima.usecase.transit.TransitHealthCorrectionUseCase
import com.example.toplutasima.usecase.transit.TransitPostSaveHealthUseCase
import com.example.toplutasima.viewmodel.records.DayGroup
import com.example.toplutasima.viewmodel.records.LocalRecordsTripDataSource
import com.example.toplutasima.viewmodel.records.RecordRowUiModel
import com.example.toplutasima.viewmodel.records.RecordsTripDataSource
import com.example.toplutasima.viewmodel.records.RecordsUiState
import com.example.toplutasima.viewmodel.records.TransitExportUiOptions
import com.example.toplutasima.viewmodel.records.TransitHistoryUndoUiModel
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

class RecordsViewModel internal constructor(
    application: Application,
    private val profileSyncRepository: ProfileSyncRepository,
    private val tripRepository: RecordsTripDataSource,
    autoLoad: Boolean,
    private val healthUseCase: TransitPostSaveHealthUseCase = TransitPostSaveHealthUseCase(),
    private val healthCorrectionUseCase: TransitHealthCorrectionUseCase = TransitHealthCorrectionUseCase(),
    private val provenanceStore: TransitRecordProvenanceStore = TransitRecordProvenanceStore(),
    private val provenanceResolver: TransitRecordProvenanceResolver = TransitRecordProvenanceResolver(),
    private val postSaveHealthEnabled: Boolean = TransitFeatureFlags.POST_SAVE_DATA_HEALTH,
    private val provenanceEnabled: Boolean = TransitFeatureFlags.PROVENANCE_BADGES,
    private val duplicateResolutionEnabled: Boolean =
        TransitFeatureFlags.TRANSIT_DUPLICATE_RESOLUTION && postSaveHealthEnabled,
    private val changeHistoryEnabled: Boolean = TransitFeatureFlags.TRANSIT_CHANGE_HISTORY,
    private val transitExportEnabled: Boolean = TransitFeatureFlags.TRANSIT_EXPORT,
    private val duplicateCandidateUseCase: TransitDuplicateCandidateUseCase =
        TransitDuplicateCandidateUseCase(enabled = duplicateResolutionEnabled),
    private val duplicateMergeUseCase: TransitDuplicateMergeUseCase =
        TransitDuplicateMergeUseCase(enabled = duplicateResolutionEnabled),
    private val duplicateResolutionCoordinator: TransitDuplicateResolutionCoordinator =
        TransitDuplicateResolutionCoordinator(enabled = duplicateResolutionEnabled),
    private val duplicateDecisionStore: TransitDuplicateDecisionStore =
        TransitDuplicateDecisionStore(
            readRaw = { null },
            writeRaw = { _ -> },
            enabled = duplicateResolutionEnabled
        ),
    private val changeHistoryStore: TransitChangeHistoryStore =
        TransitChangeHistoryStore(
            readRaw = { null },
            writeRaw = { false },
            enabled = changeHistoryEnabled
        ),
    private val recordDiffUseCase: TransitRecordDiffUseCase = TransitRecordDiffUseCase(),
    private val historyUndoUseCase: TransitHistoryUndoEligibilityUseCase =
        TransitHistoryUndoEligibilityUseCase(),
    private val transitExportUseCase: TransitExportUseCase =
        TransitExportUseCase(enabled = transitExportEnabled),
    private val summaryEngine: TransitSummaryEngine = TransitSummaryEngine(),
    private val insightsEngine: TransitInsightsEngine = TransitInsightsEngine(summaryEngine),
    private val syncStatusStore: TransitSyncStatusStore = TransitSyncStatusStore(
        readRaw = { null },
        writeRaw = { _ -> },
        initialUserId = null
    ),
    private val transitSafExportCoordinator: TransitSafExportCoordinator =
        TransitSafExportCoordinator(application.contentResolver)
) : AndroidViewModel(application) {
    constructor(
        application: Application,
        profileSyncRepository: ProfileSyncRepository
    ) : this(
        application = application,
        profileSyncRepository = profileSyncRepository,
        tripRepository = LocalRecordsTripDataSource(
            LocalTripRepository(
                application,
                (application as TopluTasimaApp).database.tripDao()
            )
        ),
        autoLoad = true,
        duplicateDecisionStore = TransitDuplicateDecisionStore.create(
            application,
            TransitFeatureFlags.POST_SAVE_DATA_HEALTH &&
                TransitFeatureFlags.TRANSIT_DUPLICATE_RESOLUTION
        ),
        changeHistoryStore = TransitChangeHistoryStore.create(
            application,
            TransitFeatureFlags.TRANSIT_CHANGE_HISTORY
        ),
        syncStatusStore = TransitSyncStatusStore.get(application)
    )

    constructor(
        application: Application,
        profileSyncRepository: ProfileSyncRepository,
        localTripRepository: LocalTripRepository,
        healthUseCase: TransitPostSaveHealthUseCase,
        healthCorrectionUseCase: TransitHealthCorrectionUseCase,
        provenanceStore: TransitRecordProvenanceStore,
        provenanceResolver: TransitRecordProvenanceResolver,
        duplicateCandidateUseCase: TransitDuplicateCandidateUseCase,
        duplicateMergeUseCase: TransitDuplicateMergeUseCase,
        duplicateResolutionCoordinator: TransitDuplicateResolutionCoordinator,
        duplicateDecisionStore: TransitDuplicateDecisionStore,
        changeHistoryStore: TransitChangeHistoryStore,
        recordDiffUseCase: TransitRecordDiffUseCase,
        historyUndoUseCase: TransitHistoryUndoEligibilityUseCase,
        transitExportUseCase: TransitExportUseCase,
        summaryEngine: TransitSummaryEngine,
        insightsEngine: TransitInsightsEngine,
        syncStatusStore: TransitSyncStatusStore
    ) : this(
        application = application,
        profileSyncRepository = profileSyncRepository,
        tripRepository = LocalRecordsTripDataSource(localTripRepository),
        autoLoad = true,
        healthUseCase = healthUseCase,
        healthCorrectionUseCase = healthCorrectionUseCase,
        provenanceStore = provenanceStore,
        provenanceResolver = provenanceResolver,
        duplicateCandidateUseCase = duplicateCandidateUseCase,
        duplicateMergeUseCase = duplicateMergeUseCase,
        duplicateResolutionCoordinator = duplicateResolutionCoordinator,
        duplicateDecisionStore = duplicateDecisionStore,
        changeHistoryStore = changeHistoryStore,
        recordDiffUseCase = recordDiffUseCase,
        historyUndoUseCase = historyUndoUseCase,
        transitExportUseCase = transitExportUseCase,
        summaryEngine = summaryEngine,
        insightsEngine = insightsEngine,
        syncStatusStore = syncStatusStore
    )

    private val _uiState = MutableStateFlow(RecordsUiState())
    val uiState: StateFlow<RecordsUiState> = _uiState.asStateFlow()

    private var monthLoadJob: Job? = null
    private var latestMonthRequestId = 0L
    private var searchJob: Job? = null
    private var latestSearchRequestId = 0L
    private var duplicatePreviewJob: Job? = null
    private var latestDuplicatePreviewRequestId = 0L
    private var historyLoadJob: Job? = null
    private var latestHistoryRequestId = 0L

    private var allProfilesMap = emptyMap<String, com.example.toplutasima.data.local.entity.ProfileEntity>()
    private var allLinksMap = emptyMap<String, com.example.toplutasima.data.local.entity.TripProfileLinkEntity>()

    init {
        if (autoLoad) {
            loadProfileData()
            loadMonthSummaries()
        }
    }

    fun loadProfileData() {
        viewModelScope.launch {
            try {
                // Firestore'dan sharedWithTransit=true olan kişileri çek ve Room'u güncelle.
                // Hata alınırsa Room cache'i kullanılır; sessizce devam edilir.
                try {
                    profileSyncRepository.refreshSharedProfiles()
                } catch (_: Exception) {}

                val userId = CurrentUserProvider.requireUserId()
                val db = com.example.toplutasima.data.local.AppDatabase.getDatabase(getApplication())
                val profiles = db.profileDao().getAllProfiles(userId)
                val links = db.tripProfileLinkDao().getAllLinks(userId)

                allProfilesMap = profiles.associateBy { it.id }
                allLinksMap = links.associateBy { it.tripStableKey }

                _uiState.value = _uiState.value.copy(
                    activeProfiles = profiles.filter { it.sharedWithTransit && !it.archived }
                )

                if (!TransitFeatureFlags.LIVE_ROOM_FLOWS) {
                    _uiState.value.selectedMonth?.let { selectMonth(it) }
                }
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
                    monthSummaries = summaries.sortedBy { it.sortKey },
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
                    monthSummaries = updatedSummaries.sortedBy { it.sortKey },
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
        val requestId = beginMonthRequest()
        invalidateSearchRequest()
        monthLoadJob = viewModelScope.launch {
            if (!isLatestMonthRequest(requestId)) return@launch
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
                val yearMonth = "${month.sortKey.substring(0, 4)}-${month.sortKey.substring(4, 6)}"
                val recordsFlow = if (TransitFeatureFlags.LIVE_ROOM_FLOWS) {
                    tripRepository.observeTripsForMonth(yearMonth)
                } else {
                    tripRepository.getTripsForMonth(yearMonth)
                }
                recordsFlow.flowOn(Dispatchers.IO).collectLatest { rawEntities ->
                    val metadata = withContext(Dispatchers.Default) {
                        val scan = if (postSaveHealthEnabled) {
                            healthUseCase.scan(
                                records = rawEntities,
                                provenanceStore = provenanceStore,
                                provenanceEnabled = provenanceEnabled
                            )
                        } else {
                            null
                        }
                        val provenance = if (provenanceEnabled) {
                            rawEntities.associate { entity ->
                                entity.id to provenanceResolver.resolveFields(
                                    userId = entity.userId,
                                    localRecordId = entity.id,
                                    record = entity.toMap(),
                                    store = provenanceStore,
                                    enabled = true
                                )
                            }
                        } else {
                            emptyMap()
                        }
                        val duplicates = if (duplicateResolutionEnabled) {
                            val userId = rawEntities.firstOrNull()?.userId.orEmpty()
                            duplicateCandidateUseCase.findCandidates(
                                records = rawEntities,
                                excludedRecordIds = syncStatusStore.tombstonedRecordIds(userId),
                                decisionLookup = duplicateDecisionStore
                            )
                        } else {
                            emptyList()
                        }
                        MonthRecordMetadata(
                            scan = scan,
                            provenanceByRecordId = provenance,
                            duplicateCandidates = duplicates
                        )
                    }
                    val groups = withContext(Dispatchers.Default) {
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

                            val baseRow = mapFirestoreRecordToRow(rec as Map<String, Any>)
                            val rowModel = baseRow.copy(
                                healthIssues = metadata.scan?.issuesByRecordId
                                    ?.get(baseRow.localRecordId)
                                    .orEmpty(),
                                provenanceByField = metadata.provenanceByRecordId[baseRow.localRecordId]
                                    .orEmpty()
                            )

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

                    if (!isLatestMonthRequest(requestId)) return@collectLatest
                    val filterState = _uiState.value.filterState
                    val filteredGroups = RecordFilterUtils.filterDayGroups(groups, filterState)
                    _uiState.value = _uiState.value.copy(
                        selectedMonthTrips = groups,
                        filteredTrips = filteredGroups,
                        filteredTotalCount = RecordFilterUtils.countFilteredRecords(filteredGroups),
                        unfilteredTotalCount = totalCount,
                        incompleteRecords = incomplete,
                        healthIssuesByRecordId = metadata.scan?.issuesByRecordId.orEmpty(),
                        healthCorrections = metadata.scan?.corrections.orEmpty(),
                        fullHealthIssueCount = metadata.scan?.issues?.size ?: 0,
                        duplicateCandidates = metadata.duplicateCandidates,
                        isLoading = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!isLatestMonthRequest(requestId)) return@launch
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMsg = "Ay kayıtları yüklenemedi: ${e.message}"
                )
            }
        }
    }

    fun clearSelectedMonth() {
        invalidateMonthRequest()
        invalidateSearchRequest()
        _uiState.value = _uiState.value.copy(
            selectedMonth = null,
            selectedMonthTrips = emptyList(),
            filteredTrips = emptyList(),
            isLoading = false,
            filteredTotalCount = 0,
            unfilteredTotalCount = 0,
            incompleteRecords = emptyList(),
            isIncompleteExpanded = false,
            filterState = RecordFilterState(),
            isFilterPanelOpen = false,
            globalSearchResults = emptyList(),
            globalSearchError = "",
            globalSearchLoading = false,
            healthIssuesByRecordId = emptyMap(),
            healthCorrections = emptyList(),
            selectedHealthRecordId = null,
            fullHealthScanMessage = "",
            fullHealthIssueCount = 0,
            duplicateCandidates = emptyList(),
            selectedDuplicateCandidate = null,
            duplicateFieldSelections = emptyMap(),
            duplicateMergePreview = null,
            duplicateResolutionMessage = "",
            selectedHistoryRecordId = null,
            selectedHistoryEvents = emptyList(),
            selectedHistoryEventId = null,
            historyUndoByEventId = emptyMap(),
            historyMessage = ""
        )
    }

    fun clearGlobalSearch() {
        invalidateSearchRequest()
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
        val requestId = beginSearchRequest()
        searchJob = viewModelScope.launch {
            if (!isLatestSearchRequest(requestId)) return@launch
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
                if (!isLatestSearchRequest(requestId)) return@launch
                _uiState.value = _uiState.value.copy(
                    globalSearchLoading = false,
                    globalSearchResults = rows
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!isLatestSearchRequest(requestId)) return@launch
                _uiState.value = _uiState.value.copy(
                    globalSearchLoading = false,
                    globalSearchError = e.message ?: "Error"
                )
            }
        }
    }

    private fun beginMonthRequest(): Long {
        monthLoadJob?.takeIf { it.isActive }?.cancel()
        latestMonthRequestId += 1
        return latestMonthRequestId
    }

    private fun invalidateMonthRequest() {
        monthLoadJob?.takeIf { it.isActive }?.cancel()
        latestMonthRequestId += 1
    }

    private suspend fun isLatestMonthRequest(requestId: Long): Boolean =
        requestId == latestMonthRequestId && currentCoroutineContext().isActive

    private fun beginSearchRequest(): Long {
        searchJob?.takeIf { it.isActive }?.cancel()
        latestSearchRequestId += 1
        return latestSearchRequestId
    }

    private fun invalidateSearchRequest() {
        searchJob?.takeIf { it.isActive }?.cancel()
        latestSearchRequestId += 1
    }

    private suspend fun isLatestSearchRequest(requestId: Long): Boolean =
        requestId == latestSearchRequestId && currentCoroutineContext().isActive

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
                    val userId = CurrentUserProvider.requireUserId()
                    val existingEntity = tripRepository.getTripByFirestoreDocId(docId)
                        ?: tripRepository.getTripById(docId)
                    if (existingEntity != null) {
                        val existingMap = existingEntity.toMap()
                        val changedFieldIds = fields.keys.filterTo(linkedSetOf()) { fieldId ->
                            existingMap[fieldId]?.toString() != fields[fieldId]?.toString()
                        }
                        val mergedMap = existingMap.toMutableMap()
                        mergedMap.putAll(fields)
                        recalculateDerivedFields(mergedMap)
                        mergedMap["firestoreDocId"] = existingEntity.firestoreDocId?.takeIf { it.isNotBlank() } ?: docId
                        tripRepository.saveTrip(mergedMap.toEntity(userId))
                        appendChangeHistory(
                            userId = userId,
                            recordId = existingEntity.id,
                            operation = TransitChangeOperation.MANUAL_EDIT,
                            source = TransitChangeSource.USER,
                            before = existingMap,
                            after = mergedMap
                        )
                        if (provenanceEnabled && changedFieldIds.isNotEmpty()) {
                            provenanceStore.markManualFields(
                                userId = userId,
                                localRecordId = existingEntity.id,
                                currentValues = mergedMap,
                                changedFieldIds = changedFieldIds
                            )
                            appendManualProvenanceHistory(
                                userId = userId,
                                recordId = existingEntity.id,
                                values = mergedMap,
                                changedFieldIds = changedFieldIds
                            )
                        }

                        // Update local-only profile link
                        if (profileId != null) {
                            val db = com.example.toplutasima.data.local.AppDatabase.getDatabase(getApplication())
                            val linkDao = db.tripProfileLinkDao()
                            if (profileId.isBlank()) {
                                linkDao.deleteLinksForTrip(userId, docId, existingEntity.firestoreDocId ?: "")
                            } else {
                                val tripKey = existingEntity.firestoreDocId?.takeIf { it.isNotBlank() } ?: docId
                                val existingLinks = if (existingEntity.firestoreDocId.orEmpty().isNotBlank()) {
                                    linkDao.getLinksForTrip(userId, existingEntity.firestoreDocId.orEmpty())
                                } else {
                                    linkDao.getLinksForTrip(userId, docId)
                                }
                                if (existingLinks.isNotEmpty()) {
                                    val existing = existingLinks.first()
                                    linkDao.upsert(
                                        existing.copy(
                                            profileId = profileId,
                                            seatmateNote = seatmateNote,
                                            updatedAt = System.currentTimeMillis(),
                                            userId = userId
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
                                            updatedAt = System.currentTimeMillis(),
                                            userId = userId
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
                    loadProfileData()
                    loadMonthSummaries()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    saveMsg = "❌ ${e.message}"
                )
            }
        }
    }

    private fun recalculateDerivedFields(record: MutableMap<String, Any?>) {
        val tarih = record["tarih"]?.toString().orEmpty()
        if (tarih.isNotBlank()) {
            record["gun"] = TransitRecordCalculations.computeGun(tarih)
            record["gununTipi"] = TransitRecordCalculations.computeGununTipi(tarih)
            record["sortDate"] = TransitRecordCalculations.computeSortDate(tarih)
            record["yearMonth"] = TransitRecordCalculations.computeYearMonth(tarih)
        }

        val planlananBinis = record["planlananBinis"]?.toString()
        val planlananInis = record["planlananInis"]?.toString()
        val gercekBinis = record["gercekBinis"]?.toString()
        val gercekInis = record["gercekInis"]?.toString()

        record["gecikme"] = TransitRecordCalculations.computeGecikme(planlananBinis, gercekBinis)
        record["planlananYolSuresi"] = TransitRecordCalculations.computeYolSuresi(planlananBinis, planlananInis)
        record["gercekYolSuresi"] = TransitRecordCalculations.computeYolSuresi(gercekBinis, gercekInis)

        if (record.containsKey("mesafe")) {
            val distanceKm = TransitRecordCalculations.parseDistanceKm(record["mesafe"]) ?: 0.0
            record.putAll(
                TransitRecordCalculations.calculatedDistanceFields(
                    distanceKm,
                    resetRmvDistance = true
                )
            )
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
                    if (!TransitFeatureFlags.LIVE_ROOM_FLOWS) {
                        currentMonth?.let { selectMonth(it) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    saveMsg = "❌ ${e.message}"
                )
            }
        }
    }

    fun retryDelete(localRecordId: String) {
        if (!TransitFeatureFlags.SYNC_DELETE_RECEIPTS || localRecordId.isBlank()) return
        viewModelScope.launch {
            try {
                val queued = withContext(Dispatchers.IO) { tripRepository.retryDelete(localRecordId) }
                _uiState.value = _uiState.value.copy(
                    saveMsg = if (queued) "Silme yeniden sıraya alındı" else "Silme yeniden sıraya alınamadı"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(saveMsg = "Silme hatası: ${e.message}")
            }
        }
    }

    fun keepDeleteLocalOnly(localRecordId: String) {
        if (!TransitFeatureFlags.SYNC_DELETE_RECEIPTS || localRecordId.isBlank()) return
        viewModelScope.launch {
            try {
                val kept = withContext(Dispatchers.IO) {
                    tripRepository.keepDeleteLocalOnly(localRecordId)
                }
                _uiState.value = _uiState.value.copy(
                    saveMsg = if (kept) "Kayıt yalnız bu cihazda silinmiş olarak tutuluyor" else "İşlem uygulanamadı"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(saveMsg = "Silme hatası: ${e.message}")
            }
        }
    }

    fun scanAllTransitHistory() {
        if (!postSaveHealthEnabled || _uiState.value.isHealthScanning) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isHealthScanning = true,
                fullHealthScanMessage = "Transit geçmişi denetleniyor"
            )
            try {
                val records = withContext(Dispatchers.IO) {
                    tripRepository.getAllTrips().firstOrNull().orEmpty()
                }
                val scan = withContext(Dispatchers.Default) {
                    healthUseCase.scan(records, provenanceStore, provenanceEnabled)
                }
                val duplicateCandidates = withContext(Dispatchers.Default) {
                    if (duplicateResolutionEnabled) {
                        val userId = records.firstOrNull()?.userId.orEmpty()
                        duplicateCandidateUseCase.findCandidates(
                            records = records,
                            excludedRecordIds = syncStatusStore.tombstonedRecordIds(userId),
                            decisionLookup = duplicateDecisionStore
                        )
                    } else {
                        emptyList()
                    }
                }
                _uiState.value = _uiState.value.copy(
                    isHealthScanning = false,
                    healthIssuesByRecordId = scan.issuesByRecordId,
                    healthCorrections = scan.corrections,
                    fullHealthIssueCount = scan.issues.size,
                    duplicateCandidates = duplicateCandidates,
                    fullHealthScanMessage = if (scan.issues.isEmpty()) {
                        "${scan.scannedRecordCount} kayıt denetlendi; sorun bulunmadı"
                    } else {
                        "${scan.scannedRecordCount} kayıt denetlendi; ${scan.issues.size} bulgu var"
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isHealthScanning = false,
                    fullHealthScanMessage = "Denetim tamamlanamadı: ${e.message}"
                )
            }
        }
    }

    fun showHealthIssues(localRecordId: String?) {
        if (!postSaveHealthEnabled) return
        _uiState.value = _uiState.value.copy(selectedHealthRecordId = localRecordId)
    }

    fun openSelectedHealthRecord() {
        val localRecordId = _uiState.value.selectedHealthRecordId ?: return
        val row = _uiState.value.selectedMonthTrips.asSequence()
            .flatMap { it.trips.asSequence() }
            .firstOrNull { it.localRecordId == localRecordId }
        if (row != null) {
            setEditingRecord(row.originalRecord)
            showHealthIssues(null)
            return
        }
        viewModelScope.launch {
            val entity = withContext(Dispatchers.IO) { tripRepository.getTripById(localRecordId) }
            entity?.toMap()?.let { setEditingRecord(it as Map<String, Any>) }
            showHealthIssues(null)
        }
    }

    fun applySafeHealthCorrections(localRecordId: String? = null) {
        if (!postSaveHealthEnabled || _uiState.value.isApplyingHealthCorrections) return
        val corrections = _uiState.value.healthCorrections.filter {
            it.deterministic && (localRecordId == null || it.localRecordId == localRecordId)
        }
        if (corrections.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isApplyingHealthCorrections = true)
            try {
                withContext(Dispatchers.IO) {
                    corrections.forEach { correction ->
                        val current = tripRepository.getTripById(correction.localRecordId)
                            ?: return@forEach
                        val before = current.toMap()
                        val patched = healthCorrectionUseCase.preview(before, correction)
                        tripRepository.saveTrip(patched.toEntity(current.userId))
                        appendChangeHistory(
                            userId = current.userId,
                            recordId = current.id,
                            operation = if (corrections.size > 1) {
                                TransitChangeOperation.USER_APPROVED_BULK_CORRECTION
                            } else {
                                TransitChangeOperation.AUTOMATIC_HEALTH_CORRECTION
                            },
                            source = TransitChangeSource.DATA_HEALTH,
                            before = before,
                            after = patched
                        )
                    }
                }
                _uiState.value = _uiState.value.copy(
                    isApplyingHealthCorrections = false,
                    selectedHealthRecordId = null,
                    saveMsg = "Güvenli düzeltmeler uygulandı"
                )
                scanAllTransitHistory()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isApplyingHealthCorrections = false,
                    saveMsg = "Düzeltme uygulanamadı: ${e.message}"
                )
            }
        }
    }

    fun openDuplicateResolution(candidate: TransitDuplicateCandidate) {
        if (!duplicateResolutionEnabled) return
        val defaultSelection = TransitDuplicateMergeUseCase.MERGEABLE_FIELDS
            .associateWith { TransitMergeValueSource.FIRST }
        _uiState.value = _uiState.value.copy(
            selectedDuplicateCandidate = candidate,
            duplicateFieldSelections = defaultSelection,
            duplicateMergePreview = null,
            duplicateResolutionMessage = ""
        )
        refreshDuplicatePreview()
    }

    fun dismissDuplicateResolution() {
        latestDuplicatePreviewRequestId += 1
        duplicatePreviewJob?.cancel()
        _uiState.value = _uiState.value.copy(
            selectedDuplicateCandidate = null,
            duplicateFieldSelections = emptyMap(),
            duplicateMergePreview = null,
            duplicateResolutionMessage = "",
            isResolvingDuplicate = false
        )
    }

    fun selectDuplicateField(fieldId: String, source: TransitMergeValueSource) {
        if (!duplicateResolutionEnabled || fieldId !in TransitDuplicateMergeUseCase.MERGEABLE_FIELDS) return
        _uiState.value = _uiState.value.copy(
            duplicateFieldSelections = _uiState.value.duplicateFieldSelections + (fieldId to source)
        )
        refreshDuplicatePreview()
    }

    fun keepDuplicateSeparate() = recordDuplicateDecision(TransitDuplicateDecision.KEEP_SEPARATE)

    fun reviewDuplicateLater() = recordDuplicateDecision(TransitDuplicateDecision.REVIEW_LATER)

    fun keepFirstDuplicate() = keepDuplicateRecord(keepFirst = true)

    fun keepSecondDuplicate() = keepDuplicateRecord(keepFirst = false)

    fun mergeDuplicateRecords(acknowledgedWarningIds: Set<String> = emptySet()) {
        if (!duplicateResolutionEnabled || _uiState.value.isResolvingDuplicate) return
        val candidate = _uiState.value.selectedDuplicateCandidate ?: return
        val selection = TransitDuplicateMergeSelection(_uiState.value.duplicateFieldSelections)
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isResolvingDuplicate = true, duplicateResolutionMessage = "")
            try {
                val result = withContext(Dispatchers.IO) {
                    val first = tripRepository.getTripById(candidate.firstRecordId)
                        ?: error("Birinci kayıt artık mevcut değil")
                    val second = tripRepository.getTripById(candidate.secondRecordId)
                        ?: error("İkinci kayıt artık mevcut değil")
                    check(duplicateCandidateUseCase.matchesCurrentRecords(candidate, first, second)) {
                        "Kayıtlardan biri değişti; aday yeniden değerlendirilmelidir"
                    }
                    var preview = duplicateMergeUseCase.preview(first, second, selection, provenanceStore)
                        ?: error("Birleştirme önizlemesi oluşturulamadı")
                    if (acknowledgedWarningIds.isNotEmpty()) {
                        preview = duplicateMergeUseCase.acknowledgeWarnings(preview, acknowledgedWarningIds)
                    }
                    duplicateResolutionCoordinator.execute(
                        preview = preview,
                        saveMergedRecord = { tripRepository.saveTrip(preview.mergedRecord) },
                        afterMergedRecordSaved = {
                            provenanceStore.putKnownFields(
                                userId = first.userId,
                                localRecordId = preview.targetRecordId,
                                currentValues = preview.mergedRecord.toMap(),
                                provenanceByField = preview.selectedProvenanceByField
                            )
                            appendChangeHistory(
                                userId = first.userId,
                                recordId = preview.targetRecordId,
                                operation = TransitChangeOperation.DUPLICATE_MERGE,
                                source = TransitChangeSource.DUPLICATE_RESOLUTION,
                                before = first.toMap(),
                                after = preview.mergedRecord.toMap(),
                                deduplicationKey = "merge:${candidate.decisionFingerprint}"
                            )
                        },
                        deleteSourceRecord = {
                            tripRepository.deleteTrip(preview.sourceRecordIdToDelete)
                            provenanceStore.removeRecord(first.userId, preview.sourceRecordIdToDelete)
                        }
                    ).also { execution ->
                        if (execution is TransitDuplicateResolutionResult.Applied) {
                            duplicateDecisionStore.record(
                                first.userId,
                                candidate,
                                TransitDuplicateDecision.MERGE_FIELDS
                            )
                        }
                    }
                }
                when (result) {
                    TransitDuplicateResolutionResult.Applied -> {
                        removeDuplicateCandidate(candidate)
                        dismissDuplicateResolution()
                        _uiState.value = _uiState.value.copy(
                            saveMsg = "Kayıtlar birleştirildi; silme makbuzu ayrıca izlenebilir"
                        )
                    }
                    is TransitDuplicateResolutionResult.ValidationBlocked -> {
                        _uiState.value = _uiState.value.copy(
                            isResolvingDuplicate = false,
                            duplicateMergePreview = result.preview,
                            duplicateResolutionMessage = if (result.preview.criticalIssues.isNotEmpty()) {
                                "Kritik veri tutarsızlığı nedeniyle birleştirme engellendi"
                            } else {
                                "Devam etmek için uyarıları açıkça onaylayın"
                            }
                        )
                    }
                    is TransitDuplicateResolutionResult.SaveFailed -> {
                        _uiState.value = _uiState.value.copy(
                            isResolvingDuplicate = false,
                            duplicateResolutionMessage =
                                "Birleşik kayıt yazılamadı; hiçbir kaynak silinmedi: ${result.error.message}"
                        )
                    }
                    is TransitDuplicateResolutionResult.DeleteFailed -> {
                        _uiState.value = _uiState.value.copy(
                            isResolvingDuplicate = false,
                            duplicateResolutionMessage =
                                "Birleşik kayıt güvende; kaynak silme tamamlanamadı: ${result.error.message}"
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    isResolvingDuplicate = false,
                    duplicateResolutionMessage = "Birleştirme tamamlanamadı: ${error.message}"
                )
            }
        }
    }

    private fun refreshDuplicatePreview() {
        if (!duplicateResolutionEnabled) return
        val candidate = _uiState.value.selectedDuplicateCandidate ?: return
        val selection = TransitDuplicateMergeSelection(_uiState.value.duplicateFieldSelections)
        val requestId = ++latestDuplicatePreviewRequestId
        duplicatePreviewJob?.cancel()
        duplicatePreviewJob = viewModelScope.launch {
            val preview = withContext(Dispatchers.Default) {
                val first = withContext(Dispatchers.IO) { tripRepository.getTripById(candidate.firstRecordId) }
                val second = withContext(Dispatchers.IO) { tripRepository.getTripById(candidate.secondRecordId) }
                if (
                    first == null || second == null ||
                    !duplicateCandidateUseCase.matchesCurrentRecords(candidate, first, second)
                ) null
                else duplicateMergeUseCase.preview(first, second, selection, provenanceStore)
            }
            if (
                requestId == latestDuplicatePreviewRequestId &&
                _uiState.value.selectedDuplicateCandidate?.decisionFingerprint == candidate.decisionFingerprint
            ) {
                _uiState.value = _uiState.value.copy(duplicateMergePreview = preview)
            }
        }
    }

    private fun recordDuplicateDecision(decision: TransitDuplicateDecision) {
        if (!duplicateResolutionEnabled) return
        val candidate = _uiState.value.selectedDuplicateCandidate ?: return
        val userId = runCatching { CurrentUserProvider.requireUserId() }.getOrNull() ?: return
        duplicateDecisionStore.record(userId, candidate, decision)
        if (decision == TransitDuplicateDecision.KEEP_SEPARATE) removeDuplicateCandidate(candidate)
        dismissDuplicateResolution()
    }

    private fun keepDuplicateRecord(keepFirst: Boolean) {
        if (!duplicateResolutionEnabled || _uiState.value.isResolvingDuplicate) return
        val candidate = _uiState.value.selectedDuplicateCandidate ?: return
        val keptId = if (keepFirst) candidate.firstRecordId else candidate.secondRecordId
        val deletedId = if (keepFirst) candidate.secondRecordId else candidate.firstRecordId
        val decision = if (keepFirst) TransitDuplicateDecision.KEEP_FIRST else TransitDuplicateDecision.KEEP_SECOND
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isResolvingDuplicate = true)
            try {
                val userId = CurrentUserProvider.requireUserId()
                withContext(Dispatchers.IO) {
                    val first = tripRepository.getTripById(candidate.firstRecordId)
                        ?: error("Birinci kayıt artık mevcut değil")
                    val second = tripRepository.getTripById(candidate.secondRecordId)
                        ?: error("İkinci kayıt artık mevcut değil")
                    check(duplicateCandidateUseCase.matchesCurrentRecords(candidate, first, second)) {
                        "Kayıtlardan biri değişti; silmeden önce aday yeniden değerlendirilmelidir"
                    }
                    check(first.id == keptId || second.id == keptId) { "Korunacak kayıt bulunamadı" }
                    tripRepository.deleteTrip(deletedId)
                    duplicateDecisionStore.record(userId, candidate, decision)
                    provenanceStore.removeRecord(userId, deletedId)
                }
                removeDuplicateCandidate(candidate)
                dismissDuplicateResolution()
                _uiState.value = _uiState.value.copy(saveMsg = "Kayıt korundu; silme durumu makbuzda izleniyor")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    isResolvingDuplicate = false,
                    duplicateResolutionMessage = "Çözümleme tamamlanamadı: ${error.message}"
                )
            }
        }
    }

    private fun removeDuplicateCandidate(candidate: TransitDuplicateCandidate) {
        _uiState.value = _uiState.value.copy(
            duplicateCandidates = _uiState.value.duplicateCandidates.filterNot {
                it.decisionFingerprint == candidate.decisionFingerprint
            }
        )
    }

    fun openChangeHistory(recordId: String) {
        if (!changeHistoryEnabled || recordId.isBlank()) return
        val requestId = ++latestHistoryRequestId
        historyLoadJob?.cancel()
        historyLoadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isHistoryLoading = true, historyMessage = "")
            try {
                val userId = CurrentUserProvider.requireUserId()
                val historyResult = withContext(Dispatchers.IO) {
                    changeHistoryStore.reload()
                    val events = changeHistoryStore.eventsForRecord(userId, recordId)
                    val current = tripRepository.getTripById(recordId)
                    val undo = events.associate { event ->
                        event.eventId to historyUndoUiModel(event, events, current)
                    }
                    events to undo
                }
                if (requestId != latestHistoryRequestId) return@launch
                _uiState.value = _uiState.value.copy(
                    selectedHistoryRecordId = recordId,
                    selectedHistoryEvents = historyResult.first,
                    historyUndoByEventId = historyResult.second,
                    isHistoryLoading = false
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (requestId != latestHistoryRequestId) return@launch
                _uiState.value = _uiState.value.copy(
                    isHistoryLoading = false,
                    historyMessage = "Geçmiş açılamadı: ${error.message}"
                )
            }
        }
    }

    fun dismissChangeHistory() {
        latestHistoryRequestId += 1
        historyLoadJob?.cancel()
        _uiState.value = _uiState.value.copy(
            selectedHistoryRecordId = null,
            selectedHistoryEvents = emptyList(),
            selectedHistoryEventId = null,
            historyUndoByEventId = emptyMap(),
            historyMessage = ""
        )
    }

    fun undoHistoryEvent(eventId: String, acknowledgeWarnings: Boolean = false) {
        if (!changeHistoryEnabled || eventId.isBlank()) return
        val recordId = _uiState.value.selectedHistoryRecordId ?: return
        viewModelScope.launch {
            try {
                val userId = CurrentUserProvider.requireUserId()
                val outcome = withContext(Dispatchers.IO) {
                    changeHistoryStore.reload()
                    val events = changeHistoryStore.eventsForRecord(userId, recordId)
                    val event = events.firstOrNull { it.eventId == eventId }
                        ?: error("Geçmiş olayı bulunamadı")
                    val current = tripRepository.getTripById(recordId)
                        ?: error("Kayıt artık yerelde bulunmuyor")
                    val currentMap = current.toMap()
                    val initial = historyUndoUseCase.evaluate(
                        event = event,
                        recordHistory = events,
                        currentValues = currentMap,
                        tombstoneActive = syncStatusStore.isDeletionTombstoned(userId, recordId),
                        validationInputAfterUndo = validationInput(currentMap)
                    )
                    if (!initial.canProceed) error("Bu değişiklik güvenle geri alınamaz")
                    if (initial.decision == TransitUndoDecision.REQUIRES_WARNING_CONFIRMATION && !acknowledgeWarnings) {
                        return@withContext HistoryUndoResult.WarningRequired
                    }
                    val after = currentMap.toMutableMap().apply { putAll(initial.patch) }
                    recalculateDerivedFields(after)
                    val finalEligibility = historyUndoUseCase.evaluate(
                        event = event,
                        recordHistory = events,
                        currentValues = currentMap,
                        tombstoneActive = false,
                        validationInputAfterUndo = validationInput(after)
                    )
                    if (!finalEligibility.canProceed) error("Geri alma doğrulamadan geçmedi")
                    if (
                        finalEligibility.decision == TransitUndoDecision.REQUIRES_WARNING_CONFIRMATION &&
                        !acknowledgeWarnings
                    ) {
                        return@withContext HistoryUndoResult.WarningRequired
                    }
                    tripRepository.saveTrip(after.toEntity(userId))
                    appendChangeHistory(
                        userId = userId,
                        recordId = recordId,
                        operation = TransitChangeOperation.UNDO,
                        source = TransitChangeSource.USER,
                        before = currentMap,
                        after = after,
                        deduplicationKey = "undo:$eventId"
                    )
                    HistoryUndoResult.Applied
                }
                if (outcome == HistoryUndoResult.WarningRequired) {
                    _uiState.value = _uiState.value.copy(
                        selectedHistoryEventId = eventId,
                        historyMessage = "Geri alma uyarı içeriyor; devam etmek için yeniden onaylayın"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(historyMessage = "Değişiklik geri alındı")
                    openChangeHistory(recordId)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(historyMessage = error.message.orEmpty())
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

    fun prepareTransitExport(options: TransitExportUiOptions) {
        if (!transitExportEnabled || _uiState.value.isExporting) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isExporting = true,
                showExportDialog = false,
                exportResult = "",
                preparedTransitExport = null,
                transitExportDocumentRequest = null
            )
            try {
                val userId = CurrentUserProvider.requireUserId()
                val allRecords = withContext(Dispatchers.IO) {
                    tripRepository.getAllTrips().firstOrNull().orEmpty()
                        .filter { it.userId == userId }
                }
                val filteredIds = _uiState.value.filteredTrips.asSequence()
                    .flatMap { it.trips.asSequence() }
                    .map { it.localRecordId }
                    .toSet()
                val scopedRecords = withContext(Dispatchers.Default) {
                    scopeTransitRecords(allRecords, options, filteredIds)
                }
                val scan = if (
                    TransitExportSection.DATA_HEALTH in options.sections && postSaveHealthEnabled
                ) {
                    withContext(Dispatchers.Default) {
                        healthUseCase.scan(scopedRecords, provenanceStore, provenanceEnabled)
                    }
                } else {
                    null
                }
                val scopedAnalyticsRows = withContext(Dispatchers.Default) {
                    scopedRecords.map { it.toTransitAnalyticsMap() }
                }
                val summaryResult = if (
                    TransitExportSection.SUMMARY in options.sections ||
                    TransitExportSection.INSIGHTS in options.sections
                ) {
                    summaryEngine.calculate(
                        TransitSummaryRequest(
                            selectedRecords = scopedAnalyticsRows,
                            boundedTrendRecords = scopedAnalyticsRows
                        )
                    )
                } else {
                    null
                }
                val insightResult = if (
                    TransitExportSection.INSIGHTS in options.sections &&
                    TransitFeatureFlags.TRANSIT_INSIGHTS
                ) {
                    val selectedMonth = options.selectedYearMonthOrNull()
                    val previousRecords = selectedMonth?.minusMonths(1)?.let { previous ->
                        allRecords.filter { it.belongsTo(previous) }
                    }.orEmpty()
                    val previousAnalyticsRows = withContext(Dispatchers.Default) {
                        previousRecords.map { it.toTransitAnalyticsMap() }
                    }
                    insightsEngine.calculate(
                        TransitInsightsRequest(
                            selectedPeriodLabel = exportPeriodLabel(options),
                            selectedRecords = scopedAnalyticsRows,
                            previousPeriodLabel = selectedMonth?.minusMonths(1)?.toString(),
                            previousRecords = previousAnalyticsRows,
                            selectedSummary = summaryResult,
                            tombstonedRecordIds = syncStatusStore.tombstonedRecordIds(userId),
                            provenanceByRecordId = emptyMap()
                        )
                    )
                } else {
                    null
                }
                val tombstones = syncStatusStore.tombstonedRecordIds(userId)
                val exportRecords = withContext(Dispatchers.Default) {
                    allRecords.map { entity ->
                        val provenance = provenanceResolver.resolveFields(
                            userId = userId,
                            localRecordId = entity.id,
                            record = entity.toMap(),
                            store = null,
                            enabled = true
                        )
                        TransitExportRecordMapper.fromEntity(
                            entity = entity,
                            syncPhase = syncStatusStore.stateForRecord(userId, entity.id)?.phase,
                            healthSeverity = scan?.highestSeverityByRecordId?.get(entity.id),
                            provenance = TransitExportRecordMapper.provenanceInputs(
                                provenance,
                                TransitExportProvenanceEvidence.RECORD_DERIVED
                            ),
                            matchesFilter = entity.id in filteredIds,
                            isTombstoned = entity.id in tombstones
                        )
                    }
                }
                val request = TransitExportRequest(
                    format = options.format,
                    scope = exportScope(options),
                    records = exportRecords,
                    tombstonedRecordIds = tombstones,
                    sections = options.sections.filterTo(linkedSetOf()) {
                        it != TransitExportSection.INSIGHTS || TransitFeatureFlags.TRANSIT_INSIGHTS
                    },
                    summary = summaryResult?.summary?.let(::summaryMetrics).orEmpty(),
                    insights = insightResult?.insights?.map { insight ->
                        TransitExportInsight(
                            id = insight.semanticKey,
                            title = insight.type.name,
                            result = insight.value,
                            period = insight.periodLabel,
                            confidence = insight.confidence.level.name,
                            explanation = insight.explanation.calculation,
                            recordCount = insight.explanation.recordCount
                        )
                    }.orEmpty(),
                    healthSummary = scan?.let(::healthExportSummary)
                )
                when (val result = transitExportUseCase.prepare(request)) {
                    TransitExportPreparationResult.Disabled -> {
                        _uiState.value = _uiState.value.copy(
                            isExporting = false,
                            exportResult = "Transit dışa aktarma bu derlemede kapalı"
                        )
                    }
                    is TransitExportPreparationResult.Ready -> {
                        _uiState.value = _uiState.value.copy(
                            isExporting = false,
                            preparedTransitExport = result.document,
                            transitExportDocumentRequest =
                                transitSafExportCoordinator.createDocumentRequest(result.document)
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    preparedTransitExport = null,
                    transitExportDocumentRequest = null,
                    exportResult = "Dışa aktarma iptal edildi"
                )
                throw cancelled
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    preparedTransitExport = null,
                    transitExportDocumentRequest = null,
                    exportResult = "Dışa aktarma hazırlanamadı: ${error.message}"
                )
            }
        }
    }

    fun onTransitExportPickerLaunched() {
        _uiState.value = _uiState.value.copy(transitExportDocumentRequest = null)
    }

    fun completeTransitExport(uri: Uri?) {
        if (!transitExportEnabled) return
        val document = _uiState.value.preparedTransitExport ?: return
        if (uri == null) {
            _uiState.value = _uiState.value.copy(
                preparedTransitExport = null,
                transitExportDocumentRequest = null,
                exportResult = "Dışa aktarma iptal edildi"
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true, exportResult = "")
            try {
                when (val result = transitSafExportCoordinator.write(uri, document)) {
                    is TransitExportWriteResult.Success -> _uiState.value = _uiState.value.copy(
                        isExporting = false,
                        preparedTransitExport = null,
                        exportResult = "${result.bytesWritten} bayt güvenle dışa aktarıldı"
                    )
                    is TransitExportWriteResult.Failure -> _uiState.value = _uiState.value.copy(
                        isExporting = false,
                        preparedTransitExport = null,
                        exportResult = buildString {
                            append("Dışa aktarma yazılamadı: ${result.message}")
                            if (!result.incompleteDestinationRemoved) {
                                append("; belge sağlayıcısı eksik hedefi silemedi")
                            }
                        }
                    )
                }
            } catch (cancelled: CancellationException) {
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    preparedTransitExport = null,
                    exportResult = "Dışa aktarma iptal edildi"
                )
                throw cancelled
            }
        }
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

    private fun appendChangeHistory(
        userId: String,
        recordId: String,
        operation: TransitChangeOperation,
        source: TransitChangeSource,
        before: Map<String, Any?>?,
        after: Map<String, Any?>,
        deduplicationKey: String? = null
    ) {
        if (!changeHistoryEnabled) return
        changeHistoryStore.append(
            TransitChangeEventDraft(
                userId = userId,
                recordId = recordId,
                operation = operation,
                occurredAtEpochMillis = System.currentTimeMillis(),
                source = source,
                changes = recordDiffUseCase.diff(before, after),
                syncStatus = currentHistorySyncStatus(userId, recordId),
                deduplicationKey = deduplicationKey
            )
        )
    }

    private fun currentHistorySyncStatus(
        userId: String,
        recordId: String
    ): TransitHistorySyncStatus = when (
        syncStatusStore.stateForRecord(userId, recordId)?.phase
    ) {
        TransitSyncPhase.LOCAL_SAVING,
        TransitSyncPhase.LOCAL_SAFE,
        TransitSyncPhase.LOCAL_DELETED -> TransitHistorySyncStatus.LOCAL_ONLY

        TransitSyncPhase.PENDING,
        TransitSyncPhase.DELETE_PENDING -> TransitHistorySyncStatus.PENDING

        TransitSyncPhase.SYNCING,
        TransitSyncPhase.DELETING -> TransitHistorySyncStatus.SYNCING

        TransitSyncPhase.SYNCED,
        TransitSyncPhase.DELETED -> TransitHistorySyncStatus.SYNCED

        TransitSyncPhase.TEMPORARY_ERROR,
        TransitSyncPhase.DELETE_TEMPORARY_ERROR -> TransitHistorySyncStatus.TEMPORARY_ERROR

        TransitSyncPhase.PERMANENT_ERROR,
        TransitSyncPhase.DELETE_PERMANENT_ERROR -> TransitHistorySyncStatus.PERMANENT_ERROR

        null -> TransitHistorySyncStatus.NOT_APPLICABLE
    }

    private fun appendManualProvenanceHistory(
        userId: String,
        recordId: String,
        values: Map<String, Any?>,
        changedFieldIds: Set<String>
    ) {
        if (!changeHistoryEnabled || changedFieldIds.isEmpty()) return
        val manualEvidence = TransitHistoryProvenanceEvidence(
            source = TransitHistoryProvenanceSource.MANUAL,
            durability = TransitHistoryEvidenceDurability.EXPLICIT_USER_ACTION
        )
        val changes = changedFieldIds.asSequence()
            .filter { it in TransitRecordDiffUseCase.TRACKED_FIELDS }
            .sorted()
            .map { fieldId ->
                val value = TransitHistoryValue.fromKnownField(values[fieldId])
                TransitFieldChange(
                    fieldId = fieldId,
                    oldValue = value,
                    newValue = value,
                    newProvenance = manualEvidence
                )
            }
            .toList()
        changeHistoryStore.append(
            TransitChangeEventDraft(
                userId = userId,
                recordId = recordId,
                operation = TransitChangeOperation.PROVENANCE_CHANGE,
                occurredAtEpochMillis = System.currentTimeMillis(),
                source = TransitChangeSource.USER,
                changes = changes,
                syncStatus = TransitHistorySyncStatus.NOT_APPLICABLE
            )
        )
    }

    private fun historyUndoUiModel(
        event: com.example.toplutasima.transit.history.TransitChangeEvent,
        events: List<com.example.toplutasima.transit.history.TransitChangeEvent>,
        current: TripEntity?
    ): TransitHistoryUndoUiModel {
        if (current == null) {
            return TransitHistoryUndoUiModel(false, disabledReason = "Kayıt yerelde bulunmuyor")
        }
        val currentMap = current.toMap()
        val initial = historyUndoUseCase.evaluate(
            event = event,
            recordHistory = events,
            currentValues = currentMap,
            tombstoneActive = syncStatusStore.isDeletionTombstoned(current.userId, current.id),
            validationInputAfterUndo = validationInput(currentMap)
        )
        if (!initial.canProceed) {
            return TransitHistoryUndoUiModel(
                enabled = false,
                disabledReason = initial.blockReason?.name ?: "Güvenli geri alma mümkün değil"
            )
        }
        val after = currentMap.toMutableMap().apply { putAll(initial.patch) }
        recalculateDerivedFields(after)
        val finalResult = historyUndoUseCase.evaluate(
            event = event,
            recordHistory = events,
            currentValues = currentMap,
            tombstoneActive = false,
            validationInputAfterUndo = validationInput(after)
        )
        return TransitHistoryUndoUiModel(
            enabled = finalResult.canProceed,
            requiresWarningConfirmation =
                finalResult.decision == TransitUndoDecision.REQUIRES_WARNING_CONFIRMATION,
            disabledReason = finalResult.blockReason?.name
        )
    }

    private fun validationInput(values: Map<String, Any?>): TransitRecordValidationInput =
        TransitRecordValidationInput(
            segments = listOf(
                TransitRecordSegmentInput(
                    boardingStop = values["binisDuragi"]?.toString().orEmpty(),
                    alightingStop = values["inisDuragi"]?.toString().orEmpty(),
                    plannedDeparture = values["planlananBinis"]?.toString().orEmpty(),
                    plannedArrival = values["planlananInis"]?.toString().orEmpty(),
                    actualDeparture = values["gercekBinis"]?.toString().orEmpty(),
                    actualArrival = values["gercekInis"]?.toString().orEmpty(),
                    boardingStopId = values[TransitRecordCalculations.FIELD_FROM_STOP_ID]?.toString().orEmpty(),
                    alightingStopId = values[TransitRecordCalculations.FIELD_TO_STOP_ID]?.toString().orEmpty(),
                    distanceKm = values["mesafe"],
                    rmvDistanceKm = values[TransitRecordCalculations.FIELD_RMV_DISTANCE_KM],
                    orsDistanceKm = values[TransitRecordCalculations.FIELD_ORS_DISTANCE_KM],
                    storedPlannedDurationMinutes = values["planlananYolSuresi"]?.toString()?.toIntOrNull(),
                    storedActualDurationMinutes = values["gercekYolSuresi"]?.toString()?.toIntOrNull()
                )
            ),
            actualTimesRequired = false
        )

    private fun scopeTransitRecords(
        records: List<TripEntity>,
        options: TransitExportUiOptions,
        filteredIds: Set<String>
    ): List<TripEntity> = when (options.scopeType) {
        TransitExportScopeType.SELECTED_MONTH -> {
            val month = options.selectedYearMonthOrNull()
            if (month == null) emptyList() else records.filter { it.belongsTo(month) }
        }
        TransitExportScopeType.DATE_RANGE -> {
            val start = options.startDateIso?.let { LocalDate.parse(it) }
            val end = options.endDateIso?.let { LocalDate.parse(it) }
            records.filter { record ->
                val date = record.isoDateOrNull()
                date != null && (start == null || !date.isBefore(start)) &&
                    (end == null || !date.isAfter(end))
            }
        }
        TransitExportScopeType.ALL_TRANSIT -> records
        TransitExportScopeType.FILTERED -> records.filter { it.id in filteredIds }
    }

    private fun TripEntity.belongsTo(month: YearMonth): Boolean =
        yearMonth == month.toString() || isoDateOrNull()?.let(YearMonth::from) == month

    private fun TripEntity.toTransitAnalyticsMap(): Map<String, Any> =
        toMap().mapNotNull { (key, value) -> value?.let { key to it } }.toMap()

    private fun exportScope(options: TransitExportUiOptions): TransitExportScope =
        when (options.scopeType) {
            TransitExportScopeType.SELECTED_MONTH -> TransitExportScope.selectedMonth(
                requireNotNull(options.selectedYearMonthOrNull()).toString()
            )
            TransitExportScopeType.DATE_RANGE -> TransitExportScope.dateRange(
                requireNotNull(options.startDateIso),
                requireNotNull(options.endDateIso)
            )
            TransitExportScopeType.ALL_TRANSIT -> TransitExportScope.allTransit()
            TransitExportScopeType.FILTERED -> TransitExportScope.filtered(
                filterDescription = _uiState.value.filterState.toString(),
                sortDescription = "date_ascending"
            )
        }

    private fun TransitExportUiOptions.selectedYearMonthOrNull(): YearMonth? {
        if (scopeType != TransitExportScopeType.SELECTED_MONTH) return null
        val sortKey = _uiState.value.selectedMonth?.sortKey ?: return null
        if (sortKey.length < 6) return null
        return runCatching {
            YearMonth.of(sortKey.substring(0, 4).toInt(), sortKey.substring(4, 6).toInt())
        }.getOrNull()
    }

    private fun exportPeriodLabel(options: TransitExportUiOptions): String = when (options.scopeType) {
        TransitExportScopeType.SELECTED_MONTH -> options.selectedYearMonthOrNull()?.toString().orEmpty()
        TransitExportScopeType.DATE_RANGE -> "${options.startDateIso}/${options.endDateIso}"
        TransitExportScopeType.ALL_TRANSIT -> "Tüm transit kayıtları"
        TransitExportScopeType.FILTERED -> "Filtrelenmiş transit kayıtları"
    }

    private fun TripEntity.isoDateOrNull(): LocalDate? = runCatching {
        sortDate?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) }
            ?: tarih?.split('.')?.takeIf { it.size == 3 }?.let {
                LocalDate.of(it[2].toInt(), it[1].toInt(), it[0].toInt())
            }
    }.getOrNull()

    private fun summaryMetrics(summary: com.example.toplutasima.model.SummaryData): List<TransitExportMetric> =
        listOf(
            TransitExportMetric("total_trips", "Toplam yolculuk", summary.totalTrips.toString()),
            TransitExportMetric("total_distance_km", "Toplam mesafe", summary.totalDistanceKm.toString(), "km"),
            TransitExportMetric("average_delay_minutes", "Ortalama gecikme", summary.avgDelay.toString(), "minute"),
            TransitExportMetric("most_used_line", "En sık kullanılan hat", summary.freqLine),
            TransitExportMetric("most_used_origin", "En sık başlangıç durağı", summary.freqFrom),
            TransitExportMetric("most_used_destination", "En sık varış durağı", summary.freqTo),
            TransitExportMetric("planned_duration_minutes", "Planlanan toplam süre", summary.totalPlannedMin.toString(), "minute"),
            TransitExportMetric("actual_duration_minutes", "Gerçek toplam süre", summary.totalActualMin.toString(), "minute")
        )

    private fun healthExportSummary(scan: TransitHealthScanResult): TransitExportHealthSummary {
        val affectedRecords = scan.issues.mapTo(mutableSetOf()) { it.localRecordId }.size
        return TransitExportHealthSummary(
            scannedRecordCount = scan.scannedRecordCount,
            healthyRecordCount = (scan.scannedRecordCount - affectedRecords).coerceAtLeast(0),
            informationalIssueCount = scan.issues.count { it.severity == TransitHealthSeverity.INFO },
            warningIssueCount = scan.issues.count { it.severity == TransitHealthSeverity.WARNING },
            criticalIssueCount = scan.issues.count { it.severity == TransitHealthSeverity.CRITICAL }
        )
    }
}

private enum class HistoryUndoResult { Applied, WarningRequired }

private data class MonthRecordMetadata(
    val scan: TransitHealthScanResult?,
    val provenanceByRecordId: Map<String, Map<String, com.example.toplutasima.transit.provenance.TransitFieldProvenance>>,
    val duplicateCandidates: List<TransitDuplicateCandidate>
)
