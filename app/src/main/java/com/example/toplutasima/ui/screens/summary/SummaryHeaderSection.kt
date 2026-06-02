package com.example.toplutasima.ui.screens.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.S
import com.example.toplutasima.ui.components.RmvFooter
import com.example.toplutasima.ui.theme.AccentDark
import com.example.toplutasima.ui.theme.AccentLight
import com.example.toplutasima.ui.theme.AccentSoftD
import com.example.toplutasima.ui.theme.AccentSoftL
import com.example.toplutasima.ui.theme.SurfaceD2
import com.example.toplutasima.ui.theme.SurfaceL2
import com.example.toplutasima.ui.theme.TextHighDark
import com.example.toplutasima.ui.theme.TextHighLight
import com.example.toplutasima.ui.theme.isAppInDarkTheme
import com.example.toplutasima.viewmodel.SummaryUiState

@Composable
private fun isDark() = isAppInDarkTheme()

@Composable
internal fun SummaryHeaderSection(
    state: SummaryUiState,
    showPersonal: Boolean,
    lang: AppLanguage,
    onSheetMenuOpenChange: (Boolean) -> Unit,
    onLoadSheet: (String) -> Unit,
    onTogglePersonal: () -> Unit
) {
    val dark = isDark()
    val accent = if (dark) AccentDark else AccentLight
    val accentSoft = if (dark) AccentSoftD else AccentSoftL
    val surface = if (dark) SurfaceD2 else SurfaceL2
    val textHigh = if (dark) TextHighDark else TextHighLight

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(modifier = Modifier.weight(3f)) {
            FilledTonalButton(
                onClick = { if (!showPersonal) onSheetMenuOpenChange(true) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                enabled = !showPersonal,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = surface,
                    contentColor = textHigh,
                    disabledContainerColor = surface.copy(alpha = 0.62f),
                    disabledContentColor = textHigh.copy(alpha = 0.62f)
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.DateRange,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    displaySummarySheet(state.selectedSheet, lang),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            DropdownMenu(
                expanded = state.sheetMenuOpen,
                onDismissRequest = { onSheetMenuOpenChange(false) }
            ) {
                state.sheetNames.forEach { name ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                displaySummarySheet(name, lang),
                                fontWeight = if (name == state.selectedSheet) FontWeight.Bold else FontWeight.Normal,
                                color = if (name == state.selectedSheet) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = { onLoadSheet(name) }
                    )
                }
            }
        }
        FilledTonalButton(
            onClick = onTogglePersonal,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = if (showPersonal) ButtonDefaults.filledTonalButtonColors(
                containerColor = accentSoft,
                contentColor = accent
            ) else ButtonDefaults.filledTonalButtonColors(
                containerColor = surface,
                contentColor = accent
            )
        ) {
            Icon(
                imageVector = Icons.Outlined.DirectionsCar,
                contentDescription = S.modePersonal(lang),
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

internal fun LazyListScope.SummaryFooterSection(
    isLoading: Boolean,
    lang: AppLanguage,
    onRefresh: () -> Unit
) {
    item {
        Button(
            onClick = onRefresh,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            Text(
                if (isLoading) S.refreshing(lang) else S.refreshData(lang),
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
        }
    }

    item { Spacer(Modifier.height(4.dp)) }
    item { RmvFooter() }
    item { Spacer(Modifier.height(16.dp)) }
}
