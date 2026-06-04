# TopluTasima Kod İnceleme Raporu

Kapsam: `app/src/main/java/com/example/toplutasima/`
Üretilme tarihi: 2026-06-04
Toplam Kotlin dosyası: 153

## 1. Dosya Envanteri

| Dosya | Satır | Sınıf | Fonksiyon | Paket |
|-------|-------|-------|-----------|-------|
| app/src/main/java/com/example/toplutasima/viewmodel/RmvLogViewModel.kt | 1864 | 1 | 92 | com.example.toplutasima.viewmodel |
| app/src/main/java/com/example/toplutasima/ui/screens/summary/TripStatsSection.kt | 755 | 0 | 4 | com.example.toplutasima.ui.screens.summary |
| app/src/main/java/com/example/toplutasima/ui/Strings.kt | 682 | 1 | 471 | com.example.toplutasima.ui |
| app/src/main/java/com/example/toplutasima/service/TransitTripForegroundService.kt | 677 | 1 | 32 | com.example.toplutasima.service |
| app/src/main/java/com/example/toplutasima/usecase/SummaryCalculator.kt | 652 | 9 | 11 | com.example.toplutasima.usecase |
| app/src/main/java/com/example/toplutasima/viewmodel/RecordsViewModel.kt | 552 | 1 | 20 | com.example.toplutasima.viewmodel |
| app/src/main/java/com/example/toplutasima/viewmodel/BulkUpdateViewModel.kt | 527 | 1 | 18 | com.example.toplutasima.viewmodel |
| app/src/main/java/com/example/toplutasima/ui/screens/MaintenanceScreen.kt | 525 | 0 | 1 | com.example.toplutasima.ui.screens |
| app/src/main/java/com/example/toplutasima/network/firestore/FirestoreTripRemoteDataSource.kt | 516 | 1 | 21 | com.example.toplutasima.network.firestore |
| app/src/main/java/com/example/toplutasima/ui/screens/records/DayListScreen.kt | 481 | 0 | 3 | com.example.toplutasima.ui.screens.records |
| app/src/main/java/com/example/toplutasima/ui/screens/records/EditRecordDialog.kt | 432 | 0 | 3 | com.example.toplutasima.ui.screens.records |
| app/src/main/java/com/example/toplutasima/network/firestore/FirestoreMigrationService.kt | 413 | 1 | 11 | com.example.toplutasima.network.firestore |
| app/src/main/java/com/example/toplutasima/ui/screens/records/RecordsFilterPanel.kt | 394 | 0 | 4 | com.example.toplutasima.ui.screens.records |
| app/src/main/java/com/example/toplutasima/viewmodel/PersonalTripViewModel.kt | 373 | 1 | 18 | com.example.toplutasima.viewmodel |
| app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/StopSelectionSection.kt | 370 | 0 | 2 | com.example.toplutasima.ui.screens.rmvlog |
| app/src/main/java/com/example/toplutasima/ui/screens/settings/DiagnosticsSection.kt | 349 | 0 | 2 | com.example.toplutasima.ui.screens.settings |
| app/src/main/java/com/example/toplutasima/network/rmv/RmvJourneyService.kt | 337 | 1 | 7 | com.example.toplutasima.network.rmv |
| app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/ManualLogForm.kt | 321 | 0 | 1 | com.example.toplutasima.ui.screens.rmvlog |
| app/src/main/java/com/example/toplutasima/ui/screens/settings/ThemeLanguageSettingsSection.kt | 320 | 0 | 2 | com.example.toplutasima.ui.screens.settings |
| app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/AdditionalInfoSection.kt | 317 | 0 | 3 | com.example.toplutasima.ui.screens.rmvlog |
| app/src/main/java/com/example/toplutasima/ui/screens/records/TripCard.kt | 311 | 0 | 5 | com.example.toplutasima.ui.screens.records |
| app/src/main/java/com/example/toplutasima/service/transit/TransitProximityTracker.kt | 309 | 1 | 18 | com.example.toplutasima.service.transit |
| app/src/main/java/com/example/toplutasima/ui/screens/records/MonthListScreen.kt | 300 | 0 | 2 | com.example.toplutasima.ui.screens.records |
| app/src/main/java/com/example/toplutasima/ui/components/AddPersonalTripDialog.kt | 298 | 0 | 1 | com.example.toplutasima.ui.components |
| app/src/main/java/com/example/toplutasima/data/PrefsManager.kt | 296 | 4 | 26 | com.example.toplutasima.data |
| app/src/main/java/com/example/toplutasima/network/rmv/RmvSegmentDetailsService.kt | 293 | 2 | 13 | com.example.toplutasima.network.rmv |
| app/src/main/java/com/example/toplutasima/ui/screens/PersonalTripsScreen.kt | 286 | 0 | 1 | com.example.toplutasima.ui.screens |
| app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/PlannedRouteSection.kt | 284 | 0 | 2 | com.example.toplutasima.ui.screens.rmvlog |
| app/src/main/java/com/example/toplutasima/viewmodel/SummaryViewModel.kt | 255 | 2 | 9 | com.example.toplutasima.viewmodel |
| app/src/main/java/com/example/toplutasima/service/PersonalTripForegroundService.kt | 254 | 1 | 12 | com.example.toplutasima.service |
| app/src/main/java/com/example/toplutasima/ui/screens/summary/DurationDelaySection.kt | 254 | 0 | 2 | com.example.toplutasima.ui.screens.summary |
| app/src/main/java/com/example/toplutasima/ui/screens/settings/FavoriteSettingsSection.kt | 249 | 0 | 1 | com.example.toplutasima.ui.screens.settings |
| app/src/main/java/com/example/toplutasima/network/rmv/RmvStopService.kt | 248 | 1 | 8 | com.example.toplutasima.network.rmv |
| app/src/main/java/com/example/toplutasima/ui/screens/PersonalSummaryContent.kt | 244 | 0 | 3 | com.example.toplutasima.ui.screens |
| app/src/main/java/com/example/toplutasima/network/rmv/RmvDistanceService.kt | 235 | 1 | 9 | com.example.toplutasima.network.rmv |
| app/src/main/java/com/example/toplutasima/usecase/TripPlanningUseCase.kt | 234 | 2 | 7 | com.example.toplutasima.usecase |
| app/src/main/java/com/example/toplutasima/repository/TransitRecordRepository.kt | 225 | 1 | 7 | com.example.toplutasima.repository |
| app/src/main/java/com/example/toplutasima/usecase/ExportUseCase.kt | 224 | 2 | 8 | com.example.toplutasima.usecase |
| app/src/main/java/com/example/toplutasima/model/Models.kt | 222 | 18 | 1 | com.example.toplutasima.model |
| app/src/main/java/com/example/toplutasima/network/RmvFeatureParsers.kt | 221 | 1 | 20 | com.example.toplutasima.network |
| app/src/main/java/com/example/toplutasima/ui/screens/maintenance/MigrationActionsSection.kt | 217 | 0 | 1 | com.example.toplutasima.ui.screens.maintenance |
| app/src/main/java/com/example/toplutasima/ui/screens/bulkupdate/BulkUpdateIdleContent.kt | 215 | 0 | 1 | com.example.toplutasima.ui.screens.bulkupdate |
| app/src/main/java/com/example/toplutasima/ui/screens/summary/ReportCardsSection.kt | 214 | 0 | 1 | com.example.toplutasima.ui.screens.summary |
| app/src/main/java/com/example/toplutasima/usecase/DataHealthChecker.kt | 212 | 3 | 8 | com.example.toplutasima.usecase |
| app/src/main/java/com/example/toplutasima/ui/components/PersonalTripCard.kt | 210 | 0 | 1 | com.example.toplutasima.ui.components |
| app/src/main/java/com/example/toplutasima/network/rmv/RmvSegmentParser.kt | 206 | 1 | 16 | com.example.toplutasima.network.rmv |
| app/src/main/java/com/example/toplutasima/data/repository/LocalTripRepository.kt | 204 | 1 | 15 | com.example.toplutasima.data.repository |
| app/src/main/java/com/example/toplutasima/ui/screens/RmvLogScreen.kt | 200 | 0 | 1 | com.example.toplutasima.ui.screens |
| app/src/main/java/com/example/toplutasima/ui/screens/summary/InsightSection.kt | 199 | 0 | 2 | com.example.toplutasima.ui.screens.summary |
| app/src/main/java/com/example/toplutasima/ui/screens/summary/LineDetailSheet.kt | 188 | 0 | 4 | com.example.toplutasima.ui.screens.summary |
| app/src/main/java/com/example/toplutasima/ui/navigation/MainAppScreen.kt | 186 | 0 | 2 | com.example.toplutasima.ui.navigation |
| app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/DepartureSection.kt | 186 | 0 | 2 | com.example.toplutasima.ui.screens.rmvlog |
| app/src/main/java/com/example/toplutasima/location/PersonalLocationHelper.kt | 181 | 1 | 6 | com.example.toplutasima.location |
| app/src/main/java/com/example/toplutasima/usecase/HeatmapUtils.kt | 180 | 3 | 3 | com.example.toplutasima.usecase |
| app/src/main/java/com/example/toplutasima/ui/components/HeatmapCalendar.kt | 176 | 0 | 1 | com.example.toplutasima.ui.components |
| app/src/main/java/com/example/toplutasima/ui/screens/settings/TransitNotificationSettingsSection.kt | 174 | 0 | 1 | com.example.toplutasima.ui.screens.settings |
| app/src/main/java/com/example/toplutasima/data/OfflineQueueStore.kt | 171 | 2 | 12 | com.example.toplutasima.data |
| app/src/main/java/com/example/toplutasima/service/transit/TransitReminderScheduler.kt | 171 | 2 | 4 | com.example.toplutasima.service.transit |
| app/src/main/java/com/example/toplutasima/usecase/TransitRecordCalculations.kt | 171 | 1 | 15 | com.example.toplutasima.usecase |
| app/src/main/java/com/example/toplutasima/ui/screens/RecordsScreen.kt | 168 | 0 | 1 | com.example.toplutasima.ui.screens |
| app/src/main/java/com/example/toplutasima/ui/screens/SummaryScreen.kt | 167 | 0 | 1 | com.example.toplutasima.ui.screens |
| app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/ActualTimesSection.kt | 163 | 0 | 1 | com.example.toplutasima.ui.screens.rmvlog |
| app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/ChangeStopDialog.kt | 162 | 0 | 1 | com.example.toplutasima.ui.screens.rmvlog |
| app/src/main/java/com/example/toplutasima/service/JourneyMatchForegroundService.kt | 161 | 1 | 11 | com.example.toplutasima.service |
| app/src/main/java/com/example/toplutasima/ui/screens/summary/SummaryHeaderSection.kt | 159 | 0 | 3 | com.example.toplutasima.ui.screens.summary |
| app/src/main/java/com/example/toplutasima/usecase/RecordFilterUtils.kt | 157 | 2 | 4 | com.example.toplutasima.usecase |
| app/src/main/java/com/example/toplutasima/ui/screens/summary/WeekdayWeekendCard.kt | 150 | 0 | 3 | com.example.toplutasima.ui.screens.summary |
| app/src/main/java/com/example/toplutasima/network/PersonalFirestoreService.kt | 148 | 1 | 13 | com.example.toplutasima.network |
| app/src/main/java/com/example/toplutasima/ui/screens/records/PersonalRecordsContent.kt | 147 | 0 | 2 | com.example.toplutasima.ui.screens.records |
| app/src/main/java/com/example/toplutasima/usecase/MonthComparisonUtils.kt | 146 | 2 | 4 | com.example.toplutasima.usecase |
| app/src/main/java/com/example/toplutasima/ui/theme/Theme.kt | 145 | 0 | 2 | com.example.toplutasima.ui.theme |
| app/src/main/java/com/example/toplutasima/ui/screens/summary/MonthlyTrendCard.kt | 144 | 0 | 1 | com.example.toplutasima.ui.screens.summary |
| app/src/main/java/com/example/toplutasima/network/rmv/RmvRetrofitClient.kt | 139 | 1 | 11 | com.example.toplutasima.network.rmv |
| app/src/main/java/com/example/toplutasima/network/RmvApiService.kt | 129 | 4 | 14 | com.example.toplutasima.network |
| app/src/main/java/com/example/toplutasima/service/transit/TransitServiceStateStore.kt | 126 | 4 | 5 | com.example.toplutasima.service.transit |
| app/src/main/java/com/example/toplutasima/service/TransitNotificationReceiver.kt | 120 | 1 | 5 | com.example.toplutasima.service |
| app/src/main/java/com/example/toplutasima/service/transit/TransitNotificationBuilder.kt | 117 | 2 | 4 | com.example.toplutasima.service.transit |
| app/src/main/java/com/example/toplutasima/diagnostics/TransitTrackerLogger.kt | 114 | 1 | 5 | com.example.toplutasima.diagnostics |
| app/src/main/java/com/example/toplutasima/auth/LoginActivity.kt | 111 | 1 | 3 | com.example.toplutasima.auth |
| app/src/main/java/com/example/toplutasima/service/transit/TransitActionIntents.kt | 110 | 2 | 5 | com.example.toplutasima.service.transit |
| app/src/main/java/com/example/toplutasima/network/ApiErrors.kt | 107 | 2 | 6 | com.example.toplutasima.network |
| app/src/main/java/com/example/toplutasima/viewmodel/rmvlog/RmvLogUiState.kt | 107 | 3 | 0 | com.example.toplutasima.viewmodel.rmvlog |
| app/src/main/java/com/example/toplutasima/ui/screens/bulkupdate/BulkUpdateRunningContent.kt | 103 | 0 | 1 | com.example.toplutasima.ui.screens.bulkupdate |
| app/src/main/java/com/example/toplutasima/location/NearbyStopsManager.kt | 99 | 1 | 4 | com.example.toplutasima.location |
| app/src/main/java/com/example/toplutasima/data/local/AppDatabase.kt | 96 | 1 | 7 | com.example.toplutasima.data.local |
| app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/RmvLogHeader.kt | 95 | 0 | 1 | com.example.toplutasima.ui.screens.rmvlog |
| app/src/main/java/com/example/toplutasima/ui/components/personaltrip/PersonalTripInfoRows.kt | 94 | 0 | 1 | com.example.toplutasima.ui.components.personaltrip |
| app/src/main/java/com/example/toplutasima/data/repository/TripMapper.kt | 93 | 0 | 2 | com.example.toplutasima.data.repository |
| app/src/main/java/com/example/toplutasima/network/firestore/FirestoreFavoriteDataSource.kt | 85 | 1 | 4 | com.example.toplutasima.network.firestore |
| app/src/main/java/com/example/toplutasima/repository/TripProfileLinkRepository.kt | 84 | 1 | 6 | com.example.toplutasima.repository |
| app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/JourneyMatchSection.kt | 81 | 0 | 1 | com.example.toplutasima.ui.screens.rmvlog |
| app/src/main/java/com/example/toplutasima/ui/screens/bulkupdate/BulkUpdateCounter.kt | 79 | 0 | 3 | com.example.toplutasima.ui.screens.bulkupdate |
| app/src/main/java/com/example/toplutasima/ui/components/personaltrip/PersonalTripStatusBadge.kt | 78 | 0 | 1 | com.example.toplutasima.ui.components.personaltrip |
| app/src/main/java/com/example/toplutasima/ui/screens/bulkupdate/BulkUpdateRateLimitedContent.kt | 78 | 0 | 1 | com.example.toplutasima.ui.screens.bulkupdate |
| app/src/main/java/com/example/toplutasima/viewmodel/SettingsViewModel.kt | 77 | 2 | 9 | com.example.toplutasima.viewmodel |
| app/src/main/java/com/example/toplutasima/usecase/ReportCardUtils.kt | 75 | 4 | 2 | com.example.toplutasima.usecase |
| app/src/main/java/com/example/toplutasima/ui/screens/bulkupdate/BulkUpdateDoneContent.kt | 73 | 0 | 1 | com.example.toplutasima.ui.screens.bulkupdate |
| app/src/main/java/com/example/toplutasima/di/AppModule.kt | 72 | 0 | 0 | com.example.toplutasima.di |
| app/src/main/java/com/example/toplutasima/ui/screens/maintenance/MaintenanceActionCard.kt | 72 | 0 | 1 | com.example.toplutasima.ui.screens.maintenance |
| app/src/main/java/com/example/toplutasima/ui/screens/BulkUpdateScreen.kt | 71 | 0 | 1 | com.example.toplutasima.ui.screens |
| app/src/main/java/com/example/toplutasima/viewmodel/records/RecordsUiState.kt | 71 | 3 | 0 | com.example.toplutasima.viewmodel.records |
| app/src/main/java/com/example/toplutasima/ui/screens/login/LoginScreen.kt | 69 | 0 | 1 | com.example.toplutasima.ui.screens.login |
| app/src/main/java/com/example/toplutasima/diagnostics/AppErrorReporter.kt | 68 | 1 | 8 | com.example.toplutasima.diagnostics |
| app/src/main/java/com/example/toplutasima/ui/components/SummaryCard.kt | 67 | 0 | 3 | com.example.toplutasima.ui.components |
| app/src/main/java/com/example/toplutasima/ui/screens/bulkupdate/BulkUpdatePausedContent.kt | 67 | 0 | 1 | com.example.toplutasima.ui.screens.bulkupdate |
| app/src/main/java/com/example/toplutasima/data/repository/ProfileSyncRepository.kt | 66 | 1 | 2 | com.example.toplutasima.data.repository |
| app/src/main/java/com/example/toplutasima/ui/components/RmvFooter.kt | 66 | 0 | 1 | com.example.toplutasima.ui.components |
| app/src/main/java/com/example/toplutasima/ui/theme/Color.kt | 65 | 0 | 0 | com.example.toplutasima.ui.theme |
| app/src/main/java/com/example/toplutasima/network/firestore/FirestorePersonService.kt | 64 | 2 | 4 | com.example.toplutasima.network.firestore |
| app/src/main/java/com/example/toplutasima/ui/screens/summary/ReportMetric.kt | 64 | 1 | 3 | com.example.toplutasima.ui.screens.summary |
| app/src/main/java/com/example/toplutasima/network/rmv/RmvDtos.kt | 63 | 7 | 0 | com.example.toplutasima.network.rmv |
| app/src/main/java/com/example/toplutasima/data/local/dao/TripDao.kt | 61 | 2 | 12 | com.example.toplutasima.data.local.dao |
| app/src/main/java/com/example/toplutasima/worker/TransitActionWorker.kt | 59 | 1 | 1 | com.example.toplutasima.worker |
| app/src/main/java/com/example/toplutasima/ui/screens/SettingsScreen.kt | 57 | 0 | 1 | com.example.toplutasima.ui.screens |
| app/src/main/java/com/example/toplutasima/usecase/RecordRowMapper.kt | 56 | 1 | 1 | com.example.toplutasima.usecase |
| app/src/main/java/com/example/toplutasima/data/local/entity/TripEntity.kt | 55 | 1 | 0 | com.example.toplutasima.data.local.entity |
| app/src/main/java/com/example/toplutasima/MainActivity.kt | 55 | 1 | 1 | com.example.toplutasima |
| app/src/main/java/com/example/toplutasima/repository/TripRecordMapper.kt | 55 | 1 | 1 | com.example.toplutasima.repository |
| app/src/main/java/com/example/toplutasima/model/PersonalTrip.kt | 53 | 1 | 0 | com.example.toplutasima.model |
| app/src/main/java/com/example/toplutasima/network/rmv/RmvTimeUtils.kt | 52 | 1 | 5 | com.example.toplutasima.network.rmv |
| app/src/main/java/com/example/toplutasima/repository/RmvTripRepository.kt | 51 | 1 | 8 | com.example.toplutasima.repository |
| app/src/main/java/com/example/toplutasima/TopluTasimaApp.kt | 50 | 1 | 1 | com.example.toplutasima |
| app/src/main/java/com/example/toplutasima/ui/components/personaltrip/PersonalTripActionRow.kt | 49 | 0 | 1 | com.example.toplutasima.ui.components.personaltrip |
| app/src/main/java/com/example/toplutasima/worker/PeriodicSyncWorker.kt | 47 | 1 | 1 | com.example.toplutasima.worker |
| app/src/main/java/com/example/toplutasima/data/AppEventBus.kt | 46 | 4 | 1 | com.example.toplutasima.data |
| app/src/main/java/com/example/toplutasima/network/StopNameUtils.kt | 37 | 1 | 2 | com.example.toplutasima.network |
| app/src/main/java/com/example/toplutasima/usecase/TransitTimeUtils.kt | 37 | 1 | 5 | com.example.toplutasima.usecase |
| app/src/main/java/com/example/toplutasima/model/TripStatus.kt | 36 | 2 | 4 | com.example.toplutasima.model |
| app/src/main/java/com/example/toplutasima/data/local/dao/ProfileDao.kt | 35 | 1 | 8 | com.example.toplutasima.data.local.dao |
| app/src/main/java/com/example/toplutasima/data/local/dao/TripProfileLinkDao.kt | 35 | 1 | 8 | com.example.toplutasima.data.local.dao |
| app/src/main/java/com/example/toplutasima/ui/theme/Type.kt | 34 | 0 | 0 | com.example.toplutasima.ui.theme |
| app/src/main/java/com/example/toplutasima/network/RmvEndpointAvailability.kt | 33 | 2 | 6 | com.example.toplutasima.network |
| app/src/main/java/com/example/toplutasima/ui/screens/maintenance/MaintenanceConfirmDialog.kt | 33 | 0 | 1 | com.example.toplutasima.ui.screens.maintenance |
| app/src/main/java/com/example/toplutasima/auth/AuthService.kt | 32 | 1 | 2 | com.example.toplutasima.auth |
| app/src/main/java/com/example/toplutasima/data/local/entity/TripProfileLinkEntity.kt | 32 | 1 | 0 | com.example.toplutasima.data.local.entity |
| app/src/main/java/com/example/toplutasima/network/ApiClient.kt | 30 | 1 | 2 | com.example.toplutasima.network |
| app/src/main/java/com/example/toplutasima/service/ActivityRecognitionReceiver.kt | 29 | 1 | 1 | com.example.toplutasima.service |
| app/src/main/java/com/example/toplutasima/viewmodel/bulkupdate/BulkUpdateUiState.kt | 29 | 3 | 0 | com.example.toplutasima.viewmodel.bulkupdate |
| app/src/main/java/com/example/toplutasima/repository/PersonalTripRepository.kt | 28 | 1 | 5 | com.example.toplutasima.repository |
| app/src/main/java/com/example/toplutasima/ui/screens/maintenance/MaintenanceResultBanner.kt | 27 | 0 | 1 | com.example.toplutasima.ui.screens.maintenance |
| app/src/main/java/com/example/toplutasima/viewmodel/personaltrip/PersonalTripUiState.kt | 27 | 1 | 0 | com.example.toplutasima.viewmodel.personaltrip |
| app/src/main/java/com/example/toplutasima/model/VehicleType.kt | 26 | 1 | 1 | com.example.toplutasima.model |
| app/src/main/java/com/example/toplutasima/ui/screens/bulkupdate/BulkUpdateLoadingContent.kt | 26 | 0 | 1 | com.example.toplutasima.ui.screens.bulkupdate |
| app/src/main/java/com/example/toplutasima/worker/OfflineSyncWorker.kt | 26 | 1 | 1 | com.example.toplutasima.worker |
| app/src/main/java/com/example/toplutasima/ui/components/TimeVisualTransformation.kt | 25 | 1 | 3 | com.example.toplutasima.ui.components |
| app/src/main/java/com/example/toplutasima/ui/LocaleManager.kt | 24 | 2 | 2 | com.example.toplutasima.ui |
| app/src/main/java/com/example/toplutasima/data/local/entity/ProfileEntity.kt | 22 | 1 | 0 | com.example.toplutasima.data.local.entity |
| app/src/main/java/com/example/toplutasima/ui/AppColors.kt | 19 | 0 | 0 | com.example.toplutasima.ui |
| app/src/main/java/com/example/toplutasima/model/FavoriteStop.kt | 17 | 3 | 0 | com.example.toplutasima.model |
| app/src/main/java/com/example/toplutasima/ui/util/VehicleIcon.kt | 14 | 0 | 1 | com.example.toplutasima.ui.util |
| app/src/main/java/com/example/toplutasima/ui/util/EmojiText.kt | 12 | 0 | 1 | com.example.toplutasima.ui.util |
| app/src/main/java/com/example/toplutasima/ui/util/ColorPreviewUtils.kt | 10 | 0 | 1 | com.example.toplutasima.ui.util |
| app/src/main/java/com/example/toplutasima/model/MonthSummary.kt | 9 | 1 | 0 | com.example.toplutasima.model |

