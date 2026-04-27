package com.example.toplutasima.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.toplutasima.BuildConfig
import com.example.toplutasima.model.BulkUpdateRow
import com.example.toplutasima.model.Segment
import com.example.toplutasima.model.TripResult
import com.example.toplutasima.model.VehicleType
import com.example.toplutasima.network.RmvApiService

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

enum class BulkUpdatePhase {
    IDLE, LOADING, RUNNING, PAUSED, RATE_LIMITED, DONE
}

enum class BulkUpdateMode {
    DISTANCE_STOPS, STOP_NAMES
}

data class BulkUpdateUiState(
    val phase: BulkUpdatePhase = BulkUpdatePhase.IDLE,
    val mode: BulkUpdateMode = BulkUpdateMode.DISTANCE_STOPS,
    val rows: List<BulkUpdateRow> = emptyList(),
    val totalRows: Int = 0,
    val currentIndex: Int = 0,
    val successCount: Int = 0,
    val failCount: Int = 0,
    val skipCount: Int = 0,
    val currentRowInfo: String = "",
    val rateLimitCountdown: Int = 0,
    val rateLimitReason: String = "",
    val errorMessage: String = "",
    val elapsedMs: Long = 0L,
    val avgMsPerRow: Long = 0L
)

class BulkUpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(BulkUpdateUiState())
    val uiState: StateFlow<BulkUpdateUiState> = _uiState.asStateFlow()

    private var updateJob: Job? = null
    @Volatile private var isPaused = false
    @Volatile private var isCancelled = false

    // --- Caches ---
    private val stopIdCache = mutableMapOf<String, String>() // stopName -> stopId
    private val tripCache = mutableMapOf<String, TripResult>() // "line|from|to|time" -> TripResult

    // --- Rate Limiting ---
    private val orsCallTimestamps = mutableListOf<Long>()  // epoch ms of each ORS call
    private val rmvCallTimestamps = mutableListOf<Long>()  // epoch ms of each RMV call

    companion object {
        private const val ORS_MAX_PER_MINUTE = 40
        private const val ORS_SAFETY_MARGIN = 2 // start waiting at 38
        private const val RMV_MAX_PER_HOUR = 600
        private const val RMV_SAFETY_MARGIN = 10 // start waiting at 590
        private const val TAG = "BulkUpdate"
    }

    private fun logD(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    fun loadPendingRows() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(phase = BulkUpdatePhase.LOADING, errorMessage = "")
            try {
                val rows = withContext(Dispatchers.IO) { 
                    com.example.toplutasima.network.FirestoreService.fetchRowsForBulkUpdate()
                }
                _uiState.value = _uiState.value.copy(
                    phase = BulkUpdatePhase.IDLE,
                    rows = rows,
                    totalRows = rows.size,
                    currentIndex = 0,
                    successCount = 0,
                    failCount = 0,
                    skipCount = 0
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    phase = BulkUpdatePhase.IDLE,
                    errorMessage = "Yükleme hatası: ${e.message}"
                )
            }
        }
    }

    fun loadAllRows() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(phase = BulkUpdatePhase.LOADING, mode = BulkUpdateMode.STOP_NAMES, errorMessage = "")
            try {
                val rows = withContext(Dispatchers.IO) { 
                    com.example.toplutasima.network.FirestoreService.fetchAllRowsForStopNameUpdate()
                }
                _uiState.value = _uiState.value.copy(
                    phase = BulkUpdatePhase.IDLE,
                    mode = BulkUpdateMode.STOP_NAMES,
                    rows = rows,
                    totalRows = rows.size,
                    currentIndex = 0,
                    successCount = 0,
                    failCount = 0,
                    skipCount = 0
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    phase = BulkUpdatePhase.IDLE,
                    errorMessage = "Yükleme hatası: ${e.message}"
                )
            }
        }
    }

    fun startUpdate() {
        isPaused = false
        isCancelled = false
        val rows = _uiState.value.rows
        if (rows.isEmpty()) return
        val currentMode = _uiState.value.mode

        _uiState.value = _uiState.value.copy(
            phase = BulkUpdatePhase.RUNNING,
            currentIndex = 0,
            successCount = 0,
            failCount = 0,
            skipCount = 0,
            errorMessage = "",
            elapsedMs = 0L,
            avgMsPerRow = 0L
        )

        updateJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            for (i in rows.indices) {
                if (isCancelled) break

                // Handle pause
                while (isPaused && !isCancelled) {
                    delay(200)
                }
                if (isCancelled) break

                val row = rows[i]
                _uiState.value = _uiState.value.copy(
                    currentIndex = i,
                    currentRowInfo = "${row.hat} ${row.binisDuragi} → ${row.inisDuragi}"
                )

                try {
                    val success = withContext(Dispatchers.IO) {
                        if (currentMode == BulkUpdateMode.STOP_NAMES) processStopNameRow(row)
                        else processRow(row)
                    }
                    val elapsed = System.currentTimeMillis() - startTime
                    val completed = i + 1
                    val avg = if (completed > 0) elapsed / completed else 0L
                    if (success) {
                        _uiState.value = _uiState.value.copy(
                            successCount = _uiState.value.successCount + 1,
                            elapsedMs = elapsed, avgMsPerRow = avg
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            skipCount = _uiState.value.skipCount + 1,
                            elapsedMs = elapsed, avgMsPerRow = avg
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Row ${row.rowIndex} failed: ${e.message}")
                    val elapsed = System.currentTimeMillis() - startTime
                    val completed = i + 1
                    val avg = if (completed > 0) elapsed / completed else 0L
                    _uiState.value = _uiState.value.copy(
                        failCount = _uiState.value.failCount + 1,
                        elapsedMs = elapsed, avgMsPerRow = avg
                    )
                }
            }

            _uiState.value = _uiState.value.copy(
                phase = BulkUpdatePhase.DONE,
                currentRowInfo = ""
            )
        }
    }

    fun pauseUpdate() {
        isPaused = true
        _uiState.value = _uiState.value.copy(phase = BulkUpdatePhase.PAUSED)
    }

    fun resumeUpdate() {
        isPaused = false
        _uiState.value = _uiState.value.copy(phase = BulkUpdatePhase.RUNNING)
    }

    fun cancelUpdate() {
        isCancelled = true
        isPaused = false
        updateJob?.cancel()
        _uiState.value = _uiState.value.copy(phase = BulkUpdatePhase.DONE, currentRowInfo = "")
    }

    fun resetState() {
        stopIdCache.clear()
        tripCache.clear()
        _uiState.value = BulkUpdateUiState()
    }

    fun resetAllDistanceAndStops() {
        if (_uiState.value.phase != BulkUpdatePhase.IDLE) return

        isPaused = false
        isCancelled = false

        updateJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                phase = BulkUpdatePhase.LOADING, 
                errorMessage = ""
            )
            try {
                // Fetch all from Firestore
                val trips = withContext(Dispatchers.IO) { com.example.toplutasima.network.FirestoreService.fetchTrips() }

                _uiState.value = _uiState.value.copy(
                    phase = BulkUpdatePhase.RUNNING,
                    mode = BulkUpdateMode.DISTANCE_STOPS,
                    totalRows = trips.size,
                    currentIndex = 0,
                    successCount = 0,
                    failCount = 0,
                    skipCount = 0,
                    elapsedMs = 0L,
                    avgMsPerRow = 0L
                )

                var success = 0
                var fail = 0
                val startTime = System.currentTimeMillis()

                for ((index, trip) in trips.withIndex()) {
                    if (isCancelled) break
                    while (isPaused && !isCancelled) delay(200)
                    if (isCancelled) break

                    val docId = trip["firestoreDocId"]?.toString() ?: ""
                    if (docId.isBlank()) continue

                    val date = trip["tarih"]?.toString() ?: ""
                    val line = trip["hat"]?.toString() ?: ""

                    _uiState.value = _uiState.value.copy(
                        currentIndex = index,
                        currentRowInfo = "$date - $line (Sıfırlanıyor...)"
                    )

                    try {
                        val ok = withContext(Dispatchers.IO) {
                            com.example.toplutasima.network.FirestoreService.updateTrip(docId, mapOf("mesafe" to "", "durakSayisi" to ""))
                        }
                        if (ok) success++ else fail++
                    } catch (e: Exception) {
                        fail++
                    }

                    val elapsed = System.currentTimeMillis() - startTime
                    val completed = index + 1
                    val avg = if (completed > 0) elapsed / completed else 0L

                    _uiState.value = _uiState.value.copy(
                        successCount = success,
                        failCount = fail,
                        elapsedMs = elapsed,
                        avgMsPerRow = avg
                    )
                }

                _uiState.value = _uiState.value.copy(
                    phase = BulkUpdatePhase.DONE,
                    currentRowInfo = ""
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    phase = BulkUpdatePhase.IDLE,
                    errorMessage = "Sıfırlama hatası: ${e.message}"
                )
            }
        }
    }

    // ----- Rate Limiting -----

    private suspend fun checkOrsRateLimit() {
        val now = System.currentTimeMillis()
        orsCallTimestamps.removeAll { now - it > 60_000 }
        if (orsCallTimestamps.size >= ORS_MAX_PER_MINUTE - ORS_SAFETY_MARGIN) {
            val oldest = orsCallTimestamps.firstOrNull() ?: return
            val waitMs = 60_000 - (now - oldest) + 1000 // wait until oldest expires + 1s buffer
            if (waitMs > 0) {
                awaitWithCountdown(waitMs, "ORS limiti (${ORS_MAX_PER_MINUTE}/dk)")
            }
        }
    }

    private fun recordOrsCall() {
        orsCallTimestamps.add(System.currentTimeMillis())
    }

    private suspend fun checkRmvRateLimit() {
        val now = System.currentTimeMillis()
        rmvCallTimestamps.removeAll { now - it > 3_600_000 }
        if (rmvCallTimestamps.size >= RMV_MAX_PER_HOUR - RMV_SAFETY_MARGIN) {
            val oldest = rmvCallTimestamps.firstOrNull() ?: return
            val waitMs = 3_600_000 - (now - oldest) + 1000
            if (waitMs > 0) {
                awaitWithCountdown(waitMs, "RMV limiti (${RMV_MAX_PER_HOUR}/saat)")
            }
        }
    }

    private fun recordRmvCall() {
        rmvCallTimestamps.add(System.currentTimeMillis())
    }

    private suspend fun awaitWithCountdown(waitMs: Long, reason: String) {
        val totalSec = (waitMs / 1000).toInt()
        _uiState.value = _uiState.value.copy(
            phase = BulkUpdatePhase.RATE_LIMITED,
            rateLimitCountdown = totalSec,
            rateLimitReason = reason
        )
        for (sec in totalSec downTo 1) {
            if (isCancelled) return
            _uiState.value = _uiState.value.copy(rateLimitCountdown = sec)
            delay(1000)
        }
        _uiState.value = _uiState.value.copy(
            phase = BulkUpdatePhase.RUNNING,
            rateLimitCountdown = 0,
            rateLimitReason = ""
        )
    }

    // ----- Core Processing -----

    private suspend fun processRow(row: BulkUpdateRow): Boolean {
        if (row.binisDuragi.isBlank() || row.inisDuragi.isBlank() || row.hat.isBlank()) {
            logD("Skipping row ${row.rowIndex}: missing required fields")
            return false
        }

        // 1) Resolve stop IDs with caching
        val fromId = resolveStopId(row.binisDuragi)
        val toId = resolveStopId(row.inisDuragi)
        if (fromId.isBlank() || toId.isBlank()) {
            logD("Could not resolve stop IDs for row ${row.rowIndex}")
            return false
        }

        // 2) Fetch trip (with caching)
        val tripKey = "${row.tarih}|${row.hat}|${row.binisDuragi}|${row.inisDuragi}|${row.planlananBinis.take(5)}"
        val trip = tripCache.getOrElse(tripKey) {
            checkRmvRateLimit()
            val apiDate = try { RmvApiService.convertToApiDate(row.tarih) } catch (_: Exception) { "" }
            if (apiDate.isBlank()) return false
            val searchTime = row.planlananBinis.take(5).ifBlank { "08:00" }
            val result = RmvApiService.fetchTripBasic(fromId, toId, apiDate, searchTime, row.hat)
            recordRmvCall()
            tripCache[tripKey] = result
            result
        }

        // 3) Find matching segment
        val seg = trip.segments.firstOrNull { seg ->
            seg.line.equals(row.hat, ignoreCase = true) ||
            row.hat.contains(seg.line, ignoreCase = true) ||
            seg.line.contains(row.hat, ignoreCase = true)
        } ?: trip.segments.firstOrNull()
        ?: return false

        if (seg.journeyRef.isBlank()) {
            logD("No journeyRef for row ${row.rowIndex}")
            return false
        }

        // 4) Fetch journey stops
        checkRmvRateLimit()
        val journeySegment = RmvApiService.fetchJourneyStops(
            seg.journeyRef, seg.fromStop, seg.toStop, seg.dep
        )
        recordRmvCall()

        if (journeySegment.coords.size < 2) {
            logD("Not enough coords for row ${row.rowIndex}: ${journeySegment.coords.size}")
            return false
        }

        // 5) Calculate distance
        val distanceKm = when (row.tur) {
            VehicleType.BUS.key -> {
                checkOrsRateLimit()
                val d = RmvApiService.calculateDistanceORS(journeySegment.coords)
                recordOrsCall()
                d
            }
            else -> RmvApiService.calculateDistanceRail(journeySegment.coords, journeySegment.allStopCoords, journeySegment.fromIdx, journeySegment.toIdx)
        }

        val stopCount = journeySegment.stopCount
        if (distanceKm <= 0.0 && stopCount <= 0) {
            logD("No useful data for row ${row.rowIndex}")
            return false
        }

        // 6) Write back to Firebase
        val mesafeStr = if (distanceKm > 0) String.format(Locale.US, "%.2f km", distanceKm) else ""
        val stopCountStr = if (stopCount > 0) stopCount.toString() else ""
        val ok = com.example.toplutasima.network.FirestoreService.updateTrip(row.firestoreDocId, mapOf("mesafe" to mesafeStr, "durakSayisi" to stopCountStr))
        logD("Row ${row.rowIndex} / doc ${row.firestoreDocId}: distance=$mesafeStr, stops=$stopCount, ok=$ok")
        return ok
    }

    private suspend fun resolveStopId(stopName: String): String {
        stopIdCache[stopName]?.let { return it }

        checkRmvRateLimit()
        val options = RmvApiService.searchStopOptions(stopName, 1)
        recordRmvCall()

        val id = options.firstOrNull()?.id.orEmpty()
        if (id.isNotBlank()) {
            stopIdCache[stopName] = id
        }
        return id
    }

    // ----- Stop Name Update Processing -----

    private suspend fun processStopNameRow(row: BulkUpdateRow): Boolean {
        if (row.binisDuragi.isBlank() || row.inisDuragi.isBlank() || row.hat.isBlank()) {
            logD("Skipping stop name row ${row.rowIndex}: missing required fields")
            return false
        }

        // 1) Resolve stop IDs with caching
        val fromId = resolveStopId(row.binisDuragi)
        val toId = resolveStopId(row.inisDuragi)
        if (fromId.isBlank() || toId.isBlank()) {
            logD("Could not resolve stop IDs for stop name row ${row.rowIndex}")
            return false
        }

        // 2) Fetch trip (with caching)
        val tripKey = "sn|${row.tarih}|${row.hat}|${row.binisDuragi}|${row.inisDuragi}|${row.planlananBinis.take(5)}"
        val trip = tripCache.getOrElse(tripKey) {
            checkRmvRateLimit()
            val apiDate = try { RmvApiService.convertToApiDate(row.tarih) } catch (_: Exception) { "" }
            if (apiDate.isBlank()) return false
            val searchTime = row.planlananBinis.take(5).ifBlank { "08:00" }
            val result = RmvApiService.fetchTripBasic(fromId, toId, apiDate, searchTime, row.hat)
            recordRmvCall()
            tripCache[tripKey] = result
            result
        }

        // 3) Find matching segment
        val seg = trip.segments.firstOrNull { seg ->
            seg.line.equals(row.hat, ignoreCase = true) ||
            row.hat.contains(seg.line, ignoreCase = true) ||
            seg.line.contains(row.hat, ignoreCase = true)
        } ?: trip.segments.firstOrNull()
        ?: return false

        // 4) Update stop names and direction
        val ok = com.example.toplutasima.network.FirestoreService.updateTrip(row.firestoreDocId, mapOf(
            "binisDuragi" to seg.fromStop,
            "inisDuragi" to seg.toStop,
            "yon" to seg.direction
        ))
        logD("StopName row ${row.rowIndex} / doc ${row.firestoreDocId}: ok=$ok")
        return ok
    }
}
