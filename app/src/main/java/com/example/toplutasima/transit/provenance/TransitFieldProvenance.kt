package com.example.toplutasima.transit.provenance

/**
 * A transit-only description of where a value currently shown to the user came from.
 *
 * This is deliberately not persisted. Sprint 1 provenance is derived from the transit data that
 * already exists, so adding it does not require a Room or Firestore schema change.
 */
enum class TransitFieldSource {
    LIVE_RMV,
    PLANNED_RMV,
    TRANSIT_LOCATION,
    RMV_DISTANCE,
    ORS_DISTANCE,
    CACHE,
    MANUAL,
    UNKNOWN
}

enum class TransitFieldFreshness {
    FRESH,
    AGING,
    STALE,
    UNKNOWN
}

data class TransitFieldProvenance(
    val fieldId: String,
    val source: TransitFieldSource,
    val lastUpdatedAtEpochMillis: Long? = null,
    val freshness: TransitFieldFreshness = TransitFieldFreshness.UNKNOWN,
    val isFallback: Boolean = false,
    /** The source represented by a cached value, if known. */
    val backingSource: TransitFieldSource? = null,
    /** The preferred source that was unavailable when a fallback was selected, if known. */
    val fallbackFor: TransitFieldSource? = null
)

data class TransitFreshnessPolicy(
    val freshForMillis: Long = 2 * 60 * 1_000L,
    val staleAfterMillis: Long = 10 * 60 * 1_000L
) {
    init {
        require(freshForMillis >= 0L) { "freshForMillis must not be negative" }
        require(staleAfterMillis >= freshForMillis) {
            "staleAfterMillis must be greater than or equal to freshForMillis"
        }
    }
}

data class TransitFieldProvenanceInput(
    val fieldId: String,
    val source: TransitFieldSource,
    val lastUpdatedAtEpochMillis: Long? = null,
    val isFallback: Boolean = false,
    val fallbackFor: TransitFieldSource? = null,
    val isFromCache: Boolean = false
)
