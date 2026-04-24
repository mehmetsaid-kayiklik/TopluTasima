package com.example.toplutasima.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.toplutasima.data.PrefsManager
import com.example.toplutasima.model.VehicleType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Toplu taşıma yolculuğu sırasında sürekli (ongoing) bildirim gösterir.
 *
 * GPS takibi yapmaz — sadece bildirim yönetimi ve hatırlatma zamanlayıcısı.
 *
 * Çalışma akışı:
 *  1. Kaydet → ACTION_START: Bildirim göster (hat + iniş durağı + Bindim/İndim butonları)
 *  2. Bindim → ACTION_UPDATE_BOARDING: "Bindim" butonunu kaldır, hatırlatma zamanlayıcısı kur
 *  3. İndim → ACTION_STOP / İndim basılınca otomatik
 *  4. Aktarma → ACTION_NEXT_SEGMENT: Sonraki segment için bildirim güncelle
 */
class TransitTripForegroundService : Service() {

    companion object {
        private const val TAG = "TransitTripService"

        // Bildirim kanal ID'leri
        const val CHANNEL_TRACKING = "transit_trip_tracking"
        const val CHANNEL_REMINDER = "transit_trip_reminder"

        // Bildirim ID'leri
        const val NOTIF_ID_TRACKING = 9010
        const val NOTIF_ID_REMINDER = 9011

        // Intent Action'ları
        const val ACTION_START = "com.example.toplutasima.transit.START"
        const val ACTION_STOP = "com.example.toplutasima.transit.STOP"
        const val ACTION_UPDATE_BOARDING = "com.example.toplutasima.transit.BOARDED"
        const val ACTION_NEXT_SEGMENT = "com.example.toplutasima.transit.NEXT_SEG"

        // Intent Extra anahtarları
        const val EXTRA_LINE = "line"
        const val EXTRA_ALIGHTING_STOP = "alightingStop"
        const val EXTRA_PLANNED_ARR = "plannedArr"
        const val EXTRA_VEHICLE_TYPE = "vehicleType"
        const val EXTRA_SEGMENT_INDEX = "segmentIndex"
        const val EXTRA_TOTAL_SEGMENTS = "totalSegments"

        // ViewModel tarafından gözlemlenen durum
        private val _isActive = MutableStateFlow(false)
        val isActive: StateFlow<Boolean> = _isActive

        private val _activeSegmentIndex = MutableStateFlow(0)
        val activeSegmentIndex: StateFlow<Int> = _activeSegmentIndex
    }

