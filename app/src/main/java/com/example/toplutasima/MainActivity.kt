package com.example.toplutasima

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.toplutasima.ui.navigation.MainAppScreen
import com.example.toplutasima.ui.theme.TopluTasimaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT

        setContent {
            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val currentWindow = (view.context as Activity).window
                    WindowCompat.getInsetsController(currentWindow, view).isAppearanceLightStatusBars = false
                }
            }

            TopluTasimaTheme(darkTheme = true, dynamicColor = false) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainAppScreen(isDarkTheme = true)
                }
            }
        }
    }
}