## 2. Paket Dağılımı

(kök) -> 2 dosya, toplam 105 satır
auth -> 2 dosya, toplam 143 satır
data -> 3 dosya, toplam 513 satır
data/local -> 1 dosya, toplam 96 satır
data/local/dao -> 3 dosya, toplam 131 satır
data/local/entity -> 3 dosya, toplam 109 satır
data/repository -> 3 dosya, toplam 363 satır
di -> 1 dosya, toplam 72 satır
diagnostics -> 2 dosya, toplam 182 satır
location -> 2 dosya, toplam 280 satır
model -> 6 dosya, toplam 363 satır
network -> 7 dosya, toplam 705 satır
network/firestore -> 4 dosya, toplam 1078 satır
network/rmv -> 8 dosya, toplam 1573 satır
repository -> 5 dosya, toplam 443 satır
service -> 5 dosya, toplam 1241 satır
service/transit -> 5 dosya, toplam 833 satır
ui -> 3 dosya, toplam 725 satır
ui/components -> 6 dosya, toplam 842 satır
ui/components/personaltrip -> 3 dosya, toplam 221 satır
ui/navigation -> 1 dosya, toplam 186 satır
ui/screens -> 8 dosya, toplam 1718 satır
ui/screens/bulkupdate -> 7 dosya, toplam 641 satır
ui/screens/login -> 1 dosya, toplam 69 satır
ui/screens/maintenance -> 4 dosya, toplam 349 satır
ui/screens/records -> 6 dosya, toplam 2065 satır
ui/screens/rmvlog -> 9 dosya, toplam 1979 satır
ui/screens/settings -> 4 dosya, toplam 1092 satır
ui/screens/summary -> 9 dosya, toplam 2127 satır
ui/theme -> 3 dosya, toplam 244 satır
ui/util -> 3 dosya, toplam 36 satır
usecase -> 11 dosya, toplam 2144 satır
viewmodel -> 6 dosya, toplam 3648 satır
viewmodel/bulkupdate -> 1 dosya, toplam 29 satır
viewmodel/personaltrip -> 1 dosya, toplam 27 satır
viewmodel/records -> 1 dosya, toplam 71 satır
viewmodel/rmvlog -> 1 dosya, toplam 107 satır
worker -> 3 dosya, toplam 132 satır

