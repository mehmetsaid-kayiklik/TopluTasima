package com.example.toplutasima.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.toplutasima.R
import com.example.toplutasima.data.AppEventBus
import com.example.toplutasima.network.RmvApiService
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class JourneyMatchForegroundService : Service() {
    companion object {
        const val ACTION_START = "com.example.toplutasima.journeymatch.START"
        const val EXTRA_DATE = "date"
        const val EXTRA_TIME = "time"
        private const val CHANNEL_ID = "journey_match"
        private const val NOTIF_ID = 43_202
        private const val TAG = "JourneyMatchService"

        fun start(context: Context, date: String, time: String) {
            val intent = Intent(context, JourneyMatchForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_DATE, date)
                putExtra(EXTRA_TIME, time)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(this) }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        startForeground(NOTIF_ID, buildNotification("GPS izi aliniyor"))
        val date = intent.getStringExtra(EXTRA_DATE).orEmpty()
        val time = intent.getStringExtra(EXTRA_TIME).orEmpty()
        serviceScope.launch {
            runMatch(date, time)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private suspend fun runMatch(date: String, time: String) {
        if (!hasLocationPermission()) {
            AppEventBus.emit(AppEventBus.Event.JourneyMatchCompleted(emptyList(), "Konum izni yok"))
            return
        }
        val points = mutableListOf<Pair<Double, Double>>()
        repeat(4) { index ->
            getCurrentLocationSuspend()?.let { point ->
                if (points.none { distanceMeters(it, point) < 20.0 }) points += point
            }
            if (index < 3) delay(8_000L)
        }
        if (points.size < 2) {
            AppEventBus.emit(AppEventBus.Event.JourneyMatchCompleted(emptyList(), "Yeterli GPS noktasi alinamadi"))
            return
        }
        val candidates = RmvApiService.matchJourneyTrack(points, date, time)
        val message = if (candidates.isEmpty()) {
            "GPS eslesmesi bulunamadi veya servis desteklenmiyor"
        } else {
            "${candidates.size} olasi sefer bulundu"
        }
        AppEventBus.emit(AppEventBus.Event.JourneyMatchCompleted(candidates, message))
    }

    @Suppress("MissingPermission")
    private suspend fun getCurrentLocationSuspend(): Pair<Double, Double>? {
        if (!hasLocationPermission()) return null
        val tokenSource = CancellationTokenSource()
        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { tokenSource.cancel() }
            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, tokenSource.token)
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        cont.resume(loc.latitude to loc.longitude)
                    } else {
                        fusedClient.lastLocation
                            .addOnSuccessListener { last -> cont.resume(last?.let { it.latitude to it.longitude }) }
                            .addOnFailureListener { cont.resume(null) }
                    }
                }
                .addOnFailureListener {
                    Log.w(TAG, "GPS failed: ${it.message}")
                    cont.resume(null)
                }
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun distanceMeters(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(b.first - a.first)
        val dLon = Math.toRadians(b.second - a.second)
        val lat1 = Math.toRadians(a.first)
        val lat2 = Math.toRadians(b.first)
        val h = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(lat1) * kotlin.math.cos(lat2) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        return r * 2 * kotlin.math.asin(kotlin.math.sqrt(h))
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "GPS sefer eslestirme", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun buildNotification(text: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Sefer eslestiriliyor")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
}
