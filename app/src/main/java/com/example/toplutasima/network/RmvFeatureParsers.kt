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
import kotlin.math.abs

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
        val explicitlyPrefixedTokens = Regex(
            "(?<![\\p{L}\\p{N}-])(?:[A-Z]{1,4}-\\s*\\d{1,4}|(?:BUS|TRAM|RB|RE|S|U|M|X|N)\\s*\\d{1,4})(?![\\p{L}\\p{N}-])",
            RegexOption.IGNORE_CASE
        ).findAll(text).map { normalizeLineToken(it.value) }
        val labelledTokens = Regex(
            "(?<![\\p{L}\\p{N}-])(?:LINE|LINES|LINIE|LINIEN|HAT)\\s*[:#]?\\s*" +
                "([A-Z]{1,4}-\\s*\\d{1,4}|(?:BUS|TRAM|RB|RE|S|U|M|X|N)\\s*\\d{1,4}|\\d{1,4})" +
                "(?![\\p{L}\\p{N}-])",
            RegexOption.IGNORE_CASE
        ).findAll(text).map { normalizeLineToken(it.groupValues[1]) }
        val tokens = (explicitlyPrefixedTokens + labelledTokens).toSet()
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

    /**
     * Parses only messages attached to the selected journeyDetail response.
     * If HAFAS supplies message product metadata, its internal line/operator refs
     * must also match exactly. Missing metadata is accepted because attachment to
     * the journey detail is already the authoritative scope.
     */
    fun parseJourneyTransitAlerts(
        root: JsonObject,
        displayLine: String,
        lineRef: String,
        operatorRef: String,
        onDecision: (messageId: String, included: Boolean, reason: String) -> Unit = { _, _, _ -> }
    ): List<TransitAlert> {
        val journeyDetails = mutableListOf<JsonObject>()
        collectNamedObjects(root, "JourneyDetail", journeyDetails)

        val attachedCandidates = mutableListOf<JsonObject>()
        val attachedMessageIds = mutableSetOf<String>()
        journeyDetails.forEach { journey ->
            collectMessageObjects(
                el = journey,
                inMessageScope = false,
                objects = attachedCandidates,
                referencedIds = attachedMessageIds
            )
        }

        val allMessageCandidates = mutableListOf<JsonObject>()
        collectMessageObjects(
            el = root,
            inMessageScope = false,
            objects = allMessageCandidates,
            referencedIds = mutableSetOf()
        )

        val attachedKeys = attachedCandidates.mapTo(mutableSetOf()) { alertKey(it) }
        val targetLineRef = normalizeIdentityRef(lineRef)
        val targetOperatorRef = normalizeIdentityRef(operatorRef)
        val seen = mutableSetOf<String>()
        val alerts = mutableListOf<TransitAlert>()

        (attachedCandidates + allMessageCandidates).forEach { obj ->
            val title = cleanAlertText(firstString(obj, *alertTitleKeys.toTypedArray()))
                ?: return@forEach
            val id = firstString(obj, "id", "hid", "messageId") ?: title
            if (!seen.add(id)) return@forEach

            val key = alertKey(obj)
            val isAttached = key in attachedKeys || id in attachedMessageIds
            if (!isAttached) {
                onDecision(id, false, "not attached to selected journeyDetail")
                return@forEach
            }

            val messageLineRefs = identityRefs(obj, lineIdentityKeys)
            if (targetLineRef.isNotBlank() && messageLineRefs.isNotEmpty() && targetLineRef !in messageLineRefs) {
                onDecision(id, false, "lineRef mismatch; messageRefs=$messageLineRefs")
                return@forEach
            }

            val messageOperatorRefs = identityRefs(obj, operatorIdentityKeys)
            if (
                targetOperatorRef.isNotBlank() &&
                messageOperatorRefs.isNotEmpty() &&
                targetOperatorRef !in messageOperatorRefs
            ) {
                onDecision(id, false, "operatorRef mismatch; messageRefs=$messageOperatorRefs")
                return@forEach
            }

            val detail = collectStringsForKeys(obj, alertDetailKeys)
                .mapNotNull { cleanAlertText(it) }
                .filterNot { it.equals(title, ignoreCase = true) }
                .distinct()
                .joinToString(" ")
                .trim()

            onDecision(id, true, "attached journey message with matching exact refs")
            if (alerts.size < 5) {
                alerts += TransitAlert(
                    id = id,
                    title = title,
                    detail = detail,
                    line = normalizeLineToken(displayLine),
                    severity = firstString(obj, "priority", "severity", "type") ?: "info"
                )
            }
        }
        return alerts
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
                lat = coordinate(obj, "lat", "y"),
                lon = coordinate(obj, "lon", "lng", "x")
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
            val line = journeyLineCandidate(obj) ?: return@mapNotNull null
            val normalizedLine = normalizeLineToken(line)
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

    private fun journeyLineCandidate(obj: JsonObject): String? {
        val explicitLine = string(obj, "line", "num") ?: productName(obj)
        if (explicitLine != null) return explicitLine.takeIf(::isValidLineCode)

        val name = string(obj, "name") ?: return null
        val hasJourneyContext = string(obj, "direction", "dirTxt", "dir") != null ||
            (nestedName(obj, "Origin") != null && nestedName(obj, "Destination") != null) ||
            string(obj, "jid", "ctxRecon") != null
        return name.takeIf { hasJourneyContext && isValidLineCode(it) }
    }

    private fun isValidLineCode(value: String): Boolean =
        normalizeLineToken(value).matches(Regex("(?:[A-Z]{1,4}-?\\d{1,4}|\\d{1,4})"))

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

    private val lineIdentityKeys = setOf("lineid", "lineref", "matchid")
    private val operatorIdentityKeys = setOf("operatorcode", "operatorid", "admin")

    private fun collectNamedObjects(el: JsonElement?, name: String, out: MutableList<JsonObject>) {
        when (el) {
            is JsonObject -> el.forEach { (key, value) ->
                if (key.equals(name, ignoreCase = true)) {
                    out += elements(value)
                }
                collectNamedObjects(value, name, out)
            }
            is JsonArray -> el.forEach { collectNamedObjects(it, name, out) }
            else -> {}
        }
    }

    private fun collectMessageObjects(
        el: JsonElement?,
        inMessageScope: Boolean,
        objects: MutableList<JsonObject>,
        referencedIds: MutableSet<String>
    ) {
        when (el) {
            is JsonObject -> {
                if (inMessageScope) {
                    firstString(el, "id", "hid", "messageId")?.let(referencedIds::add)
                    if (el.keys.any { it.lowercase(Locale.ROOT) in alertTitleKeys }) objects += el
                }
                el.forEach { (key, value) ->
                    collectMessageObjects(
                        el = value,
                        inMessageScope = inMessageScope || isMessageContainerKey(key),
                        objects = objects,
                        referencedIds = referencedIds
                    )
                }
            }
            is JsonArray -> el.forEach {
                collectMessageObjects(it, inMessageScope, objects, referencedIds)
            }
            else -> {}
        }
    }

    private fun isMessageContainerKey(key: String): Boolean {
        val normalized = key.lowercase(Locale.ROOT)
        return "message" in normalized || normalized == "him"
    }

    private fun alertKey(obj: JsonObject): String =
        firstString(obj, "id", "hid", "messageId")
            ?: firstString(obj, *alertTitleKeys.toTypedArray())
            ?: obj.toString()

    private fun identityRefs(obj: JsonObject, acceptedKeys: Set<String>): Set<String> {
        val refs = mutableSetOf<String>()
        fun walk(el: JsonElement?) {
            when (el) {
                is JsonObject -> el.forEach { (key, value) ->
                    if (key.lowercase(Locale.ROOT) in acceptedKeys) {
                        collectStrings(value)
                            .map(::normalizeIdentityRef)
                            .filterTo(refs) { it.isNotBlank() }
                    } else {
                        walk(value)
                    }
                }
                is JsonArray -> el.forEach(::walk)
                else -> {}
            }
        }
        walk(obj)
        return refs
    }

    private fun normalizeIdentityRef(value: String): String =
        value.trim().uppercase(Locale.ROOT).replace(Regex("\\s+"), "")

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

    private fun coordinate(obj: JsonObject, vararg keys: String): Double? =
        keys.firstNotNullOfOrNull { key ->
            val raw = obj.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }
                ?.value
                ?.let { it as? JsonPrimitive }
                ?.doubleOrNull
                ?: return@firstNotNullOfOrNull null
            normalizeCoordinate(raw)
        }

    private fun normalizeCoordinate(value: Double): Double =
        if (abs(value) > 1_000.0) value / 1_000_000.0 else value

    private fun productName(obj: JsonObject): String? =
        (obj["Product"] as? JsonObject)?.let { string(it, "name", "num") }
            ?: (obj["ProductAtStop"] as? JsonObject)?.let { string(it, "name", "num") }

    private fun nestedName(obj: JsonObject, key: String): String? =
        (obj[key] as? JsonObject)?.let { string(it, "name") }
}
