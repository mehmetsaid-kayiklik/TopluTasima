package com.example.toplutasima.viewmodel.rmvlog

import com.example.toplutasima.model.Departure
import com.example.toplutasima.model.JourneyMatchCandidate
import com.example.toplutasima.model.StopOption
import com.example.toplutasima.model.TransitAlert
import com.example.toplutasima.model.TripResult
import com.example.toplutasima.model.VehicleType

enum class LogMode { AUTO, MANUAL }

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
    val note: String = "",
    val profileId: String = "",
    val seatmateNote: String = ""
)

data class RmvLogUiState(
    val from: String = "",
    val to: String = "",
    val fromId: String = "",
    val toId: String = "",
    val fromOptions: List<StopOption> = emptyList(),
    val toOptions: List<StopOption> = emptyList(),
    val fromMenuOpen: Boolean = false,
    val toMenuOpen: Boolean = false,
    val isEditingTimes: Boolean = false,
    val date: String = "",
    val time: String = "",
    val departures: List<Departure> = emptyList(),
    val selectedDeparture: Departure? = null,
    val trip: TripResult? = null,
    val transitAlerts: List<TransitAlert> = emptyList(),
    val transitAlertsLoading: Boolean = false,
    val journeyMatchCandidates: List<JourneyMatchCandidate> = emptyList(),
    val journeyMatchLoading: Boolean = false,
    val journeyMatchMessage: String = "",
    val segmentHavaDurumu: Map<Int, String> = emptyMap(),
    val havaMenuOpen: Boolean = false,
    val segmentOturabildim: Map<Int, Boolean> = emptyMap(),
    val segmentBiletKontrolu: Map<Int, Boolean> = emptyMap(),
    val segmentNote: Map<Int, String> = emptyMap(),
    val segmentProfileId: Map<Int, String> = emptyMap(),
    val segmentSeatmateNote: Map<Int, String> = emptyMap(),
    val activeProfiles: List<com.example.toplutasima.data.local.entity.ProfileEntity> = emptyList(),
    val status: String = "",
    val customBindimTime: String = "",
    val customIndimTime: String = "",
    val firstSavedId: String = "",
    val lastSavedId: String = "",
    val segmentIds: List<String> = emptyList(),
    val selectedSegmentIndex: Int = 0,
    val persistentStops: List<com.example.toplutasima.model.Segment> = emptyList(),
    val changeStopSegIdx: Int = -1,
    val changeStopMode: String = "",
    val changeStopSelectedIdx: Int = -1,
    val changeStopManualText: String = "",
    val isLoadingStopsForEdit: Boolean = false,
    val nearbyStops: List<com.example.toplutasima.network.RmvApiService.NearbyStop> = emptyList(),
    val nearbyLoading: Boolean = false,
    val nearbyHasLoaded: Boolean = false,
    val showAddFavDialog: Boolean = false,
    val addFavStopId: String = "",
    val addFavStopName: String = "",
    val addFavLabel: String = "",
    val addFavUsageType: com.example.toplutasima.model.UsageType = com.example.toplutasima.model.UsageType.BOTH,
    val addFavMessage: String = "",
    val mode: LogMode = LogMode.AUTO,
    val manual: ManualEntryState = ManualEntryState()
) {
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
