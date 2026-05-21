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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileEditDialog(
    profile: com.example.toplutasima.data.local.entity.ProfileEntity?,
    lang: com.example.toplutasima.ui.AppLanguage,
    onDismiss: () -> Unit,
    onSave: (displayName: String, nameKind: String, birthHint: String, memoryNote: String, infoSource: String) -> Unit
) {
    var displayName by remember { mutableStateOf(profile?.displayName ?: "") }
    var nameKind by remember { mutableStateOf(profile?.nameKind ?: "NICKNAME") }
    var birthHint by remember { mutableStateOf(profile?.birthHint ?: "") }
    var memoryNote by remember { mutableStateOf(profile?.memoryNote ?: "") }
    var infoSource by remember { mutableStateOf(profile?.infoSource ?: "UNKNOWN") }

    var nameKindMenuOpen by remember { mutableStateOf(false) }
    var infoSourceMenuOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (profile == null) "➕ " + S.profileAddNewTitle(lang) else "✏️ " + S.profileEditTitle(lang),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Display Name
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text(S.profileFieldDisplayName(lang)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                // Name Kind Dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = when (nameKind) {
                            "NICKNAME" -> S.profileNameKindNickname(lang)
                            "FIRST_NAME" -> S.profileNameKindFirstName(lang)
                            else -> S.profileNameKindUnknown(lang)
                        },
                        onValueChange = {},
                        label = { Text(S.profileFieldNameKind(lang)) },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            IconButton(onClick = { nameKindMenuOpen = true }) {
                                Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = nameKindMenuOpen,
                        onDismissRequest = { nameKindMenuOpen = false },
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        DropdownMenuItem(
                            text = { Text(S.profileNameKindNickname(lang)) },
                            onClick = {
                                nameKind = "NICKNAME"
                                nameKindMenuOpen = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(S.profileNameKindFirstName(lang)) },
                            onClick = {
                                nameKind = "FIRST_NAME"
                                nameKindMenuOpen = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(S.profileNameKindUnknown(lang)) },
                            onClick = {
                                nameKind = "UNKNOWN"
                                nameKindMenuOpen = false
                            }
                        )
                    }
                }

                // Info Source Dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = when (infoSource) {
                            "ASKED" -> S.profileInfoSourceAsked(lang)
                            "OBSERVED" -> S.profileInfoSourceObserved(lang)
                            else -> S.profileInfoSourceUnknown(lang)
                        },
                        onValueChange = {},
                        label = { Text(S.profileFieldInfoSource(lang)) },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            IconButton(onClick = { infoSourceMenuOpen = true }) {
                                Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = infoSourceMenuOpen,
                        onDismissRequest = { infoSourceMenuOpen = false },
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        DropdownMenuItem(
                            text = { Text(S.profileInfoSourceAsked(lang)) },
                            onClick = {
                                infoSource = "ASKED"
                                infoSourceMenuOpen = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(S.profileInfoSourceObserved(lang)) },
                            onClick = {
                                infoSource = "OBSERVED"
                                infoSourceMenuOpen = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(S.profileInfoSourceUnknown(lang)) },
                            onClick = {
                                infoSource = "UNKNOWN"
                                infoSourceMenuOpen = false
                            }
                        )
                    }
                }

                // Birth Hint
                OutlinedTextField(
                    value = birthHint,
                    onValueChange = { birthHint = it },
                    label = { Text(S.profileFieldBirthHint(lang)) },
                    placeholder = { Text("örn: 1990'lar, 12 Mayıs") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                // Memory Note
                OutlinedTextField(
                    value = memoryNote,
                    onValueChange = { memoryNote = it },
                    label = { Text(S.profileFieldMemoryNote(lang)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    shape = RoundedCornerShape(12.dp)
                )

                // Warning message
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "⚠️",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = S.profileWarningMemoryNote(lang),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (displayName.isNotBlank()) {
                        onSave(displayName.trim(), nameKind, birthHint.trim(), memoryNote.trim(), infoSource)
                    }
                },
                enabled = displayName.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(S.editDone(lang))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(S.cancelChange(lang))
            }
        }
    )
}