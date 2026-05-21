package com.example.toplutasima.ui.screens.rmvlog

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toplutasima.data.PrefsManager
import com.example.toplutasima.model.UsageType
import com.example.toplutasima.model.VehicleType
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.ErrorRed
import com.example.toplutasima.ui.S
import com.example.toplutasima.ui.SuccessGreen
import com.example.toplutasima.ui.WarningAmber
import com.example.toplutasima.ui.components.TimeVisualTransformation
import com.example.toplutasima.ui.util.vehicleIcon
import com.example.toplutasima.viewmodel.RmvLogViewModel
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