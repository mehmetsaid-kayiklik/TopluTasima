package com.example.toplutasima.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.toplutasima.BuildConfig
import com.example.toplutasima.service.transit.TransitProximityTracker
import com.google.android.gms.location.ActivityRecognitionResult

class ActivityRecognitionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityRecognitionResult.hasResult(intent)) return

        val result = ActivityRecognitionResult.extractResult(intent) ?: return
        val (activityType, confidence) =
            TransitProximityTracker.updateActivityConfidence(result.probableActivities)

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Activity update: type=$activityType confidence=$confidence")
        }
    }

    private companion object {
        private const val TAG = "ActivityRecReceiver"
    }
}
