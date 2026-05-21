package com.example.toplutasima.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.toplutasima.ui.LocaleManager
import com.example.toplutasima.ui.S
import com.example.toplutasima.ui.screens.settings.ColorPersonalSettingsSection
import com.example.toplutasima.ui.screens.settings.DiagnosticsSection
import com.example.toplutasima.ui.screens.settings.FavoriteSettingsSection
import com.example.toplutasima.ui.screens.settings.ProfileBackupSection
import com.example.toplutasima.ui.screens.settings.ThemeLanguageSettingsSection
import com.example.toplutasima.ui.screens.settings.TransitNotificationSettingsSection
import com.example.toplutasima.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean,
    settingsViewModel: SettingsViewModel
) {
    val lang = LocaleManager.currentLanguage
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(S.settingsTitle(lang), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            ThemeLanguageSettingsSection(
                lang = lang,
                settingsViewModel = settingsViewModel
            )
            FavoriteSettingsSection(
                settingsState = settingsState,
                settingsViewModel = settingsViewModel,
                lang = lang
            )
            ColorPersonalSettingsSection(lang = lang)
            TransitNotificationSettingsSection(lang = lang)
            ProfileBackupSection(
                settingsState = settingsState,
                settingsViewModel = settingsViewModel,
                lang = lang
            )
            DiagnosticsSection(lang = lang)
        }
    }
}