## 3. Büyük Dosyalar (>300 satır)

### app/src/main/java/com/example/toplutasima/viewmodel/RmvLogViewModel.kt

- Boyut: 1864 satır, 1 deklarasyon, 92 fonksiyon.
- Rol: ViewModel/state yönetimi.
- Neden büyük: 92 fonksiyon var; 6 uzun fonksiyon var (selectDeparture: 83 satır, saveToSheets: 72 satır, restoreRecord: 120 satır); 24 yerel paket grubuna bağlı.
- Bölünebilir mi: Evet. UI state, event handling ve veri yükleme/validasyon akışları ayrı use-case veya state reducer yardımcılarına ayrılabilir.
- En uzun fonksiyonlar: restoreRecord (120), confirmChangeStop (105), fetchStopsForChangeStop (102), saveManualRecord (96), selectDeparture (83).
- Import yoğunluğu: 47 import; yerel:26, kotlinx.coroutines:11, java.time:3, android.content:2, androidx.lifecycle:2, java.util:2.

### app/src/main/java/com/example/toplutasima/ui/screens/summary/TripStatsSection.kt

- Boyut: 755 satır, 0 deklarasyon, 4 fonksiyon.
- Rol: UI/Compose ekranı.
- Neden büyük: 7 yerel paket grubuna bağlı.
- Bölünebilir mi: Evet. Büyük composable'lar (isDark, summarySurface, SummaryIndicator, LazyListScope) alt bileşenlere ve preview/state holder dosyalarına ayrılabilir.
- En uzun fonksiyonlar: isDark (3), summarySurface (3), SummaryIndicator (3), LazyListScope (2).
- Import yoğunluğu: 51 import; androidx.compose:34, yerel:17.

### app/src/main/java/com/example/toplutasima/ui/Strings.kt

- Boyut: 682 satır, 1 deklarasyon, 471 fonksiyon.
- Rol: UI/Compose ekranı.
- Neden büyük: 471 fonksiyon var.
- Bölünebilir mi: Evet. Büyük composable'lar (monthName, weatherName, dayName, delayBucketName) alt bileşenlere ve preview/state holder dosyalarına ayrılabilir.
- En uzun fonksiyonlar: monthName (15), weatherName (14), dayName (10), delayBucketName (8), transitReminderOption (8).

### app/src/main/java/com/example/toplutasima/service/TransitTripForegroundService.kt

- Boyut: 677 satır, 1 deklarasyon, 32 fonksiyon.
- Rol: Network/Firestore servisleri.
- Neden büyük: 32 fonksiyon var; 1 uzun fonksiyon var (onStartCommand: 128 satır); 7 yerel paket grubuna bağlı.
- Bölünebilir mi: Evet. Koleksiyon referansları, ortak query builder'lar ve alan mapper'ları ayrı Firestore data source/mapper dosyalarına ayrılabilir.
- En uzun fonksiyonlar: onStartCommand (128), startForegroundForCurrentState (31), scheduleReminderByType (28), createNotificationChannels (27), restoreServiceState (20).
- Import yoğunluğu: 26 import; yerel:10, android.app:4, androidx.work:3, android.content:2, android.os:2, java.time:2.

### app/src/main/java/com/example/toplutasima/usecase/SummaryCalculator.kt

- Boyut: 652 satır, 9 deklarasyon, 11 fonksiyon.
- Rol: Uygulama katmanı.
- Neden büyük: 9 deklarasyon aynı dosyada; 10 yerel paket grubuna bağlı.
- Bölünebilir mi: Evet. En büyük fonksiyonlar (computeMonthlyTrend, rowsForSheet, isWeekendTrip, lineDetailSlotKey) etrafında sorumluluk bazlı dosya ayrımı yapılabilir.
- En uzun fonksiyonlar: computeMonthlyTrend (58), rowsForSheet (15), isWeekendTrip (12), lineDetailSlotKey (10), timeSlotKey (10).
- Import yoğunluğu: 11 import; yerel:10, java.util:1.

### app/src/main/java/com/example/toplutasima/viewmodel/RecordsViewModel.kt

