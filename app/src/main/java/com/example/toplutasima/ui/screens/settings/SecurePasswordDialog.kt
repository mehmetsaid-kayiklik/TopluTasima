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
internal fun SecurePasswordDialog(
    title: String,
    desc: String,
    hint: String,
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: (CharArray) -> Unit,
    onDismiss: () -> Unit
) {
    var editTextRef by remember { mutableStateOf<EditText?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(desc, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { ctx ->
                        EditText(ctx).apply {
                            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                            this.hint = hint
                            requestFocus()
                            editTextRef = this
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    update = { }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val et = editTextRef
                    if (et != null) {
                        val textLength = et.text.length
                        if (textLength >= 4) {
                            val passwordChars = CharArray(textLength)
                            et.text.getChars(0, textLength, passwordChars, 0)
                            // Overwrite character buffer in EditText to prevent sensitive data lingering in memory
                            val editable = et.text
                            for (i in 0 until textLength) {
                                editable.replace(i, i + 1, "0")
                            }
                            editable.clear()
                            onConfirm(passwordChars)
                        }
                    }
                }
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = {
                val et = editTextRef
                if (et != null) {
                    val editable = et.text
                    val textLength = editable.length
                    for (i in 0 until textLength) {
                        editable.replace(i, i + 1, "0")
                    }
                    editable.clear()
                }
                onDismiss()
            }) {
                Text(cancelLabel)
            }
        }
    )
}