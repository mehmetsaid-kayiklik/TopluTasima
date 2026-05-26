package com.example.toplutasima.ui.screens.maintenance

import androidx.compose.runtime.Composable
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.S

@Composable
internal fun MigrationActionsSection(
    lang: AppLanguage,
    stripSecondsRunning: Boolean,
    stripSecondsResult: String,
    showStripSecondsDialog: Boolean,
    onRequestStripSeconds: () -> Unit,
    onConfirmStripSeconds: () -> Unit,
    onDismissStripSeconds: () -> Unit,
    yolSuresiRunning: Boolean,
    yolSuresiResult: String,
    showYolSuresiDialog: Boolean,
    onRequestYolSuresi: () -> Unit,
    onConfirmYolSuresi: () -> Unit,
    onDismissYolSuresi: () -> Unit,
    derivedFieldsRunning: Boolean,
    derivedFieldsResult: String,
    showDerivedFieldsDialog: Boolean,
    onRequestDerivedFields: () -> Unit,
    onConfirmDerivedFields: () -> Unit,
    onDismissDerivedFields: () -> Unit,
    yearMonthRunning: Boolean,
    yearMonthResult: String,
    showYearMonthDialog: Boolean,
    onRequestYearMonth: () -> Unit,
    onConfirmYearMonth: () -> Unit,
    onDismissYearMonth: () -> Unit,
    sortDateRunning: Boolean,
    sortDateResult: String,
    showSortDateDialog: Boolean,
    onRequestSortDate: () -> Unit,
    onConfirmSortDate: () -> Unit,
    onDismissSortDate: () -> Unit,
    distanceFieldsRunning: Boolean,
    distanceFieldsResult: String,
    showDistanceFieldsDialog: Boolean,
    onRequestDistanceFields: () -> Unit,
    onConfirmDistanceFields: () -> Unit,
    onDismissDistanceFields: () -> Unit,
    seatmateUuidRunning: Boolean,
    seatmateUuidResult: String,
    showSeatmateUuidDialog: Boolean,
    onRequestSeatmateUuid: () -> Unit,
    onConfirmSeatmateUuid: () -> Unit,
    onDismissSeatmateUuid: () -> Unit,
    earlyDeparturesRunning: Boolean,
    earlyDeparturesResult: String,
    showEarlyDeparturesDialog: Boolean,
    onRequestEarlyDepartures: () -> Unit,
    onConfirmEarlyDepartures: () -> Unit,
    onDismissEarlyDepartures: () -> Unit
) {
    MaintenanceActionCard(
        title = "⏰  Saat Temizleme",
        isRunning = stripSecondsRunning,
        runningText = S.stripSecondsRunning(lang),
        result = stripSecondsResult,
        buttonText = S.stripSecondsButton(lang),
        onClick = onRequestStripSeconds
    )
    if (showStripSecondsDialog) {
        MaintenanceConfirmDialog(
            title = S.stripSecondsConfirmTitle(lang),
            text = S.stripSecondsConfirmText(lang),
            confirmText = S.yes(lang),
            dismissText = S.cancel(lang),
            onConfirm = onConfirmStripSeconds,
            onDismiss = onDismissStripSeconds
        )
    }

    MaintenanceActionCard(
        title = "⏱️  " + S.migrateYolSuresiButton(lang).substring(3).trim(),
        isRunning = yolSuresiRunning,
        runningText = S.migrateYolSuresiRunning(lang),
        result = yolSuresiResult,
        buttonText = S.migrateYolSuresiButton(lang),
        onClick = onRequestYolSuresi
    )
    if (showYolSuresiDialog) {
        MaintenanceConfirmDialog(
            title = S.migrateYolSuresiConfirmTitle(lang),
            text = S.migrateYolSuresiConfirmText(lang),
            confirmText = S.yes(lang),
            dismissText = S.cancel(lang),
            onConfirm = onConfirmYolSuresi,
            onDismiss = onDismissYolSuresi
        )
    }

    MaintenanceActionCard(
        title = S.migrateDerivedFieldsButton(lang),
        description = "Kayit duzenlemede hesaplanan gun, ay/siralama, gecikme, sure ve mesafe alanlarini tum eski seyahatler icin yeniler.",
        isRunning = derivedFieldsRunning,
        runningText = S.migrateDerivedFieldsRunning(lang),
        result = derivedFieldsResult,
        buttonText = S.migrateDerivedFieldsButton(lang),
        onClick = onRequestDerivedFields
    )
    if (showDerivedFieldsDialog) {
        MaintenanceConfirmDialog(
            title = S.migrateDerivedFieldsConfirmTitle(lang),
            text = S.migrateDerivedFieldsConfirmText(lang),
            confirmText = S.yes(lang),
            dismissText = S.cancel(lang),
            onConfirm = onConfirmDerivedFields,
            onDismiss = onDismissDerivedFields
        )
    }

    MaintenanceActionCard(
        title = "📅  Ay Alanı Güncelleme",
        description = "Eski kayıtlara yearMonth (YYYY-MM) alanı ekler. Migration bir kez yapılması yeterlidir; bundan sonra ay bazlı sorgular çok daha hızlı çalışır.",
        isRunning = yearMonthRunning,
        runningText = S.migrateYearMonthRunning(lang),
        result = yearMonthResult,
        buttonText = S.migrateYearMonthButton(lang),
        onClick = onRequestYearMonth
    )
    if (showYearMonthDialog) {
        MaintenanceConfirmDialog(
            title = S.migrateYearMonthConfirmTitle(lang),
            text = S.migrateYearMonthConfirmText(lang),
            confirmText = S.yes(lang),
            dismissText = S.cancel(lang),
            onConfirm = onConfirmYearMonth,
            onDismiss = onDismissYearMonth
        )
    }

    MaintenanceActionCard(
        title = "📆  Sıralama Alanı Güncelleme",
        description = "Eski kayıtlara sortDate (YYYY-MM-DD) alanı ekler. Bu alan kronolojik sıralamayı düzeltir; ay/yıl geçişlerindeki sıra hatalarını giderir.",
        isRunning = sortDateRunning,
        runningText = S.migrateSortDateRunning(lang),
        result = sortDateResult,
        buttonText = S.migrateSortDateButton(lang),
        onClick = onRequestSortDate
    )
    if (showSortDateDialog) {
        MaintenanceConfirmDialog(
            title = S.migrateSortDateConfirmTitle(lang),
            text = S.migrateSortDateConfirmText(lang),
            confirmText = S.yes(lang),
            dismissText = S.cancel(lang),
            onConfirm = onConfirmSortDate,
            onDismiss = onDismissSortDate
        )
    }

    MaintenanceActionCard(
        title = "📏  Mesafe Alanları Güncelleme",
        description = "Eski kayıtlardaki mevcut mesafeyi ORS alanlarına kopyalar ve RMV mesafe alanlarını bekliyor olarak hazırlar. API çağrısı yapmaz.",
        isRunning = distanceFieldsRunning,
        runningText = S.migrateDistanceFieldsRunning(lang),
        result = distanceFieldsResult,
        buttonText = S.migrateDistanceFieldsButton(lang),
        onClick = onRequestDistanceFields
    )
    if (showDistanceFieldsDialog) {
        MaintenanceConfirmDialog(
            title = S.migrateDistanceFieldsConfirmTitle(lang),
            text = S.migrateDistanceFieldsConfirmText(lang),
            confirmText = S.yes(lang),
            dismissText = S.cancel(lang),
            onConfirm = onConfirmDistanceFields,
            onDismiss = onDismissDistanceFields
        )
    }

    MaintenanceActionCard(
        title = "👥  Yanıma Oturan Kişi UUID Güncelleme",
        description = "Eski kayıtlara seatmateUuid alanı ekler. Bu alan sayesinde yeni profil ve oturma ilişkileri sorunsuz çalışacaktır. Migration bir kez yapılması yeterlidir.",
        isRunning = seatmateUuidRunning,
        runningText = S.migrateSeatmateUuidRunning(lang),
        result = seatmateUuidResult,
        buttonText = S.migrateSeatmateUuidButton(lang),
        onClick = onRequestSeatmateUuid
    )
    if (showSeatmateUuidDialog) {
        MaintenanceConfirmDialog(
            title = S.migrateSeatmateUuidConfirmTitle(lang),
            text = S.migrateSeatmateUuidConfirmText(lang),
            confirmText = S.yes(lang),
            dismissText = S.cancel(lang),
            onConfirm = onConfirmSeatmateUuid,
            onDismiss = onDismissSeatmateUuid
        )
    }

    MaintenanceActionCard(
        title = "⏰  Erken Biniş Gecikmelerini Düzelt",
        description = "Gecikme değeri 0 kaydedilmiş ancak erken biniş olan seferlerin gecikme değerini günceller.",
        isRunning = earlyDeparturesRunning,
        runningText = S.migrateEarlyDeparturesRunning(lang),
        result = earlyDeparturesResult,
        buttonText = S.migrateEarlyDeparturesButton(lang),
        onClick = onRequestEarlyDepartures
    )
    if (showEarlyDeparturesDialog) {
        MaintenanceConfirmDialog(
            title = S.migrateEarlyDeparturesConfirmTitle(lang),
            text = S.migrateEarlyDeparturesConfirmText(lang),
            confirmText = S.yes(lang),
            dismissText = S.cancel(lang),
            onConfirm = onConfirmEarlyDepartures,
            onDismiss = onDismissEarlyDepartures
        )
    }
}
