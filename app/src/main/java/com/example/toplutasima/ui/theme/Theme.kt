package com.example.toplutasima.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.8.sp
    )
)

val TransitDarkColorScheme = darkColorScheme(
    primary = TransitBlue,
    onPrimary = Color.White,
    primaryContainer = TransitBlueDim,
    onPrimaryContainer = TextPrimary,
    secondary = TransitBlue,
    onSecondary = Color.White,
    secondaryContainer = SurfaceElevated,
    onSecondaryContainer = TextPrimary,
    tertiary = WarningAmber,
    onTertiary = BackgroundDark,
    tertiaryContainer = SurfaceElevated,
    onTertiaryContainer = TextPrimary,
    background = BackgroundDark,
    onBackground = TextPrimary,
    surface = SurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceTint = TransitBlue,
    inverseSurface = TextPrimary,
    inverseOnSurface = BackgroundDark,
    outline = TextMuted,
    outlineVariant = TextMuted.copy(alpha = 0.55f),
    error = DangerRed,
    onError = Color.White,
    errorContainer = DangerRed.copy(alpha = 0.18f),
    onErrorContainer = TextPrimary
)

val TransitLightColorScheme = lightColorScheme(
    primary = TransitBlue,
    onPrimary = Color.White,
    primaryContainer = TransitBlueSoft,
    onPrimaryContainer = Color(0xFF082F5F),
    secondary = TransitBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2ECFF),
    onSecondaryContainer = TextPrimaryLight,
    tertiary = WarningAmber,
    onTertiary = Color(0xFF271500),
    tertiaryContainer = Color(0xFFFFE3C1),
    onTertiaryContainer = Color(0xFF3B2100),
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceCardLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceElevatedLight,
    onSurfaceVariant = TextSecondaryLight,
    surfaceTint = TransitBlue,
    inverseSurface = TextPrimaryLight,
    inverseOnSurface = BackgroundLight,
    outline = BorderLightTone,
    outlineVariant = BorderLightTone.copy(alpha = 0.7f),
    error = DangerRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

@Composable
fun TopluTasimaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> TransitDarkColorScheme
        else -> TransitLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}

@Composable
fun isAppInDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f
