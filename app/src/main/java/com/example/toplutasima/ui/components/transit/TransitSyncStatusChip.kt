package com.example.toplutasima.ui.components.transit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.toplutasima.transit.TransitFeatureFlags
import com.example.toplutasima.transit.sync.TransitSyncPhase
import com.example.toplutasima.transit.sync.TransitSyncState
import com.example.toplutasima.transit.sync.TransitSyncStatusStore
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.LocaleManager
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TransitSyncStatusChip(
    recordId: String,
    modifier: Modifier = Modifier,
    store: TransitSyncStatusStore = TransitSyncStatusStore.get(LocalContext.current)
) {
    if (!TransitFeatureFlags.SYNC_RECEIPTS || recordId.isBlank()) return
    val state by store.observeRecord(recordId).collectAsStateWithLifecycle(initialValue = null)
    state?.let { TransitSyncStatusChip(state = it, modifier = modifier) }
}

@Composable
fun TransitSyncReceipt(
    recordIds: Collection<String>,
    modifier: Modifier = Modifier,
    store: TransitSyncStatusStore = TransitSyncStatusStore.get(LocalContext.current)
) {
    if (!TransitFeatureFlags.SYNC_RECEIPTS || recordIds.isEmpty()) return
    val states by store.observeRecords(recordIds).collectAsStateWithLifecycle(initialValue = emptyList())
    val mostImportant = states.minByOrNull { it.phase.displayPriority }
    mostImportant?.let { TransitSyncStatusChip(state = it, modifier = modifier) }
}

@Composable
fun TransitSyncStatusChip(
    state: TransitSyncState,
    modifier: Modifier = Modifier,
    lang: AppLanguage = LocaleManager.currentLanguage
) {
    val presentation = state.phase.presentation(lang)
    var showDetails by remember(state.recordId, state.updatedAtEpochMillis) { mutableStateOf(false) }
    val accessibilityText = buildString {
        append(presentation.accessibilityLabel)
        append(". ")
        append(updatedLabel(lang, state.updatedAtEpochMillis))
        state.detail?.takeIf { it.isNotBlank() }?.let {
            append(". ")
            append(it)
        }
    }

    Surface(
        modifier = modifier
            .semantics { contentDescription = accessibilityText }
            .clickable { showDetails = true },
        color = presentation.containerColor,
        contentColor = presentation.contentColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = presentation.icon,
                contentDescription = null,
                modifier = Modifier.width(14.dp)
            )
            Text(
                text = presentation.label,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }

    if (showDetails) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            title = { Text(syncDetailsTitle(lang)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(presentation.accessibilityLabel)
                    Text(updatedLabel(lang, state.updatedAtEpochMillis))
                    if (state.attemptCount > 0) {
                        Text(attemptLabel(lang, state.attemptCount))
                    }
                    state.detail?.takeIf { it.isNotBlank() }?.let { Text(it) }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetails = false }) {
                    Text(closeLabel(lang))
                }
            }
        )
    }
}

private data class SyncPresentation(
    val label: String,
    val accessibilityLabel: String,
    val icon: ImageVector,
    val contentColor: Color,
    val containerColor: Color
)

