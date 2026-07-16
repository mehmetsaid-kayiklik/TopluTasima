package com.example.toplutasima.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.toplutasima.auth.CurrentUserProvider
import com.example.toplutasima.data.AppEventBus
import com.example.toplutasima.data.PrefsManager
import com.example.toplutasima.data.repository.ProfileSyncRepository
import com.example.toplutasima.model.Departure
import com.example.toplutasima.model.JourneyMatchCandidate
import com.example.toplutasima.model.SeatingStatus
import com.example.toplutasima.model.Segment
import com.example.toplutasima.model.StopOption
import com.example.toplutasima.model.TicketStatus
import com.example.toplutasima.model.TripResult
import com.example.toplutasima.model.VehicleType
import com.example.toplutasima.network.RmvApiService
import com.example.toplutasima.repository.RmvTripRepository
import com.example.toplutasima.repository.TransitRecordRepository
import com.example.toplutasima.repository.TripProfileLinkRepository
import com.example.toplutasima.service.JourneyMatchForegroundService
import com.example.toplutasima.service.TransitTripForegroundService
import com.example.toplutasima.service.transit.TransitServiceStateStore
import com.example.toplutasima.ui.LocaleManager
import com.example.toplutasima.ui.S
import com.example.toplutasima.usecase.TransitRecordCalculations
import com.example.toplutasima.usecase.TransitTimeUtils
import com.example.toplutasima.usecase.TripPlanningUseCase
import com.example.toplutasima.usecase.JourneyMatchUseCase
import com.example.toplutasima.usecase.ManualEntryUseCase
import com.example.toplutasima.usecase.RecordSaveUseCase
import com.example.toplutasima.usecase.StopSelectionUseCase
import com.example.toplutasima.viewmodel.rmvlog.LogMode
import com.example.toplutasima.viewmodel.rmvlog.ManualEntryState
import com.example.toplutasima.viewmodel.rmvlog.RmvLogUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

