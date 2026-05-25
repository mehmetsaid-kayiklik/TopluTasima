package com.example.toplutasima.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import com.example.toplutasima.data.backup.ProfileBackupManager
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.S
import com.example.toplutasima.viewmodel.SettingsUiState
import com.example.toplutasima.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Arrays

@Composable
internal fun ProfileBackupSection(
    settingsState: SettingsUiState,
    settingsViewModel: SettingsViewModel,
    lang: AppLanguage
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backupManager = remember { ProfileBackupManager(context) }
    var showExportPasswordDialog by remember { mutableStateOf(false) }
    var showImportPasswordDialog by remember { mutableStateOf(false) }
    var showWipeConfirmDialog by remember { mutableStateOf(false) }
    var showImportResultDialog by remember { mutableStateOf<ProfileBackupManager.ImportResult?>(null) }
    var pendingExportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var operationError by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            pendingExportUri = uri
            showExportPasswordDialog = true
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
            showImportPasswordDialog = true
        }
    }
        // ── Profil Yönetimi Card ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "👤 " + S.profileManagementTitle(lang),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = S.profileManagementDesc(lang),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { settingsViewModel.showProfileManager(true) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Text(S.profileManageButton(lang), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ── Profil Yedeği Card ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = S.profileBackupSectionTitle(lang),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = S.profileBackupDesc(lang),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            runCatching {
                                exportLauncher.launch("profiles_backup.toplutasima-profiles")
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Text(S.profileExportButton(lang), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            runCatching {
                                importLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Text(S.profileImportButton(lang), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = { showWipeConfirmDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Text(S.profileWipeButton(lang), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (showExportPasswordDialog) {
            SecurePasswordDialog(
                title = S.profilePasswordTitle(lang),
                desc = S.profilePasswordExportDesc(lang),
                hint = S.profilePasswordHint(lang),
                confirmLabel = S.confirmChange(lang),
                cancelLabel = S.cancelChange(lang),
                onConfirm = { password ->
                    val uri = pendingExportUri
                    if (uri != null) {
                        scope.launch {
                            try {
                                val encryptedBytes = backupManager.exportBackup(password)
                                withContext(Dispatchers.IO) {
                                    context.contentResolver.openOutputStream(uri)?.use { outStream ->
                                        outStream.write(encryptedBytes)
                                    }
                                }
                            } catch (e: Exception) {
                                operationError = e.message ?: S.unknownError(lang)
                            } finally {
                                Arrays.fill(password, '0') // Wipe password array
                                showExportPasswordDialog = false
                                pendingExportUri = null
                            }
                        }
                    } else {
                        Arrays.fill(password, '0')
                        showExportPasswordDialog = false
                    }
                },
                onDismiss = {
                    showExportPasswordDialog = false
                    pendingExportUri = null
                }
            )
        }

        if (showImportPasswordDialog) {
            SecurePasswordDialog(
                title = S.profilePasswordTitle(lang),
                desc = S.profilePasswordImportDesc(lang),
                hint = S.profilePasswordHint(lang),
                confirmLabel = S.confirmChange(lang),
                cancelLabel = S.cancelChange(lang),
                onConfirm = { password ->
                    val uri = pendingImportUri
                    if (uri != null) {
                        scope.launch {
                            try {
                                val encryptedBytes = withContext(Dispatchers.IO) {
                                    context.contentResolver.openInputStream(uri)?.use { inStream ->
                                        inStream.readBytes()
                                    }
                                }
                                if (encryptedBytes != null) {
                                    val result = backupManager.importBackup(encryptedBytes, password)
                                    if (result.error != null) {
                                        operationError = result.error
                                    } else {
                                        showImportResultDialog = result
                                    }
                                } else {
                                    operationError = S.unknownError(lang)
                                }
                            } catch (e: Exception) {
                                operationError = e.message ?: S.unknownError(lang)
                            } finally {
                                Arrays.fill(password, '0') // Wipe password array
                                showImportPasswordDialog = false
                                pendingImportUri = null
                            }
                        }
                    } else {
                        Arrays.fill(password, '0')
                        showImportPasswordDialog = false
                    }
                },
                onDismiss = {
                    showImportPasswordDialog = false
                    pendingImportUri = null
                }
            )
        }

        if (showWipeConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showWipeConfirmDialog = false },
                title = { Text(S.profileWipeConfirmTitle(lang), fontWeight = FontWeight.Bold) },
                text = { Text(S.profileWipeConfirmText(lang)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showWipeConfirmDialog = false
                            scope.launch {
                                backupManager.clearBackupData()
                            }
                        }
                    ) {
                        Text(S.favDelete(lang), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showWipeConfirmDialog = false }) {
                        Text(S.cancelChange(lang))
                    }
                }
            )
        }

        // Result Dialog
        val resultData = showImportResultDialog
        if (resultData != null) {
            AlertDialog(
                onDismissRequest = { showImportResultDialog = null },
                title = { Text(S.profileImportSuccessTitle(lang), fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        S.profileImportSuccessText(
                            resultData.addedProfiles,
                            resultData.updatedProfiles,
                            resultData.addedLinks,
                            resultData.updatedLinks,
                            resultData.skippedLinks,
                            lang
                        )
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showImportResultDialog = null }) {
                        Text(S.editDone(lang))
                    }
                }
            )
        }

        // Error Dialog
        val err = operationError
        if (err != null) {
            AlertDialog(
                onDismissRequest = { operationError = null },
                title = { Text(S.profileErrorTitle(lang), fontWeight = FontWeight.Bold) },
                text = { Text(err) },
                confirmButton = {
                    TextButton(onClick = { operationError = null }) {
                        Text(S.confirmChange(lang))
                    }
                }
            )
        }

        // Profile Manager Dialog
        if (settingsState.showProfileManager) {
            ProfileManagerDialog(
                state = settingsState,
                lang = lang,
                onDismiss = { settingsViewModel.showProfileManager(false) },
                onAddProfile = { settingsViewModel.startEditingProfile(null) },
                onEditProfile = { profile -> settingsViewModel.startEditingProfile(profile) },
                onDeleteProfile = { id -> settingsViewModel.showDeleteProfileConfirm(id) },
                onToggleArchive = { profile -> settingsViewModel.toggleArchiveProfile(profile) }
            )
        }

        // Profile Edit Dialog
        if (settingsState.showProfileEditDialog) {
            ProfileEditDialog(
                profile = settingsState.editingProfile,
                lang = lang,
                onDismiss = { settingsViewModel.cancelEditingProfile() },
                onSave = { displayName, nameKind, birthHint, memoryNote, infoSource, sharedWithTransit ->
                    settingsViewModel.saveProfile(
                        displayName,
                        nameKind,
                        birthHint,
                        memoryNote,
                        infoSource,
                        sharedWithTransit
                    )
                }
            )
        }

        // Profile Delete Confirmation Dialog
        val deleteProfileId = settingsState.profileDeleteConfirmId
        if (deleteProfileId != null) {
            AlertDialog(
                onDismissRequest = { settingsViewModel.showDeleteProfileConfirm(null) },
                title = { Text(S.profileWipeConfirmTitle(lang), fontWeight = FontWeight.Bold) },
                text = { Text(S.profileWipeConfirmText(lang)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            settingsViewModel.confirmDeleteProfile()
                        }
                    ) {
                        Text(S.favDelete(lang), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { settingsViewModel.showDeleteProfileConfirm(null) }) {
                        Text(S.cancelChange(lang))
                    }
                }
            )
        }

}