- Boyut: 552 satır, 1 deklarasyon, 20 fonksiyon.
- Rol: ViewModel/state yönetimi.
- Neden büyük: 20 fonksiyon var; 2 uzun fonksiyon var (selectMonth: 84 satır, updateRecord: 79 satır); 8 yerel paket grubuna bağlı.
- Bölünebilir mi: Evet. UI state, event handling ve veri yükleme/validasyon akışları ayrı use-case veya state reducer yardımcılarına ayrılabilir.
- En uzun fonksiyonlar: selectMonth (84), updateRecord (79), exportMonth (58), syncAndReload (33), runGlobalSearch (30).
- Import yoğunluğu: 29 import; yerel:14, kotlinx.coroutines:8, java.time:3, androidx.lifecycle:2, android.app:1, android.util:1.

### app/src/main/java/com/example/toplutasima/viewmodel/BulkUpdateViewModel.kt

- Boyut: 527 satır, 1 deklarasyon, 18 fonksiyon.
- Rol: ViewModel/state yönetimi.
- Neden büyük: 3 uzun fonksiyon var (startUpdate: 83 satır, resetAllDistanceAndStops: 95 satır, processRow: 87 satır); 9 yerel paket grubuna bağlı.
- Bölünebilir mi: Evet. UI state, event handling ve veri yükleme/validasyon akışları ayrı use-case veya state reducer yardımcılarına ayrılabilir.
- En uzun fonksiyonlar: resetAllDistanceAndStops (95), processRow (87), startUpdate (83), processStopNameRow (45), loadAllRows (26).
- Import yoğunluğu: 25 import; yerel:12, kotlinx.coroutines:8, androidx.lifecycle:2, android.app:1, android.util:1, java.util:1.

### app/src/main/java/com/example/toplutasima/ui/screens/MaintenanceScreen.kt

- Boyut: 525 satır, 0 deklarasyon, 1 fonksiyon.
- Rol: UI/Compose ekranı.
- Neden büyük: 8 yerel paket grubuna bağlı.
- Bölünebilir mi: Evet. Büyük composable'lar (MaintenanceScreen) alt bileşenlere ve preview/state holder dosyalarına ayrılabilir.
- En uzun fonksiyonlar: MaintenanceScreen (4).
- Import yoğunluğu: 48 import; androidx.compose:33, yerel:9, kotlinx.coroutines:4, android.content:1, org.koin:1.

### app/src/main/java/com/example/toplutasima/network/firestore/FirestoreTripRemoteDataSource.kt

- Boyut: 516 satır, 1 deklarasyon, 21 fonksiyon.
- Rol: Network/Firestore servisleri.
- Neden büyük: 21 fonksiyon var; Firestore sorgu/yazma akışı yoğun; 4 yerel paket grubuna bağlı.
- Bölünebilir mi: Evet. Koleksiyon referansları, ortak query builder'lar ve alan mapper'ları ayrı Firestore data source/mapper dosyalarına ayrılabilir.
- En uzun fonksiyonlar: updateTrip (48), updateActual (30), clearActual (30), fetchRowsForBulkUpdate (28), saveTrip (24).
- Import yoğunluğu: 8 import; yerel:4, kotlinx.coroutines:2, android.util:1, com.google:1.

### app/src/main/java/com/example/toplutasima/ui/screens/records/DayListScreen.kt

- Boyut: 481 satır, 0 deklarasyon, 3 fonksiyon.
- Rol: UI/Compose ekranı.
- Neden büyük: 6 yerel paket grubuna bağlı.
- Bölünebilir mi: Evet. Büyük composable'lar (DayListScreen, isDark, IncompleteRecordsBanner) alt bileşenlere ve preview/state holder dosyalarına ayrılabilir.
- En uzun fonksiyonlar: DayListScreen (4), isDark (3), IncompleteRecordsBanner (3).
- Import yoğunluğu: 78 import; androidx.compose:57, yerel:21.

### app/src/main/java/com/example/toplutasima/ui/screens/records/EditRecordDialog.kt

- Boyut: 432 satır, 0 deklarasyon, 3 fonksiyon.
- Rol: UI/Compose ekranı.
- Neden büyük: 5 yerel paket grubuna bağlı.
- Bölünebilir mi: Evet. Büyük composable'lar (openDatePicker, EditRecordDialog, DeleteRecordConfirmDialog) alt bileşenlere ve preview/state holder dosyalarına ayrılabilir.
- En uzun fonksiyonlar: openDatePicker (12), EditRecordDialog (4), DeleteRecordConfirmDialog (4).
- Import yoğunluğu: 50 import; androidx.compose:43, yerel:5, android.app:1, android.util:1.

### app/src/main/java/com/example/toplutasima/network/firestore/FirestoreMigrationService.kt

- Boyut: 413 satır, 1 deklarasyon, 11 fonksiyon.
- Rol: Network/Firestore servisleri.
- Neden büyük: 2 uzun fonksiyon var (migrateDerivedFields: 65 satır, migrateDistanceFields: 72 satır).
- Bölünebilir mi: Evet. Koleksiyon referansları, ortak query builder'lar ve alan mapper'ları ayrı Firestore data source/mapper dosyalarına ayrılabilir.
- En uzun fonksiyonlar: migrateDistanceFields (72), migrateDerivedFields (65), migrateYearMonth (47), migrateSeatmateUuid (43), migrateEarlyDepartures (38).
- Import yoğunluğu: 7 import; yerel:3, com.google:2, android.util:1, kotlinx.coroutines:1.

### app/src/main/java/com/example/toplutasima/ui/screens/records/RecordsFilterPanel.kt

- Boyut: 394 satır, 0 deklarasyon, 4 fonksiyon.
- Rol: UI/Compose ekranı.
- Neden büyük: satır sayısı daha çok veri/model tanımı ve yardımcı bloklardan geliyor.
- Bölünebilir mi: Evet. Büyük composable'lar (ExportFormatButton, ChipItem, FilterPanel, ActiveFilterBar) alt bileşenlere ve preview/state holder dosyalarına ayrılabilir.
- En uzun fonksiyonlar: ExportFormatButton (23), ChipItem (14), FilterPanel (4), ActiveFilterBar (3).
- Import yoğunluğu: 45 import; androidx.compose:42, yerel:3.

### app/src/main/java/com/example/toplutasima/viewmodel/PersonalTripViewModel.kt

- Boyut: 373 satır, 1 deklarasyon, 18 fonksiyon.
- Rol: ViewModel/state yönetimi.
- Neden büyük: 6 yerel paket grubuna bağlı.
- Bölünebilir mi: Evet. UI state, event handling ve veri yükleme/validasyon akışları ayrı use-case veya state reducer yardımcılarına ayrılabilir.
- En uzun fonksiyonlar: recordBindim (49), recordIndim (46), saveFromInlineForm (27), load (22), updateTrip (22).
- Import yoğunluğu: 22 import; kotlinx.coroutines:6, yerel:6, java.time:3, android.content:2, androidx.lifecycle:2, java.util:2.

### app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/StopSelectionSection.kt

- Boyut: 370 satır, 0 deklarasyon, 2 fonksiyon.
- Rol: UI/Compose ekranı.
- Neden büyük: 7 yerel paket grubuna bağlı.
- Bölünebilir mi: Evet. Büyük composable'lar (StopSelectionSection, AddFavoriteDialog) alt bileşenlere ve preview/state holder dosyalarına ayrılabilir.
- En uzun fonksiyonlar: StopSelectionSection (4), AddFavoriteDialog (4).
- Import yoğunluğu: 52 import; androidx.compose:45, yerel:7.

### app/src/main/java/com/example/toplutasima/ui/screens/settings/DiagnosticsSection.kt

- Boyut: 349 satır, 0 deklarasyon, 2 fonksiyon.
- Rol: UI/Compose ekranı.
- Neden büyük: 8 yerel paket grubuna bağlı.
- Bölünebilir mi: Evet. Büyük composable'lar (shareLogFile, DiagnosticsSection) alt bileşenlere ve preview/state holder dosyalarına ayrılabilir.
- En uzun fonksiyonlar: shareLogFile (29), DiagnosticsSection (4).
- Import yoğunluğu: 42 import; androidx.compose:33, yerel:8, java.io:1.

### app/src/main/java/com/example/toplutasima/network/rmv/RmvJourneyService.kt

- Boyut: 337 satır, 1 deklarasyon, 7 fonksiyon.
- Rol: Network/Firestore servisleri.
- Neden büyük: 10 yerel paket grubuna bağlı.
- Bölünebilir mi: Evet. Koleksiyon referansları, ortak query builder'lar ve alan mapper'ları ayrı Firestore data source/mapper dosyalarına ayrılabilir.
- En uzun fonksiyonlar: rmvCall (16), logE (6), logD (4), matchJourneyTrack (2), fetchDepartureBoard (2).
- Import yoğunluğu: 24 import; yerel:10, kotlinx.serialization:7, kotlinx.coroutines:3, org.json:2, android.util:1, java.time:1.

### app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/ManualLogForm.kt

- Boyut: 321 satır, 0 deklarasyon, 1 fonksiyon.
- Rol: UI/Compose ekranı.
- Neden büyük: 8 yerel paket grubuna bağlı.
- Bölünebilir mi: Evet. Büyük composable'lar (ManualLogForm) alt bileşenlere ve preview/state holder dosyalarına ayrılabilir.
- En uzun fonksiyonlar: ManualLogForm (3).
- Import yoğunluğu: 49 import; androidx.compose:41, yerel:8.

### app/src/main/java/com/example/toplutasima/ui/screens/settings/ThemeLanguageSettingsSection.kt

- Boyut: 320 satır, 0 deklarasyon, 2 fonksiyon.
- Rol: UI/Compose ekranı.
- Neden büyük: 7 yerel paket grubuna bağlı.
- Bölünebilir mi: Evet. Büyük composable'lar (ThemeLanguageSettingsSection, ColorPersonalSettingsSection) alt bileşenlere ve preview/state holder dosyalarına ayrılabilir.
- En uzun fonksiyonlar: ThemeLanguageSettingsSection (4), ColorPersonalSettingsSection (4).
- Import yoğunluğu: 48 import; androidx.compose:40, yerel:7, android.content:1.

### app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/AdditionalInfoSection.kt

- Boyut: 317 satır, 0 deklarasyon, 3 fonksiyon.
- Rol: UI/Compose ekranı.
- Neden büyük: 8 yerel paket grubuna bağlı.
- Bölünebilir mi: Evet. Büyük composable'lar (AdditionalInfoSection, SaveClearActionsSection, RmvStatusCard) alt bileşenlere ve preview/state holder dosyalarına ayrılabilir.
- En uzun fonksiyonlar: AdditionalInfoSection (4), SaveClearActionsSection (4), RmvStatusCard (4).
- Import yoğunluğu: 47 import; androidx.compose:39, yerel:8.

