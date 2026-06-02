package com.example.toplutasima

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.toplutasima.data.PrefsManager
import com.example.toplutasima.model.ThemeMode
import com.example.toplutasima.ui.navigation.MainAppScreen
import com.example.toplutasima.ui.theme.TopluTasimaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT

        setContent {
            val themeMode = PrefsManager.themeMode
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            val useMaterialYou = PrefsManager.useMaterialYou
            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val currentWindow = (view.context as Activity).window
                    WindowCompat.getInsetsController(
                        currentWindow,
                        view
                    ).isAppearanceLightStatusBars = !darkTheme
                }
            }

            TopluTasimaTheme(darkTheme = darkTheme, dynamicColor = useMaterialYou) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainAppScreen()
                }
            }
        }
    }
}
