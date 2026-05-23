package com.example.toplutasima.ui.screens.settings

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import com.example.toplutasima.data.PrefsManager
import com.example.toplutasima.model.UsageType
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.S
import com.example.toplutasima.viewmodel.SettingsUiState
import com.example.toplutasima.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun FavoriteSettingsSection(
    settingsState: SettingsUiState,
    settingsViewModel: SettingsViewModel,
    lang: AppLanguage
) {
    val scope = rememberCoroutineScope()
    var showRestoreDialog by remember { mutableStateOf(false) }
    var restoreRunning by remember { mutableStateOf(false) }
    var restoreResult by remember { mutableStateOf("") }
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

}