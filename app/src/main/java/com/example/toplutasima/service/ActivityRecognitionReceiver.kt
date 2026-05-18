package com.example.toplutasima.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.toplutasima.BuildConfig
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity

class ActivityRecognitionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityRecognitionResult.hasResult(intent)) return

        val result = ActivityRecognitionResult.extractResult(intent) ?: return
        val (activityType, confidence) = selectActivityConfidence(result.probableActivities)
        TransitTripForegroundService._currentActivityConfidence.value = activityType to confidence

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Activity update: type=$activityType confidence=$confidence")
        }
    }

    private fun selectActivityConfidence(activities: List<DetectedActivity>): Pair<Int, Int> {
        val walkingLike = activities
            .filter { it.type in WALKING_ACTIVITY_TYPES }
            .maxByOrNull { it.confidence }

        if (walkingLike != null) {
            return walkingLike.type to walkingLike.confidence
        }

        val mostLikely = activities.maxByOrNull { it.confidence }
        return (mostLikely?.type ?: DetectedActivity.UNKNOWN) to (mostLikely?.confidence ?: 0)
    }

    private companion object {
        private const val TAG = "ActivityRecReceiver"
        private val WALKING_ACTIVITY_TYPES = setOf(
            DetectedActivity.WALKING,
            DetectedActivity.ON_FOOT,
            DetectedActivity.RUNNING
        )
    }
}
