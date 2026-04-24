package com.example.toplutasima.network

import android.util.Log
import com.example.toplutasima.BuildConfig
import com.example.toplutasima.model.Departure
import com.example.toplutasima.model.Segment
import com.example.toplutasima.model.StopOption
import com.example.toplutasima.model.VehicleType
import com.example.toplutasima.model.TripResult
import com.example.toplutasima.network.rmv.RmvCoordWrapper
import com.example.toplutasima.network.rmv.RmvStopLocation
import com.example.toplutasima.network.rmv.rmvApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object RmvApiService {
    // ── Debug-only logging helpers ─────────────────────────────────────────
    private fun logD(tag: String, msg: String) { if (BuildConfig.DEBUG) Log.d(tag, msg) }
    private fun logE(tag: String, msg: String, t: Throwable? = null) {
        if (BuildConfig.DEBUG) { if (t != null) Log.e(tag, msg, t) else Log.e(tag, msg) }
    }

    private const val RMV_BASE = "https://www.rmv.de/hapi"
    private val RMV_ACCESS_ID = BuildConfig.RMV_ACCESS_ID
    private val ORS_API_KEY = BuildConfig.ORS_API_KEY

    data class JourneySegment(
        val stopCount: Int,
        val coords: List<Pair<Double, Double>>,
        val stopNames: List<String> = emptyList(),
        val stopTimes: List<String> = emptyList(),
        // Tüm hat listesi içinde kullanıcının biniş/iniş indeksleri
        val fromIdx: Int = 0,
        val toIdx: Int = -1
    )

    fun formatTimeDigits(digits: String): String {
        val padded = digits.padStart(4, '0')
        return "${padded.substring(0, 2)}:${padded.substring(2, 4)}"
    }

    fun convertToApiDate(uiDate: String): String {
        return LocalDate.parse(uiDate.trim(), DateTimeFormatter.ofPattern("dd.MM.yyyy"))
            .format(DateTimeFormatter.ISO_DATE)
    }

    // ── 1. Location search — Retrofit ────────────────────────────────────────

    suspend fun searchStopOptions(input: String, max: Int = 3): List<StopOption> {
        if (input.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                val response = rmvApi.searchStops(
                    accessId = RMV_ACCESS_ID,
                    input = input,
                    maxNo = max
                )
                val stops = mutableListOf<StopOption>()

                fun addRmvStop(s: RmvStopLocation) {
                    if (s.id.isNotBlank() && s.name.isNotBlank()) stops += StopOption(s.id, s.name)
                }

                // "StopLocation" can be a single object or array
                response.stopLocation?.let { el ->
                    when (el) {
                        is JsonArray -> el.take(max).forEach { item ->
                            val obj = item.jsonObject
                            addRmvStop(RmvStopLocation(
                                id = obj["id"]?.jsonPrimitive?.content.orEmpty().trim(),
                                name = obj["name"]?.jsonPrimitive?.content.orEmpty().trim()
                            ))
                        }
                        is JsonObject -> addRmvStop(RmvStopLocation(
                            id = el["id"]?.jsonPrimitive?.content.orEmpty().trim(),
                            name = el["name"]?.jsonPrimitive?.content.orEmpty().trim()
                        ))
                        else -> {}
                    }
                    return@withContext stops
                }

                // Fallback: coord location wrapper
                response.coordLocations?.take(max)?.forEach { wrapper ->
                    wrapper.stopLocation?.let { addRmvStop(it) }
                }
                stops
            } catch (e: Exception) {
                logE("RmvApi", "searchStopOptions error: ${e.message}")
                emptyList()
            }
        }
    }

    // ── 1b. Nearby stops — Retrofit ──────────────────────────────────────────

    data class NearbyStop(val id: String, val name: String, val distanceMeters: Int)

    suspend fun searchNearbyStops(lat: Double, lon: Double, radiusMeters: Int = 500, max: Int = 8): List<NearbyStop> {
        return withContext(Dispatchers.IO) {
            try {
                val json = rmvApi.getNearbyStops(
                    accessId = RMV_ACCESS_ID,
                    lat = lat,
                    lon = lon,
                    maxNo = max,
                    radiusMeters = radiusMeters
                )
                val stops = mutableListOf<NearbyStop>()

                fun extractStop(obj: JsonObject) {
                    val id = obj["id"]?.jsonPrimitive?.content.orEmpty().trim()
                    val name = obj["name"]?.jsonPrimitive?.content.orEmpty().trim()
                    val dist = obj["dist"]?.jsonPrimitive?.intOrNull ?: 0
                    if (id.isNotBlank() && name.isNotBlank()) {
                        stops += NearbyStop(id, name, dist)
                    }
                }

                // HAFAS nearbystops wraps results in "stopLocationOrCoordLocation"
                // Each element has a "StopLocation" or "CoordLocation" child
                val wrapperElement = json["stopLocationOrCoordLocation"]
                if (wrapperElement != null) {
                    val wrappers = when (wrapperElement) {
                        is JsonArray -> wrapperElement.mapNotNull { it as? JsonObject }
                        is JsonObject -> listOf(wrapperElement)
                        else -> emptyList()
                    }
                    wrappers.take(max).forEach { wrapper ->
                        // Each wrapper: { "StopLocation": { id, name, dist, ... } }
                        val stopLoc = wrapper["StopLocation"]
                        if (stopLoc is JsonObject) {
                            extractStop(stopLoc)
                        }
                    }
                }

                // Fallback: some versions put StopLocation directly at root level
                if (stops.isEmpty()) {
                    val directStops = json["StopLocation"]
                    if (directStops != null) {
                        when (directStops) {
                            is JsonArray -> directStops.take(max).forEach { item ->
                                if (item is JsonObject) extractStop(item)
                            }
                            is JsonObject -> extractStop(directStops)
                            else -> {}
                        }
                    }
                }

                logD("RmvApi", "searchNearbyStops: found ${stops.size} stops")
                stops.sortedBy { it.distanceMeters }
            } catch (e: Exception) {
                logE("RmvApi", "searchNearbyStops error: ${e.message}", e)
                emptyList()
            }
        }
    }

    // ── 2. Departure board — Retrofit ─────────────────────────────────────────

    suspend fun fetchDepartureBoard(
        stopId: String,
        destId: String,
        date: String,
        time: String
    ): List<Departure> = withContext(Dispatchers.IO) {
        // Step 1: Discover valid line+direction combos via trip API
        val validLineDirections = mutableMapOf<String, MutableSet<String>>()
        if (destId.isNotBlank()) {
            try {
                val tripJson = rmvApi.getTrip(
                    accessId = RMV_ACCESS_ID,
                    originId = stopId,
                    destId = destId,
                    date = date,
                    time = time,
                    numF = 6
                )
                val trips = when (val t = tripJson["Trip"]) {
                    is JsonArray -> t
                    is JsonObject -> JsonArray(listOf(t))
                    else -> JsonArray(emptyList())
                }
                trips.forEach { tripEl ->
                    val trip = tripEl.jsonObject
                    val legs = try { legsArrayKtx(trip) } catch (_: Exception) { return@forEach }
                    for (legEl in legs) {
                        val leg = legEl.jsonObject
                        val type = leg["type"]?.jsonPrimitive?.content?.trim() ?: ""
                        if (type.equals("WALK", ignoreCase = true)) continue
                        val code = extractPublicLineCodeKtx(leg)
                        if (code.isNotBlank()) {
                            val normCode = normalizeLineCode(code)
                            val dir = extractDirectionKtx(leg)
                            validLineDirections.getOrPut(normCode) { mutableSetOf() }.add(dir)
                        }
                        break
                    }
                }
                logD("DepartureBoard", "Valid line+direction combos: $validLineDirections")
            } catch (_: Exception) { /* proceed without filtering */ }
        }

        // Step 2: Fetch departure board
        val minTime = try { LocalTime.parse(time.trim().take(5)) } catch (_: Exception) { null }

        val response = try {
            rmvApi.getDepartures(
                accessId = RMV_ACCESS_ID,
                stopId = stopId,
                date = date,
                time = time
            )
        } catch (e: Exception) {
            logE("RmvApi", "fetchDepartureBoard error: ${e.message}")
            return@withContext emptyList()
        }

        val rawDep = response.departure ?: return@withContext emptyList()
        val depArray: JsonArray = when (rawDep) {
            is JsonArray -> rawDep
            is JsonObject -> JsonArray(listOf(rawDep))
            else -> return@withContext emptyList()
        }

        val departures = mutableListOf<Departure>()
        val lineRx = Regex(
            """\b([A-Za-z]{1,4}-?\s?\d{1,3}|RB\s?\d{1,3}|RE\s?\d{1,3}|S\s?\d{1,2}|U\s?\d{1,2}|X\s?\d{1,3}|\d{1,3})\b""",
            RegexOption.IGNORE_CASE
        )

        for (depEl in depArray) {
            val dep = depEl.jsonObject
            val depTime = dep["time"]?.jsonPrimitive?.content?.take(5).orEmpty()
            val direction = dep["direction"]?.jsonPrimitive?.content?.trim().orEmpty()
            val track = dep["track"]?.jsonPrimitive?.content?.trim().orEmpty()

            // Skip departures before user's entered time
            if (minTime != null && depTime.length >= 5) {
                try {
                    if (LocalTime.parse(depTime.take(5)).isBefore(minTime)) continue
                } catch (_: Exception) {}
            }

            val product = safeProductKtx(dep["Product"] ?: dep["ProductAtStop"])
            val rawName = product?.get("name")?.jsonPrimitive?.content?.trim()
                ?: dep["name"]?.jsonPrimitive?.content?.trim().orEmpty()
            val rawNum = product?.get("num")?.jsonPrimitive?.content?.trim().orEmpty()

            var line = ""
            for (candidate in listOf(rawName, rawNum)) {
                val m = lineRx.find(candidate ?: "")?.value ?: continue
                line = m.replace(" ", "").uppercase()
                if (line.startsWith("BUS")) line = line.removePrefix("BUS")
                break
            }
            if (line.isBlank() && rawName?.isNotBlank() == true) line = rawName
            if (line.isBlank()) continue

            // Filter by valid line+direction
            if (validLineDirections.isNotEmpty()) {
                val normalizedLine = normalizeLineCode(line)
                val validDirs = validLineDirections[normalizedLine]
                if (validDirs == null) {
                    logD("DepartureBoard", "SKIP '$normalizedLine' dir='$direction' — line not in valid set")
                    continue
                }
                if (validDirs.isNotEmpty() && direction.isNotBlank()) {
                    val dirMatch = validDirs.any { validDir ->
                        validDir.isBlank() ||
                        direction.contains(validDir, ignoreCase = true) ||
                        validDir.contains(direction, ignoreCase = true)
                    }
                    if (!dirMatch) {
                        logD("DepartureBoard", "SKIP '$normalizedLine' dir='$direction' — direction mismatch")
                        continue
                    }
                }
                logD("DepartureBoard", "KEEP '$normalizedLine' dir='$direction'")
            }

            val typeTr = mapTypeTrKtx(product, line)
            val cleanLine = if (typeTr == VehicleType.STRASSENBAHN.key && line.startsWith("TRAM"))
                line.removePrefix("TRAM") else line

            val journeyDetailRef = dep["JourneyDetailRef"]?.jsonObject?.get("ref")
                ?.jsonPrimitive?.content.orEmpty()

            departures += Departure(cleanLine, direction, depTime, track, typeTr, journeyDetailRef)
        }

        departures.forEach { logD("DEBUG_BOARD", "time: ${it.time}, ref: ${it.journeyDetailRef}") }
        departures
    }

    // ── 3. Journey stops — OkHttp (suspend) ──────────────────────────────────

    suspend fun fetchJourneyStops(
        ref: String,
        fromStop: String,
        toStop: String,
        boardingDepTime: String = "",
        fromId: String = "",
        toId: String = ""
    ): JourneySegment = withContext(Dispatchers.IO) {
        val url = "$RMV_BASE/journeyDetail?accessId=${ApiClient.encode(RMV_ACCESS_ID)}&id=${ApiClient.encode(ref)}&format=json"
        val req = Request.Builder().url(url).get().build()
        ApiClient.http.newCall(req).execute().use { res ->
            val body = res.body?.string().orEmpty()
            logD("JourneyDiag", "journeyDetail HTTP ${res.code}, bodyLen=${body.length}")
            if (!res.isSuccessful) return@withContext JourneySegment(0, emptyList())
            val json = JSONObject(body)

            val stopsRaw = json.optJSONObject("JourneyDetail")?.opt("Stops")?.let { s ->
                when (s) { is JSONObject -> s.opt("Stop"); else -> s }
            } ?: json.opt("Stops")?.let { s ->
                when (s) { is JSONObject -> s.opt("Stop"); else -> s }
            } ?: json.opt("Stop")

            data class StopInfo(val name: String, val lat: Double, val lon: Double, val depTime: String, val extId: String)
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
                        val dt = (depTimeVal.ifEmpty { arrTimeVal }).take(5)
                        val extId = stop.optString("extId", stop.optString("id", "")).trim()
                        if (name.isNotBlank() && lat != 0.0 && lon != 0.0) {
                            stopList.add(StopInfo(name, lat, lon, dt, extId))
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
                    val dt = (depTimeVal.ifEmpty { arrTimeVal }).take(5)
                    val extId = stopsRaw.optString("extId", stopsRaw.optString("id", "")).trim()
                    if (name.isNotBlank() && lat != 0.0 && lon != 0.0) {
                        stopList.add(StopInfo(name, lat, lon, dt, extId))
                    } else {
                        filteredOutCount++
                        logD("JourneyDiag", "FILTERED OUT single stop: name='$name'")
                    }
                }
                null -> logD("JourneyDiag", "stopsRaw is NULL — no stops found in JSON")
                else -> logD("JourneyDiag", "stopsRaw unexpected type: ${stopsRaw::class.simpleName}")
            }
            logD("JourneyDiag", "totalStops=$totalStopsBeforeFilter filteredOut=$filteredOutCount remaining=${stopList.size}")

            if (stopList.isEmpty()) return@withContext JourneySegment(0, emptyList(), emptyList(), emptyList())

            var fromIdx = -1
            var toIdx = -1

            fun extractExtId(compositeId: String): String =
                Regex("""L=(\d+)""").find(compositeId)?.groupValues?.get(1).orEmpty()

            val fromExtId = extractExtId(fromId)
            val toExtId = extractExtId(toId)
            logD("JourneyDiag", "fromExtId='$fromExtId' toExtId='$toExtId'")

            for (i in stopList.indices) {
                val s = stopList[i]
                if (fromIdx == -1 && fromExtId.isNotBlank() && s.extId.contains(fromExtId)) { fromIdx = i }
                if (toIdx == -1 && toExtId.isNotBlank() && s.extId.contains(toExtId)) { toIdx = i }
            }

            val boardingTime = boardingDepTime.take(5)
            if (fromIdx == -1 && boardingTime.isNotBlank()) {
                for (i in stopList.indices) {
                    val s = stopList[i]
                    val nameMatch = s.name.contains(fromStop, ignoreCase = true) || fromStop.contains(s.name, ignoreCase = true)
                    if (nameMatch && s.depTime == boardingTime) { fromIdx = i; break }
                }
            }

            for (i in stopList.indices) {
                val n = stopList[i].name
                if (fromIdx == -1 && (n.contains(fromStop, ignoreCase = true) || fromStop.contains(n, ignoreCase = true))) fromIdx = i
                if (toIdx == -1 && (n.contains(toStop, ignoreCase = true) || toStop.contains(n, ignoreCase = true))) toIdx = i
            }

            if (fromIdx == -1) {
                for (i in stopList.indices) {
                    if (StopNameUtils.fuzzyMatch(fromStop, stopList[i].name)) { fromIdx = i; break }
                }
            }
            if (toIdx == -1) {
                for (i in stopList.indices) {
                    if (StopNameUtils.fuzzyMatch(toStop, stopList[i].name)) { toIdx = i; break }
                }
            }

            if (fromIdx == -1 && boardingTime.isNotBlank()) {
                for (i in stopList.indices) {
                    if (stopList[i].depTime == boardingTime) { fromIdx = i; break }
                }
            }

            val resolvedFrom = if (fromIdx != -1) fromIdx else 0
            val resolvedTo = if (toIdx != -1 && toIdx >= resolvedFrom) toIdx else stopList.size - 1

            // Mesafe hesabı için sadece segment koordinatları (biniş→iniş arası)
            val segmentCoords = stopList.subList(resolvedFrom, resolvedTo + 1).map { Pair(it.lat, it.lon) }
            val segStopCount = maxOf(0, resolvedTo - resolvedFrom)

            // Durak listesi: tüm hat (dialog'da seçim yapılabilsin)
            val allNames = stopList.map { it.name }
            val allTimes = stopList.map { it.depTime }

            logD("JourneyDiag", "RETURN fromIdx=$resolvedFrom toIdx=$resolvedTo totalStops=${stopList.size} segStops=$segStopCount")
            JourneySegment(
                stopCount = segStopCount,
                coords = segmentCoords,
                stopNames = allNames,
                stopTimes = allTimes,
                fromIdx = resolvedFrom,
                toIdx = resolvedTo
            )
        }
    }

    // ── 4. ORS distance — OkHttp (suspend) ───────────────────────────────────

    suspend fun calculateDistanceORS(coords: List<Pair<Double, Double>>): Double {
        if (coords.size < 2) return 0.0
        return withContext(Dispatchers.IO) {
            try {
                val coordArray = org.json.JSONArray()
                for (c in coords) coordArray.put(org.json.JSONArray().put(c.second).put(c.first))
                val jsonBody = JSONObject().put("coordinates", coordArray).toString()
                val reqBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())
                val req = Request.Builder()
                    .url("https://api.openrouteservice.org/v2/directions/driving-car/geojson")
                    .addHeader("Authorization", ORS_API_KEY)
                    .post(reqBody)
                    .build()
                ApiClient.http.newCall(req).execute().use { res ->
                    val respBody = res.body?.string().orEmpty()
                    if (res.isSuccessful) {
                        val json = JSONObject(respBody)
                        val segments = json.optJSONArray("features")
                            ?.optJSONObject(0)?.optJSONObject("properties")
                            ?.optJSONArray("segments") ?: org.json.JSONArray()
                        var meters = 0.0
                        for (i in 0 until segments.length()) meters += segments.optJSONObject(i)?.optDouble("distance", 0.0) ?: 0.0
                        meters / 1000.0
                    } else 0.0
                }
            } catch (e: Exception) { logE("ORSDiag", "ORS EXCEPTION: ${e.message}"); 0.0 }
        }
    }

    // ── 5. Rail distance — OkHttp (suspend) ──────────────────────────────────

    suspend fun calculateDistanceRail(coords: List<Pair<Double, Double>>): Double {
        if (coords.size < 2) return 0.0
        return withContext(Dispatchers.IO) {
            try {
                val pointParams = coords.joinToString("&") { "point=${it.first},${it.second}" }
                val url = "https://routing.openrailrouting.org/route?$pointParams&profile=all_tracks&points_encoded=false"
                val req = Request.Builder().url(url).get().build()
                ApiClient.http.newCall(req).execute().use { res ->
                    if (res.isSuccessful) {
                        val json = JSONObject(res.body?.string().orEmpty())
                        val paths = json.optJSONArray("paths")
                        if (paths != null && paths.length() > 0) {
                            val distance = paths.getJSONObject(0).optDouble("distance", 0.0)
                            if (distance > 0) return@withContext distance / 1000.0
                        }
                    }
                }
            } catch (e: Exception) { logD("RailDist", "Multi-waypoint failed: ${e.message}") }

            // Fallback: pairwise
            var totalMeters = 0.0
            for (i in 0 until coords.size - 1) {
                try {
                    val from = coords[i]; val to = coords[i + 1]
                    val url = "https://routing.openrailrouting.org/route?point=${from.first},${from.second}&point=${to.first},${to.second}&profile=all_tracks&points_encoded=false"
                    ApiClient.http.newCall(Request.Builder().url(url).get().build()).execute().use { res ->
                        if (res.isSuccessful) {
                            val json = JSONObject(res.body?.string().orEmpty())
                            val paths = json.optJSONArray("paths")
                            if (paths != null && paths.length() > 0)
                                totalMeters += paths.getJSONObject(0).optDouble("distance", 0.0)
                        }
                    }
                } catch (_: Exception) {}
            }
            totalMeters / 1000.0
        }
    }

    // ── 6. Trip — OkHttp (suspend) ────────────────────────────────────────────

    suspend fun fetchTripBasic(
        originId: String, destId: String, date: String, time: String, preferredLine: String = ""
    ): TripResult = withContext(Dispatchers.IO) {
        val numTrips = if (preferredLine.isNotBlank()) 6 else 1
        val url = "$RMV_BASE/trip?accessId=${ApiClient.encode(RMV_ACCESS_ID)}&originId=${ApiClient.encode(originId)}&destId=${ApiClient.encode(destId)}&date=${ApiClient.encode(date)}&time=${ApiClient.encode(time)}&numF=$numTrips&format=json"
        ApiClient.http.newCall(Request.Builder().url(url).get().build()).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw IllegalStateException("RMV trip HTTP ${res.code}: ${body.take(200)}")
            val json = JSONObject(body)
            val trips = when (val t = json.opt("Trip")) {
                is JSONArray -> t
                is JSONObject -> JSONArray().put(t)
                else -> throw IllegalStateException("Trip yok")
            }
            if (trips.length() == 0) throw IllegalStateException("Trip boş")
            val normalizedPref = normalizeLineCode(preferredLine)
            var bestResult: TripResult? = null
            var fallbackResult: TripResult? = null
            for (ti in 0 until trips.length()) {
                val trip0 = trips.getJSONObject(ti)
                val tripResult = parseTripObject(trip0) ?: continue
                if (fallbackResult == null) fallbackResult = tripResult
                if (normalizedPref.isNotBlank()) {
                    val firstLine = normalizeLineCode(tripResult.segments.firstOrNull()?.line.orEmpty())
                    if (firstLine == normalizedPref) { bestResult = tripResult; break }
                } else { bestResult = tripResult; break }
            }
            bestResult ?: fallbackResult ?: throw IllegalStateException("Toplu taşıma segment bulunamadı")
        }
    }

    // ── 7. Segment details (suspend wrapper) ─────────────────────────────────

    data class SegmentDetails(val distanceKm: Double, val stopCount: Int, val stopNames: List<String>, val stopTimes: List<String> = emptyList())

    suspend fun fetchSegmentDetails(seg: Segment): SegmentDetails = withContext(NonCancellable + Dispatchers.IO) {
        try {
            if (seg.journeyRef.isBlank()) return@withContext SegmentDetails(0.0, 0, emptyList(), emptyList())
            val details = fetchSegmentDetailsOnce(seg)
            if (details.distanceKm < 1.0 && details.stopCount > 5) {
                val retry = fetchSegmentDetailsOnce(seg)
                if (retry.distanceKm > details.distanceKm) return@withContext retry
            }
            details
        } catch (_: Exception) { SegmentDetails(0.0, 0, emptyList(), emptyList()) }
    }

    private suspend fun fetchSegmentDetailsOnce(seg: Segment): SegmentDetails {
        val journeySegment = fetchJourneyStops(seg.journeyRef, seg.fromStop, seg.toStop, seg.dep)
        val distanceKm = if (journeySegment.coords.size >= 2) {
            when (seg.typeTr) {
                VehicleType.BUS.key -> calculateDistanceORS(journeySegment.coords)
                else -> calculateDistanceRail(journeySegment.coords)
            }
        } else 0.0
        return SegmentDetails(distanceKm, journeySegment.stopCount, journeySegment.stopNames, journeySegment.stopTimes)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun parseTripObject(trip0: JSONObject): TripResult? {
        val legs = try { legsArray(trip0) } catch (_: Exception) { return null }
        val segments = mutableListOf<Segment>()
        var overallDep = ""; var overallArr = ""
        for (i in 0 until legs.length()) {
            val leg = legs.getJSONObject(i)
            val type = leg.optString("type", "").trim()
            if (type.equals("WALK", ignoreCase = true)) continue
            val origin = leg.optJSONObject("Origin")
            val dest = leg.optJSONObject("Destination")
            val dep = origin?.optString("time", "")?.take(5).orEmpty()
            val arr = dest?.optString("time", "")?.take(5).orEmpty()
            val fromStop = origin?.optString("name", "").orEmpty()
            val toStop = dest?.optString("name", "").orEmpty()
            val rawLine = extractPublicLineCode(leg)
            if (rawLine.isBlank()) continue
            val direction = extractDirection(leg)
            val typeTr = mapTypeTr(leg, rawLine)
            val line = if (typeTr == VehicleType.STRASSENBAHN.key && rawLine.startsWith("TRAM")) rawLine.removePrefix("TRAM") else rawLine
            if (overallDep.isBlank() && dep.isNotBlank()) overallDep = dep
            if (arr.isNotBlank()) overallArr = arr
            val journeyRef = leg.optJSONObject("JourneyDetailRef")?.optString("ref", "").orEmpty()
            segments += Segment(typeTr, line, direction, fromStop, toStop, dep, arr, journeyRef = journeyRef)
        }
        if (segments.isEmpty()) return null
        return TripResult(segments, overallDep, overallArr, diffMinutesFlexible(overallDep, overallArr))
    }

    // kotlinx.serialization helpers (for Retrofit responses)

    /**
     * RMV API sometimes returns "Product" as a JsonArray instead of JsonObject.
     * This helper safely extracts the first JsonObject from either format.
     */
    private fun safeProductKtx(el: kotlinx.serialization.json.JsonElement?): JsonObject? {
        return when (el) {
            is JsonObject -> el
            is JsonArray -> el.firstOrNull() as? JsonObject
            else -> null
        }
    }

    private fun legsArrayKtx(trip: JsonObject): JsonArray {
        fun asArr(el: kotlinx.serialization.json.JsonElement): JsonArray = when (el) {
            is JsonArray -> el
            is JsonObject -> JsonArray(listOf(el))
            else -> throw IllegalStateException("Leg format")
        }
        trip["Leg"]?.let { return asArr(it) }
        trip["Legs"]?.jsonObject?.get("Leg")?.let { return asArr(it) }
        trip["LegList"]?.jsonObject?.get("Leg")?.let { return asArr(it) }
        trip["legList"]?.jsonObject?.get("leg")?.let { return asArr(it) }
        throw IllegalStateException("Leg yok")
    }

    private fun extractPublicLineCodeKtx(leg: JsonObject): String {
        val candidates = mutableListOf<String>()
        candidates += leg["name"]?.jsonPrimitive?.content.orEmpty()
        safeProductKtx(leg["Product"])?.let { p ->
            candidates += p["name"]?.jsonPrimitive?.content.orEmpty()
            candidates += p["num"]?.jsonPrimitive?.content.orEmpty()
        }
        val rx = Regex("""\b([A-Za-z]{1,4}-?\s?\d{1,3}|RB\s?\d{1,3}|RE\s?\d{1,3}|S\s?\d{1,2}|U\s?\d{1,2}|X\s?\d{1,3}|\d{1,3})\b""", RegexOption.IGNORE_CASE)
        for (raw in candidates) {
            val m = rx.find(raw.trim())?.value ?: continue
            var cleaned = m.replace(" ", "").uppercase()
            if (cleaned.startsWith("BUS")) cleaned = cleaned.removePrefix("BUS")
            return cleaned
        }
        return ""
    }

    private fun extractDirectionKtx(leg: JsonObject): String {
        val d1 = leg["direction"]?.jsonPrimitive?.content?.trim().orEmpty()
        if (d1.isNotBlank()) return d1
        return leg["Transportation"]?.jsonObject?.get("direction")?.jsonPrimitive?.content?.trim().orEmpty()
    }

    private fun mapTypeTrKtx(product: JsonObject?, line: String): String {
        if (line.startsWith("U")) return VehicleType.UBAHN.key
        if (line.startsWith("S")) return VehicleType.SBAHN.key
        if (line.startsWith("RB") || line.startsWith("RE")) return VehicleType.RERB.key
        if (line.startsWith("ICE") || line.startsWith("IC") || line.startsWith("ECE") || line.startsWith("EC")) return VehicleType.FERNZUG.key
        if (line.startsWith("STR") || line.startsWith("TRAM")) return VehicleType.STRASSENBAHN.key
        val catOut = product?.get("catOut")?.jsonPrimitive?.content?.trim().orEmpty()
        val typeStr = product?.get("type")?.jsonPrimitive?.content?.trim().orEmpty()
        val cls = product?.get("cls")?.jsonPrimitive?.intOrNull ?: 0
        if (catOut.contains("Tram", ignoreCase = true) || catOut.contains("Straßenbahn", ignoreCase = true) ||
            typeStr.contains("Tram", ignoreCase = true) || cls == 16) return VehicleType.STRASSENBAHN.key
        return VehicleType.BUS.key
    }

    // org.json helpers (for OkHttp responses)
    private fun mapTypeTr(leg: JSONObject, line: String): String {
        if (line.startsWith("U")) return VehicleType.UBAHN.key
        if (line.startsWith("S")) return VehicleType.SBAHN.key
        if (line.startsWith("RB") || line.startsWith("RE")) return VehicleType.RERB.key
        if (line.startsWith("ICE") || line.startsWith("IC") || line.startsWith("ECE") || line.startsWith("EC")) return VehicleType.FERNZUG.key
        if (line.startsWith("STR") || line.startsWith("TRAM")) return VehicleType.STRASSENBAHN.key
        val product = leg.optJSONObject("Product")
        val catOut = product?.optString("catOut", "")?.trim().orEmpty()
        val typeStr = product?.optString("type", "")?.trim().orEmpty()
        val cls = product?.optInt("cls", 0) ?: 0
        if (catOut.contains("Tram", ignoreCase = true) || catOut.contains("Straßenbahn", ignoreCase = true) ||
            typeStr.contains("Tram", ignoreCase = true) || cls == 16) return VehicleType.STRASSENBAHN.key
        return VehicleType.BUS.key
    }

    private fun extractPublicLineCode(leg: JSONObject): String {
        val candidates = mutableListOf<String>()
        candidates += leg.optString("name", "")
        leg.optJSONObject("Product")?.let { p -> candidates += p.optString("name", ""); candidates += p.optString("num", "") }
        val rx = Regex("""\b([A-Za-z]{1,4}-?\s?\d{1,3}|RB\s?\d{1,3}|RE\s?\d{1,3}|S\s?\d{1,2}|U\s?\d{1,2}|X\s?\d{1,3}|\d{1,3})\b""", RegexOption.IGNORE_CASE)
        for (raw in candidates) {
            val m = rx.find(raw.trim())?.value ?: continue
            var cleaned = m.replace(" ", "").uppercase()
            if (cleaned.startsWith("BUS")) cleaned = cleaned.removePrefix("BUS")
            return cleaned
        }
        return ""
    }

    private fun normalizeLineCode(raw: String): String {
        var s = raw.trim().uppercase().replace(" ", "")
        for (prefix in listOf("BUS", "TRAM", "U-BAHN", "S-BAHN", "STRAẞENBAHN", "STRASSENBAHN")) {
            if (s.startsWith(prefix)) s = s.removePrefix(prefix)
        }
        return s
    }

    private fun extractDirection(leg: JSONObject): String {
        val d1 = leg.optString("direction", "").trim()
        if (d1.isNotBlank()) return d1
        return leg.optJSONObject("Transportation")?.optString("direction", "")?.trim().orEmpty()
    }

    private fun legsArray(trip0: JSONObject): JSONArray {
        fun asArray(any: Any): JSONArray = when (any) {
            is JSONArray -> any
            is JSONObject -> JSONArray().put(any)
            else -> throw IllegalStateException("Leg format beklenmedik")
        }
        trip0.opt("Leg")?.let { return asArray(it) }
        trip0.optJSONObject("Legs")?.opt("Leg")?.let { return asArray(it) }
        trip0.optJSONObject("LegList")?.opt("Leg")?.let { return asArray(it) }
        trip0.optJSONObject("legList")?.opt("leg")?.let { return asArray(it) }
        throw IllegalStateException("Leg yok")
    }

    private fun diffMinutesFlexible(dep: String, arr: String): Int {
        fun parse(t: String): LocalTime? {
            val tt = t.trim()
            return try {
                when {
                    tt.length >= 8 -> LocalTime.parse(tt.substring(0, 8))
                    tt.length >= 5 -> LocalTime.parse(tt.substring(0, 5))
                    else -> null
                }
            } catch (_: Exception) { null }
        }
        val d = parse(dep) ?: return 0
        val a = parse(arr) ?: return 0
        var diff = java.time.Duration.between(d, a).toMinutes().toInt()
        if (diff < 0) diff += 24 * 60
        return diff
    }
}
