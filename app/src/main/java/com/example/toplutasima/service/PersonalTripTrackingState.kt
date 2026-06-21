package com.example.toplutasima.service

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import android.util.Log

object PersonalTripTrackingState {
    private const val TAG = "PersonalTripState"
    private const val PREFS_NAME = "personal_trip_tracking_state"
    private const val KEY_IS_TRACKING = "is_tracking"
    private const val KEY_IN_VEHICLE_SESSION = "in_vehicle_session"
    private const val KEY_MANUALLY_STOPPED_THIS_SESSION = "manually_stopped_this_session"
    private const val KEY_PENDING_EXIT_DEADLINE_MS = "pending_exit_deadline_ms"

    fun isTracking(context: Context): Boolean =
        prefs(context).getBoolean(KEY_IS_TRACKING, false)

    fun setTracking(context: Context, tracking: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_IS_TRACKING, tracking)
            .apply()
        requestTileRefresh(context)
    }

    fun isVehicleSessionActive(context: Context): Boolean =
        prefs(context).getBoolean(KEY_IN_VEHICLE_SESSION, false)

    fun markVehicleEnter(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_IN_VEHICLE_SESSION, true)
            .putBoolean(KEY_MANUALLY_STOPPED_THIS_SESSION, false)
            .remove(KEY_PENDING_EXIT_DEADLINE_MS)
            .apply()
    }

    fun markVehicleExit(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_IN_VEHICLE_SESSION, false)
            .putBoolean(KEY_MANUALLY_STOPPED_THIS_SESSION, false)
            .remove(KEY_PENDING_EXIT_DEADLINE_MS)
            .apply()
    }

    fun isManuallyStoppedThisSession(context: Context): Boolean =
        prefs(context).getBoolean(KEY_MANUALLY_STOPPED_THIS_SESSION, false)

    fun markManualStop(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_MANUALLY_STOPPED_THIS_SESSION, true)
            .apply()
    }

    fun clearManualStop(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_MANUALLY_STOPPED_THIS_SESSION, false)
            .apply()
    }

    fun pendingExitDeadlineMs(context: Context): Long =
        prefs(context).getLong(KEY_PENDING_EXIT_DEADLINE_MS, 0L)

    fun setPendingExitDeadlineMs(context: Context, deadlineMs: Long) {
        prefs(context).edit()
            .putLong(KEY_PENDING_EXIT_DEADLINE_MS, deadlineMs)
            .apply()
    }

    fun clearPendingExit(context: Context) {
        prefs(context).edit()
            .remove(KEY_PENDING_EXIT_DEADLINE_MS)
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun requestTileRefresh(context: Context) {
        try {
            TileService.requestListeningState(
                context.applicationContext,
                ComponentName(context.applicationContext, PersonalTripQuickTile::class.java)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Quick Settings tile refresh request failed: ${e.message}")
        }
    }
}