### app/src/main/java/com/example/toplutasima/ui/screens/records/TripCard.kt

- Boyut: 311 satır, 0 deklarasyon, 5 fonksiyon.
- Rol: UI/Compose ekranı.
- Neden büyük: 1 uzun fonksiyon var (TripCard: 180 satır); 4 yerel paket grubuna bağlı.
- Bölünebilir mi: Evet. Büyük composable'lar (TripCard, StatItem, StopBlock, TransitChip) alt bileşenlere ve preview/state holder dosyalarına ayrılabilir.
- En uzun fonksiyonlar: TripCard (180), StatItem (22), StopBlock (20), TransitChip (3), timeWindow (2).
- Import yoğunluğu: 48 import; androidx.compose:37, yerel:11.

### app/src/main/java/com/example/toplutasima/service/transit/TransitProximityTracker.kt

- Boyut: 309 satır, 1 deklarasyon, 18 fonksiyon.
- Rol: Network/Firestore servisleri.
- Neden büyük: 1 uzun fonksiyon var (start: 60 satır).
- Bölünebilir mi: Evet. Koleksiyon referansları, ortak query builder'lar ve alan mapper'ları ayrı Firestore data source/mapper dosyalarına ayrılabilir.
- En uzun fonksiyonlar: start (60), shouldAutoAlight (28), getCurrentLocationSuspend (20), requestActivityUpdates (19), updateActivityConfidence (16).
- Import yoğunluğu: 31 import; kotlinx.coroutines:11, com.google:5, kotlin.math:5, android.content:2, android.annotation:1, android.location:1.

## 4. Duplicate / Benzer Kod Tespiti

### Desen bazlı tekrarlar

- Firestore query/yazma mantığı: app/src/main/java/com/example/toplutasima/network/firestore/FirestoreTripRemoteDataSource.kt (14), app/src/main/java/com/example/toplutasima/network/firestore/FirestoreFavoriteDataSource.kt (7), app/src/main/java/com/example/toplutasima/network/PersonalFirestoreService.kt (6), app/src/main/java/com/example/toplutasima/network/firestore/FirestoreMigrationService.kt (5), app/src/main/java/com/example/toplutasima/network/firestore/FirestorePersonService.kt (5), app/src/main/java/com/example/toplutasima/data/repository/LocalTripRepository.kt (4)
- Room DAO / database pattern'i: app/src/main/java/com/example/toplutasima/data/local/AppDatabase.kt (2)
- Delay/duration/tarih hesapları: app/src/main/java/com/example/toplutasima/usecase/SummaryCalculator.kt (28), app/src/main/java/com/example/toplutasima/viewmodel/RmvLogViewModel.kt (17), app/src/main/java/com/example/toplutasima/network/rmv/RmvTimeUtils.kt (8), app/src/main/java/com/example/toplutasima/data/PrefsManager.kt (6), app/src/main/java/com/example/toplutasima/diagnostics/TransitTrackerLogger.kt (6), app/src/main/java/com/example/toplutasima/usecase/HeatmapUtils.kt (6), app/src/main/java/com/example/toplutasima/viewmodel/PersonalTripViewModel.kt (6), app/src/main/java/com/example/toplutasima/service/transit/TransitProximityTracker.kt (5) ...
- Utility benzeri format/parse/calculate fonksiyonlari: app/src/main/java/com/example/toplutasima/viewmodel/RmvLogViewModel.kt (45), app/src/main/java/com/example/toplutasima/network/rmv/RmvJourneyService.kt (16), app/src/main/java/com/example/toplutasima/network/RmvFeatureParsers.kt (16), app/src/main/java/com/example/toplutasima/usecase/MonthComparisonUtils.kt (16), app/src/main/java/com/example/toplutasima/network/rmv/RmvRetrofitClient.kt (11), app/src/main/java/com/example/toplutasima/network/rmv/RmvTimeUtils.kt (11), app/src/main/java/com/example/toplutasima/data/PrefsManager.kt (10), app/src/main/java/com/example/toplutasima/network/RmvApiService.kt (10) ...

### Normalize edilmis kopya bloklar

- Blok 1: 13 dosyada geçiyor -> app/src/main/java/com/example/toplutasima/ui/screens/MaintenanceScreen.kt:254, app/src/main/java/com/example/toplutasima/ui/screens/maintenance/MaintenanceActionCard.kt:11, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/ActualTimesSection.kt:8, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/AdditionalInfoSection.kt:7, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/DepartureSection.kt:7/50, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/JourneyMatchSection.kt:9, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/ManualLogForm.kt:7/43/81, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/PlannedRouteSection.kt:7/152, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/StopSelectionSection.kt:12/80, app/src/main/java/com/example/toplutasima/ui/screens/settings/DiagnosticsSection.kt:17, app/src/main/java/com/example/toplutasima/ui/screens/settings/FavoriteSettingsSection.kt:11, app/src/main/java/com/example/toplutasima/ui/screens/settings/ThemeLanguageSettingsSection.kt:14/81/132, app/src/main/java/com/example/toplutasima/ui/screens/settings/TransitNotificationSettingsSection.kt:5
  Ornek desen: `Card( | modifier = Modifier.fillMaxWidth(), | shape = RoundedCornerShape(0.dp),`
- Blok 2: 8 dosyada geçiyor -> app/src/main/java/com/example/toplutasima/ui/screens/PersonalTripsScreen.kt:14, app/src/main/java/com/example/toplutasima/ui/screens/maintenance/MaintenanceActionCard.kt:10, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/ActualTimesSection.kt:7, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/AdditionalInfoSection.kt:6, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/DepartureSection.kt:6, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/ManualLogForm.kt:6, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/PlannedRouteSection.kt:6, app/src/main/java/com/example/toplutasima/ui/screens/settings/TransitNotificationSettingsSection.kt:4
  Ornek desen: `) { | Card( | modifier = Modifier.fillMaxWidth(),`
- Blok 3: 7 dosyada geçiyor -> app/src/main/java/com/example/toplutasima/ui/components/PersonalTripCard.kt:51, app/src/main/java/com/example/toplutasima/ui/screens/BulkUpdateScreen.kt:6, app/src/main/java/com/example/toplutasima/ui/screens/PersonalTripsScreen.kt:17, app/src/main/java/com/example/toplutasima/ui/screens/records/RecordsFilterPanel.kt:11, app/src/main/java/com/example/toplutasima/ui/screens/summary/InsightSection.kt:12, app/src/main/java/com/example/toplutasima/ui/screens/summary/MonthlyTrendCard.kt:10, app/src/main/java/com/example/toplutasima/ui/screens/summary/WeekdayWeekendCard.kt:9
  Ornek desen: `shape = RoundedCornerShape(0.dp), | colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant) | ) {`
- Blok 4: 7 dosyada geçiyor -> app/src/main/java/com/example/toplutasima/ui/components/PersonalTripCard.kt:52, app/src/main/java/com/example/toplutasima/ui/screens/BulkUpdateScreen.kt:7, app/src/main/java/com/example/toplutasima/ui/screens/PersonalTripsScreen.kt:18, app/src/main/java/com/example/toplutasima/ui/screens/records/RecordsFilterPanel.kt:12, app/src/main/java/com/example/toplutasima/ui/screens/summary/InsightSection.kt:13, app/src/main/java/com/example/toplutasima/ui/screens/summary/MonthlyTrendCard.kt:11, app/src/main/java/com/example/toplutasima/ui/screens/summary/WeekdayWeekendCard.kt:10
  Ornek desen: `colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant) | ) { | Column(`
- Blok 5: 5 dosyada geçiyor -> app/src/main/java/com/example/toplutasima/ui/screens/PersonalTripsScreen.kt:19, app/src/main/java/com/example/toplutasima/ui/screens/summary/InsightSection.kt:14, app/src/main/java/com/example/toplutasima/ui/screens/summary/MonthlyTrendCard.kt:12, app/src/main/java/com/example/toplutasima/ui/screens/summary/TripStatsSection.kt:450, app/src/main/java/com/example/toplutasima/ui/screens/summary/WeekdayWeekendCard.kt:11
  Ornek desen: `) { | Column( | modifier = Modifier.padding(0.dp),`
- Blok 6: 4 dosyada geçiyor -> app/src/main/java/com/example/toplutasima/ui/components/AddPersonalTripDialog.kt:124, app/src/main/java/com/example/toplutasima/ui/screens/PersonalTripsScreen.kt:70, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/AdditionalInfoSection.kt:61, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/ManualLogForm.kt:102
  Ornek desen: `modifier = Modifier.fillMaxWidth(), | shape = RoundedCornerShape(0.dp), | colors = ButtonDefaults.filledTonalButtonColors(`
- Blok 7: 4 dosyada geçiyor -> app/src/main/java/com/example/toplutasima/ui/screens/MaintenanceScreen.kt:262, app/src/main/java/com/example/toplutasima/ui/screens/bulkupdate/BulkUpdateLoadingContent.kt:3, app/src/main/java/com/example/toplutasima/ui/screens/maintenance/MaintenanceActionCard.kt:25, app/src/main/java/com/example/toplutasima/ui/screens/settings/FavoriteSettingsSection.kt:65
  Ornek desen: `Row( | modifier = Modifier.fillMaxWidth(), | verticalAlignment = Alignment.CenterVertically,`
- Blok 8: 4 dosyada geçiyor -> app/src/main/java/com/example/toplutasima/ui/components/AddPersonalTripDialog.kt:125, app/src/main/java/com/example/toplutasima/ui/screens/PersonalTripsScreen.kt:71, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/AdditionalInfoSection.kt:62, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/ManualLogForm.kt:103
  Ornek desen: `shape = RoundedCornerShape(0.dp), | colors = ButtonDefaults.filledTonalButtonColors( | containerColor = MaterialTheme.colorScheme.primaryContainer,`
- Blok 9: 4 dosyada geçiyor -> app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/AdditionalInfoSection.kt:5, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/DepartureSection.kt:5, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/PlannedRouteSection.kt:5, app/src/main/java/com/example/toplutasima/ui/screens/settings/TransitNotificationSettingsSection.kt:3
  Ornek desen: `lang: AppLanguage | ) { | Card(`
- Blok 10: 4 dosyada geçiyor -> app/src/main/java/com/example/toplutasima/ui/components/personaltrip/PersonalTripStatusBadge.kt:15, app/src/main/java/com/example/toplutasima/ui/screens/PersonalTripsScreen.kt:128/156, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/AdditionalInfoSection.kt:198, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/ManualLogForm.kt:196
  Ornek desen: `Card( | modifier = Modifier.fillMaxWidth(), | shape = RoundedCornerShape(0.dp),`

