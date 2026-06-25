package com.example.toplutasima.network.rmv

import android.util.Log
import com.example.toplutasima.BuildConfig
import com.example.toplutasima.diagnostics.TransitTrackerLogger
import com.example.toplutasima.model.Segment
import com.example.toplutasima.model.VehicleType
import com.example.toplutasima.network.ApiErrors
import com.example.toplutasima.network.RmvApiService
import com.example.toplutasima.network.RmvEndpointAvailability
import com.example.toplutasima.network.StopNameUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.pow

class RmvSegmentDetailsService(
    private val distanceService: RmvDistanceService = RmvDistanceService()
) {
    private data class StopInfo(
        val name: String,
        val lat: Double,
        val lon: Double,
        val depTime: String,
        val extId: String
    )

    suspend fun fetchJourneyStops(
        ref: String,
        fromStop: String,
        toStop: String,
        boardingDepTime: String = "",
        fromId: String = "",
        toId: String = ""
    ): RmvApiService.JourneySegment = withContext(Dispatchers.IO) {
        val response = rmvCall("journeyDetail") { requestId ->
            rmvApi.getJourneyDetail(
                ref = ref,
                requestId = requestId
            )
        }
        val body = response.toString()
        logD("JourneyDiag", "journeyDetail Retrofit bodyLen=${body.length}")
        val json = JSONObject(body)

        val stopsRaw = json.optJSONObject("JourneyDetail")?.opt("Stops")?.let { stops ->
            when (stops) {
                is JSONObject -> stops.opt("Stop")
                else -> stops
            }
        } ?: json.opt("Stops")?.let { stops ->
            when (stops) {
                is JSONObject -> stops.opt("Stop")
                else -> stops
            }
        } ?: json.opt("Stop")

        val stopList = mutableListOf<StopInfo>()
        var totalStopsBeforeFilter = 0
        var filteredOutCount = 0
        when (stopsRaw) {
            is JSONArray -> {
                totalStopsBeforeFilter = stopsRaw.length()
                for (i in 0 until stopsRaw.length()) {
                    val stop = stopsRaw.getJSONObject(i)
                    val name = stop.optString("name", "").trim()
                    val lat = stop.optDouble("lat", 0.0)
                    val lon = stop.optDouble("lon", 0.0)
                    val depTimeVal = stop.optString("depTime", "").trim()
                    val arrTimeVal = stop.optString("arrTime", "").trim()
                    val depTime = (depTimeVal.ifEmpty { arrTimeVal }).take(5)
                    val extId = stop.optString("extId", stop.optString("id", "")).trim()
                    if (name.isNotBlank() && lat != 0.0 && lon != 0.0) {
                        stopList.add(StopInfo(name, lat, lon, depTime, extId))
                    } else {
                        filteredOutCount++
                        logD("JourneyDiag", "FILTERED OUT stop[$i]: name='$name'")
                    }
                }
            }
            is JSONObject -> {
                totalStopsBeforeFilter = 1
                val name = stopsRaw.optString("name", "").trim()
                val lat = stopsRaw.optDouble("lat", 0.0)
                val lon = stopsRaw.optDouble("lon", 0.0)
                val depTimeVal = stopsRaw.optString("depTime", "").trim()
                val arrTimeVal = stopsRaw.optString("arrTime", "").trim()
                val depTime = (depTimeVal.ifEmpty { arrTimeVal }).take(5)
                val extId = stopsRaw.optString("extId", stopsRaw.optString("id", "")).trim()
                if (name.isNotBlank() && lat != 0.0 && lon != 0.0) {
                    stopList.add(StopInfo(name, lat, lon, depTime, extId))
                } else {
                    filteredOutCount++
                    logD("JourneyDiag", "FILTERED OUT single stop: name='$name'")
                }
            }
            null -> logD("JourneyDiag", "stopsRaw is NULL - no stops found in JSON")
            else -> logD("JourneyDiag", "stopsRaw unexpected type: ${stopsRaw::class.simpleName}")
        }
        logD(
            "JourneyDiag",
            "totalStops=$totalStopsBeforeFilter filteredOut=$filteredOutCount remaining=${stopList.size}"
        )

        if (stopList.isEmpty()) {
            return@withContext RmvApiService.JourneySegment(0, emptyList(), emptyList(), emptyList())
        }

        var fromIdx = -1
        var toIdx = -1

        fun extractExtId(compositeId: String): String {
            val value = compositeId.trim()
            val lParam = Regex("""L=(\d+)""").find(value)?.groupValues?.get(1).orEmpty()
            if (lParam.isNotBlank()) return lParam
            return value.takeIf { it.matches(Regex("""\d+""")) }.orEmpty()
        }

        val fromExtId = extractExtId(fromId)
        val toExtId = extractExtId(toId)
        val boardingTime = boardingDepTime.take(5)
        logD("JourneyDiag", "fromExtId='$fromExtId' toExtId='$toExtId' boardingTime='$boardingTime'")

        fun StopInfo.matchesExtId(extId: String): Boolean =
            extId.isNotBlank() && this.extId.contains(extId)

        fun StopInfo.matchesName(stopName: String): Boolean =
            stopName.isNotBlank() &&
                (name.contains(stopName, ignoreCase = true) || stopName.contains(name, ignoreCase = true))

        fun StopInfo.matchesFuzzy(stopName: String): Boolean =
            stopName.isNotBlank() && StopNameUtils.fuzzyMatch(stopName, name)

        fun findFirstIndex(indices: Iterable<Int>, predicate: (StopInfo) -> Boolean): Int {
            for (i in indices) {
                if (predicate(stopList[i])) return i
            }
            return -1
        }

        fun firstResolved(vararg attempts: () -> Int): Int {
            for (attempt in attempts) {
                val idx = attempt()
                if (idx != -1) return idx
            }
            return -1
        }

        fun allIndices(): Iterable<Int> = stopList.indices

        fun indicesAfter(index: Int): Iterable<Int> =
            if (index >= 0 && index < stopList.lastIndex) (index + 1)..stopList.lastIndex else emptyList()

        fromIdx = firstResolved(
            {
                if (boardingTime.isNotBlank()) {
                    findFirstIndex(allIndices()) { it.matchesExtId(fromExtId) && it.depTime == boardingTime }
                } else {
                    -1
                }
            },
            {
                if (boardingTime.isNotBlank()) {
                    findFirstIndex(allIndices()) { it.matchesName(fromStop) && it.depTime == boardingTime }
                } else {
                    -1
                }
            },
            {
                if (boardingTime.isNotBlank()) {
                    findFirstIndex(allIndices()) { it.matchesFuzzy(fromStop) && it.depTime == boardingTime }
                } else {
                    -1
                }
            },
            { findFirstIndex(allIndices()) { it.matchesExtId(fromExtId) } },
            { findFirstIndex(allIndices()) { it.matchesName(fromStop) } },
            { findFirstIndex(allIndices()) { it.matchesFuzzy(fromStop) } },
            {
                if (boardingTime.isNotBlank()) {
                    findFirstIndex(allIndices()) { it.depTime == boardingTime }
                } else {
                    -1
                }
            }
        )

        toIdx = firstResolved(
            { findFirstIndex(indicesAfter(fromIdx)) { it.matchesExtId(toExtId) } },
            { findFirstIndex(indicesAfter(fromIdx)) { it.matchesName(toStop) } },
            { findFirstIndex(indicesAfter(fromIdx)) { it.matchesFuzzy(toStop) } },
            { findFirstIndex(allIndices()) { it.matchesExtId(toExtId) } },
            { findFirstIndex(allIndices()) { it.matchesName(toStop) } },
            { findFirstIndex(allIndices()) { it.matchesFuzzy(toStop) } }
        )

        logD(
            "JourneyDiag",
            "IDX-RESOLVE fromIdx=$fromIdx toIdx=$toIdx (before resolve) fromStop='$fromStop' toStop='$toStop'"
        )

        val resolvedFrom = if (fromIdx != -1) fromIdx else 0
        val resolvedTo = if (toIdx != -1) toIdx else stopList.size - 1

        val segmentStops = if (resolvedTo >= resolvedFrom) {
            stopList.subList(resolvedFrom, resolvedTo + 1)
        } else {
            stopList.subList(resolvedTo, resolvedFrom + 1).asReversed()
        }
        val segmentCoords = segmentStops.map { Pair(it.lat, it.lon) }
        logWaypointDiagnostics(segmentStops, resolvedFrom, resolvedTo)
        val segStopCount = kotlin.math.abs(resolvedTo - resolvedFrom)
        val allNames = stopList.map { it.name }
        val allTimes = stopList.map { it.depTime }
        val allStopCoords = stopList.map { Pair(it.lat, it.lon) }
        val polylineCoords = parseJourneyPolylineCoords(json)

        logD(
            "JourneyDiag",
            "RETURN fromIdx=$resolvedFrom toIdx=$resolvedTo totalStops=${stopList.size} " +
                "segStops=$segStopCount coordsSize=${segmentCoords.size} polylineCoords=${polylineCoords.size}"
        )
        RmvApiService.JourneySegment(
            stopCount = segStopCount,
            coords = segmentCoords,
            stopNames = allNames,
            stopTimes = allTimes,
            fromIdx = resolvedFrom,
            toIdx = resolvedTo,
            allStopCoords = allStopCoords,
            polylineCoords = polylineCoords
        )
    }

    suspend fun fetchSegmentDetails(seg: Segment): RmvApiService.SegmentDetails =
        withContext(NonCancellable + Dispatchers.IO) {
            try {
                if (seg.journeyRef.isBlank()) {
                    return@withContext RmvApiService.SegmentDetails(0.0, 0, emptyList(), emptyList())
                }
                val details = fetchSegmentDetailsOnce(seg)
                if (details.distanceKm < 1.0 && details.stopCount > 5) {
                    val retry = fetchSegmentDetailsOnce(seg)
                    if (retry.distanceKm > details.distanceKm) return@withContext retry
                }
                details
            } catch (_: Exception) {
                RmvApiService.SegmentDetails(0.0, 0, emptyList(), emptyList())
            }
        }

    private suspend fun fetchSegmentDetailsOnce(seg: Segment): RmvApiService.SegmentDetails {
        val journeySegment = fetchJourneyStops(
            seg.journeyRef,
            seg.fromStop,
            seg.toStop,
            seg.dep,
            seg.fromStopId,
            seg.toStopId
        )
        val distanceResult = if (journeySegment.coords.size >= 2) {
            when (seg.typeTr) {
                VehicleType.BUS.key -> distanceService.calculateDistanceORS(
                    journeySegment.coords,
                    journeySegment.polylineCoords
                )
                else -> distanceService.calculateDistanceRail(
                    journeySegment.coords,
                    journeySegment.allStopCoords,
                    journeySegment.fromIdx,
                    journeySegment.toIdx,
                    journeySegment.polylineCoords
                )
            }
        } else {
            SegmentDistanceResult(null, null)
        }
        val distanceKm = distanceResult.apiDistanceKm ?: 0.0
        val toCoords = journeySegment.allStopCoords.getOrNull(journeySegment.toIdx)
        return RmvApiService.SegmentDetails(
            distanceKm = distanceKm,
            stopCount = journeySegment.stopCount,
            stopNames = journeySegment.stopNames,
            stopTimes = journeySegment.stopTimes,
            fromIdx = journeySegment.fromIdx,
            toIdx = journeySegment.toIdx,
            toStopLat = toCoords?.first ?: Double.NaN,
            toStopLng = toCoords?.second ?: Double.NaN,
            distanceResult = distanceResult
        )
    }

    private suspend fun <T> rmvCall(endpoint: String, block: suspend (String) -> T): T {
        val requestId = ApiErrors.newRequestId()
        logD("RmvRequest", "$endpoint requestId=$requestId")
        return try {
            val result = block(requestId)
            RmvEndpointAvailability.markSupported(endpoint)
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val apiError = ApiErrors.fromThrowable("RMV", endpoint, requestId, e)
            RmvEndpointAvailability.markFromException(endpoint, apiError)
            throw apiError
        }
    }

    private fun parseJourneyPolylineCoords(json: JSONObject): List<Pair<Double, Double>> {
        return try {
            val polylineGroup = json.optJSONObject("PolylineGroup")
                ?: json.optJSONObject("JourneyDetail")?.optJSONObject("PolylineGroup")
                ?: return emptyList()
            val descRaw = polylineGroup.opt("polylineDesc") ?: return emptyList()
            val desc = when (descRaw) {
                is JSONArray -> descRaw.optJSONObject(0)
                is JSONObject -> descRaw
                else -> null
            } ?: return emptyList()
            val crd = desc.optJSONArray("crd") ?: return emptyList()
            val points = mutableListOf<Pair<Double, Double>>()
            var i = 0
            while (i + 1 < crd.length()) {
                val lon = crd.optDouble(i, Double.NaN)
                val lat = crd.optDouble(i + 1, Double.NaN)
                if (!lat.isNaN() && !lon.isNaN() &&
                    kotlin.math.abs(lat) <= 90.0 &&
                    kotlin.math.abs(lon) <= 180.0
                ) {
                    points += Pair(lat, lon)
                }
                i += 2
            }
            points
        } catch (e: Exception) {
            logD("JourneyDiag", "Polyline parse failed: ${e.message}")
            emptyList()
        }
    }

    private fun logWaypointDiagnostics(stops: List<StopInfo>, resolvedFrom: Int, resolvedTo: Int) {
        if (!BuildConfig.DEBUG) return
        val step = if (resolvedTo >= resolvedFrom) 1 else -1
        TransitTrackerLogger.log(
            "JourneyWaypoint",
            "Waypoint list before ORS: count=${stops.size} fromIdx=$resolvedFrom toIdx=$resolvedTo"
        )
        stops.forEachIndexed { index, stop ->
            val sourceIndex = resolvedFrom + (index * step)
            TransitTrackerLogger.log(
                "JourneyWaypoint",
                "Waypoint[$index] sourceIdx=$sourceIndex id=${stop.extId} " +
                    "name='${stop.name}' lat=${formatCoord(stop.lat)} lon=${formatCoord(stop.lon)}"
            )
        }
        logWaypointSegments(stops)
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2).pow(2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2).pow(2)
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }

    private fun logWaypointSegments(stops: List<StopInfo>) {
        if (!BuildConfig.DEBUG) return
        for (i in 0 until stops.size - 1) {
            val a = stops[i]
            val b = stops[i + 1]
            val d = haversineKm(a.lat, a.lon, b.lat, b.lon)
            val flag = if (d > 1.0) " ANOMALI" else ""
            TransitTrackerLogger.log(
                "JourneyWaypoint",
                "Segment[$i] ${a.name} -> ${b.name}: ${String.format(Locale.US, "%.3f", d)} km$flag"
            )
        }
    }

    private fun formatCoord(value: Double): String =
        String.format(Locale.US, "%.6f", value)

    private fun logD(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.d(tag, msg)
    }
}
