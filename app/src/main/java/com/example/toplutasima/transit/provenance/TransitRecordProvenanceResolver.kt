package com.example.toplutasima.transit.provenance

import com.example.toplutasima.usecase.TransitRecordCalculations

/**
 * Resolves field history without guessing. Matching session metadata wins, explicit structured
 * RMV/ORS distance fields are the only historical evidence, and everything else stays UNKNOWN.
 */
class TransitRecordProvenanceResolver(
    private val provenanceUseCase: TransitFieldProvenanceUseCase = TransitFieldProvenanceUseCase()
) {
    fun resolveFields(
        userId: String,
        localRecordId: String,
        record: Map<String, *>,
        store: TransitRecordProvenanceStore? = null,
        fieldIds: Set<String> = DEFAULT_TRACKED_FIELDS,
        enabled: Boolean = true
    ): Map<String, TransitFieldProvenance> {
        if (!enabled) return emptyMap()
        val session = store?.matchingProvenance(userId, localRecordId, record).orEmpty()

        return fieldIds.associateWith { fieldId ->
            session[fieldId]?.let(::refreshFreshness)
                ?: explicitHistoricalEvidence(fieldId, record)
                ?: provenanceUseCase.unknown(fieldId)
        }
    }

    private fun refreshFreshness(value: TransitFieldProvenance): TransitFieldProvenance {
        val cachedBackingSource = value.backingSource
        val inputSource = if (value.source == TransitFieldSource.CACHE && cachedBackingSource != null) {
            cachedBackingSource
        } else {
            value.source
        }
        return provenanceUseCase.resolve(
            TransitFieldProvenanceInput(
                fieldId = value.fieldId,
                source = inputSource,
                lastUpdatedAtEpochMillis = value.lastUpdatedAtEpochMillis,
                isFallback = value.isFallback,
                fallbackFor = value.fallbackFor,
                isFromCache = value.source == TransitFieldSource.CACHE && cachedBackingSource != null
            )
        ).let { refreshed ->
            if (value.source == TransitFieldSource.CACHE && cachedBackingSource == null) {
                refreshed.copy(source = TransitFieldSource.CACHE, backingSource = null)
            } else {
                refreshed
            }
        }
    }

    private fun explicitHistoricalEvidence(
        fieldId: String,
        record: Map<String, *>
    ): TransitFieldProvenance? = when (fieldId) {
        FIELD_DISTANCE -> resolvePreferredDistance(record)
        TransitRecordCalculations.FIELD_ORS_DISTANCE_KM,
        TransitRecordCalculations.FIELD_ORS_DISTANCE_TEXT -> {
            if (TransitRecordCalculations.parseDistanceKm(record[fieldId]) != null) {
                provenanceUseCase.resolve(
                    TransitFieldProvenanceInput(
                        fieldId = fieldId,
                        source = TransitFieldSource.ORS_DISTANCE
                    )
                )
            } else {
                null
            }
        }
        TransitRecordCalculations.FIELD_RMV_DISTANCE_KM,
        TransitRecordCalculations.FIELD_RMV_DISTANCE_TEXT -> {
            if (TransitRecordCalculations.parseDistanceKm(record[fieldId]) != null) {
                provenanceUseCase.recordDistance(
                    fieldId = fieldId,
                    record = record,
                    preferRmvDistance = true
                ).takeIf { it.source == TransitFieldSource.RMV_DISTANCE }
            } else {
                null
            }
        }
        else -> null
    }

    private fun resolvePreferredDistance(record: Map<String, *>): TransitFieldProvenance? {
        val hasOrs = TransitRecordCalculations.parseDistanceKm(
            record[TransitRecordCalculations.FIELD_ORS_DISTANCE_KM]
        ) != null
        val hasRmv = TransitRecordCalculations.parseDistanceKm(
            record[TransitRecordCalculations.FIELD_RMV_DISTANCE_KM]
        ) != null
        if (!hasOrs && !hasRmv) return null

        val resolved = provenanceUseCase.recordDistance(
            fieldId = FIELD_DISTANCE,
            record = record,
            preferRmvDistance = false
        )
        // The only stored update timestamp belongs to the RMV distance. Do not attach it to an ORS
        // value just because both distance fields happen to exist on the record.
        return if (resolved.source == TransitFieldSource.ORS_DISTANCE) {
            provenanceUseCase.resolve(
                TransitFieldProvenanceInput(
                    fieldId = FIELD_DISTANCE,
                    source = TransitFieldSource.ORS_DISTANCE,
                    isFallback = resolved.isFallback,
                    fallbackFor = resolved.fallbackFor
                )
            )
        } else {
            resolved
        }
    }

    companion object {
        const val FIELD_DATE = "tarih"
        const val FIELD_LINE = "hat"
        const val FIELD_BOARDING_STOP = "binisDuragi"
        const val FIELD_ALIGHTING_STOP = "inisDuragi"
        const val FIELD_PLANNED_DEPARTURE = "planlananBinis"
        const val FIELD_PLANNED_ARRIVAL = "planlananInis"
        const val FIELD_ACTUAL_DEPARTURE = "gercekBinis"
        const val FIELD_ACTUAL_ARRIVAL = "gercekInis"
        const val FIELD_DISTANCE = "mesafe"

        val DEFAULT_TRACKED_FIELDS: Set<String> = linkedSetOf(
            FIELD_DATE,
            FIELD_LINE,
            FIELD_BOARDING_STOP,
            FIELD_ALIGHTING_STOP,
            FIELD_PLANNED_DEPARTURE,
            FIELD_PLANNED_ARRIVAL,
            FIELD_ACTUAL_DEPARTURE,
            FIELD_ACTUAL_ARRIVAL,
            FIELD_DISTANCE,
            TransitRecordCalculations.FIELD_ORS_DISTANCE_KM,
            TransitRecordCalculations.FIELD_RMV_DISTANCE_KM
        )
    }
}
