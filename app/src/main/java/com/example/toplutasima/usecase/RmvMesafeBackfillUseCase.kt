package com.example.toplutasima.usecase

import com.example.toplutasima.data.local.entity.TripEntity
import com.example.toplutasima.data.repository.LocalTripRepository
import com.example.toplutasima.network.rmv.RmvDistanceService
import com.example.toplutasima.network.rmv.RmvSegmentDetailsService
import com.example.toplutasima.network.rmv.SegmentDistanceResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

data class BackfillResult(
    val updated: Int,
    val failed: Int
)

class RmvMesafeBackfillUseCase(
    private val tripRepository: LocalTripRepository,
    private val segmentDetailsService: RmvSegmentDetailsService = RmvSegmentDetailsService(),
    private val distanceService: RmvDistanceService = RmvDistanceService()
) {
    suspend fun run(onProgress: (current: Int, total: Int) -> Unit): BackfillResult =
        withContext(Dispatchers.IO) {
            val tripIds = tripRepository.getTripsNeedingMesafeBackfill().map { it.id }
            val total = tripIds.size
            var updated = 0
            var failed = 0

            if (total == 0) {
                onProgress(0, 0)
                return@withContext BackfillResult(updated, failed)
            }

            for ((index, tripId) in tripIds.withIndex()) {
                val current = index + 1
                val freshTrip = tripRepository.getTripById(tripId)
                if (freshTrip != null && freshTrip.needsMesafeBackfill()) {
                    try {
                        if (backfillTrip(freshTrip)) {
                            updated++
                        } else {
                            failed++
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        failed++
                        runCatching { markTripFailed(freshTrip) }
                    }
                }

                onProgress(current, total)
                if (!currentCoroutineContext().isActive) break
                if (current < total) delay(400)
            }

            BackfillResult(updated, failed)
        }

    private suspend fun backfillTrip(trip: TripEntity): Boolean {
        val journeyRef = trip.journeyRef.required("journeyRef")
        val fromStop = trip.binisDuragi.required("binisDuragi")
        val toStop = trip.inisDuragi.required("inisDuragi")

        val journeySegment = segmentDetailsService.fetchJourneyStops(
            ref = journeyRef,
            fromStop = fromStop,
            toStop = toStop,
            boardingDepTime = trip.planlananBinis.orEmpty(),
            fromId = trip.fromStopId.orEmpty(),
            toId = trip.toStopId.orEmpty()
        )
        val coords = journeySegment.coords
        if (coords.size < 2) error("Not enough journey coordinates")

        val polyDistanceKm = distanceService.calculateDistanceFromPoly(
            polylineCoords = journeySegment.polylineCoords,
            fromExact = coords.first(),
            toExact = coords.last()
        )
        val fields = TransitRecordCalculations.calculatedDistanceFields(
            SegmentDistanceResult(
                apiDistanceKm = existingOrsDistanceKm(trip),
                polyDistanceKm = polyDistanceKm
            )
        )

        tripRepository.updateTripMesafeBackfill(trip, fields)
        return polyDistanceKm != null && polyDistanceKm > 0.0
    }

    private suspend fun markTripFailed(trip: TripEntity) {
        val fields = TransitRecordCalculations.calculatedDistanceFields(
            SegmentDistanceResult(
                apiDistanceKm = existingOrsDistanceKm(trip),
                polyDistanceKm = null
            )
        )
        tripRepository.updateTripMesafeBackfill(trip, fields)
    }

    private fun existingOrsDistanceKm(trip: TripEntity): Double? =
        trip.orsMesafeKm ?: TransitRecordCalculations.parseDistanceKm(trip.mesafe)

    private fun TripEntity.needsMesafeBackfill(): Boolean =
        when (rmvMesafeDurumu) {
            null,
            "",
            TransitRecordCalculations.RMV_DISTANCE_PENDING,
            TransitRecordCalculations.RMV_DISTANCE_FAILED -> true
            TransitRecordCalculations.RMV_DISTANCE_READY -> rmvMesafeKm == null
            else -> false
        }

    private fun String?.required(fieldName: String): String =
        this?.takeIf { it.isNotBlank() } ?: error("Missing $fieldName")
}
