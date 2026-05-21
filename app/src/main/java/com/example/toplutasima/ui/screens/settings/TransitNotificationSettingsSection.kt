package com.example.toplutasima.ui.screens.settings

import android.content.Context
import android.text.InputType
import android.widget.EditText
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.toplutasima.data.OfflineQueueStore
import com.example.toplutasima.data.PrefsManager
import com.example.toplutasima.data.TransitAutoActualTimeMode
import com.example.toplutasima.data.backup.ProfileBackupManager
import com.example.toplutasima.data.local.entity.ProfileEntity
import com.example.toplutasima.diagnostics.AppErrorReporter
import com.example.toplutasima.model.ThemeMode
import com.example.toplutasima.model.UsageType
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.LocaleManager
import com.example.toplutasima.ui.S
import com.example.toplutasima.ui.components.RmvFooter
import com.example.toplutasima.ui.screens.MaintenanceScreen
import com.example.toplutasima.ui.util.parsePreviewColor
import com.example.toplutasima.viewmodel.SettingsUiState
import com.example.toplutasima.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Arrays
@Composable
internal fun TransitNotificationSettingsSection(
    lang: AppLanguage
) {
        // ── Toplu Taşıma Bildirim Ayarları Card ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(S.transitNotifSettingsTitle(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                // Toggle: bildirimler açık/kapalı
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(S.transitNotifEnabled(lang), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(S.transitNotifEnabledDesc(lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = PrefsManager.transitNotificationsEnabled,
                        onCheckedChange = { PrefsManager.changeTransitNotifications(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }

                if (PrefsManager.transitNotificationsEnabled) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    // ── Hatırlatma Türü ──
                    Text(S.transitReminderTypeTitle(lang), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)

                    val reminderTypes = listOf(
                        com.example.toplutasima.data.TransitReminderType.LOCATION to S.transitReminderTypeLocation(lang),
                        com.example.toplutasima.data.TransitReminderType.TIME     to S.transitReminderTypeTime(lang),
                        com.example.toplutasima.data.TransitReminderType.NONE     to S.transitReminderTypeNone(lang)
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        reminderTypes.forEach { (type, label) ->
                            val selected = PrefsManager.transitReminderType == type
                            FilterChip(
                                selected = selected,
                                onClick = { PrefsManager.changeTransitReminderType(type) },
                                label = { Text(label, fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Tür açıklaması
                    val typeDesc = when (PrefsManager.transitReminderType) {
                        com.example.toplutasima.data.TransitReminderType.LOCATION ->
                            S.transitReminderTypeLocationDesc(lang)
                        com.example.toplutasima.data.TransitReminderType.TIME ->
                            S.transitReminderTypeTimeDesc(lang)
                        com.example.toplutasima.data.TransitReminderType.NONE -> ""
                    }
                    if (typeDesc.isNotBlank()) {
                        Text(typeDesc, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    // ── Hatırlatma Zamanı (yalnızca TIME seçiliyken) ──
                    if (PrefsManager.transitReminderType == com.example.toplutasima.data.TransitReminderType.TIME) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        Text(S.transitReminderTitle(lang), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val offsetOptions = listOf(-2, -1, 0, 1, 2)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            offsetOptions.forEach { minutes ->
                                val selected = PrefsManager.reminderOffsetMinutes == minutes
                                FilterChip(
                                    selected = selected,
                                    onClick = { PrefsManager.changeReminderOffset(minutes) },
                                    label = { Text(S.transitReminderOption(minutes, lang), fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(S.gpsJourneyMatchTitle(lang), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(S.gpsJourneyMatchDesc(lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = PrefsManager.gpsJourneyMatchEnabled,
                        onCheckedChange = { PrefsManager.changeGpsJourneyMatchEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                Text(S.autoActualTimeTitle(lang), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(S.autoActualTimeDesc(lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                val autoActualOptions = listOf(
                    TransitAutoActualTimeMode.OFF to S.autoActualOff(lang),
                    TransitAutoActualTimeMode.CONFIRM to S.autoActualConfirm(lang),
                    TransitAutoActualTimeMode.AUTO to S.autoActualAuto(lang)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    autoActualOptions.forEach { (mode, label) ->
                        val selected = PrefsManager.transitAutoActualTimeMode == mode
                        FilterChip(
                            selected = selected,
                            onClick = { PrefsManager.changeTransitAutoActualTimeMode(mode) },
                            label = { Text(label, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Text(S.autoActualModeDesc(lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

}