class RmvLogViewModel(
    application: Application,
    private val stopSelectionUseCase: StopSelectionUseCase,
    private val journeyMatchUseCase: JourneyMatchUseCase,
    private val recordSaveUseCase: RecordSaveUseCase,
    private val manualEntryUseCase: ManualEntryUseCase,
    private val rmvTripRepository: RmvTripRepository,
    private val transitRecordRepository: TransitRecordRepository,
    private val tripProfileLinkRepository: TripProfileLinkRepository,
    private val tripPlanner: TripPlanningUseCase,
    private val nearbyManager: com.example.toplutasima.location.NearbyStopsManager,
    private val profileSyncRepository: ProfileSyncRepository
) : AndroidViewModel(application) {
    private companion object {
        const val DEPARTURE_REFRESH_INTERVAL_MS = 30_000L
    }

    private val prefs = application.getSharedPreferences("rmv_prefs", Context.MODE_PRIVATE)
    private val transitServiceStateStore = TransitServiceStateStore(application)

    private var actualTimesRefreshJob: Job? = null
    private var departureRefreshJob: Job? = null
    private var serviceStateRefreshJob: Job? = null

    private val _uiState = MutableStateFlow(
        RmvLogUiState(
            date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
            time = LocalTime.now().withSecond(0).withNano(0).format(DateTimeFormatter.ofPattern("HHmm")),
            firstSavedId = prefs.getString("first_id", "") ?: "",
            lastSavedId = prefs.getString("last_id", "") ?: "",
            status = S.statusReady(LocaleManager.currentLanguage)
        )
    )
    val uiState: StateFlow<RmvLogUiState> = _uiState.asStateFlow()

    private fun lang() = LocaleManager.currentLanguage
    private fun ctx(): Context = getApplication()

    private fun applySegmentDetails(seg: Segment, details: RmvApiService.SegmentDetails): Segment {
        val stopNames = details.stopNames.ifEmpty { seg.stopNames }
        val stopTimes = details.stopTimes.ifEmpty { seg.stopTimes }
        val hasResolvedRange = details.fromIdx >= 0 && details.toIdx >= 0
        return seg.copy(
            distanceKm = if (details.distanceKm > 0) details.distanceKm else seg.distanceKm,
            polyDistanceKm = details.distanceResult.polyDistanceKm ?: seg.polyDistanceKm,
            stopCount = if (details.stopCount > 0) details.stopCount else seg.stopCount,
            stopNames = stopNames,
            stopTimes = stopTimes,
            stopFromIdx = if (hasResolvedRange) details.fromIdx else seg.stopFromIdx,
            stopToIdx = if (hasResolvedRange) details.toIdx else seg.stopToIdx,
            toStopLat = if (!details.toStopLat.isNaN()) details.toStopLat else seg.toStopLat,
            toStopLng = if (!details.toStopLng.isNaN()) details.toStopLng else seg.toStopLng
        )
    }

    private fun Departure.isSameDeparture(other: Departure): Boolean {
        if (journeyDetailRef.isNotBlank() && other.journeyDetailRef.isNotBlank()) {
            return journeyDetailRef == other.journeyDetailRef
        }
        return RmvApiService.normalizeLineForDisplay(line) == RmvApiService.normalizeLineForDisplay(other.line) &&
            direction.trim().equals(other.direction.trim(), ignoreCase = true) &&
            time.take(5) == other.time.take(5)
    }

    private fun Departure.withLiveFieldsFrom(fresh: Departure): Departure = copy(
        realtime = fresh.realtime,
        realtimeDate = fresh.realtimeDate,
        track = fresh.track,
        cancelled = fresh.cancelled
    )

    private fun stopDepartureRefresh() {
        departureRefreshJob?.cancel()
        departureRefreshJob = null
    }

    private fun startDepartureRefresh(fromId: String, toId: String, uiDate: String, apiDate: String, searchTime: String) {
        stopDepartureRefresh()
        departureRefreshJob = viewModelScope.launch {
            while (isActive) {
                delay(DEPARTURE_REFRESH_INTERVAL_MS)
                val current = _uiState.value
                if (current.fromId != fromId || current.toId != toId || current.date != uiDate) break
                try {
                    val freshDepartures = rmvTripRepository.fetchDepartures(fromId, toId, apiDate, searchTime)
                    val latest = _uiState.value
                    if (latest.fromId != fromId || latest.toId != toId || latest.date != uiDate) break
                    val mergedDepartures = freshDepartures.map { fresh ->
                        latest.departures.firstOrNull { it.isSameDeparture(fresh) }
                            ?.withLiveFieldsFrom(fresh)
                            ?: fresh
                    }
                    val selected = latest.selectedDeparture?.let { selectedDeparture ->
                        freshDepartures.firstOrNull { selectedDeparture.isSameDeparture(it) }
                            ?.let { selectedDeparture.withLiveFieldsFrom(it) }
                            ?: selectedDeparture
                    }
                    _uiState.value = latest.copy(
                        departures = mergedDepartures,
                        selectedDeparture = selected
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Realtime yenileme arka planda sessizce denenir; ana akışı bozmasın.
                }
            }
        }
    }

    init {
        refreshTransitServiceState()
        // AppEventBus — bildirimden yapılan Bindim/İndim işlemlerini UI'a yansıt
        viewModelScope.launch {
            AppEventBus.events.collect { event ->
                when (event) {
                    is AppEventBus.Event.TripSynced -> handleTripSyncedFromNotif(event)
                    is AppEventBus.Event.JourneyMatchCompleted -> {
                        _uiState.value = _uiState.value.copy(
                            journeyMatchLoading = false,
                            journeyMatchCandidates = event.candidates,
                            journeyMatchMessage = event.message,
                            status = event.message
                        )
                    }
                }
            }
        }
        loadActiveProfiles()
    }

    fun hasLocationPermission(): Boolean = nearbyManager.hasLocationPermission()

    // ── Yakındaki duraklar ────────────────────────────────────────────────────

    fun fetchNearbyStopsOnOpen() {
        val state = _uiState.value
        if (state.nearbyLoading || state.nearbyHasLoaded || !hasLocationPermission()) return
        fetchNearbyStops()
    }

    fun fetchNearbyStops() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(nearbyLoading = true)
            try {
                val stops = nearbyManager.fetchNearbyStops()
                _uiState.value = _uiState.value.copy(
                    nearbyStops = stops,
                    nearbyLoading = false,
                    nearbyHasLoaded = true
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    nearbyStops = emptyList(),
                    nearbyLoading = false,
                    nearbyHasLoaded = true,
                    status = "${S.errorPrefix(lang())}: ${e.message}"
                )
            }
        }
    }


    fun selectNearbyStop(stop: com.example.toplutasima.network.RmvApiService.NearbyStop) {
        stopDepartureRefresh()
        _uiState.value = stopSelectionUseCase.selectNearbyStop(_uiState.value, stop)
    }

    // ── Favoriye ekleme dialog ────────────────────────────────────────────────


    fun showAddFavoriteDialog(stopId: String, stopName: String) {
        _uiState.value = stopSelectionUseCase.showAddFavoriteDialog(_uiState.value, stopId, stopName)
    }


    fun dismissAddFavoriteDialog() {
        _uiState.value = stopSelectionUseCase.dismissAddFavoriteDialog(_uiState.value)
    }


    fun updateAddFavLabel(label: String) {
        _uiState.value = stopSelectionUseCase.updateAddFavLabel(_uiState.value, label)
    }


    fun updateAddFavUsageType(type: com.example.toplutasima.model.UsageType) {
        _uiState.value = stopSelectionUseCase.updateAddFavUsageType(_uiState.value, type)
    }


    fun confirmAddFavorite() {
        _uiState.value = stopSelectionUseCase.addFavorite(_uiState.value, lang())
    }


    fun selectFavoriteFrom(stopId: String, stopName: String) {
        stopDepartureRefresh()
        _uiState.value = stopSelectionUseCase.selectFavoriteFrom(_uiState.value, stopId, stopName)
    }


    fun selectFavoriteTo(stopId: String, stopName: String) {
        stopDepartureRefresh()
        _uiState.value = stopSelectionUseCase.selectFavoriteTo(_uiState.value, stopId, stopName)
    }

    fun removeFavorite(id: String) {
        stopSelectionUseCase.removeFavorite(id)
    }

    fun loadFavorites() = stopSelectionUseCase.loadFavorites()

    // ── Durak arama ──────────────────────────────────────────────────────────


    fun updateFrom(value: String) {
        stopDepartureRefresh()
        _uiState.value = stopSelectionUseCase.updateFrom(_uiState.value, value)
    }


    fun updateTo(value: String) {
        stopDepartureRefresh()
        _uiState.value = stopSelectionUseCase.updateTo(_uiState.value, value)
    }


    fun selectFrom(option: StopOption) {
        stopDepartureRefresh()
        _uiState.value = stopSelectionUseCase.selectFrom(_uiState.value, option)
    }


    fun selectTo(option: StopOption) {
        stopDepartureRefresh()
        _uiState.value = stopSelectionUseCase.selectTo(_uiState.value, option)
    }

    fun setFromMenuOpen(open: Boolean) {
        _uiState.value = _uiState.value.copy(fromMenuOpen = open)
    }

    fun setToMenuOpen(open: Boolean) {
        _uiState.value = _uiState.value.copy(toMenuOpen = open)
    }

    fun setHavaMenuOpen(open: Boolean) {
        _uiState.value = _uiState.value.copy(havaMenuOpen = open)
    }


    fun swapFromTo() {
        stopDepartureRefresh()
        _uiState.value = stopSelectionUseCase.swapFromTo(_uiState.value)
    }

    fun updateDate(value: String) {
        stopDepartureRefresh()
        _uiState.value = _uiState.value.copy(date = value)
    }

    fun updateTime(value: String) {
        stopDepartureRefresh()
        _uiState.value = _uiState.value.copy(time = value.filter { it.isDigit() }.take(4))
    }

    fun clearFrom() {
        stopDepartureRefresh()
        _uiState.value = _uiState.value.copy(from = "", fromId = "", fromOptions = emptyList())
    }

    fun clearTo() {
        stopDepartureRefresh()
        _uiState.value = _uiState.value.copy(to = "", toId = "", toOptions = emptyList())
    }

    fun clearTime() {
        stopDepartureRefresh()
        _uiState.value = _uiState.value.copy(time = "")
    }

    fun clearCustomBindimTime() {
        _uiState.value = _uiState.value.copy(customBindimTime = "")
    }

    fun clearCustomIndimTime() {
        _uiState.value = _uiState.value.copy(customIndimTime = "")
    }

    // ── Segment ek bilgileri ─────────────────────────────────────────────────

    fun updateHavaDurumu(value: String) {
        val s = _uiState.value
        val idx = s.selectedSegmentIndex
        _uiState.value = s.copy(
            segmentHavaDurumu = s.segmentHavaDurumu + (idx to value),
            havaMenuOpen = false
        )
        val segId = _uiState.value.segmentIds.getOrNull(idx)
        if (segId != null) {
            viewModelScope.launch {
                transitRecordRepository.updateExistingRecord(segId, mapOf("havaDurumu" to value))
            }
        }
    }

    fun updateOturabildim(value: Boolean) {
        val s = _uiState.value
        val idx = s.selectedSegmentIndex
        _uiState.value = s.copy(segmentOturabildim = s.segmentOturabildim + (idx to value))
        val segId = _uiState.value.segmentIds.getOrNull(idx)
        if (segId != null) {
            viewModelScope.launch {
                transitRecordRepository.updateExistingRecord(segId, mapOf("oturabildimMi" to SeatingStatus.fromBoolean(value).key))
            }
        }
    }

    fun updateBiletKontrolu(value: Boolean) {
        val s = _uiState.value
        val idx = s.selectedSegmentIndex
        _uiState.value = s.copy(segmentBiletKontrolu = s.segmentBiletKontrolu + (idx to value))
        val segId = _uiState.value.segmentIds.getOrNull(idx)
        if (segId != null) {
            viewModelScope.launch {
                transitRecordRepository.updateExistingRecord(segId, mapOf("biletKontrolü" to TicketStatus.fromBoolean(value).key))
            }
        }
    }

    fun updateNote(value: String) {
        val s = _uiState.value
        val idx = s.selectedSegmentIndex
        _uiState.value = s.copy(segmentNote = s.segmentNote + (idx to value))
    }

    fun updateCustomBindimTime(value: String) {
        _uiState.value = _uiState.value.copy(customBindimTime = value.filter { it.isDigit() }.take(4))
    }

    fun updateCustomIndimTime(value: String) {
        _uiState.value = _uiState.value.copy(customIndimTime = value.filter { it.isDigit() }.take(4))
    }

    fun setEditingTimes(editing: Boolean) {
        _uiState.value = _uiState.value.copy(isEditingTimes = editing)
    }

    fun updateSegmentDep(index: Int, newDep: String) {
        val s = _uiState.value
        val t = s.trip ?: return
        val newSegs = t.segments.toMutableList()
        newSegs[index] = newSegs[index].copy(dep = newDep)
        val newOverallDep = newSegs.first().dep
        val newOverallArr = newSegs.last().arr
        _uiState.value = s.copy(trip = t.copy(
            segments = newSegs,
            overallDep = newOverallDep,
            durationMin = TransitTimeUtils.computeDuration(newOverallDep, newOverallArr)
        ))
    }

    fun updateSegmentArr(index: Int, newArr: String) {
        val s = _uiState.value
        val t = s.trip ?: return
        val newSegs = t.segments.toMutableList()
        newSegs[index] = newSegs[index].copy(arr = newArr)
        val newOverallDep = newSegs.first().dep
        val newOverallArr = newSegs.last().arr
        _uiState.value = s.copy(trip = t.copy(
            segments = newSegs,
            overallArr = newOverallArr,
            durationMin = TransitTimeUtils.computeDuration(newOverallDep, newOverallArr)
        ))
    }

    // ── Form sıfırlama ───────────────────────────────────────────────────────

    fun clearForm() {
        stopDepartureRefresh()
        _uiState.value = _uiState.value.copy(
            date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
            from = "", fromId = "", fromOptions = emptyList(),
            to = "", toId = "", toOptions = emptyList(),
            departures = emptyList(),
            selectedDeparture = null,
            trip = null,
            customBindimTime = "",
            customIndimTime = "",
            firstSavedId = "",
            lastSavedId = "",
            segmentIds = emptyList(),
            selectedSegmentIndex = 0,
            persistentStops = emptyList(),
            isEditingTimes = false,
            time = LocalTime.now().withSecond(0).withNano(0).format(DateTimeFormatter.ofPattern("HHmm")),
            segmentHavaDurumu = emptyMap(),
            segmentOturabildim = emptyMap(),
            segmentBiletKontrolu = emptyMap(),
            segmentNote = emptyMap(),
            status = S.statusReady(lang()),
            manual = _uiState.value.manual.copy(
                typeTr = VehicleType.BUS.key, line = "", direction = "",
                boardingStop = "", alightingStop = "",
                plannedDep = "", actualDep = "", plannedArr = "", actualArr = "",
                distance = "", stopCount = "",
                weather = "Bilinmiyor", oturabildim = false, biletKontrolu = false, note = ""
            )
        )
        prefs.edit().remove("first_id").remove("last_id").apply()
        // Bildirim aktifse durdur
        stopTransitNotification()
    }

    // ── API çağrıları ────────────────────────────────────────────────────────


    fun searchFrom() {
        viewModelScope.launch {
            try {
                val query = _uiState.value.from.trim()
                _uiState.value = _uiState.value.copy(status = S.statusSearchingFrom(lang()), fromOptions = emptyList())
                val result = stopSelectionUseCase.searchStops(query, 5)
                _uiState.value = _uiState.value.copy(
                    fromOptions = result.options,
                    fromMenuOpen = true,
                    status = if (result.options.isEmpty()) S.statusFromNoResult(lang()) else S.statusFromReady(lang())
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(transitAlertsLoading = false, status = "${S.errorPrefix(lang())}: ${e.message}")
            }
        }
    }


    fun searchTo() {
        viewModelScope.launch {
            try {
                val query = _uiState.value.to.trim()
                _uiState.value = _uiState.value.copy(status = S.statusSearchingTo(lang()), toOptions = emptyList())
                val result = stopSelectionUseCase.searchStops(query, 5)
                _uiState.value = _uiState.value.copy(
                    toOptions = result.options,
                    toMenuOpen = true,
                    status = if (result.options.isEmpty()) S.statusToNoResult(lang()) else S.statusToReady(lang())
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(transitAlertsLoading = false, status = "${S.errorPrefix(lang())}: ${e.message}")
            }
        }
    }

    fun fetchDepartures() {
        viewModelScope.launch {
            try {
                stopDepartureRefresh()
                val s = _uiState.value
                _uiState.value = s.copy(
                    status = S.statusFetchingDepartures(lang()),
                    departures = emptyList(),
                    selectedDeparture = null,
                    trip = null,
                    transitAlerts = emptyList(),
                    journeyMatchCandidates = emptyList(),
                    journeyMatchMessage = "",
                    firstSavedId = "",
                    lastSavedId = "",
                    segmentIds = emptyList(),
                    selectedSegmentIndex = 0,
                    isEditingTimes = false
                )
                if (s.fromId.isBlank() || s.toId.isBlank()) throw IllegalStateException(S.errorSelectStops(lang()))
                val searchTime = if (s.time.isNotBlank()) RmvApiService.formatTimeDigits(s.time)
                    else LocalTime.now().withSecond(0).withNano(0).format(DateTimeFormatter.ofPattern("HH:mm"))
                val apiDate = RmvApiService.convertToApiDate(s.date)
                val deps = rmvTripRepository.fetchDepartures(s.fromId, s.toId, apiDate, searchTime)
                _uiState.value = _uiState.value.copy(
                    departures = deps,
                    status = if (deps.isEmpty()) S.statusNoDepartures(lang()) else S.statusDeparturesReady(deps.size, lang())
                )
                if (deps.isNotEmpty()) {
                    startDepartureRefresh(s.fromId, s.toId, s.date, apiDate, searchTime)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    transitAlertsLoading = false,
                    status = "${S.errorPrefix(lang())}: ${e.message}"
                )
            }
        }
    }

    /**
     * Seçilen kalkışa göre seyahat planını oluşturur.
     * Tüm segment hesaplama mantığı [TripPlanningUseCase]'e devredilmiştir.
     */
    fun selectDeparture(dep: Departure) {
        _uiState.value = stopSelectionUseCase.selectDepartureStart(_uiState.value, dep, lang())
        viewModelScope.launch {
            try {
                val result = stopSelectionUseCase.selectDeparture(dep, _uiState.value, tripPlanner, lang())
                _uiState.value = _uiState.value.copy(
                    trip = result.trip,
                    persistentStops = result.persistentStops,
                    status = S.statusPlanReady(result.trip.segments.size, lang())
                )
                loadTransitAlerts(dep)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    transitAlertsLoading = false,
                    status = "${S.errorPrefix(lang())}: ${e.message}"
                )
            }
        }
    }

    // ── Kayıt ve zaman damgası ────────────────────────────────────────────────

    private fun loadTransitAlerts(departure: Departure) {
        viewModelScope.launch {
            try {
                val alerts = rmvTripRepository.fetchTransitAlerts(departure)
                _uiState.value = _uiState.value.copy(
                    transitAlerts = alerts,
                    transitAlertsLoading = false
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    transitAlerts = emptyList(),
                    transitAlertsLoading = false
                )
            }
        }
    }


    fun startJourneyMatch() {
        val result = journeyMatchUseCase.matchJourney(_uiState.value, hasLocationPermission())
        _uiState.value = result.state
        if (result.shouldStartService) {
            JourneyMatchForegroundService.start(ctx(), result.apiDate, result.searchTime)
        }
    }


    fun confirmJourneyMatch(candidate: JourneyMatchCandidate) {
        val result = journeyMatchUseCase.selectJourneyMatch(_uiState.value, candidate)
        _uiState.value = result.state
        result.departureIndex?.let { selectDeparture(_uiState.value.departures[it]) }
    }


    fun saveToSheets() {
        viewModelScope.launch {
            try {
                val s = _uiState.value
                val tr = s.trip ?: throw IllegalStateException(S.errorGetPlanFirst(lang()))
                _uiState.value = s.copy(status = S.statusSavingSheets(lang()))
                val result = recordSaveUseCase.saveRmvRecord(s) { id, profileId, seatmateNote ->
                    tripProfileLinkRepository.updateTripProfileLink(id, profileId, seatmateNote)
                }
                if (result.segmentIds != null) {
                    prefs.edit().putString("first_id", result.firstId.orEmpty()).putString("last_id", result.lastId.orEmpty()).apply()
                    _uiState.value = _uiState.value.copy(firstSavedId = result.firstId.orEmpty(), lastSavedId = result.lastId.orEmpty(), segmentIds = result.segmentIds, status = S.statusSaved(lang()))
                } else {
                    _uiState.value = _uiState.value.copy(status = S.statusSaved(lang()))
                }
                if (result.shouldStartNotification) startTransitNotification(tr)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(status = "${S.errorPrefix(lang())}: ${e.message}")
            }
        }
    }


    fun recordBindim() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(status = S.statusSaving(lang()))
                val result = recordSaveUseCase.recordBindim(_uiState.value)
                _uiState.value = _uiState.value.copy(status = if (result.ok) S.statusBoarded(result.time, lang()) else "${S.errorPrefix(lang())}: Kayıt bulunamadı (id=${result.id})")
                if (result.ok) updateTransitNotificationBoarding(result.id)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(status = "${S.errorPrefix(lang())}: ${e.message}")
            }
        }
    }


    fun recordIndim() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(status = S.statusSaving(lang()))
                val result = recordSaveUseCase.recordIndim(_uiState.value)
                _uiState.value = _uiState.value.copy(status = if (result.ok) S.statusAlighted(result.time, lang()) else "${S.errorPrefix(lang())}: Kayıt bulunamadı (id=${result.id})")
                if (result.ok) handleTransitNotificationAfterIndim()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(status = "${S.errorPrefix(lang())}: ${e.message}")
            }
        }
    }

    // ── Kayıt geri yükleme ───────────────────────────────────────────────────


    fun undoBindim() {
        viewModelScope.launch {
            try {
                val s = _uiState.value
                val segId = s.segmentIds.getOrElse(s.selectedSegmentIndex) { "" }
                if (segId.isBlank()) return@launch
                _uiState.value = s.copy(status = S.statusSaving(lang()))
                val ok = recordSaveUseCase.clearRecord(segId, clearDep = true, clearArr = false)
                _uiState.value = _uiState.value.copy(customBindimTime = "", status = if (ok) S.statusUndoDone(lang()) else "${S.errorPrefix(lang())}: Kayit bulunamadi (id=${segId})")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(status = "${S.errorPrefix(lang())}: ${e.message}")
            }
        }
    }


    fun undoIndim() {
        viewModelScope.launch {
            try {
                val s = _uiState.value
                val segId = s.segmentIds.getOrElse(s.selectedSegmentIndex) { "" }
                if (segId.isBlank()) return@launch
                _uiState.value = s.copy(status = S.statusSaving(lang()))
                val ok = recordSaveUseCase.clearRecord(segId, clearDep = false, clearArr = true)
                _uiState.value = _uiState.value.copy(customIndimTime = "", status = if (ok) S.statusUndoDone(lang()) else "${S.errorPrefix(lang())}: Kayit bulunamadi (id=${segId})")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(status = "${S.errorPrefix(lang())}: ${e.message}")
            }
        }
    }


    fun restoreRecord(record: Map<String, Any>) {
        clearForm()
        val docId = record["firestoreDocId"]?.toString() ?: ""
        val customId = record["id"]?.toString() ?: docId
        val restored = recordSaveUseCase.restoreRecord(_uiState.value, record) { }
        _uiState.value = restored.state.copy(status = S.statusReady(lang()))
        prefs.edit().putString("first_id", restored.firstId).putString("last_id", restored.lastId).apply()
        viewModelScope.launch {
            try {
                val linkDao = com.example.toplutasima.data.local.AppDatabase.getDatabase(getApplication()).tripProfileLinkDao()
                val userId = CurrentUserProvider.requireUserId()
                val links = if (docId.isNotBlank()) {
                    linkDao.getLinksForTrip(userId, docId)
                } else {
                    linkDao.getLinksForTrip(userId, customId)
                }
                val link = links.firstOrNull()
                if (link != null) {
                    val current = _uiState.value
                    _uiState.value = current.copy(manual = current.manual.copy(profileId = link.profileId, seatmateNote = link.seatmateNote.orEmpty()))
                }
            } catch (_: Exception) {}
        }
    }

    // ── Segment geçişi ────────────────────────────────────────────────────────

    private fun carryOverEkBilgiler(s: RmvLogUiState, from: Int, to: Int): RmvLogUiState {
        val newHava = if (to !in s.segmentHavaDurumu)
            s.segmentHavaDurumu + (to to (s.segmentHavaDurumu[from] ?: "Bilinmiyor"))
        else s.segmentHavaDurumu
        val newOtur = if (to !in s.segmentOturabildim)
            s.segmentOturabildim + (to to (s.segmentOturabildim[from] ?: false))
        else s.segmentOturabildim
        val newBilet = if (to !in s.segmentBiletKontrolu)
            s.segmentBiletKontrolu + (to to (s.segmentBiletKontrolu[from] ?: false))
        else s.segmentBiletKontrolu
        return s.copy(
            selectedSegmentIndex = to,
            segmentHavaDurumu = newHava,
            segmentOturabildim = newOtur,
            segmentBiletKontrolu = newBilet
        )
    }

    fun nextSegment() {
        val s = _uiState.value
        val maxIdx = (s.trip?.segments?.size ?: 1) - 1
        if (s.selectedSegmentIndex < maxIdx) {
            _uiState.value = carryOverEkBilgiler(s, s.selectedSegmentIndex, s.selectedSegmentIndex + 1)
        }
    }

    fun prevSegment() {
        val s = _uiState.value
        if (s.selectedSegmentIndex > 0) {
            _uiState.value = carryOverEkBilgiler(s, s.selectedSegmentIndex, s.selectedSegmentIndex - 1)
        }
    }

    // ── Durak değiştirme dialog ───────────────────────────────────────────────

    private fun changeStopDialogState(state: RmvLogUiState, segIdx: Int, mode: String): RmvLogUiState =
        state.copy(
            changeStopSegIdx = segIdx,
            changeStopMode = mode,
            changeStopSelectedIdx = -1,
            changeStopManualText = ""
        )

    fun showChangeStopDialog(segIdx: Int, mode: String) {
        _uiState.value = changeStopDialogState(_uiState.value, segIdx, mode)
    }

    fun dismissChangeStopDialog() {
        _uiState.value = _uiState.value.copy(
            changeStopSegIdx = -1,
            changeStopMode = "",
            changeStopSelectedIdx = -1,
            changeStopManualText = ""
        )
    }

    /**
     * ✏️ butonuna basıldığında çağrılır.
     * Segment'te stopNames varsa direkt dialog açar.
     * Yoksa (geri yüklenmiş kayıt), API'den durak listesini çekip sonra açar.
     */

    fun fetchStopsForChangeStop(segIdx: Int) {
        val s = _uiState.value
        val seg = s.trip?.segments?.getOrNull(segIdx) ?: return
        if (seg.stopNames.size > 1) {
            showChangeStopDialog(segIdx, "")
            return
        }
        _uiState.value = s.copy(isLoadingStopsForEdit = true, status = S.loadingStopList(lang()))
        viewModelScope.launch {
            try {
                val result = stopSelectionUseCase.fetchStopsForChangeStop(
                    _uiState.value,
                    segIdx,
                    tripPlanner,
                    lang()
                )
                _uiState.value = if (result.openDialog) {
                    changeStopDialogState(result.state, segIdx, "")
                } else {
                    result.state
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingStopsForEdit = false, status = "${S.errorPrefix(lang())}: ${e.message}")
            }
        }
    }

    fun selectChangeStop(stopIdx: Int) {
        _uiState.value = _uiState.value.copy(changeStopSelectedIdx = stopIdx)
    }


    fun confirmChangeStop() {
        viewModelScope.launch {
            try {
                val s = _uiState.value
                _uiState.value = s.copy(status = S.savingStopChange(lang()))
                _uiState.value = stopSelectionUseCase.confirmChangeStop(s, lang()) { id, binisDuragi, binisTime, inisDuragi, inisTime, mesafe, durakSayisi, distanceResult ->
                    transitRecordRepository.updateStops(
                        id,
                        binisDuragi,
                        binisTime,
                        inisDuragi,
                        inisTime,
                        mesafe,
                        durakSayisi,
                        distanceResult
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(status = "${S.errorPrefix(lang())}: ${e.message}")
            }
        }
    }

    // ── Kayıt Modu Değiştirme ─────────────────────────────────────────────────

    /** 3 modlu geçiş: AUTO ↔ MANUAL ↔ PERSONAL */
    fun setMode(newMode: LogMode) {
        _uiState.value = _uiState.value.copy(mode = newMode)
    }

    fun setManualTypeMenuOpen(open: Boolean) {
        _uiState.value = _uiState.value.copy(manual = _uiState.value.manual.copy(typeMenuOpen = open))
    }


    fun setManualWeatherMenuOpen(open: Boolean) {
        _uiState.value = _uiState.value.copy(manual = _uiState.value.manual.copy(weatherMenuOpen = open))
    }


    fun updateManualField(field: String, value: String) {
        _uiState.value = _uiState.value.copy(
            manual = manualEntryUseCase.updateManualField(_uiState.value.manual, field, value)
        )
    }

    fun updateManualLine(value: String) {
        updateManualField("line", value)
    }

    fun updateManualVehicle(value: String) {
        updateManualField("type", value)
    }

    fun updateManualDelay(value: String) {
        _uiState.value = _uiState.value.copy(
            manual = manualEntryUseCase.updateManualDelay(_uiState.value.manual, value)
        )
    }

    fun updateManualDuration(value: String) {
        _uiState.value = _uiState.value.copy(
            manual = manualEntryUseCase.updateManualDuration(_uiState.value.manual, value)
        )
    }

    fun validateManualForm(): Boolean = manualEntryUseCase.validateManualForm(_uiState.value.manual)

    fun computeManualDuration(): Int = manualEntryUseCase.computeManualDuration(
        _uiState.value.manual.plannedDep,
        _uiState.value.manual.plannedArr
    )

    fun loadActiveProfiles() {
        viewModelScope.launch {
            try {
                val profiles = profileSyncRepository.refreshSharedProfiles()
                _uiState.value = _uiState.value.copy(activeProfiles = profiles)
            } catch (_: Exception) {}
        }
    }

    fun updateSegmentProfile(index: Int, profileId: String) {
        val s = _uiState.value
        _uiState.value = s.copy(
            segmentProfileId = s.segmentProfileId + (index to profileId)
        )
        val segId = _uiState.value.segmentIds.getOrNull(index)
        if (segId != null) {
            viewModelScope.launch {
                tripProfileLinkRepository.updateTripProfileLink(segId, profileId, s.segmentSeatmateNote[index].orEmpty())
            }
        }
    }

    fun updateSegmentSeatmateNote(index: Int, note: String) {
        val s = _uiState.value
        _uiState.value = s.copy(
            segmentSeatmateNote = s.segmentSeatmateNote + (index to note)
        )
        val segId = _uiState.value.segmentIds.getOrNull(index)
        if (segId != null) {
            val profId = s.segmentProfileId[index].orEmpty()
            if (profId.isNotBlank()) {
                viewModelScope.launch {
                    tripProfileLinkRepository.updateTripProfileLink(segId, profId, note)
                }
            }
        }
    }


    fun updateManualOtur(value: Boolean) {
        _uiState.value = _uiState.value.copy(manual = _uiState.value.manual.copy(oturabildim = value))
    }


    fun updateManualBilet(value: Boolean) {
        _uiState.value = _uiState.value.copy(manual = _uiState.value.manual.copy(biletKontrolu = value))
    }


    fun saveManualRecord() {
        viewModelScope.launch {
            try {
                val s = _uiState.value
                _uiState.value = s.copy(status = S.statusSavingSheets(lang()))
                val result = recordSaveUseCase.saveManualRecord(s) { id, profileId, seatmateNote ->
                    tripProfileLinkRepository.updateTripProfileLink(id, profileId, seatmateNote)
                }
                if (result.segmentIds != null) {
                    prefs.edit().putString("first_id", result.firstId.orEmpty()).putString("last_id", result.lastId.orEmpty()).apply()
                    _uiState.value = _uiState.value.copy(firstSavedId = result.firstId.orEmpty(), lastSavedId = result.lastId.orEmpty(), segmentIds = result.segmentIds, status = S.statusSaved(lang()))
                } else {
                    _uiState.value = _uiState.value.copy(status = S.statusSaved(lang()))
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(status = "${S.errorPrefix(lang())}: ${e.message}")
            }
        }
    }

    // ── Toplu Taşıma Bildirim Yardımcıları ────────────────────────────────────

    /**
     * Android 13+ (API 33) için POST_NOTIFICATIONS izni kontrolü.
     */
    private fun hasNotificationPermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ctx().checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true // API 32 ve altında izin gerekmez
        }
    }

    /** ViewModel dışından izin durumunu sorgulamak için. */
    fun needsNotificationPermission(): Boolean {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            !hasNotificationPermission()
    }

    private fun hasActivityRecognitionPermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ctx().checkSelfPermission(android.Manifest.permission.ACTIVITY_RECOGNITION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun needsActivityRecognitionPermission(): Boolean {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q &&
            PrefsManager.transitNotificationsEnabled &&
            PrefsManager.transitAutoActualTimeMode == com.example.toplutasima.data.TransitAutoActualTimeMode.AUTO &&
            !hasActivityRecognitionPermission()
    }

    /**
     * Kayıt başarılı olduktan sonra bildirim servisini başlatır.
     */
    private fun startTransitNotification(tr: TripResult) {
        if (!PrefsManager.transitNotificationsEnabled) return
        if (!hasNotificationPermission()) return
        val s = _uiState.value
        val segIdx = s.selectedSegmentIndex
        val seg = tr.segments.getOrNull(segIdx) ?: return
        val segId = s.segmentIds.getOrNull(segIdx).orEmpty()

        try {
            val intent = Intent(ctx(), TransitTripForegroundService::class.java).apply {
                action = TransitTripForegroundService.ACTION_START
                putExtra(TransitTripForegroundService.EXTRA_LINE, seg.line)
                putExtra(TransitTripForegroundService.EXTRA_ALIGHTING_STOP, seg.toStop)
                putExtra(TransitTripForegroundService.EXTRA_PLANNED_ARR, seg.arr)
                putExtra(TransitTripForegroundService.EXTRA_VEHICLE_TYPE, seg.typeTr)
                putExtra(TransitTripForegroundService.EXTRA_SEGMENT_INDEX, segIdx)
                putExtra(TransitTripForegroundService.EXTRA_TOTAL_SEGMENTS, tr.segments.size)
                putExtra(TransitTripForegroundService.EXTRA_TRIP_ID, segId)
                putExtra(TransitTripForegroundService.EXTRA_SEGMENT_IDS, s.segmentIds.toTypedArray())
                if (!seg.toStopLat.isNaN()) putExtra(TransitTripForegroundService.EXTRA_ALIGHTING_LAT, seg.toStopLat)
                if (!seg.toStopLng.isNaN()) putExtra(TransitTripForegroundService.EXTRA_ALIGHTING_LNG, seg.toStopLng)
            }
            ctx().startForegroundService(intent)
        } catch (e: Exception) {
            android.util.Log.e("RmvLogViewModel", "Bildirim servisi başlatılamadı: ${e.message}")
        }
    }

    /**
     * Bindim kaydedildikten sonra bildirimi günceller (Bindim butonunu kaldırır + hatırlatma kurar).
     */
    private fun updateTransitNotificationBoarding(segId: String) {
        if (!PrefsManager.transitNotificationsEnabled) return
        if (!hasNotificationPermission()) return
        try {
            val intent = Intent(ctx(), TransitTripForegroundService::class.java).apply {
                action = TransitTripForegroundService.ACTION_UPDATE_BOARDING
                putExtra(TransitTripForegroundService.EXTRA_TRIP_ID, segId)
            }
            ctx().startService(intent)
        } catch (e: Exception) {
            android.util.Log.e("RmvLogViewModel", "Bildirim güncellenemedi: ${e.message}")
        }
    }

    /**
     * İndim kaydedildikten sonra: son segment ise durdur, değilse sonraki segmente geç.
     */
    private fun handleTransitNotificationAfterIndim() {
        if (!PrefsManager.transitNotificationsEnabled) return
        if (!hasNotificationPermission()) return
        val s = _uiState.value
        val tr = s.trip ?: return
        val currentIdx = s.selectedSegmentIndex
        val isLastSegment = currentIdx >= tr.segments.size - 1

        if (isLastSegment) {
            stopTransitNotification()
        } else {
            // Sonraki segmente geç
            val nextIdx = currentIdx + 1
            val nextSeg = tr.segments.getOrNull(nextIdx) ?: return
            val nextSegId = s.segmentIds.getOrNull(nextIdx).orEmpty()
            if (nextSegId.isBlank()) {
                updateTransitNotificationAlighting(s.segmentIds.getOrNull(currentIdx).orEmpty())
                return
            }
            try {
                val intent = Intent(ctx(), TransitTripForegroundService::class.java).apply {
                    action = TransitTripForegroundService.ACTION_NEXT_SEGMENT
                    putExtra(TransitTripForegroundService.EXTRA_LINE, nextSeg.line)
                    putExtra(TransitTripForegroundService.EXTRA_ALIGHTING_STOP, nextSeg.toStop)
                    putExtra(TransitTripForegroundService.EXTRA_PLANNED_ARR, nextSeg.arr)
                    putExtra(TransitTripForegroundService.EXTRA_VEHICLE_TYPE, nextSeg.typeTr)
                    putExtra(TransitTripForegroundService.EXTRA_SEGMENT_INDEX, nextIdx)
                    putExtra(TransitTripForegroundService.EXTRA_TOTAL_SEGMENTS, tr.segments.size)
                    putExtra(TransitTripForegroundService.EXTRA_TRIP_ID, nextSegId)
                    putExtra(TransitTripForegroundService.EXTRA_SEGMENT_IDS, s.segmentIds.toTypedArray())
                    if (!nextSeg.toStopLat.isNaN()) putExtra(TransitTripForegroundService.EXTRA_ALIGHTING_LAT, nextSeg.toStopLat)
                    if (!nextSeg.toStopLng.isNaN()) putExtra(TransitTripForegroundService.EXTRA_ALIGHTING_LNG, nextSeg.toStopLng)
                }
                ctx().startService(intent)
            } catch (e: Exception) {
                android.util.Log.e("RmvLogViewModel", "Sonraki segment bildirimi başlatılamadı: ${e.message}")
            }
            // ViewModel'de de sonraki segmente geç
            nextSegment()
        }
    }

    private fun updateTransitNotificationAlighting(segId: String) {
        if (!PrefsManager.transitNotificationsEnabled) return
        if (!hasNotificationPermission()) return
        if (segId.isBlank()) return
        try {
            val intent = Intent(ctx(), TransitTripForegroundService::class.java).apply {
                action = TransitTripForegroundService.ACTION_HANDLE_INDIM_FROM_NOTIF
                putExtra(TransitTripForegroundService.EXTRA_TRIP_ID, segId)
            }
            ctx().startService(intent)
        } catch (e: Exception) {
            android.util.Log.e("RmvLogViewModel", "Bildirim indirimi güncellenemedi: ${e.message}")
        }
    }

    /**
     * Bildirim servisini durdurur.
     */
    private fun stopTransitNotification() {
        try {
            val intent = Intent(ctx(), TransitTripForegroundService::class.java).apply {
                action = TransitTripForegroundService.ACTION_STOP
            }
            ctx().startService(intent)
        } catch (_: Exception) {
            // Servis çalışmıyorsa hata vermesin
        }
    }

    // ── AppEventBus: Bildirimden Bindim/İndim UI Sync ────────────────────────

    /**
     * Worker başarıyla Firestore'a yazdıktan sonra AppEventBus üzerinden bu event gelir.
     * tripId → segmentIndex eşleşmesi bulunur; yanlış segmentin verisi yazılmaz.
     *
     * SharedFlow yalnızca canlı process için çalışır. ViewModel sonradan açılırsa
     * bu event'i alamaz; bu yüzden [refreshActualTimesFromPrefs] de çağrılmalı.
     */
    private fun handleTripSyncedFromNotif(event: AppEventBus.Event.TripSynced) {
        val s = _uiState.value
        // tripId hangi segmente ait?
        val segIdx = s.segmentIds.indexOfFirst { it == event.tripId }
        if (segIdx < 0) return // Bu ViewModel'e ait değil, yoksay
        if (segIdx != s.selectedSegmentIndex) {
            refreshActualTimesFromPrefs()
            return
        }

        if (event.isBoarding) {
            _uiState.value = s.copy(
                customBindimTime = TransitTimeUtils.toDigits(event.timestamp),
                status = "Bindim ✅ (${event.timestamp}) — bildirimden"
            )
        } else {
            val isLastSeg = segIdx >= (s.trip?.segments?.size ?: 1) - 1
            _uiState.value = s.copy(
                customIndimTime = TransitTimeUtils.toDigits(event.timestamp),
                status = "İndim ✅ (${event.timestamp}) — bildirimden"
            )
            if (isLastSeg) {
                stopTransitNotification()
            }
        }
    }

    /**
     * ViewModel açıldığında veya resume edildiğinde çağrılır.
     * DB'den mevcut segmentlerin gerçek biniş/iniş saatlerini tazeler.
     * SharedFlow geçmişi yoktur; bu metod kaçırılan event'lerin telafisidir.
     */
    fun refreshTransitServiceState() {
        val activeState = transitServiceStateStore.readActiveState()
        if (activeState == null) {
            refreshActualTimesFromPrefs()
            return
        }

        val serviceState = activeState.state
        val current = _uiState.value
        val currentId = current.segmentIds.getOrNull(current.selectedSegmentIndex).orEmpty()
        if (current.trip != null && currentId == serviceState.tripId) {
            if (current.selectedSegmentIndex != serviceState.segmentIndex) {
                _uiState.value = current.copy(selectedSegmentIndex = serviceState.segmentIndex)
            }
            refreshActualTimesFromPrefs()
            return
        }

        serviceStateRefreshJob?.cancel()
        serviceStateRefreshJob = viewModelScope.launch {
            try {
                val record = transitRecordRepository.fetchRecord(serviceState.tripId) ?: return@launch
                restoreActiveTransitServiceState(activeState, record)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                refreshActualTimesFromPrefs()
            }
        }
    }

    private fun restoreActiveTransitServiceState(
        activeState: TransitServiceStateStore.ActiveState,
        record: Map<String, Any>
    ) {
        val serviceState = activeState.state
        val docId = record["firestoreDocId"]?.toString()?.takeIf { it.isNotBlank() }
        val customId = record["id"]?.toString()?.takeIf { it.isNotBlank() }
            ?: docId
            ?: serviceState.tripId
        val tarih = record["tarih"]?.toString().orEmpty()
        val tur = record["tur"]?.toString().orEmpty()
        val hat = record["hat"]?.toString().orEmpty()
        val yon = record["yon"]?.toString().orEmpty()
        val binisDuragi = record["binisDuragi"]?.toString().orEmpty()
        val inisDuragi = record["inisDuragi"]?.toString().orEmpty()
        val planlananBinis = record["planlananBinis"]?.toString().orEmpty()
        val planlananInis = record["planlananInis"]?.toString().orEmpty()
        val gercekBinis = record["gercekBinis"]?.toString().orEmpty()
        val gercekInis = record["gercekInis"]?.toString().orEmpty()
        val havaDurumu = record["havaDurumu"]?.toString() ?: "Bilinmiyor"
        val oturabildim = record["oturabildimMi"]?.toString() == SeatingStatus.YES.key
        val biletKontrolu = record["biletKontrolü"]?.toString() == TicketStatus.HAPPENED.key
        val not = record["not"]?.toString().orEmpty()
        val mesafe = record["mesafe"]?.toString().orEmpty()
        val durakSayisi = record["durakSayisi"]?.toString().orEmpty()
        val selectedIndex = serviceState.segmentIndex.coerceIn(0, (serviceState.totalSegments - 1).coerceAtLeast(0))
        val totalSegments = serviceState.totalSegments.coerceAtLeast(1)
        val segmentIds = MutableList(totalSegments) { index ->
            serviceState.segmentIds.getOrNull(index).orEmpty()
        }
        segmentIds[selectedIndex] = customId

        val distanceKm = TransitRecordCalculations.orsDistanceKm(record)
            ?: TransitRecordCalculations.parseDistanceKm(mesafe)
            ?: 0.0
        val stopCount = durakSayisi.toIntOrNull() ?: 0
        val activeSegment = Segment(
            typeTr = tur,
            line = hat,
            direction = yon,
            fromStop = binisDuragi,
            toStop = inisDuragi,
            dep = planlananBinis,
            arr = planlananInis,
            distanceKm = distanceKm,
            stopCount = stopCount,
            journeyRef = record[TransitRecordCalculations.FIELD_JOURNEY_REF]?.toString().orEmpty(),
            fromStopId = record[TransitRecordCalculations.FIELD_FROM_STOP_ID]?.toString().orEmpty(),
            toStopId = record[TransitRecordCalculations.FIELD_TO_STOP_ID]?.toString().orEmpty(),
            toStopLat = serviceState.alightingLat,
            toStopLng = serviceState.alightingLng
        )
        val segments = MutableList(totalSegments) { activeSegment }
        segments[selectedIndex] = activeSegment
        val trip = TripResult(
            segments = segments,
            overallDep = segments.firstOrNull()?.dep.orEmpty(),
            overallArr = segments.lastOrNull()?.arr.orEmpty(),
            durationMin = TransitRecordCalculations.computeYolSuresi(planlananBinis, planlananInis).toIntOrNull() ?: 0
        )

        _uiState.value = _uiState.value.copy(
            mode = LogMode.AUTO,
            date = tarih.ifBlank { _uiState.value.date },
            from = binisDuragi,
            to = inisDuragi,
            trip = trip,
            segmentIds = segmentIds,
            firstSavedId = segmentIds.firstOrNull { it.isNotBlank() }.orEmpty(),
            lastSavedId = segmentIds.lastOrNull { it.isNotBlank() }.orEmpty(),
            selectedSegmentIndex = selectedIndex,
            customBindimTime = if (gercekBinis.isNotBlank()) TransitTimeUtils.toDigits(gercekBinis) else "",
            customIndimTime = if (gercekInis.isNotBlank()) TransitTimeUtils.toDigits(gercekInis) else "",
            segmentHavaDurumu = mapOf(selectedIndex to havaDurumu),
            segmentOturabildim = mapOf(selectedIndex to oturabildim),
            segmentBiletKontrolu = mapOf(selectedIndex to biletKontrolu),
            segmentNote = mapOf(selectedIndex to not),
            status = if (activeState.isWaitingToBoard) S.statusReady(lang()) else S.statusReady(lang())
        )
        prefs.edit()
            .putString("first_id", segmentIds.firstOrNull { it.isNotBlank() }.orEmpty())
            .putString("last_id", segmentIds.lastOrNull { it.isNotBlank() }.orEmpty())
            .apply()
    }

    fun refreshActualTimesFromPrefs() {
        val s = _uiState.value
        val segmentIds = s.segmentIds
        if (segmentIds.isEmpty()) return // Kayıt yüklü değil, gerek yok
        val selectedIndex = s.selectedSegmentIndex
        val selectedId = segmentIds.getOrNull(selectedIndex).orEmpty()
        if (selectedId.isBlank()) return
        actualTimesRefreshJob?.cancel()
        actualTimesRefreshJob = viewModelScope.launch {
            try {
                val record = transitRecordRepository.fetchRecord(selectedId) ?: return@launch
                val latest = _uiState.value
                val latestSelectedId = latest.segmentIds.getOrNull(latest.selectedSegmentIndex).orEmpty()
                if (latestSelectedId != selectedId) return@launch
                val gercekBinis = record["gercekBinis"]?.toString().orEmpty()
                val gercekInis = record["gercekInis"]?.toString().orEmpty()
                val newBindim = if (gercekBinis.isNotBlank()) TransitTimeUtils.toDigits(gercekBinis)
                                else latest.customBindimTime
                val newIndim = if (gercekInis.isNotBlank()) TransitTimeUtils.toDigits(gercekInis)
                               else latest.customIndimTime
                if (newBindim != latest.customBindimTime || newIndim != latest.customIndimTime) {
                    _uiState.value = latest.copy(
                        customBindimTime = newBindim,
                        customIndimTime = newIndim
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) { /* ağ hatası — sessizce geç */ }
        }
    }

    override fun onCleared() {
        stopDepartureRefresh()
        actualTimesRefreshJob?.cancel()
        super.onCleared()
    }
}
