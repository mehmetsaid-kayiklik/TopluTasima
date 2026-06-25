package com.example.toplutasima.usecase

import com.example.toplutasima.data.PrefsManager
import com.example.toplutasima.model.Departure
import com.example.toplutasima.model.Segment
import com.example.toplutasima.model.StopOption
import com.example.toplutasima.model.TripResult
import com.example.toplutasima.model.VehicleType
import com.example.toplutasima.network.RmvApiService
import com.example.toplutasima.network.rmv.SegmentDistanceResult
import com.example.toplutasima.repository.RmvTripRepository
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.S
import com.example.toplutasima.viewmodel.rmvlog.RmvLogUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class StopSelectionUseCase(
    private val rmvTripRepository: RmvTripRepository,
    private val prefsManager: PrefsManager
) {
    data class StopSearchResult(
        val options: List<StopOption>,
        val fromCache: Boolean
    )

    data class ChangeStopFetchResult(
        val state: RmvLogUiState,
        val openDialog: Boolean
    )

    data class DepartureSelectionResult(
        val trip: TripResult,
        val persistentStops: List<Segment>
    )

    fun showAddFavoriteDialog(state: RmvLogUiState, stopId: String, stopName: String): RmvLogUiState =
        state.copy(
            showAddFavDialog = true,
            addFavStopId = stopId,
            addFavStopName = stopName,
            addFavLabel = "",
            addFavUsageType = com.example.toplutasima.model.UsageType.BOTH,
            addFavMessage = ""
        )

    fun dismissAddFavoriteDialog(state: RmvLogUiState): RmvLogUiState =
        state.copy(showAddFavDialog = false, addFavMessage = "")

    fun updateAddFavLabel(state: RmvLogUiState, label: String): RmvLogUiState =
        state.copy(addFavLabel = label)

    fun updateAddFavUsageType(
        state: RmvLogUiState,
        type: com.example.toplutasima.model.UsageType
    ): RmvLogUiState =
        state.copy(addFavUsageType = type)

    fun addFavorite(state: RmvLogUiState, language: AppLanguage): RmvLogUiState {
        if (state.addFavStopId.isBlank()) return state
        val label = state.addFavLabel.ifBlank { state.addFavStopName }
        prefsManager.addFavorite(
            stopId = state.addFavStopId,
            stopName = state.addFavStopName,
            label = label,
            usageType = state.addFavUsageType
        )
        return state.copy(
            showAddFavDialog = false,
            addFavMessage = S.favAdded(language)
        )
    }

    fun removeFavorite(id: String) {
        prefsManager.removeFavorite(id)
    }

    fun loadFavorites() = prefsManager.favorites

    fun selectNearbyStop(
        state: RmvLogUiState,
        stop: RmvApiService.NearbyStop
    ): RmvLogUiState =
        state.copy(
            from = stop.name,
            fromId = stop.id,
            fromOptions = emptyList(),
            fromMenuOpen = false
        )

    fun selectFavoriteFrom(state: RmvLogUiState, stopId: String, stopName: String): RmvLogUiState =
        state.copy(from = stopName, fromId = stopId, fromOptions = emptyList(), fromMenuOpen = false)

    fun selectFavoriteTo(state: RmvLogUiState, stopId: String, stopName: String): RmvLogUiState =
        state.copy(to = stopName, toId = stopId, toOptions = emptyList(), toMenuOpen = false)

    fun updateFrom(state: RmvLogUiState, value: String): RmvLogUiState =
        state.copy(from = value, fromId = "", fromOptions = emptyList())

    fun updateTo(state: RmvLogUiState, value: String): RmvLogUiState =
        state.copy(to = value, toId = "", toOptions = emptyList())

    fun selectFrom(state: RmvLogUiState, option: StopOption): RmvLogUiState =
        state.copy(from = option.routingName, fromId = option.routingId, fromMenuOpen = false)

    fun selectTo(state: RmvLogUiState, option: StopOption): RmvLogUiState =
        state.copy(to = option.routingName, toId = option.routingId, toMenuOpen = false)

    fun swapFromTo(state: RmvLogUiState): RmvLogUiState =
        state.copy(
            from = state.to,
            fromId = state.toId,
            fromOptions = state.toOptions,
            to = state.from,
            toId = state.fromId,
            toOptions = state.fromOptions
        )

    fun searchNearbyStops(state: RmvLogUiState, stops: List<RmvApiService.NearbyStop>): RmvLogUiState =
        state.copy(nearbyStops = stops, nearbyLoading = false, nearbyHasLoaded = true)

    suspend fun searchStops(query: String, max: Int): StopSearchResult {
        val cached = prefsManager.getCachedStops(query, max)
        if (cached.isNotEmpty()) return StopSearchResult(cached, fromCache = true)
        val options = rmvTripRepository.searchLocations(query, max).map { it.toStopOption() }
        prefsManager.saveStopSearch(query, options)
        return StopSearchResult(options, fromCache = false)
    }

    fun selectDepartureStart(
        state: RmvLogUiState,
        dep: Departure,
        language: AppLanguage
    ): RmvLogUiState =
        state.copy(
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
            status = S.statusFetchingPlan(language)
        )

    suspend fun selectDeparture(
        dep: Departure,
        state: RmvLogUiState,
        tripPlanner: TripPlanningUseCase,
        language: AppLanguage
    ): DepartureSelectionResult {
        if (state.fromId.isBlank() || state.toId.isBlank()) {
            throw IllegalStateException(S.errorSelectFromList(language))
        }
        val finalTrip = tripPlanner.plan(
            TripPlanningUseCase.PlanInput(
                dep = dep,
                fromId = state.fromId,
                toId = state.toId,
                from = state.from,
                to = state.to,
                date = state.date
            )
        )
        val updatedSegments = finalTrip.segments.map { seg ->
            val details = if (seg.stopNames.isNotEmpty()) {
                RmvApiService.SegmentDetails(
                    distanceKm = seg.distanceKm,
                    stopCount = seg.stopCount,
                    stopNames = seg.stopNames,
                    stopTimes = seg.stopTimes,
                    fromIdx = seg.stopFromIdx,
                    toIdx = seg.stopToIdx,
                    toStopLat = seg.toStopLat,
                    toStopLng = seg.toStopLng,
                    distanceResult = SegmentDistanceResult(
                        apiDistanceKm = seg.distanceKm.takeIf { it > 0.0 },
                        polyDistanceKm = seg.polyDistanceKm
                    )
                )
            } else {
                runCatching { rmvTripRepository.fetchSegmentDetails(seg) }
                    .getOrDefault(RmvApiService.SegmentDetails(0.0, 0, emptyList()))
            }
            applySegmentDetails(seg, details)
        }
        return DepartureSelectionResult(
            trip = finalTrip.copy(segments = updatedSegments),
            persistentStops = updatedSegments
        )
    }

    suspend fun fetchStopsForChangeStop(
        state: RmvLogUiState,
        segIdx: Int,
        tripPlanner: TripPlanningUseCase,
        language: AppLanguage
    ): ChangeStopFetchResult {
        val seg = state.trip?.segments?.getOrNull(segIdx)
            ?: return ChangeStopFetchResult(state.copy(isLoadingStopsForEdit = false), false)
        if (seg.stopNames.size > 1) {
            return ChangeStopFetchResult(state.copy(isLoadingStopsForEdit = false), true)
        }

        val fromOpts = rmvTripRepository.searchStops(seg.fromStop.trim(), 3)
        val fromId = fromOpts.firstOrNull()?.id ?: throw Exception(S.errorStopNotFound(language))
        val toOpts = rmvTripRepository.searchStops(seg.toStop.trim(), 3)
        val toId = toOpts.firstOrNull()?.id ?: throw Exception(S.errorStopNotFound(language))
        val apiDate = RmvApiService.convertToApiDate(state.date)
        val searchTime = seg.dep.take(5).ifBlank {
            LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        }
        val deps = rmvTripRepository.fetchDepartures(fromId, toId, apiDate, searchTime)
        val matchingDep = deps.firstOrNull { dep ->
            dep.line.contains(seg.line, ignoreCase = true) || seg.line.contains(dep.line, ignoreCase = true)
        } ?: deps.firstOrNull() ?: throw Exception(S.statusNoDepartures(language))
        val newTrip = tripPlanner.plan(
            TripPlanningUseCase.PlanInput(
                dep = matchingDep,
                fromId = fromId,
                toId = toId,
                from = seg.fromStop,
                to = seg.toStop,
                date = state.date
            )
        )
        val newSeg = newTrip.segments.firstOrNull() ?: throw Exception("Segment bulunamadı")
        val details = runCatching { rmvTripRepository.fetchSegmentDetails(newSeg) }
            .getOrDefault(RmvApiService.SegmentDetails(0.0, 0, emptyList()))
        val stopNames = details.stopNames.ifEmpty { newSeg.stopNames }
        val stopTimes = details.stopTimes.ifEmpty { newSeg.stopTimes }
        if (stopNames.size <= 1) throw Exception(S.errorStopNotFound(language))

        val currentTrip = state.trip ?: return ChangeStopFetchResult(state, false)
        val currentSeg = currentTrip.segments.getOrNull(segIdx) ?: return ChangeStopFetchResult(state, false)
        val detailRangeResolved = details.fromIdx >= 0 && details.toIdx >= 0
        val updatedSeg = currentSeg.copy(
            stopNames = stopNames,
            stopTimes = stopTimes,
            journeyRef = newSeg.journeyRef,
            stopFromIdx = if (detailRangeResolved) details.fromIdx else newSeg.stopFromIdx,
            stopToIdx = if (detailRangeResolved) details.toIdx else newSeg.stopToIdx,
            distanceKm = if (details.distanceKm > 0) details.distanceKm else newSeg.distanceKm,
            polyDistanceKm = details.distanceResult.polyDistanceKm ?: newSeg.polyDistanceKm,
            stopCount = if (details.stopCount > 0) details.stopCount else newSeg.stopCount,
            toStopLat = if (!details.toStopLat.isNaN()) details.toStopLat else newSeg.toStopLat,
            toStopLng = if (!details.toStopLng.isNaN()) details.toStopLng else newSeg.toStopLng
        )
        val newSegments = currentTrip.segments.toMutableList()
        newSegments[segIdx] = updatedSeg
        val newPersistentStops = state.persistentStops.toMutableList().also { stops ->
            if (segIdx in stops.indices) {
                stops[segIdx] = updatedSeg
            }
        }
        return ChangeStopFetchResult(
            state.copy(
                trip = currentTrip.copy(segments = newSegments),
                persistentStops = newPersistentStops,
                fromId = fromId,
                toId = toId,
                isLoadingStopsForEdit = false,
                status = S.statusReady(language)
            ),
            openDialog = true
        )
    }

    suspend fun confirmChangeStop(
        state: RmvLogUiState,
        language: AppLanguage,
        updateStops: suspend (
            id: String,
            binisDuragi: String?,
            binisTime: String?,
            inisDuragi: String?,
            inisTime: String?,
            mesafe: String?,
            durakSayisi: String?,
            distanceResult: SegmentDistanceResult?
        ) -> Boolean
    ): RmvLogUiState {
        val segIdx = state.changeStopSegIdx
        val trip = state.trip ?: return state
        if (segIdx < 0 || segIdx >= trip.segments.size) return state
        val seg = trip.segments[segIdx]
        val selectedIdx = state.changeStopSelectedIdx
        if (seg.stopNames.isNotEmpty() && (selectedIdx < 0 || selectedIdx >= seg.stopNames.size)) return state

        val newStopName = if (seg.stopNames.isNotEmpty()) {
            seg.stopNames[selectedIdx]
        } else {
            state.changeStopManualText.trim().ifBlank { return state }
        }
        val newTime = if (seg.stopNames.isNotEmpty()) seg.stopTimes.getOrElse(selectedIdx) { "" } else ""
        val segId = state.changeStopSegmentId(segIdx)
        if (segId.isBlank()) return state

        val isBinis = state.changeStopMode == "binis"
        val currentFromIdx = if (isBinis) maxOf(0, selectedIdx) else seg.stopFromIdx
        val currentToIdx = if (!isBinis) {
            maxOf(0, selectedIdx)
        } else {
            seg.stopToIdx.takeIf { it >= 0 } ?: maxOf(0, seg.stopNames.size - 1)
        }
        val newStopCount = kotlin.math.abs(currentToIdx - currentFromIdx)
        var newDistanceKm = seg.distanceKm
        var newDistanceResult = SegmentDistanceResult(
            apiDistanceKm = seg.distanceKm.takeIf { it > 0.0 },
            polyDistanceKm = seg.polyDistanceKm
        )
        var calculatedDistanceResult: SegmentDistanceResult? = null
        if (seg.journeyRef.isNotBlank()) {
            val newFrom = if (isBinis) newStopName else seg.fromStop
            val newTo = if (!isBinis) newStopName else seg.toStop
            try {
                val journeySegment = withContext(Dispatchers.IO) {
                    RmvApiService.fetchJourneyStops(seg.journeyRef, newFrom, newTo)
                }
                if (journeySegment.coords.size >= 2) {
                    newDistanceResult = withContext(Dispatchers.IO) {
                        if (seg.typeTr == VehicleType.BUS.key) {
                            RmvApiService.calculateDistanceORS(
                                journeySegment.coords,
                                journeySegment.polylineCoords
                            )
                        } else {
                            RmvApiService.calculateDistanceRail(
                                journeySegment.coords,
                                journeySegment.allStopCoords,
                                journeySegment.fromIdx,
                                journeySegment.toIdx,
                                journeySegment.polylineCoords
                            )
                        }
                    }
                    newDistanceKm = newDistanceResult.apiDistanceKm ?: 0.0
                    calculatedDistanceResult = newDistanceResult
                }
            } catch (_: Exception) {
            }
        }

        val newMesafe = if (newDistanceKm > 0) String.format(Locale.US, "%.2f km", newDistanceKm) else ""
        val newDurakSayisi = if (newStopCount > 0) newStopCount.toString() else ""
        val ok = updateStops(
            segId,
            if (isBinis) newStopName else null,
            if (isBinis) newTime else null,
            if (!isBinis) newStopName else null,
            if (!isBinis) newTime else null,
            newMesafe,
            newDurakSayisi,
            calculatedDistanceResult
        )
        if (!ok) return state.copy(status = S.stopUpdateFailed(language))

        val updatedSeg = if (isBinis) {
            seg.copy(
                fromStop = newStopName,
                dep = newTime.ifBlank { seg.dep },
                distanceKm = newDistanceKm,
                polyDistanceKm = newDistanceResult.polyDistanceKm,
                stopCount = newStopCount,
                fromStopId = "",
                stopFromIdx = currentFromIdx
            )
        } else {
            seg.copy(
                toStop = newStopName,
                arr = newTime.ifBlank { seg.arr },
                distanceKm = newDistanceKm,
                polyDistanceKm = newDistanceResult.polyDistanceKm,
                stopCount = newStopCount,
                toStopId = "",
                stopToIdx = currentToIdx
            )
        }
        val newSegments = trip.segments.toMutableList()
        newSegments[segIdx] = updatedSeg
        val newTrip = trip.copy(
            segments = newSegments,
            overallDep = newSegments.first().dep,
            overallArr = newSegments.last().arr
        )
        val newPersistentStops = state.persistentStops.toMutableList().also { stops ->
            if (segIdx in stops.indices) {
                stops[segIdx] = updatedSeg
            }
        }
        return state.copy(
            trip = newTrip,
            persistentStops = newPersistentStops,
            changeStopSegIdx = -1,
            changeStopMode = "",
            changeStopSelectedIdx = -1,
            status = S.stopUpdated(language)
        )
    }

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

    private fun RmvLogUiState.changeStopSegmentId(segIdx: Int): String {
        val directId = segmentIds.getOrNull(segIdx).orEmpty()
        if (directId.isNotBlank()) return directId

        val segmentCount = trip?.segments?.size ?: 0
        val lastSegIdx = (segmentCount - 1).coerceAtLeast(0)
        return when {
            segmentCount <= 1 && firstSavedId.isNotBlank() -> firstSavedId
            segmentCount <= 1 && lastSavedId.isNotBlank() -> lastSavedId
            segIdx == 0 && firstSavedId.isNotBlank() -> firstSavedId
            segIdx == lastSegIdx && lastSavedId.isNotBlank() -> lastSavedId
            else -> ""
        }
    }
}
