package com.example.toplutasima.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koin.androidx.compose.koinViewModel
import com.example.toplutasima.data.PrefsManager
import com.example.toplutasima.model.UsageType
import com.example.toplutasima.ui.ErrorRed
import com.example.toplutasima.ui.LocaleManager
import com.example.toplutasima.ui.S
import com.example.toplutasima.ui.SuccessGreen
import com.example.toplutasima.ui.WarningAmber
import com.example.toplutasima.model.VehicleType
import com.example.toplutasima.ui.components.RmvFooter
import com.example.toplutasima.ui.components.TimeVisualTransformation
import com.example.toplutasima.viewmodel.RmvLogViewModel
import com.example.toplutasima.viewmodel.LogMode
import com.example.toplutasima.viewmodel.PersonalTripViewModel

@Composable
fun RMVLogScreen(modifier: Modifier = Modifier, viewModel: RmvLogViewModel = koinViewModel()) {
    val personalViewModel: PersonalTripViewModel = koinViewModel()
    val personalState by personalViewModel.uiState.collectAsState()
    val state by viewModel.uiState.collectAsState()
    val scroll = rememberScrollState()
    val lang = LocaleManager.currentLanguage

    val destFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // --- GRADIENT HEADER ---
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
                        when (state.mode) {
                            LogMode.MANUAL   -> S.manualLogTitle(lang)
                            LogMode.PERSONAL -> S.personalTitle(lang)
                            else             -> S.logHeader(lang)
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when (state.mode) {
                            LogMode.MANUAL   -> S.manualLogSubheader(lang)
                            LogMode.PERSONAL -> "GPS · ORS"
                            else             -> S.logSubheader(lang)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
                // [Manuel] ve [🚗 Kişisel] — yan yana iki buton
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = { viewModel.setMode(if (state.mode == LogMode.MANUAL) LogMode.AUTO else LogMode.MANUAL) },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color.White,
                            containerColor = if (state.mode == LogMode.MANUAL) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.15f)
                        )
                    ) { Text(S.modeManual(lang), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge) }
                    TextButton(
                        onClick = { viewModel.setMode(if (state.mode == LogMode.PERSONAL) LogMode.AUTO else LogMode.PERSONAL) },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color.White,
                            containerColor = if (state.mode == LogMode.PERSONAL) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.15f)
                        )
                    ) { Text("🚗 ${S.modePersonal(lang)}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge) }
                }
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.mode == LogMode.PERSONAL) {
                PersonalTripsContent(
                    uiState = personalState,
                    lang = lang,
                    viewModel = personalViewModel
                )
            } else if (state.isManualMode) {
                ManualLogForm(state, viewModel, lang)
            } else {

            // Location permission launcher
            var hasLocationPermission by remember { mutableStateOf(viewModel.hasLocationPermission()) }
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { perms ->
                hasLocationPermission =
                    perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                    viewModel.hasLocationPermission()
            }

            // Auto-fetch nearby only once when the screen first opens.
            LaunchedEffect(Unit) {
                if (hasLocationPermission) {
                    viewModel.fetchNearbyStopsOnOpen()
                }
            }

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
                        permissionLauncher.launch(arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ))
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
                                        text = { Text(opt.name) }, 
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
                                        text = { Text(opt.name) }, 
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

            // --- TARİH & SAAT CARD ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(S.dateTime(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = state.date,
                            onValueChange = { viewModel.updateDate(it) },
                            label = { Text(S.date(lang)) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = state.time,
                            onValueChange = { viewModel.updateTime(it) },
                            label = { Text(S.time(lang)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            visualTransformation = TimeVisualTransformation(),
                            trailingIcon = {
                                if (state.time.isNotBlank()) {
                                    IconButton(onClick = { viewModel.clearTime() }) {
                                        Icon(Icons.Default.Clear, contentDescription = S.clear(lang))
                                    }
                                }
                            }
                        )
                    }

                    Button(
                        onClick = { viewModel.fetchDepartures() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        Text(S.fetchTimes(lang), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                }
            }

            // --- KALKIŞLAR CARD ---
            if (state.departures.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(S.departures(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        state.departures.forEach { dep ->
                            val isSelected = state.selectedDeparture == dep
                            val bgColor = if (isSelected)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surface
                            val emoji = when (dep.typeTr) {
                                VehicleType.UBAHN.key -> "🚇"
                                VehicleType.SBAHN.key -> "🚆"
                                VehicleType.RERB.key -> "🚂"
                                VehicleType.FERNZUG.key -> "🚄"
                                VehicleType.STRASSENBAHN.key -> "🚋"
                                else -> "🚌"
                            }
                            Surface(
                                onClick = { viewModel.selectDeparture(dep) },
                                shape = RoundedCornerShape(10.dp),
                                color = bgColor,
                                tonalElevation = if (isSelected) 4.dp else 0.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(emoji, fontSize = 22.sp)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "${S.vehicleTypeName(dep.typeTr, lang)} • ${dep.line}",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            dep.direction,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        // Show transfer info only for the selected departure (trip is loaded)
                                        if (isSelected && state.trip != null) {
                                            val t = state.trip!!
                                            val transferText = if (t.segments.size <= 1) {
                                                "(${S.transferDirect(lang)})"
                                            } else {
                                                "(${S.transferCount(t.segments.size - 1, lang)})"
                                            }
                                            Text(
                                                transferText,
                                                fontWeight = FontWeight.SemiBold,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            dep.time,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        if (dep.track.isNotBlank()) {
                                            Text(
                                                "Gl. ${dep.track}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- PLAN BİLGİSİ ---
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
                        Text(S.plannedRoute(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (state.trip != null) {
                            TextButton(onClick = { viewModel.setEditingTimes(!state.isEditingTimes) }) {
                                Text(
                                    if (state.isEditingTimes) S.editDone(lang) else S.editEdit(lang),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    val t = state.trip
                    if (t != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(S.departure(lang), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(t.overallDep, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(S.arrival(lang), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(t.overallArr, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(S.duration(lang), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${t.durationMin} ${S.minutesShort(lang)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        if (state.isEditingTimes) {
                            t.segments.forEachIndexed { idx, s ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                ) {
                                    Text(s.line, modifier = Modifier.weight(0.4f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    OutlinedTextField(
                                        value = s.dep,
                                        onValueChange = { viewModel.updateSegmentDep(idx, it) },
                                        label = { Text(S.departure(lang)) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    OutlinedTextField(
                                        value = s.arr,
                                        onValueChange = { viewModel.updateSegmentArr(idx, it) },
                                        label = { Text(S.arrival(lang)) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                            }
                        } else {
                            t.segments.forEachIndexed { idx, s ->
                                val emoji = when (s.typeTr) {
                                    VehicleType.UBAHN.key -> "🚇"
                                    VehicleType.SBAHN.key -> "🚆"
                                    VehicleType.RERB.key -> "🚂"
                                    VehicleType.FERNZUG.key -> "🚄"
                                    VehicleType.STRASSENBAHN.key -> "🚋"
                                    else -> "🚌"
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(emoji, fontSize = 22.sp)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${S.vehicleTypeName(s.typeTr, lang)} • ${s.line}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        Text("${s.fromStop} ${s.dep} → ${s.toStop} ${s.arr}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (s.distanceKm > 0 || s.stopCount > 0) {
                                            Text(
                                                buildString {
                                                    if (s.stopCount > 0) append("📍 ${s.stopCount} ${S.stops(lang)}")
                                                    if (s.distanceKm > 0) {
                                                        if (s.stopCount > 0) append("  •  ")
                                                        append("${String.format("%.2f", s.distanceKm)} km")
                                                    }
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    // Change stop button (visible only after saving)
                                    if (state.segmentIds.isNotEmpty() && s.stopNames.size > 1) {
                                        IconButton(
                                            onClick = { viewModel.showChangeStopDialog(idx, "") },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Text("✏️", fontSize = 16.sp)
                                        }
                                    }
                                }
                                if (idx < t.segments.size - 1) {
                                    Spacer(Modifier.height(4.dp))
                                }
                            }
                        }
                    } else {
                        Text(S.noRouteYet(lang), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }

            // --- EK BİLGİLER CARD ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Title row with inline segment selector
                    val ekTrip = state.trip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(S.additionalInfo(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (ekTrip != null && ekTrip.segments.size > 1) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                IconButton(
                                    onClick = { viewModel.prevSegment() },
                                    enabled = state.selectedSegmentIndex > 0,
                                    modifier = Modifier.size(28.dp)
                                ) { Text("←", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (state.selectedSegmentIndex > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) }
                                Text(
                                    "${state.selectedSegmentIndex + 1}/${ekTrip.segments.size}",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                IconButton(
                                    onClick = { viewModel.nextSegment() },
                                    enabled = state.selectedSegmentIndex < ekTrip.segments.size - 1,
                                    modifier = Modifier.size(28.dp)
                                ) { Text("→", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (state.selectedSegmentIndex < ekTrip.segments.size - 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) }
                            }
                        }
                    }
                    // Show current segment info & transfer point
                    if (ekTrip != null && ekTrip.segments.size > 1) {
                        val ekSeg = ekTrip.segments.getOrNull(state.selectedSegmentIndex)
                        if (ekSeg != null) {
                            val ekEmoji = when (ekSeg.typeTr) {
                                VehicleType.UBAHN.key -> "🚇"; VehicleType.SBAHN.key -> "🚆"; VehicleType.RERB.key -> "🚂"; VehicleType.FERNZUG.key -> "🚄"; VehicleType.STRASSENBAHN.key -> "🚋"; else -> "🚌"
                            }
                            Text(
                                "$ekEmoji ${ekSeg.line} → ${ekSeg.toStop}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            // Transfer point info
                            val nextSeg = ekTrip.segments.getOrNull(state.selectedSegmentIndex + 1)
                            if (nextSeg != null) {
                                Text(
                                    "🔄 ${ekSeg.toStop} (${ekSeg.arr} → ${nextSeg.dep})",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }

                    // Per-segment values
                    val curHava = state.segmentHavaDurumu[state.selectedSegmentIndex] ?: "Bilinmiyor"
                    val curOtur = state.segmentOturabildim[state.selectedSegmentIndex] ?: false
                    val curBilet = state.segmentBiletKontrolu[state.selectedSegmentIndex] ?: false
                    val curNote = state.segmentNote[state.selectedSegmentIndex] ?: ""

                    // Hava Durumu
                    Box {
                        FilledTonalButton(
                            onClick = { viewModel.setHavaMenuOpen(true) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            val havaEmoji = S.weatherOptions.find { it.first == curHava }?.second ?: "❓"
                            Text("$havaEmoji  ${S.weatherLabel(lang)}: ${S.weatherName(curHava, lang)}", fontWeight = FontWeight.SemiBold)
                        }
                        DropdownMenu(expanded = state.havaMenuOpen, onDismissRequest = { viewModel.setHavaMenuOpen(false) }) {
                            S.weatherOptions.forEach { (key, emoji) ->
                                DropdownMenuItem(
                                    text = { Text("$emoji  ${S.weatherName(key, lang)}") },
                                    onClick = {
                                        viewModel.updateHavaDurumu(key)
                                        viewModel.setHavaMenuOpen(false)
                                    }
                                )
                            }
                        }
                    }

                    // Toggles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(S.seatedToggle(lang), style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = curOtur,
                            onCheckedChange = { viewModel.updateOturabildim(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(S.ticketControl(lang), style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = curBilet,
                            onCheckedChange = { viewModel.updateBiletKontrolu(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }

                    OutlinedTextField(
                        value = curNote,
                        onValueChange = { viewModel.updateNote(it) },
                        label = { Text(S.noteOptional(lang)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // --- KAYDET & TEMİZLE ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    enabled = (state.trip != null),
                    onClick = { viewModel.saveToSheets() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    val btnText = if (state.segmentIds.isEmpty()) S.saveToSheets(lang) else S.updateRecord(lang)
                    Text(btnText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                OutlinedButton(
                    onClick = { viewModel.clearForm() },
                    modifier = Modifier.weight(0.5f),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    Text(S.clearFormButton(lang), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }

            // --- BİNDİM / İNDİM ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Title row with inline segment selector
                    val tripForSelector = state.trip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(S.actualTimes(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (tripForSelector != null && tripForSelector.segments.size > 1) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                IconButton(
                                    onClick = { viewModel.prevSegment() },
                                    enabled = state.selectedSegmentIndex > 0,
                                    modifier = Modifier.size(28.dp)
                                ) { Text("←", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (state.selectedSegmentIndex > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) }
                                Text(
                                    "${state.selectedSegmentIndex + 1}/${tripForSelector.segments.size}",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                IconButton(
                                    onClick = { viewModel.nextSegment() },
                                    enabled = state.selectedSegmentIndex < tripForSelector.segments.size - 1,
                                    modifier = Modifier.size(28.dp)
                                ) { Text("→", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (state.selectedSegmentIndex < tripForSelector.segments.size - 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) }
                            }
                        }
                    }
                    if (tripForSelector != null && tripForSelector.segments.size > 1) {
                        val seg = tripForSelector.segments.getOrNull(state.selectedSegmentIndex)
                        if (seg != null) {
                            val emoji = when (seg.typeTr) {
                                VehicleType.UBAHN.key -> "🚇"
                                VehicleType.SBAHN.key -> "🚆"
                                VehicleType.RERB.key -> "🚂"
                                VehicleType.FERNZUG.key -> "🚄"
                                VehicleType.STRASSENBAHN.key -> "🚋"
                                else -> "🚌"
                            }
                            Text(
                                "$emoji ${seg.line} → ${seg.toStop}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Bindim
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = state.customBindimTime,
                            onValueChange = { viewModel.updateCustomBindimTime(it) },
                            label = { Text(S.time(lang)) },
                            placeholder = { Text(S.now(lang)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            visualTransformation = TimeVisualTransformation(),
                            trailingIcon = {
                                if (state.customBindimTime.isNotBlank()) {
                                    IconButton(onClick = { viewModel.clearCustomBindimTime() }) {
                                        Icon(Icons.Default.Clear, contentDescription = S.clear(lang))
                                    }
                                }
                            }
                        )
                        Button(
                            enabled = state.segmentIds.isNotEmpty(),
                            onClick = { viewModel.recordBindim() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) { Text(S.boarded(lang)) }
                    }

                    // İndim
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = state.customIndimTime,
                            onValueChange = { viewModel.updateCustomIndimTime(it) },
                            label = { Text(S.time(lang)) },
                            placeholder = { Text(S.now(lang)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            visualTransformation = TimeVisualTransformation(),
                            trailingIcon = {
                                if (state.customIndimTime.isNotBlank()) {
                                    IconButton(onClick = { viewModel.clearCustomIndimTime() }) {
                                        Icon(Icons.Default.Clear, contentDescription = S.clear(lang))
                                    }
                                }
                            }
                        )
                        Button(
                            enabled = state.segmentIds.isNotEmpty(),
                            onClick = { viewModel.recordIndim() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) { Text(S.alighted(lang)) }
                    }
                }
            }

            // --- DURUM KARTI ---
            val statusColor = when {
                state.status.contains("✅") -> SuccessGreen
                state.status.contains("Hata") || state.status.contains("Fehler") || state.status.contains("Error") -> ErrorRed
                state.status.contains("...") -> WarningAmber
                else -> MaterialTheme.colorScheme.secondary
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Column {
                        Text(S.statusLabel(lang), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                        Text(state.status, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // --- DURAK LİSTESİ ---
            val persistentStops = state.persistentStops
            if (persistentStops.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            S.allStopsTitle(lang),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        persistentStops.forEach { seg ->
                            if (seg.stopNames.isNotEmpty()) {
                                Text(
                                    S.lineStops(seg.line, lang),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                seg.stopNames.forEachIndexed { idx, name ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(start = 8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                        )
                                        Text(
                                            "${idx + 1}. $name",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            } // End of else (!isManualMode)

            Spacer(Modifier.height(12.dp))
            RmvFooter()
            Spacer(Modifier.height(16.dp))
        }
    }

    // ── Change Stop Dialog ──
    if (state.changeStopSegIdx >= 0) {
        val segIdx = state.changeStopSegIdx
        val trip = state.trip
        val seg = trip?.segments?.getOrNull(segIdx)
        if (seg != null && seg.stopNames.isNotEmpty()) {
            val mode = state.changeStopMode
            AlertDialog(
                onDismissRequest = { viewModel.dismissChangeStopDialog() },
                title = {
                    Text(
                        if (mode == "binis") S.changeBoardingStop(lang)
                        else if (mode == "inis") S.changeAlightingStop(lang)
                        else S.changeStop(lang),
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Mode selection if not yet chosen
                        if (mode.isBlank()) {
                            FilledTonalButton(
                                onClick = { viewModel.showChangeStopDialog(segIdx, "binis") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) { Text(S.changeBoardingStop(lang)) }
                            Spacer(Modifier.height(4.dp))
                            FilledTonalButton(
                                onClick = { viewModel.showChangeStopDialog(segIdx, "inis") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) { Text(S.changeAlightingStop(lang)) }
                        } else {
                            // Current value
                            val currentStop = if (mode == "binis") seg.fromStop else seg.toStop
                            val currentTime = if (mode == "binis") seg.dep else seg.arr
                            Text(
                                "${S.oldValue(lang)}: $currentStop ($currentTime)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(S.selectNewStop(lang), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))

                            // Stop list
                            seg.stopNames.forEachIndexed { stopIdx, name ->
                                val time = seg.stopTimes.getOrElse(stopIdx) { "" }
                                val isSelected = state.changeStopSelectedIdx == stopIdx
                                Surface(
                                    onClick = { viewModel.selectChangeStop(stopIdx) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surface,
                                    tonalElevation = if (isSelected) 4.dp else 0.dp
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "${stopIdx + 1}. $name",
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (time.isNotBlank()) {
                                            Text(
                                                time,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }

                            // Preview
                            if (state.changeStopSelectedIdx >= 0) {
                                val newName = seg.stopNames.getOrElse(state.changeStopSelectedIdx) { "" }
                                val newTime = seg.stopTimes.getOrElse(state.changeStopSelectedIdx) { "" }
                                Spacer(Modifier.height(8.dp))
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            "${S.oldValue(lang)}: $currentStop ($currentTime)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = ErrorRed
                                        )
                                        Text(
                                            "${S.newValue(lang)}: $newName" + if (newTime.isNotBlank()) " ($newTime)" else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = SuccessGreen
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    if (mode.isNotBlank() && state.changeStopSelectedIdx >= 0) {
                        Button(onClick = { viewModel.confirmChangeStop() }) {
                            Text(S.confirmChange(lang))
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissChangeStopDialog() }) {
                        Text(S.cancelChange(lang))
                    }
                }
            )
        }
    }

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

// ── Manual Log Form Composable ──
@Composable
fun ManualLogForm(
    state: com.example.toplutasima.viewmodel.RmvLogUiState,
    viewModel: com.example.toplutasima.viewmodel.RmvLogViewModel,
    lang: com.example.toplutasima.ui.AppLanguage
) {
    // 1. Tarih & Araç Card
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(S.dateTime(lang).replace("🕐  ", "") + " & " + S.vehicleTypes(lang).replace("🚏  ", ""), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            
            OutlinedTextField(
                value = state.date,
                onValueChange = { viewModel.updateDate(it) },
                label = { Text(S.date(lang)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Box {
                FilledTonalButton(
                    onClick = { viewModel.setManualTypeMenuOpen(true) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    val emoji = when (state.manualTypeTr) {
                        VehicleType.UBAHN.key -> "🚇"; VehicleType.SBAHN.key -> "🚆"; VehicleType.RERB.key -> "🚂"; VehicleType.FERNZUG.key -> "🚄"; VehicleType.STRASSENBAHN.key -> "🚋"; else -> "🚌"
                    }
                    Text("$emoji  ${S.vehicleTypeName(state.manualTypeTr, lang)}", fontWeight = FontWeight.SemiBold)
                }
                DropdownMenu(expanded = state.manualTypeMenuOpen, onDismissRequest = { viewModel.setManualTypeMenuOpen(false) }) {
                    VehicleType.allKeys.forEach { type ->
                        DropdownMenuItem(
                            text = { 
                                val emoji = when (type) { VehicleType.UBAHN.key -> "🚇"; VehicleType.SBAHN.key -> "🚆"; VehicleType.RERB.key -> "🚂"; VehicleType.FERNZUG.key -> "🚄"; VehicleType.STRASSENBAHN.key -> "🚋"; else -> "🚌" }
                                Text("$emoji  ${S.vehicleTypeName(type, lang)}") 
                            },
                            onClick = { viewModel.updateManualField("type", type) }
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.manualLine, onValueChange = { viewModel.updateManualField("line", it) },
                    label = { Text(S.colLine(lang)) }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = state.manualDirection, onValueChange = { viewModel.updateManualField("direction", it) },
                    label = { Text(S.directionLabel(lang)) }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp)
                )
            }
        }
    }

    // 2. Duraklar & Saatler
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(S.stopSelection(lang).replace("📍  ", ""), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            
            OutlinedTextField(
                value = state.manualBoardingStop, onValueChange = { viewModel.updateManualField("boardingStop", it) },
                label = { Text(S.boardingStop(lang)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = state.manualAlightingStop, onValueChange = { viewModel.updateManualField("alightingStop", it) },
                label = { Text(S.alightingStop(lang)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp)
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.manualPlannedDep, onValueChange = { viewModel.updateManualField("plannedDep", it) },
                    label = { Text("Plan. ${S.departure(lang)}") }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), visualTransformation = TimeVisualTransformation()
                )
                OutlinedTextField(
                    value = state.manualActualDep, onValueChange = { viewModel.updateManualField("actualDep", it) },
                    label = { Text("Gerçek ${S.departure(lang)}") }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), visualTransformation = TimeVisualTransformation()
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.manualPlannedArr, onValueChange = { viewModel.updateManualField("plannedArr", it) },
                    label = { Text("Plan. ${S.arrival(lang)}") }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), visualTransformation = TimeVisualTransformation()
                )
                OutlinedTextField(
                    value = state.manualActualArr, onValueChange = { viewModel.updateManualField("actualArr", it) },
                    label = { Text("Gerçek ${S.arrival(lang)}") }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), visualTransformation = TimeVisualTransformation()
                )
            }
        }
    }

    // 3. Ekstra
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(S.additionalInfo(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.manualDistance, onValueChange = { viewModel.updateManualField("distance", it) },
                    label = { Text(S.colDistance(lang)) }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = state.manualStopCount, onValueChange = { viewModel.updateManualField("stopCount", it) },
                    label = { Text(S.colStops(lang)) }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            Box {
                FilledTonalButton(
                    onClick = { viewModel.setManualWeatherMenuOpen(true) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    val havaEmoji = S.weatherOptions.find { it.first == state.manualWeather }?.second ?: "❓"
                    Text("$havaEmoji  ${S.weatherLabel(lang)}: ${S.weatherName(state.manualWeather, lang)}", fontWeight = FontWeight.SemiBold)
                }
                DropdownMenu(expanded = state.manualWeatherMenuOpen, onDismissRequest = { viewModel.setManualWeatherMenuOpen(false) }) {
                    S.weatherOptions.forEach { (key, emoji) ->
                        DropdownMenuItem(
                            text = { Text("$emoji  ${S.weatherName(key, lang)}") },
                            onClick = { viewModel.updateManualField("weather", key) }
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(S.seatedToggle(lang), style = MaterialTheme.typography.bodyMedium)
                Switch(checked = state.manualOturabildim, onCheckedChange = { viewModel.updateManualOtur(it) })
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(S.ticketControl(lang), style = MaterialTheme.typography.bodyMedium)
                Switch(checked = state.manualBiletKontrolu, onCheckedChange = { viewModel.updateManualBilet(it) })
            }

            OutlinedTextField(
                value = state.manualNote, onValueChange = { viewModel.updateManualField("note", it) },
                label = { Text(S.noteOptional(lang)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
            )
        }
    }

    // 4. Buttons
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { viewModel.saveManualRecord() },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(vertical = 14.dp),
            enabled = state.manualLine.isNotBlank() && state.manualBoardingStop.isNotBlank() && state.manualAlightingStop.isNotBlank()
        ) {
            val btnText = if (state.segmentIds.isEmpty()) S.saveToSheets(lang) else S.updateRecord(lang)
            Text(btnText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        OutlinedButton(
            onClick = { viewModel.clearForm() },
            modifier = Modifier.weight(0.5f),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            Text(S.clearFormButton(lang), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }

    // Status
    val statusColor = when {
        state.status.contains("✅") -> SuccessGreen
        state.status.contains("Hata") || state.status.contains("Fehler") || state.status.contains("Error") -> ErrorRed
        state.status.contains("...") -> WarningAmber
        else -> MaterialTheme.colorScheme.secondary
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(statusColor))
            Column {
                Text(S.statusLabel(lang), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                Text(state.status, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
