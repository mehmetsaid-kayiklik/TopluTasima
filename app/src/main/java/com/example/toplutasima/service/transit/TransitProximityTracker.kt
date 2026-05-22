package com.example.toplutasima.service.transit

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.toplutasima.data.TransitAutoActualTimeMode
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class TransitProximityTracker(
    context: Context,
    private val autoModeProvider: () -> TransitAutoActualTimeMode,
    private val onGpsFallback: () -> Unit,
    private val onManualReminderReached: () -> Unit,
    private val onAutoAlighting: () -> Unit,
    private val logDebug: (String) -> Unit
) {
    private val context = context.applicationContext
    private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(this.context) }
    private val activityRecognitionClient by lazy { ActivityRecognition.getClient(this.context) }
    private val trackerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var proximityJob: Job? = null
    private var isCurrentlyWalking = false
    private var consecutiveWalkingLoops = 0

    fun missingPrerequisiteReason(targetLat: Double, targetLng: Double): String? {
        if (targetLat.isNaN() || targetLng.isNaN()) return "koordinat yok"
        if (!hasLocationPermission()) return "konum izni yok"
        return null
    }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun start(targetLat: Double, targetLng: Double) {
        if (missingPrerequisiteReason(targetLat, targetLng) != null) return
        logDebug("Proximity watch basliyor -> hedef: ($targetLat, $targetLng)")
        stop()
        requestActivityUpdates()
        proximityJob = trackerScope.launch {
            var consecutiveNullCount = 0
            var previousLocation: Location? = null
            var nextCheckIntervalMs = DEFAULT_PROXIMITY_CHECK_INTERVAL_MS
            while (isActive) {
                delay(nextCheckIntervalMs)
                try {
                    val loc = getCurrentLocationSuspend()
                    if (loc == null) {
                        resetWalkingState()
                        nextCheckIntervalMs = DEFAULT_PROXIMITY_CHECK_INTERVAL_MS
                        consecutiveNullCount++
                        Log.w(TAG, "GPS alinamadi ($consecutiveNullCount/3)")
                        if (consecutiveNullCount >= 3) {
                            onGpsFallback()
                            removeActivityUpdates()
                            break
                        }
                        continue
                    }

                    consecutiveNullCount = 0
                    val dist = haversineMeters(loc.latitude, loc.longitude, targetLat, targetLng)
                    nextCheckIntervalMs = checkIntervalForDistance(dist)
                    val speedMps = movementSpeedMps(loc, previousLocation)
                    previousLocation = loc
                    logDebug(
                        "Proximity check: ${dist.toInt()}m speed=" +
                            (speedMps?.let { "%.1f".format(it) } ?: "unknown") +
                            "m/s next=${nextCheckIntervalMs / 1000}s"
                    )

                    if (dist <= ALIGHTING_PROXIMITY_METERS) {
                        if (autoModeProvider() == TransitAutoActualTimeMode.AUTO) {
                            if (shouldAutoAlight(dist, speedMps)) {
                                removeActivityUpdates()
                                onAutoAlighting()
                                break
                            }
                        } else {
                            removeActivityUpdates()
                            onManualReminderReached()
                            break
                        }
                    } else {
                        resetWalkingState()
                    }
                } catch (e: Exception) {
                    resetWalkingState()
                    Log.w(TAG, "Proximity check hatasi: ${e.message}")
                }
            }
        }
    }

    fun stop() {
        proximityJob?.cancel()
        proximityJob = null
        resetWalkingState()
        removeActivityUpdates()
    }

    fun shutdown() {
        stop()
        trackerScope.cancel()
    }

    private fun shouldAutoAlight(distanceMeters: Double, speedMps: Double?): Boolean {
        if (distanceMeters <= CLOSE_ALIGHTING_PROXIMITY_METERS) {
            consecutiveWalkingLoops = 0
            if (speedMps != null && speedMps <= CLOSE_AUTO_ALIGHTING_MAX_SPEED_MPS) {
                return true
            }
            logDebug("Auto Indim bekliyor: 50m icinde ama hareket hizi hala yuksek")
            return false
        }

        isCurrentlyWalking = isValidWalkingActivity(_currentActivityConfidence.value)
        if (isCurrentlyWalking) {
            consecutiveWalkingLoops++
            if (consecutiveWalkingLoops >= 2) {
                return true
            }
            logDebug("Auto Indim yurume teyidi bekliyor: $consecutiveWalkingLoops/2")
            return false
        }

        consecutiveWalkingLoops = 0
        if (isSlowEnoughForAutoAlighting(speedMps)) {
            return true
        }
        logDebug("Auto Indim bekliyor: hedefe yakin ama hareket hizi hala yuksek")
        return false
    }

    private fun checkIntervalForDistance(distanceMeters: Double): Long =
        when {
            distanceMeters <= CLOSE_ALIGHTING_PROXIMITY_METERS -> CLOSE_PROXIMITY_CHECK_INTERVAL_MS
            distanceMeters <= ALIGHTING_PROXIMITY_METERS -> NEAR_PROXIMITY_CHECK_INTERVAL_MS
            else -> DEFAULT_PROXIMITY_CHECK_INTERVAL_MS
        }

    @SuppressLint("MissingPermission")
    private fun requestActivityUpdates() {
        _currentActivityConfidence.value = DetectedActivity.UNKNOWN to 0
        if (!hasActivityRecognitionPermission()) {
            logDebug("Activity Recognition izni yok, GPS fallback aktif kalacak")
            return
        }

        activityRecognitionClient
            .requestActivityUpdates(
                DEFAULT_PROXIMITY_CHECK_INTERVAL_MS,
                TransitActionIntents.activityRecognitionPendingIntent(context)
            )
            .addOnSuccessListener { logDebug("Activity Recognition updates baslatildi") }
            .addOnFailureListener { e ->
                Log.w(TAG, "Activity Recognition updates baslatilamadi: ${e.message}")
            }
    }

    private fun removeActivityUpdates() {
        _currentActivityConfidence.value = DetectedActivity.UNKNOWN to 0
        try {
            activityRecognitionClient
                .removeActivityUpdates(TransitActionIntents.activityRecognitionPendingIntent(context))
                .addOnSuccessListener { logDebug("Activity Recognition updates durduruldu") }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Activity Recognition updates durdurulamadi: ${e.message}")
                }
        } catch (e: SecurityException) {
            Log.w(TAG, "Activity Recognition updates durdurulamadi: ${e.message}")
        }
    }

    @Suppress("MissingPermission")
    private suspend fun getCurrentLocationSuspend(): Location? {
        if (!hasLocationPermission()) return null
        val tokenSource = CancellationTokenSource()
        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { tokenSource.cancel() }
            fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, tokenSource.token)
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        cont.resume(loc)
                    } else {
                        fusedClient.lastLocation
                            .addOnSuccessListener { last -> cont.resume(last) }
                            .addOnFailureListener { cont.resume(null) }
                    }
                }
                .addOnFailureListener { cont.resume(null) }
        }
    }

    private fun resetWalkingState() {
        isCurrentlyWalking = false
        consecutiveWalkingLoops = 0
    }

    private fun isSlowEnoughForAutoAlighting(speedMps: Double?): Boolean =
        speedMps != null && speedMps <= AUTO_ALIGHTING_MAX_SPEED_MPS

    private fun movementSpeedMps(current: Location, previous: Location?): Double? {
        if (current.hasSpeed()) return current.speed.toDouble()
        if (previous == null) return null
        val seconds = locationDeltaSeconds(previous, current) ?: return null
        if (seconds <= 0.0) return null
        val meters = haversineMeters(
            previous.latitude,
            previous.longitude,
            current.latitude,
            current.longitude
        )
        return meters / seconds
    }

    private fun locationDeltaSeconds(previous: Location, current: Location): Double? {
        val elapsedNanos = current.elapsedRealtimeNanos - previous.elapsedRealtimeNanos
        if (elapsedNanos > 0L) return elapsedNanos / 1_000_000_000.0
        val wallMillis = current.time - previous.time
        if (wallMillis > 0L) return wallMillis / 1_000.0
        return null
    }

    private fun hasActivityRecognitionPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED

    private fun isValidWalkingActivity(activityConfidence: Pair<Int, Int>): Boolean {
        val (activityType, confidence) = activityConfidence
        return activityType in WALKING_ACTIVITY_TYPES && confidence >= VALID_WALKING_CONFIDENCE
    }

    private fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val radiusMeters = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return radiusMeters * 2 * asin(sqrt(a))
    }

    companion object {
        private const val TAG = "TransitProximity"
        private const val DEFAULT_PROXIMITY_CHECK_INTERVAL_MS = 30_000L
        private const val NEAR_PROXIMITY_CHECK_INTERVAL_MS = 10_000L
        private const val CLOSE_PROXIMITY_CHECK_INTERVAL_MS = 5_000L
        private const val ALIGHTING_PROXIMITY_METERS = 100.0
        private const val CLOSE_ALIGHTING_PROXIMITY_METERS = 50.0
        private const val VALID_WALKING_CONFIDENCE = 70
        private const val CLOSE_AUTO_ALIGHTING_MAX_SPEED_MPS = 1.0
        private const val AUTO_ALIGHTING_MAX_SPEED_MPS = 2.0
        private val WALKING_ACTIVITY_TYPES = setOf(
            DetectedActivity.WALKING,
            DetectedActivity.ON_FOOT,
            DetectedActivity.RUNNING
        )

        private val _currentActivityConfidence =
            MutableStateFlow(DetectedActivity.UNKNOWN to 0)
        val currentActivityConfidence: StateFlow<Pair<Int, Int>> = _currentActivityConfidence

        fun updateActivityConfidence(activities: List<DetectedActivity>): Pair<Int, Int> {
            val walkingLike = activities
                .filter { it.type in WALKING_ACTIVITY_TYPES }
                .maxByOrNull { it.confidence }

            val selected = if (walkingLike != null) {
                walkingLike.type to walkingLike.confidence
            } else {
                val mostLikely = activities.maxByOrNull { it.confidence }
                (mostLikely?.type ?: DetectedActivity.UNKNOWN) to (mostLikely?.confidence ?: 0)
            }

            _currentActivityConfidence.value = selected
            return selected
        }
    }
}
