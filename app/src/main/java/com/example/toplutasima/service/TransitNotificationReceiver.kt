package com.example.toplutasima.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.toplutasima.model.VehicleType

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

        // SharedPreferences anahtarları — ViewModel bu değerleri okur
        const val PREFS_NAME = "transit_notif_actions"
        const val KEY_PENDING_ACTION = "pending_action"
        const val KEY_ACTION_TIMESTAMP = "action_timestamp"

        const val PENDING_BINDIM = "bindim"
        const val PENDING_INDIM = "indim"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_NOTIF_BINDIM -> {
                Log.d(TAG, "Bildirimden Bindim aksiyonu alındı")
                writePendingAction(context, PENDING_BINDIM)
            }

            ACTION_NOTIF_INDIM -> {
                Log.d(TAG, "Bildirimden İndim aksiyonu alındı")
                writePendingAction(context, PENDING_INDIM)
            }

            ACTION_REMINDER_TRIGGER -> {
                Log.d(TAG, "Hatırlatma alarm'ı tetiklendi")
                showReminderNotification(context, intent)
            }
        }
    }

    /**
     * Aksiyonu SharedPreferences'a yazar. ViewModel bu değeri periyodik olarak
     * veya LaunchedEffect ile kontrol eder.
     */
    private fun writePendingAction(context: Context, action: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PENDING_ACTION, action)
            .putLong(KEY_ACTION_TIMESTAMP, System.currentTimeMillis())
            .apply()
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
