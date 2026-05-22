package com.example.toplutasima.service.transit

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class TransitServiceStateStore(private val context: Context) {
    data class State(
        val line: String,
        val alightingStop: String,
        val plannedArr: String,
        val vehicleType: String,
        val segmentIndex: Int,
        val totalSegments: Int,
        val tripId: String,
        val alightingLat: Double,
        val alightingLng: Double,
        val hasBoarded: Boolean
    )

    private companion object {
        const val TAG = "TransitStateStore"
        const val PREFS_STATE_NAME = "transit_service_state"
        const val PKEY_LINE = "transit_state_line"
        const val PKEY_ALIGHTING_STOP = "transit_state_alighting_stop"
        const val PKEY_PLANNED_ARR = "transit_state_planned_arr"
        const val PKEY_VEHICLE_TYPE = "transit_state_vehicle_type"
        const val PKEY_SEG_IDX = "transit_state_seg_idx"
        const val PKEY_TOTAL_SEGS = "transit_state_total_segs"
        const val PKEY_TRIP_ID = "transit_state_trip_id"
        const val PKEY_LAT = "transit_state_lat"
        const val PKEY_LNG = "transit_state_lng"
        const val PKEY_HAS_BOARDED = "transit_state_has_boarded"
    }

    private val prefs by lazy { createPrefs() }

    fun save(state: State) {
        prefs.edit()
            .putString(PKEY_LINE, state.line)
            .putString(PKEY_ALIGHTING_STOP, state.alightingStop)
            .putString(PKEY_PLANNED_ARR, state.plannedArr)
            .putString(PKEY_VEHICLE_TYPE, state.vehicleType)
            .putInt(PKEY_SEG_IDX, state.segmentIndex)
            .putInt(PKEY_TOTAL_SEGS, state.totalSegments)
            .putString(PKEY_TRIP_ID, state.tripId)
            .putLong(PKEY_LAT, state.alightingLat.toBits())
            .putLong(PKEY_LNG, state.alightingLng.toBits())
            .putBoolean(PKEY_HAS_BOARDED, state.hasBoarded)
            .apply()
    }

    fun restore(): State? {
        if (!prefs.contains(PKEY_TRIP_ID)) return null
        return State(
            line = prefs.getString(PKEY_LINE, "") ?: "",
            alightingStop = prefs.getString(PKEY_ALIGHTING_STOP, "") ?: "",
            plannedArr = prefs.getString(PKEY_PLANNED_ARR, "") ?: "",
            vehicleType = prefs.getString(PKEY_VEHICLE_TYPE, "") ?: "",
            segmentIndex = prefs.getInt(PKEY_SEG_IDX, 0),
            totalSegments = prefs.getInt(PKEY_TOTAL_SEGS, 1),
            tripId = prefs.getString(PKEY_TRIP_ID, "") ?: "",
            alightingLat = Double.fromBits(prefs.getLong(PKEY_LAT, Double.NaN.toBits())),
            alightingLng = Double.fromBits(prefs.getLong(PKEY_LNG, Double.NaN.toBits())),
            hasBoarded = prefs.getBoolean(PKEY_HAS_BOARDED, false)
        )
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun createPrefs(): android.content.SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_STATE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: java.security.GeneralSecurityException) {
            Log.e(TAG, "EncryptedSharedPreferences fail, fallback to plain", e)
            context.getSharedPreferences(PREFS_STATE_NAME, Context.MODE_PRIVATE)
        } catch (e: java.io.IOException) {
            Log.e(TAG, "EncryptedSharedPreferences io fail, fallback to plain", e)
            context.getSharedPreferences(PREFS_STATE_NAME, Context.MODE_PRIVATE)
        }
    }
}
