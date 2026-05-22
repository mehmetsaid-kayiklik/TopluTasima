package com.example.toplutasima.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.toplutasima.BuildConfig
import com.example.toplutasima.data.PrefsManager
import com.example.toplutasima.data.TransitAutoActualTimeMode
import com.example.toplutasima.data.TransitReminderType
import com.example.toplutasima.service.transit.TransitActionIntents
import com.example.toplutasima.service.transit.TransitNotificationBuilder
import com.example.toplutasima.service.transit.TransitReminderScheduler
import com.example.toplutasima.service.transit.TransitServiceStateStore
import com.example.toplutasima.worker.TransitActionWorker
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
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
        const val CHANNEL_REMINDER = "transit_trip_reminder_v2"

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
        const val ACTION_REFRESH_REMINDER_SETTINGS = "com.example.toplutasima.transit.REFRESH_REMINDER_SETTINGS"

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
        private const val EXTRA_FROM_NOTIFICATION_ACTION = "fromNotificationAction"
        private const val PROXIMITY_CHECK_INTERVAL_MS = 30_000L
        private const val ALIGHTING_PROXIMITY_METERS = 100.0
        private const val CLOSE_ALIGHTING_PROXIMITY_METERS = 50.0
        private const val VALID_WALKING_CONFIDENCE = 70
        private const val CLOSE_AUTO_ALIGHTING_MAX_SPEED_MPS = 1.0
        private const val AUTO_ALIGHTING_MAX_SPEED_MPS = 2.0
        // ViewModel tarafından gözlemlenen durum
        private val _isActive = MutableStateFlow(false)
        val isActive: StateFlow<Boolean> = _isActive

        private val _activeSegmentIndex = MutableStateFlow(0)
        val activeSegmentIndex: StateFlow<Int> = _activeSegmentIndex

        internal val _currentActivityConfidence =
            MutableStateFlow(DetectedActivity.UNKNOWN to 0)
        val currentActivityConfidence: StateFlow<Pair<Int, Int>> = _currentActivityConfidence

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
    private var isCurrentlyWalking = false
    private var consecutiveWalkingLoops = 0
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** LOCATION seçiliyken GPS/izin sorunu nedeniyle TIME'a düşüldü mü? */
    private var usingTimeFallback = false

    private val stateStore by lazy { TransitServiceStateStore(this) }
    private val notificationBuilder by lazy { TransitNotificationBuilder(this) }
    private val reminderScheduler by lazy {
        TransitReminderScheduler(
            context = this,
            onImmediateReminder = ::showReminderNotification,
            logDebug = ::logD
        )
    }
    private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val activityRecognitionClient by lazy { ActivityRecognition.getClient(this) }

    private fun logD(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    override fun onCreate() {
        super.onCreate()
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
                if (intent.getBooleanExtra(EXTRA_FROM_NOTIFICATION_ACTION, false)) {
                    enqueueTransitActionWorker(isBoarding = true)
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
                if (intent.getBooleanExtra(EXTRA_FROM_NOTIFICATION_ACTION, false)) {
                    enqueueTransitActionWorker(isBoarding = false)
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
                    // Hatırlatma bildirimini temizle (İndim basıldı)
                    getSystemService(NotificationManager::class.java).cancel(NOTIF_ID_REMINDER)
                    cancelReminder()
                    cancelProximityWatchAlarm()
                    // KNOWN LIMITATION: Sonraki segmentin verileri ViewModel'den gelir.
                    // Uygulama kapalıysa bildirim "bekleniyor" modunda kalır;
                    // uygulama açılınca ViewModel ACTION_NEXT_SEGMENT gönderir.
                    updateTrackingNotificationWaiting()
                }
            }
            ACTION_REFRESH_REMINDER_SETTINGS -> {
                if (!hasUsableServiceState()) {
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                if (!startForegroundForCurrentState(useLocationType = false)) {
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                cancelReminder()
                cancelProximityWatchAlarm()
                getSystemService(NotificationManager::class.java).cancel(NOTIF_ID_REMINDER)
                usingTimeFallback = false
                if (hasBoarded) scheduleReminderByType()
                updateTrackingNotification()
                logD("Hatırlatma ayarları yeniden uygulandı: ${PrefsManager.transitReminderType}")
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
        stateStore.clear()
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
        stateStore.save(
            TransitServiceStateStore.State(
                line = currentLine,
                alightingStop = currentAlightingStop,
                plannedArr = currentPlannedArr,
                vehicleType = currentVehicleType,
                segmentIndex = currentSegmentIndex,
                totalSegments = totalSegments,
                tripId = currentTripId,
                alightingLat = alightingLat,
                alightingLng = alightingLng,
                hasBoarded = hasBoarded
            )
        )
    }

    /** Restart sonrası state'i geri yükler. */
    private fun restoreServiceState(): Boolean {
        val state = stateStore.restore()
        if (state == null) {
            Log.w(TAG, "Kayitli transit servis state'i bulunamadi")
            return false
        }
        currentLine = state.line
        currentAlightingStop = state.alightingStop
        currentPlannedArr = state.plannedArr
        currentVehicleType = state.vehicleType
        currentSegmentIndex = state.segmentIndex
        totalSegments = state.totalSegments
        currentTripId = state.tripId
        alightingLat = state.alightingLat
        alightingLng = state.alightingLng
        hasBoarded = state.hasBoarded
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
     * LOCATION seçiliyken GPS/izin/koordinat sorunu TIME alarmına düşmez.
     */
    private fun scheduleReminderByType() {
        val needsLocationWatch =
            PrefsManager.transitReminderType == TransitReminderType.LOCATION ||
                PrefsManager.transitAutoActualTimeMode != TransitAutoActualTimeMode.OFF

        if (needsLocationWatch) {
            val hasCoords = !alightingLat.isNaN() && !alightingLng.isNaN()
            val hasPermission = hasLocationPermission()
            if (hasCoords && hasPermission) {
                scheduleProximityWatchAlarm()
                return
            }
            if (PrefsManager.transitReminderType == TransitReminderType.LOCATION) {
                val reason = if (!hasPermission) "izin yok" else "koordinat yok"
                Log.w(TAG, "LOCATION secili ama konum hatirlatmasi kurulamadi: $reason")
                return
            }
            Log.w(TAG, "Otomatik saat icin GPS izleme baslatilamadi")
        }

        when (PrefsManager.transitReminderType) {
            TransitReminderType.LOCATION -> {
                logD("Hatırlatma türü LOCATION — saat bazlı fallback kurulmadı")
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
            enableVibration(true)
            setVibrationPattern(TransitNotificationBuilder.REMINDER_VIBRATION_PATTERN)
            setSound(null, null)
        }
        nm.createNotificationChannel(reminderChannel)
    }

    // ── Takip Bildirimi ──────────────────────────────────────────────────────

    private fun buildTrackingNotification(): Notification =
        notificationBuilder.buildTrackingNotification(
            TransitNotificationBuilder.TrackingState(
                line = currentLine,
                alightingStop = currentAlightingStop,
                plannedArr = currentPlannedArr,
                vehicleType = currentVehicleType,
                segmentIndex = currentSegmentIndex,
                totalSegments = totalSegments,
                tripId = currentTripId,
                hasBoarded = hasBoarded,
                usingTimeFallback = usingTimeFallback
            )
        )

    private fun updateTrackingNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID_TRACKING, buildTrackingNotification())
    }

    /** Bir sonraki segment bekleniyor ara durumu bildirimi. */
    private fun updateTrackingNotificationWaiting() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID_TRACKING, notificationBuilder.buildWaitingForNextSegmentNotification())
    }

    // ── Hatırlatma Zamanlayıcısı ──────────────────────────────────────────────
    private fun scheduleReminder() {
        reminderScheduler.schedule(
            TransitReminderScheduler.ReminderState(
                line = currentLine,
                alightingStop = currentAlightingStop,
                plannedArr = currentPlannedArr,
                vehicleType = currentVehicleType,
                tripId = currentTripId
            )
        )
    }

    private fun cancelReminder() = reminderScheduler.cancel()

    /**
     * Servis içinden doğrudan hatırlatma göstermek gerektiğinde (çok kısa delay).
     */
    private fun showReminderNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID_REMINDER, buildReminderNotification(
            currentLine, currentAlightingStop, currentPlannedArr, currentVehicleType, currentTripId
        ))
    }

    /**
     * Hatırlatma bildirimini oluşturur. Receiver'dan da çağrılabilmesi için companion'dan erişilebilir.
     */
    fun buildReminderNotification(
        line: String, alightingStop: String, plannedArr: String, vehicleType: String,
        tripId: String = currentTripId
    ): Notification =
        notificationBuilder.buildReminderNotification(line, alightingStop, plannedArr, vehicleType, tripId)

    // ── PendingIntent Yardımcıları ────────────────────────────────────────────

    private fun enqueueTransitActionWorker(isBoarding: Boolean) {
        if (currentTripId.isBlank()) return
        val timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        val workData = Data.Builder()
            .putString(TransitActionWorker.KEY_TRIP_ID, currentTripId)
            .putBoolean(TransitActionWorker.KEY_IS_BOARDING, isBoarding)
            .putString(TransitActionWorker.KEY_TIMESTAMP, timestamp)
            .build()
        val workRequest = OneTimeWorkRequestBuilder<TransitActionWorker>()
            .setInputData(workData)
            .build()
        WorkManager.getInstance(applicationContext).enqueue(workRequest)
    }

    // ── Proximity Watch ───────────────────────────────────────────────────────

    /**
     * Bindim kaydedildikten sonra çağrılır.
     * plannedArr - 3 dakikaya bir AlarmManager alarmı kurar.
     * Alarm tetiklenince ACTION_START_PROXIMITY_WATCH → startProximityWatch() çalışır.
     * Koordinat yoksa (manuel kayıt) sessizce çıkar — scheduleReminder() fallback olarak yeterli.
     */
    private fun scheduleProximityWatchAlarm() {
        if (alightingLat.isNaN() || alightingLng.isNaN()) return
        reminderScheduler.scheduleProximityWatch(
            plannedArr = currentPlannedArr,
            onImmediateStart = ::startProximityWatchInForeground
        )
    }

    /** Proximity watch alarmını ve coroutine'ini iptal eder. */
    private fun cancelProximityWatchAlarm() {
        proximityJob?.cancel()
        proximityJob = null
        isCurrentlyWalking = false
        consecutiveWalkingLoops = 0
        removeActivityUpdates()
        reminderScheduler.cancelProximityWatchAlarm()
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

    @SuppressLint("MissingPermission")
    private fun requestActivityUpdates() {
        _currentActivityConfidence.value = DetectedActivity.UNKNOWN to 0
        if (!hasActivityRecognitionPermission()) {
            logD("Activity Recognition izni yok, GPS fallback aktif kalacak")
            return
        }

        activityRecognitionClient
            .requestActivityUpdates(
                PROXIMITY_CHECK_INTERVAL_MS,
                TransitActionIntents.activityRecognitionPendingIntent(this)
            )
            .addOnSuccessListener { logD("Activity Recognition updates baslatildi") }
            .addOnFailureListener { e ->
                Log.w(TAG, "Activity Recognition updates baslatilamadi: ${e.message}")
            }
    }

    private fun removeActivityUpdates() {
        _currentActivityConfidence.value = DetectedActivity.UNKNOWN to 0
        try {
            activityRecognitionClient
                .removeActivityUpdates(TransitActionIntents.activityRecognitionPendingIntent(this))
                .addOnSuccessListener { logD("Activity Recognition updates durduruldu") }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Activity Recognition updates durdurulamadi: ${e.message}")
                }
        } catch (e: SecurityException) {
            Log.w(TAG, "Activity Recognition updates durdurulamadi: ${e.message}")
        }
    }

    private fun fallbackToTimeReminder(reason: String) {
        Log.w(TAG, "Konum takibi baslatilamadi: $reason")
        usingTimeFallback = false
        if (!startForegroundForCurrentState(useLocationType = false)) {
            stopSelf()
            return
        }
        if (PrefsManager.transitReminderType == TransitReminderType.TIME) {
            usingTimeFallback = true
            scheduleReminder()
        } else {
            Log.w(TAG, "Saat bazli fallback kapali, hatirlatma kurulmayacak")
            updateTrackingNotification()
        }
    }

    private fun startProximityWatch() {
        if (alightingLat.isNaN() || alightingLng.isNaN()) return
        if (!hasLocationPermission()) return
        logD("Proximity watch başlıyor → hedef: ($alightingLat, $alightingLng)")
        proximityJob?.cancel()
        isCurrentlyWalking = false
        consecutiveWalkingLoops = 0
        requestActivityUpdates()
        proximityJob = serviceScope.launch {
            var consecutiveNullCount = 0
            var previousLocation: Location? = null
            while (isActive) {
                delay(PROXIMITY_CHECK_INTERVAL_MS)
                try {
                    val loc = getCurrentLocationSuspend()
                    if (loc == null) {
                        isCurrentlyWalking = false
                        consecutiveWalkingLoops = 0
                        consecutiveNullCount++
                        Log.w(TAG, "GPS alınamadı ($consecutiveNullCount/3)")
                        if (consecutiveNullCount >= 3) {
                            Log.w(TAG, "GPS surekli alinamiyor, konum hatirlatmasi durduruldu")
                            usingTimeFallback = false
                            startForegroundForCurrentState(useLocationType = false)
                            if (PrefsManager.transitReminderType == TransitReminderType.TIME) {
                                usingTimeFallback = true
                                scheduleReminder()
                            }
                            updateTrackingNotification()
                            removeActivityUpdates()
                            break
                        }
                        continue
                    }
                    consecutiveNullCount = 0
                    val dist = haversineMeters(loc.latitude, loc.longitude, alightingLat, alightingLng)
                    val speedMps = movementSpeedMps(loc, previousLocation)
                    previousLocation = loc
                    logD("Proximity check: ${dist.toInt()}m speed=${speedMps?.let { "%.1f".format(it) } ?: "unknown"}m/s")
                    if (dist <= ALIGHTING_PROXIMITY_METERS) {
                        if (PrefsManager.transitAutoActualTimeMode == TransitAutoActualTimeMode.AUTO) {
                            if (dist <= CLOSE_ALIGHTING_PROXIMITY_METERS) {
                                consecutiveWalkingLoops = 0
                                if (speedMps != null && speedMps <= CLOSE_AUTO_ALIGHTING_MAX_SPEED_MPS) {
                                    handleAutoAlighting()
                                    break
                                }
                                logD("Auto Indim bekliyor: 50m icinde ama hareket hizi hala yuksek")
                            } else {
                                isCurrentlyWalking = isValidWalkingActivity(_currentActivityConfidence.value)
                                if (isCurrentlyWalking) {
                                    consecutiveWalkingLoops++
                                    if (consecutiveWalkingLoops >= 2) {
                                        handleAutoAlighting()
                                        break
                                    }
                                    logD("Auto Indim yurume teyidi bekliyor: $consecutiveWalkingLoops/2")
                                } else {
                                    consecutiveWalkingLoops = 0
                                    if (isSlowEnoughForAutoAlighting(speedMps)) {
                                        handleAutoAlighting()
                                        break
                                    }
                                    logD("Auto Indim bekliyor: hedefe yakin ama hareket hizi hala yuksek")
                                }
                            }
                        } else {
                            showReminderNotification()
                            cancelReminder()   // Fallback AlarmManager'ı da iptal et
                            startForegroundForCurrentState(useLocationType = false)
                            removeActivityUpdates()
                            break
                        }
                    } else {
                        isCurrentlyWalking = false
                        consecutiveWalkingLoops = 0
                    }
                } catch (e: Exception) {
                    isCurrentlyWalking = false
                    consecutiveWalkingLoops = 0
                    Log.w(TAG, "Proximity check hatası: ${e.message}")
                }
            }
        }
    }

    private fun handleAutoAlighting() {
        enqueueTransitActionWorker(isBoarding = false)
        getSystemService(NotificationManager::class.java).cancel(NOTIF_ID_REMINDER)
        cancelReminder()
        cancelProximityWatchAlarm()
        if (currentSegmentIndex >= totalSegments - 1) {
            stopTracking()
        } else {
            updateTrackingNotificationWaiting()
        }
        logD("GPS proximity otomatik Indim kaydi olusturdu")
    }


    @Suppress("MissingPermission")
    private suspend fun getCurrentLocationSuspend(): Location? {
        if (!hasLocationPermission()) return null
        val tokenSource = CancellationTokenSource()
        return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { tokenSource.cancel() }
            fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, tokenSource.token)
                .addOnSuccessListener { loc ->
                    if (loc != null) cont.resume(loc)
                    else fusedClient.lastLocation
                        .addOnSuccessListener { last -> cont.resume(last) }
                        .addOnFailureListener { cont.resume(null) }
                }
                .addOnFailureListener { cont.resume(null) }
        }
    }

    private fun isSlowEnoughForAutoAlighting(speedMps: Double?): Boolean =
        speedMps != null && speedMps <= AUTO_ALIGHTING_MAX_SPEED_MPS

    private fun movementSpeedMps(current: Location, previous: Location?): Double? {
        if (current.hasSpeed()) return current.speed.toDouble()
        if (previous == null) return null
        val seconds = locationDeltaSeconds(previous, current) ?: return null
        if (seconds <= 0.0) return null
        val meters = haversineMeters(previous.latitude, previous.longitude, current.latitude, current.longitude)
        return meters / seconds
    }

    private fun locationDeltaSeconds(previous: Location, current: Location): Double? {
        val elapsedNanos = current.elapsedRealtimeNanos - previous.elapsedRealtimeNanos
        if (elapsedNanos > 0L) return elapsedNanos / 1_000_000_000.0
        val wallMillis = current.time - previous.time
        if (wallMillis > 0L) return wallMillis / 1_000.0
        return null
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    private fun hasActivityRecognitionPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) ==
                PackageManager.PERMISSION_GRANTED

    private fun isValidWalkingActivity(activityConfidence: Pair<Int, Int>): Boolean {
        val (activityType, confidence) = activityConfidence
        return activityType in setOf(
            DetectedActivity.WALKING,
            DetectedActivity.ON_FOOT,
            DetectedActivity.RUNNING
        ) && confidence >= VALID_WALKING_CONFIDENCE
    }

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
