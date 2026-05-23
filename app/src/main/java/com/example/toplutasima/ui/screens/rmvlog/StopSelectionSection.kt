package com.example.toplutasima.ui.screens.rmvlog

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.focus.focusRequester
import com.example.toplutasima.data.PrefsManager
import com.example.toplutasima.model.UsageType
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.S
import com.example.toplutasima.ui.SuccessGreen
import com.example.toplutasima.viewmodel.RmvLogViewModel
import com.example.toplutasima.viewmodel.rmvlog.RmvLogUiState

@Composable
internal fun StopSelectionSection(
    state: RmvLogUiState,
    viewModel: RmvLogViewModel,
    lang: AppLanguage,
    hasLocationPermission: Boolean,
    onRequestLocationPermission: () -> Unit,
    destFocusRequester: FocusRequester
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    // --- YAKINDAKI DURAKLAR ---
    if (hasLocationPermission) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        S.nearbyStopsTitle(lang),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(onClick = { viewModel.fetchNearbyStops() }) {
                        Text(S.nearbyRefresh(lang), fontSize = 12.sp)
                    }
                }
                if (state.nearbyLoading) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            S.nearbyLoading(lang),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (state.nearbyStops.isEmpty()) {
                    Text(
                        if (state.nearbyHasLoaded) S.nearbyNone(lang) else S.nearbyRefreshHint(lang),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.nearbyStops.forEach { stop ->
                            AssistChip(
                                onClick = {
                                    viewModel.selectNearbyStop(stop)
                                    destFocusRequester.requestFocus()
                                    keyboardController?.show()
                                },
                                label = {
                                    Column {
                                        Text(stop.name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                        Text(S.nearbyMeters(stop.distanceMeters, lang), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                leadingIcon = { Text("📍", fontSize = 14.sp) },
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }
            }
        }
    } else {
        // Show a small button to request permission
        FilledTonalButton(
            onClick = {
                onRequestLocationPermission()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(S.nearbyStopsTitle(lang), fontWeight = FontWeight.SemiBold)
        }
    }

    // --- DURAK SEÇİMİ CARD ---
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(S.stopSelection(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                FilledTonalIconButton(
                    onClick = { viewModel.swapFromTo() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Text("⇅", fontSize = 16.sp)
                }
            }

            // Boarding favorite chips
            val boardingFavs = PrefsManager.boardingFavorites()
            if (boardingFavs.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    boardingFavs.forEach { fav ->
                        AssistChip(
                            onClick = { viewModel.selectFavoriteFrom(fav.stopId, fav.stopName) },
                            label = { Text(fav.label, fontSize = 12.sp, maxLines = 1) },
                            leadingIcon = { Text("⭐", fontSize = 12.sp) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }

            // From
            OutlinedTextField(
                value = state.from,
                onValueChange = { viewModel.updateFrom(it) },
                label = { Text(S.boardingStop(lang)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    if (state.from.isNotBlank()) {
                        IconButton(onClick = { viewModel.clearFrom() }) {
                            Icon(Icons.Default.Clear, contentDescription = S.clear(lang))
                        }
                    }
                }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Button(
                        onClick = { viewModel.searchFrom() },
                        shape = RoundedCornerShape(10.dp)
                    ) { Text(S.search(lang)) }
                    DropdownMenu(expanded = state.fromMenuOpen, onDismissRequest = { viewModel.setFromMenuOpen(false) }) {
                        state.fromOptions.forEach { opt -> 
                            DropdownMenuItem(
                                text = { Text(if (opt.resolvedStopName.isNotBlank()) "${opt.name} -> ${opt.resolvedStopName}" else opt.name) },
                                onClick = { 
                                    viewModel.selectFrom(opt) 
                                    destFocusRequester.requestFocus()
                                }
                            ) 
                        }
                    }
                }
                if (state.fromId.isNotBlank()) {
                    Text(S.selected(lang), color = SuccessGreen, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                    // Add to favorites button
                    IconButton(
                        onClick = { viewModel.showAddFavoriteDialog(state.fromId, state.from) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Text("⭐", fontSize = 16.sp)
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            // Alighting favorite chips
            val alightingFavs = PrefsManager.alightingFavorites()
            if (alightingFavs.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    alightingFavs.forEach { fav ->
                        AssistChip(
                            onClick = { viewModel.selectFavoriteTo(fav.stopId, fav.stopName) },
                            label = { Text(fav.label, fontSize = 12.sp, maxLines = 1) },
                            leadingIcon = { Text("⭐", fontSize = 12.sp) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }

            // To
            OutlinedTextField(
                value = state.to,
                onValueChange = { viewModel.updateTo(it) },
                label = { Text(S.alightingStop(lang)) },
                modifier = Modifier.fillMaxWidth().focusRequester(destFocusRequester),
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    if (state.to.isNotBlank()) {
                        IconButton(onClick = { viewModel.clearTo() }) {
                            Icon(Icons.Default.Clear, contentDescription = S.clear(lang))
                        }
                    }
                }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Button(
                        onClick = { viewModel.searchTo() },
                        shape = RoundedCornerShape(10.dp)
                    ) { Text(S.search(lang)) }
                    DropdownMenu(expanded = state.toMenuOpen, onDismissRequest = { viewModel.setToMenuOpen(false) }) {
                        state.toOptions.forEach { opt -> 
                            DropdownMenuItem(
                                text = { Text(if (opt.resolvedStopName.isNotBlank()) "${opt.name} -> ${opt.resolvedStopName}" else opt.name) },
                                onClick = { 
                                    viewModel.selectTo(opt) 
                                    keyboardController?.hide()
                                }
                            ) 
                        }
                    }
                }
                if (state.toId.isNotBlank()) {
                    Text(S.selected(lang), color = SuccessGreen, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                    // Add to favorites button
                    IconButton(
                        onClick = { viewModel.showAddFavoriteDialog(state.toId, state.to) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Text("⭐", fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
internal fun AddFavoriteDialog(
    state: RmvLogUiState,
    viewModel: RmvLogViewModel,
    lang: AppLanguage
) {
    // ── Add Favorite Dialog ──
    if (state.showAddFavDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissAddFavoriteDialog() },
            title = { Text(S.addToFavorites(lang), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(state.addFavStopName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = state.addFavLabel,
                        onValueChange = { viewModel.updateAddFavLabel(it) },
                        label = { Text(S.favLabel(lang)) },
                        placeholder = { Text(S.favLabelHint(lang)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    Text(S.favUsageType(lang), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            UsageType.BOARDING to S.favUsageBoarding(lang),
                            UsageType.ALIGHTING to S.favUsageAlighting(lang),
                            UsageType.BOTH to S.favUsageBoth(lang)
                        ).forEach { (type, label) ->
                            val selected = state.addFavUsageType == type
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.updateAddFavUsageType(type) },
                                label = { Text(label, fontSize = 12.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmAddFavorite() }) { Text(S.add(lang)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissAddFavoriteDialog() }) { Text(S.cancel(lang)) }
            }
        )
}
}
