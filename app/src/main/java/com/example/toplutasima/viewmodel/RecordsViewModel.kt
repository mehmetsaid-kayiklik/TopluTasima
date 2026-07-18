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
import com.example.toplutasima.model.MonthSummary
import com.example.toplutasima.domain.transit.health.TransitHealthCorrection
import com.example.toplutasima.domain.transit.health.TransitHealthIssue
import com.example.toplutasima.domain.transit.health.TransitHealthScanResult
import com.example.toplutasima.domain.transit.health.TransitHealthSeverity
import com.example.toplutasima.transit.TransitFeatureFlags
import com.example.toplutasima.transit.provenance.TransitRecordProvenanceResolver
import com.example.toplutasima.transit.provenance.TransitRecordProvenanceStore
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
    private val provenanceEnabled: Boolean = TransitFeatureFlags.PROVENANCE_BADGES
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
        autoLoad = true
    )

    constructor(
        application: Application,
        profileSyncRepository: ProfileSyncRepository,
        healthUseCase: TransitPostSaveHealthUseCase,
        healthCorrectionUseCase: TransitHealthCorrectionUseCase,
        provenanceStore: TransitRecordProvenanceStore,
        provenanceResolver: TransitRecordProvenanceResolver
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
        healthUseCase = healthUseCase,
        healthCorrectionUseCase = healthCorrectionUseCase,
        provenanceStore = provenanceStore,
        provenanceResolver = provenanceResolver
    )

    private val _uiState = MutableStateFlow(RecordsUiState())
    val uiState: StateFlow<RecordsUiState> = _uiState.asStateFlow()

    private var monthLoadJob: Job? = null
    private var latestMonthRequestId = 0L
    private var searchJob: Job? = null
    private var latestSearchRequestId = 0L

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
                        MonthRecordMetadata(scan = scan, provenanceByRecordId = provenance)
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
            fullHealthIssueCount = 0
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
                        if (provenanceEnabled && changedFieldIds.isNotEmpty()) {
                            provenanceStore.markManualFields(
                                userId = userId,
                                localRecordId = existingEntity.id,
                                currentValues = mergedMap,
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
                _uiState.value = _uiState.value.copy(
                    isHealthScanning = false,
                    healthIssuesByRecordId = scan.issuesByRecordId,
                    healthCorrections = scan.corrections,
                    fullHealthIssueCount = scan.issues.size,
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
                        val patched = healthCorrectionUseCase.preview(current.toMap(), correction)
                        tripRepository.saveTrip(patched.toEntity(current.userId))
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
}

private data class MonthRecordMetadata(
    val scan: TransitHealthScanResult?,
    val provenanceByRecordId: Map<String, Map<String, com.example.toplutasima.transit.provenance.TransitFieldProvenance>>
)