private fun TransitSyncPhase.presentation(lang: AppLanguage): SyncPresentation {
    val green = Color(0xFF1B6E3C)
    val greenBg = Color(0xFFE2F4E8)
    val blue = Color(0xFF2056A8)
    val blueBg = Color(0xFFE4EDFC)
    val amber = Color(0xFF805500)
    val amberBg = Color(0xFFFFEFC5)
    val red = Color(0xFF9B1C1C)
    val redBg = Color(0xFFFFE2E2)
    val labels = when (lang) {
        AppLanguage.TR -> when (this) {
            TransitSyncPhase.LOCAL_SAVING -> "Yerelde kaydediliyor" to "Transit kaydı yerel depoya kaydediliyor"
            TransitSyncPhase.LOCAL_SAFE -> "Yerelde güvende" to "Transit kaydı yerel depoda güvende"
            TransitSyncPhase.PENDING -> "Senkron bekliyor" to "Transit kaydı bulut senkronizasyonunu bekliyor"
            TransitSyncPhase.SYNCING -> "Senkronlanıyor" to "Transit kaydı bulutla senkronlanıyor"
            TransitSyncPhase.SYNCED -> "Bulutla eşitlendi" to "Transit kaydı bulutla senkronlandı"
            TransitSyncPhase.TEMPORARY_ERROR -> "Geçici senkron hatası" to "Transit kaydında geçici senkronizasyon hatası var; yeniden denenecek"
            TransitSyncPhase.PERMANENT_ERROR -> "Müdahale gerekiyor" to "Transit kaydı senkronizasyon için kullanıcı müdahalesi gerektiriyor"
            TransitSyncPhase.LOCAL_DELETED -> "Yerelde silindi" to "Transit kaydı bu cihazdan silindi"
            TransitSyncPhase.DELETE_PENDING -> "Silme bekliyor" to "Transit kaydının buluttan silinmesi bekliyor"
            TransitSyncPhase.DELETING -> "Siliniyor" to "Transit kaydı buluttan siliniyor"
            TransitSyncPhase.DELETED -> "Buluttan silindi" to "Transit kaydı buluttan silindi"
            TransitSyncPhase.DELETE_TEMPORARY_ERROR -> "Geçici silme hatası" to "Silme işleminde geçici hata var; yeniden denenecek"
            TransitSyncPhase.DELETE_PERMANENT_ERROR -> "Silme için müdahale" to "Silme işlemi kullanıcı müdahalesi gerektiriyor"
        }
        AppLanguage.DE -> when (this) {
            TransitSyncPhase.LOCAL_SAVING -> "Wird lokal gespeichert" to "Der ÖPNV-Eintrag wird lokal gespeichert"
            TransitSyncPhase.LOCAL_SAFE -> "Lokal gesichert" to "Der ÖPNV-Eintrag ist lokal gesichert"
            TransitSyncPhase.PENDING -> "Synchronisierung ausstehend" to "Der ÖPNV-Eintrag wartet auf die Cloud-Synchronisierung"
            TransitSyncPhase.SYNCING -> "Wird synchronisiert" to "Der ÖPNV-Eintrag wird mit der Cloud synchronisiert"
            TransitSyncPhase.SYNCED -> "Cloud synchronisiert" to "Der ÖPNV-Eintrag wurde mit der Cloud synchronisiert"
            TransitSyncPhase.TEMPORARY_ERROR -> "Temporärer Sync-Fehler" to "Temporärer Synchronisierungsfehler; ein neuer Versuch folgt"
            TransitSyncPhase.PERMANENT_ERROR -> "Aktion erforderlich" to "Für die Synchronisierung ist eine Aktion erforderlich"
            TransitSyncPhase.LOCAL_DELETED -> "Lokal gelöscht" to "Der ÖPNV-Eintrag wurde lokal gelöscht"
            TransitSyncPhase.DELETE_PENDING -> "Löschung ausstehend" to "Die Cloud-Löschung des ÖPNV-Eintrags steht aus"
            TransitSyncPhase.DELETING -> "Wird gelöscht" to "Der ÖPNV-Eintrag wird aus der Cloud gelöscht"
            TransitSyncPhase.DELETED -> "Aus Cloud gelöscht" to "Der ÖPNV-Eintrag wurde aus der Cloud gelöscht"
            TransitSyncPhase.DELETE_TEMPORARY_ERROR -> "Temporärer Löschfehler" to "Temporärer Löschfehler; ein neuer Versuch folgt"
            TransitSyncPhase.DELETE_PERMANENT_ERROR -> "Löschaktion erforderlich" to "Für die Löschung ist eine Aktion erforderlich"
        }
        AppLanguage.EN -> when (this) {
            TransitSyncPhase.LOCAL_SAVING -> "Saving locally" to "The transit record is being saved locally"
            TransitSyncPhase.LOCAL_SAFE -> "Safe on device" to "The transit record is safe on this device"
            TransitSyncPhase.PENDING -> "Sync pending" to "The transit record is waiting for cloud sync"
            TransitSyncPhase.SYNCING -> "Syncing" to "The transit record is syncing with the cloud"
            TransitSyncPhase.SYNCED -> "Cloud synced" to "The transit record is synced with the cloud"
            TransitSyncPhase.TEMPORARY_ERROR -> "Temporary sync error" to "The transit record has a temporary sync error and will retry"
            TransitSyncPhase.PERMANENT_ERROR -> "Action required" to "The transit record needs user action to synchronize"
            TransitSyncPhase.LOCAL_DELETED -> "Deleted locally" to "The transit record was deleted from this device"
            TransitSyncPhase.DELETE_PENDING -> "Delete pending" to "The transit record is waiting to be deleted from the cloud"
            TransitSyncPhase.DELETING -> "Deleting" to "The transit record is being deleted from the cloud"
            TransitSyncPhase.DELETED -> "Deleted from cloud" to "The transit record was deleted from the cloud"
            TransitSyncPhase.DELETE_TEMPORARY_ERROR -> "Temporary delete error" to "The delete has a temporary error and will retry"
            TransitSyncPhase.DELETE_PERMANENT_ERROR -> "Delete action required" to "The delete needs user action"
        }
    }
    val (icon, foreground, background) = when (this) {
        TransitSyncPhase.LOCAL_SAVING -> Triple(Icons.Outlined.Save, blue, blueBg)
        TransitSyncPhase.LOCAL_SAFE -> Triple(Icons.Outlined.CheckCircle, green, greenBg)
        TransitSyncPhase.PENDING -> Triple(Icons.Outlined.CloudOff, amber, amberBg)
        TransitSyncPhase.SYNCING -> Triple(Icons.Outlined.CloudSync, blue, blueBg)
        TransitSyncPhase.SYNCED -> Triple(Icons.Outlined.CloudDone, green, greenBg)
        TransitSyncPhase.TEMPORARY_ERROR -> Triple(Icons.Outlined.CloudOff, amber, amberBg)
        TransitSyncPhase.PERMANENT_ERROR -> Triple(Icons.Outlined.ErrorOutline, red, redBg)
        TransitSyncPhase.LOCAL_DELETED -> Triple(Icons.Outlined.Delete, amber, amberBg)
        TransitSyncPhase.DELETE_PENDING -> Triple(Icons.Outlined.CloudOff, amber, amberBg)
        TransitSyncPhase.DELETING -> Triple(Icons.Outlined.CloudSync, blue, blueBg)
        TransitSyncPhase.DELETED -> Triple(Icons.Outlined.CloudDone, green, greenBg)
        TransitSyncPhase.DELETE_TEMPORARY_ERROR -> Triple(Icons.Outlined.CloudOff, amber, amberBg)
        TransitSyncPhase.DELETE_PERMANENT_ERROR -> Triple(Icons.Outlined.ErrorOutline, red, redBg)
    }
    return SyncPresentation(labels.first, labels.second, icon, foreground, background)
}

