package com.example.toplutasima

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat
import com.example.toplutasima.ui.*
import com.example.toplutasima.data.PrefsManager
import com.example.toplutasima.model.ThemeMode
import com.example.toplutasima.ui.navigation.MainAppScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT

        setContent {
            val context = LocalContext.current
            val composePrefs = remember { context.getSharedPreferences("rmv_prefs", Context.MODE_PRIVATE) }

            val systemDark = isSystemInDarkTheme()
            val isDarkTheme = when (PrefsManager.themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val currentWindow = (view.context as Activity).window
                    WindowCompat.getInsetsController(currentWindow, view).isAppearanceLightStatusBars = !isDarkTheme
                }
            }

            val bgHex = composePrefs.getString("bg_color", "") ?: ""
            val btnHex = composePrefs.getString("btn_color", "") ?: ""

            fun parseColorHex(hex: String, fallback: Color): Color {
                return try {
                    if (hex.isNotBlank()) Color(android.graphics.Color.parseColor(hex)) else fallback
                } catch (e: Exception) { fallback }
            }

            val modernDarkScheme = darkColorScheme(
                primary = Teal,
                onPrimary = DarkBlue,
                primaryContainer = Color(0xFF004D40),
                onPrimaryContainer = TealLight,
                secondary = AccentBlue,
                onSecondary = DarkBlue,
                secondaryContainer = Color(0xFF0C4A6E),
                onSecondaryContainer = AccentBlue,
                tertiary = Color(0xFFA78BFA),
                background = DarkBlue,
                onBackground = SoftWhite,
                surface = MidBlue,
                onSurface = SoftWhite,
                surfaceVariant = CardDark,
                onSurfaceVariant = Color(0xFFCBD5E1),
                outline = Color(0xFF475569),
                error = ErrorRed,
                onError = Color.White
            )

            val modernLightScheme = lightColorScheme(
                primary = Color(0xFF0D9488),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFCCFBF1),
                onPrimaryContainer = Color(0xFF134E4A),
                secondary = Color(0xFF0284C7),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFE0F2FE),
                onSecondaryContainer = Color(0xFF0C4A6E),
                tertiary = Color(0xFF7C3AED),
                background = LightBg,
                onBackground = DarkText,
                surface = Color.White,
                onSurface = DarkText,
                surfaceVariant = Color(0xFFF1F5F9),
                onSurfaceVariant = Color(0xFF475569),
                outline = Color(0xFFCBD5E1),
                error = ErrorRed,
                onError = Color.White
            )

            val baseScheme = if (PrefsManager.useMaterialYou && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (isDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (isDarkTheme) modernDarkScheme else modernLightScheme
            }

            val dynamicScheme = baseScheme.copy(
                background = parseColorHex(bgHex, baseScheme.background),
                primary = parseColorHex(btnHex, baseScheme.primary)
            )

            MaterialTheme(
                colorScheme = dynamicScheme,
                typography = MaterialTheme.typography.copy(
                    headlineLarge = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                    headlineMedium = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    titleLarge = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    titleMedium = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainAppScreen(isDarkTheme = isDarkTheme)
                }
            }
        }
    }
}
