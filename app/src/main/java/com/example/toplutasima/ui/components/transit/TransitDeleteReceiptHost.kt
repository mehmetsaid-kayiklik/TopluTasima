package com.example.toplutasima.ui.components.transit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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

/** A record-independent receipt: it remains visible after Room has removed the card. */
@Composable
fun TransitDeleteReceiptHost(
    onRetry: (String) -> Unit,
    onKeepLocalOnly: (String) -> Unit,
    onOpenHistory: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    store: TransitSyncStatusStore = TransitSyncStatusStore.get(LocalContext.current),
    lang: AppLanguage = LocaleManager.currentLanguage
) {
    if (!TransitFeatureFlags.SYNC_RECEIPTS || !TransitFeatureFlags.SYNC_DELETE_RECEIPTS) return
    val receipts by store.observeDeletionReceipts()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val latest = receipts.firstOrNull() ?: return
    var dismissedKey by remember { mutableStateOf<String?>(null) }
    val key = "${latest.recordId}:${latest.updatedAtEpochMillis}"
    if (dismissedKey == key) return

    DeleteReceiptCard(
        state = latest,
        onRetry = { onRetry(latest.recordId) },
        onKeepLocalOnly = { onKeepLocalOnly(latest.recordId) },
        onOpenHistory = onOpenHistory?.let { callback -> { callback(latest.recordId) } },
        onDismiss = { dismissedKey = key },
        modifier = modifier,
        lang = lang
    )
}

@Composable
private fun DeleteReceiptCard(
    state: TransitSyncState,
    onRetry: () -> Unit,
    onKeepLocalOnly: () -> Unit,
    onOpenHistory: (() -> Unit)?,
    onDismiss: () -> Unit,
    modifier: Modifier,
    lang: AppLanguage
) {
    val permanentFailure = state.phase == TransitSyncPhase.DELETE_PERMANENT_ERROR
    val shortId = state.deleteMetadata?.firestoreDocId?.takeLast(8)
        ?: state.recordId.takeLast(8)
    val description = trd(
        lang,
        "Silme makbuzu, kayıt $shortId",
        "Löschbeleg, Eintrag $shortId",
        "Delete receipt, record $shortId"
    )
    Card(
        modifier = modifier.fillMaxWidth().semantics { contentDescription = description },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(description, style = MaterialTheme.typography.labelMedium)
            TransitSyncStatusChip(state = state, lang = lang)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (permanentFailure) {
                    TextButton(onClick = onRetry) {
                        Text(trd(lang, "Yeniden dene", "Erneut versuchen", "Retry"))
                    }
                    TextButton(onClick = onKeepLocalOnly) {
                        Text(trd(lang, "Yalnız yerel sil", "Nur lokal löschen", "Keep local delete"))
                    }
                }
                if (TransitFeatureFlags.TRANSIT_CHANGE_HISTORY && onOpenHistory != null) {
                    TextButton(onClick = onOpenHistory) {
                        Text(trd(lang, "Geçmiş", "Verlauf", "History"))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(trd(lang, "Kapat", "Schließen", "Dismiss"))
                }
            }
        }
    }
}

private fun trd(lang: AppLanguage, tr: String, de: String, en: String): String = when (lang) {
    AppLanguage.TR -> tr
    AppLanguage.DE -> de
    AppLanguage.EN -> en
}
