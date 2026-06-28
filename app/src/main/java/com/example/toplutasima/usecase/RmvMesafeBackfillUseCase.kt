package com.example.toplutasima.usecase

import com.example.toplutasima.data.local.entity.TripEntity
import com.example.toplutasima.data.repository.LocalTripRepository
import com.example.toplutasima.diagnostics.RmvMesafeBackfillLogger
import com.example.toplutasima.network.ApiRequestException
import com.example.toplutasima.network.rmv.RmvDistanceService
import com.example.toplutasima.network.rmv.RmvSegmentDetailsService
import com.example.toplutasima.network.rmv.SegmentDistanceResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.EOFException
import java.net.SocketTimeoutException

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
            var processed = 0
            val statusCounts = linkedMapOf<String, Int>()

            if (total == 0) {
                onProgress(0, 0)
                logBackfillSummary(processed, statusCounts)
                return@withContext BackfillResult(updated, failed)
            }

            for ((index, tripId) in tripIds.withIndex()) {
                val current = index + 1
                val freshTrip = tripRepository.getTripById(tripId)
                if (freshTrip != null && freshTrip.needsMesafeBackfill()) {
                    processed++
                    try {
                        val status = backfillTrip(freshTrip)
                        statusCounts.increment(status)
                        if (status.isSuccessfulBackfillStatus()) updated++ else failed++
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val failureStatus = e.toMesafeFailureStatus()
                        val status = if (failureStatus.isReferenceFailure()) {
                            logBackfillFailure(freshTrip, failureStatus, e)
                            runCatching {
                                tripRepository.updateTripMesafeBackfill(
                                    freshTrip,
                                    TransitRecordCalculations.polyUnavailableDistanceFields()
                                )
                            }
                            RmvMesafeBackfillLogger.log(
                                LOG_TAG,
                                "tripId=${freshTrip.id} docId=${freshTrip.firestoreDocId.orEmpty()} " +
                                    "status=${TransitRecordCalculations.RMV_DISTANCE_POLY_UNAVAILABLE} " +
                                    "reason=$failureStatus"
                            )
                            TransitRecordCalculations.RMV_DISTANCE_POLY_UNAVAILABLE
                        } else {
                            logBackfillFailure(freshTrip, failureStatus, e)
                            runCatching { markTripFailed(freshTrip, failureStatus) }
                            failureStatus
                        }
                        statusCounts.increment(status)
                        if (status.isSuccessfulBackfillStatus()) updated++ else failed++
                    }
                }

                onProgress(current, total)
                if (!currentCoroutineContext().isActive) break
                if (current < total) delay(400)
            }

            logBackfillSummary(processed, statusCounts)
            BackfillResult(updated, failed)
        }

    suspend fun resetAllMesafeBackfillState(): Int =
        withContext(Dispatchers.IO) {
            val resetCount = tripRepository.resetAllMesafeBackfillState()
            RmvMesafeBackfillLogger.log(
                RESET_LOG_TAG,
                "Tüm RMV mesafe hesapları sıfırlandı: resetCount=$resetCount"
            )
            resetCount
        }

    suspend fun cleanupFallbackDistances(): Int =
        withContext(Dispatchers.IO) {
            val cleanupCount = tripRepository.cleanupRmvFallbackDistances()
            RmvMesafeBackfillLogger.log(
                FALLBACK_CLEANUP_LOG_TAG,
                "ORS fallback RMV mesafe kayıtları düzeltildi: cleanupCount=$cleanupCount"
            )
            cleanupCount
        }

    private suspend fun backfillTrip(trip: TripEntity): String {
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
        if (coords.size < 2) {
            throw RmvMesafeNoResultException(
                "No result from HAFAS journeyDetail: coords=${coords.size} " +
                    "polylineCoords=${journeySegment.polylineCoords.size}"
            )
        }

        val polyDistanceKm = distanceService.calculateDistanceFromPoly(
            polylineCoords = journeySegment.polylineCoords,
            fromExact = coords.first(),
            toExact = coords.last()
        )
        if (polyDistanceKm == null || polyDistanceKm <= 0.0) {
            val status = TransitRecordCalculations.RMV_DISTANCE_FAILED_NO_RESULT
            val fields = failedDistanceFields(trip, status)
            tripRepository.updateTripMesafeBackfill(trip, fields)
            RmvMesafeBackfillLogger.log(
                LOG_TAG,
                "tripId=${trip.id} status=$status HAFAS polyline distance empty " +
                    "coords=${coords.size} polylineCoords=${journeySegment.polylineCoords.size}"
            )
            return status
        }

        val fields = TransitRecordCalculations.calculatedDistanceFields(
            SegmentDistanceResult(
                apiDistanceKm = existingOrsDistanceKm(trip),
                polyDistanceKm = polyDistanceKm
            )
        )

        tripRepository.updateTripMesafeBackfill(trip, fields)
        return TransitRecordCalculations.RMV_DISTANCE_READY
    }

    private suspend fun markTripFailed(trip: TripEntity, failureStatus: String) {
        tripRepository.updateTripMesafeBackfill(trip, failedDistanceFields(trip, failureStatus))
    }

    private fun failedDistanceFields(
        trip: TripEntity,
        failureStatus: String
    ): LinkedHashMap<String, Any> =
        TransitRecordCalculations.calculatedDistanceFields(
            SegmentDistanceResult(
                apiDistanceKm = existingOrsDistanceKm(trip),
                polyDistanceKm = null
            )
        ).apply {
            this[TransitRecordCalculations.FIELD_RMV_DISTANCE_STATUS] = failureStatus
        }

    private fun existingOrsDistanceKm(trip: TripEntity): Double? =
        trip.orsMesafeKm ?: TransitRecordCalculations.parseDistanceKm(trip.mesafe)

    private fun TripEntity.needsMesafeBackfill(): Boolean =
        when (rmvMesafeDurumu) {
            null,
            "",
            TransitRecordCalculations.RMV_DISTANCE_PENDING,
            TransitRecordCalculations.RMV_DISTANCE_FAILED_RATE_LIMIT,
            TransitRecordCalculations.RMV_DISTANCE_FAILED_TIMEOUT,
            TransitRecordCalculations.RMV_DISTANCE_FAILED_NO_RESULT -> true
            else -> false
        }

    private fun String?.required(fieldName: String): String =
        this?.takeIf { it.isNotBlank() } ?: error("Missing $fieldName")

    private fun Exception.toMesafeFailureStatus(): String = when {
        this is IllegalStateException && message == "Missing journeyRef" ->
            TransitRecordCalculations.RMV_DISTANCE_MISSING_REFERENCE
        this is ApiRequestException && isSvcParamInvalid ->
            TransitRecordCalculations.RMV_DISTANCE_INVALID_REFERENCE
        this is ApiRequestException && isRateLimited ->
            TransitRecordCalculations.RMV_DISTANCE_FAILED_RATE_LIMIT
        hasCause { it is SocketTimeoutException || it.isTimeoutLike() } ->
            TransitRecordCalculations.RMV_DISTANCE_FAILED_TIMEOUT
        isNoResultFailure() ->
            TransitRecordCalculations.RMV_DISTANCE_FAILED_NO_RESULT
        else ->
            TransitRecordCalculations.RMV_DISTANCE_FAILED_PARSE_EXCEPTION
    }

    private fun Throwable.isNoResultFailure(): Boolean =
        this is RmvMesafeNoResultException || hasCause { throwable ->
            throwable is EOFException ||
                throwable.message.orEmpty().contains("no result", ignoreCase = true) ||
                throwable.message.orEmpty().contains("empty response", ignoreCase = true) ||
                throwable.message.orEmpty().contains("not enough journey coordinates", ignoreCase = true) ||
                throwable.message.orEmpty().contains("unexpected end", ignoreCase = true) ||
                throwable.message.orEmpty().contains("end of input", ignoreCase = true)
        }

    private fun Throwable.isTimeoutLike(): Boolean =
        this::class.java.simpleName.contains("Timeout", ignoreCase = true) ||
            message.orEmpty().contains("timeout", ignoreCase = true) ||
            message.orEmpty().contains("timed out", ignoreCase = true)

    private fun Throwable.hasCause(matches: (Throwable) -> Boolean): Boolean {
        val seen = mutableSetOf<Throwable>()
        var current: Throwable? = this
        while (current != null && seen.add(current)) {
            if (matches(current)) return true
            current = current.cause
        }
        return false
    }

    private fun String.isReferenceFailure(): Boolean =
        this == TransitRecordCalculations.RMV_DISTANCE_MISSING_REFERENCE ||
            this == TransitRecordCalculations.RMV_DISTANCE_INVALID_REFERENCE

    private fun String.isSuccessfulBackfillStatus(): Boolean =
        this == TransitRecordCalculations.RMV_DISTANCE_READY

    private fun MutableMap<String, Int>.increment(key: String) {
        this[key] = (this[key] ?: 0) + 1
    }

    private fun logBackfillSummary(
        processed: Int,
        statusCounts: Map<String, Int>
    ) {
        val reportedStatuses = setOf(
            TransitRecordCalculations.RMV_DISTANCE_READY,
            TransitRecordCalculations.RMV_DISTANCE_POLY_UNAVAILABLE,
            TransitRecordCalculations.RMV_DISTANCE_FAILED_RATE_LIMIT,
            TransitRecordCalculations.RMV_DISTANCE_FAILED_TIMEOUT,
            TransitRecordCalculations.RMV_DISTANCE_FAILED_NO_RESULT,
            TransitRecordCalculations.RMV_DISTANCE_FAILED_PARSE_EXCEPTION
        )
        val otherStatuses = statusCounts
            .filterKeys { it !in reportedStatuses }
            .filterValues { it > 0 }

        val summary = buildString {
            appendLine("=== Backfill Özeti ===")
            appendLine("Toplam islenen: $processed")
            appendLine("hazir (HAFAS poly): ${statusCounts.countOf(TransitRecordCalculations.RMV_DISTANCE_READY)}")
            appendLine("poly_yok: ${statusCounts.countOf(TransitRecordCalculations.RMV_DISTANCE_POLY_UNAVAILABLE)}")
            appendLine("hata_rate_limit_429: ${statusCounts.countOf(TransitRecordCalculations.RMV_DISTANCE_FAILED_RATE_LIMIT)}")
            appendLine("hata_timeout: ${statusCounts.countOf(TransitRecordCalculations.RMV_DISTANCE_FAILED_TIMEOUT)}")
            appendLine("hata_sonuc_yok: ${statusCounts.countOf(TransitRecordCalculations.RMV_DISTANCE_FAILED_NO_RESULT)}")
            appendLine("hata_parse_exception: ${statusCounts.countOf(TransitRecordCalculations.RMV_DISTANCE_FAILED_PARSE_EXCEPTION)}")
            if (otherStatuses.isNotEmpty()) {
                appendLine("diger durumlar:")
                otherStatuses.forEach { (status, count) ->
                    appendLine("  $status: $count")
                }
            }
            append("======================")
        }
        RmvMesafeBackfillLogger.log(SUMMARY_LOG_TAG, summary)
    }

    private fun Map<String, Int>.countOf(status: String): Int = this[status] ?: 0

    private fun logBackfillFailure(trip: TripEntity, failureStatus: String, error: Exception) {
        val details = buildString {
            append("tripId=${trip.id} docId=${trip.firestoreDocId.orEmpty()} status=$failureStatus ")
            append("exception=${error::class.java.name}: ${error.message.orEmpty()}")
            if (error is ApiRequestException) {
                append(" endpoint=${error.endpoint} requestId=${error.requestId}")
                error.statusCode?.let { append(" http=$it") }
                error.retryAfterSeconds?.let { append(" retryAfter=${it}s") }
                error.errorCode?.let { append(" errorCode=$it") }
                if (error.bodyPreview.isNotBlank()) {
                    append(" bodyPreview=${error.bodyPreview}")
                }
            }
        }
        RmvMesafeBackfillLogger.log(LOG_TAG, details)
    }

    private class RmvMesafeNoResultException(message: String) : IllegalStateException(message)

    private companion object {
        const val LOG_TAG = "RmvMesafeBackfill"
        const val SUMMARY_LOG_TAG = "RmvMesafeBackfill-Summary"
        const val RESET_LOG_TAG = "RmvMesafeBackfill-Reset"
        const val FALLBACK_CLEANUP_LOG_TAG = "RmvMesafeBackfill-FallbackCleanup"
    }
}
