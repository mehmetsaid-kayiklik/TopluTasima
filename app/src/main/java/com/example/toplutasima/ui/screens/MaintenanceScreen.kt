package com.example.toplutasima.ui.screens

import android.content.Context
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import com.example.toplutasima.TopluTasimaApp
import com.example.toplutasima.data.repository.LocalTripRepository
import com.example.toplutasima.network.firestore.FirestoreMigrationService
import com.example.toplutasima.network.firestore.FirestoreTripRemoteDataSource
import com.example.toplutasima.ui.LocaleManager
import com.example.toplutasima.ui.S
import com.example.toplutasima.ui.components.RmvFooter
import com.example.toplutasima.ui.screens.maintenance.MigrationActionsSection
import com.example.toplutasima.viewmodel.BulkUpdateViewModel
import com.example.toplutasima.viewmodel.SettingsViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel

@Composable
fun MaintenanceScreen(
    onBack: () -> Unit,
    settingsViewModel: SettingsViewModel
) {
    val lang = LocaleManager.currentLanguage
    val scope = rememberCoroutineScope()
    val bulkUpdateViewModel: BulkUpdateViewModel = koinViewModel()

    val context = LocalContext.current
    val app = context.applicationContext as TopluTasimaApp
    val tripRemoteDataSource = remember { FirestoreTripRemoteDataSource() }
    val migrationService = remember { FirestoreMigrationService() }
    val tripRepository = remember(context, tripRemoteDataSource) {
        LocalTripRepository(context.applicationContext, app.database.tripDao(), tripRemoteDataSource)
    }
    val prefs = remember { context.getSharedPreferences("maintenance_prefs", Context.MODE_PRIVATE) }

    // Dialog state for strip-seconds migration
    var showStripSecondsDialog by remember { mutableStateOf(false) }
    var stripSecondsRunning by remember { mutableStateOf(false) }
    var stripSecondsResult by remember { mutableStateOf("") }

    // Dialog state for yol suresi migration
    var showYolSuresiDialog by remember { mutableStateOf(false) }
    var yolSuresiRunning by remember { mutableStateOf(false) }
    var yolSuresiResult by remember { mutableStateOf("") }

    // Dialog state for derived fields migration
    var showDerivedFieldsDialog by remember { mutableStateOf(false) }
    var derivedFieldsRunning by remember { mutableStateOf(false) }
    var derivedFieldsResult by remember { mutableStateOf("") }

    // Dialog state for yearMonth migration
    var showYearMonthDialog by remember { mutableStateOf(false) }
    var yearMonthRunning by remember { mutableStateOf(false) }
    var yearMonthResult by remember { mutableStateOf("") }

    // Dialog state for sortDate migration
    var showSortDateDialog by remember { mutableStateOf(false) }
    var sortDateRunning by remember { mutableStateOf(false) }
    var sortDateResult by remember { mutableStateOf("") }

    // Dialog state for distance fields migration
    var showDistanceFieldsDialog by remember { mutableStateOf(false) }
    var distanceFieldsRunning by remember { mutableStateOf(false) }
    var distanceFieldsResult by remember { mutableStateOf("") }
    var showRmvMesafeResetDialog by remember { mutableStateOf(false) }

    // Dialog state for seatmate uuid migration
    var showSeatmateUuidDialog by remember { mutableStateOf(false) }
    var seatmateUuidRunning by remember { mutableStateOf(false) }
    var seatmateUuidResult by remember { mutableStateOf("") }

    // Dialog state for early departures migration
    var showEarlyDeparturesDialog by remember { mutableStateOf(false) }
    var earlyDeparturesRunning by remember { mutableStateOf(false) }
    var earlyDeparturesResult by remember { mutableStateOf("") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Header with back button ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = onBack) {
                Text(S.maintenanceBack(lang), fontWeight = FontWeight.SemiBold)
            }
            Text(
                S.maintenanceTitle(lang),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        MigrationActionsSection(
            lang = lang,
            stripSecondsRunning = stripSecondsRunning,
            stripSecondsResult = stripSecondsResult,
            showStripSecondsDialog = showStripSecondsDialog,
            onRequestStripSeconds = { showStripSecondsDialog = true },
            onDismissStripSeconds = { showStripSecondsDialog = false },
            onConfirmStripSeconds = {
                showStripSecondsDialog = false
                stripSecondsRunning = true
                stripSecondsResult = ""
                scope.launch {
                    try {
                        val count = withContext(Dispatchers.IO) {
                            val updated = migrationService.migrateStripSeconds()
                            tripRepository.syncFromFirestore(fullSync = true)
                            updated
                        }
                        stripSecondsResult = S.stripSecondsDone(count, lang)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        stripSecondsResult = "${S.stripSecondsFailed(lang)}: ${e.message}"
                    } finally {
                        stripSecondsRunning = false
                    }
                }
            },
            yolSuresiRunning = yolSuresiRunning,
            yolSuresiResult = yolSuresiResult,
            showYolSuresiDialog = showYolSuresiDialog,
            onRequestYolSuresi = { showYolSuresiDialog = true },
            onDismissYolSuresi = { showYolSuresiDialog = false },
            onConfirmYolSuresi = {
                showYolSuresiDialog = false
                yolSuresiRunning = true
                yolSuresiResult = ""
                scope.launch {
                    try {
                        val (count, total) = withContext(Dispatchers.IO) {
                            val result = migrationService.migrateYolSuresi()
                            tripRepository.syncFromFirestore(fullSync = true)
                            result
                        }
                        yolSuresiResult = S.migrateYolSuresiDone(count, total, lang)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        yolSuresiResult = "${S.stripSecondsFailed(lang)}: ${e.message}"
                    } finally {
                        yolSuresiRunning = false
                    }
                }
            },
            derivedFieldsRunning = derivedFieldsRunning,
            derivedFieldsResult = derivedFieldsResult,
            showDerivedFieldsDialog = showDerivedFieldsDialog,
            onRequestDerivedFields = { showDerivedFieldsDialog = true },
            onDismissDerivedFields = { showDerivedFieldsDialog = false },
            onConfirmDerivedFields = {
                showDerivedFieldsDialog = false
                derivedFieldsRunning = true
                derivedFieldsResult = ""
                scope.launch {
                    try {
                        val (count, total) = withContext(Dispatchers.IO) {
                            val result = migrationService.migrateDerivedFields()
                            tripRepository.syncFromFirestore(fullSync = true)
                            result
                        }
                        derivedFieldsResult = S.migrateDerivedFieldsDone(count, total, lang)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        derivedFieldsResult = "${S.stripSecondsFailed(lang)}: ${e.message}"
                    } finally {
                        derivedFieldsRunning = false
                    }
                }
            },
            yearMonthRunning = yearMonthRunning,
            yearMonthResult = yearMonthResult,
            showYearMonthDialog = showYearMonthDialog,
            onRequestYearMonth = { showYearMonthDialog = true },
            onDismissYearMonth = { showYearMonthDialog = false },
            onConfirmYearMonth = {
                showYearMonthDialog = false
                yearMonthRunning = true
                yearMonthResult = ""
                scope.launch {
                    try {
                        val (count, total) = withContext(Dispatchers.IO) {
                            val result = migrationService.migrateYearMonth()
                            tripRepository.syncFromFirestore(fullSync = true)
                            result
                        }
                        yearMonthResult = S.migrateYearMonthDone(count, total, lang)
                        prefs.edit().putBoolean("yearMonthMigrated", true).apply()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        yearMonthResult = "${S.stripSecondsFailed(lang)}: ${e.message}"
                    } finally {
                        yearMonthRunning = false
                    }
                }
            },
            sortDateRunning = sortDateRunning,
            sortDateResult = sortDateResult,
            showSortDateDialog = showSortDateDialog,
            onRequestSortDate = { showSortDateDialog = true },
            onDismissSortDate = { showSortDateDialog = false },
            onConfirmSortDate = {
                showSortDateDialog = false
                sortDateRunning = true
                sortDateResult = ""
                scope.launch {
                    try {
                        val (count, total) = withContext(Dispatchers.IO) {
                            val result = migrationService.migrateSortDate()
                            tripRepository.syncFromFirestore(fullSync = true)
                            result
                        }
                        sortDateResult = S.migrateSortDateDone(count, total, lang)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        sortDateResult = "${S.stripSecondsFailed(lang)}: ${e.message}"
                    } finally {
                        sortDateRunning = false
                    }
                }
            },
            distanceFieldsRunning = distanceFieldsRunning,
            distanceFieldsResult = distanceFieldsResult,
            showDistanceFieldsDialog = showDistanceFieldsDialog,
            onRequestDistanceFields = { showDistanceFieldsDialog = true },
            onDismissDistanceFields = { showDistanceFieldsDialog = false },
            onConfirmDistanceFields = {
                showDistanceFieldsDialog = false
                distanceFieldsRunning = true
                distanceFieldsResult = ""
                scope.launch {
                    try {
                        val (count, total) = withContext(Dispatchers.IO) {
                            val result = migrationService.migrateDistanceFields()
                            tripRepository.syncFromFirestore(fullSync = true)
                            result
                        }
                        distanceFieldsResult = S.migrateDistanceFieldsDone(count, total, lang)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        distanceFieldsResult = "${S.stripSecondsFailed(lang)}: ${e.message}"
                    } finally {
                        distanceFieldsRunning = false
                    }
                }
            },
            rmvMesafeBackfillRunning = settingsViewModel.isBackfillRunning,
            rmvMesafeBackfillProgress = settingsViewModel.backfillProgress,
            rmvMesafeBackfillResult = settingsViewModel.backfillResultMessage,
            onRunRmvMesafeBackfill = settingsViewModel::runMesafeBackfill,
            rmvMesafeResetRunning = settingsViewModel.isBackfillResetRunning,
            rmvMesafeResetResult = settingsViewModel.backfillResetResultMessage,
            showRmvMesafeResetDialog = showRmvMesafeResetDialog,
            onRequestRmvMesafeReset = { showRmvMesafeResetDialog = true },
            onDismissRmvMesafeReset = { showRmvMesafeResetDialog = false },
            onConfirmRmvMesafeReset = {
                showRmvMesafeResetDialog = false
                settingsViewModel.resetAllMesafeBackfillState()
            },
            seatmateUuidRunning = seatmateUuidRunning,
            seatmateUuidResult = seatmateUuidResult,
            showSeatmateUuidDialog = showSeatmateUuidDialog,
            onRequestSeatmateUuid = { showSeatmateUuidDialog = true },
            onDismissSeatmateUuid = { showSeatmateUuidDialog = false },
            onConfirmSeatmateUuid = {
                showSeatmateUuidDialog = false
                seatmateUuidRunning = true
                seatmateUuidResult = ""
                scope.launch {
                    try {
                        val (total, updated) = withContext(Dispatchers.IO) {
                            val result = migrationService.migrateSeatmateUuid()
                            tripRepository.syncFromFirestore(fullSync = true)
                            result
                        }
                        seatmateUuidResult = S.migrateSeatmateUuidDone(updated, total, lang)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        seatmateUuidResult = "${S.stripSecondsFailed(lang)}: ${e.message}"
                    } finally {
                        seatmateUuidRunning = false
                    }
                }
            },
            earlyDeparturesRunning = earlyDeparturesRunning,
            earlyDeparturesResult = earlyDeparturesResult,
            showEarlyDeparturesDialog = showEarlyDeparturesDialog,
            onRequestEarlyDepartures = { showEarlyDeparturesDialog = true },
            onDismissEarlyDepartures = { showEarlyDeparturesDialog = false },
            onConfirmEarlyDepartures = {
                showEarlyDeparturesDialog = false
                earlyDeparturesRunning = true
                earlyDeparturesResult = ""
                scope.launch {
                    try {
                        val (updated, total) = withContext(Dispatchers.IO) {
                            val result = migrationService.migrateEarlyDepartures()
                            tripRepository.syncFromFirestore(fullSync = true)
                            result
                        }
                        earlyDeparturesResult = S.migrateEarlyDeparturesDone(updated, total, lang)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        earlyDeparturesResult = "${S.stripSecondsFailed(lang)}: ${e.message}"
                    } finally {
                        earlyDeparturesRunning = false
                    }
                }
            }
        )
        var healthRunning by remember { mutableStateOf(false) }
        var autoFixRunning by remember { mutableStateOf(false) }
        var healthIssues by remember { mutableStateOf<List<com.example.toplutasima.usecase.DataHealthChecker.HealthIssue>?>(null) }
        var healthExpanded by remember { mutableStateOf(false) }
        var healthError by remember { mutableStateOf("") }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(S.dataHealthTitle(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                if (healthRunning || autoFixRunning) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Text(if (healthRunning) S.dataHealthRunning(lang) else "Düzeltmeler uygulanıyor...", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    if (healthError.isNotBlank()) {
                        Text(
                            healthError,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    // Show results if available
                    if (healthIssues != null) {
                        val issues = healthIssues!!
                        if (issues.isEmpty()) {
                            Text(
                                S.dataHealthNoIssues(lang),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                S.dataHealthIssuesFound(issues.size, lang),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )

                            // Summary by type
                            Text(
                                com.example.toplutasima.usecase.DataHealthChecker.summaryText(issues),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                softWrap = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Expand/collapse detail
                            TextButton(onClick = { healthExpanded = !healthExpanded }) {
                                Text(if (healthExpanded) S.incompleteHide(lang) else S.incompleteShowAll(lang))
                            }

                            if (healthExpanded) {
                                val grouped = com.example.toplutasima.usecase.DataHealthChecker.groupByType(issues)
                                for ((type, typeIssues) in grouped) {
                                    Text(
                                        "${com.example.toplutasima.usecase.DataHealthChecker.typeLabel(type)} (${typeIssues.size})",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                    for (issue in typeIssues.take(5)) {
                                        Text(
                                            "  ${issue.tripSummary}\n  ${issue.detail}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            softWrap = true,
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                        )
                                    }
                                    if (typeIssues.size > 5) {
                                        Text(
                                            "  ... +${typeIssues.size - 5} daha",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Bu işlem yalnızca zaman, süre, ay/sıra ve mesafe alanlarını yeniler; yinelenen veya eksik temel alanları raporda bırakır.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = {
                                    autoFixRunning = true
                                    healthError = ""
                                    scope.launch {
                                        try {
                                            withContext(Dispatchers.IO) {
                                                migrationService.migrateStripSeconds()
                                                migrationService.migrateYolSuresi()
                                                migrationService.migrateYearMonth()
                                                migrationService.migrateSortDate()
                                                migrationService.migrateDistanceFields()
                                                tripRepository.syncFromFirestore(fullSync = true)
                                            }
                                            prefs.edit().putBoolean("yearMonthMigrated", true).apply()
                                            // Re-run health check
                                            val trips = withContext(Dispatchers.IO) {
                                                tripRemoteDataSource.fetchTrips()
                                            }
                                            healthIssues = com.example.toplutasima.usecase.DataHealthChecker.analyzeTrips(trips)
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            healthIssues = null
                                            healthError = "Düzeltmeler başarısız: ${e.message}"
                                        } finally {
                                            autoFixRunning = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Text("Düzeltmeleri Çalıştır", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    Button(
                        onClick = {
                            healthRunning = true
                            healthIssues = null
                            healthError = ""
                            scope.launch {
                                try {
                                    val trips = withContext(Dispatchers.IO) {
                                        tripRemoteDataSource.fetchTrips()
                                    }
                                    healthIssues = com.example.toplutasima.usecase.DataHealthChecker.analyzeTrips(trips)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    healthIssues = null
                                    healthError = "Veri sağlığı kontrolü başarısız: ${e.message}"
                                } finally {
                                    healthRunning = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        Text(S.dataHealthRun(lang), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        BulkUpdateSection(viewModel = bulkUpdateViewModel)

        RmvFooter(modifier = Modifier.padding(vertical = 8.dp))

        Spacer(modifier = Modifier.height(32.dp))
    }
    }
}