Yorum: Desen taraması birebir kopyadan daha geniş bir sinyaldir; özellikle Firestore, Room ve tarih/formatlama akışları için ortak mapper/data-source/helper adaylarını gösterir.

## 5. Bağımlılık Analizi

### En çok import edilen yerel dosyalar

- app/src/main/java/com/example/toplutasima/ui/Strings.kt -> 46 dosya tarafindan import ediliyor (app/src/main/java/com/example/toplutasima/ui/components/AddPersonalTripDialog.kt, app/src/main/java/com/example/toplutasima/ui/components/HeatmapCalendar.kt, app/src/main/java/com/example/toplutasima/ui/components/PersonalTripCard.kt, app/src/main/java/com/example/toplutasima/ui/components/SummaryCard.kt, app/src/main/java/com/example/toplutasima/ui/components/personaltrip/PersonalTripActionRow.kt, app/src/main/java/com/example/toplutasima/ui/components/personaltrip/PersonalTripInfoRows.kt, app/src/main/java/com/example/toplutasima/ui/components/personaltrip/PersonalTripStatusBadge.kt, app/src/main/java/com/example/toplutasima/ui/navigation/MainAppScreen.kt, app/src/main/java/com/example/toplutasima/ui/screens/MaintenanceScreen.kt, app/src/main/java/com/example/toplutasima/ui/screens/PersonalSummaryContent.kt, app/src/main/java/com/example/toplutasima/ui/screens/PersonalTripsScreen.kt, app/src/main/java/com/example/toplutasima/ui/screens/RecordsScreen.kt, app/src/main/java/com/example/toplutasima/ui/screens/SettingsScreen.kt, app/src/main/java/com/example/toplutasima/ui/screens/SummaryScreen.kt, app/src/main/java/com/example/toplutasima/ui/screens/maintenance/MigrationActionsSection.kt, app/src/main/java/com/example/toplutasima/ui/screens/records/DayListScreen.kt, app/src/main/java/com/example/toplutasima/ui/screens/records/EditRecordDialog.kt, app/src/main/java/com/example/toplutasima/ui/screens/records/MonthListScreen.kt, app/src/main/java/com/example/toplutasima/ui/screens/records/PersonalRecordsContent.kt, app/src/main/java/com/example/toplutasima/ui/screens/records/RecordsFilterPanel.kt, app/src/main/java/com/example/toplutasima/ui/screens/records/TripCard.kt, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/ActualTimesSection.kt, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/AdditionalInfoSection.kt, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/ChangeStopDialog.kt, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/DepartureSection.kt, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/ManualLogForm.kt, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/PlannedRouteSection.kt, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/RmvLogHeader.kt, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/StopSelectionSection.kt, app/src/main/java/com/example/toplutasima/ui/screens/settings/DiagnosticsSection.kt, app/src/main/java/com/example/toplutasima/ui/screens/settings/FavoriteSettingsSection.kt, app/src/main/java/com/example/toplutasima/ui/screens/settings/ThemeLanguageSettingsSection.kt, app/src/main/java/com/example/toplutasima/ui/screens/settings/TransitNotificationSettingsSection.kt, app/src/main/java/com/example/toplutasima/ui/screens/summary/DurationDelaySection.kt, app/src/main/java/com/example/toplutasima/ui/screens/summary/InsightSection.kt, app/src/main/java/com/example/toplutasima/ui/screens/summary/LineDetailSheet.kt, app/src/main/java/com/example/toplutasima/ui/screens/summary/MonthlyTrendCard.kt, app/src/main/java/com/example/toplutasima/ui/screens/summary/ReportCardsSection.kt, app/src/main/java/com/example/toplutasima/ui/screens/summary/ReportMetric.kt, app/src/main/java/com/example/toplutasima/ui/screens/summary/SummaryHeaderSection.kt, app/src/main/java/com/example/toplutasima/ui/screens/summary/TripStatsSection.kt, app/src/main/java/com/example/toplutasima/ui/screens/summary/WeekdayWeekendCard.kt, app/src/main/java/com/example/toplutasima/usecase/ExportUseCase.kt, app/src/main/java/com/example/toplutasima/usecase/MonthComparisonUtils.kt, app/src/main/java/com/example/toplutasima/viewmodel/RmvLogViewModel.kt, app/src/main/java/com/example/toplutasima/viewmodel/SummaryViewModel.kt)
- app/src/main/java/com/example/toplutasima/ui/LocaleManager.kt -> 42 dosya tarafindan import ediliyor (app/src/main/java/com/example/toplutasima/TopluTasimaApp.kt, app/src/main/java/com/example/toplutasima/ui/components/AddPersonalTripDialog.kt, app/src/main/java/com/example/toplutasima/ui/components/HeatmapCalendar.kt, app/src/main/java/com/example/toplutasima/ui/components/PersonalTripCard.kt, app/src/main/java/com/example/toplutasima/ui/components/SummaryCard.kt, app/src/main/java/com/example/toplutasima/ui/components/personaltrip/PersonalTripActionRow.kt, app/src/main/java/com/example/toplutasima/ui/components/personaltrip/PersonalTripInfoRows.kt, app/src/main/java/com/example/toplutasima/ui/components/personaltrip/PersonalTripStatusBadge.kt, app/src/main/java/com/example/toplutasima/ui/navigation/MainAppScreen.kt, app/src/main/java/com/example/toplutasima/ui/screens/MaintenanceScreen.kt, app/src/main/java/com/example/toplutasima/ui/screens/PersonalSummaryContent.kt, app/src/main/java/com/example/toplutasima/ui/screens/PersonalTripsScreen.kt, app/src/main/java/com/example/toplutasima/ui/screens/RecordsScreen.kt, app/src/main/java/com/example/toplutasima/ui/screens/RmvLogScreen.kt, app/src/main/java/com/example/toplutasima/ui/screens/SettingsScreen.kt, app/src/main/java/com/example/toplutasima/ui/screens/SummaryScreen.kt, app/src/main/java/com/example/toplutasima/ui/screens/maintenance/MigrationActionsSection.kt, app/src/main/java/com/example/toplutasima/ui/screens/records/TripCard.kt, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/ActualTimesSection.kt, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/AdditionalInfoSection.kt, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/ChangeStopDialog.kt, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/DepartureSection.kt, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/PlannedRouteSection.kt, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/RmvLogHeader.kt, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/StopSelectionSection.kt, app/src/main/java/com/example/toplutasima/ui/screens/settings/DiagnosticsSection.kt, app/src/main/java/com/example/toplutasima/ui/screens/settings/FavoriteSettingsSection.kt, app/src/main/java/com/example/toplutasima/ui/screens/settings/ThemeLanguageSettingsSection.kt, app/src/main/java/com/example/toplutasima/ui/screens/settings/TransitNotificationSettingsSection.kt, app/src/main/java/com/example/toplutasima/ui/screens/summary/DurationDelaySection.kt, app/src/main/java/com/example/toplutasima/ui/screens/summary/InsightSection.kt, app/src/main/java/com/example/toplutasima/ui/screens/summary/LineDetailSheet.kt, app/src/main/java/com/example/toplutasima/ui/screens/summary/MonthlyTrendCard.kt, app/src/main/java/com/example/toplutasima/ui/screens/summary/ReportCardsSection.kt, app/src/main/java/com/example/toplutasima/ui/screens/summary/ReportMetric.kt, app/src/main/java/com/example/toplutasima/ui/screens/summary/SummaryHeaderSection.kt, app/src/main/java/com/example/toplutasima/ui/screens/summary/TripStatsSection.kt, app/src/main/java/com/example/toplutasima/ui/screens/summary/WeekdayWeekendCard.kt, app/src/main/java/com/example/toplutasima/usecase/ExportUseCase.kt, app/src/main/java/com/example/toplutasima/usecase/MonthComparisonUtils.kt, app/src/main/java/com/example/toplutasima/viewmodel/RmvLogViewModel.kt, app/src/main/java/com/example/toplutasima/viewmodel/SummaryViewModel.kt)
- app/src/main/java/com/example/toplutasima/model/Models.kt -> 28 dosya tarafindan import ediliyor (app/src/main/java/com/example/toplutasima/data/AppEventBus.kt, app/src/main/java/com/example/toplutasima/data/PrefsManager.kt, app/src/main/java/com/example/toplutasima/data/repository/LocalTripRepository.kt, app/src/main/java/com/example/toplutasima/network/RmvApiService.kt, app/src/main/java/com/example/toplutasima/network/RmvFeatureParsers.kt, app/src/main/java/com/example/toplutasima/network/firestore/FirestoreTripRemoteDataSource.kt, app/src/main/java/com/example/toplutasima/network/rmv/RmvJourneyService.kt, app/src/main/java/com/example/toplutasima/network/rmv/RmvSegmentDetailsService.kt, app/src/main/java/com/example/toplutasima/network/rmv/RmvSegmentParser.kt, app/src/main/java/com/example/toplutasima/network/rmv/RmvStopService.kt, app/src/main/java/com/example/toplutasima/repository/RmvTripRepository.kt, app/src/main/java/com/example/toplutasima/repository/TransitRecordRepository.kt, app/src/main/java/com/example/toplutasima/repository/TripRecordMapper.kt, app/src/main/java/com/example/toplutasima/ui/navigation/MainAppScreen.kt, app/src/main/java/com/example/toplutasima/ui/screens/summary/DurationDelaySection.kt, app/src/main/java/com/example/toplutasima/ui/screens/summary/InsightSection.kt, app/src/main/java/com/example/toplutasima/ui/screens/summary/MonthlyTrendCard.kt, app/src/main/java/com/example/toplutasima/ui/screens/summary/TripStatsSection.kt, app/src/main/java/com/example/toplutasima/ui/screens/summary/WeekdayWeekendCard.kt, app/src/main/java/com/example/toplutasima/usecase/MonthComparisonUtils.kt, app/src/main/java/com/example/toplutasima/usecase/ReportCardUtils.kt, app/src/main/java/com/example/toplutasima/usecase/SummaryCalculator.kt, app/src/main/java/com/example/toplutasima/usecase/TripPlanningUseCase.kt, app/src/main/java/com/example/toplutasima/viewmodel/BulkUpdateViewModel.kt, app/src/main/java/com/example/toplutasima/viewmodel/RmvLogViewModel.kt, app/src/main/java/com/example/toplutasima/viewmodel/SummaryViewModel.kt, app/src/main/java/com/example/toplutasima/viewmodel/bulkupdate/BulkUpdateUiState.kt, app/src/main/java/com/example/toplutasima/viewmodel/rmvlog/RmvLogUiState.kt)
- app/src/main/java/com/example/toplutasima/data/PrefsManager.kt -> 14 dosya tarafindan import ediliyor (app/src/main/java/com/example/toplutasima/MainActivity.kt, app/src/main/java/com/example/toplutasima/TopluTasimaApp.kt, app/src/main/java/com/example/toplutasima/service/PersonalTripForegroundService.kt, app/src/main/java/com/example/toplutasima/service/TransitTripForegroundService.kt, app/src/main/java/com/example/toplutasima/service/transit/TransitProximityTracker.kt, app/src/main/java/com/example/toplutasima/service/transit/TransitReminderScheduler.kt, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/JourneyMatchSection.kt, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/StopSelectionSection.kt, app/src/main/java/com/example/toplutasima/ui/screens/settings/DiagnosticsSection.kt, app/src/main/java/com/example/toplutasima/ui/screens/settings/FavoriteSettingsSection.kt, app/src/main/java/com/example/toplutasima/ui/screens/settings/ThemeLanguageSettingsSection.kt, app/src/main/java/com/example/toplutasima/ui/screens/settings/TransitNotificationSettingsSection.kt, app/src/main/java/com/example/toplutasima/viewmodel/RmvLogViewModel.kt, app/src/main/java/com/example/toplutasima/viewmodel/SettingsViewModel.kt)
- app/src/main/java/com/example/toplutasima/model/PersonalTrip.kt -> 12 dosya tarafindan import ediliyor (app/src/main/java/com/example/toplutasima/network/PersonalFirestoreService.kt, app/src/main/java/com/example/toplutasima/repository/PersonalTripRepository.kt, app/src/main/java/com/example/toplutasima/ui/components/AddPersonalTripDialog.kt, app/src/main/java/com/example/toplutasima/ui/components/PersonalTripCard.kt, app/src/main/java/com/example/toplutasima/ui/components/personaltrip/PersonalTripActionRow.kt, app/src/main/java/com/example/toplutasima/ui/components/personaltrip/PersonalTripInfoRows.kt, app/src/main/java/com/example/toplutasima/ui/components/personaltrip/PersonalTripStatusBadge.kt, app/src/main/java/com/example/toplutasima/ui/screens/PersonalSummaryContent.kt, app/src/main/java/com/example/toplutasima/ui/screens/PersonalTripsScreen.kt, app/src/main/java/com/example/toplutasima/ui/screens/records/PersonalRecordsContent.kt, app/src/main/java/com/example/toplutasima/viewmodel/PersonalTripViewModel.kt, app/src/main/java/com/example/toplutasima/viewmodel/personaltrip/PersonalTripUiState.kt)
- app/src/main/java/com/example/toplutasima/model/VehicleType.kt -> 11 dosya tarafindan import ediliyor (app/src/main/java/com/example/toplutasima/network/rmv/RmvJourneyService.kt, app/src/main/java/com/example/toplutasima/network/rmv/RmvSegmentDetailsService.kt, app/src/main/java/com/example/toplutasima/network/rmv/RmvSegmentParser.kt, app/src/main/java/com/example/toplutasima/ui/screens/records/RecordsFilterPanel.kt, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/ManualLogForm.kt, app/src/main/java/com/example/toplutasima/ui/screens/summary/ReportMetric.kt, app/src/main/java/com/example/toplutasima/ui/util/VehicleIcon.kt, app/src/main/java/com/example/toplutasima/usecase/TripPlanningUseCase.kt, app/src/main/java/com/example/toplutasima/viewmodel/BulkUpdateViewModel.kt, app/src/main/java/com/example/toplutasima/viewmodel/RmvLogViewModel.kt, app/src/main/java/com/example/toplutasima/viewmodel/rmvlog/RmvLogUiState.kt)
- app/src/main/java/com/example/toplutasima/usecase/TransitRecordCalculations.kt -> 11 dosya tarafindan import ediliyor (app/src/main/java/com/example/toplutasima/data/repository/LocalTripRepository.kt, app/src/main/java/com/example/toplutasima/data/repository/TripMapper.kt, app/src/main/java/com/example/toplutasima/network/firestore/FirestoreMigrationService.kt, app/src/main/java/com/example/toplutasima/network/firestore/FirestoreTripRemoteDataSource.kt, app/src/main/java/com/example/toplutasima/repository/TransitRecordRepository.kt, app/src/main/java/com/example/toplutasima/repository/TripRecordMapper.kt, app/src/main/java/com/example/toplutasima/ui/screens/records/EditRecordDialog.kt, app/src/main/java/com/example/toplutasima/viewmodel/BulkUpdateViewModel.kt, app/src/main/java/com/example/toplutasima/viewmodel/PersonalTripViewModel.kt, app/src/main/java/com/example/toplutasima/viewmodel/RecordsViewModel.kt, app/src/main/java/com/example/toplutasima/viewmodel/RmvLogViewModel.kt)
- app/src/main/java/com/example/toplutasima/viewmodel/rmvlog/RmvLogUiState.kt -> 11 dosya tarafindan import ediliyor (app/src/main/java/com/example/toplutasima/ui/screens/RmvLogScreen.kt, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/ActualTimesSection.kt, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/AdditionalInfoSection.kt, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/ChangeStopDialog.kt, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/DepartureSection.kt, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/JourneyMatchSection.kt, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/ManualLogForm.kt, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/PlannedRouteSection.kt, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/RmvLogHeader.kt, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/StopSelectionSection.kt, app/src/main/java/com/example/toplutasima/viewmodel/RmvLogViewModel.kt)
- app/src/main/java/com/example/toplutasima/viewmodel/RmvLogViewModel.kt -> 10 dosya tarafindan import ediliyor (app/src/main/java/com/example/toplutasima/di/AppModule.kt, app/src/main/java/com/example/toplutasima/ui/navigation/MainAppScreen.kt, app/src/main/java/com/example/toplutasima/ui/screens/RmvLogScreen.kt, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/ActualTimesSection.kt, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/AdditionalInfoSection.kt, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/ChangeStopDialog.kt, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/DepartureSection.kt, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/JourneyMatchSection.kt, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/PlannedRouteSection.kt, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/StopSelectionSection.kt)
- app/src/main/java/com/example/toplutasima/network/RmvApiService.kt -> 8 dosya tarafindan import ediliyor (app/src/main/java/com/example/toplutasima/location/NearbyStopsManager.kt, app/src/main/java/com/example/toplutasima/network/rmv/RmvSegmentDetailsService.kt, app/src/main/java/com/example/toplutasima/network/rmv/RmvStopService.kt, app/src/main/java/com/example/toplutasima/repository/RmvTripRepository.kt, app/src/main/java/com/example/toplutasima/service/JourneyMatchForegroundService.kt, app/src/main/java/com/example/toplutasima/usecase/TripPlanningUseCase.kt, app/src/main/java/com/example/toplutasima/viewmodel/BulkUpdateViewModel.kt, app/src/main/java/com/example/toplutasima/viewmodel/RmvLogViewModel.kt)
- app/src/main/java/com/example/toplutasima/viewmodel/BulkUpdateViewModel.kt -> 8 dosya tarafindan import ediliyor (app/src/main/java/com/example/toplutasima/di/AppModule.kt, app/src/main/java/com/example/toplutasima/ui/screens/BulkUpdateScreen.kt, app/src/main/java/com/example/toplutasima/ui/screens/MaintenanceScreen.kt, app/src/main/java/com/example/toplutasima/ui/screens/bulkupdate/BulkUpdateDoneContent.kt, app/src/main/java/com/example/toplutasima/ui/screens/bulkupdate/BulkUpdateIdleContent.kt, app/src/main/java/com/example/toplutasima/ui/screens/bulkupdate/BulkUpdatePausedContent.kt, app/src/main/java/com/example/toplutasima/ui/screens/bulkupdate/BulkUpdateRateLimitedContent.kt, app/src/main/java/com/example/toplutasima/ui/screens/bulkupdate/BulkUpdateRunningContent.kt)
- app/src/main/java/com/example/toplutasima/viewmodel/PersonalTripViewModel.kt -> 8 dosya tarafindan import ediliyor (app/src/main/java/com/example/toplutasima/di/AppModule.kt, app/src/main/java/com/example/toplutasima/ui/components/AddPersonalTripDialog.kt, app/src/main/java/com/example/toplutasima/ui/components/PersonalTripCard.kt, app/src/main/java/com/example/toplutasima/ui/screens/PersonalSummaryContent.kt, app/src/main/java/com/example/toplutasima/ui/screens/PersonalTripsScreen.kt, app/src/main/java/com/example/toplutasima/ui/screens/RecordsScreen.kt, app/src/main/java/com/example/toplutasima/ui/screens/RmvLogScreen.kt, app/src/main/java/com/example/toplutasima/ui/screens/records/PersonalRecordsContent.kt)
- app/src/main/java/com/example/toplutasima/model/FavoriteStop.kt -> 7 dosya tarafindan import ediliyor (app/src/main/java/com/example/toplutasima/MainActivity.kt, app/src/main/java/com/example/toplutasima/data/PrefsManager.kt, app/src/main/java/com/example/toplutasima/network/firestore/FirestoreFavoriteDataSource.kt, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/StopSelectionSection.kt, app/src/main/java/com/example/toplutasima/ui/screens/settings/FavoriteSettingsSection.kt, app/src/main/java/com/example/toplutasima/ui/screens/settings/ThemeLanguageSettingsSection.kt, app/src/main/java/com/example/toplutasima/viewmodel/SettingsViewModel.kt)
- app/src/main/java/com/example/toplutasima/viewmodel/bulkupdate/BulkUpdateUiState.kt -> 7 dosya tarafindan import ediliyor (app/src/main/java/com/example/toplutasima/ui/screens/BulkUpdateScreen.kt, app/src/main/java/com/example/toplutasima/ui/screens/bulkupdate/BulkUpdateDoneContent.kt, app/src/main/java/com/example/toplutasima/ui/screens/bulkupdate/BulkUpdateIdleContent.kt, app/src/main/java/com/example/toplutasima/ui/screens/bulkupdate/BulkUpdatePausedContent.kt, app/src/main/java/com/example/toplutasima/ui/screens/bulkupdate/BulkUpdateRateLimitedContent.kt, app/src/main/java/com/example/toplutasima/ui/screens/bulkupdate/BulkUpdateRunningContent.kt, app/src/main/java/com/example/toplutasima/viewmodel/BulkUpdateViewModel.kt)
- app/src/main/java/com/example/toplutasima/viewmodel/records/RecordsUiState.kt -> 7 dosya tarafindan import ediliyor (app/src/main/java/com/example/toplutasima/ui/screens/records/DayListScreen.kt, app/src/main/java/com/example/toplutasima/ui/screens/records/MonthListScreen.kt, app/src/main/java/com/example/toplutasima/ui/screens/records/TripCard.kt, app/src/main/java/com/example/toplutasima/usecase/ExportUseCase.kt, app/src/main/java/com/example/toplutasima/usecase/RecordFilterUtils.kt, app/src/main/java/com/example/toplutasima/usecase/RecordRowMapper.kt, app/src/main/java/com/example/toplutasima/viewmodel/RecordsViewModel.kt)

