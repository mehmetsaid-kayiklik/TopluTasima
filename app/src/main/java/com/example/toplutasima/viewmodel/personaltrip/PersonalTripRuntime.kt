package com.example.toplutasima.viewmodel.personaltrip

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.toplutasima.location.PersonalLocationHelper
import com.example.toplutasima.service.PersonalTripForegroundService
import com.example.toplutasima.service.PersonalTripPermissionGuard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

internal interface PersonalTripRuntime {
    fun hasLocationPermission(context: Context): Boolean

    suspend fun resolveCurrentLocation(): Triple<String, Double, Double>?

    fun startTracking(context: Context, docId: String)

    fun stopTracking(context: Context, docId: String? = null)

    suspend fun stopTrackingAndAwaitFinalization(
        context: Context,
        docId: String
    ): PersonalTripForegroundService.TripFinalization?
}

internal class AndroidPersonalTripRuntime(
    application: Application
) : PersonalTripRuntime {
    private val locationHelper = PersonalLocationHelper(application)

    override fun hasLocationPermission(context: Context): Boolean =
        PersonalTripPermissionGuard.hasLocationPermission(context)

    override suspend fun resolveCurrentLocation(): Triple<String, Double, Double>? =
        locationHelper.resolveCurrentLocation()

    override fun startTracking(context: Context, docId: String) {
        val intent = Intent(context, PersonalTripForegroundService::class.java)
            .setAction(PersonalTripForegroundService.ACTION_START)
            .putExtra(PersonalTripForegroundService.EXTRA_TRIP_DOC_ID, docId)
        ContextCompat.startForegroundService(context, intent)
    }

    override fun stopTracking(context: Context, docId: String?) {
        val intent = Intent(context, PersonalTripForegroundService::class.java)
            .setAction(PersonalTripForegroundService.ACTION_STOP)
        if (!docId.isNullOrBlank()) {
            intent.putExtra(PersonalTripForegroundService.EXTRA_TRIP_DOC_ID, docId)
        }
        context.startService(intent)
    }

    override suspend fun stopTrackingAndAwaitFinalization(
        context: Context,
        docId: String
    ): PersonalTripForegroundService.TripFinalization? {
        val previousSequence =
            PersonalTripForegroundService.lastFinalization.value?.sequence ?: 0L
        stopTracking(context, docId)
        return withTimeoutOrNull(FINALIZATION_TIMEOUT_MS) {
            PersonalTripForegroundService.lastFinalization.first { result ->
                result != null &&
                    result.sequence > previousSequence &&
                    result.tripDocumentId == docId
            }
        }
    }

    private companion object {
        const val FINALIZATION_TIMEOUT_MS = 15_000L
    }
}
