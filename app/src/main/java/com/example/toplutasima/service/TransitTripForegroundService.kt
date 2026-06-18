package com.example.toplutasima.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.toplutasima.BuildConfig
import com.example.toplutasima.diagnostics.TransitTrackerLogger
import com.example.toplutasima.data.PrefsManager
import com.example.toplutasima.data.TransitAutoActualTimeMode
import com.example.toplutasima.data.TransitReminderType
import com.example.toplutasima.service.transit.TransitNotificationBuilder
import com.example.toplutasima.service.transit.TransitProximityTracker
import com.example.toplutasima.service.transit.TransitReminderScheduler
import com.example.toplutasima.service.transit.TransitServiceStateStore
import com.example.toplutasima.worker.TransitActionWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

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
        const val EXTRA_SEGMENT_IDS = "segmentIds"
        const val EXTRA_ALIGHTING_LAT = "alightingLat"
        const val EXTRA_ALIGHTING_LNG = "alightingLng"
        private const val EXTRA_FROM_NOTIFICATION_ACTION = "fromNotificationAction"
        // ViewModel tarafından gözlemlenen durum
        private val _isActive = MutableStateFlow(false)
        val isActive: StateFlow<Boolean> = _isActive

        private val _activeSegmentIndex = MutableStateFlow(0)
        val activeSegmentIndex: StateFlow<Int> = _activeSegmentIndex

        val currentActivityConfidence: StateFlow<Pair<Int, Int>> =
            TransitProximityTracker.currentActivityConfidence

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
    private var segmentIds: List<String> = emptyList()
    private var hasBoarded = false
    @Volatile
    private var isServiceDestroyed = false

    private data class AutoAlightWorkRequest(
        val uniqueName: String,
        val workId: UUID
    )

    private data class AutoAlightWorkObserver(
        val liveData: LiveData<List<WorkInfo>>,
        val observer: Observer<List<WorkInfo>>
    )

    // Proximity alert alanları
    private var alightingLat: Double = Double.NaN
    private var alightingLng: Double = Double.NaN
    /** LOCATION seçiliyken GPS/izin sorunu nedeniyle TIME'a düşüldü mü? */
    private var usingTimeFallback = false

    private val stateStore by lazy { TransitServiceStateStore(this) }
    private val notificationBuilder by lazy { TransitNotificationBuilder(this) }
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val workStatusExecutor by lazy { Executor { command -> mainHandler.post(command) } }
    private val autoAlightWorkObservers = mutableMapOf<String, AutoAlightWorkObserver>()
    private val autoAlightWorkStates = mutableMapOf<UUID, WorkInfo.State>()
    private val reminderScheduler by lazy {
        TransitReminderScheduler(
            context = this,
            onImmediateReminder = ::showReminderNotification,
            logDebug = ::logD
        )
    }
    private val proximityTracker by lazy {
        TransitProximityTracker(
            context = this,
            autoModeProvider = { PrefsManager.transitAutoActualTimeMode },
            onGpsFallback = ::handleGpsFallback,
            onManualReminderReached = ::handleProximityReminder,
            onAutoAlighting = ::handleAutoAlighting,
            logDebug = ::logD
        )
    }

    private fun logD(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
        TransitTrackerLogger.log(this, TAG, message)
    }

    private fun formatWorkData(data: Data): String {
        val values = data.keyValueMap
        if (values.isEmpty()) return "{}"

        return values.entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            "$key=${formatDataValue(value)}"
        }
    }

    private fun formatDataValue(value: Any?): String =
        when (value) {
            is Array<*> -> value.contentDeepToString()
            is BooleanArray -> value.contentToString()
            is ByteArray -> value.contentToString()
            is DoubleArray -> value.contentToString()
            is FloatArray -> value.contentToString()
            is IntArray -> value.contentToString()
            is LongArray -> value.contentToString()
            else -> value.toString()
        }

    override fun onCreate() {
        super.onCreate()
        isServiceDestroyed = false
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
                TransitTrackerLogger.cleanOldLogs(this, maxDaysToKeep = 2)
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
        proximityTracker.shutdown()
        isServiceDestroyed = true
        removeAutoAlightWorkObservers()
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
        intent.getStringArrayExtra(EXTRA_SEGMENT_IDS)?.let { ids ->
            segmentIds = ids.toList()
        }
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
                hasBoarded = hasBoarded,
                segmentIds = normalizedSegmentIds()
            )
        )
    }

    private fun normalizedSegmentIds(): List<String> {
        val validTotal = totalSegments.coerceAtLeast(1)
        val normalized = MutableList(validTotal) { index -> segmentIds.getOrNull(index).orEmpty() }
        if (currentSegmentIndex in normalized.indices && normalized[currentSegmentIndex].isBlank()) {
            normalized[currentSegmentIndex] = currentTripId
        }
        return normalized
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
        segmentIds = state.segmentIds
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
            val hasPermission = proximityTracker.hasLocationPermission()
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
            .addTag(TransitActionWorker.TAG)
            .build()
        TransitTrackerLogger.log(
            applicationContext,
            TAG,
            "About to enqueue TransitActionWorker workId=${workRequest.id} " +
                "class=${TransitActionWorker::class.java.name} tripId=$currentTripId isBoarding=$isBoarding"
        )
        val operation = WorkManager.getInstance(applicationContext).enqueue(workRequest)
        operation.result.addListener(
            {
                try {
                    operation.result.get()
                    TransitTrackerLogger.log(
                        applicationContext,
                        TAG,
                        "enqueue operation SUCCEEDED for TransitActionWorker workId=${workRequest.id}"
                    )
                } catch (e: Exception) {
                    TransitTrackerLogger.log(
                        applicationContext,
                        TAG,
                        "enqueue operation FAILED for TransitActionWorker workId=${workRequest.id}: ${e.message}"
                    )
                }
            },
            Executor { it.run() }
        )
    }

    private fun enqueueTransitActionWorkerWithId(
        segId: String,
        isBoarding: Boolean
    ): AutoAlightWorkRequest {
        val timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        val workData = Data.Builder()
            .putString(TransitActionWorker.KEY_TRIP_ID, segId)
            .putBoolean(TransitActionWorker.KEY_IS_BOARDING, isBoarding)
            .putString(TransitActionWorker.KEY_TIMESTAMP, timestamp)
            .build()
        val workRequest = OneTimeWorkRequestBuilder<TransitActionWorker>()
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
            .setInputData(workData)
            .addTag(TransitActionWorker.TAG)
            .build()
        val uniqueName = "autoAlight_$segId"
        val workManager = WorkManager.getInstance(applicationContext)
        TransitTrackerLogger.log(
            applicationContext,
            TAG,
            "About to call enqueueUniqueWork for $uniqueName workId=${workRequest.id} " +
                "class=${TransitActionWorker::class.java.name}"
        )
        val operation = workManager.enqueueUniqueWork(
            uniqueName,
            ExistingWorkPolicy.KEEP,
            workRequest
        )
        TransitTrackerLogger.log(
            applicationContext,
            TAG,
            "enqueueUniqueWork() returned, operation=${operation.result}"
        )
        operation.result.addListener(
            {
                try {
                    operation.result.get()
                    TransitTrackerLogger.log(
                        applicationContext,
                        TAG,
                        "enqueue operation SUCCEEDED for $uniqueName"
                    )
                } catch (e: Exception) {
                    TransitTrackerLogger.log(
                        applicationContext,
                        TAG,
                        "enqueue operation FAILED for $uniqueName: ${e.message}"
                    )
                }
            },
            Executor { it.run() }
        )
        observeAutoAlightWork(uniqueName)
        return AutoAlightWorkRequest(uniqueName = uniqueName, workId = workRequest.id)
    }

    private fun observeAutoAlightWork(uniqueName: String) {
        mainHandler.post {
            if (isServiceDestroyed) return@post
            if (autoAlightWorkObservers.containsKey(uniqueName)) return@post

            val liveData = WorkManager.getInstance(applicationContext)
                .getWorkInfosForUniqueWorkLiveData(uniqueName)
            val observer = Observer<List<WorkInfo>> { workInfos ->
                workInfos.forEach { workInfo ->
                    val previousState = autoAlightWorkStates[workInfo.id]
                    if (previousState != workInfo.state) {
                        autoAlightWorkStates[workInfo.id] = workInfo.state
                        TransitTrackerLogger.log(
                            applicationContext,
                            TAG,
                            "AutoAlight worker state changed uniqueName=$uniqueName " +
                                "workId=${workInfo.id} state=${workInfo.state} " +
                                "outputData=${formatWorkData(workInfo.outputData)}"
                        )
                    }
                }
            }
            autoAlightWorkObservers[uniqueName] = AutoAlightWorkObserver(liveData, observer)
            liveData.observeForever(observer)
        }
    }

    private fun logAutoAlightWorkStatus(uniqueName: String, requestedWorkId: UUID) {
        val workManager = WorkManager.getInstance(applicationContext)
        val future = workManager.getWorkInfosForUniqueWork(uniqueName)
        future.addListener(
            {
                try {
                    val workInfos = future.get()
                    val status = workInfos.joinToString(separator = ", ") { workInfo ->
                        "${workInfo.id}:${workInfo.state}:outputData=${formatWorkData(workInfo.outputData)}"
                    }.ifBlank { "no WorkInfo found" }
                    TransitTrackerLogger.log(
                        applicationContext,
                        TAG,
                        "AutoAlight worker status uniqueName=$uniqueName " +
                            "requestedWorkId=$requestedWorkId workInfos=[$status]"
                    )
                } catch (e: Exception) {
                    TransitTrackerLogger.log(
                        applicationContext,
                        TAG,
                        "AutoAlight worker status query failed uniqueName=$uniqueName " +
                            "requestedWorkId=$requestedWorkId error=${e.message}"
                    )
                }
            },
            workStatusExecutor
        )
    }

    private fun removeAutoAlightWorkObservers() {
        val removeObservers = {
            autoAlightWorkObservers.values.forEach { entry ->
                entry.liveData.removeObserver(entry.observer)
            }
            autoAlightWorkObservers.clear()
            autoAlightWorkStates.clear()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            removeObservers()
        } else {
            mainHandler.post { removeObservers() }
        }
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
        proximityTracker.stop()
        reminderScheduler.cancelProximityWatchAlarm()
    }

    /**
     * 30 saniyede bir GPS kontrol eder.
     * İniş durağına ≤500m yaklaşıldığında bildirim gönderir ve döngüyü bitirir.
     * Koşul 3 (GPS alınamaz): 3 ardışık başarısız lokasyon denemesinden sonra
     * TIME fallback devreye girer ve proximity watch durur.
     */
    private fun startProximityWatchInForeground() {
        val reason = proximityTracker.missingPrerequisiteReason(alightingLat, alightingLng)
        if (reason != null) {
            fallbackToTimeReminder(reason)
            return
        }
        if (!startForegroundForCurrentState(useLocationType = true)) {
            fallbackToTimeReminder("location foreground baslatilamadi")
            return
        }
        proximityTracker.start(alightingLat, alightingLng)
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

    private fun handleGpsFallback() {
        Log.w(TAG, "GPS surekli alinamiyor, konum hatirlatmasi durduruldu")
        usingTimeFallback = false
        startForegroundForCurrentState(useLocationType = false)
        if (PrefsManager.transitReminderType == TransitReminderType.TIME) {
            usingTimeFallback = true
            scheduleReminder()
        }
        updateTrackingNotification()
    }

    private fun handleProximityReminder() {
        showReminderNotification()
        cancelReminder()
        startForegroundForCurrentState(useLocationType = false)
    }

    private fun handleAutoAlighting() {
        val segId = segmentIds.getOrNull(currentSegmentIndex)
            .takeIf { !it.isNullOrBlank() }
            ?: currentTripId
        Log.d(TAG, "handleAutoAlighting: currentSegmentIndex=$currentSegmentIndex segmentIds=$segmentIds resolvedSegId=$segId")
        if (segId.isBlank()) {
            logD("Auto Indim iptal: segId bos")
            return
        }
        val workRequest = enqueueTransitActionWorkerWithId(segId, isBoarding = false)
        logD(
            "[TransitTripService] Worker enqueued for segId=$segId " +
                "uniqueName=${workRequest.uniqueName} workId=${workRequest.workId}"
        )
        logAutoAlightWorkStatus(workRequest.uniqueName, workRequest.workId)
        getSystemService(NotificationManager::class.java).cancel(NOTIF_ID_REMINDER)
        cancelReminder()
        cancelProximityWatchAlarm()
        if (currentSegmentIndex >= totalSegments - 1) {
            stopTracking()
        } else {
            updateTrackingNotificationWaiting()
        }
        logD("GPS proximity otomatik Indim kaydi olusturdu: segId=$segId")
    }
}
