package com.example.toplutasima.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.toplutasima.service.transit.TransitNotificationBuilder
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Bildirim üzerindeki aksiyon butonlarından ve hatırlatma alarm'ından
 * gelen Intent'leri işler.
 *
 * Bindim → Worker (DB) + servise ACTION_UPDATE_BOARDING (bildirim hızlı güncellenir)
 * İndim  → Worker (DB) + servise ACTION_HANDLE_INDIM_FROM_NOTIF (son segment ise servis kapanır)
 */
class TransitNotificationReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "TransitNotifReceiver"

        const val ACTION_NOTIF_BINDIM = "com.example.toplutasima.transit.NOTIF_BINDIM"
        const val ACTION_NOTIF_INDIM = "com.example.toplutasima.transit.NOTIF_INDIM"
        const val ACTION_REMINDER_TRIGGER = "com.example.toplutasima.transit.REMINDER"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_NOTIF_BINDIM -> {
                Log.d(TAG, "Bildirimden Bindim aksiyonu alındı")
                val tripId = intent.getStringExtra(TransitTripForegroundService.EXTRA_TRIP_ID).orEmpty()
                // 1) DB'ye yaz (Worker)
                enqueueWorker(context, tripId, isBoarding = true)
                // 2) Servisi hemen güncelle — Bindim butonunu kaldır + hatırlatma kur
                sendServiceIntent(context, TransitTripForegroundService.ACTION_UPDATE_BOARDING, tripId)
            }

            ACTION_NOTIF_INDIM -> {
                Log.d(TAG, "Bildirimden İndim aksiyonu alındı")
                val tripId = intent.getStringExtra(TransitTripForegroundService.EXTRA_TRIP_ID).orEmpty()
                // 1) DB'ye yaz (Worker)
                enqueueWorker(context, tripId, isBoarding = false)
                // 2) Servise bildir — son segmentte ise bildirim kapanır
                sendServiceIntent(context, TransitTripForegroundService.ACTION_HANDLE_INDIM_FROM_NOTIF, tripId)
            }

            ACTION_REMINDER_TRIGGER -> {
                Log.d(TAG, "Hatırlatma alarm'ı tetiklendi")
                if (!shouldShowTimeReminder(context)) {
                    Log.d(TAG, "Hatırlatma alarm'ı güncel ayarlar nedeniyle yoksayıldı")
                    return
                }
                showReminderNotification(context, intent)
            }
        }
    }

    private fun shouldShowTimeReminder(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences("rmv_prefs", Context.MODE_PRIVATE)
        val notificationsEnabled = prefs.getBoolean("transit_notif_enabled", true)
        val reminderType = prefs.getString("transit_reminder_type", "TIME") ?: "TIME"
        return notificationsEnabled && reminderType == "TIME"
    }

    private fun enqueueWorker(context: Context, tripId: String, isBoarding: Boolean) {
        if (tripId.isBlank()) return
        val timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

        val workData = androidx.work.Data.Builder()
            .putString(com.example.toplutasima.worker.TransitActionWorker.KEY_TRIP_ID, tripId)
            .putBoolean(com.example.toplutasima.worker.TransitActionWorker.KEY_IS_BOARDING, isBoarding)
            .putString(com.example.toplutasima.worker.TransitActionWorker.KEY_TIMESTAMP, timestamp)
            .build()

        val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.toplutasima.worker.TransitActionWorker>()
            .setInputData(workData)
            .addTag(com.example.toplutasima.worker.TransitActionWorker.TAG)
            .build()

        androidx.work.WorkManager.getInstance(context).enqueue(workRequest)
    }

    /** Çalışan servise bir action intent'i gönderir. */
    private fun sendServiceIntent(context: Context, action: String, tripId: String) {
        try {
            val intent = Intent(context, TransitTripForegroundService::class.java).apply {
                this.action = action
                putExtra(TransitTripForegroundService.EXTRA_TRIP_ID, tripId)
            }
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            Log.e(TAG, "Servise intent gönderilemedi ($action): ${e.message}")
        }
    }

    /**
     * Hatırlatma bildirimini gösterir (AlarmManager tarafından tetiklenir).
     */
    private fun showReminderNotification(context: Context, intent: Intent) {
        val line = intent.getStringExtra(TransitTripForegroundService.EXTRA_LINE) ?: ""
        val alightingStop = intent.getStringExtra(TransitTripForegroundService.EXTRA_ALIGHTING_STOP) ?: ""
        val plannedArr = intent.getStringExtra(TransitTripForegroundService.EXTRA_PLANNED_ARR) ?: ""
        val vehicleType = intent.getStringExtra(TransitTripForegroundService.EXTRA_VEHICLE_TYPE) ?: ""
        val tripId = intent.getStringExtra(TransitTripForegroundService.EXTRA_TRIP_ID).orEmpty()

        val notification = TransitNotificationBuilder(context).buildReminderNotification(
            line = line,
            alightingStop = alightingStop,
            plannedArr = plannedArr,
            vehicleType = vehicleType,
            tripId = tripId
        )

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(TransitTripForegroundService.NOTIF_ID_REMINDER, notification)
    }
}
