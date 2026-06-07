package com.example.toplutasima.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.toplutasima.BuildConfig
import com.example.toplutasima.data.PrefsManager
import com.example.toplutasima.diagnostics.PersonalTripTrackerLogger
import com.example.toplutasima.location.PersonalLocationHelper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Kişisel araç yolculuğu sırasında arka planda GPS takibi yapar.
 *
 * Çalışma prensibi:
 *  • FusedLocationProviderClient ile N saniyede bir GPS noktası (waypoint) toplar.
 *  • Her 4 dakikada bir birikmiş waypoint'leri ORS API'ye gönderir.
 *  • ORS'ten dönen gerçek yol mesafesini kümülatif toplama ekler.
 *  • Notifikasyonu anlık mesafe ile günceller.
 *  • ViewModel bu sınıfın companion object'indeki StateFlow'larını gözlemler.
 */
class PersonalTripForegroundService : Service() {

    companion object {
        private const val TAG = "PersonalTripService"
        private const val CHANNEL_ID = "personal_trip_tracking"
        private const val NOTIF_ID = 9001
        private const val ORS_BATCH_INTERVAL_MS = 4 * 60 * 1000L // 4 dakika

        // ViewModel tarafından gözlemlenen durum akışları
        private val _liveDistanceKm = MutableStateFlow(0.0)
        val liveDistanceKm: StateFlow<Double> = _liveDistanceKm
        val currentDistanceKm: Double get() = _liveDistanceKm.value

        private val _isTracking = MutableStateFlow(false)
        val isTracking: StateFlow<Boolean> = _isTracking

        /** Servisi başlatmak/durdurmak için Intent Action'ları */
        const val ACTION_START = "com.example.toplutasima.personal.START"
        const val ACTION_STOP  = "com.example.toplutasima.personal.STOP"
        const val EXTRA_TRIP_DOC_ID = "com.example.toplutasima.personal.TRIP_DOC_ID"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var locationHelper: PersonalLocationHelper

    /** Birikmiş ama henüz ORS'e gönderilmemiş waypoint'ler */
    private val pendingWaypoints = mutableListOf<Pair<Double, Double>>()
    /** Tamamlanmış ORS batch'lerinden gelen toplam mesafe (metre) */
    private var totalDistanceMeters = 0.0
    private var activeTripDocId = ""
    private var rawWaypointCount = 0
    private var orsBatchSequence = 0
    private var lastRawLocation: Location? = null

    private val fusedClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: run {
                logD("LOCATION_RESULT without lastLocation locations=${result.locations.size}")
                return
            }
            val previous = lastRawLocation
            val deltaMeters = previous?.distanceTo(loc)?.toDouble()
            val ageMs = System.currentTimeMillis() - loc.time
            val count = synchronized(pendingWaypoints) {
                pendingWaypoints.add(loc.latitude to loc.longitude)
                pendingWaypoints.size
            }
            rawWaypointCount += 1
            logD(
                "WAYPOINT rawIndex=$rawWaypointCount pending=$count resultLocations=${result.locations.size} " +
                    "lat=${formatCoord(loc.latitude)} lng=${formatCoord(loc.longitude)} " +
                    "accuracyM=${accuracyText(loc)} speedMps=${speedText(loc)} ageMs=$ageMs " +
                    "deltaFromPrevM=${formatMeters(deltaMeters)} elapsedNanos=${loc.elapsedRealtimeNanos}"
            )
            lastRawLocation = loc
        }
    }

    private var orsBatchJob: Job? = null

    // ── Service Lifecycle ────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        locationHelper = PersonalLocationHelper(this)
        createNotificationChannel()
        PersonalTripTrackerLogger.cleanOldLogs(this, maxDaysToKeep = 7)
        logD("onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                activeTripDocId = intent.getStringExtra(EXTRA_TRIP_DOC_ID).orEmpty()
                logD(
                    "ACTION_START startId=$startId flags=$flags alreadyTracking=${_isTracking.value} " +
                        "jobActive=${orsBatchJob?.isActive == true} pending=${pendingCount()} totalKm=${formatKm(totalDistanceMeters)}"
                )
                startTracking()
            }
            ACTION_STOP  -> {
                val stopDocId = intent.getStringExtra(EXTRA_TRIP_DOC_ID).orEmpty()
                if (stopDocId.isNotBlank()) activeTripDocId = stopDocId
                logD(
                    "ACTION_STOP startId=$startId flags=$flags tracking=${_isTracking.value} " +
                        "jobActive=${orsBatchJob?.isActive == true} pending=${pendingCount()} totalKm=${formatKm(totalDistanceMeters)}"
                )
                stopTracking()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        logD("onDestroy tracking=${_isTracking.value} pending=${pendingCount()} totalKm=${formatKm(totalDistanceMeters)}")
        super.onDestroy()
        stopTracking()
        serviceScope.cancel()
    }

    // ── Takip Başlat / Durdur ────────────────────────────────────────────────

    @Suppress("MissingPermission")
    private fun startTracking() {
        logD(
            "startTracking begin hasPermission=${locationHelper.hasPermission()} existingJobActive=${orsBatchJob?.isActive == true} " +
                "pendingBefore=${pendingCount()} totalBeforeKm=${formatKm(totalDistanceMeters)}"
        )
        if (!locationHelper.hasPermission()) {
            Log.w(TAG, "Konum izni yok, takip başlatılmadı")
            logD("startTracking aborted reason=no_location_permission")
            _isTracking.value = false
            stopSelf()
            return
        }

        logD("Takip başlıyor")
        _isTracking.value = true
        _liveDistanceKm.value = 0.0
        totalDistanceMeters = 0.0
        rawWaypointCount = 0
        orsBatchSequence = 0
        lastRawLocation = null
        synchronized(pendingWaypoints) { pendingWaypoints.clear() }
        logD("tracking state reset pending=0 totalKm=0.000")

        startForeground(NOTIF_ID, buildNotification("0.0 km"))

        val intervalMs = (PrefsManager.waypointIntervalSeconds * 1000L).coerceAtLeast(5_000L)
        logD("requestLocationUpdates intervalMs=$intervalMs minUpdateMs=${intervalMs / 2}")
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, intervalMs
        ).setMinUpdateIntervalMillis(intervalMs / 2).build()

        try {
            fusedClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
            logD("requestLocationUpdates registered")
        } catch (e: SecurityException) {
            Log.e(TAG, "Konum izni kayboldu, takip durduruluyor: ${e.message}")
            logD("requestLocationUpdates failed reason=security_exception message=${e.message}")
            _isTracking.value = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        // 4 dakikada bir ORS'e gönder
        orsBatchJob = serviceScope.launch {
            while (true) {
                delay(ORS_BATCH_INTERVAL_MS)
                sendBatchToOrs(trigger = "periodic")
            }
        }
        logD("ORS batch job started intervalMs=$ORS_BATCH_INTERVAL_MS")
    }

    private fun stopTracking() {
        logD(
            "stopTracking requested tracking=${_isTracking.value} jobActive=${orsBatchJob?.isActive == true} " +
                "pending=${pendingCount()} totalKm=${formatKm(totalDistanceMeters)}"
        )
        orsBatchJob?.cancel()
        orsBatchJob = null
        fusedClient.removeLocationUpdates(locationCallback)
        logD("location updates removed; final batch will run if enough pending waypoints exist")

        // Son batch
        serviceScope.launch {
            sendBatchToOrs(trigger = "stop")
            _isTracking.value = false
            logD("tracking stopped finalTotalKm=${formatKm(totalDistanceMeters)}")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    // ── ORS Batch ─────────────────────────────────────────────────────────────

    private suspend fun sendBatchToOrs(trigger: String = "manual") {
        val batchId = ++orsBatchSequence
        var pendingAtSnapshot = 0
        val batch = synchronized(pendingWaypoints) {
            pendingAtSnapshot = pendingWaypoints.size
            if (pendingWaypoints.size < 2) emptyList() else pendingWaypoints.toList()
        }

        if (batch.size < 2) {
            logD("BATCH#$batchId skip trigger=$trigger reason=not_enough_waypoints pending=$pendingAtSnapshot")
            return
        }

        logD(
            "BATCH#$batchId snapshot trigger=$trigger pending=$pendingAtSnapshot batch=${batch.size} " +
                "rawPathKm=${formatKm(pathDistanceMeters(batch))} totalBeforeKm=${formatKm(totalDistanceMeters)}"
        )

        // GPS drift filtresi: birbirine 30m'den yakın ardışık noktaları ele
        // Bu hem GPS titreşiminden kaynaklanan sahte mesafeyi önler
        // hem de ORS'e giden nokta sayısını azaltır
        val filtered = mutableListOf<Pair<Double, Double>>()
        filtered.add(batch.first())
        logD("BATCH#$batchId filter[0] KEEP first=${formatPoint(batch.first())}")
        for (i in 1 until batch.size) {
            val prev = filtered.last()
            val curr = batch[i]
            val distM = haversineMeters(prev.first, prev.second, curr.first, curr.second)
            if (distM >= 30.0) {   // 30m'den kısa hareketi yoksay
                filtered.add(curr)
                logD("BATCH#$batchId filter[$i] KEEP distFromPrevFilteredM=${formatMeters(distM)} point=${formatPoint(curr)}")
            } else {
                logD("BATCH#$batchId filter[$i] SKIP distFromPrevFilteredM=${formatMeters(distM)} point=${formatPoint(curr)}")
            }
        }

        if (filtered.size < 2) {
            logD("BATCH#$batchId skip trigger=$trigger reason=filtered_not_enough raw=${batch.size} filtered=${filtered.size}")
            return
        }

        logD(
            "BATCH#$batchId ORS request trigger=$trigger raw=${batch.size} filtered=${filtered.size} " +
                "filteredStraightPathKm=${formatKm(pathDistanceMeters(filtered))} first=${formatPoint(filtered.first())} last=${formatPoint(filtered.last())}"
        )
        val meters = locationHelper.fetchRouteDistanceMeters(filtered)
        if (meters != null) {
            val beforeMeters = totalDistanceMeters
            totalDistanceMeters += meters
            val km = totalDistanceMeters / 1000.0
            _liveDistanceKm.value = km
            updateNotification(String.format(Locale.US, "%.1f km", km))
            logD(
                "BATCH#$batchId ORS success addedKm=${formatKm(meters)} " +
                    "totalBeforeKm=${formatKm(beforeMeters)} totalAfterKm=${formatKm(totalDistanceMeters)}"
            )

            synchronized(pendingWaypoints) {
                val lastPoint = batch.last()
                val beforePending = pendingWaypoints.size
                val uniqueBatchPoints = batch.toSet().size
                pendingWaypoints.removeAll(batch.toSet())
                val restoredLastPoint = pendingWaypoints.isEmpty()
                if (restoredLastPoint) pendingWaypoints.add(lastPoint)
                logD(
                    "BATCH#$batchId pending cleanup before=$beforePending removeBatch=${batch.size} " +
                        "uniqueRemove=$uniqueBatchPoints after=${pendingWaypoints.size} restoredLastPoint=$restoredLastPoint"
                )
            }
        } else {
            Log.w(TAG, "ORS yanıt vermedi, bu batch atlandı")
            logD("BATCH#$batchId ORS failed trigger=$trigger raw=${batch.size} filtered=${filtered.size}")
        }
    }

    /** Haversine formülü ile iki koordinat arasındaki mesafeyi metre cinsinden hesaplar. */
    private fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val R = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = Math.sin(dLat / 2).let { it * it } +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLng / 2).let { it * it }
        return R * 2 * Math.asin(Math.sqrt(a))
    }

    private fun logD(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
        PersonalTripTrackerLogger.log(this, TAG, "trip=${activeTripDocId.ifBlank { "-" }} $message")
    }

    private fun pendingCount(): Int = synchronized(pendingWaypoints) { pendingWaypoints.size }

    private fun pathDistanceMeters(points: List<Pair<Double, Double>>): Double {
        var meters = 0.0
        for (i in 0 until points.lastIndex) {
            val a = points[i]
            val b = points[i + 1]
            meters += haversineMeters(a.first, a.second, b.first, b.second)
        }
        return meters
    }

    private fun formatPoint(point: Pair<Double, Double>): String =
        "${formatCoord(point.first)},${formatCoord(point.second)}"

    private fun formatCoord(value: Double): String =
        String.format(Locale.US, "%.6f", value)

    private fun formatMeters(value: Double?): String =
        if (value == null) "n/a" else String.format(Locale.US, "%.1f", value)

    private fun formatKm(meters: Double): String =
        String.format(Locale.US, "%.3f", meters / 1000.0)

    private fun accuracyText(location: Location): String =
        if (location.hasAccuracy()) String.format(Locale.US, "%.1f", location.accuracy) else "n/a"

    private fun speedText(location: Location): String =
        if (location.hasSpeed()) String.format(Locale.US, "%.2f", location.speed) else "n/a"

    // ── Bildirim ─────────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Sürüş Takibi",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Kişisel araç yolculuğu kaydı sırasında gösterilir"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(distanceText: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🚗 Sürüş kaydediliyor")
            .setContentText(distanceText)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()

    private fun updateNotification(distanceText: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIF_ID, buildNotification(distanceText))
    }
}
