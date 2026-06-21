package com.example.toplutasima.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.toplutasima.BuildConfig
import com.example.toplutasima.diagnostics.PersonalTripTrackerLogger
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity

class PersonalTripActivityTransitionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        when (intent.action) {
            PersonalTripActionIntents.ACTION_EXIT_DEBOUNCE_EXPIRED ->
                handleExitDebounceExpired(appContext)
            PersonalTripActionIntents.ACTION_ACTIVITY_TRANSITION_UPDATE ->
                handleActivityTransition(appContext, intent)
        }
    }

    private fun handleActivityTransition(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) {
            log(context, "Activity transition broadcast without result; ignoring")
            return
        }

        val result = ActivityTransitionResult.extractResult(intent) ?: return
        result.transitionEvents.forEach { event ->
            if (event.activityType != DetectedActivity.IN_VEHICLE) return@forEach
            when (event.transitionType) {
                ActivityTransition.ACTIVITY_TRANSITION_ENTER -> handleVehicleEnter(context)
                ActivityTransition.ACTIVITY_TRANSITION_EXIT -> handleVehicleExit(context)
            }
        }
    }

    companion object {
        private const val TAG = "PersonalTripAutoTrigger"
        private const val EXIT_DEBOUNCE_MS = 45_000L

        @SuppressLint("MissingPermission")
        fun register(context: Context) {
            val appContext = context.applicationContext
            if (!hasActivityRecognitionPermission(appContext)) {
                log(
                    appContext,
                    "Activity Transition registration skipped: ACTIVITY_RECOGNITION permission missing"
                )
                return
            }

            val request = ActivityTransitionRequest(
                listOf(
                    ActivityTransition.Builder()
                        .setActivityType(DetectedActivity.IN_VEHICLE)
                        .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                        .build(),
                    ActivityTransition.Builder()
                        .setActivityType(DetectedActivity.IN_VEHICLE)
                        .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                        .build()
                )
            )

            try {
                ActivityRecognition.getClient(appContext)
                    .requestActivityTransitionUpdates(
                        request,
                        PersonalTripActionIntents.activityTransitionPendingIntent(appContext)
                    )
                    .addOnSuccessListener {
                        log(
                            appContext,
                            "Activity Transition updates registered for IN_VEHICLE ENTER/EXIT"
                        )
                    }
                    .addOnFailureListener { e ->
                        log(
                            appContext,
                            "Activity Transition registration failed: ${e.message}"
                        )
                    }
            } catch (e: SecurityException) {
                log(
                    appContext,
                    "Activity Transition registration failed: missing permission (${e.message})"
                )
            }
        }

        private fun handleVehicleEnter(context: Context) {
            val hadPendingExit = PersonalTripTrackingState.pendingExitDeadlineMs(context) > 0L
            if (hadPendingExit) {
                cancelExitDebounce(context)
                PersonalTripTrackingState.clearPendingExit(context)
            }

            val sessionWasActive = PersonalTripTrackingState.isVehicleSessionActive(context)
            val manuallyStopped = PersonalTripTrackingState.isManuallyStoppedThisSession(context)
            if (!sessionWasActive) {
                PersonalTripTrackingState.markVehicleEnter(context)
            }

            if (hadPendingExit && PersonalTripTrackingState.isTracking(context)) {
                log(
                    context,
                    "IN_VEHICLE ENTER detected within debounce window, continuing trip"
                )
                return
            }

            if (sessionWasActive && manuallyStopped) {
                log(
                    context,
                    "IN_VEHICLE ENTER detected, manual stop active for this session; ignoring auto start"
                )
                return
            }

            log(context, "IN_VEHICLE ENTER detected, starting trip")
            try {
                PersonalTripActionIntents.startPersonalTripService(context)
            } catch (e: Exception) {
                log(context, "IN_VEHICLE ENTER start failed: ${e.message}")
            }
        }

        private fun handleVehicleExit(context: Context) {
            if (!PersonalTripTrackingState.isVehicleSessionActive(context) &&
                !PersonalTripTrackingState.isTracking(context)
            ) {
                log(context, "IN_VEHICLE EXIT detected without active session; ignoring")
                return
            }

            val deadlineMs = SystemClock.elapsedRealtime() + EXIT_DEBOUNCE_MS
            PersonalTripTrackingState.setPendingExitDeadlineMs(context, deadlineMs)
            scheduleExitDebounce(context, deadlineMs)
            log(
                context,
                "IN_VEHICLE EXIT detected, waiting ${EXIT_DEBOUNCE_MS / 1000}s before stopping trip"
            )
        }

        private fun handleExitDebounceExpired(context: Context) {
            val deadlineMs = PersonalTripTrackingState.pendingExitDeadlineMs(context)
            if (deadlineMs <= 0L) {
                log(context, "EXIT debounce expired without pending exit; ignoring")
                return
            }

            if (SystemClock.elapsedRealtime() + 500L < deadlineMs) {
                scheduleExitDebounce(context, deadlineMs)
                return
            }

            val tracking = PersonalTripTrackingState.isTracking(context)
            PersonalTripTrackingState.markVehicleExit(context)
            if (!tracking) {
                log(context, "EXIT debounce elapsed; trip already stopped")
                return
            }

            log(context, "IN_VEHICLE EXIT debounce elapsed, stopping trip")
            try {
                PersonalTripActionIntents.stopPersonalTripService(context)
            } catch (e: Exception) {
                log(context, "IN_VEHICLE EXIT stop failed: ${e.message}")
            }
        }

        private fun scheduleExitDebounce(context: Context, deadlineMs: Long) {
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            alarmManager.set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                deadlineMs,
                PersonalTripActionIntents.exitDebouncePendingIntent(context)
            )
        }

        private fun cancelExitDebounce(context: Context) {
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            alarmManager.cancel(PersonalTripActionIntents.exitDebouncePendingIntent(context))
        }

        private fun hasActivityRecognitionPermission(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACTIVITY_RECOGNITION
                ) == PackageManager.PERMISSION_GRANTED

        private fun log(context: Context, message: String) {
            if (BuildConfig.DEBUG) Log.d(TAG, message)
            PersonalTripTrackerLogger.log(context, TAG, message)
        }
    }
}
