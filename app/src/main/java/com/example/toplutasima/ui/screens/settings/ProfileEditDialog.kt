package com.example.toplutasima.ui.screens.settings

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import com.example.toplutasima.ui.S
import com.example.toplutasima.ui.WarningAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileEditDialog(
    profile: com.example.toplutasima.data.local.entity.ProfileEntity?,
    lang: com.example.toplutasima.ui.AppLanguage,
    onDismiss: () -> Unit,
    onSave: (
        displayName: String,
        nameKind: String,
        birthHint: String,
        memoryNote: String,
        infoSource: String,
        sharedWithTransit: Boolean
    ) -> Unit
) {
    var displayName by remember { mutableStateOf(profile?.displayName ?: "") }
    var nameKind by remember { mutableStateOf(profile?.nameKind ?: "NICKNAME") }
    var birthHint by remember { mutableStateOf(profile?.birthHint ?: "") }
    var memoryNote by remember { mutableStateOf(profile?.memoryNote ?: "") }
    var infoSource by remember { mutableStateOf(profile?.infoSource ?: "UNKNOWN") }
    var sharedWithTransit by remember { mutableStateOf(profile?.sharedWithTransit ?: false) }

    var nameKindMenuOpen by remember { mutableStateOf(false) }
    var infoSourceMenuOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (profile == null) S.profileAddNewTitle(lang) else S.profileEditTitle(lang),
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
                        Icon(
                            imageVector = Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = WarningAmber
                        )
                        Text(
                            text = S.profileWarningMemoryNote(lang),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = S.profileShareWithTransitTitle(lang),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = S.profileShareWithTransitDesc(lang),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = sharedWithTransit,
                        onCheckedChange = { sharedWithTransit = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (displayName.isNotBlank()) {
                        onSave(
                            displayName.trim(),
                            nameKind,
                            birthHint.trim(),
                            memoryNote.trim(),
                            infoSource,
                            sharedWithTransit
                        )
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