    // Aktif segment bilgileri
    private var currentLine = ""
    private var currentAlightingStop = ""
    private var currentPlannedArr = ""
    private var currentVehicleType = ""
    private var currentSegmentIndex = 0
    private var totalSegments = 1
    private var hasBoarded = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                readExtras(intent)
                hasBoarded = false
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        startForeground(
                            NOTIF_ID_TRACKING,
                            buildTrackingNotification(),
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                        )
                    } else {
                        startForeground(NOTIF_ID_TRACKING, buildTrackingNotification())
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "startForeground başarısız: ${e.message}", e)
                    stopSelf()
                    return START_NOT_STICKY
                }
                _isActive.value = true
                _activeSegmentIndex.value = currentSegmentIndex
                Log.d(TAG, "Bildirim başladı: $currentLine → $currentAlightingStop ($currentPlannedArr)")
            }
            ACTION_UPDATE_BOARDING -> {
                hasBoarded = true
                updateTrackingNotification()
                scheduleReminder()
                Log.d(TAG, "Bindim kaydedildi, hatırlatma kuruldu")
            }
            ACTION_NEXT_SEGMENT -> {
                cancelReminder()
                readExtras(intent)
                hasBoarded = false
                updateTrackingNotification()
                _activeSegmentIndex.value = currentSegmentIndex
                Log.d(TAG, "Sonraki segment: $currentLine → $currentAlightingStop ($currentPlannedArr)")
            }
            ACTION_STOP -> {
                stopTracking()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        cancelReminder()
        _isActive.value = false
    }

    // ── Extras Okuma ─────────────────────────────────────────────────────────

    private fun readExtras(intent: Intent) {
        currentLine = intent.getStringExtra(EXTRA_LINE) ?: currentLine
        currentAlightingStop = intent.getStringExtra(EXTRA_ALIGHTING_STOP) ?: currentAlightingStop
        currentPlannedArr = intent.getStringExtra(EXTRA_PLANNED_ARR) ?: currentPlannedArr
        currentVehicleType = intent.getStringExtra(EXTRA_VEHICLE_TYPE) ?: currentVehicleType
        currentSegmentIndex = intent.getIntExtra(EXTRA_SEGMENT_INDEX, currentSegmentIndex)
        totalSegments = intent.getIntExtra(EXTRA_TOTAL_SEGMENTS, totalSegments)
    }

    // ── Takip Durdurma ───────────────────────────────────────────────────────

    private fun stopTracking() {
        Log.d(TAG, "Bildirim durduruluyor")
        cancelReminder()
        // Hatırlatma bildirimini de kaldır
        val nm = getSystemService(NotificationManager::class.java)
        nm.cancel(NOTIF_ID_REMINDER)
        _isActive.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ── Bildirim Kanalları ────────────────────────────────────────────────────

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)

        // Sürekli takip bildirimi — sessiz
        val trackingChannel = NotificationChannel(
            CHANNEL_TRACKING,
            "Toplu Taşıma Takibi",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Toplu taşıma yolculuğu sırasında gösterilir"
        }
        nm.createNotificationChannel(trackingChannel)

        // Hatırlatma bildirimi — yüksek öncelik (heads-up)
        val reminderChannel = NotificationChannel(
            CHANNEL_REMINDER,
            "İniş Hatırlatması",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "İnmeniz gereken durak yaklaştığında uyarır"
            // Telefon sessize alınmış ve titreşim kapalı, ama kanal HIGH olduğu için
            // heads-up notification gösterilir ve WearOS saate yansır
            enableVibration(false)
            setSound(null, null)
        }
        nm.createNotificationChannel(reminderChannel)
    }

    // ── Takip Bildirimi ──────────────────────────────────────────────────────

    private fun vehicleEmoji(): String = when (currentVehicleType) {
        VehicleType.UBAHN.key -> "🚇"
        VehicleType.SBAHN.key -> "🚆"
        VehicleType.RERB.key -> "🚂"
        VehicleType.FERNZUG.key -> "🚄"
        VehicleType.STRASSENBAHN.key -> "🚋"
        else -> "🚌"
    }

    private fun segmentPrefix(): String =
        if (totalSegments > 1) "(${currentSegmentIndex + 1}/$totalSegments) " else ""

    private fun buildTrackingNotification(): Notification {
        val emoji = vehicleEmoji()
        val prefix = segmentPrefix()

        val title = if (hasBoarded) {
            "$emoji $prefix$currentLine — Yolculuk aktif"
        } else {
            "$emoji $prefix$currentLine"
        }

        val arrText = if (currentPlannedArr.isNotBlank()) " ($currentPlannedArr)" else ""
        val text = "📍 İniş: $currentAlightingStop$arrText"

        val builder = NotificationCompat.Builder(this, CHANNEL_TRACKING)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)

        // Aksiyon butonları
        if (!hasBoarded) {
            builder.addAction(
                0,
                "Bindim ✋",
                createActionPendingIntent(TransitNotificationReceiver.ACTION_NOTIF_BINDIM)
            )
        }
        builder.addAction(
            0,
            "İndim 🏁",
            createActionPendingIntent(TransitNotificationReceiver.ACTION_NOTIF_INDIM)
        )

        return builder.build()
    }

    private fun updateTrackingNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID_TRACKING, buildTrackingNotification())
    }

    // ── Hatırlatma Zamanlayıcısı ──────────────────────────────────────────────

    private fun scheduleReminder() {
        if (currentPlannedArr.isBlank()) {
            Log.w(TAG, "Planlanan varış saati yok, hatırlatma kurulamıyor")
            return
        }

        try {
            val arrTime = LocalTime.parse(currentPlannedArr.take(5), DateTimeFormatter.ofPattern("HH:mm"))
            val offsetMinutes = PrefsManager.reminderOffsetMinutes.toLong()
            val reminderTime = arrTime.plusMinutes(offsetMinutes)
            val now = LocalTime.now()

            var delayMs = now.until(reminderTime, ChronoUnit.MILLIS)
            // Eğer geçmişte kaldıysa (gece yarısı geçişi veya zaten geçmiş)
            if (delayMs < 0) {
                delayMs += 24 * 60 * 60 * 1000L
            }
            // Eğer çok kısa (5 saniye altı) ise hemen göster
            if (delayMs < 5_000L) {
                showReminderNotification()
                return
            }

            val triggerAt = System.currentTimeMillis() + delayMs
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(this, TransitNotificationReceiver::class.java).apply {
                action = TransitNotificationReceiver.ACTION_REMINDER_TRIGGER
                putExtra(EXTRA_LINE, currentLine)
                putExtra(EXTRA_ALIGHTING_STOP, currentAlightingStop)
                putExtra(EXTRA_PLANNED_ARR, currentPlannedArr)
                putExtra(EXTRA_VEHICLE_TYPE, currentVehicleType)
            }
            val pi = PendingIntent.getBroadcast(
                this, NOTIF_ID_REMINDER, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Exact alarm kullan (önemli zamanlamalar için)
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                Log.d(TAG, "Hatırlatma kuruldu: ${delayMs / 1000}s sonra ($reminderTime)")
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                Log.w(TAG, "Exact alarm izni yok, inexact alarm kullanılıyor")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Hatırlatma kurulamadı: ${e.message}")
        }
    }

    private fun cancelReminder() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, TransitNotificationReceiver::class.java).apply {
            action = TransitNotificationReceiver.ACTION_REMINDER_TRIGGER
        }
        val pi = PendingIntent.getBroadcast(
            this, NOTIF_ID_REMINDER, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pi)
    }

    /**
     * Servis içinden doğrudan hatırlatma göstermek gerektiğinde (çok kısa delay).
     */
    private fun showReminderNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID_REMINDER, buildReminderNotification(
            currentLine, currentAlightingStop, currentPlannedArr, currentVehicleType
        ))
    }

    /**
     * Hatırlatma bildirimini oluşturur. Receiver'dan da çağrılabilmesi için companion'dan erişilebilir.
     */
    fun buildReminderNotification(
        line: String, alightingStop: String, plannedArr: String, vehicleType: String
    ): Notification {
        val emoji = when (vehicleType) {
            VehicleType.UBAHN.key -> "🚇"
            VehicleType.SBAHN.key -> "🚆"
            VehicleType.RERB.key -> "🚂"
            VehicleType.FERNZUG.key -> "🚄"
            VehicleType.STRASSENBAHN.key -> "🚋"
            else -> "🚌"
        }

        return NotificationCompat.Builder(this, CHANNEL_REMINDER)
            .setContentTitle("⏰ İnmeniz gereken durak!")
            .setContentText("$emoji $line → 📍 $alightingStop ($plannedArr)")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .addAction(
                0,
                "İndim 🏁",
                createActionPendingIntent(TransitNotificationReceiver.ACTION_NOTIF_INDIM)
            )
            .build()
    }

    // ── PendingIntent Yardımcıları ────────────────────────────────────────────

    private fun createActionPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, TransitNotificationReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
