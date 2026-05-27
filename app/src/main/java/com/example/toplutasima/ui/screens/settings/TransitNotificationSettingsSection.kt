package com.example.toplutasima.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toplutasima.data.PrefsManager
import com.example.toplutasima.data.TransitAutoActualTimeMode
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.S

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
                                    label = {
                                        Text(
                                            S.transitReminderOption(minutes, lang),
                                            fontSize = 11.sp,
                                            lineHeight = 14.sp,
                                            maxLines = 2,
                                            textAlign = TextAlign.Center
                                        )
                                    },
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
