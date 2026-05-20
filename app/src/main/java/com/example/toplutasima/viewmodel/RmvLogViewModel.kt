package com.example.toplutasima.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.toplutasima.data.AppEventBus
import com.example.toplutasima.data.PrefsManager
import com.example.toplutasima.model.Departure
import com.example.toplutasima.model.JourneyMatchCandidate
import com.example.toplutasima.model.Segment
import com.example.toplutasima.model.SeatingStatus
import com.example.toplutasima.model.StopOption
import com.example.toplutasima.model.TicketStatus
import com.example.toplutasima.model.TripResult
import com.example.toplutasima.model.TransitAlert
import com.example.toplutasima.model.VehicleType
import com.example.toplutasima.network.FirestoreService
import com.example.toplutasima.network.RmvApiService
import com.example.toplutasima.repository.TripRepository
import com.example.toplutasima.service.JourneyMatchForegroundService
import com.example.toplutasima.service.TransitNotificationReceiver
import com.example.toplutasima.service.TransitTripForegroundService
import com.example.toplutasima.ui.LocaleManager
import com.example.toplutasima.ui.S
import com.example.toplutasima.usecase.TripPlanningUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

// ── Kayıt Modu ───────────────────────────────────────────────────────────────
enum class LogMode { AUTO, MANUAL }

// ── Manuel giriş alanları ayrı bir state paketine taşındı ────────────────────
// Böylece RmvLogUiState daha az değişken barındırır ve form state'i net ayrılır.
data class ManualEntryState(
    val isManualMode: Boolean = false,
    val typeTr: String = VehicleType.BUS.key,
    val typeMenuOpen: Boolean = false,
    val line: String = "",
    val direction: String = "",
    val boardingStop: String = "",
    val alightingStop: String = "",
    val plannedDep: String = "",
    val actualDep: String = "",
    val plannedArr: String = "",
    val actualArr: String = "",
    val distance: String = "",
    val stopCount: String = "",
    val weather: String = "Bilinmiyor",
    val weatherMenuOpen: Boolean = false,
    val oturabildim: Boolean = false,
    val biletKontrolu: Boolean = false,
    val note: String = ""
)

data class RmvLogUiState(
    // ── Durak arama formu ──
    val from: String = "",
    val to: String = "",
    val fromId: String = "",
    val toId: String = "",
    val fromOptions: List<StopOption> = emptyList(),
    val toOptions: List<StopOption> = emptyList(),
    val fromMenuOpen: Boolean = false,
    val toMenuOpen: Boolean = false,
    // ── Zaman / tarih ──
    val isEditingTimes: Boolean = false,
    val date: String = "",
    val time: String = "",
    // ── Seyahat verisi ──
    val departures: List<Departure> = emptyList(),
    val selectedDeparture: Departure? = null,
    val trip: TripResult? = null,
    val transitAlerts: List<TransitAlert> = emptyList(),
    val transitAlertsLoading: Boolean = false,
    val journeyMatchCandidates: List<JourneyMatchCandidate> = emptyList(),
    val journeyMatchLoading: Boolean = false,
    val journeyMatchMessage: String = "",
    // ── Segment ek bilgileri ──
    val segmentHavaDurumu: Map<Int, String> = emptyMap(),
    val havaMenuOpen: Boolean = false,
    val segmentOturabildim: Map<Int, Boolean> = emptyMap(),
    val segmentBiletKontrolu: Map<Int, Boolean> = emptyMap(),
    val segmentNote: Map<Int, String> = emptyMap(),
    // ── UI durumu ──
    val status: String = "",
    val customBindimTime: String = "",
    val customIndimTime: String = "",
    val firstSavedId: String = "",
    val lastSavedId: String = "",
    val segmentIds: List<String> = emptyList(),
    val selectedSegmentIndex: Int = 0,
    val persistentStops: List<com.example.toplutasima.model.Segment> = emptyList(),
    // ── Durak değiştirme dialog ──
    val changeStopSegIdx: Int = -1,
    val changeStopMode: String = "",  // "binis" or "inis"
    val changeStopSelectedIdx: Int = -1,
    val changeStopManualText: String = "",
    val isLoadingStopsForEdit: Boolean = false,
    // ── Yakındaki duraklar ──
    val nearbyStops: List<com.example.toplutasima.network.RmvApiService.NearbyStop> = emptyList(),
    val nearbyLoading: Boolean = false,
    val nearbyHasLoaded: Boolean = false,
    // ── Favoriye ekleme dialog state ──
    val showAddFavDialog: Boolean = false,
    val addFavStopId: String = "",
    val addFavStopName: String = "",
    val addFavLabel: String = "",
    val addFavUsageType: com.example.toplutasima.model.UsageType = com.example.toplutasima.model.UsageType.BOTH,
    val addFavMessage: String = "",
    // ── Kayıt Modu ─────────────────────────────────────────────────────────
    val mode: LogMode = LogMode.AUTO,
    // ── Manuel mod (ayrı state paketine taşındı) ──
    val manual: ManualEntryState = ManualEntryState()
) {
    // Screen kodu değişmesin diye backward-compat property'ler
    val isManualMode get() = mode == LogMode.MANUAL
    val manualTypeTr get() = manual.typeTr
    val manualTypeMenuOpen get() = manual.typeMenuOpen
    val manualLine get() = manual.line
    val manualDirection get() = manual.direction
    val manualBoardingStop get() = manual.boardingStop
    val manualAlightingStop get() = manual.alightingStop
    val manualPlannedDep get() = manual.plannedDep
    val manualActualDep get() = manual.actualDep
    val manualPlannedArr get() = manual.plannedArr
    val manualActualArr get() = manual.actualArr
    val manualDistance get() = manual.distance
    val manualStopCount get() = manual.stopCount
    val manualWeather get() = manual.weather
    val manualWeatherMenuOpen get() = manual.weatherMenuOpen
    val manualOturabildim get() = manual.oturabildim
    val manualBiletKontrolu get() = manual.biletKontrolu
    val manualNote get() = manual.note
}

