package com.example.toplutasima.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.toplutasima.TopluTasimaApp
import com.example.toplutasima.data.repository.TripRepository
import com.example.toplutasima.network.FirestoreService
import com.example.toplutasima.ui.LocaleManager
import com.example.toplutasima.ui.S
import com.example.toplutasima.ui.components.RmvFooter
import com.example.toplutasima.viewmodel.BulkUpdateViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import android.content.Context
import androidx.compose.ui.platform.LocalContext

@Composable
fun MaintenanceScreen(
    onBack: () -> Unit
) {
    val lang = LocaleManager.currentLanguage
    val scope = rememberCoroutineScope()
    val bulkUpdateViewModel: BulkUpdateViewModel = koinViewModel()

    val context = LocalContext.current
    val app = context.applicationContext as TopluTasimaApp
    val tripRepository = remember(context) {
        TripRepository(context.applicationContext, app.database.tripDao())
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

    // Dialog state for seatmate uuid migration
    var showSeatmateUuidDialog by remember { mutableStateOf(false) }
    var seatmateUuidRunning by remember { mutableStateOf(false) }
    var seatmateUuidResult by remember { mutableStateOf("") }

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

        // ── Strip Seconds from Times Card ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("⏰  Saat Temizleme", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                if (stripSecondsRunning) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Text(S.stripSecondsRunning(lang), style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    if (stripSecondsResult.isNotBlank()) {
                        Text(
                            stripSecondsResult,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (stripSecondsResult.contains("✅")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                    Button(
                        onClick = { showStripSecondsDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        Text(S.stripSecondsButton(lang), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        if (showStripSecondsDialog) {
            AlertDialog(
                onDismissRequest = { showStripSecondsDialog = false },
                title = { Text(S.stripSecondsConfirmTitle(lang)) },
                text = { Text(S.stripSecondsConfirmText(lang)) },
                confirmButton = {
                    TextButton(onClick = {
                        showStripSecondsDialog = false
                        stripSecondsRunning = true
                        stripSecondsResult = ""
                        scope.launch {
                            try {
                                val count = withContext(Dispatchers.IO) {
                                    val updated = FirestoreService.migrateStripSeconds()
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
                    }) {
                        Text(S.yes(lang))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showStripSecondsDialog = false }) {
                        Text(S.cancel(lang))
                    }
                }
            )
        }

        // ── Yol Suresi Migration Card ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("⏱️  " + S.migrateYolSuresiButton(lang).substring(3).trim(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                if (yolSuresiRunning) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Text(S.migrateYolSuresiRunning(lang), style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    if (yolSuresiResult.isNotBlank()) {
                        Text(
                            yolSuresiResult,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (yolSuresiResult.contains("✅")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                    Button(
                        onClick = { showYolSuresiDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        Text(S.migrateYolSuresiButton(lang), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        if (showYolSuresiDialog) {
            AlertDialog(
                onDismissRequest = { showYolSuresiDialog = false },
                title = { Text(S.migrateYolSuresiConfirmTitle(lang)) },
                text = { Text(S.migrateYolSuresiConfirmText(lang)) },
                confirmButton = {
                    TextButton(onClick = {
                        showYolSuresiDialog = false
                        yolSuresiRunning = true
                        yolSuresiResult = ""
                        scope.launch {
                            try {
                                val (count, total) = withContext(Dispatchers.IO) {
                                    val result = FirestoreService.migrateYolSuresi()
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
                    }) {
                        Text(S.yes(lang))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showYolSuresiDialog = false }) {
                        Text(S.cancel(lang))
                    }
                }
            )
        }

        // ── YearMonth Migration Card ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("📅  Ay Alanı Güncelleme", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Eski kayıtlara yearMonth (YYYY-MM) alanı ekler. Migration bir kez yapılması yeterlidir; bundan sonra ay bazlı sorgular çok daha hızlı çalışır.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (yearMonthRunning) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Text(S.migrateYearMonthRunning(lang), style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    if (yearMonthResult.isNotBlank()) {
                        Text(
                            yearMonthResult,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (yearMonthResult.contains("✅")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                    Button(
                        onClick = { showYearMonthDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        Text(S.migrateYearMonthButton(lang), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        if (showYearMonthDialog) {
            AlertDialog(
                onDismissRequest = { showYearMonthDialog = false },
                title = { Text(S.migrateYearMonthConfirmTitle(lang)) },
                text = { Text(S.migrateYearMonthConfirmText(lang)) },
                confirmButton = {
                    TextButton(onClick = {
                        showYearMonthDialog = false
                        yearMonthRunning = true
                        yearMonthResult = ""
                        scope.launch {
                            try {
                                val (count, total) = withContext(Dispatchers.IO) {
                                    val result = FirestoreService.migrateYearMonth()
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
                    }) {
                        Text(S.yes(lang))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showYearMonthDialog = false }) {
                        Text(S.cancel(lang))
                    }
                }
            )
        }

        // ── SortDate Migration Card ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("📆  Sıralama Alanı Güncelleme", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Eski kayıtlara sortDate (YYYY-MM-DD) alanı ekler. Bu alan kronolojik sıralamayı düzeltir; ay/yıl geçişlerindeki sıra hatalarını giderir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (sortDateRunning) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Text(S.migrateSortDateRunning(lang), style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    if (sortDateResult.isNotBlank()) {
                        Text(
                            sortDateResult,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (sortDateResult.contains("✅")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                    Button(
                        onClick = { showSortDateDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        Text(S.migrateSortDateButton(lang), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        if (showSortDateDialog) {
            AlertDialog(
                onDismissRequest = { showSortDateDialog = false },
                title = { Text(S.migrateSortDateConfirmTitle(lang)) },
                text = { Text(S.migrateSortDateConfirmText(lang)) },
                confirmButton = {
                    TextButton(onClick = {
                        showSortDateDialog = false
                        sortDateRunning = true
                        sortDateResult = ""
                        scope.launch {
                            try {
                                val (count, total) = withContext(Dispatchers.IO) {
                                    val result = FirestoreService.migrateSortDate()
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
                    }) {
                        Text(S.yes(lang))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSortDateDialog = false }) {
                        Text(S.cancel(lang))
                    }
                }
            )
        }

        // ── Data Health Card ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("📏  Mesafe Alanları Güncelleme", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Eski kayıtlardaki mevcut mesafeyi ORS alanlarına kopyalar ve RMV mesafe alanlarını bekliyor olarak hazırlar. API çağrısı yapmaz.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (distanceFieldsRunning) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Text(S.migrateDistanceFieldsRunning(lang), style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    if (distanceFieldsResult.isNotBlank()) {
                        Text(
                            distanceFieldsResult,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (distanceFieldsResult.contains("✅")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                    Button(
                        onClick = { showDistanceFieldsDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        Text(S.migrateDistanceFieldsButton(lang), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        if (showDistanceFieldsDialog) {
            AlertDialog(
                onDismissRequest = { showDistanceFieldsDialog = false },
                title = { Text(S.migrateDistanceFieldsConfirmTitle(lang)) },
                text = { Text(S.migrateDistanceFieldsConfirmText(lang)) },
                confirmButton = {
                    TextButton(onClick = {
                        showDistanceFieldsDialog = false
                        distanceFieldsRunning = true
                        distanceFieldsResult = ""
                        scope.launch {
                            try {
                                val (count, total) = withContext(Dispatchers.IO) {
                                    val result = FirestoreService.migrateDistanceFields()
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
                    }) {
                        Text(S.yes(lang))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDistanceFieldsDialog = false }) {
                        Text(S.cancel(lang))
                    }
                }
            )
        }

        // ── Seatmate UUID Migration Card ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("👥  Yanıma Oturan Kişi UUID Güncelleme", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Eski kayıtlara seatmateUuid alanı ekler. Bu alan sayesinde yeni profil ve oturma ilişkileri sorunsuz çalışacaktır. Migration bir kez yapılması yeterlidir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (seatmateUuidRunning) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Text(S.migrateSeatmateUuidRunning(lang), style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    if (seatmateUuidResult.isNotBlank()) {
                        Text(
                            seatmateUuidResult,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (seatmateUuidResult.contains("✅")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                    Button(
                        onClick = { showSeatmateUuidDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        Text(S.migrateSeatmateUuidButton(lang), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        if (showSeatmateUuidDialog) {
            AlertDialog(
                onDismissRequest = { showSeatmateUuidDialog = false },
                title = { Text(S.migrateSeatmateUuidConfirmTitle(lang)) },
                text = { Text(S.migrateSeatmateUuidConfirmText(lang)) },
                confirmButton = {
                    TextButton(onClick = {
                        showSeatmateUuidDialog = false
                        seatmateUuidRunning = true
                        seatmateUuidResult = ""
                        scope.launch {
                            try {
                                val (total, updated) = withContext(Dispatchers.IO) {
                                    val result = FirestoreService.migrateSeatmateUuid()
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
                    }) {
                        Text(S.yes(lang))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSeatmateUuidDialog = false }) {
                        Text(S.cancel(lang))
                    }
                }
            )
        }

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
                                        "${com.example.toplutasima.usecase.DataHealthChecker.typeEmoji(type)} ${com.example.toplutasima.usecase.DataHealthChecker.typeLabel(type)} (${typeIssues.size})",
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
                                                FirestoreService.migrateStripSeconds()
                                                FirestoreService.migrateYolSuresi()
                                                FirestoreService.migrateYearMonth()
                                                FirestoreService.migrateSortDate()
                                                FirestoreService.migrateDistanceFields()
                                                tripRepository.syncFromFirestore(fullSync = true)
                                            }
                                            prefs.edit().putBoolean("yearMonthMigrated", true).apply()
                                            // Re-run health check
                                            val trips = withContext(Dispatchers.IO) {
                                                FirestoreService.fetchTrips()
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
                                        FirestoreService.fetchTrips()
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
