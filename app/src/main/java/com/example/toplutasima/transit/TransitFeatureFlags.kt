package com.example.toplutasima.transit

/**
 * Transit-only build-time rollback switches for the staged transit improvements.
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
    const val TRANSIT_INSIGHTS = true
    const val TRANSIT_CHANGE_HISTORY = true
    const val TRANSIT_EXPORT = true
    const val TRANSIT_DUPLICATE_RESOLUTION = true
}
