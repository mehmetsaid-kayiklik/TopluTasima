package com.example.toplutasima.transit.provenance

import com.example.toplutasima.model.Departure
import com.example.toplutasima.model.Segment
import com.example.toplutasima.usecase.TransitRecordCalculations
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Derives field provenance from existing transit values without introducing stored metadata.
 */
class TransitFieldProvenanceUseCase(
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val freshnessPolicy: TransitFreshnessPolicy = TransitFreshnessPolicy()
) {
    fun resolve(input: TransitFieldProvenanceInput): TransitFieldProvenance {
        val baseSource = input.source
        val hasKnownSource = baseSource != TransitFieldSource.UNKNOWN
        val effectiveSource = if (input.isFromCache && hasKnownSource) {
            TransitFieldSource.CACHE
        } else {
            baseSource
        }
        val lastUpdatedAt = input.lastUpdatedAtEpochMillis?.takeIf { it >= 0L }

        return TransitFieldProvenance(
            fieldId = input.fieldId,
            source = effectiveSource,
            lastUpdatedAtEpochMillis = lastUpdatedAt,
            freshness = freshness(lastUpdatedAt),
            isFallback = input.isFallback || (input.isFromCache && hasKnownSource),
            backingSource = baseSource.takeIf { input.isFromCache && hasKnownSource },
            fallbackFor = input.fallbackFor
        )
    }

    fun departureTime(
        fieldId: String,
        departure: Departure?,
        observedAtEpochMillis: Long = nowEpochMillis(),
        isFromCache: Boolean = false
    ): TransitFieldProvenance {
        val source = when {
            departure?.realtime?.isNotBlank() == true -> TransitFieldSource.LIVE_RMV
            departure?.time?.isNotBlank() == true -> TransitFieldSource.PLANNED_RMV
            else -> TransitFieldSource.UNKNOWN
        }
        val plannedFallback = source == TransitFieldSource.PLANNED_RMV
        return resolve(
            TransitFieldProvenanceInput(
                fieldId = fieldId,
                source = source,
                lastUpdatedAtEpochMillis = observedAtEpochMillis.takeIf {
                    source != TransitFieldSource.UNKNOWN
                },
                isFallback = plannedFallback,
                fallbackFor = TransitFieldSource.LIVE_RMV.takeIf { plannedFallback },
                isFromCache = isFromCache
            )
        )
    }

    fun plannedRmv(
        fieldId: String,
        observedAtEpochMillis: Long = nowEpochMillis(),
        isFromCache: Boolean = false
    ): TransitFieldProvenance = resolve(
        TransitFieldProvenanceInput(
            fieldId = fieldId,
            source = TransitFieldSource.PLANNED_RMV,
            lastUpdatedAtEpochMillis = observedAtEpochMillis,
            isFromCache = isFromCache
        )
    )

    fun manual(
        fieldId: String,
        editedAtEpochMillis: Long = nowEpochMillis()
    ): TransitFieldProvenance = resolve(
        TransitFieldProvenanceInput(
            fieldId = fieldId,
            source = TransitFieldSource.MANUAL,
            lastUpdatedAtEpochMillis = editedAtEpochMillis
        )
    )

    fun transitLocation(
        fieldId: String,
        hasLocationResult: Boolean,
        observedAtEpochMillis: Long = nowEpochMillis(),
        isFromCache: Boolean = false
    ): TransitFieldProvenance = resolve(
        TransitFieldProvenanceInput(
            fieldId = fieldId,
            source = if (hasLocationResult) {
                TransitFieldSource.TRANSIT_LOCATION
            } else {
                TransitFieldSource.UNKNOWN
            },
            lastUpdatedAtEpochMillis = observedAtEpochMillis.takeIf { hasLocationResult },
            isFromCache = isFromCache
        )
    )

    /** Provenance for the distance currently displayed from an in-memory RMV segment. */
    fun segmentDistance(
        fieldId: String,
        segment: Segment?,
        observedAtEpochMillis: Long = nowEpochMillis(),
        preferRmvDistance: Boolean = false,
        isFromCache: Boolean = false
    ): TransitFieldProvenance {
        val orsDistanceKm = segment?.distanceKm
        val rmvDistanceKm = segment?.polyDistanceKm
        val hasOrs = orsDistanceKm != null && orsDistanceKm.isFinite() && orsDistanceKm > 0.0
        val hasRmv = rmvDistanceKm != null && rmvDistanceKm.isFinite() && rmvDistanceKm > 0.0
        return distance(
            fieldId = fieldId,
            hasOrsDistance = hasOrs,
            hasRmvDistance = hasRmv,
            preferRmvDistance = preferRmvDistance,
            observedAtEpochMillis = observedAtEpochMillis,
            isFromCache = isFromCache
        )
    }

    /**
     * Provenance for an existing transit record map. Distance parsing reuses
     * [TransitRecordCalculations] instead of duplicating legacy parsing rules.
     */
    fun recordDistance(
        fieldId: String,
        record: Map<String, *>,
        observedAtEpochMillis: Long? = parseUpdatedAt(
            record[TransitRecordCalculations.FIELD_RMV_DISTANCE_UPDATED_AT]
        ),
        preferRmvDistance: Boolean = false,
        isFromCache: Boolean = false
    ): TransitFieldProvenance = distance(
        fieldId = fieldId,
        hasOrsDistance = TransitRecordCalculations.orsDistanceKm(record) != null,
        hasRmvDistance = TransitRecordCalculations.rmvDistanceKm(record) != null,
        preferRmvDistance = preferRmvDistance,
        observedAtEpochMillis = observedAtEpochMillis,
        isFromCache = isFromCache
    )

    fun unknown(fieldId: String): TransitFieldProvenance = resolve(
        TransitFieldProvenanceInput(fieldId = fieldId, source = TransitFieldSource.UNKNOWN)
    )

    private fun distance(
        fieldId: String,
        hasOrsDistance: Boolean,
        hasRmvDistance: Boolean,
        preferRmvDistance: Boolean,
        observedAtEpochMillis: Long?,
        isFromCache: Boolean
    ): TransitFieldProvenance {
        val preferred = if (preferRmvDistance) {
            TransitFieldSource.RMV_DISTANCE
        } else {
            TransitFieldSource.ORS_DISTANCE
        }
        val alternative = if (preferRmvDistance) {
            TransitFieldSource.ORS_DISTANCE
        } else {
            TransitFieldSource.RMV_DISTANCE
        }
        val preferredAvailable = if (preferRmvDistance) hasRmvDistance else hasOrsDistance
        val alternativeAvailable = if (preferRmvDistance) hasOrsDistance else hasRmvDistance
        val source = when {
            preferredAvailable -> preferred
            alternativeAvailable -> alternative
            else -> TransitFieldSource.UNKNOWN
        }
        val usedAlternative = !preferredAvailable && alternativeAvailable

        return resolve(
            TransitFieldProvenanceInput(
                fieldId = fieldId,
                source = source,
                lastUpdatedAtEpochMillis = observedAtEpochMillis?.takeIf {
                    source != TransitFieldSource.UNKNOWN
                },
                isFallback = usedAlternative,
                fallbackFor = preferred.takeIf { usedAlternative },
                isFromCache = isFromCache
            )
        )
    }

    private fun freshness(lastUpdatedAtEpochMillis: Long?): TransitFieldFreshness {
        if (lastUpdatedAtEpochMillis == null) return TransitFieldFreshness.UNKNOWN
        val ageMillis = (nowEpochMillis() - lastUpdatedAtEpochMillis).coerceAtLeast(0L)
        return when {
            ageMillis <= freshnessPolicy.freshForMillis -> TransitFieldFreshness.FRESH
            ageMillis < freshnessPolicy.staleAfterMillis -> TransitFieldFreshness.AGING
            else -> TransitFieldFreshness.STALE
        }
    }

    private fun parseUpdatedAt(value: Any?): Long? {
        val raw = value?.toString()?.trim().orEmpty()
        if (raw.isBlank()) return null
        raw.toLongOrNull()?.let { return it.takeIf { epoch -> epoch >= 0L } }
        runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()?.let { return it }

        for (formatter in LEGACY_DATE_TIME_FORMATTERS) {
            val parsed = runCatching { LocalDateTime.parse(raw, formatter) }.getOrNull() ?: continue
            return parsed.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        return null
    }

    private companion object {
        val LEGACY_DATE_TIME_FORMATTERS: List<DateTimeFormatter> = listOf(
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME
        )
    }
}