class RmvLogViewModel(
    application: Application,
    private val repository: TripRepository,
    private val tripPlanner: TripPlanningUseCase,
    private val nearbyManager: com.example.toplutasima.location.NearbyStopsManager
) : AndroidViewModel(application) {
    private companion object {
        const val DEPARTURE_REFRESH_INTERVAL_MS = 30_000L
    }

    private val prefs = application.getSharedPreferences("rmv_prefs", Context.MODE_PRIVATE)

    private var tripDetailJob: Job? = null
    private var actualTimesRefreshJob: Job? = null
    private var departureRefreshJob: Job? = null

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
                    val freshDepartures = repository.fetchDepartures(fromId, toId, apiDate, searchTime)
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
        _uiState.value = _uiState.value.copy(
            from = stop.name,
            fromId = stop.id,
            fromOptions = emptyList(),
            fromMenuOpen = false
        )
    }

    // ── Favoriye ekleme dialog ────────────────────────────────────────────────

    fun showAddFavoriteDialog(stopId: String, stopName: String) {
        _uiState.value = _uiState.value.copy(
            showAddFavDialog = true,
            addFavStopId = stopId,
            addFavStopName = stopName,
            addFavLabel = "",
            addFavUsageType = com.example.toplutasima.model.UsageType.BOTH,
            addFavMessage = ""
        )
    }

    fun dismissAddFavoriteDialog() {
        _uiState.value = _uiState.value.copy(showAddFavDialog = false, addFavMessage = "")
    }

    fun updateAddFavLabel(label: String) {
        _uiState.value = _uiState.value.copy(addFavLabel = label)
    }

    fun updateAddFavUsageType(type: com.example.toplutasima.model.UsageType) {
        _uiState.value = _uiState.value.copy(addFavUsageType = type)
    }

    fun confirmAddFavorite() {
        val s = _uiState.value
        if (s.addFavStopId.isBlank()) return
        val label = s.addFavLabel.ifBlank { s.addFavStopName }
        com.example.toplutasima.data.PrefsManager.addFavorite(
            stopId = s.addFavStopId,
            stopName = s.addFavStopName,
            label = label,
            usageType = s.addFavUsageType
        )
        _uiState.value = s.copy(
            showAddFavDialog = false,
            addFavMessage = S.favAdded(lang())
        )
    }

    fun selectFavoriteFrom(stopId: String, stopName: String) {
        stopDepartureRefresh()
        _uiState.value = _uiState.value.copy(
            from = stopName, fromId = stopId, fromOptions = emptyList(), fromMenuOpen = false
        )
    }

    fun selectFavoriteTo(stopId: String, stopName: String) {
        stopDepartureRefresh()
        _uiState.value = _uiState.value.copy(
            to = stopName, toId = stopId, toOptions = emptyList(), toMenuOpen = false
        )
    }

    // ── Durak arama ──────────────────────────────────────────────────────────

    fun updateFrom(value: String) {
        stopDepartureRefresh()
        _uiState.value = _uiState.value.copy(from = value, fromId = "", fromOptions = emptyList())
    }

    fun updateTo(value: String) {
        stopDepartureRefresh()
        _uiState.value = _uiState.value.copy(to = value, toId = "", toOptions = emptyList())
    }

    fun selectFrom(option: StopOption) {
        stopDepartureRefresh()
        _uiState.value = _uiState.value.copy(
            from = option.routingName,
            fromId = option.routingId,
            fromMenuOpen = false
        )
    }

    fun selectTo(option: StopOption) {
        stopDepartureRefresh()
        _uiState.value = _uiState.value.copy(
            to = option.routingName,
            toId = option.routingId,
            toMenuOpen = false
        )
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
        val s = _uiState.value
        _uiState.value = s.copy(
            from = s.to, fromId = s.toId, fromOptions = s.toOptions,
            to = s.from, toId = s.fromId, toOptions = s.fromOptions
        )
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
                repository.updateExistingRecord(segId, mapOf("havaDurumu" to value))
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
                repository.updateExistingRecord(segId, mapOf("oturabildimMi" to SeatingStatus.fromBoolean(value).key))
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
                repository.updateExistingRecord(segId, mapOf("biletKontrolü" to TicketStatus.fromBoolean(value).key))
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
            durationMin = computeDuration(newOverallDep, newOverallArr)
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
            durationMin = computeDuration(newOverallDep, newOverallArr)
        ))
    }

    private fun computeDuration(dep: String, arr: String): Int {
        return try {
            val d = LocalTime.parse(dep.take(5))
            val a = LocalTime.parse(arr.take(5))
            var diff = Duration.between(d, a).toMinutes().toInt()
            if (diff < 0) diff += 24 * 60
            diff
        } catch (_: Exception) { 0 }
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
                val cached = PrefsManager.getCachedStops(query, 5)
                if (cached.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        fromOptions = cached,
                        fromMenuOpen = true,
                        status = S.statusFromReady(lang())
                    )
                    return@launch
                }
                _uiState.value = _uiState.value.copy(status = S.statusSearchingFrom(lang()), fromOptions = emptyList())
                val opts = repository.searchLocations(query, 5).map { it.toStopOption() }
                PrefsManager.saveStopSearch(query, opts)
                _uiState.value = _uiState.value.copy(
                    fromOptions = opts,
                    fromMenuOpen = true,
                    status = if (opts.isEmpty()) S.statusFromNoResult(lang()) else S.statusFromReady(lang())
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    transitAlertsLoading = false,
                    status = "${S.errorPrefix(lang())}: ${e.message}"
                )
            }
        }
    }

    fun searchTo() {
        viewModelScope.launch {
            try {
                val query = _uiState.value.to.trim()
                val cached = PrefsManager.getCachedStops(query, 5)
                if (cached.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        toOptions = cached,
                        toMenuOpen = true,
                        status = S.statusToReady(lang())
                    )
                    return@launch
                }
                _uiState.value = _uiState.value.copy(status = S.statusSearchingTo(lang()), toOptions = emptyList())
                val opts = repository.searchLocations(query, 5).map { it.toStopOption() }
                PrefsManager.saveStopSearch(query, opts)
                _uiState.value = _uiState.value.copy(
                    toOptions = opts,
                    toMenuOpen = true,
                    status = if (opts.isEmpty()) S.statusToNoResult(lang()) else S.statusToReady(lang())
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    transitAlertsLoading = false,
                    status = "${S.errorPrefix(lang())}: ${e.message}"
                )
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
                val deps = repository.fetchDepartures(s.fromId, s.toId, apiDate, searchTime)
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
        tripDetailJob?.cancel()
        _uiState.value = _uiState.value.copy(
            selectedDeparture = dep,
            trip = null,
            transitAlerts = emptyList(),
            transitAlertsLoading = true,
            journeyMatchCandidates = emptyList(),
            firstSavedId = "",
            lastSavedId = "",
            segmentIds = emptyList(),
            selectedSegmentIndex = 0,
            isEditingTimes = false,
            customBindimTime = "",
            customIndimTime = "",
            status = S.statusFetchingPlan(lang())
        )
        viewModelScope.launch {
            try {
                val s = _uiState.value
                if (s.fromId.isBlank() || s.toId.isBlank()) throw IllegalStateException(S.errorSelectFromList(lang()))

                val input = TripPlanningUseCase.PlanInput(
                    dep = dep,
                    fromId = s.fromId,
                    toId = s.toId,
                    from = s.from,
                    to = s.to,
                    date = s.date
                )
                // UseCase içinde tüm hesaplama ve API çağrıları gerçekleşir
                val finalTrip = tripPlanner.plan(input)

                _uiState.value = _uiState.value.copy(
                    trip = finalTrip,
                    status = S.statusPlanReady(finalTrip.segments.size, lang())
                )
                loadTransitAlerts(dep.line)

                // Segment detaylarını arka planda tamamla (iptal edilebilir)
                val expectedSegmentCount = finalTrip.segments.size
                tripDetailJob = viewModelScope.launch {
                    try {
                        val detailsList = finalTrip.segments.map { seg ->
                            ensureActive()
                            if (seg.stopNames.isNotEmpty()) {
                                RmvApiService.SegmentDetails(
                                    distanceKm = seg.distanceKm,
                                    stopCount = seg.stopCount,
                                    stopNames = seg.stopNames,
                                    stopTimes = seg.stopTimes,
                                    fromIdx = seg.stopFromIdx,
                                    toIdx = seg.stopToIdx,
                                    toStopLat = seg.toStopLat,
                                    toStopLng = seg.toStopLng
                                )
                            } else {
                                runCatching { repository.fetchSegmentDetails(seg) }
                                    .getOrDefault(RmvApiService.SegmentDetails(0.0, 0, emptyList()))
                            }
                        }
                        ensureActive()
                        val current = _uiState.value
                        val currentTrip = current.trip ?: return@launch
                        if (currentTrip.segments.size != expectedSegmentCount) return@launch
                        val updatedSegs = currentTrip.segments.mapIndexed { idx, seg ->
                            val d = detailsList[idx]
                            applySegmentDetails(seg, d)
                        }
                        _uiState.value = current.copy(
                            trip = currentTrip.copy(segments = updatedSegs),
                            persistentStops = updatedSegs
                        )
                    } catch (_: Exception) { }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    transitAlertsLoading = false,
                    status = "${S.errorPrefix(lang())}: ${e.message}"
                )
            }
        }
    }

    fun fetchTrip() {
        tripDetailJob?.cancel()
        viewModelScope.launch {
            try {
                val s = _uiState.value
                _uiState.value = s.copy(status = S.statusFetchingPlan(lang()), trip = null, firstSavedId = "", lastSavedId = "", isEditingTimes = false)
                if (s.fromId.isBlank() || s.toId.isBlank()) throw IllegalStateException(S.errorSelectFromList(lang()))
                val searchTime = if (s.time.isNotBlank()) RmvApiService.formatTimeDigits(s.time)
                    else LocalTime.now().withSecond(0).withNano(0).format(DateTimeFormatter.ofPattern("HH:mm"))
                val apiDate = RmvApiService.convertToApiDate(s.date)
                val res = repository.fetchTripBasic(s.fromId, s.toId, apiDate, searchTime)
                _uiState.value = _uiState.value.copy(trip = res, status = S.statusPlanReady(res.segments.size, lang()))

                val expectedSegmentCount = res.segments.size
                tripDetailJob = viewModelScope.launch {
                    try {
                        val detailsList = res.segments.map { seg ->
                            ensureActive()
                            runCatching { repository.fetchSegmentDetails(seg) }
                                .getOrDefault(RmvApiService.SegmentDetails(0.0, 0, emptyList()))
                        }
                        ensureActive()
                        val current = _uiState.value
                        val currentTrip = current.trip ?: return@launch
                        if (currentTrip.segments.size != expectedSegmentCount) return@launch
                        val updatedSegs = currentTrip.segments.mapIndexed { idx, seg ->
                            val d = detailsList[idx]
                            applySegmentDetails(seg, d)
                        }
                        _uiState.value = current.copy(trip = currentTrip.copy(segments = updatedSegs))
                    } catch (_: Exception) { }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(status = "${S.errorPrefix(lang())}: ${e.message}")
            }
        }
    }

    // ── Kayıt ve zaman damgası ────────────────────────────────────────────────

    private fun loadTransitAlerts(line: String) {
        viewModelScope.launch {
            try {
                val apiDate = runCatching { RmvApiService.convertToApiDate(_uiState.value.date) }.getOrDefault("")
                val alerts = repository.fetchTransitAlerts(line, apiDate)
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
        val s = _uiState.value
        if (!hasLocationPermission()) {
            _uiState.value = s.copy(journeyMatchMessage = "Konum izni gerekli", status = "Konum izni gerekli")
            return
        }
        val apiDate = runCatching { RmvApiService.convertToApiDate(s.date) }.getOrDefault("")
        val searchTime = if (s.time.isNotBlank()) RmvApiService.formatTimeDigits(s.time)
            else LocalTime.now().withSecond(0).withNano(0).format(DateTimeFormatter.ofPattern("HH:mm"))
        _uiState.value = s.copy(
            journeyMatchLoading = true,
            journeyMatchCandidates = emptyList(),
            journeyMatchMessage = "GPS izi aliniyor...",
            status = "GPS izi aliniyor..."
        )
        JourneyMatchForegroundService.start(ctx(), apiDate, searchTime)
    }

    fun confirmJourneyMatch(candidate: JourneyMatchCandidate) {
        val matchingDeparture = _uiState.value.departures.firstOrNull {
            RmvApiService.normalizeLineForDisplay(it.line) == RmvApiService.normalizeLineForDisplay(candidate.line)
        }
        _uiState.value = _uiState.value.copy(
            journeyMatchCandidates = emptyList(),
            journeyMatchMessage = "GPS eslesmesi onaylandi: ${candidate.line}",
            status = "GPS eslesmesi onaylandi: ${candidate.line}"
        )
        if (matchingDeparture != null) {
            selectDeparture(matchingDeparture)
        }
    }

    fun saveToSheets() {
        viewModelScope.launch {
            try {
                val s = _uiState.value
                val tr = s.trip ?: throw IllegalStateException(S.errorGetPlanFirst(lang()))
                _uiState.value = s.copy(status = S.statusSavingSheets(lang()))

                if (s.segmentIds.isEmpty()) {
                    val ids = tr.segments.mapIndexed { idx, seg ->
                        val id = UUID.randomUUID().toString()
                        val hava = s.segmentHavaDurumu[idx] ?: "Bilinmiyor"
                        val otur = s.segmentOturabildim[idx] ?: false
                        val bilet = s.segmentBiletKontrolu[idx] ?: false
                        val not = s.segmentNote[idx] ?: ""
                        val ok = repository.saveSegment(id, s.date, seg, hava, otur, bilet, not)
                        if (!ok) throw Exception(S.errorSaveFailed(lang()))
                        if (idx == s.selectedSegmentIndex && (s.customBindimTime.isNotBlank() || s.customIndimTime.isNotBlank())) {
                            repository.updateActual(
                                id,
                                s.customBindimTime.takeIf { it.isNotBlank() }?.let { RmvApiService.formatTimeDigits(it) },
                                s.customIndimTime.takeIf { it.isNotBlank() }?.let { RmvApiService.formatTimeDigits(it) }
                            )
                        }
                        id
                    }
                    val firstId = ids.firstOrNull().orEmpty()
                    val lastId = ids.lastOrNull().orEmpty()
                    prefs.edit().putString("first_id", firstId).putString("last_id", lastId).apply()
                    _uiState.value = _uiState.value.copy(firstSavedId = firstId, lastSavedId = lastId, segmentIds = ids, status = S.statusSaved(lang()))
                } else {
                    s.segmentIds.forEachIndexed { idx, id ->
                        val seg = tr.segments.getOrNull(idx) ?: return@forEachIndexed
                        val hava = s.segmentHavaDurumu[idx] ?: "Bilinmiyor"
                        val otur = s.segmentOturabildim[idx] ?: false
                        val bilet = s.segmentBiletKontrolu[idx] ?: false
                        val not = s.segmentNote[idx] ?: ""
                        val planlananSure = FirestoreService.computeYolSuresi(
                            seg.dep.ifBlank { null }, seg.arr.ifBlank { null }
                        )
                        val ok = repository.updateExistingRecord(id, mapOf(
                            "planlananBinis" to seg.dep,
                            "planlananInis" to seg.arr,
                            "planlananYolSuresi" to planlananSure,
                            "havaDurumu" to hava,
                            "oturabildimMi" to SeatingStatus.fromBoolean(otur).key,
                            "biletKontrolü" to TicketStatus.fromBoolean(bilet).key,
                            "not" to not
                        ))
                        if (!ok) throw Exception(S.errorSaveFailed(lang()))
                    }
                    _uiState.value = _uiState.value.copy(status = S.statusSaved(lang()))
                }

                // ── Bildirim başlat ──────────────────────────────────────────
                startTransitNotification(tr)

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(status = "${S.errorPrefix(lang())}: ${e.message}")
            }
        }
    }

    fun recordBindim() {
        viewModelScope.launch {
            try {
                val s = _uiState.value
                val segId = s.segmentIds.getOrElse(s.selectedSegmentIndex) { "" }
                if (segId.isBlank()) throw IllegalStateException(S.errorGetPlanFirst(lang()))
                val t = if (s.customBindimTime.isNotBlank()) RmvApiService.formatTimeDigits(s.customBindimTime)
                    else LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                _uiState.value = s.copy(status = S.statusSaving(lang()))
                val ok = repository.updateActual(segId, t, null)
                _uiState.value = _uiState.value.copy(
                    status = if (ok) S.statusBoarded(t, lang())
                             else "${S.errorPrefix(lang())}: Kayıt bulunamadı (id=$segId)"
                )
                // ── Bildirimi güncelle (Bindim butonunu kaldır + hatırlatma kur) ──
                if (ok) updateTransitNotificationBoarding(segId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(status = "${S.errorPrefix(lang())}: ${e.message}")
            }
        }
    }

    fun recordIndim() {
        viewModelScope.launch {
            try {
                val s = _uiState.value
                val segId = s.segmentIds.getOrElse(s.selectedSegmentIndex) { "" }
                if (segId.isBlank()) throw IllegalStateException(S.errorGetPlanFirst(lang()))
                val t = if (s.customIndimTime.isNotBlank()) RmvApiService.formatTimeDigits(s.customIndimTime)
                    else LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                _uiState.value = s.copy(status = S.statusSaving(lang()))
                val ok = repository.updateActual(segId, null, t)
                _uiState.value = _uiState.value.copy(
                    status = if (ok) S.statusAlighted(t, lang())
                             else "${S.errorPrefix(lang())}: Kayıt bulunamadı (id=$segId)"
                )
                // ── Bildirim: durdur veya sonraki segmente geç ──
                if (ok) handleTransitNotificationAfterIndim()
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
                val ok = repository.clearActual(segId, clearDep = true, clearArr = false)
                _uiState.value = _uiState.value.copy(
                    customBindimTime = "",
                    status = if (ok) S.statusUndoDone(lang()) else "${S.errorPrefix(lang())}: Kayit bulunamadi (id=$segId)"
                )
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
                val ok = repository.clearActual(segId, clearDep = false, clearArr = true)
                _uiState.value = _uiState.value.copy(
                    customIndimTime = "",
                    status = if (ok) S.statusUndoDone(lang()) else "${S.errorPrefix(lang())}: Kayit bulunamadi (id=$segId)"
                )
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
        val tarih = record["tarih"]?.toString() ?: ""
        val tur = record["tur"]?.toString() ?: ""
        val hat = record["hat"]?.toString() ?: ""
        val yon = record["yon"]?.toString() ?: ""
        val binisDuragi = record["binisDuragi"]?.toString() ?: ""
        val inisDuragi = record["inisDuragi"]?.toString() ?: ""
        val planlananBinis = record["planlananBinis"]?.toString() ?: ""
        val planlananInis = record["planlananInis"]?.toString() ?: ""
        val gercekBinis = record["gercekBinis"]?.toString() ?: ""
        val gercekInis = record["gercekInis"]?.toString() ?: ""
        val havaDurumu = record["havaDurumu"]?.toString() ?: "Bilinmiyor"
        val oturabildim = record["oturabildimMi"]?.toString() == SeatingStatus.YES.key
        val biletKontrolu = record["biletKontrolü"]?.toString() == TicketStatus.HAPPENED.key
        val not = record["not"]?.toString() ?: ""
        val mesafe = record["mesafe"]?.toString() ?: ""
        val durakSayisi = record["durakSayisi"]?.toString() ?: ""

        val distanceKm = FirestoreService.orsDistanceKm(record) ?: FirestoreService.parseDistanceKm(mesafe) ?: 0.0
        val stopCount = durakSayisi.toIntOrNull() ?: 0

        val segment = com.example.toplutasima.model.Segment(
            typeTr = tur, line = hat, direction = yon,
            fromStop = binisDuragi, toStop = inisDuragi,
            dep = planlananBinis, arr = planlananInis,
            distanceKm = distanceKm,
            stopCount = stopCount,
            journeyRef = record[FirestoreService.FIELD_JOURNEY_REF]?.toString().orEmpty(),
            fromStopId = record[FirestoreService.FIELD_FROM_STOP_ID]?.toString().orEmpty(),
            toStopId = record[FirestoreService.FIELD_TO_STOP_ID]?.toString().orEmpty()
        )

        val trip = com.example.toplutasima.model.TripResult(
            segments = listOf(segment),
            overallDep = planlananBinis,
            overallArr = planlananInis,
            durationMin = try {
                val d = java.time.LocalTime.parse(planlananBinis.take(5))
                val a = java.time.LocalTime.parse(planlananInis.take(5))
                var diff = java.time.Duration.between(d, a).toMinutes().toInt()
                if (diff < 0) diff += 24 * 60
                diff
            } catch (_: Exception) { 0 }
        )

        fun toDigits(t: String): String = t.replace(":", "").take(4)

        _uiState.value = _uiState.value.copy(
            date = tarih,
            from = binisDuragi,
            to = inisDuragi,
            trip = trip,
            segmentIds = if (customId.isNotBlank()) listOf(customId) else emptyList(),
            firstSavedId = customId,
            lastSavedId = customId,
            selectedSegmentIndex = 0,
            customBindimTime = if (gercekBinis.isNotBlank()) toDigits(gercekBinis) else "",
            customIndimTime = if (gercekInis.isNotBlank()) toDigits(gercekInis) else "",
            segmentHavaDurumu = mapOf(0 to havaDurumu),
            segmentOturabildim = mapOf(0 to oturabildim),
            segmentBiletKontrolu = mapOf(0 to biletKontrolu),
            segmentNote = mapOf(0 to not),
            status = S.statusReady(lang())
        )
        prefs.edit().putString("first_id", customId).putString("last_id", customId).apply()
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

    fun showChangeStopDialog(segIdx: Int, mode: String) {
        _uiState.value = _uiState.value.copy(
            changeStopSegIdx = segIdx,
            changeStopMode = mode,
            changeStopSelectedIdx = -1,
            changeStopManualText = ""
        )
    }

    fun dismissChangeStopDialog() {
        _uiState.value = _uiState.value.copy(
            changeStopSegIdx = -1,
            changeStopMode = "",
            changeStopSelectedIdx = -1,
            changeStopManualText = ""
        )
    }

    fun updateChangeStopManualText(text: String) {
        _uiState.value = _uiState.value.copy(changeStopManualText = text)
    }

    /**
     * ✏️ butonuna basıldığında çağrılır.
     * Segment'te stopNames varsa direkt dialog açar.
     * Yoksa (geri yüklenmiş kayıt), API'den durak listesini çekip sonra açar.
     */
    fun fetchStopsForChangeStop(segIdx: Int) {
        val s = _uiState.value
        val seg = s.trip?.segments?.getOrNull(segIdx) ?: return

        // Durak listesi zaten varsa direkt dialog aç
        if (seg.stopNames.size > 1) {
            showChangeStopDialog(segIdx, "")
            return
        }

        // stopNames yok → API'den çek
        _uiState.value = s.copy(
            isLoadingStopsForEdit = true,
            status = S.loadingStopList(lang())
        )
        viewModelScope.launch {
            try {
                // 1. Biniş ve iniş durak ID'lerini ara
                val fromOpts = repository.searchStops(seg.fromStop.trim(), 3)
                val fromId = fromOpts.firstOrNull()?.id
                    ?: throw Exception(S.errorStopNotFound(lang()))

                val toOpts = repository.searchStops(seg.toStop.trim(), 3)
                val toId = toOpts.firstOrNull()?.id
                    ?: throw Exception(S.errorStopNotFound(lang()))

                // 2. Kalkış listesi çek (kayıtlı planlanan saati kullan)
                val apiDate = RmvApiService.convertToApiDate(s.date)
                val searchTime = seg.dep.take(5).ifBlank {
                    java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                }
                val deps = repository.fetchDepartures(fromId, toId, apiDate, searchTime)

                // 3. Hat numarasına göre en uygun kalkışı bul
                val matchingDep = deps.firstOrNull { dep ->
                    dep.line.contains(seg.line, ignoreCase = true) ||
                    seg.line.contains(dep.line, ignoreCase = true)
                } ?: deps.firstOrNull()
                    ?: throw Exception(S.statusNoDepartures(lang()))

                // 4. Seyahat planını çek
                val input = TripPlanningUseCase.PlanInput(
                    dep = matchingDep,
                    fromId = fromId,
                    toId = toId,
                    from = seg.fromStop,
                    to = seg.toStop,
                    date = s.date
                )
                val newTrip = tripPlanner.plan(input)
                val newSeg = newTrip.segments.firstOrNull()
                    ?: throw Exception("Segment bulunamadı")

                // 5. Durak detaylarını (stopNames, stopTimes, journeyRef) çek
                val details = runCatching { repository.fetchSegmentDetails(newSeg) }
                    .getOrDefault(RmvApiService.SegmentDetails(0.0, 0, emptyList()))

                val stopNames = details.stopNames.ifEmpty { newSeg.stopNames }
                val stopTimes = details.stopTimes.ifEmpty { newSeg.stopTimes }

                if (stopNames.size <= 1) throw Exception(S.errorStopNotFound(lang()))

                // 6. Mevcut segmenti sadece durak meta verisiyle güncelle
                //    (orijinal dep/arr/fromStop/toStop korunur)
                val current = _uiState.value
                val currentTrip = current.trip ?: return@launch
                val currentSeg = currentTrip.segments.getOrNull(segIdx) ?: return@launch

                val detailRangeResolved = details.fromIdx >= 0 && details.toIdx >= 0
                val updatedSeg = currentSeg.copy(
                    stopNames   = stopNames,
                    stopTimes   = stopTimes,
                    journeyRef  = newSeg.journeyRef,
                    stopFromIdx = if (detailRangeResolved) details.fromIdx else newSeg.stopFromIdx,
                    stopToIdx   = if (detailRangeResolved) details.toIdx else newSeg.stopToIdx,
                    distanceKm  = if (details.distanceKm > 0) details.distanceKm else newSeg.distanceKm,
                    stopCount   = if (details.stopCount > 0) details.stopCount else newSeg.stopCount,
                    toStopLat   = if (!details.toStopLat.isNaN()) details.toStopLat else newSeg.toStopLat,
                    toStopLng   = if (!details.toStopLng.isNaN()) details.toStopLng else newSeg.toStopLng
                )
                val newSegs = currentTrip.segments.toMutableList()
                newSegs[segIdx] = updatedSeg

                _uiState.value = current.copy(
                    trip = currentTrip.copy(segments = newSegs),
                    fromId = fromId,
                    toId   = toId,
                    isLoadingStopsForEdit = false,
                    status = S.statusReady(lang())
                )

                // 7. Artık dialog açılabilir
                showChangeStopDialog(segIdx, "")

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingStopsForEdit = false,
                    status = "${S.errorPrefix(lang())}: ${e.message}"
                )
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
                val segIdx = s.changeStopSegIdx
                val trip = s.trip ?: return@launch
                if (segIdx < 0 || segIdx >= trip.segments.size) return@launch
                val seg = trip.segments[segIdx]
                val selectedIdx = s.changeStopSelectedIdx
                // Manuel modda (stopNames boş) selectedIdx=-1 geçerli; liste modunda sınır kontrolü yap
                if (seg.stopNames.isNotEmpty() && (selectedIdx < 0 || selectedIdx >= seg.stopNames.size)) return@launch

                val newStopName = if (seg.stopNames.isNotEmpty()) {
                    // Liste modunda seçilen durak
                    seg.stopNames[selectedIdx]
                } else {
                    // Manuel giriş modu
                    val manualText = s.changeStopManualText.trim()
                    if (manualText.isBlank()) return@launch
                    manualText
                }
                val newTime = if (seg.stopNames.isNotEmpty()) seg.stopTimes.getOrElse(selectedIdx) { "" } else ""
                val segId = s.segmentIds.getOrElse(segIdx) { "" }
                if (segId.isBlank()) return@launch

                _uiState.value = s.copy(status = S.savingStopChange(lang()))

                val isBinis = s.changeStopMode == "binis"

                // stopNames artık tüm hattı kapsıyor; seg.stopFromIdx / stopToIdx
                // mevcut biniş-iniş pozisyonlarını tutuyor.
                val currentFromIdx = if (isBinis) maxOf(0, selectedIdx) else seg.stopFromIdx
                val currentToIdx   = if (!isBinis) maxOf(0, selectedIdx) else
                    seg.stopToIdx.takeIf { it >= 0 } ?: maxOf(0, seg.stopNames.size - 1)
                val newStopCount = kotlin.math.abs(currentToIdx - currentFromIdx)

                var newDistanceKm = seg.distanceKm
                if (seg.journeyRef.isNotBlank()) {
                    val newFrom = if (isBinis) newStopName else seg.fromStop
                    val newTo   = if (!isBinis) newStopName else seg.toStop
                    try {
                        val journeySegment = withContext(Dispatchers.IO) {
                            RmvApiService.fetchJourneyStops(seg.journeyRef, newFrom, newTo)
                        }
                        // fetchJourneyStops coords = fromIdx→toIdx arası, mesafe için doğru
                        if (journeySegment.coords.size >= 2) {
                            newDistanceKm = withContext(Dispatchers.IO) {
                                if (seg.typeTr == VehicleType.BUS.key) RmvApiService.calculateDistanceORS(journeySegment.coords)
                                else RmvApiService.calculateDistanceRail(journeySegment.coords, journeySegment.allStopCoords, journeySegment.fromIdx, journeySegment.toIdx)
                            }
                        }
                    } catch (_: Exception) { }
                }

                val newMesafe = if (newDistanceKm > 0) String.format(Locale.US, "%.2f km", newDistanceKm) else ""
                val newDurakSayisi = if (newStopCount > 0) newStopCount.toString() else ""

                val ok = repository.updateStops(
                    id = segId,
                    binisDuragi = if (isBinis) newStopName else null,
                    binisTime   = if (isBinis) newTime else null,
                    inisDuragi  = if (!isBinis) newStopName else null,
                    inisTime    = if (!isBinis) newTime else null,
                    mesafe      = newMesafe,
                    durakSayisi = newDurakSayisi
                )

                if (ok) {
                    val updatedSeg = if (isBinis)
                        seg.copy(
                            fromStop = newStopName, dep = newTime.ifBlank { seg.dep },
                            distanceKm = newDistanceKm, stopCount = newStopCount,
                            fromStopId = "",
                            stopFromIdx = currentFromIdx
                        )
                    else
                        seg.copy(
                            toStop = newStopName, arr = newTime.ifBlank { seg.arr },
                            distanceKm = newDistanceKm, stopCount = newStopCount,
                            toStopId = "",
                            stopToIdx = currentToIdx
                        )
                    val newSegs = trip.segments.toMutableList()
                    newSegs[segIdx] = updatedSeg
                    val newTrip = trip.copy(
                        segments = newSegs,
                        overallDep = newSegs.first().dep,
                        overallArr = newSegs.last().arr
                    )
                    _uiState.value = _uiState.value.copy(
                        trip = newTrip,
                        changeStopSegIdx = -1,
                        changeStopMode = "",
                        changeStopSelectedIdx = -1,
                        status = S.stopUpdated(lang())
                    )
                } else {
                    _uiState.value = _uiState.value.copy(status = S.stopUpdateFailed(lang()))
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

    /** Backward-compat — Manuel mod toggle */
    fun setManualMode(isManual: Boolean) {
        _uiState.value = _uiState.value.copy(
            mode = if (isManual) LogMode.MANUAL else LogMode.AUTO
        )
    }

    fun setManualTypeMenuOpen(open: Boolean) {
        _uiState.value = _uiState.value.copy(manual = _uiState.value.manual.copy(typeMenuOpen = open))
    }

    fun setManualWeatherMenuOpen(open: Boolean) {
        _uiState.value = _uiState.value.copy(manual = _uiState.value.manual.copy(weatherMenuOpen = open))
    }

    fun updateManualField(field: String, value: String) {
        val m = _uiState.value.manual
        _uiState.value = _uiState.value.copy(manual = when (field) {
            "type"         -> m.copy(typeTr = value, typeMenuOpen = false)
            "line"         -> m.copy(line = value)
            "direction"    -> m.copy(direction = value)
            "boardingStop" -> m.copy(boardingStop = value)
            "alightingStop"-> m.copy(alightingStop = value)
            "plannedDep"   -> m.copy(plannedDep = value.filter { it.isDigit() }.take(4))
            "actualDep"    -> m.copy(actualDep = value.filter { it.isDigit() }.take(4))
            "plannedArr"   -> m.copy(plannedArr = value.filter { it.isDigit() }.take(4))
            "actualArr"    -> m.copy(actualArr = value.filter { it.isDigit() }.take(4))
            "distance"     -> m.copy(distance = value)
            "stopCount"    -> m.copy(stopCount = value)
            "weather"      -> m.copy(weather = value, weatherMenuOpen = false)
            "note"         -> m.copy(note = value)
            else           -> m
        })
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
                val m = s.manual
                _uiState.value = s.copy(status = S.statusSavingSheets(lang()))

                if (m.boardingStop.isBlank() || m.alightingStop.isBlank() || m.line.isBlank()) {
                    throw IllegalStateException("Hat, biniş ve iniş durakları zorunludur.")
                }

                fun formatTime(t: String): String {
                    if (t.isBlank()) return ""
                    val padded = t.padStart(4, '0')
                    return "${padded.substring(0, 2)}:${padded.substring(2, 4)}"
                }

                val distanceKm = m.distance.replace(",", ".").toDoubleOrNull() ?: 0.0
                val stCount = m.stopCount.toIntOrNull() ?: 0

                val segment = com.example.toplutasima.model.Segment(
                    typeTr = m.typeTr, line = m.line, direction = m.direction,
                    fromStop = m.boardingStop, toStop = m.alightingStop,
                    dep = formatTime(m.plannedDep), arr = formatTime(m.plannedArr),
                    distanceKm = distanceKm, stopCount = stCount
                )

                if (s.segmentIds.isEmpty()) {
                    val newId = UUID.randomUUID().toString()
                    val ok = repository.saveSegment(newId, s.date, segment, m.weather, m.oturabildim, m.biletKontrolu, m.note)

                    if (ok) {
                        val actDep = formatTime(m.actualDep)
                        val actArr = formatTime(m.actualArr)
                        if (actDep.isNotBlank() || actArr.isNotBlank()) {
                            repository.updateActual(newId, actDep.ifBlank { null }, actArr.ifBlank { null })
                        }
                    }
                    if (!ok) throw Exception(S.errorSaveFailed(lang()))
                    prefs.edit().putString("first_id", newId).putString("last_id", newId).apply()
                    _uiState.value = _uiState.value.copy(firstSavedId = newId, lastSavedId = newId, segmentIds = listOf(newId), status = S.statusSaved(lang()))
                } else {
                    val docId = s.segmentIds.first()
                    val actDep = formatTime(m.actualDep)
                    val actArr = formatTime(m.actualArr)

                    // Bug 2 fix: türetilen alanları (gecikme, süreler) client-side hesapla ve
                    // updateMap'e ekle. updateExistingRecord ham alan yazdığından bunları
                    // yeniden hesaplamaz; burada açıkça eklemek gerekir.
                    val gecikme = FirestoreService.computeGecikme(
                        segment.dep.ifBlank { null }, actDep.ifBlank { null }
                    )
                    val planlananSure = FirestoreService.computeYolSuresi(
                        segment.dep.ifBlank { null }, segment.arr.ifBlank { null }
                    )
                    val gercekSure = FirestoreService.computeYolSuresi(
                        actDep.ifBlank { null }, actArr.ifBlank { null }
                    )

                    val mesafeText = if (distanceKm > 0) String.format(Locale.US, "%.2f km", distanceKm) else "Bilinmiyor"
                    val updateMap = linkedMapOf<String, Any>(
                        "tur" to m.typeTr,
                        "hat" to m.line,
                        "yon" to m.direction,
                        "binisDuragi" to m.boardingStop,
                        "inisDuragi" to m.alightingStop,
                        "planlananBinis" to segment.dep,
                        "planlananInis" to segment.arr,
                        "gercekBinis" to actDep,
                        "gercekInis" to actArr,
                        "gecikme" to gecikme,
                        "planlananYolSuresi" to planlananSure,
                        "gercekYolSuresi" to gercekSure,
                        "mesafe" to mesafeText,
                        "durakSayisi" to if (stCount > 0) stCount.toString() else "Bilinmiyor",
                        "havaDurumu" to m.weather,
                        "oturabildimMi" to SeatingStatus.fromBoolean(m.oturabildim).key,
                        "biletKontrolü" to TicketStatus.fromBoolean(m.biletKontrolu).key,
                        "not" to m.note
                    )
                    updateMap.putAll(FirestoreService.calculatedDistanceFields(distanceKm, resetRmvDistance = true))
                    val ok = repository.updateExistingRecord(docId, updateMap)
                    if (!ok) throw Exception(S.errorSaveFailed(lang()))
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

        fun formatTime(t: String) = t.replace(":", "").take(4)

        if (event.isBoarding) {
            _uiState.value = s.copy(
                customBindimTime = formatTime(event.timestamp),
                status = "Bindim ✅ (${event.timestamp}) — bildirimden"
            )
        } else {
            val isLastSeg = segIdx >= (s.trip?.segments?.size ?: 1) - 1
            _uiState.value = s.copy(
                customIndimTime = formatTime(event.timestamp),
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
                val record = repository.fetchRecord(selectedId) ?: return@launch
                val latest = _uiState.value
                val latestSelectedId = latest.segmentIds.getOrNull(latest.selectedSegmentIndex).orEmpty()
                if (latestSelectedId != selectedId) return@launch
                val gercekBinis = record["gercekBinis"]?.toString().orEmpty()
                val gercekInis = record["gercekInis"]?.toString().orEmpty()
                fun toDigits(t: String) = t.replace(":", "").take(4)
                val newBindim = if (gercekBinis.isNotBlank()) toDigits(gercekBinis)
                                else latest.customBindimTime
                val newIndim = if (gercekInis.isNotBlank()) toDigits(gercekInis)
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
        tripDetailJob?.cancel()
        super.onCleared()
    }
}
