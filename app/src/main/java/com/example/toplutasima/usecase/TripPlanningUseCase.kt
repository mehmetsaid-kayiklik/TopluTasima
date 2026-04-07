package com.example.toplutasima.usecase

import com.example.toplutasima.model.Departure
import com.example.toplutasima.model.Segment
import com.example.toplutasima.model.TripResult
import com.example.toplutasima.model.VehicleType
import com.example.toplutasima.network.RmvApiService
import com.example.toplutasima.network.StopNameUtils
import com.example.toplutasima.repository.TripRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalTime

/**
 * Domain katmanı: Sefer planlama iş mantığını ViewModel'den ayırır.
 * Seçilen bir kalkışı alır ve tam seyahat planını (segmentler + süreler) döner.
 */
class TripPlanningUseCase(private val repository: TripRepository) {

    data class PlanInput(
        val dep: Departure,
        val fromId: String,
        val toId: String,
        val from: String,
        val to: String,
        val date: String
    )

    /** Ana giriş noktası: [PlanInput] alır, [TripResult] döner. */
    suspend fun plan(input: PlanInput): TripResult {
        val dep = input.dep

        // 1. Araç yolculuğunu ID ile çek (kesin durak sırasını al)
        val rawJourney = withContext(Dispatchers.IO) {
            RmvApiService.fetchJourneyStops(
                dep.journeyDetailRef, input.from, input.to,
                dep.time, input.fromId, input.toId
            )
        }
        if (rawJourney.coords.isEmpty()) {
            throw IllegalStateException("Segment verisi bulunamadı")
        }

        // 2. Yolculuğu gerçek biniş durağından kes
        val boardingTime = dep.time.take(5)
        val exactJourney = if (
            rawJourney.stopTimes.firstOrNull()?.take(5) != boardingTime &&
            boardingTime.isNotBlank()
        ) {
            val boardIdx = rawJourney.stopTimes.indexOfFirst { it.take(5) == boardingTime }
            if (boardIdx > 0) {
                RmvApiService.JourneySegment(
                    stopCount = maxOf(0, rawJourney.stopNames.size - boardIdx - 1),
                    coords = rawJourney.coords.drop(boardIdx),
                    stopNames = rawJourney.stopNames.drop(boardIdx),
                    stopTimes = rawJourney.stopTimes.drop(boardIdx)
                )
            } else rawJourney
        } else rawJourney

        // 3. Aktarmasız mı yoksa aktarmalı mı?
        val isDirect = exactJourney.stopNames.any { StopNameUtils.fuzzyMatch(input.to, it) }
        val segments = buildSegments(dep, input, exactJourney, isDirect)

        // 4. Toplam süreyi hesapla
        val depTime = segments.first().dep
        val arrTime = segments.last().arr
        val durationMin = calcDurationMin(depTime, arrTime)

        return TripResult(
            segments = segments,
            overallDep = depTime,
            overallArr = arrTime,
            durationMin = durationMin
        )
    }

    // ── Segment oluşturma ──────────────────────────────────────────────────────

    private suspend fun buildSegments(
        dep: Departure,
        input: PlanInput,
        exactJourney: RmvApiService.JourneySegment,
        isDirect: Boolean
    ): List<Segment> {
        val segments = mutableListOf<Segment>()

        if (isDirect) {
            segments += buildDirectSegment(dep, input, exactJourney)
        } else {
            val apiDate = RmvApiService.convertToApiDate(input.date)
            val guideTrip = runCatching {
                repository.fetchTripBasic(input.fromId, input.toId, apiDate, dep.time, preferredLine = dep.line)
            }.getOrNull()

            if (guideTrip != null && guideTrip.segments.size > 1) {
                segments += buildTransferSegments(dep, input, exactJourney, guideTrip.segments)
            } else {
                segments += buildFallbackSegment(dep, input, exactJourney)
            }
        }
        return segments
    }

