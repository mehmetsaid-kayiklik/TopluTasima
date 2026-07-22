package com.example.toplutasima.drive.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.widget.Toast

@Composable
internal fun VehicleAssignmentSection(
    state: VehicleAssignmentUiState,
    onShowPicker: () -> Unit,
    onHidePicker: () -> Unit,
    onAssign: (String) -> Unit,
    onRemove: () -> Unit,
    onMessageShown: () -> Unit
) {
    val context = LocalContext.current
    state.message?.let { message ->
        LaunchedEffect(message) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            onMessageShown()
        }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Atanmış kişi", style = MaterialTheme.typography.titleMedium)
            val assignment = state.assignment
            val assignedPerson = state.assignedPerson
            when {
                assignment?.activePersonId == null -> Text("Atanmamış")
                assignedPerson != null -> Text(assignedPerson.displayName.orEmpty().ifBlank { "Paylaşılan kişi" })
                else -> Text(
                    "Kişi artık paylaşılmıyor, silinmiş veya erişilemiyor.",
                    color = MaterialTheme.colorScheme.error
                )
            }
            assignment?.let {
                val status = when {
                    it.healthCode != null -> "Dikkat gerekiyor: ${it.healthCode.name}"
                    it.syncState.name == "PENDING" || it.syncState.name == "RETRY" -> "Senkronizasyon bekliyor"
                    it.syncState.name == "CONFLICT" -> "Çakışma var; güncel remote değer korunuyor"
                    else -> "Senkronize"
                }
                Text(status, style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onShowPicker, enabled = !state.working) {
                    Text(if (assignment?.activePersonId == null) "Kişi seç" else "Atamayı değiştir")
                }
                Spacer(Modifier.weight(1f))
                if (assignment?.activePersonId != null) {
                    OutlinedButton(onClick = onRemove, enabled = !state.working) {
                        Text("Atamayı kaldır")
                    }
                }
            }
            if (state.working) CircularProgressIndicator()
        }
    }

    if (state.pickerVisible) {
        AlertDialog(
            onDismissRequest = onHidePicker,
            title = { Text("Kişi seç") },
            text = {
                when {
                    state.directoryLoading -> CircularProgressIndicator()
                    state.directoryError -> Text("Kişi listesi yenilenemedi; güvenli yerel liste gösteriliyor.")
                    state.people.isEmpty() -> Text("Paylaşılmış aktif kişi yok. Bellek'te ‘TopluTaşıma ile paylaş’ seçeneğini açın.")
                    else -> LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(state.people.filter { it.selectable }, key = { it.personId }) { person ->
                            TextButton(
                                onClick = { onAssign(person.personId) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(person.displayName.orEmpty().ifBlank { "İsimsiz kişi" })
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onHidePicker) { Text("Kapat") } }
        )
    }
}
