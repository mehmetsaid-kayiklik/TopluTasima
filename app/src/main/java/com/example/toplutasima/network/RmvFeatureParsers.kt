package com.example.toplutasima.network

import com.example.toplutasima.model.JourneyMatchCandidate
import com.example.toplutasima.model.LocationOption
import com.example.toplutasima.model.LocationOptionKind
import com.example.toplutasima.model.ReachabilityPoint
import com.example.toplutasima.model.ReachabilityResult
import com.example.toplutasima.model.TransitAlert
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import java.util.Locale
import kotlin.math.abs

object RmvFeatureParsers {
    fun normalizeLineToken(value: String): String =
        value.uppercase(Locale.ROOT)
            .replace(Regex("\\s+"), "")
            .removePrefix("BUS")
            .removePrefix("TRAM")

    fun lineMatchesAlert(line: String, text: String): Boolean {
        val normalizedLine = normalizeLineToken(line)
        if (normalizedLine.isBlank()) return false
        val tokens = Regex("[A-Za-z]{0,4}\\s?\\d{1,4}")
            .findAll(text)
            .map { normalizeLineToken(it.value) }
            .toSet()
        return normalizedLine in tokens || text.uppercase(Locale.ROOT).contains(normalizedLine)
    }

    fun parseTransitAlerts(root: JsonObject, line: String): List<TransitAlert> {
        val candidates = mutableListOf<JsonObject>()
        collectObjects(root, candidates)
        val seen = mutableSetOf<String>()
        return candidates.mapNotNull { obj ->
            val text = collectStrings(obj).joinToString(" ").replace(Regex("\\s+"), " ").trim()
            if (text.length < 8 || !lineMatchesAlert(line, text)) return@mapNotNull null
            val title = firstString(obj, "summary", "title", "head", "header", "subject")
                ?: text.take(90)
            val id = firstString(obj, "id", "hid", "messageId") ?: title
            if (!seen.add(id)) return@mapNotNull null
            TransitAlert(
                id = id,
                title = title,
                detail = text,
                line = normalizeLineToken(line),
                severity = firstString(obj, "priority", "severity", "type") ?: "info"
            )
        }.take(5)
    }

    fun parseLocationOptions(root: JsonObject): List<LocationOption> {
        val result = mutableListOf<LocationOption>()
        fun addLocation(obj: JsonObject, forcedKind: LocationOptionKind? = null) {
            val name = string(obj, "name", "displayName", "title") ?: return
            val id = string(obj, "id", "extId", "lid") ?: name
            val rawType = string(obj, "type", "locationType")?.uppercase(Locale.ROOT).orEmpty()
            val kind = forcedKind ?: when {
                rawType.contains("ADR") || rawType.contains("ADDRESS") -> LocationOptionKind.ADDRESS
                rawType.contains("POI") -> LocationOptionKind.POI
                else -> LocationOptionKind.STOP
            }
            result += LocationOption(
                id = id,
                name = name,
                kind = kind,
                lat = double(obj, "lat", "y"),
                lon = double(obj, "lon", "lng", "x")
            )
        }

        elements(root["StopLocation"]).forEach { addLocation(it, LocationOptionKind.STOP) }
        elements(root["CoordLocation"]).forEach { addLocation(it, null) }
        elements(root["stopLocationOrCoordLocation"]).forEach { wrapper ->
            (wrapper["StopLocation"] as? JsonObject)?.let { addLocation(it, LocationOptionKind.STOP) }
            (wrapper["CoordLocation"] as? JsonObject)?.let { addLocation(it, null) }
            addLocation(wrapper, null)
        }
        return result.distinctBy { "${it.kind}:${it.id}:${it.name}" }
    }

    fun parseJourneyMatchCandidates(root: JsonObject, requestId: String): List<JourneyMatchCandidate> {
        val objects = mutableListOf<JsonObject>()
        collectObjects(root, objects)
        return objects.mapNotNull { obj ->
            val line = string(obj, "line", "name", "num") ?: productName(obj) ?: return@mapNotNull null
            val normalizedLine = normalizeLineToken(line)
            if (normalizedLine.isBlank() || normalizedLine.none { it.isDigit() }) return@mapNotNull null
            JourneyMatchCandidate(
                id = string(obj, "id", "jid", "ctxRecon") ?: normalizedLine,
                line = normalizedLine,
                direction = string(obj, "direction", "dirTxt", "dir") ?: "",
                fromStop = nestedName(obj, "Origin") ?: nestedName(obj, "origin") ?: "",
                toStop = nestedName(obj, "Destination") ?: nestedName(obj, "destination") ?: "",
                confidence = int(obj, "confidence", "score", "matchQuality") ?: 0,
                requestId = requestId
            )
        }.distinctBy { "${it.id}:${it.line}:${it.direction}" }.take(5)
    }

    fun parseReachability(root: JsonObject, originLat: Double, originLon: Double, minutes: Int): ReachabilityResult {
        val options = parseLocationOptions(root)
        val points = options.mapNotNull { option ->
            val lat = option.lat ?: return@mapNotNull null
            val lon = option.lon ?: return@mapNotNull null
            if (abs(lat) < 0.0001 && abs(lon) < 0.0001) return@mapNotNull null
            ReachabilityPoint(option.name, lat, lon, minutes)
        }.take(80)
        return ReachabilityResult(
            originLat = originLat,
            originLon = originLon,
            minutes = minutes,
            points = points,
            supported = true,
            message = if (points.isEmpty()) "Erisilebilir nokta bulunamadi" else ""
        )
    }

    private fun elements(el: JsonElement?): List<JsonObject> = when (el) {
        is JsonArray -> el.mapNotNull { it as? JsonObject }
        is JsonObject -> listOf(el)
        else -> emptyList()
    }

    private fun collectObjects(el: JsonElement?, out: MutableList<JsonObject>) {
        when (el) {
            is JsonObject -> {
                out += el
                el.values.forEach { collectObjects(it, out) }
            }
            is JsonArray -> el.forEach { collectObjects(it, out) }
            else -> {}
        }
    }

    private fun collectStrings(el: JsonElement?): List<String> = when (el) {
        is JsonPrimitive -> listOfNotNull(el.takeIf { it.isString }?.content)
        is JsonArray -> el.flatMap { collectStrings(it) }
        is JsonObject -> el.values.flatMap { collectStrings(it) }
        else -> emptyList()
    }

    private fun firstString(obj: JsonObject, vararg keys: String): String? =
        keys.firstNotNullOfOrNull { string(obj, it) }

    private fun string(obj: JsonObject, vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key ->
            (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.takeIf { it.isNotBlank() }
        }

    private fun int(obj: JsonObject, vararg keys: String): Int? =
        keys.firstNotNullOfOrNull { key -> (obj[key] as? JsonPrimitive)?.intOrNull }

    private fun double(obj: JsonObject, vararg keys: String): Double? =
        keys.firstNotNullOfOrNull { key -> (obj[key] as? JsonPrimitive)?.doubleOrNull }

    private fun productName(obj: JsonObject): String? =
        (obj["Product"] as? JsonObject)?.let { string(it, "name", "num") }
            ?: (obj["ProductAtStop"] as? JsonObject)?.let { string(it, "name", "num") }

    private fun nestedName(obj: JsonObject, key: String): String? =
        (obj[key] as? JsonObject)?.let { string(it, "name") }
}
