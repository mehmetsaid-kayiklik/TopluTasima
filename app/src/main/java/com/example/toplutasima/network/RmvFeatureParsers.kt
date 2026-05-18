package com.example.toplutasima.network

import com.example.toplutasima.model.JourneyMatchCandidate
import com.example.toplutasima.model.LocationOption
import com.example.toplutasima.model.LocationOptionKind
import com.example.toplutasima.model.TransitAlert
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import java.util.Locale

object RmvFeatureParsers {
    private val alertTitleKeys = setOf("summary", "title", "head", "header", "subject")
    private val alertDetailKeys = setOf(
        "detail",
        "description",
        "text",
        "message",
        "messagetext",
        "shorttext",
        "longtext",
        "content",
        "body",
        "note"
    )
    private val alertTextKeys = alertTitleKeys + alertDetailKeys

    fun normalizeLineToken(value: String): String =
        value.uppercase(Locale.ROOT)
            .replace(Regex("\\s+"), "")
            .removePrefix("BUS")
            .removePrefix("TRAM")

    fun lineMatchesAlert(line: String, text: String): Boolean {
        val normalizedLine = normalizeLineToken(line)
        if (normalizedLine.isBlank()) return false
        val tokens = Regex("\\b(?:BUS|TRAM|S|U|RB|RE|M|X|N)?\\s*\\d{1,4}\\b", RegexOption.IGNORE_CASE)
            .findAll(text)
            .map { normalizeLineToken(it.value) }
            .toSet()
        return normalizedLine in tokens
    }

    fun parseTransitAlerts(root: JsonObject, line: String): List<TransitAlert> {
        val candidates = mutableListOf<JsonObject>()
        collectAlertObjects(root, candidates)
        val seen = mutableSetOf<String>()
        return candidates.mapNotNull { obj ->
            val title = cleanAlertText(firstString(obj, *alertTitleKeys.toTypedArray()))
                ?: return@mapNotNull null
            val detailParts = collectStringsForKeys(obj, alertDetailKeys)
                .mapNotNull { cleanAlertText(it) }
                .filterNot { it.equals(title, ignoreCase = true) }
                .distinct()
            val detail = detailParts.joinToString(" ").trim()
            val searchableText = listOf(title, detail).joinToString(" ")
            if (!lineMatchesAlert(line, searchableText)) return@mapNotNull null
            val id = firstString(obj, "id", "hid", "messageId") ?: title
            if (!seen.add(id)) return@mapNotNull null
            TransitAlert(
                id = id,
                title = title,
                detail = detail,
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

    private fun collectAlertObjects(el: JsonElement?, out: MutableList<JsonObject>) {
        when (el) {
            is JsonObject -> {
                if (el.keys.any { it.lowercase(Locale.ROOT) in alertTextKeys }) out += el
                el.values.forEach { collectAlertObjects(it, out) }
            }
            is JsonArray -> el.forEach { collectAlertObjects(it, out) }
            else -> {}
        }
    }

    private fun collectStrings(el: JsonElement?): List<String> = when (el) {
        is JsonPrimitive -> listOfNotNull(el.takeIf { it.isString }?.content)
        is JsonArray -> el.flatMap { collectStrings(it) }
        is JsonObject -> el.values.flatMap { collectStrings(it) }
        else -> emptyList()
    }

    private fun collectStringsForKeys(el: JsonElement?, keys: Set<String>): List<String> {
        val result = mutableListOf<String>()
        fun walk(node: JsonElement?, acceptedParent: Boolean) {
            when (node) {
                is JsonPrimitive -> {
                    if (acceptedParent && node.isString) result += node.content
                }
                is JsonArray -> node.forEach { walk(it, acceptedParent) }
                is JsonObject -> node.forEach { (key, value) ->
                    walk(value, acceptedParent || key.lowercase(Locale.ROOT) in keys)
                }
                else -> {}
            }
        }
        walk(el, false)
        return result
    }

    private fun cleanAlertText(value: String?): String? {
        val cleaned = value
            ?.replace(Regex("<[^>]+>"), " ")
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.takeIf { it.length >= 4 }
            ?: return null
        if (looksTechnicalAlertText(cleaned)) return null
        return cleaned
    }

    private fun looksTechnicalAlertText(value: String): Boolean {
        val upper = value.uppercase(Locale.ROOT)
        if ("PROD_" in upper || "@X=" in upper || "@Y=" in upper || "@L=" in upper) return true
        if (Regex("#[0-9A-F]{6}", RegexOption.IGNORE_CASE).findAll(value).count() >= 2) return true
        if (Regex("\\bde:\\d{2,}:").containsMatchIn(value)) return true
        return false
    }

    private fun firstString(obj: JsonObject, vararg keys: String): String? =
        keys.firstNotNullOfOrNull { string(obj, it) }

    private fun string(obj: JsonObject, vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key ->
            obj.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }
                ?.value
                ?.let { it as? JsonPrimitive }
                ?.takeIf { it.isString }
                ?.content
                ?.trim()
                ?.takeIf { it.isNotBlank() }
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