### God class riski

- Yuksek/orta risk: app/src/main/java/com/example/toplutasima/ui/Strings.kt -> 682 satir, 471 fonksiyon, 0 import, 0 yerel paket grubu; rol: UI/Compose ekranı.
- Yuksek/orta risk: app/src/main/java/com/example/toplutasima/viewmodel/RmvLogViewModel.kt -> 1864 satir, 92 fonksiyon, 47 import, 24 yerel paket grubu; rol: ViewModel/state yönetimi.
- Yuksek/orta risk: app/src/main/java/com/example/toplutasima/service/TransitTripForegroundService.kt -> 677 satir, 32 fonksiyon, 26 import, 7 yerel paket grubu; rol: Network/Firestore servisleri.
- Yuksek/orta risk: app/src/main/java/com/example/toplutasima/ui/screens/summary/TripStatsSection.kt -> 755 satir, 4 fonksiyon, 51 import, 7 yerel paket grubu; rol: UI/Compose ekranı.
- Yuksek/orta risk: app/src/main/java/com/example/toplutasima/usecase/SummaryCalculator.kt -> 652 satir, 11 fonksiyon, 11 import, 10 yerel paket grubu; rol: Uygulama katmanı.
- Yuksek/orta risk: app/src/main/java/com/example/toplutasima/viewmodel/RecordsViewModel.kt -> 552 satir, 20 fonksiyon, 29 import, 8 yerel paket grubu; rol: ViewModel/state yönetimi.
- Yuksek/orta risk: app/src/main/java/com/example/toplutasima/viewmodel/BulkUpdateViewModel.kt -> 527 satir, 18 fonksiyon, 25 import, 9 yerel paket grubu; rol: ViewModel/state yönetimi.
- Yuksek/orta risk: app/src/main/java/com/example/toplutasima/network/firestore/FirestoreTripRemoteDataSource.kt -> 516 satir, 21 fonksiyon, 8 import, 4 yerel paket grubu; rol: Network/Firestore servisleri.
- Yuksek/orta risk: app/src/main/java/com/example/toplutasima/ui/screens/MaintenanceScreen.kt -> 525 satir, 1 fonksiyon, 48 import, 8 yerel paket grubu; rol: UI/Compose ekranı.
- Yuksek/orta risk: app/src/main/java/com/example/toplutasima/ui/screens/records/DayListScreen.kt -> 481 satir, 3 fonksiyon, 78 import, 6 yerel paket grubu; rol: UI/Compose ekranı.
- Yuksek/orta risk: app/src/main/java/com/example/toplutasima/viewmodel/PersonalTripViewModel.kt -> 373 satir, 18 fonksiyon, 22 import, 6 yerel paket grubu; rol: ViewModel/state yönetimi.
- Yuksek/orta risk: app/src/main/java/com/example/toplutasima/viewmodel/SummaryViewModel.kt -> 255 satir, 9 fonksiyon, 26 import, 14 yerel paket grubu; rol: ViewModel/state yönetimi.

