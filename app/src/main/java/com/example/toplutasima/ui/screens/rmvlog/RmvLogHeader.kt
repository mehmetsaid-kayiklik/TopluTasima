package com.example.toplutasima.ui.screens.rmvlog

import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.S
import com.example.toplutasima.viewmodel.rmvlog.LogMode
import com.example.toplutasima.viewmodel.rmvlog.RmvLogUiState

@Composable
internal fun RmvLogHeader(
    state: RmvLogUiState,
    showPersonal: Boolean,
    lang: AppLanguage,
    onToggleMode: () -> Unit,
    onTogglePersonal: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.secondary
                    )
                )
            )
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    when {
                        showPersonal -> S.personalTitle(lang)
                        state.mode == LogMode.MANUAL -> S.manualLogTitle(lang)
                        else -> S.logHeader(lang)
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    when {
                        showPersonal -> "GPS · ORS"
                        state.mode == LogMode.MANUAL -> S.manualLogSubheader(lang)
                        else -> S.logSubheader(lang)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = onToggleMode,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color.White,
                        containerColor = if (state.mode == LogMode.MANUAL) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.15f)
                    )
                ) { Text(S.modeManual(lang), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge) }
                TextButton(
                    onClick = onTogglePersonal,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color.White,
                        containerColor = if (showPersonal) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.15f)
                    )
                ) { Text("🚗 ${S.modePersonal(lang)}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}