package com.example.toplutasima.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object PersonalTripActionIntents {
    const val ACTION_ACTIVITY_TRANSITION_UPDATE =
        "com.example.toplutasima.personal.ACTIVITY_TRANSITION_UPDATE"
    const val ACTION_EXIT_DEBOUNCE_EXPIRED =
        "com.example.toplutasima.personal.EXIT_DEBOUNCE_EXPIRED"

    private const val ACTIVITY_TRANSITION_REQUEST_CODE = 9101
    private const val EXIT_DEBOUNCE_REQUEST_CODE = 9102

    fun serviceIntent(context: Context, action: String): Intent =
        Intent(context, PersonalTripForegroundService::class.java).apply {
            this.action = action
        }

    fun startPersonalTripService(context: Context) {
        val intent = serviceIntent(context, PersonalTripForegroundService.ACTION_START)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopPersonalTripService(context: Context) {
        context.startService(serviceIntent(context, PersonalTripForegroundService.ACTION_STOP))
    }

    fun activityTransitionPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, PersonalTripActivityTransitionReceiver::class.java).apply {
            action = ACTION_ACTIVITY_TRANSITION_UPDATE
        }
        return PendingIntent.getBroadcast(
            context,
            ACTIVITY_TRANSITION_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    fun exitDebouncePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, PersonalTripActivityTransitionReceiver::class.java).apply {
            action = ACTION_EXIT_DEBOUNCE_EXPIRED
        }
        return PendingIntent.getBroadcast(
            context,
            EXIT_DEBOUNCE_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
