package com.example.toplutasima.service.transit

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import com.example.toplutasima.service.TransitNotificationReceiver
import com.example.toplutasima.service.TransitTripForegroundService
import com.example.toplutasima.ui.util.vehicleIcon

class TransitNotificationBuilder(private val context: Context) {
    data class TrackingState(
        val line: String,
        val alightingStop: String,
        val plannedArr: String,
        val vehicleType: String,
        val segmentIndex: Int,
        val totalSegments: Int,
        val tripId: String,
        val hasBoarded: Boolean,
        val usingTimeFallback: Boolean
    )

    companion object {
        internal val REMINDER_VIBRATION_PATTERN = longArrayOf(0L, 350L, 150L, 350L)
    }

    fun buildTrackingNotification(state: TrackingState): Notification {
        val emoji = vehicleIcon(state.vehicleType)
        val prefix = segmentPrefix(state)
        val title = if (state.hasBoarded) {
            "$emoji $prefix${state.line} — Yolculuk aktif"
        } else {
            "$emoji $prefix${state.line}"
        }
        val arrText = if (state.plannedArr.isNotBlank()) " (${state.plannedArr})" else ""
        val fallbackSuffix = if (state.usingTimeFallback) " · ⏰ Saat bazlı hatırlatma aktif" else ""
        val text = "📍 İniş: ${state.alightingStop}$arrText$fallbackSuffix"

        val builder = NotificationCompat.Builder(context, TransitTripForegroundService.CHANNEL_TRACKING)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentIntent(TransitActionIntents.contentPendingIntent(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)

        if (state.totalSegments > 1) {
            builder.setProgress(state.totalSegments, state.segmentIndex + 1, false)
        }

        if (!state.hasBoarded) {
            builder.addAction(
                0,
                "Bindim ✋",
                TransitActionIntents.transitActionPendingIntent(
                    context,
                    TransitNotificationReceiver.ACTION_NOTIF_BINDIM,
                    state.tripId
                )
            )
        }
        builder.addAction(
            0,
            "İndim 🏁",
            TransitActionIntents.transitActionPendingIntent(
                context,
                TransitNotificationReceiver.ACTION_NOTIF_INDIM,
                state.tripId
            )
        )

        return builder.build()
    }

    fun buildWaitingForNextSegmentNotification(): Notification =
        NotificationCompat.Builder(context, TransitTripForegroundService.CHANNEL_TRACKING)
            .setContentTitle("🔄 Aktarma bekleniyor")
            .setContentText("Sonraki aktarma bekleniyor...")
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentIntent(TransitActionIntents.contentPendingIntent(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()

    fun buildReminderNotification(
        line: String,
        alightingStop: String,
        plannedArr: String,
        vehicleType: String,
        tripId: String
    ): Notification {
        val emoji = vehicleIcon(vehicleType)
        return NotificationCompat.Builder(context, TransitTripForegroundService.CHANNEL_REMINDER)
            .setContentTitle("⏰ İnmeniz gereken durak!")
            .setContentText("$emoji $line → 📍 $alightingStop ($plannedArr)")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(REMINDER_VIBRATION_PATTERN)
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
            .addAction(
                0,
                "İndim 🏁",
                TransitActionIntents.transitActionPendingIntent(
                    context,
                    TransitNotificationReceiver.ACTION_NOTIF_INDIM,
                    tripId
                )
            )
            .build()
    }

    private fun segmentPrefix(state: TrackingState): String =
        if (state.totalSegments > 1) "(${state.segmentIndex + 1}/${state.totalSegments}) " else ""

}
