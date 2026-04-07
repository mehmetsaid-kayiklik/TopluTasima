package com.example.toplutasima.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.toplutasima.data.PrefsManager
import com.example.toplutasima.model.ThemeMode
import com.example.toplutasima.model.UsageType
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.LocaleManager
import com.example.toplutasima.ui.S
import com.example.toplutasima.ui.components.RmvFooter
import com.example.toplutasima.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean,
    settingsViewModel: SettingsViewModel
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("rmv_prefs", Context.MODE_PRIVATE) }
    val lang = LocaleManager.currentLanguage
    val settingsState by settingsViewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var bgColorHex by remember { mutableStateOf(prefs.getString("bg_color", "") ?: "") }
    var btnColorHex by remember { mutableStateOf(prefs.getString("btn_color", "") ?: "") }

    // Maintenance overlay state
    var showMaintenance by remember { mutableStateOf(false) }

    // Favorite restore state
    var showRestoreDialog by remember { mutableStateOf(false) }
    var restoreRunning by remember { mutableStateOf(false) }
    var restoreResult by remember { mutableStateOf("") }

    fun parsePreviewColor(hex: String): Color {
        return try {
            if (hex.isNotBlank()) Color(android.graphics.Color.parseColor(hex)) else Color.Transparent
        } catch (_: Exception) { Color.Transparent }
    }

    // ── Show MaintenanceScreen overlay ──
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(S.settingsTitle(lang), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        // ── Tema Card — Segmented Control ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(S.themeTitle(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                val themeModes = listOf(
                    ThemeMode.SYSTEM to ("🔄" to S.themeModeSystem(lang)),
                    ThemeMode.LIGHT to ("☀️" to S.themeModeLight(lang)),
                    ThemeMode.DARK to ("🌙" to S.themeModeDark(lang))
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    themeModes.forEach { (mode, emojiAndName) ->
                        val (emoji, name) = emojiAndName
                        val isSelected = PrefsManager.themeMode == mode
                        Surface(
                            onClick = { settingsViewModel.setThemeMode(mode) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                   else MaterialTheme.colorScheme.surface,
                            tonalElevation = if (isSelected) 4.dp else 0.dp,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(emoji, fontSize = 20.sp)
                                Text(
                                    name,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.onSurface
                                )
                                if (isSelected) {
                                    Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Language Card ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(S.languageTitle(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                val languages = listOf(
                    AppLanguage.TR to ("\uD83C\uDDF9\uD83C\uDDF7" to "Türkçe"),
                    AppLanguage.DE to ("\uD83C\uDDE9\uD83C\uDDEA" to "Deutsch"),
                    AppLanguage.EN to ("\uD83C\uDDEC\uD83C\uDDE7" to "English")
                )

                languages.forEach { (langOption, flagAndName) ->
                    val (flag, name) = flagAndName
                    val isSelected = lang == langOption
                    Surface(
                        onClick = { LocaleManager.setLanguage(langOption, prefs) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                               else MaterialTheme.colorScheme.surface,
                        tonalElevation = if (isSelected) 4.dp else 0.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(flag, fontSize = 24.sp)
                            Text(
                                name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurface
                            )
                            if (isSelected) {
                                Spacer(Modifier.weight(1f))
                                Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                        }
                    }
                }
            }
        }

        // ── Favori Duraklar Card ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(S.favoritesTitle(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                val favorites = PrefsManager.favorites
                if (favorites.isEmpty()) {
                    Text(
                        S.favEmpty(lang),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    favorites.forEach { fav ->
                        Surface(
                            onClick = { settingsViewModel.startEditing(fav.id) },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 1.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("⭐", fontSize = 18.sp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(fav.label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text(fav.stopName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                val usageLabel = when (fav.usageType) {
                                    UsageType.BOARDING -> S.favUsageBoarding(lang)
                                    UsageType.ALIGHTING -> S.favUsageAlighting(lang)
                                    UsageType.BOTH -> S.favUsageBoth(lang)
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(usageLabel, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                                }
                                IconButton(
                                    onClick = { settingsViewModel.showDeleteConfirm(fav.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Text("🗑️", fontSize = 16.sp)
                                }
                            }
                        }
                    }
                }

                // ── Favorileri Geri Yükle butonu ──
                if (restoreRunning) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Text(S.favRestoreRunning(lang), style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    if (restoreResult.isNotBlank()) {
                        Text(
                            restoreResult,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (restoreResult.contains("✅")) MaterialTheme.colorScheme.primary
                                   else if (restoreResult.contains("❌")) MaterialTheme.colorScheme.error
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = { showRestoreDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Text(S.favRestoreButton(lang), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // ── Favori Edit Dialog ──
        if (settingsState.editingFavId != null) {
            AlertDialog(
                onDismissRequest = { settingsViewModel.cancelEdit() },
                title = { Text(S.favEditTitle(lang), fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = settingsState.editLabel,
                            onValueChange = { settingsViewModel.updateEditLabel(it) },
                            label = { Text(S.favLabel(lang)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        Text(S.favUsageType(lang), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                UsageType.BOARDING to S.favUsageBoarding(lang),
                                UsageType.ALIGHTING to S.favUsageAlighting(lang),
                                UsageType.BOTH to S.favUsageBoth(lang)
                            ).forEach { (type, label) ->
                                val selected = settingsState.editUsageType == type
                                FilterChip(
                                    selected = selected,
                                    onClick = { settingsViewModel.updateEditUsageType(type) },
                                    label = { Text(label, fontSize = 12.sp) }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { settingsViewModel.saveEdit() }) { Text(S.save(lang)) }
                },
                dismissButton = {
                    TextButton(onClick = { settingsViewModel.cancelEdit() }) { Text(S.cancel(lang)) }
                }
            )
        }

        // ── Favori Delete Confirm Dialog ──
        if (settingsState.showDeleteConfirm != null) {
            AlertDialog(
                onDismissRequest = { settingsViewModel.cancelDelete() },
                title = { Text(S.favDelete(lang), fontWeight = FontWeight.Bold) },
                text = { Text(S.favDeleteConfirm(lang)) },
                confirmButton = {
                    TextButton(onClick = { settingsViewModel.confirmDelete() }) { Text(S.favDelete(lang)) }
                },
                dismissButton = {
                    TextButton(onClick = { settingsViewModel.cancelDelete() }) { Text(S.cancel(lang)) }
                }
            )
        }

        // ── Favori Restore Dialog ──
        if (showRestoreDialog) {
            AlertDialog(
                onDismissRequest = { showRestoreDialog = false },
                title = { Text(S.favRestoreConfirmTitle(lang), fontWeight = FontWeight.Bold) },
                text = { Text(S.favRestoreConfirmText(lang)) },
                confirmButton = {
                    TextButton(onClick = {
                        showRestoreDialog = false
                        restoreRunning = true
                        restoreResult = ""
                        scope.launch {
                            try {
                                val newCount = withContext(Dispatchers.IO) {
                                    PrefsManager.restoreFromFirebase()
                                }
                                restoreResult = if (newCount > 0) S.favRestoreDone(newCount, lang)
                                               else S.favRestoreEmpty(lang)
                            } catch (e: Exception) {
                                val details = e.message?.takeIf { it.isNotBlank() }
                                restoreResult = if (details != null) {
                                    "${S.favRestoreFailed(lang)}: $details"
                                } else {
                                    S.favRestoreFailed(lang)
                                }
                            } finally {
                                restoreRunning = false
                            }
                        }
                    }) { Text(S.yes(lang)) }
                },
                dismissButton = {
                    TextButton(onClick = { showRestoreDialog = false }) { Text(S.cancel(lang)) }
                }
            )
        }

        // ── Renk Ayarları Card ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(S.colorSettings(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(parsePreviewColor(bgColorHex))
                            .then(
                                if (parsePreviewColor(bgColorHex) == Color.Transparent)
                                    Modifier.background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                else Modifier
                            )
                    )
                    OutlinedTextField(
                        value = bgColorHex,
                        onValueChange = { bgColorHex = it },
                        label = { Text(S.bgColorHex(lang)) },
                        placeholder = { Text("#FFFFFF") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(parsePreviewColor(btnColorHex))
                            .then(
                                if (parsePreviewColor(btnColorHex) == Color.Transparent)
                                    Modifier.background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                else Modifier
                            )
                    )
                    OutlinedTextField(
                        value = btnColorHex,
                        onValueChange = { btnColorHex = it },
                        label = { Text(S.btnColorHex(lang)) },
                        placeholder = { Text("#00BCD4") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }

                Button(
                    onClick = {
                        prefs.edit().putString("bg_color", bgColorHex).putString("btn_color", btnColorHex).apply()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    Text(S.saveColors(lang), fontWeight = FontWeight.SemiBold)
                }

                Text(
                    S.restartHint(lang),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // ── Veri Bakımı butonu ──
        OutlinedButton(
            onClick = { showMaintenance = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            Text(S.maintenanceButton(lang), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }

            RmvFooter(modifier = Modifier.padding(vertical = 8.dp))
        }

        if (showMaintenance) {
            Dialog(
                onDismissRequest = { showMaintenance = false },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnClickOutside = false
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MaintenanceScreen(
                        onBack = { showMaintenance = false }
                    )
                }
            }
        }

    }
}