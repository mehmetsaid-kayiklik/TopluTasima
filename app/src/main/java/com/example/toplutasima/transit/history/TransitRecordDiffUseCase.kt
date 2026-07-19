package com.example.toplutasima.transit.history

import java.security.MessageDigest

/** Builds a minimal, stable field diff without retaining either complete record object. */
class TransitRecordDiffUseCase {
    fun diff(
        before: Map<String, Any?>?,
        after: Map<String, Any?>,
        fieldIds: Set<String>? = null,
        oldProvenance: Map<String, TransitHistoryProvenanceEvidence> = emptyMap(),
        newProvenance: Map<String, TransitHistoryProvenanceEvidence> = emptyMap()
    ): List<TransitFieldChange> {
        val selectedFields = (fieldIds ?: buildSet {
            before?.keys?.let(::addAll)
            addAll(after.keys)
        }).intersect(TRACKED_FIELDS)

        return selectedFields.sorted().mapNotNull { fieldId ->
            val oldValue = valueFor(before, fieldId)
            val newValue = valueFor(after, fieldId)
            val oldEvidence = oldProvenance[fieldId]?.takeIf { it.isDurable }
            val newEvidence = newProvenance[fieldId]?.takeIf { it.isDurable }
            if (oldValue == newValue && oldEvidence == newEvidence) {
                null
            } else {
                TransitFieldChange(
                    fieldId = fieldId,
                    oldValue = oldValue,
                    newValue = newValue,
                    oldProvenance = oldEvidence,
                    newProvenance = newEvidence
                )
            }
        }
    }

    private fun valueFor(values: Map<String, Any?>?, fieldId: String): TransitHistoryValue = when {
        values == null || !values.containsKey(fieldId) -> TransitHistoryValue.unknown()
        else -> TransitHistoryValue.fromKnownField(values[fieldId])
    }

    companion object {
        val TRACKED_FIELDS: Set<String> = linkedSetOf(
            "tarih",
            "tur",
            "hat",
            "binisDuragi",
            "inisDuragi",
            "planlananBinis",
            "gercekBinis",
            "planlananInis",
            "gercekInis",
            "planlananYolSuresi",
            "gercekYolSuresi",
            "gecikme",
            "mesafe",
            "orsMesafeKm",
            "rmvMesafeKm",
            "not"
        )
    }
}

internal object TransitChangeEventId {
    fun create(draft: TransitChangeEventDraft, changes: List<TransitFieldChange>): String {
        val material = buildString {
            appendPart(draft.userId)
            appendPart(draft.recordId)
            appendPart(draft.operation.name)
            val stableKey = draft.deduplicationKey?.takeIf { it.isNotBlank() }
            if (stableKey != null) {
                appendPart("dedupe")
                appendPart(stableKey)
            } else {
                appendPart(draft.source.name)
                appendPart(draft.occurredAtEpochMillis.toString())
                changes.sortedBy { it.fieldId }.forEach { change ->
                    appendPart(change.fieldId)
                    appendPart(change.oldValue.state.name)
                    appendPart(change.oldValue.value.orEmpty())
                    appendPart(change.newValue.state.name)
                    appendPart(change.newValue.value.orEmpty())
                    appendPart(change.oldProvenance?.source?.name.orEmpty())
                    appendPart(change.oldProvenance?.durability?.name.orEmpty())
                    appendPart(change.newProvenance?.source?.name.orEmpty())
                    appendPart(change.newProvenance?.durability?.name.orEmpty())
                }
            }
        }
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(material.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
        return "transit-${hash.take(EVENT_ID_HASH_LENGTH)}"
    }

    private fun StringBuilder.appendPart(value: String) {
        append(value.length)
        append(':')
        append(value)
        append('|')
    }

    private const val EVENT_ID_HASH_LENGTH = 32
}
