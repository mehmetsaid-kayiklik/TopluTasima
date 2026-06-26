package com.example.toplutasima.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.toplutasima.BuildConfig
import com.example.toplutasima.MainActivity
import com.example.toplutasima.R
import com.example.toplutasima.diagnostics.PersonalTripTrackerLogger

object PersonalTripPermissionGuard {
    private const val TAG = "PersonalTripPermission"
    private const val CHANNEL_ID = "personal_trip_permission"
    private const val NOTIF_ID = 9002
    private const val OPEN_APP_REQUEST_CODE = 9201

    fun hasLocationPermission(context: Context): Boolean {
        val appContext = context.applicationContext
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }

    fun handleMissingLocationPermission(
        context: Context,
        source: String,
        notifyUser: Boolean
    ) {
        val appContext = context.applicationContext
        PersonalTripTrackingState.setTracking(appContext, false)
        PersonalTripTrackingState.markLocationPermissionReminder(appContext)
        log(appContext, "$source start skipped: location permission missing")
        if (notifyUser) {
            showLocationPermissionNotification(appContext)
        }
    }

    @SuppressLint("MissingPermission")
    private fun showLocationPermissionNotification(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            log(context, "Location permission notification skipped: POST_NOTIFICATIONS missing")
            return
        }

        val notificationManager =
            ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return
        createNotificationChannel(notificationManager)

        val openAppIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            ?: Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        val pendingIntent = PendingIntent.getActivity(
            context,
            OPEN_APP_REQUEST_CODE,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = context.getString(R.string.personal_trip_location_permission_text)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_personal_trip_tile)
            .setContentTitle(context.getString(R.string.personal_trip_location_permission_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(NOTIF_ID, notification)
    }

    private fun createNotificationChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Personal trip permission",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)
    }

    private fun log(context: Context, message: String) {
        if (BuildConfig.DEBUG) Log.w(TAG, message)
        PersonalTripTrackerLogger.log(context, TAG, message)
    }
}