    private suspend fun buildDirectSegment(
        dep: Departure,
        input: PlanInput,
        exactJourney: RmvApiService.JourneySegment
    ): Segment {
        val destIdx = exactJourney.stopNames.indexOfFirst { StopNameUtils.fuzzyMatch(input.to, it) }
        val slicedNames = if (destIdx != -1) exactJourney.stopNames.take(destIdx + 1) else exactJourney.stopNames
        val slicedTimes = if (destIdx != -1) exactJourney.stopTimes.take(destIdx + 1) else exactJourney.stopTimes
        val slicedCoords = if (destIdx != -1) exactJourney.coords.take(destIdx + 1) else exactJourney.coords

        val distanceKm = calcDistance(dep.typeTr, slicedCoords)
        return Segment(
            typeTr = dep.typeTr, line = dep.line, direction = dep.direction,
            fromStop = slicedNames.firstOrNull() ?: input.from,
            toStop = slicedNames.lastOrNull() ?: input.to,
            dep = slicedTimes.firstOrNull() ?: dep.time,
            arr = slicedTimes.lastOrNull() ?: "",
            distanceKm = distanceKm,
            stopCount = maxOf(0, slicedNames.size - 1),
            stopNames = slicedNames,
            stopTimes = slicedTimes,
            journeyRef = dep.journeyDetailRef
        )
    }

    private suspend fun buildTransferSegments(
        dep: Departure,
        input: PlanInput,
        exactJourney: RmvApiService.JourneySegment,
        guideSegments: List<Segment>
    ): List<Segment> {
        val transferStop = guideSegments[0].toStop
        val transferIdx = exactJourney.stopNames.indexOfFirst {
            it.contains(transferStop, ignoreCase = true) || transferStop.contains(it, ignoreCase = true)
        }

        if (transferIdx == -1) return listOf(buildFallbackSegment(dep, input, exactJourney))

        val leg1Names = exactJourney.stopNames.take(transferIdx + 1)
        val leg1Times = exactJourney.stopTimes.take(transferIdx + 1)
        val leg1Coords = exactJourney.coords.take(transferIdx + 1)
        val leg1Distance = calcDistance(dep.typeTr, leg1Coords)

        val result = mutableListOf<Segment>()
        result += Segment(
            typeTr = dep.typeTr, line = dep.line, direction = dep.direction,
            fromStop = leg1Names.first(), toStop = leg1Names.last(),
            dep = leg1Times.first(), arr = leg1Times.last(),
            distanceKm = leg1Distance,
            stopCount = maxOf(0, leg1Names.size - 1),
            stopNames = leg1Names, stopTimes = leg1Times,
            journeyRef = dep.journeyDetailRef
        )
        // Sonraki bacakları rehber seferden ekle
        for (i in 1 until guideSegments.size) result += guideSegments[i]
        return result
    }

    private suspend fun buildFallbackSegment(
        dep: Departure,
        input: PlanInput,
        exactJourney: RmvApiService.JourneySegment
    ): Segment {
        val distanceKm = calcDistance(dep.typeTr, exactJourney.coords)
        return Segment(
            typeTr = dep.typeTr, line = dep.line, direction = dep.direction,
            fromStop = exactJourney.stopNames.firstOrNull() ?: input.from,
            toStop = input.to,
            dep = exactJourney.stopTimes.firstOrNull() ?: dep.time,
            arr = "",
            distanceKm = distanceKm,
            stopCount = exactJourney.stopCount,
            stopNames = exactJourney.stopNames,
            stopTimes = exactJourney.stopTimes,
            journeyRef = dep.journeyDetailRef
        )
    }

    // ── Yardımcılar ───────────────────────────────────────────────────────────

    private suspend fun calcDistance(typeTr: String, coords: List<Pair<Double, Double>>): Double {
        if (coords.size < 2) return 0.0
        return withContext(Dispatchers.IO) {
            if (typeTr == VehicleType.BUS.key) RmvApiService.calculateDistanceORS(coords)
            else RmvApiService.calculateDistanceRail(coords)
        }
    }

    private fun calcDurationMin(dep: String, arr: String): Int {
        if (dep.isBlank() || arr.isBlank()) return 0
        return try {
            val d = LocalTime.parse(dep.take(5))
            val a = LocalTime.parse(arr.take(5))
            var diff = java.time.Duration.between(d, a).toMinutes().toInt()
            if (diff < 0) diff += 24 * 60
            diff
        } catch (_: Exception) { 0 }
    }
}
