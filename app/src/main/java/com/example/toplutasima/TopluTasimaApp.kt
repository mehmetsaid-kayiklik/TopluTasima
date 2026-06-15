package com.example.toplutasima

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.toplutasima.data.OfflineQueueStore
import com.example.toplutasima.data.PrefsManager
import com.example.toplutasima.di.appModule
import com.example.toplutasima.diagnostics.AppErrorReporter
import com.example.toplutasima.diagnostics.TransitTrackerLogger
import com.example.toplutasima.ui.LocaleManager
import com.example.toplutasima.worker.PeriodicSyncWorker
import com.example.toplutasima.worker.TopluTasimaWorkerFactory
import com.example.toplutasima.worker.TransitActionWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import java.util.concurrent.TimeUnit

class TopluTasimaApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val loggedTransitWorkerTerminalStates = mutableSetOf<String>()

    val database by lazy { com.example.toplutasima.data.local.AppDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        AppErrorReporter.install(this)

        // PrefsManager ve LocaleManager'ı burada başlat.
        // Service / Worker / BroadcastReceiver, MainActivity'den önce çalışabilir;
        // bu yüzden init Application seviyesinde yapılmalı.
        val prefs = getSharedPreferences("rmv_prefs", Context.MODE_PRIVATE)
        LocaleManager.init(prefs)
        PrefsManager.init(prefs, appScope)

        val koinApplication = startKoin {
            androidContext(this@TopluTasimaApp)
            modules(appModule)
        }

        val workerFactory: TopluTasimaWorkerFactory = koinApplication.koin.get()
        val workerFactoryClassName = workerFactory::class.java.name
        val workerFactoryMessage =
            "WorkManager initialized with factory: $workerFactoryClassName; " +
                "expected=com.example.toplutasima.worker.TopluTasimaWorkerFactory"

        TransitTrackerLogger.log(this, "WorkerFactory", "About to initialize WorkManager with factory: ${workerFactory::class.java.name}")
        WorkManager.initialize(
            this,
            Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .build()
        )
        Log.d("WorkerFactory", workerFactoryMessage)
        TransitTrackerLogger.log(this, "WorkerFactory", workerFactoryMessage)
        observeTransitActionWorkerTerminalStates()

        if (OfflineQueueStore.pendingCount(this) > 0) {
            OfflineQueueStore.scheduleSync(this)
        }

        val syncRequest = PeriodicWorkRequestBuilder<PeriodicSyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "PeriodicFirestoreSync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }

    private fun observeTransitActionWorkerTerminalStates() {
        WorkManager.getInstance(this)
            .getWorkInfosByTagLiveData(TransitActionWorker.TAG)
            .observeForever { workInfoList ->
                workInfoList?.forEach { info ->
                    if (info.state == WorkInfo.State.FAILED || info.state == WorkInfo.State.CANCELLED) {
                        val logKey = "${info.id}:${info.state}"
                        if (loggedTransitWorkerTerminalStates.add(logKey)) {
                            TransitTrackerLogger.log(
                                this,
                                "WorkManagerObserver",
                                "Worker ${info.state}: id=${info.id} tags=${info.tags}"
                            )
                        }
                    }
                }
            }
    }
}
