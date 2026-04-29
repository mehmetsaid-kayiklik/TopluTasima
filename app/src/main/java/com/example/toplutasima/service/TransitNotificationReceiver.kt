package com.example.toplutasima.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.toplutasima.model.VehicleType
import com.example.toplutasima.network.FirestoreService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Bildirim üzerindeki aksiyon butonlarından ve hatırlatma alarm'ından
 * gelen Intent'leri işler.
 *
 * Bindim/İndim aksiyonları SharedPreferences üzerinden bir "pending action"
 * olarak yazılır ve ViewModel tarafından okunup işlenir.
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
                updateActualFromNotification(context, intent, isBoarding = true)
            }

            ACTION_NOTIF_INDIM -> {
                Log.d(TAG, "Bildirimden İndim aksiyonu alındı")
                updateActualFromNotification(context, intent, isBoarding = false)
            }

            ACTION_REMINDER_TRIGGER -> {
                Log.d(TAG, "Hatırlatma alarm'ı tetiklendi")
                showReminderNotification(context, intent)
            }
        }
    }

    private fun updateActualFromNotification(context: Context, intent: Intent, isBoarding: Boolean) {
        val tripId = intent.getStringExtra(TransitTripForegroundService.EXTRA_TRIP_ID).orEmpty()
        if (tripId.isBlank()) return

        val timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

        val workData = androidx.work.Data.Builder()
            .putString(com.example.toplutasima.worker.TransitActionWorker.KEY_TRIP_ID, tripId)
            .putBoolean(com.example.toplutasima.worker.TransitActionWorker.KEY_IS_BOARDING, isBoarding)
            .putString(com.example.toplutasima.worker.TransitActionWorker.KEY_TIMESTAMP, timestamp)
            .build()

        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()

        val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.toplutasima.worker.TransitActionWorker>()
            .setConstraints(constraints)
            .setInputData(workData)
            .build()

        androidx.work.WorkManager.getInstance(context).enqueue(workRequest)
    }

    /**
     * Hatırlatma bildirimini gösterir (AlarmManager tarafından tetiklenir).
     */
    private fun showReminderNotification(context: Context, intent: Intent) {
        val line = intent.getStringExtra(TransitTripForegroundService.EXTRA_LINE) ?: ""
        val alightingStop = intent.getStringExtra(TransitTripForegroundService.EXTRA_ALIGHTING_STOP) ?: ""
        val plannedArr = intent.getStringExtra(TransitTripForegroundService.EXTRA_PLANNED_ARR) ?: ""
        val vehicleType = intent.getStringExtra(TransitTripForegroundService.EXTRA_VEHICLE_TYPE) ?: ""

        val emoji = when (vehicleType) {
            VehicleType.UBAHN.key -> "🚇"
            VehicleType.SBAHN.key -> "🚆"
            VehicleType.RERB.key -> "🚂"
            VehicleType.FERNZUG.key -> "🚄"
            VehicleType.STRASSENBAHN.key -> "🚋"
            else -> "🚌"
        }

        // İndim aksiyon butonu
        val indimIntent = Intent(context, TransitNotificationReceiver::class.java).apply {
            action = ACTION_NOTIF_INDIM
            putExtra(
                TransitTripForegroundService.EXTRA_TRIP_ID,
                intent.getStringExtra(TransitTripForegroundService.EXTRA_TRIP_ID).orEmpty()
            )
        }
        val indimPi = android.app.PendingIntent.getBroadcast(
            context,
            ACTION_NOTIF_INDIM.hashCode(),
            indimIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, TransitTripForegroundService.CHANNEL_REMINDER)
            .setContentTitle("⏰ İnmeniz gereken durak!")
            .setContentText("$emoji $line → 📍 $alightingStop ($plannedArr)")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .addAction(0, "İndim 🏁", indimPi)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(TransitTripForegroundService.NOTIF_ID_REMINDER, notification)
    }
}
