package com.example.toplutasima.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.toplutasima.ui.LocaleManager
import com.example.toplutasima.ui.components.RmvFooter
import com.example.toplutasima.ui.components.transit.TransitSyncReceipt
import com.example.toplutasima.ui.components.transit.TransitValidationSheet
import com.example.toplutasima.domain.transit.validation.TransitValidationField
import com.example.toplutasima.transit.TransitFeatureFlags
import com.example.toplutasima.ui.screens.rmvlog.ActualTimesSection
import com.example.toplutasima.ui.screens.rmvlog.AddFavoriteDialog
import com.example.toplutasima.ui.screens.rmvlog.AdditionalInfoSection
import com.example.toplutasima.ui.screens.rmvlog.ChangeStopDialog
import com.example.toplutasima.ui.screens.rmvlog.DateTimeSection
import com.example.toplutasima.ui.screens.rmvlog.DepartureSection
import com.example.toplutasima.ui.screens.rmvlog.JourneyMatchSection
import com.example.toplutasima.ui.screens.rmvlog.ManualLogForm
import com.example.toplutasima.ui.screens.rmvlog.PersistentStopsSection
import com.example.toplutasima.ui.screens.rmvlog.PlannedRouteSection
import com.example.toplutasima.ui.screens.rmvlog.RmvLogHeader
import com.example.toplutasima.ui.screens.rmvlog.RmvStatusCard
import com.example.toplutasima.ui.screens.rmvlog.SaveClearActionsSection
import com.example.toplutasima.ui.screens.rmvlog.StopSelectionSection
import com.example.toplutasima.viewmodel.PersonalTripViewModel
import com.example.toplutasima.viewmodel.RmvLogViewModel
import com.example.toplutasima.viewmodel.rmvlog.LogMode
import org.koin.androidx.compose.koinViewModel

@Composable
fun RMVLogScreen(
    modifier: Modifier = Modifier,
    viewModel: RmvLogViewModel = koinViewModel(),
    showPersonal: Boolean = false,
    onTogglePersonal: (Boolean) -> Unit = {}
) {
    val personalViewModel: PersonalTripViewModel = koinViewModel()
    val personalState by personalViewModel.uiState.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val validationState by viewModel.validationUiState.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()
    val lang = LocaleManager.currentLanguage
    val lifecycleOwner = LocalLifecycleOwner.current
    val destFocusRequester = remember { FocusRequester() }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshTransitServiceState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.loadActiveProfiles()
    }

    LaunchedEffect(state.segmentIds, state.selectedSegmentIndex) {
        if (state.segmentIds.isNotEmpty()) {
            viewModel.refreshTransitServiceState()
        }
    }

    LaunchedEffect(validationState.focusField) {
        val target = validationState.focusField ?: return@LaunchedEffect
        val scrollTarget = when (target) {
            TransitValidationField.BOARDING_STOP,
            TransitValidationField.ALIGHTING_STOP -> 0
            TransitValidationField.PLANNED_DEPARTURE,
            TransitValidationField.PLANNED_ARRIVAL,
            TransitValidationField.SEGMENTS -> scroll.maxValue / 2
            TransitValidationField.ACTUAL_DEPARTURE,
            TransitValidationField.ACTUAL_ARRIVAL,
            TransitValidationField.DISTANCE,
            TransitValidationField.RECORD -> scroll.maxValue
        }
        scroll.animateScrollTo(scrollTarget)
        viewModel.consumeValidationFocus()
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RmvLogHeader(
            state = state,
            showPersonal = showPersonal,
            lang = lang,
            onToggleMode = { viewModel.setMode(if (state.mode == LogMode.MANUAL) LogMode.AUTO else LogMode.MANUAL) },
            onTogglePersonal = { onTogglePersonal(!showPersonal) }
        )

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (showPersonal) {
                PersonalTripsContent(
                    uiState = personalState,
                    lang = lang,
                    viewModel = personalViewModel
                )
            } else if (state.isManualMode) {
                ManualLogForm(state, viewModel, lang)
            } else {
                var hasLocationPermission by remember { mutableStateOf(viewModel.hasLocationPermission()) }
                val locationPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { perms ->
                    hasLocationPermission =
                        perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                            viewModel.hasLocationPermission()
                }
                val requestLocationPermission = {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }

                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) {
                    viewModel.saveToSheets()
                }

                val activityRecognitionPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) {
                    viewModel.recordBindim()
                }

                LaunchedEffect(Unit) {
                    if (hasLocationPermission) {
                        viewModel.fetchNearbyStopsOnOpen()
                    }
                }

                StopSelectionSection(
                    state = state,
                    viewModel = viewModel,
                    lang = lang,
                    hasLocationPermission = hasLocationPermission,
                    onRequestLocationPermission = requestLocationPermission,
                    destFocusRequester = destFocusRequester
                )
                DateTimeSection(state, viewModel, lang)
                JourneyMatchSection(
                    state = state,
                    viewModel = viewModel,
                    hasLocationPermission = hasLocationPermission,
                    onRequestLocationPermission = requestLocationPermission
                )
                DepartureSection(state, viewModel, lang)
                PlannedRouteSection(state, viewModel, lang)
                AdditionalInfoSection(state, viewModel, lang)
                SaveClearActionsSection(
                    state = state,
                    viewModel = viewModel,
                    lang = lang,
                    onSaveClick = {
                        if (viewModel.needsNotificationPermission()) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.saveToSheets()
                        }
                    }
                )
                ActualTimesSection(
                    state = state,
                    viewModel = viewModel,
                    lang = lang,
                    onBoardedClick = {
                        if (viewModel.needsActivityRecognitionPermission()) {
                            activityRecognitionPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                        } else {
                            viewModel.recordBindim()
                        }
                    }
                )
                RmvStatusCard(state, lang)
                PersistentStopsSection(state, lang)
            }

            if (TransitFeatureFlags.SYNC_RECEIPTS && !showPersonal && state.segmentIds.isNotEmpty()) {
                TransitSyncReceipt(recordIds = state.segmentIds)
            }

            Spacer(Modifier.height(12.dp))
            RmvFooter()
            Spacer(Modifier.height(16.dp))
        }
    }

    ChangeStopDialog(state, viewModel, lang)
    AddFavoriteDialog(state, viewModel, lang)
    if (!showPersonal) {
        TransitValidationSheet(
            state = validationState,
            lang = lang,
            onDismiss = viewModel::dismissTransitValidation,
            onIssueSelected = viewModel::focusValidationIssue,
            onContinueWithWarnings = viewModel::continueAfterValidationWarnings
        )
    }
}
