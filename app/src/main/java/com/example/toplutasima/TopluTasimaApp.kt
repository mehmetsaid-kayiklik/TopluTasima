package com.example.toplutasima

import android.app.Application
import android.content.Context
import com.example.toplutasima.data.PrefsManager
import com.example.toplutasima.di.appModule
import com.example.toplutasima.ui.LocaleManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class TopluTasimaApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // PrefsManager ve LocaleManager'ı burada başlat.
        // Service / Worker / BroadcastReceiver, MainActivity'den önce çalışabilir;
        // bu yüzden init Application seviyesinde yapılmalı.
        val prefs = getSharedPreferences("rmv_prefs", Context.MODE_PRIVATE)
        LocaleManager.init(prefs)
        PrefsManager.init(prefs)

        startKoin {
            androidContext(this@TopluTasimaApp)
            modules(appModule)
        }
    }
}
