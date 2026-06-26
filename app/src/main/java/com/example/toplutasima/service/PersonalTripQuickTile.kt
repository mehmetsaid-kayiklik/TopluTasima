package com.example.toplutasima.service

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.example.toplutasima.BuildConfig
import com.example.toplutasima.R
import com.example.toplutasima.diagnostics.PersonalTripTrackerLogger

class PersonalTripQuickTile : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val tracking = PersonalTripTrackingState.isTracking(this)
        if (tracking) {
            log("Quick Settings manual STOP requested")
            PersonalTripTrackingState.markManualStop(this)
            PersonalTripTrackingState.setTracking(this, false)
            try {
                PersonalTripActionIntents.stopPersonalTripService(this)
            } catch (e: Exception) {
                log("Quick Settings manual STOP failed: ${e.message}")
            }
        } else {
            log("Quick Settings manual START requested")
            PersonalTripTrackingState.clearManualStop(this)
            if (!PersonalTripPermissionGuard.hasLocationPermission(this)) {
                PersonalTripPermissionGuard.handleMissingLocationPermission(
                    context = this,
                    source = "quick_settings_tile",
                    notifyUser = true
                )
                updateTile()
                return
            }
            try {
                val started = PersonalTripActionIntents.startPersonalTripService(
                    context = this,
                    source = "quick_settings_tile",
                    notifyUser = true
                )
                PersonalTripTrackingState.setTracking(this, started)
            } catch (e: Exception) {
                PersonalTripTrackingState.setTracking(this, false)
                log("Quick Settings manual START failed: ${e.message}")
            }
        }
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val tracking = PersonalTripTrackingState.isTracking(this)
        tile.state = if (tracking) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(
            if (tracking) {
                R.string.personal_trip_tile_label_active
            } else {
                R.string.personal_trip_tile_label
            }
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(
                if (tracking) {
                    R.string.personal_trip_tile_subtitle_active
                } else {
                    R.string.personal_trip_tile_subtitle_inactive
                }
            )
        }
        tile.icon = Icon.createWithResource(this, R.drawable.ic_personal_trip_tile)
        tile.updateTile()
    }

    private fun log(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
        PersonalTripTrackerLogger.log(this, TAG, message)
    }

    private companion object {
        private const val TAG = "PersonalTripQuickTile"
    }
}
