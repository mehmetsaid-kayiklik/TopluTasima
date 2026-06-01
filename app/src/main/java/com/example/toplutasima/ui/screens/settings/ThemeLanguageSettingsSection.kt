package com.example.toplutasima.ui.screens.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toplutasima.data.PrefsManager
import com.example.toplutasima.model.ThemeMode
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.LocaleManager
import com.example.toplutasima.ui.S
import com.example.toplutasima.ui.util.parsePreviewColor
import com.example.toplutasima.viewmodel.SettingsViewModel

@Composable
internal fun ThemeLanguageSettingsSection(
    lang: AppLanguage,
    settingsViewModel: SettingsViewModel
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("rmv_prefs", Context.MODE_PRIVATE) }
            Text(
                S.settingsSectionBasic(lang),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

        // ── Tema Card — Segmented Control ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(S.themeTitle(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                val themeModes = listOf(
                    ThemeMode.SYSTEM to S.themeModeSystem(lang),
                    ThemeMode.LIGHT to S.themeModeLight(lang),
                    ThemeMode.DARK to S.themeModeDark(lang)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    themeModes.forEach { (mode, name) ->
                        val isSelected = PrefsManager.themeMode == mode
                        Surface(
                            onClick = { settingsViewModel.setThemeMode(mode) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                   else MaterialTheme.colorScheme.surface,
                            tonalElevation = if (isSelected) 4.dp else 0.dp,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    name,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.onSurface
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Outlined.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Material You", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (lang == AppLanguage.TR) "Dinamik sistem renklerini kullan" else if (lang == AppLanguage.EN) "Use dynamic system colors" else "Dynamische Systemfarben verwenden",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = PrefsManager.useMaterialYou,
                            onCheckedChange = { PrefsManager.setMaterialYou(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        }

        // ── Language Card ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(S.languageTitle(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                val languages = listOf(
                    AppLanguage.TR to "Türkçe",
                    AppLanguage.DE to "Deutsch",
                    AppLanguage.EN to "English"
                )

                languages.forEach { (langOption, name) ->
                    val isSelected = lang == langOption
                    Surface(
                        onClick = { LocaleManager.setLanguage(langOption, prefs) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                               else MaterialTheme.colorScheme.surface,
                        tonalElevation = if (isSelected) 4.dp else 0.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurface
                            )
                            if (isSelected) {
                                Spacer(Modifier.weight(1f))
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
}

@Composable
internal fun ColorPersonalSettingsSection(
    lang: AppLanguage
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("rmv_prefs", Context.MODE_PRIVATE) }
    var bgColorHex by remember { mutableStateOf(prefs.getString("bg_color", "") ?: "") }
    var btnColorHex by remember { mutableStateOf(prefs.getString("btn_color", "") ?: "") }
        // ── Renk Ayarları Card ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(S.colorSettings(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(parsePreviewColor(bgColorHex))
                            .then(
                                if (parsePreviewColor(bgColorHex) == Color.Transparent)
                                    Modifier.background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                else Modifier
                            )
                    )
                    OutlinedTextField(
                        value = bgColorHex,
                        onValueChange = { bgColorHex = it },
                        label = { Text(S.bgColorHex(lang)) },
                        placeholder = { Text("#FFFFFF") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(parsePreviewColor(btnColorHex))
                            .then(
                                if (parsePreviewColor(btnColorHex) == Color.Transparent)
                                    Modifier.background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                else Modifier
                            )
                    )
                    OutlinedTextField(
                        value = btnColorHex,
                        onValueChange = { btnColorHex = it },
                        label = { Text(S.btnColorHex(lang)) },
                        placeholder = { Text("#00BCD4") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }

                Button(
                    onClick = {
                        prefs.edit().putString("bg_color", bgColorHex).putString("btn_color", btnColorHex).apply()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    Text(S.saveColors(lang), fontWeight = FontWeight.SemiBold)
                }

                Text(
                    S.restartHint(lang),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // ── Kişisel Araç Ayarları Card ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(S.personalSettingsCard(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                Text(S.personalWaypointInterval(lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                val intervalOptions = listOf(
                    10  to "10 ${S.personalSec(lang)}",
                    20  to "20 ${S.personalSec(lang)}",
                    30  to "30 ${S.personalSec(lang)}",
                    60  to "1 ${S.personalMin(lang)}"
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    intervalOptions.forEach { (seconds, label) ->
                        val selected = PrefsManager.waypointIntervalSeconds == seconds
                        FilterChip(
                            selected = selected,
                            onClick = { PrefsManager.setWaypointInterval(seconds) },
                            label = { Text(label, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
}
