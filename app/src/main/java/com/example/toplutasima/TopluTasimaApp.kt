package com.example.toplutasima

import android.app.Application
import android.content.Context
import com.example.toplutasima.data.OfflineQueueStore
import com.example.toplutasima.data.PrefsManager
import com.example.toplutasima.di.appModule
import com.example.toplutasima.diagnostics.AppErrorReporter
import com.example.toplutasima.ui.LocaleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class TopluTasimaApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        AppErrorReporter.install(this)

        // PrefsManager ve LocaleManager'ı burada başlat.
        // Service / Worker / BroadcastReceiver, MainActivity'den önce çalışabilir;
        // bu yüzden init Application seviyesinde yapılmalı.
        val prefs = getSharedPreferences("rmv_prefs", Context.MODE_PRIVATE)
        LocaleManager.init(prefs)
        PrefsManager.init(prefs, appScope)
        if (OfflineQueueStore.pendingCount(this) > 0) {
            OfflineQueueStore.scheduleSync(this)
        }

        startKoin {
            androidContext(this@TopluTasimaApp)
            modules(appModule)
        }
    }
}
