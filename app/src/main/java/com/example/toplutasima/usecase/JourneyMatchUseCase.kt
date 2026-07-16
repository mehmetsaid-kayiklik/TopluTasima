package com.example.toplutasima.usecase

import com.example.toplutasima.model.JourneyMatchCandidate
import com.example.toplutasima.network.RmvApiService
import com.example.toplutasima.viewmodel.rmvlog.RmvLogUiState
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class JourneyMatchUseCase {
    data class MatchStart(
        val state: RmvLogUiState,
        val apiDate: String,
        val searchTime: String,
        val shouldStartService: Boolean
    )

    data class JourneySelection(
        val state: RmvLogUiState,
        val departureIndex: Int?
    )

    fun matchJourney(state: RmvLogUiState, hasLocationPermission: Boolean): MatchStart {
        if (!hasLocationPermission) {
            val message = "Konum izni gerekli"
            return MatchStart(
                state = state.copy(journeyMatchMessage = message, status = message),
                apiDate = "",
                searchTime = "",
                shouldStartService = false
            )
        }
        val apiDate = runCatching { RmvApiService.convertToApiDate(state.date) }.getOrDefault("")
        val searchTime = if (state.time.isNotBlank()) {
            RmvApiService.formatTimeDigits(state.time)
        } else {
            LocalTime.now().withSecond(0).withNano(0).format(DateTimeFormatter.ofPattern("HH:mm"))
        }
        val message = "GPS izi aliniyor..."
        return MatchStart(
            state = state.copy(
                journeyMatchLoading = true,
                journeyMatchCandidates = emptyList(),
                journeyMatchMessage = message,
                status = message
            ),
            apiDate = apiDate,
            searchTime = searchTime,
            shouldStartService = true
        )
    }

    fun selectJourneyMatch(state: RmvLogUiState, candidate: JourneyMatchCandidate): JourneySelection {
        val matchingIndex = state.departures.indexOfFirst {
            RmvApiService.normalizeLineForDisplay(it.line) ==
                RmvApiService.normalizeLineForDisplay(candidate.line)
        }.takeIf { it >= 0 }
        val message = "GPS eslesmesi onaylandi: ${candidate.line}"
        return JourneySelection(
            state = state.copy(
                journeyMatchCandidates = emptyList(),
                journeyMatchMessage = message,
                status = message
            ),
            departureIndex = matchingIndex
        )
    }
}
