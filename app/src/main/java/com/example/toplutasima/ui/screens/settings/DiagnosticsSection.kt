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
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.TextButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.toplutasima.diagnostics.TransitTrackerLogger
import java.io.File

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

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                val trackingLogsTitle = when (lang) {
                    AppLanguage.TR -> "GPS Takip Günlükleri (Son 2 Gün)"
                    AppLanguage.DE -> "GPS-Tracking-Logs (Letzte 2 Tage)"
                    else -> "GPS Tracking Logs (Last 2 Days)"
                }
                Text(
                    trackingLogsTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )

                var logFiles by remember { mutableStateOf(TransitTrackerLogger.getLogFiles(context)) }
                var selectedLogFile by remember { mutableStateOf<File?>(null) }
                var selectedLogContent by remember { mutableStateOf("") }

                if (logFiles.isEmpty()) {
                    val noLogsText = when (lang) {
                        AppLanguage.TR -> "Kayıtlı takip günlüğü bulunamadı."
                        AppLanguage.DE -> "Keine Tracking-Logs gefunden."
                        else -> "No tracking logs found."
                    }
                    Text(
                        noLogsText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    logFiles.forEach { file ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            val displayName = file.name.removePrefix("tracker_log_").removeSuffix(".txt")
                            Text(
                                displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            
                            val viewBtnText = when (lang) {
                                AppLanguage.TR -> "Görüntüle"
                                AppLanguage.DE -> "Ansehen"
                                else -> "View"
                            }
                            TextButton(onClick = {
                                selectedLogFile = file
                                selectedLogContent = TransitTrackerLogger.readLogFile(file)
                            }) {
                                Text(viewBtnText, fontSize = 12.sp)
                            }

                            val shareBtnText = when (lang) {
                                AppLanguage.TR -> "Paylaş"
                                AppLanguage.DE -> "Teilen"
                                else -> "Share"
                            }
                            TextButton(onClick = {
                                shareLogFile(context, file, lang)
                            }) {
                                Text(shareBtnText, fontSize = 12.sp)
                            }
                            
                            val deleteBtnText = when (lang) {
                                AppLanguage.TR -> "Sil"
                                AppLanguage.DE -> "Löschen"
                                else -> "Delete"
                            }
                            TextButton(onClick = {
                                if (TransitTrackerLogger.deleteLogFile(file)) {
                                    logFiles = TransitTrackerLogger.getLogFiles(context)
                                }
                            }) {
                                Text(deleteBtnText, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                if (selectedLogFile != null) {
                    Dialog(
                        onDismissRequest = { selectedLogFile = null },
                        properties = DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp).fillMaxSize()
                            ) {
                                val titleText = selectedLogFile?.name?.removePrefix("tracker_log_")?.removeSuffix(".txt").orEmpty()
                                val header = when (lang) {
                                    AppLanguage.TR -> "Takip Günlüğü: $titleText"
                                    AppLanguage.DE -> "Tracking-Log: $titleText"
                                    else -> "Tracking Log: $titleText"
                                }
                                Text(
                                    header,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp))
                                        .padding(8.dp)
                                ) {
                                    val scrollState = rememberScrollState()
                                    Text(
                                        text = selectedLogContent.ifBlank {
                                            when (lang) {
                                                AppLanguage.TR -> "Dosya boş."
                                                AppLanguage.DE -> "Datei ist leer."
                                                else -> "File is empty."
                                            }
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(scrollState)
                                    )
                                }
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    val closeText = when (lang) {
                                        AppLanguage.TR -> "Kapat"
                                        AppLanguage.DE -> "Schließen"
                                        else -> "Close"
                                    }
                                    OutlinedButton(onClick = { selectedLogFile = null }) {
                                        Text(closeText)
                                    }
                                }
                            }
                        }
                    }
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

private fun shareLogFile(context: android.content.Context, file: File, lang: AppLanguage) {
    try {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(android.content.Intent.EXTRA_SUBJECT, "GPS Proximity Log: ${file.name}")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        val chooserTitle = when (lang) {
            AppLanguage.TR -> "Log Dosyasını Paylaş"
            AppLanguage.DE -> "Log-Datei teilen"
            else -> "Share Log File"
        }
        
        val chooser = android.content.Intent.createChooser(shareIntent, chooserTitle).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (e: Exception) {
        android.util.Log.e("DiagnosticsSection", "Log dosyası paylaşılamadı: ${e.message}", e)
    }
}
