package com.example.toplutasima.service.transit

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.toplutasima.MainActivity
import com.example.toplutasima.service.ActivityRecognitionReceiver
import com.example.toplutasima.service.TransitNotificationReceiver
import com.example.toplutasima.service.TransitTripForegroundService

object TransitActionIntents {
    data class ReminderAlarmData(
        val line: String,
        val alightingStop: String,
        val plannedArr: String,
        val vehicleType: String,
        val tripId: String
    )

    private const val EXTRA_FROM_NOTIFICATION_ACTION = "fromNotificationAction"
    private const val PROXIMITY_WATCH_REQUEST_CODE = 9012
    private const val ACTIVITY_RECOGNITION_REQUEST_CODE = 9013
    private const val ACTION_ACTIVITY_RECOGNITION_UPDATE =
        "com.example.toplutasima.transit.ACTIVITY_RECOGNITION_UPDATE"

    fun contentPendingIntent(context: Context): PendingIntent {
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun transitActionPendingIntent(
        context: Context,
        notificationAction: String,
        tripId: String
    ): PendingIntent {
        val serviceAction = when (notificationAction) {
            TransitNotificationReceiver.ACTION_NOTIF_BINDIM ->
                TransitTripForegroundService.ACTION_UPDATE_BOARDING
            TransitNotificationReceiver.ACTION_NOTIF_INDIM ->
                TransitTripForegroundService.ACTION_HANDLE_INDIM_FROM_NOTIF
            else -> notificationAction
        }
        val intent = Intent(context, TransitTripForegroundService::class.java).apply {
            action = serviceAction
            putExtra(TransitTripForegroundService.EXTRA_TRIP_ID, tripId)
            putExtra(EXTRA_FROM_NOTIFICATION_ACTION, true)
        }
        val requestCode = 31 * notificationAction.hashCode() + tripId.hashCode()
        return PendingIntent.getForegroundService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun reminderTriggerPendingIntent(
        context: Context,
        data: ReminderAlarmData? = null
    ): PendingIntent {
        val intent = Intent(context, TransitNotificationReceiver::class.java).apply {
            action = TransitNotificationReceiver.ACTION_REMINDER_TRIGGER
            if (data != null) {
                putExtra(TransitTripForegroundService.EXTRA_LINE, data.line)
                putExtra(TransitTripForegroundService.EXTRA_ALIGHTING_STOP, data.alightingStop)
                putExtra(TransitTripForegroundService.EXTRA_PLANNED_ARR, data.plannedArr)
                putExtra(TransitTripForegroundService.EXTRA_VEHICLE_TYPE, data.vehicleType)
                putExtra(TransitTripForegroundService.EXTRA_TRIP_ID, data.tripId)
            }
        }
        return PendingIntent.getBroadcast(
            context,
            TransitTripForegroundService.NOTIF_ID_REMINDER,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun proximityWatchPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, TransitTripForegroundService::class.java).apply {
            action = TransitTripForegroundService.ACTION_START_PROXIMITY_WATCH
        }
        return PendingIntent.getForegroundService(
            context,
            PROXIMITY_WATCH_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun activityRecognitionPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ActivityRecognitionReceiver::class.java).apply {
            action = ACTION_ACTIVITY_RECOGNITION_UPDATE
        }
        return PendingIntent.getBroadcast(
            context,
            ACTIVITY_RECOGNITION_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }
}
