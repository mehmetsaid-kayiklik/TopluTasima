package com.example.toplutasima.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.toplutasima.data.PrefsManager
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

        private val _isTracking = MutableStateFlow(false)
        val isTracking: StateFlow<Boolean> = _isTracking

        /** Servisi başlatmak/durdurmak için Intent Action'ları */
        const val ACTION_START = "com.example.toplutasima.personal.START"
        const val ACTION_STOP  = "com.example.toplutasima.personal.STOP"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var locationHelper: PersonalLocationHelper

    /** Birikmiş ama henüz ORS'e gönderilmemiş waypoint'ler */
    private val pendingWaypoints = mutableListOf<Pair<Double, Double>>()
    /** Tamamlanmış ORS batch'lerinden gelen toplam mesafe (metre) */
    private var totalDistanceMeters = 0.0

    private val fusedClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            pendingWaypoints.add(loc.latitude to loc.longitude)
            Log.d(TAG, "Waypoint #${pendingWaypoints.size}: ${loc.latitude}, ${loc.longitude}")
        }
    }

    private var orsBatchJob: Job? = null

    // ── Service Lifecycle ────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        locationHelper = PersonalLocationHelper(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTracking()
            ACTION_STOP  -> stopTracking()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopTracking()
        serviceScope.cancel()
    }

    // ── Takip Başlat / Durdur ────────────────────────────────────────────────

    @Suppress("MissingPermission")
    private fun startTracking() {
        Log.d(TAG, "Takip başlıyor")
        _isTracking.value = true
        _liveDistanceKm.value = 0.0
        totalDistanceMeters = 0.0
        pendingWaypoints.clear()

        startForeground(NOTIF_ID, buildNotification("0.0 km"))

        val intervalMs = (PrefsManager.waypointIntervalSeconds * 1000L).coerceAtLeast(5_000L)
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, intervalMs
        ).setMinUpdateIntervalMillis(intervalMs / 2).build()

        fusedClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)

        // 4 dakikada bir ORS'e gönder
        orsBatchJob = serviceScope.launch {
            while (true) {
                delay(ORS_BATCH_INTERVAL_MS)
                sendBatchToOrs()
            }
        }
    }

    private fun stopTracking() {
        Log.d(TAG, "Takip durdu — toplam: ${totalDistanceMeters / 1000.0} km")
        orsBatchJob?.cancel()
        orsBatchJob = null
        fusedClient.removeLocationUpdates(locationCallback)

        // Son batch
        serviceScope.launch {
            sendBatchToOrs()
            _isTracking.value = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    // ── ORS Batch ─────────────────────────────────────────────────────────────

    private suspend fun sendBatchToOrs() {
        val batch = synchronized(pendingWaypoints) {
            if (pendingWaypoints.size < 2) return
            val copy = pendingWaypoints.toList()
            // Son noktayı tutarak sürekliliği sağla
            val lastPoint = pendingWaypoints.last()
            pendingWaypoints.clear()
            pendingWaypoints.add(lastPoint)
            copy
        }

        Log.d(TAG, "ORS gönderiliyor: ${batch.size} waypoint")
        val meters = locationHelper.fetchRouteDistanceMeters(batch)
        if (meters != null) {
            totalDistanceMeters += meters
            val km = totalDistanceMeters / 1000.0
            _liveDistanceKm.value = km
            updateNotification(String.format("%.1f km", km))
            Log.d(TAG, "ORS mesafesi eklendi: +${meters/1000.0} km, toplam: $km km")
        } else {
            Log.w(TAG, "ORS yanıt vermedi, bu batch atlandı")
        }
    }

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
