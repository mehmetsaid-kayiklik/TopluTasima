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
internal fun ProfileManagerDialog(
    state: SettingsUiState,
    lang: com.example.toplutasima.ui.AppLanguage,
    onDismiss: () -> Unit,
    onAddProfile: () -> Unit,
    onEditProfile: (com.example.toplutasima.data.local.entity.ProfileEntity) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onToggleArchive: (com.example.toplutasima.data.local.entity.ProfileEntity) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("👤 " + S.profileManagementTitle(lang), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onAddProfile,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text(S.profileAddButton(lang), fontWeight = FontWeight.Bold)
                }

                if (state.profiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(S.profileNoProfilesYet(lang), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.profiles) { profile ->
                            val alpha = if (profile.archived) 0.5f else 1.0f
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .alpha(alpha),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = profile.displayName,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            IconButton(onClick = { onEditProfile(profile) }) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Edit",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            IconButton(onClick = { onToggleArchive(profile) }) {
                                                Icon(
                                                    imageVector = if (profile.archived) Icons.Default.Refresh else Icons.Default.FavoriteBorder,
                                                    contentDescription = "Archive",
                                                    tint = MaterialTheme.colorScheme.secondary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            IconButton(onClick = { onDeleteProfile(profile.id) }) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Details
                                    val kindText = when (profile.nameKind) {
                                        "NICKNAME" -> "🏷️ " + S.profileNameKindNickname(lang)
                                        "FIRST_NAME" -> "👤 " + S.profileNameKindFirstName(lang)
                                        else -> "❓ " + S.profileNameKindUnknown(lang)
                                    }
                                    Text(
                                        text = kindText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )

                                    if (!profile.birthHint.isNullOrBlank()) {
                                        Text(
                                            text = "🎂 " + S.profileBirthHintPrefix(lang) + ": ${profile.birthHint}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                        )
                                    }

                                    if (!profile.memoryNote.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "📝 ${profile.memoryNote}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(S.editDone(lang))
            }
        }
    )
}