private val TransitSyncPhase.displayPriority: Int
    get() = when (this) {
        TransitSyncPhase.PERMANENT_ERROR -> 0
        TransitSyncPhase.DELETE_PERMANENT_ERROR -> 0
        TransitSyncPhase.TEMPORARY_ERROR -> 1
        TransitSyncPhase.DELETE_TEMPORARY_ERROR -> 1
        TransitSyncPhase.PENDING -> 2
        TransitSyncPhase.DELETE_PENDING -> 2
        TransitSyncPhase.SYNCING -> 3
        TransitSyncPhase.DELETING -> 3
        TransitSyncPhase.LOCAL_SAVING -> 4
        TransitSyncPhase.LOCAL_SAFE -> 5
        TransitSyncPhase.LOCAL_DELETED -> 5
        TransitSyncPhase.SYNCED -> 6
        TransitSyncPhase.DELETED -> 6
    }

private fun updatedLabel(lang: AppLanguage, epochMillis: Long): String {
    val formatted = runCatching {
        Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
    }.getOrDefault("-")
    return when (lang) {
        AppLanguage.TR -> "Son durum: $formatted"
        AppLanguage.DE -> "Letzter Status: $formatted"
        AppLanguage.EN -> "Last status: $formatted"
    }
}

private fun attemptLabel(lang: AppLanguage, count: Int): String = when (lang) {
    AppLanguage.TR -> "Senkron denemesi: $count"
    AppLanguage.DE -> "Synchronisierungsversuche: $count"
    AppLanguage.EN -> "Sync attempts: $count"
}

private fun syncDetailsTitle(lang: AppLanguage): String = when (lang) {
    AppLanguage.TR -> "Senkron makbuzu"
    AppLanguage.DE -> "Synchronisierungsbeleg"
    AppLanguage.EN -> "Sync receipt"
}

private fun closeLabel(lang: AppLanguage): String = when (lang) {
    AppLanguage.TR -> "Kapat"
    AppLanguage.DE -> "Schließen"
    AppLanguage.EN -> "Close"
}