### ViewModel bağımlılıkları

- app/src/main/java/com/example/toplutasima/viewmodel/bulkupdate/BulkUpdateUiState.kt: Yerel repository/data-source importu görünmüyor.
  Yerel: com.example.toplutasima.model.BulkUpdateRow
- app/src/main/java/com/example/toplutasima/viewmodel/BulkUpdateViewModel.kt: Repository + ek data-source bağımlılıkları var.
  Repository: com.example.toplutasima.data.repository.LocalTripRepository
  Network: com.example.toplutasima.network.RmvApiService, com.example.toplutasima.network.firestore.FirestoreTripRemoteDataSource
- app/src/main/java/com/example/toplutasima/viewmodel/personaltrip/PersonalTripUiState.kt: Yerel repository/data-source importu görünmüyor.
  Yerel: com.example.toplutasima.model.PersonalTrip
- app/src/main/java/com/example/toplutasima/viewmodel/PersonalTripViewModel.kt: Repository üzerinden ilerliyor.
  Repository: com.example.toplutasima.repository.PersonalTripRepository
- app/src/main/java/com/example/toplutasima/viewmodel/records/RecordsUiState.kt: Yerel repository/data-source importu görünmüyor.
  Yerel: com.example.toplutasima.model.MonthSummary, com.example.toplutasima.usecase.RecordFilterState
- app/src/main/java/com/example/toplutasima/viewmodel/RecordsViewModel.kt: Repository üzerinden ilerliyor.
  Repository: com.example.toplutasima.data.repository.LocalTripRepository, com.example.toplutasima.data.repository.ProfileSyncRepository, com.example.toplutasima.data.repository.toEntity, com.example.toplutasima.data.repository.toMap
- app/src/main/java/com/example/toplutasima/viewmodel/rmvlog/RmvLogUiState.kt: Yerel repository/data-source importu görünmüyor.
  Yerel: com.example.toplutasima.model.Departure, com.example.toplutasima.model.JourneyMatchCandidate, com.example.toplutasima.model.StopOption, com.example.toplutasima.model.TransitAlert, com.example.toplutasima.model.TripResult, com.example.toplutasima.model.VehicleType
- app/src/main/java/com/example/toplutasima/viewmodel/RmvLogViewModel.kt: Repository + ek data-source bağımlılıkları var.
  Repository: com.example.toplutasima.data.repository.ProfileSyncRepository, com.example.toplutasima.repository.RmvTripRepository, com.example.toplutasima.repository.TransitRecordRepository, com.example.toplutasima.repository.TripProfileLinkRepository
  Network: com.example.toplutasima.network.RmvApiService
- app/src/main/java/com/example/toplutasima/viewmodel/SettingsViewModel.kt: Yerel repository/data-source importu görünmüyor.
  Yerel: com.example.toplutasima.data.PrefsManager, com.example.toplutasima.model.ThemeMode, com.example.toplutasima.model.UsageType
- app/src/main/java/com/example/toplutasima/viewmodel/SummaryViewModel.kt: Repository üzerinden ilerliyor.
  Repository: com.example.toplutasima.data.repository.LocalTripRepository, com.example.toplutasima.data.repository.toMap

## 6. TODO / FIXME / HACK / XXX Yorumları

Bulgu yok.

## 7. Kalite Skoru (1-10)

- Mimari tutarlılık (MVVM'e uyum): 6/10
- Kod tekrarı (az tekrar = yüksek puan): 4/10
- Dosya boyutu dengesi: 3/10
- Paket organizasyonu: 8/10

Skor notu: Puanlar statik sayım, import grafiği, büyük dosya sayısı ve tekrar sinyallerine göre verildi; runtime davranış veya manuel UI kalite testi kapsama dahil değildir.

## 8. Öncelikli İyileştirme Önerileri

- [YüksekÖncelik] Büyük dosyaları sorumluluk bazlı böl; özellikle UI/service/repository blokları okunabilirliği ve testlenebilirliği düşürüyor → app/src/main/java/com/example/toplutasima/viewmodel/RmvLogViewModel.kt, app/src/main/java/com/example/toplutasima/ui/screens/summary/TripStatsSection.kt, app/src/main/java/com/example/toplutasima/ui/Strings.kt, app/src/main/java/com/example/toplutasima/service/TransitTripForegroundService.kt, app/src/main/java/com/example/toplutasima/usecase/SummaryCalculator.kt, app/src/main/java/com/example/toplutasima/viewmodel/RecordsViewModel.kt
- [YüksekÖncelik] Firestore erişiminde ortak collection/query/mapper yardımcıları oluştur; aynı erişim deseni birden fazla sınıfta tekrar ediyor → app/src/main/java/com/example/toplutasima/network/firestore/FirestoreTripRemoteDataSource.kt, app/src/main/java/com/example/toplutasima/network/firestore/FirestoreFavoriteDataSource.kt, app/src/main/java/com/example/toplutasima/network/PersonalFirestoreService.kt, app/src/main/java/com/example/toplutasima/network/firestore/FirestoreMigrationService.kt, app/src/main/java/com/example/toplutasima/network/firestore/FirestorePersonService.kt, app/src/main/java/com/example/toplutasima/data/repository/LocalTripRepository.kt
- [OrtaÖncelik] ViewModel'ların repository dışı doğrudan data-source bağımlılıklarını azalt; UI state ile veri kaynağı sınırını netleştir → app/src/main/java/com/example/toplutasima/viewmodel/BulkUpdateViewModel.kt, app/src/main/java/com/example/toplutasima/viewmodel/RmvLogViewModel.kt
- [OrtaÖncelik] Kopyalanmış/çok benzer blokları ortak helper veya mapper'a taşı → app/src/main/java/com/example/toplutasima/ui/screens/MaintenanceScreen.kt, app/src/main/java/com/example/toplutasima/ui/screens/maintenance/MaintenanceActionCard.kt, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/ActualTimesSection.kt, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/AdditionalInfoSection.kt, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/DepartureSection.kt, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/JourneyMatchSection.kt, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/ManualLogForm.kt, app/src/main/java/com/example/toplutasima/ui/screens/rmvlog/PlannedRouteSection.kt
- [DüşükÖncelik] En çok import edilen model/yardımcı dosyaların API yüzeyini küçük tut; değişiklik etkisi geniş olabilir → app/src/main/java/com/example/toplutasima/ui/Strings.kt (46), app/src/main/java/com/example/toplutasima/ui/LocaleManager.kt (42), app/src/main/java/com/example/toplutasima/model/Models.kt (28), app/src/main/java/com/example/toplutasima/data/PrefsManager.kt (14), app/src/main/java/com/example/toplutasima/model/PersonalTrip.kt (12)

