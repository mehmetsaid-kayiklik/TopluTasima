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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.window.DialogProperties
import com.example.toplutasima.data.OfflineQueueStore
import com.example.toplutasima.data.PrefsManager
import com.example.toplutasima.diagnostics.AppErrorReporter
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.S
import com.example.toplutasima.ui.components.RmvFooter
import com.example.toplutasima.ui.screens.MaintenanceScreen
import com.example.toplutasima.viewmodel.SettingsViewModel
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.TextButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import com.example.toplutasima.diagnostics.PersonalTripTrackerLogger
import com.example.toplutasima.diagnostics.TransitTrackerLogger
import kotlinx.coroutines.delay
import java.io.File

@Composable
internal fun DiagnosticsSection(
    lang: AppLanguage,
    settingsViewModel: SettingsViewModel
) {
    val context = LocalContext.current
    var showMaintenance by remember { mutableStateOf(false) }
    var showTrackingLogs by remember { mutableStateOf(false) }
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

                OutlinedButton(
                    onClick = { settingsViewModel.runMesafeBackfill() },
                    enabled = !settingsViewModel.isBackfillRunning,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    val progress = settingsViewModel.backfillProgress.ifBlank { "0/0" }
                    Text(
                        if (settingsViewModel.isBackfillRunning) {
                            "rmvMesafeKm Backfill ($progress)"
                        } else {
                            "rmvMesafeKm Backfill"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (settingsViewModel.backfillResultMessage.isNotBlank()) {
                    Text(
                        settingsViewModel.backfillResultMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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

                val trackingLogsButtonText = when (lang) {
                    AppLanguage.TR -> "Takip günlüklerini aç"
                    AppLanguage.DE -> "Tracking-Logs öffnen"
                    else -> "Open tracking logs"
                }
                OutlinedButton(
                    onClick = { showTrackingLogs = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(trackingLogsButtonText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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

        if (showTrackingLogs) {
            TrackingLogsDialog(
                context = context,
                lang = lang,
                onDismiss = { showTrackingLogs = false }
            )
        }

}

@Composable
private fun TrackingLogsDialog(
    context: android.content.Context,
    lang: AppLanguage,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.padding(16.dp).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    val title = when (lang) {
                        AppLanguage.TR -> "Takip Günlükleri"
                        AppLanguage.DE -> "Tracking-Logs"
                        else -> "Tracking Logs"
                    }
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    val closeText = when (lang) {
                        AppLanguage.TR -> "Kapat"
                        AppLanguage.DE -> "Schließen"
                        else -> "Close"
                    }
                    OutlinedButton(onClick = onDismiss) {
                        Text(closeText)
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TrackingLogSection(
                        context = context,
                        lang = lang,
                        title = when (lang) {
                            AppLanguage.TR -> "Toplu Taşıma GPS Takip Günlükleri (Son 2 Gün)"
                            AppLanguage.DE -> "ÖPNV-GPS-Tracking-Logs (Letzte 2 Tage)"
                            else -> "Transit GPS Tracking Logs (Last 2 Days)"
                        },
                        noLogsText = when (lang) {
                            AppLanguage.TR -> "Kayıtlı toplu taşıma takip günlüğü bulunamadı."
                            AppLanguage.DE -> "Keine ÖPNV-Tracking-Logs gefunden."
                            else -> "No transit tracking logs found."
                        },
                        filePrefix = "tracker_log_",
                        getLogFiles = { TransitTrackerLogger.getLogFiles(context) },
                        readLogFile = { TransitTrackerLogger.readLogFile(it) },
                        deleteLogFile = { TransitTrackerLogger.deleteLogFile(it) },
                        dialogTitle = { titleText ->
                            when (lang) {
                                AppLanguage.TR -> "Toplu Taşıma Logu: $titleText"
                                AppLanguage.DE -> "ÖPNV-Log: $titleText"
                                else -> "Transit Log: $titleText"
                            }
                        },
                        subjectPrefix = "Transit GPS Log"
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    TrackingLogSection(
                        context = context,
                        lang = lang,
                        title = when (lang) {
                            AppLanguage.TR -> "Kişisel GPS Mesafe Logları (Son 7 Gün)"
                            AppLanguage.DE -> "Persönliche GPS-Distanz-Logs (Letzte 7 Tage)"
                            else -> "Personal GPS Distance Logs (Last 7 Days)"
                        },
                        noLogsText = when (lang) {
                            AppLanguage.TR -> "Kayıtlı kişisel mesafe logu bulunamadı."
                            AppLanguage.DE -> "Keine persönlichen Distanz-Logs gefunden."
                            else -> "No personal distance logs found."
                        },
                        filePrefix = "personal_trip_log_",
                        getLogFiles = { PersonalTripTrackerLogger.getLogFiles(context) },
                        readLogFile = { PersonalTripTrackerLogger.readLogFile(it) },
                        deleteLogFile = { PersonalTripTrackerLogger.deleteLogFile(it) },
                        dialogTitle = { titleText ->
                            when (lang) {
                                AppLanguage.TR -> "Kişisel Mesafe Logu: $titleText"
                                AppLanguage.DE -> "Persönliches Distanz-Log: $titleText"
                                else -> "Personal Distance Log: $titleText"
                            }
                        },
                        subjectPrefix = "Personal Trip GPS Log"
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackingLogSection(
    context: android.content.Context,
    lang: AppLanguage,
    title: String,
    noLogsText: String,
    filePrefix: String,
    getLogFiles: () -> List<File>,
    readLogFile: (File) -> String,
    deleteLogFile: (File) -> Boolean,
    dialogTitle: (String) -> String,
    subjectPrefix: String
) {
    var logFiles by remember { mutableStateOf(getLogFiles()) }
    var selectedLogFile by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            logFiles = getLogFiles()
            delay(2000L)
        }
    }

    Text(
        title,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold
    )

    if (logFiles.isEmpty()) {
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
                val displayName = file.name.removePrefix(filePrefix).removeSuffix(".txt")
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
                }) {
                    Text(viewBtnText, fontSize = 12.sp)
                }

                val shareBtnText = when (lang) {
                    AppLanguage.TR -> "Paylaş"
                    AppLanguage.DE -> "Teilen"
                    else -> "Share"
                }
                TextButton(onClick = {
                    shareLogFile(context, file, lang, subjectPrefix = subjectPrefix)
                }) {
                    Text(shareBtnText, fontSize = 12.sp)
                }

                val deleteBtnText = when (lang) {
                    AppLanguage.TR -> "Sil"
                    AppLanguage.DE -> "Löschen"
                    else -> "Delete"
                }
                TextButton(onClick = {
                    if (deleteLogFile(file)) {
                        logFiles = getLogFiles()
                    }
                }) {
                    Text(deleteBtnText, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    selectedLogFile?.let { logFile ->
        LogFileContentDialog(
            lang = lang,
            title = dialogTitle(
                logFile.name.removePrefix(filePrefix).removeSuffix(".txt")
            ),
            file = logFile,
            readLogFile = readLogFile,
            onDismiss = { selectedLogFile = null }
        )
    }
}

@Composable
private fun LogFileContentDialog(
    lang: AppLanguage,
    title: String,
    file: File,
    readLogFile: (File) -> String,
    onDismiss: () -> Unit
) {
    var content by remember(file) { mutableStateOf("") }
    val logLines = remember(content) {
        if (content.isBlank()) emptyList() else content.lines()
    }
    val listState = rememberLazyListState()

    LaunchedEffect(file) {
        while (true) {
            content = readLogFile(file)
            delay(2000L)
        }
    }

    LaunchedEffect(logLines.size) {
        if (logLines.isNotEmpty()) {
            listState.animateScrollToItem(logLines.size - 1)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
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
                Text(
                    title,
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
                    if (logLines.isEmpty()) {
                    val scrollState = rememberScrollState()
                    Text(
                        text = content.ifBlank {
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
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(logLines) { line ->
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
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
                    OutlinedButton(onClick = onDismiss) {
                        Text(closeText)
                    }
                }
            }
        }
    }
}

private fun shareLogFile(
    context: android.content.Context,
    file: File,
    lang: AppLanguage,
    subjectPrefix: String = "GPS Proximity Log"
) {
    try {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(android.content.Intent.EXTRA_SUBJECT, "$subjectPrefix: ${file.name}")
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
