package com.example.toplutasima.transit

/**
 * Transit-only rollback switches for Sprint 1.
 *
 * Keeping these switches next to the feature code lets a release disable one slice without
 * changing the PersonalTrip branches or reverting the underlying backwards-compatible APIs.
 */
object TransitFeatureFlags {
    const val PROVENANCE_BADGES = true
    const val PRE_SAVE_VALIDATION = true
    const val LIVE_ROOM_FLOWS = true
    const val SYNC_RECEIPTS = true
    const val SYNC_DELETE_RECEIPTS = true
    const val LIVE_TRANSIT_SUMMARIES = true
    const val POST_SAVE_DATA_HEALTH = true
}
