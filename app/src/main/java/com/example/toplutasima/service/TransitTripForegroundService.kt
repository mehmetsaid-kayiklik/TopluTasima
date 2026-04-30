package com.example.toplutasima.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.toplutasima.BuildConfig
import com.example.toplutasima.data.PrefsManager
import com.example.toplutasima.data.TransitReminderType
import com.example.toplutasima.model.VehicleType
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.coroutines.resume
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Toplu taşıma yolculuğu sırasında sürekli (ongoing) bildirim gösterir.
 *
 * Konum bazlı hatırlatma açıkken kısa süreli proximity takibi de yapar.
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
        const val ACTION_START_PROXIMITY_WATCH = "com.example.toplutasima.transit.PROXIMITY_WATCH"
        const val ACTION_HANDLE_INDIM_FROM_NOTIF = "com.example.toplutasima.transit.INDIM_FROM_NOTIF"

        // Intent Extra anahtarları
        const val EXTRA_LINE = "line"
        const val EXTRA_ALIGHTING_STOP = "alightingStop"
        const val EXTRA_PLANNED_ARR = "plannedArr"
        const val EXTRA_VEHICLE_TYPE = "vehicleType"
        const val EXTRA_SEGMENT_INDEX = "segmentIndex"
        const val EXTRA_TOTAL_SEGMENTS = "totalSegments"
        // Legacy name: this is the Firestore id for the active segment record
        // (RmvLogUiState.segmentIds[index]), not a parent TripResult id.
        const val EXTRA_TRIP_ID = "tripId"
        const val EXTRA_ALIGHTING_LAT = "alightingLat"
        const val EXTRA_ALIGHTING_LNG = "alightingLng"
        private const val PROXIMITY_WATCH_REQUEST_CODE = 9012

        // SharedPreferences anahtarları — servis state'ini restart sonrası geri yükler
        private const val PREFS_STATE_NAME = "transit_service_state"
        private const val PKEY_LINE = "transit_state_line"
        private const val PKEY_ALIGHTING_STOP = "transit_state_alighting_stop"
        private const val PKEY_PLANNED_ARR = "transit_state_planned_arr"
        private const val PKEY_VEHICLE_TYPE = "transit_state_vehicle_type"
        private const val PKEY_SEG_IDX = "transit_state_seg_idx"
        private const val PKEY_TOTAL_SEGS = "transit_state_total_segs"
        private const val PKEY_TRIP_ID = "transit_state_trip_id"
        private const val PKEY_LAT = "transit_state_lat"
        private const val PKEY_LNG = "transit_state_lng"
        private const val PKEY_HAS_BOARDED = "transit_state_has_boarded"

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
    // Legacy name kept to avoid changing the existing intent/prefs contract.
    // Holds the active segment record id, not a parent trip id.
    private var currentTripId = ""
    private var hasBoarded = false

    // Proximity alert alanları
    private var alightingLat: Double = Double.NaN
    private var alightingLng: Double = Double.NaN
    private var proximityJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** LOCATION seçiliyken GPS/izin sorunu nedeniyle TIME'a düşüldü mü? */
    private var usingTimeFallback = false

    private lateinit var statePrefs: android.content.SharedPreferences
    private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private fun logD(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    override fun onCreate() {
        super.onCreate()
        statePrefs = getSharedPreferences(PREFS_STATE_NAME, Context.MODE_PRIVATE)
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            if (!restoreServiceState() || !startForegroundForCurrentState(useLocationType = false)) {
                stopSelf()
                return START_NOT_STICKY
            }
            return START_STICKY
        }

        if (intent.action != ACTION_START) {
            restoreServiceState()
        }

        when (intent.action) {
            ACTION_START -> {
                readExtras(intent)
                hasBoarded = false
                persistServiceState()
                if (!startForegroundForCurrentState(useLocationType = false)) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                logD("Bildirim başladı")
            }
            ACTION_UPDATE_BOARDING -> {
                if (!hasUsableServiceState() || !isNotificationActionForCurrentTrip(intent)) {
                    return rejectNotificationAction(startId)
                }
                hasBoarded = true
                usingTimeFallback = false
                persistServiceState()
                if (!startForegroundForCurrentState(useLocationType = false)) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                // Her zaman önce mevcut alarmları temizle, sonra türe göre kur
                cancelReminder()
                cancelProximityWatchAlarm()
                scheduleReminderByType()
                updateTrackingNotification()
                logD("Bindim kaydedildi, hatırlatma türü: ${PrefsManager.transitReminderType}")
            }
            ACTION_NEXT_SEGMENT -> {
                // Önceki segmentten kalan HER İKİ alarmı da iptal et
                cancelReminder()
                cancelProximityWatchAlarm()
                usingTimeFallback = false
                readExtras(intent)
                if (!hasUsableServiceState()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                hasBoarded = false
                persistServiceState()
                if (!startForegroundForCurrentState(useLocationType = false)) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                updateTrackingNotification()
                _activeSegmentIndex.value = currentSegmentIndex
                logD("Sonraki segment bildirimi güncellendi")
            }
            ACTION_START_PROXIMITY_WATCH -> {
                if (!hasUsableServiceState()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startProximityWatchInForeground()
                logD("Proximity watch başlatıldı (alarm ile tetiklendi)")
            }
            ACTION_HANDLE_INDIM_FROM_NOTIF -> {
                if (!hasUsableServiceState() || !isNotificationActionForCurrentTrip(intent)) {
                    return rejectNotificationAction(startId)
                }
                if (!startForegroundForCurrentState(useLocationType = false)) {
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                logD("Bildirimden İndim alındı: segment $currentSegmentIndex / ${totalSegments - 1}")
                if (currentSegmentIndex >= totalSegments - 1) {
                    // Son segment — servisi ve bildirimleri tamamen kapat
                    stopTracking()
                } else {
                    // Son segment değil — ara duruma geç
                    // KNOWN LIMITATION: Sonraki segmentin verileri ViewModel'den gelir.
                    // Uygulama kapalıysa bildirim "bekleniyor" modunda kalır;
                    // uygulama açılınca ViewModel ACTION_NEXT_SEGMENT gönderir.
                    updateTrackingNotificationWaiting()
                }
            }
            ACTION_STOP -> {
                stopTracking()
            }
            else -> {
                Log.w(TAG, "Bilinmeyen servis action: ${intent.action}")
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        cancelReminder()
        cancelProximityWatchAlarm()
        serviceScope.cancel()
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
        currentTripId = intent.getStringExtra(EXTRA_TRIP_ID) ?: currentTripId
        // Proximity alert koordinatları (ACTION_START ve ACTION_NEXT_SEGMENT'te gelir)
        if (intent.hasExtra(EXTRA_ALIGHTING_LAT)) alightingLat = intent.getDoubleExtra(EXTRA_ALIGHTING_LAT, Double.NaN)
        if (intent.hasExtra(EXTRA_ALIGHTING_LNG)) alightingLng = intent.getDoubleExtra(EXTRA_ALIGHTING_LNG, Double.NaN)
    }

    // ── Takip Durdurma ───────────────────────────────────────────────────────

    private fun stopTracking() {
        logD("Bildirim durduruluyor")
        cancelReminder()
        cancelProximityWatchAlarm()
        // State prefs'ini temizle
        if (::statePrefs.isInitialized) statePrefs.edit().clear().apply()
        // Hatırlatma bildirimini de kaldır
        val nm = getSystemService(NotificationManager::class.java)
        nm.cancel(NOTIF_ID_REMINDER)
        _isActive.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ── State Persist / Restore ────────────────────────────────────────

    /**
     * Tüm segment state'ini SharedPreferences'a yazar.
     * Restart (intent == null) veya alarm ile tetiklenmeden önce state kayıplarına karşı.
     */
    private fun persistServiceState() {
        if (!::statePrefs.isInitialized) return
        statePrefs.edit()
            .putString(PKEY_LINE, currentLine)
            .putString(PKEY_ALIGHTING_STOP, currentAlightingStop)
            .putString(PKEY_PLANNED_ARR, currentPlannedArr)
            .putString(PKEY_VEHICLE_TYPE, currentVehicleType)
            .putInt(PKEY_SEG_IDX, currentSegmentIndex)
            .putInt(PKEY_TOTAL_SEGS, totalSegments)
            .putString(PKEY_TRIP_ID, currentTripId)
            .putLong(PKEY_LAT, alightingLat.toBits())
            .putLong(PKEY_LNG, alightingLng.toBits())
            .putBoolean(PKEY_HAS_BOARDED, hasBoarded)
            .apply()
    }

    /** Restart sonrası state'i geri yükler. */
    private fun restoreServiceState(): Boolean {
        if (!::statePrefs.isInitialized)
            statePrefs = getSharedPreferences(PREFS_STATE_NAME, Context.MODE_PRIVATE)
        if (!statePrefs.contains(PKEY_TRIP_ID)) {
            Log.w(TAG, "Kayitli transit servis state'i bulunamadi")
            return false
        }
        currentLine = statePrefs.getString(PKEY_LINE, "") ?: ""
        currentAlightingStop = statePrefs.getString(PKEY_ALIGHTING_STOP, "") ?: ""
        currentPlannedArr = statePrefs.getString(PKEY_PLANNED_ARR, "") ?: ""
        currentVehicleType = statePrefs.getString(PKEY_VEHICLE_TYPE, "") ?: ""
        currentSegmentIndex = statePrefs.getInt(PKEY_SEG_IDX, 0)
        totalSegments = statePrefs.getInt(PKEY_TOTAL_SEGS, 1)
        currentTripId = statePrefs.getString(PKEY_TRIP_ID, "") ?: ""
        alightingLat = Double.fromBits(statePrefs.getLong(PKEY_LAT, Double.NaN.toBits()))
        alightingLng = Double.fromBits(statePrefs.getLong(PKEY_LNG, Double.NaN.toBits()))
        hasBoarded = statePrefs.getBoolean(PKEY_HAS_BOARDED, false)
        logD("Servis state geri yüklendi: line=$currentLine seg=$currentSegmentIndex/$totalSegments")
        return hasUsableServiceState()
    }

    private fun hasUsableServiceState(): Boolean {
        val hasValidSegment = totalSegments > 0 && currentSegmentIndex in 0 until totalSegments
        val hasTripId = currentTripId.isNotBlank()
        if (!hasValidSegment || !hasTripId) {
            Log.w(
                TAG,
                "Eksik transit state: tripId=$currentTripId seg=$currentSegmentIndex total=$totalSegments"
            )
        }
        return hasValidSegment && hasTripId
    }

    private fun isNotificationActionForCurrentTrip(intent: Intent): Boolean {
        val actionTripId = intent.getStringExtra(EXTRA_TRIP_ID).orEmpty()
        val matches = actionTripId.isNotBlank() && actionTripId == currentTripId
        if (!matches) {
            Log.w(
                TAG,
                "Bildirim aksiyonu reddedildi: actionTripId=$actionTripId currentTripId=$currentTripId"
            )
        }
        return matches
    }

    private fun rejectNotificationAction(startId: Int): Int {
        if (hasUsableServiceState() && startForegroundForCurrentState(useLocationType = false)) {
            return START_STICKY
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }

    private fun startForegroundForCurrentState(useLocationType: Boolean): Boolean {
        if (!hasUsableServiceState()) return false
        return try {
            val notification = buildTrackingNotification()
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                    val type = if (useLocationType) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                    } else {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    }
                    startForeground(NOTIF_ID_TRACKING, notification, type)
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && useLocationType -> {
                    startForeground(
                        NOTIF_ID_TRACKING,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                    )
                }
                else -> startForeground(NOTIF_ID_TRACKING, notification)
            }
            _isActive.value = true
            _activeSegmentIndex.value = currentSegmentIndex
            true
        } catch (e: Exception) {
            Log.e(TAG, "startForeground basarisiz: ${e.message}", e)
            false
        }
    }

    // ── Hatırlatma Türüne Göre Kurulum ──────────────────────────────────

    /**
     * PrefsManager.transitReminderType'a göre doğru hatırlatmayı kurar.
     * LOCATION için 3 koşulda fallback:
     *   1. Koordinat NaN   2. Konum izni yok   3. GPS alınamaz (startProximityWatch içinde)
     */
    private fun scheduleReminderByType() {
        when (PrefsManager.transitReminderType) {
            TransitReminderType.LOCATION -> {
                val hasCoords = !alightingLat.isNaN() && !alightingLng.isNaN()
                val hasPermission = hasLocationPermission()
                if (hasCoords && hasPermission) {
                    scheduleProximityWatchAlarm()
                    // Koşul 3 (GPS alınamaz) startProximityWatch() içinde ele alınır
                } else {
                    val reason = if (!hasPermission) "izin yok" else "koordinat yok"
                    Log.w(TAG, "LOCATION seçili ama fallback TIME: $reason")
                    usingTimeFallback = true
                    scheduleReminder()
                    // Fallback mesajı updateTrackingNotification() çağrısıyla gösterilecek
                }
            }
            TransitReminderType.TIME -> scheduleReminder()
            TransitReminderType.NONE -> logD("Hatırlatma türü NONE — hatırlatma kurulmadı")
        }
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

    private fun vehicleEmoji(type: String = currentVehicleType): String = when (type) {
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
        val fallbackSuffix = if (usingTimeFallback) " · ⏰ Saat bazlı hatırlatma aktif" else ""
        val text = "📍 İniş: $currentAlightingStop$arrText$fallbackSuffix"

        val contentIntent = Intent(this, com.example.toplutasima.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_TRACKING)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)

        if (totalSegments > 1) {
            builder.setProgress(totalSegments, currentSegmentIndex + 1, false)
        }

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

    /** Bir sonraki segment bekleniyor ara durumu bildirimi. */
    private fun updateTrackingNotificationWaiting() {
        val contentIntent = Intent(this, com.example.toplutasima.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPi = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_TRACKING)
            .setContentTitle("🔄 Aktarma bekleniyor")
            .setContentText("Sonraki aktarma bekleniyor...")
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentIntent(contentPi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID_TRACKING, notification)
    }

    // ── Hatırlatma Zamanlayıcısı ──────────────────────────────────────────────

    @SuppressLint("MissingPermission", "ScheduleExactAlarm")
    private fun scheduleReminder() {
        if (currentPlannedArr.isBlank()) {
            Log.w(TAG, "Planlanan varış saati yok, hatırlatma kurulamıyor")
            return
        }

        try {
            val timeString = currentPlannedArr.take(5)
            val paddedTime = if (timeString.contains(":") && timeString.length < 5) timeString.padStart(5, '0') else timeString
            val arrTime = LocalTime.parse(paddedTime, DateTimeFormatter.ofPattern("HH:mm"))
            val offsetMinutes = PrefsManager.reminderOffsetMinutes.toLong()
            val reminderTime = arrTime.plusMinutes(offsetMinutes)
            val now = LocalTime.now()

            var delayMs = now.until(reminderTime, ChronoUnit.MILLIS)
            // Eğer geçmişte kaldıysa (gece yarısı geçişi veya zaten geçmiş)
            if (delayMs < 0) {
                // Eğer hedef saat 12 saatten daha fazla geride görünüyorsa, yarına aittir (gece yarısı geçişi)
                if (delayMs < -12 * 60 * 60 * 1000L) {
                    delayMs += 24 * 60 * 60 * 1000L
                } else {
                    // Sadece birkaç saat/dakika geçmişteyse, gerçekten geçmiş demektir
                    showReminderNotification()
                    return
                }
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
                putExtra(EXTRA_TRIP_ID, currentTripId)
            }
            val pi = PendingIntent.getBroadcast(
                this, NOTIF_ID_REMINDER, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val canUseExactAlarm =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

            if (canUseExactAlarm) {
                try {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                    logD("Hatırlatma kuruldu: ${delayMs / 1000}s sonra ($reminderTime)")
                } catch (e: SecurityException) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                    Log.w(TAG, "Exact alarm güvenlik hatası, inexact alarm kullanılıyor: ${e.message}")
                }
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
        val emoji = vehicleEmoji(vehicleType)

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
            putExtra(EXTRA_TRIP_ID, currentTripId)
        }
        return PendingIntent.getBroadcast(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // ── Proximity Watch ───────────────────────────────────────────────────────

    /**
     * Bindim kaydedildikten sonra çağrılır.
     * plannedArr - 3 dakikaya bir AlarmManager alarmı kurar.
     * Alarm tetiklenince ACTION_START_PROXIMITY_WATCH → startProximityWatch() çalışır.
     * Koordinat yoksa (manuel kayıt) sessizce çıkar — scheduleReminder() fallback olarak yeterli.
     */
    @SuppressLint("MissingPermission", "ScheduleExactAlarm")
    private fun scheduleProximityWatchAlarm() {
        if (alightingLat.isNaN() || alightingLng.isNaN()) return
        if (currentPlannedArr.isBlank()) return
        try {
            val timeString = currentPlannedArr.take(5)
            val paddedTime = if (timeString.contains(":") && timeString.length < 5)
                timeString.padStart(5, '0') else timeString
            val arrTime = LocalTime.parse(paddedTime, DateTimeFormatter.ofPattern("HH:mm"))
            val watchTime = arrTime.minusMinutes(3)
            val now = LocalTime.now()
            var delayMs = now.until(watchTime, ChronoUnit.MILLIS)
            if (delayMs < 0) {
                if (delayMs < -12 * 60 * 60 * 1000L) delayMs += 24 * 60 * 60 * 1000L
                else { startProximityWatchInForeground(); return }
            }
            if (delayMs < 5_000L) { startProximityWatchInForeground(); return }

            val triggerAt = System.currentTimeMillis() + delayMs
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = PendingIntent.getForegroundService(
                this, PROXIMITY_WATCH_REQUEST_CODE,
                Intent(this, TransitTripForegroundService::class.java).apply {
                    action = ACTION_START_PROXIMITY_WATCH
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
            if (canExact) {
                try { alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi) }
                catch (e: SecurityException) { alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi) }
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
            logD("Proximity watch alarmı kuruldu: ${delayMs / 1000}s sonra ($watchTime)")
        } catch (e: Exception) {
            Log.e(TAG, "Proximity watch alarmı kurulamadı: ${e.message}")
        }
    }

    /** Proximity watch alarmını ve coroutine'ini iptal eder. */
    private fun cancelProximityWatchAlarm() {
        proximityJob?.cancel()
        proximityJob = null
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = PendingIntent.getForegroundService(
                this, PROXIMITY_WATCH_REQUEST_CODE,
                Intent(this, TransitTripForegroundService::class.java).apply {
                    action = ACTION_START_PROXIMITY_WATCH
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pi)
        } catch (_: Exception) {}
    }

    /**
     * 30 saniyede bir GPS kontrol eder.
     * İniş durağına ≤500m yaklaşıldığında bildirim gönderir ve döngüyü bitirir.
     * Koşul 3 (GPS alınamaz): 3 ardışık başarısız lokasyon denemesinden sonra
     * TIME fallback devreye girer ve proximity watch durur.
     */
    private fun startProximityWatchInForeground() {
        val hasCoords = !alightingLat.isNaN() && !alightingLng.isNaN()
        if (!hasCoords || !hasLocationPermission()) {
            fallbackToTimeReminder(if (!hasCoords) "koordinat yok" else "konum izni yok")
            return
        }
        if (!startForegroundForCurrentState(useLocationType = true)) {
            fallbackToTimeReminder("location foreground baslatilamadi")
            return
        }
        startProximityWatch()
    }

    private fun fallbackToTimeReminder(reason: String) {
        Log.w(TAG, "LOCATION hatirlatma TIME fallback'e dustu: $reason")
        usingTimeFallback = true
        if (!startForegroundForCurrentState(useLocationType = false)) {
            stopSelf()
            return
        }
        scheduleReminder()
    }

    private fun startProximityWatch() {
        if (alightingLat.isNaN() || alightingLng.isNaN()) return
        if (!hasLocationPermission()) return
        logD("Proximity watch başlıyor → hedef: ($alightingLat, $alightingLng)")
        proximityJob?.cancel()
        proximityJob = serviceScope.launch {
            var consecutiveNullCount = 0
            while (isActive) {
                delay(30_000L)
                try {
                    val loc = getCurrentLocationSuspend()
                    if (loc == null) {
                        consecutiveNullCount++
                        Log.w(TAG, "GPS alınamadı ($consecutiveNullCount/3)")
                        if (consecutiveNullCount >= 3) {
                            // Koşul 3: GPS alınamıyor — TIME fallback
                            Log.w(TAG, "GPS sürekli alınamıyor, TIME fallback devreye girdi")
                            usingTimeFallback = true
                            startForegroundForCurrentState(useLocationType = false)
                            scheduleReminder()
                            updateTrackingNotification()
                            break
                        }
                        continue
                    }
                    consecutiveNullCount = 0
                    val dist = haversineMeters(loc.first, loc.second, alightingLat, alightingLng)
                    logD("Proximity check: ${dist.toInt()}m")
                    if (dist <= 500.0) {
                        showReminderNotification()
                        cancelReminder()   // Fallback AlarmManager'ı da iptal et
                        startForegroundForCurrentState(useLocationType = false)
                        break
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Proximity check hatası: ${e.message}")
                }
            }
        }
    }


    @Suppress("MissingPermission")
    private suspend fun getCurrentLocationSuspend(): Pair<Double, Double>? {
        if (!hasLocationPermission()) return null
        val tokenSource = CancellationTokenSource()
        return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { tokenSource.cancel() }
            fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, tokenSource.token)
                .addOnSuccessListener { loc ->
                    if (loc != null) cont.resume(loc.latitude to loc.longitude)
                    else fusedClient.lastLocation
                        .addOnSuccessListener { last -> cont.resume(if (last != null) last.latitude to last.longitude else null) }
                        .addOnFailureListener { cont.resume(null) }
                }
                .addOnFailureListener { cont.resume(null) }
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    /** Haversine formülü ile iki koordinat arasındaki mesafeyi metre cinsinden hesaplar. */
    private fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val R = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return R * 2 * asin(sqrt(a))
    }
}
