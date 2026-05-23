package com.example.toplutasima.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.window.DialogProperties
import com.example.toplutasima.data.OfflineQueueStore
import com.example.toplutasima.data.PrefsManager
import com.example.toplutasima.diagnostics.AppErrorReporter
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.S
import com.example.toplutasima.ui.components.RmvFooter
import com.example.toplutasima.ui.screens.MaintenanceScreen

@Composable
internal fun DiagnosticsSection(
    lang: AppLanguage
) {
    val context = LocalContext.current
    var showMaintenance by remember { mutableStateOf(false) }
    var pendingQueueCount by remember { mutableStateOf(OfflineQueueStore.pendingCount(context)) }
    var stopCacheCount by remember { mutableStateOf(PrefsManager.stopSearchCacheSize()) }
    var lastCrashReport by remember { mutableStateOf(AppErrorReporter.lastCrash(context)) }
    var lastNonFatalReport by remember { mutableStateOf(AppErrorReporter.lastNonFatal(context)) }
        Text(
            S.settingsSectionAdvanced(lang),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { showMaintenance = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    Text(S.maintenanceButton(lang), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                Text(S.diagnosticsTitle(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                Text(
                    S.offlineQueueStatus(pendingQueueCount, lang),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            OfflineQueueStore.scheduleSync(context)
                            pendingQueueCount = OfflineQueueStore.pendingCount(context)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text(S.offlineSyncNow(lang), fontSize = 12.sp) }
                    OutlinedButton(
                        onClick = {
                            OfflineQueueStore.clear(context)
                            pendingQueueCount = 0
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text(S.offlineQueueClear(lang), fontSize = 12.sp) }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                Text(
                    S.stopCacheStatus(stopCacheCount, lang),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = {
                        PrefsManager.clearStopSearchCache()
                        stopCacheCount = 0
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(S.stopCacheClear(lang), fontSize = 12.sp) }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                val reportPreview = lastCrashReport.ifBlank { lastNonFatalReport }
                Text(
                    if (reportPreview.isBlank()) S.noCrashReport(lang) else S.lastCrashReport(lang),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (reportPreview.isNotBlank()) {
                    Text(
                        reportPreview.lines().take(5).joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            AppErrorReporter.clear(context)
                            lastCrashReport = ""
                            lastNonFatalReport = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text(S.clearCrashReport(lang), fontSize = 12.sp) }
                }
            }
        }

            RmvFooter(modifier = Modifier.padding(vertical = 8.dp))

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
