# TopluTasima Proje Sagligi Denetimi

Tarih: 2026-05-20  
Paket: `com.example.toplutasima`  
Yol: `C:\Users\mehme\AndroidStudioProjects\TopluTasima`

Notlar:
- Satir sayiminda bos satirlar dahil edildi.
- `png`, `webp`, `jar` gibi ikili dosyalar icin satir sayisi `binary` olarak isaretlendi.
- Oneriler UI davranisini veya gorsel tasarimi degistirmez; sadece dosya, sinif ve sorumluluk ayrimi onerir.

## 1. Genel Envanter

- Toplam dosya: 134
- Metin dosyasi: 116
- Ikili dosya: 18
- Toplam metin satiri: 25.205
- Kotlin dosyasi: 91
- `app/src/main/java`: 80 Kotlin dosyasi, 22.286 satir
- `app/src/test/java`: 9 Kotlin dosyasi, 598 satir
- `app/src/androidTest/java`: 2 Kotlin dosyasi, 466 satir

## 2. Ana Kaynak Dizin Dagilimi

| Dizin | Dosya | Satir |
|---|---:|---:|
| `(root)` | 2 | 176 |
| `data/` | 15 | 1.456 |
| `di/` | 1 | 45 |
| `diagnostics/` | 1 | 67 |
| `location/` | 2 | 278 |
| `model/` | 5 | 322 |
| `network/` | 10 | 3.261 |
| `repository/` | 2 | 359 |
| `service/` | 5 | 1.707 |
| `ui/` | 21 | 9.516 |
| `usecase/` | 7 | 1.188 |
| `viewmodel/` | 6 | 3.784 |
| `worker/` | 3 | 127 |

## 3. Kotlin Paket Dagilimi

| Paket | Dosya | Satir |
|---|---:|---:|
| `com.example.toplutasima` | 12 | 1.163 |
| `com.example.toplutasima.data` | 3 | 510 |
| `com.example.toplutasima.data.backup` | 4 | 426 |
| `com.example.toplutasima.data.local` | 1 | 88 |
| `com.example.toplutasima.data.local.dao` | 3 | 125 |
| `com.example.toplutasima.data.local.entity` | 3 | 103 |
| `com.example.toplutasima.data.repository` | 2 | 281 |
| `com.example.toplutasima.di` | 1 | 45 |
| `com.example.toplutasima.diagnostics` | 1 | 67 |
| `com.example.toplutasima.location` | 2 | 278 |
| `com.example.toplutasima.model` | 5 | 322 |
| `com.example.toplutasima.network` | 8 | 3.061 |
| `com.example.toplutasima.network.rmv` | 2 | 200 |
| `com.example.toplutasima.repository` | 2 | 359 |
| `com.example.toplutasima.service` | 5 | 1.707 |
| `com.example.toplutasima.ui` | 3 | 697 |
| `com.example.toplutasima.ui.components` | 6 | 918 |
| `com.example.toplutasima.ui.navigation` | 1 | 115 |
| `com.example.toplutasima.ui.screens` | 8 | 7.683 |
| `com.example.toplutasima.ui.theme` | 3 | 103 |
| `com.example.toplutasima.usecase` | 7 | 1.188 |
| `com.example.toplutasima.viewmodel` | 6 | 3.784 |
| `com.example.toplutasima.worker` | 3 | 127 |

## 4. Her Dosyanin Satir Sayisi

| Satir | Dosya |
|---:|---|
| 26 | `settings.gradle.kts` |
| 94 | `gradlew.bat` |
| 251 | `gradlew` |
| 32 | `gradle.properties` |
| 7 | `gradle/wrapper/gradle-wrapper.properties` |
| binary | `gradle/wrapper/gradle-wrapper.jar` |
| 63 | `gradle/libs.versions.toml` |
| 12 | `gradle/gradle-daemon-jvm.properties` |
| 23 | `firestore.rules` |
| 750 | `docs/HAFAS_API_ENHANCEMENTS.md` |
| 13 | `CHANGELOG.md` |
| 10 | `build.gradle.kts` |
| 76 | `app/src/test/java/com/example/toplutasima/StopNameUtilsTest.kt` |
| 35 | `app/src/test/java/com/example/toplutasima/RmvRetrofitClientTest.kt` |
| 99 | `app/src/test/java/com/example/toplutasima/RmvFeatureParsersTest.kt` |
| 42 | `app/src/test/java/com/example/toplutasima/RmvEndpointAvailabilityTest.kt` |
| 81 | `app/src/test/java/com/example/toplutasima/ReportCardUtilsTest.kt` |
| 115 | `app/src/test/java/com/example/toplutasima/FirestoreServiceTest.kt` |
| 17 | `app/src/test/java/com/example/toplutasima/ExampleUnitTest.kt` |
| 75 | `app/proguard-rules.pro` |
| 145 | `app/build.gradle.kts` |
| 56 | `app/src/test/java/com/example/toplutasima/ApiErrorsTest.kt` |
| 77 | `app/src/test/java/com/example/toplutasima/data/backup/BackupEncryptorTest.kt` |
| binary | `app/src/main/assets/rmv_logo.png` |
| 86 | `app/src/main/AndroidManifest.xml` |
| 5 | `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` |
| 5 | `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` |
| 4 | `app/src/main/res/xml/file_paths.xml` |
| 19 | `app/src/main/res/xml/data_extraction_rules.xml` |
| 13 | `app/src/main/res/xml/backup_rules.xml` |
| 30 | `app/src/main/res/drawable/ic_launcher_foreground.xml` |
| 170 | `app/src/main/res/drawable/ic_launcher_background.xml` |
| 24 | `app/src/androidTest/java/com/example/toplutasima/ExampleInstrumentedTest.kt` |
| 5 | `app/src/main/res/values/themes.xml` |
| 442 | `app/src/androidTest/java/com/example/toplutasima/DatabaseMigrationAndDaoTest.kt` |
| 10 | `app/src/main/res/values/colors.xml` |
| 4 | `app/src/main/res/values/ic_launcher_background.xml` |
| 3 | `app/src/main/res/values/strings.xml` |
| binary | `app/src/main/ic_launcher-playstore.png` |
| binary | `app/src/main/res/mipmap-xxhdpi/ic_launcher_foreground.webp` |
| binary | `app/src/main/res/mipmap-xxhdpi/ic_launcher.webp` |
| binary | `app/src/main/res/mipmap-xxhdpi/ic_launcher_round.webp` |
| binary | `app/src/main/res/mipmap-mdpi/ic_launcher_round.webp` |
| binary | `app/src/main/res/mipmap-mdpi/ic_launcher_foreground.webp` |
| binary | `app/src/main/res/mipmap-mdpi/ic_launcher.webp` |
| binary | `app/src/main/res/mipmap-hdpi/ic_launcher_foreground.webp` |
| binary | `app/src/main/res/mipmap-hdpi/ic_launcher.webp` |
| binary | `app/src/main/res/mipmap-hdpi/ic_launcher_round.webp` |
| binary | `app/src/main/res/mipmap-xhdpi/ic_launcher.webp` |
| binary | `app/src/main/res/mipmap-xhdpi/ic_launcher_foreground.webp` |
| binary | `app/src/main/res/mipmap-xhdpi/ic_launcher_round.webp` |
| binary | `app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.webp` |
| binary | `app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp` |
| binary | `app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp` |
| 58 | `app/src/main/java/com/example/toplutasima/worker/TransitActionWorker.kt` |
| 44 | `app/src/main/java/com/example/toplutasima/worker/PeriodicSyncWorker.kt` |
| 25 | `app/src/main/java/com/example/toplutasima/worker/OfflineSyncWorker.kt` |
| 36 | `app/src/main/java/com/example/toplutasima/network/StopNameUtils.kt` |
| 220 | `app/src/main/java/com/example/toplutasima/network/RmvFeatureParsers.kt` |
| 32 | `app/src/main/java/com/example/toplutasima/network/RmvEndpointAvailability.kt` |
| 1.166 | `app/src/main/java/com/example/toplutasima/network/RmvApiService.kt` |
| 127 | `app/src/main/java/com/example/toplutasima/MainActivity.kt` |
| 106 | `app/src/main/java/com/example/toplutasima/network/ApiErrors.kt` |
| 29 | `app/src/main/java/com/example/toplutasima/network/ApiClient.kt` |
| 218 | `app/src/main/java/com/example/toplutasima/viewmodel/SummaryViewModel.kt` |
| 192 | `app/src/main/java/com/example/toplutasima/viewmodel/SettingsViewModel.kt` |
| 1.821 | `app/src/main/java/com/example/toplutasima/viewmodel/RmvLogViewModel.kt` |
| 611 | `app/src/main/java/com/example/toplutasima/viewmodel/RecordsViewModel.kt` |
| 397 | `app/src/main/java/com/example/toplutasima/viewmodel/PersonalTripViewModel.kt` |
| 545 | `app/src/main/java/com/example/toplutasima/viewmodel/BulkUpdateViewModel.kt` |
| 138 | `app/src/main/java/com/example/toplutasima/network/rmv/RmvRetrofitClient.kt` |
| 161 | `app/src/main/java/com/example/toplutasima/network/PersonalFirestoreService.kt` |
| 62 | `app/src/main/java/com/example/toplutasima/network/rmv/RmvDtos.kt` |
| 1.311 | `app/src/main/java/com/example/toplutasima/network/FirestoreService.kt` |
| 67 | `app/src/main/java/com/example/toplutasima/diagnostics/AppErrorReporter.kt` |
| 49 | `app/src/main/java/com/example/toplutasima/TopluTasimaApp.kt` |
| 45 | `app/src/main/java/com/example/toplutasima/di/AppModule.kt` |
| 25 | `app/src/main/java/com/example/toplutasima/model/VehicleType.kt` |
| 98 | `app/src/main/java/com/example/toplutasima/location/NearbyStopsManager.kt` |
| 332 | `app/src/main/java/com/example/toplutasima/repository/TripRepository.kt` |
| 189 | `app/src/main/java/com/example/toplutasima/data/repository/TripRepository.kt` |
| 180 | `app/src/main/java/com/example/toplutasima/location/PersonalLocationHelper.kt` |
| 233 | `app/src/main/java/com/example/toplutasima/usecase/TripPlanningUseCase.kt` |
| 1.109 | `app/src/main/java/com/example/toplutasima/service/TransitTripForegroundService.kt` |
| 160 | `app/src/main/java/com/example/toplutasima/service/JourneyMatchForegroundService.kt` |
| 46 | `app/src/main/java/com/example/toplutasima/service/ActivityRecognitionReceiver.kt` |
| 253 | `app/src/main/java/com/example/toplutasima/service/PersonalTripForegroundService.kt` |
| 139 | `app/src/main/java/com/example/toplutasima/service/TransitNotificationReceiver.kt` |
| 172 | `app/src/main/java/com/example/toplutasima/usecase/HeatmapUtils.kt` |
| 223 | `app/src/main/java/com/example/toplutasima/usecase/ExportUseCase.kt` |
| 211 | `app/src/main/java/com/example/toplutasima/usecase/DataHealthChecker.kt` |
| 35 | `app/src/main/java/com/example/toplutasima/model/TripStatus.kt` |
| 52 | `app/src/main/java/com/example/toplutasima/model/PersonalTrip.kt` |
| 194 | `app/src/main/java/com/example/toplutasima/model/Models.kt` |
| 16 | `app/src/main/java/com/example/toplutasima/model/FavoriteStop.kt` |
| 133 | `app/src/main/java/com/example/toplutasima/usecase/RecordFilterUtils.kt` |
| 145 | `app/src/main/java/com/example/toplutasima/usecase/MonthComparisonUtils.kt` |
| 71 | `app/src/main/java/com/example/toplutasima/usecase/ReportCardUtils.kt` |
| 45 | `app/src/main/java/com/example/toplutasima/data/AppEventBus.kt` |
| 27 | `app/src/main/java/com/example/toplutasima/repository/PersonalTripRepository.kt` |
| 295 | `app/src/main/java/com/example/toplutasima/data/PrefsManager.kt` |
| 92 | `app/src/main/java/com/example/toplutasima/data/repository/TripMapper.kt` |
| 170 | `app/src/main/java/com/example/toplutasima/data/OfflineQueueStore.kt` |
| 175 | `app/src/main/java/com/example/toplutasima/data/backup/ProfileBackupManager.kt` |
| 79 | `app/src/main/java/com/example/toplutasima/data/backup/BackupModels.kt` |
| 95 | `app/src/main/java/com/example/toplutasima/data/backup/BackupEncryptor.kt` |
| 88 | `app/src/main/java/com/example/toplutasima/data/local/AppDatabase.kt` |
| 34 | `app/src/main/java/com/example/toplutasima/data/local/dao/TripProfileLinkDao.kt` |
| 31 | `app/src/main/java/com/example/toplutasima/data/local/entity/TripProfileLinkEntity.kt` |
| 54 | `app/src/main/java/com/example/toplutasima/data/local/entity/TripEntity.kt` |
| 60 | `app/src/main/java/com/example/toplutasima/data/local/dao/TripDao.kt` |
| 31 | `app/src/main/java/com/example/toplutasima/data/local/dao/ProfileDao.kt` |
| 18 | `app/src/main/java/com/example/toplutasima/data/local/entity/ProfileEntity.kt` |
| 23 | `app/src/main/java/com/example/toplutasima/ui/LocaleManager.kt` |
| 18 | `app/src/main/java/com/example/toplutasima/ui/AppColors.kt` |
| 656 | `app/src/main/java/com/example/toplutasima/ui/Strings.kt` |
| 34 | `app/src/main/java/com/example/toplutasima/ui/theme/Type.kt` |
| 58 | `app/src/main/java/com/example/toplutasima/ui/theme/Theme.kt` |
| 11 | `app/src/main/java/com/example/toplutasima/ui/theme/Color.kt` |
| 1.057 | `app/src/main/java/com/example/toplutasima/ui/screens/SummaryScreen.kt` |
| 1.526 | `app/src/main/java/com/example/toplutasima/ui/screens/SettingsScreen.kt` |
| 1.696 | `app/src/main/java/com/example/toplutasima/ui/screens/RmvLogScreen.kt` |
| 1.717 | `app/src/main/java/com/example/toplutasima/ui/screens/RecordsScreen.kt` |
| 253 | `app/src/main/java/com/example/toplutasima/ui/screens/PersonalTripsScreen.kt` |
| 184 | `app/src/main/java/com/example/toplutasima/ui/screens/PersonalSummaryContent.kt` |
| 759 | `app/src/main/java/com/example/toplutasima/ui/screens/MaintenanceScreen.kt` |
| 491 | `app/src/main/java/com/example/toplutasima/ui/screens/BulkUpdateScreen.kt` |
| 322 | `app/src/main/java/com/example/toplutasima/ui/components/PersonalTripCard.kt` |
| 175 | `app/src/main/java/com/example/toplutasima/ui/components/HeatmapCalendar.kt` |
| 274 | `app/src/main/java/com/example/toplutasima/ui/components/AddPersonalTripDialog.kt` |
| 58 | `app/src/main/java/com/example/toplutasima/ui/components/RmvFooter.kt` |
| 65 | `app/src/main/java/com/example/toplutasima/ui/components/SummaryCard.kt` |
| 24 | `app/src/main/java/com/example/toplutasima/ui/components/TimeVisualTransformation.kt` |
| 115 | `app/src/main/java/com/example/toplutasima/ui/navigation/MainAppScreen.kt` |

## 5. 300 Satiri Asan Dosyalar

| Oncelik | Satir | Dosya | Kapsam |
|---:|---:|---|---|
| 1 | 1.311 | `network/FirestoreService.kt` | veri erisimi + hesaplama + migrasyon |
| 2 | 1.821 | `viewmodel/RmvLogViewModel.kt` | kayit ekraninin tum orkestrasyonu |
| 3 | 1.166 | `network/RmvApiService.kt` | RMV API + parse + mesafe |
| 4 | 1.109 | `service/TransitTripForegroundService.kt` | servis + bildirim + konum |
| 5 | 1.717 | `ui/screens/RecordsScreen.kt` | kayitlar UI + filtre + edit dialog |
| 6 | 1.696 | `ui/screens/RmvLogScreen.kt` | RMV kayit UI + manuel form |
| 7 | 1.526 | `ui/screens/SettingsScreen.kt` | ayarlar + profil + yedek dialoglari |
| 8 | 1.057 | `ui/screens/SummaryScreen.kt` | ozet UI + rapor kartlari |
| 9 | 759 | `ui/screens/MaintenanceScreen.kt` | bakim aksiyonlari UI |
| 10 | 611 | `viewmodel/RecordsViewModel.kt` | listeleme + arama + export + edit |
| 11 | 545 | `viewmodel/BulkUpdateViewModel.kt` | toplu mesafe/durak guncelleme motoru |
| 12 | 491 | `ui/screens/BulkUpdateScreen.kt` | toplu guncelleme durum UI'lari |
| 13 | 397 | `viewmodel/PersonalTripViewModel.kt` | kisisel seyahat CRUD + servis kontrolu |
| 14 | 332 | `repository/TripRepository.kt` | RMV facade + Firestore + Room link |
| 15 | 656 | `ui/Strings.kt` | tum lokalize metinler |
| 16 | 322 | `ui/components/PersonalTripCard.kt` | kisisel seyahat karti + aksiyonlar |
| 17 | 442 | `androidTest/DatabaseMigrationAndDaoTest.kt` | DAO + migrasyon + yedek testleri |
| - | 750 | `docs/HAFAS_API_ENHANCEMENTS.md` | dokumantasyon, kod refactor kapsaminda degil |

## 6. SRP Supheli Dosyalar ve Coklu Sorumluluklar

- `FirestoreService.kt`: Firestore CRUD, tarih/sure/mesafe hesaplama, ozet analitigi, toplu update, migrasyonlar ve favoriler ayni object icinde. Bu dosya veri siniri ile domain hesaplamasini karistiriyor.
- `RmvLogViewModel.kt`: durak arama, favoriler, yakindaki duraklar, sefer secimi, yolculuk planlama, kaydetme/guncelleme, gercek saatler, bildirimler, profil eslestirme ve manuel kayit ayni ViewModel icinde.
- `RmvApiService.kt`: endpoint cagri katmani, DTO/JSON parse, journey matching, ORS/rail mesafe hesaplari ve fallback algoritmalari ayni object icinde.
- `TransitTripForegroundService.kt`: Android lifecycle, notification rendering, reminder scheduling, proximity watch, activity recognition ve konum hesaplari tek sinifta.
- `RecordsScreen.kt`, `RmvLogScreen.kt`, `SettingsScreen.kt`, `SummaryScreen.kt`: Screen composable'lari alt bolumleri kendi icinde tutuyor; bu MVVM'i bozmaz ama Compose dosya okunabilirligini ciddi dusuruyor.
- `RecordsViewModel.kt`: kayit listeleme, global arama, ay secimi, filtre, edit/delete ve export akislari ayni ViewModel'de.
- `BulkUpdateViewModel.kt`: UI state yonetimi ile rate-limit bekleme, ORS/RMV is akisi ve satir isleme algoritmasi ayni sinifta.
- `TripRepository.kt`: RMV network facade'i, Firestore save/update ve Room profil link update ayni repository'de.
- `Strings.kt`: tum ekranlarin metinleri tek object icinde; teknik olarak calisir ama degisim carpani yuksek.

## 7. Duplicate Kod Tespiti

- Arac tipi emoji map'i en az su yerlerde tekrar ediyor: `RecordsScreen.kt`, `RecordsViewModel.kt`, `RmvLogScreen.kt`, `TransitTripForegroundService.kt`, `TransitNotificationReceiver.kt`. Oneri: `model/VehicleType.kt` icine `fun VehicleType.icon()` veya `VehicleType.iconForKey(key: String)` eklemek.
- Sure/saat yardimcilari tekrar ediyor: `FirestoreService.computeYolSuresi`, `PersonalFirestoreService.computeYolSuresi`, `RmvLogViewModel.computeDuration`, lokal `formatTime`/`toDigits`, `RecordsScreen.stripSeconds`. Oneri: `usecase/TimeFormatUtils.kt` veya `model/TransitTimeUtils.kt`.
- Compose kart iskeleti (`Card(fillMaxWidth, RoundedCornerShape, surfaceVariant)`) `MaintenanceScreen`, `RmvLogScreen`, `SettingsScreen`, `SummaryScreen`, `RecordsScreen`, `BulkUpdateScreen`, `PersonalTripCard` boyunca tekrar ediyor. UI davranisi degismeden ortak `SectionCard`/`InfoCard` gibi component'e alinabilir.
- Firestore try/catch kalibi `FirestoreService.kt` icinde bircok kez ayni sekilde tekrarlaniyor. Oneri: kucuk `FirestoreSafeCall` veya repository-level private helper.
- Test verisi kurma bloklari `DatabaseMigrationAndDaoTest.kt` icinde tekrar ediyor. Oneri: test fixture factory dosyasi.

## 8. Import Tutarli mi?

- UI dosyalarinda wildcard import kullanimi yogun: `androidx.compose.foundation.layout.*`, `androidx.compose.material3.*`, `androidx.compose.runtime.*`.
- Testlerde `org.junit.Assert.*` wildcard olarak kullaniliyor.
- Bazi dosyalarda proje siniflari import edilmek yerine fully qualified kullaniliyor: ozellikle `RmvLogViewModel.kt`, `RecordsViewModel.kt`, `SettingsScreen.kt`, `TransitTripForegroundService.kt`.
- `repository/TripRepository.kt` ve `data/repository/TripRepository.kt` ayni sinif adini farkli paketlerde tasiyor. Bu import karisikligi ve yanlis repository secimi riski dogurur.
- Import sayisi cok yuksek dosyalar: `RecordsScreen.kt` 55, `SettingsScreen.kt` 54, `TransitTripForegroundService.kt` 54, `RmvLogScreen.kt` 53, `SummaryScreen.kt` 43, `RmvLogViewModel.kt` 42.

Oneri:
1. Wildcard importlari explicit importlara cevir.
2. Fully qualified proje referanslarini normal importlara al.
3. Eski `repository/` ve yeni `data/repository/` ayrimini netlestir; mumkunse yeni veri katmanina kademeli gecis plani yap.
4. Import siralamasini Android Studio optimize imports standardina birak.

## 9. Uzun Dosyalar Icin Bolme Plani

### 1. `network/FirestoreService.kt` - 1.311 satir

Analiz: Tek object; Firestore CRUD, hesaplama, summary, migration ve favori islemleri birlikte. En kritik bolme noktasi bu, cunku cok sayida repository/viewmodel buraya bagli.

Onerilen yeni dosyalar:
- `network/firestore/FirestoreTripRemoteDataSource.kt`: `saveTrip`, `updateActual`, `clearActual`, `fetchRecord`, `fetchTrips`, `fetchTripsFiltered`, `updateTrip`, `deleteTrip`, `updateExistingRecord`.
- `network/firestore/FirestoreMigrationService.kt`: `migrateStripSeconds`, `migrateYolSuresi`, `migrateYearMonth`, `migrateSortDate`, `migrateDistanceFields`, `migrateSeatmateUuid`.
- `network/firestore/FirestoreFavoriteDataSource.kt`: `saveFavorite`, `deleteFavorite`, `fetchAllFavorites`.
- `usecase/SummaryCalculator.kt`: `computeSummary` ve icindeki aggregation modelleri.
- `usecase/TransitRecordCalculations.kt`: `computeYearMonth`, `computeSortDate`, `computeGununTipi`, `computeGun`, `computeGecikme`, `computeYolSuresi`, distance helpers.

Bagimlilik etkisi: Ilk adimda `FirestoreService` facade olarak kalabilir ve yeni siniflara delegate eder. Boylece `TripRepository`, `RecordsViewModel`, `BulkUpdateViewModel` kirilmaz.

Uygulama adimlari:
1. Saf hesaplama fonksiyonlarini `TransitRecordCalculations` icine tasiyip testleri buraya bagla.
2. Migrasyon fonksiyonlarini ayri service'e al, `FirestoreService` uzerinden gecici delegate birak.
3. CRUD ve favorileri data source'lara ayir.
4. Son turda call-site'lari facade yerine yeni siniflara gecir.

### 2. `viewmodel/RmvLogViewModel.kt` - 1.821 satir

Analiz: Tek ViewModel kayit ekraninin neredeyse tum domain akisini tasiyor. MVVM korunarak ViewModel sade tutulmali; is kurallari usecase/helper siniflara alinmali.

Onerilen yeni dosyalar:
- `viewmodel/rmvlog/RmvLogUiState.kt`: `LogMode`, `ManualEntryState`, `RmvLogUiState`.
- `usecase/RmvStopSearchUseCase.kt`: `searchFrom`, `searchTo`, cache/fallback durak arama.
- `usecase/RmvJourneySelectionUseCase.kt`: `fetchDepartures`, `selectDeparture`, `fetchTrip`, segment detail enrichment.
- `usecase/RmvRecordSaveUseCase.kt`: `saveToSheets`, `restoreRecord`, segment update map hazirlama.
- `usecase/RmvManualRecordUseCase.kt`: `saveManualRecord`, manuel field validation/format.
- `usecase/TransitNotificationCoordinator.kt`: notification baslat/durdur, pref event sync, permission ihtiyac kararlarinin domain kismi.
- `usecase/FavoriteStopUseCase.kt`: favori durak ekleme/secme.

Bagimlilik etkisi: UI `RmvLogViewModel` ile konusmaya devam eder. Once private helper'lar usecase'e tasinir; public ViewModel API korunur.

Uygulama adimlari:
1. State data class'larini ayri dosyaya al.
2. Saf helper'lari ve duplicate `formatTime`/`toDigits` fonksiyonlarini ortak util'e tasi.
3. Durak arama/favori akislarini usecase'e cikar.
4. Kaydetme/manual record akislarini ayri usecase'e cikar.
5. Bildirim koordinasyonunu en son ayir; servis bagimliliklari daha riskli.

### 3. `network/RmvApiService.kt` - 1.166 satir

Analiz: RMV API client, JSON parse, journey matching, ORS/rail mesafe hesaplama ve fallback algoritmalari ayni object icinde. Network katmani test edilebilir alt parcalara ayrilmali.

Onerilen yeni dosyalar:
- `network/rmv/RmvStopService.kt`: stop/location/nearby search.
- `network/rmv/RmvJourneyService.kt`: departure board, trip basic, journey track match.
- `network/rmv/RmvSegmentParser.kt`: `parseTripObject`, line/direction/product/type extraction.
- `network/rmv/RmvSegmentDetailsService.kt`: `fetchJourneyStops`, `fetchSegmentDetails`.
- `network/rmv/RmvDistanceService.kt`: ORS, rail route, haversine, polyline/fallback hesaplari.
- `network/rmv/RmvTimeUtils.kt`: `formatTimeDigits`, `normalizeRmvClock`, `convertToApiDate`, diff helpers.

Bagimlilik etkisi: `TripRepository` su an `RmvApiService` object'ine bagli. Gecis icin object facade'i korunup yeni servisleri cagirabilir.

Uygulama adimlari:
1. DTO ve time util fonksiyonlarini ayir.
2. Parser fonksiyonlarini saf sinifa al ve mevcut parser testlerini genislet.
3. Distance hesaplarini ayir.
4. Endpoint fonksiyonlarini servis siniflarina bol.

### 4. `service/TransitTripForegroundService.kt` - 1.109 satir

Analiz: Android service lifecycle, kalici state, notification, reminder alarm, location proximity ve activity recognition ayni sinifta. Bu Android servislerinde normalden daha riskli, cunku permission ve lifecycle hatalari sessiz davranis bozar.

Onerilen yeni dosyalar:
- `service/transit/TransitServiceStateStore.kt`: encrypted prefs state persist/restore.
- `service/transit/TransitNotificationBuilder.kt`: tracking/reminder notification olusturma.
- `service/transit/TransitReminderScheduler.kt`: reminder/proximity alarm schedule/cancel.
- `service/transit/TransitProximityTracker.kt`: location updates, speed/distance/activity karar mantigi.
- `service/transit/TransitActionIntents.kt`: pending intent/action factory.

Bagimlilik etkisi: Manifest service ayni kalir. Yeni helper'lar service tarafindan kullanilir; public intent action sozlesmesi degismemeli.

Uygulama adimlari:
1. Notification build fonksiyonlarini cikart.
2. State persist/restore helper'ini cikart.
3. Alarm scheduling'i ayir.
4. Proximity/location logic'i en son ayir ve instrumentation/manual test yap.

### 5. `ui/screens/RecordsScreen.kt` - 1.717 satir

Analiz: Bir screen dosyasi icinde ay listesi, gun listesi, filtre paneli, export butonlari, aktif filtreler, trip card, edit dialog ve kisisel kayit listesi var.

Onerilen yeni dosyalar:
- `ui/screens/records/RecordsScreen.kt`: ana route ve state wiring.
- `ui/screens/records/MonthListScreen.kt`: ay listesi.
- `ui/screens/records/DayListScreen.kt`: gun listesi.
- `ui/screens/records/RecordsFilterPanel.kt`: `FilterPanel`, `ActiveFilterBar`, `ChipItem`.
- `ui/screens/records/TripCard.kt`: kayit karti.
- `ui/screens/records/EditRecordDialog.kt`: edit dialog.
- `ui/screens/records/PersonalRecordsContent.kt`: kisisel kayitlar listesi.
- `ui/util/VehicleIcon.kt`: `typeEmoji`.

Bagimlilik etkisi: Public `RecordsScreen(...)` ayni kalir. Alt composable'lar ayni package veya subpackage'te kalirsa minimal import etkisi olur.

Uygulama adimlari:
1. `typeEmoji` ortak util'e al.
2. Dialog ve filter componentlerini dosya bazinda cikar.
3. Liste/kart composable'larini cikar.
4. Son olarak ana dosyada sadece orchestration birak.

### 6. `ui/screens/RmvLogScreen.kt` - 1.696 satir

Analiz: Tek dosyada durak secimi, departure listesi, planned route, ek bilgiler, durak degistirme, journey match, actual time aksiyonlari ve manuel form var.

Onerilen yeni dosyalar:
- `ui/screens/rmvlog/RmvLogScreen.kt`: ana route.
- `ui/screens/rmvlog/StopSelectionSection.kt`: from/to, favorite, nearby stop UI.
- `ui/screens/rmvlog/DepartureSection.kt`: kalkislar ve secim.
- `ui/screens/rmvlog/PlannedRouteSection.kt`: segment listesi ve edit times.
- `ui/screens/rmvlog/AdditionalInfoSection.kt`: hava/oturma/bilet/not/profil.
- `ui/screens/rmvlog/ChangeStopDialog.kt`: durak degistirme UI.
- `ui/screens/rmvlog/JourneyMatchSection.kt`: GPS eslestirme karti.
- `ui/screens/rmvlog/ActualTimesSection.kt`: bindim/indim/undo aksiyonlari.
- `ui/screens/rmvlog/ManualLogForm.kt`: manuel form.

Bagimlilik etkisi: `RmvLogViewModel` public API'si korunursa UI split riski dusuk. Ortak vehicle icon util'i bu dosyadaki duplicate when bloklarini azaltir.

Uygulama adimlari:
1. `ManualLogForm` dosya disina cikar.
2. Dialog/section bazli composable'lari ayir.
3. Common UI helper'lari `components/` altina al.
4. Ana screen'i state + callback wiring'e indir.

### 7. `ui/screens/SettingsScreen.kt` - 1.526 satir

Analiz: Tema/dil/renk, favori yonetimi, bildirim ayarlari, diagnostics, profil backup, profil yonetimi ve secure password dialog ayni dosyada.

Onerilen yeni dosyalar:
- `ui/screens/settings/SettingsScreen.kt`: ana route.
- `ui/screens/settings/ThemeLanguageSettingsSection.kt`
- `ui/screens/settings/FavoriteSettingsSection.kt`
- `ui/screens/settings/TransitNotificationSettingsSection.kt`
- `ui/screens/settings/DiagnosticsSection.kt`
- `ui/screens/settings/ProfileBackupSection.kt`
- `ui/screens/settings/ProfileManagerDialog.kt`
- `ui/screens/settings/ProfileEditDialog.kt`
- `ui/screens/settings/SecurePasswordDialog.kt`
- `ui/util/ColorPreviewUtils.kt`: `parsePreviewColor`.

Bagimlilik etkisi: `SettingsViewModel` ayni kalir; file split sadece Compose fonksiyonlarinin importlarini etkiler.

Uygulama adimlari:
1. Dialog composable'larini ayri dosyalara cikar.
2. Section bazli ayirma yap.
3. Local state yogun olan backup/import launcher bolumunu kucuk coordinator composable'a al.

### 8. `ui/screens/SummaryScreen.kt` - 1.057 satir

Analiz: Ozet ekrani cok sayida rapor/istatistik bandini tek dosyada kuruyor. Hesaplama buyuk oranda ViewModel/usecase tarafinda, ama UI bolumleri ayrilmamis.

Onerilen yeni dosyalar:
- `ui/screens/summary/SummaryScreen.kt`: ana route.
- `ui/screens/summary/SummaryHeaderSection.kt`
- `ui/screens/summary/TripStatsSection.kt`
- `ui/screens/summary/DurationDelaySection.kt`
- `ui/screens/summary/InsightSection.kt`
- `ui/screens/summary/ReportCardsSection.kt`
- `ui/screens/summary/ReportMetric.kt`

Bagimlilik etkisi: Sadece UI importlari degisir; `SummaryViewModel` etkilenmez.

Uygulama adimlari:
1. Private composable'lari ayri dosyalara tasi.
2. `displaySheet` gibi saf formatter'lari util'e al.
3. Ana screen'de tab/secim state wiring'i birak.

### 9. `ui/screens/MaintenanceScreen.kt` - 759 satir

Analiz: Veri bakim aksiyonlari benzer kart kaliplariyla tekrar ediyor. Bu dosya structural component extraction icin dusuk riskli iyi bir ilk UI refactor adayidir.

Onerilen yeni dosyalar:
- `ui/screens/maintenance/MaintenanceScreen.kt`: ana ekran.
- `ui/screens/maintenance/MaintenanceActionCard.kt`: ortak card.
- `ui/screens/maintenance/MaintenanceConfirmDialog.kt`
- `ui/screens/maintenance/MaintenanceResultBanner.kt`
- `ui/screens/maintenance/MigrationActionsSection.kt`

Bagimlilik etkisi: `FirestoreService` migrasyon fonksiyonlari simdilik ayni kalabilir. UI davranisi degismez.

Uygulama adimlari:
1. Tekrarlanan card kalibini component'e al.
2. Confirm/result dialoglarini ayir.
3. Her migration action'i data-driven listeye cevirmek sonraki adim olabilir.

### 10. `viewmodel/RecordsViewModel.kt` - 611 satir

Analiz: Ay secimi, global search, map-to-ui, edit/delete, filtre, export ve profile link update ayni ViewModel'de.

Onerilen yeni dosyalar:
- `viewmodel/records/RecordsUiState.kt`: `RecordRowUiModel`, `DayGroup`, `RecordsUiState`.
- `usecase/RecordRowMapper.kt`: `mapFirestoreRecordToRow`, `typeEmoji` yerine ortak icon util.
- `usecase/RecordSearchUseCase.kt`: global search/latest record.
- `usecase/RecordEditUseCase.kt`: update/delete/profile link.
- `usecase/RecordExportCoordinator.kt`: export akisi.

Bagimlilik etkisi: `RecordsScreen` ViewModel public fonksiyonlarini kullanmaya devam eder. Once state/model dosyasina tasima risksizdir.

Uygulama adimlari:
1. State data class'larini ayir.
2. Mapper'i test edilebilir usecase'e al.
3. Export ve edit akisini ayir.

### 11. `viewmodel/BulkUpdateViewModel.kt` - 545 satir

Analiz: ViewModel hem state tutuyor hem batch processor, rate limiter ve RMV/ORS stratejilerini calistiriyor.

Onerilen yeni dosyalar:
- `viewmodel/bulkupdate/BulkUpdateUiState.kt`: enumlar ve state.
- `usecase/BulkDistanceUpdateUseCase.kt`: `processRow`, ORS/RMV distance flow.
- `usecase/BulkStopNameUpdateUseCase.kt`: stop name update flow.
- `usecase/RateLimiter.kt`: ORS/RMV bekleme ve call kaydi.

Bagimlilik etkisi: `BulkUpdateScreen` ViewModel API'sine bagli kalir. Usecase'ler suspend API sunar.

Uygulama adimlari:
1. State dosyasini ayir.
2. Rate limiter'i saf sinifa al.
3. Row processor'lari usecase'e tasiyip ViewModel'i orchestration'a indir.

### 12. `ui/screens/BulkUpdateScreen.kt` - 491 satir

Analiz: Ana section ile loading/running/paused/rate-limited/done durum UI'lari ayni dosyada.

Onerilen yeni dosyalar:
- `ui/screens/bulkupdate/BulkUpdateSection.kt`
- `ui/screens/bulkupdate/BulkUpdateLoadingContent.kt`
- `ui/screens/bulkupdate/BulkUpdateRunningContent.kt`
- `ui/screens/bulkupdate/BulkUpdatePausedContent.kt`
- `ui/screens/bulkupdate/BulkUpdateRateLimitedContent.kt`
- `ui/screens/bulkupdate/BulkUpdateDoneContent.kt`

Bagimlilik etkisi: `BulkUpdateViewModel` degismez; sadece composable importlari etkilenir.

Uygulama adimlari:
1. Private content composable'lari dosya disina cikar.
2. Ortak progress/status satirlarini mini component yap.

### 13. `viewmodel/PersonalTripViewModel.kt` - 397 satir

Analiz: Kisisel seyahat listesi, form state, CRUD, foreground service baslat/durdur ve location permission tek ViewModel'de.

Onerilen yeni dosyalar:
- `viewmodel/personaltrip/PersonalTripUiState.kt`
- `usecase/PersonalTripFormMapper.kt`: form -> `PersonalTrip`.
- `usecase/PersonalTripTrackingCoordinator.kt`: foreground service start/stop intentleri.
- `usecase/PersonalTripRepositoryActions.kt`: save/update/delete akislari.

Bagimlilik etkisi: `PersonalTripsScreen` public ViewModel fonksiyonlarini korursa etkilenmez.

Uygulama adimlari:
1. State dosyasini ayir.
2. Form mapping'i saf helper'a al.
3. Service start/stop tarafini coordinator'a ayir.

### 14. `repository/TripRepository.kt` - 332 satir

Analiz: Repository; RMV network islerini, Firestore save/update islerini ve Room profil link update islerini birlestiriyor. Ayrica `data/repository/TripRepository.kt` ile ad cakismasi var.

Onerilen yeni dosyalar:
- `repository/RmvTripRepository.kt`: RMV arama/planlama facade'i.
- `repository/TransitRecordRepository.kt`: Firestore trip save/update/fetch.
- `repository/TripProfileLinkRepository.kt`: Room link update.
- `repository/TripRecordMapper.kt`: segment -> map hazirlama.

Bagimlilik etkisi: En riskli nokta sinif adi. Gecis icin mevcut `TripRepository` facade kalsin, yeni repository'lere delegate etsin.

Uygulama adimlari:
1. Mapper'i cikar.
2. Profile link islemlerini ayir.
3. RMV ve Firestore facade'lerini bol.
4. DI tarafinda yeni siniflar tanimlanacaksa son adimda yap.

### 15. `ui/Strings.kt` - 656 satir

Analiz: Lokalizasyon tek object icinde. UI davranisi degismeden domain/screen bazli parcalanabilir. Uzun ama saf oldugu icin dusuk riskli.

Onerilen yeni dosyalar:
- `ui/strings/CommonStrings.kt`
- `ui/strings/RmvLogStrings.kt`
- `ui/strings/RecordsStrings.kt`
- `ui/strings/SummaryStrings.kt`
- `ui/strings/SettingsStrings.kt`
- `ui/strings/PersonalTripStrings.kt`
- `ui/Strings.kt`: facade olarak `S` object'ini gecici korur.

Bagimlilik etkisi: `S.*` call-site'lari cok fazla. Ilk asamada facade korunmali; aksi halde genis import churn olusur.

Uygulama adimlari:
1. String gruplarini yeni object'lere al.
2. `S` object'inde delegate fonksiyonlar birak.
3. Yeni kodlarda dogrudan gruplu string object'leri kullan.

### 16. `ui/components/PersonalTripCard.kt` - 322 satir

Analiz: Tek kart component'i; detay alanlari, aksiyon butonlari ve status gosterimi iceriyor. 300 satiri az asiyor, SRP riski orta-dusuk.

Onerilen yeni dosyalar:
- `ui/components/personaltrip/PersonalTripCard.kt`
- `ui/components/personaltrip/PersonalTripInfoRows.kt`
- `ui/components/personaltrip/PersonalTripActionRow.kt`
- `ui/components/personaltrip/PersonalTripStatusBadge.kt`

Bagimlilik etkisi: Public `PersonalTripCard(...)` korunursa kullanan ekranlar etkilenmez.

Uygulama adimlari:
1. Private alt row/status composable'larini ayir.
2. Public component imzasini ayni birak.

### 17. `androidTest/DatabaseMigrationAndDaoTest.kt` - 442 satir

Analiz: DAO CRUD, cascade, migration ve backup import/export testleri ayni sinifta. Test okunabilirligi dusuyor.

Onerilen yeni dosyalar:
- `androidTest/.../ProfileDaoTest.kt`
- `androidTest/.../TripProfileLinkDaoTest.kt`
- `androidTest/.../DatabaseMigrationTest.kt`
- `androidTest/.../ProfileBackupManagerInstrumentedTest.kt`
- `androidTest/.../TestDatabaseFactory.kt`
- `androidTest/.../TestProfileFixtures.kt`

Bagimlilik etkisi: Production kod etkilenmez. Test setup helper'lari ortak kullanilir.

Uygulama adimlari:
1. Fixture factory'leri ayir.
2. DAO testlerini sinif bazinda bol.
3. Migration ve backup testlerini ayir.

### Dokuman: `docs/HAFAS_API_ENHANCEMENTS.md` - 750 satir

Analiz: Kod dosyasi degil; MVVM/SRP kapsaminda refactor zorunlulugu yok.

Oneri:
1. Dokumani bolmek istenirse `docs/hafas/overview.md`, `docs/hafas/endpoints.md`, `docs/hafas/migration-notes.md` olarak ayrilabilir.
2. Bu is kod sagligi refactor'undan ayri ele alinmali.

## 10. Kucuk ve Bagimsiz Refactor Sirasi

1. Ortak saf util'ler: `VehicleIcon`, `TransitTimeUtils`, `ColorPreviewUtils`.
2. State data class dosyalari: `RmvLogUiState`, `RecordsUiState`, `BulkUpdateUiState`, `PersonalTripUiState`.
3. Dusuk riskli UI extraction: `BulkUpdateScreen`, `MaintenanceScreen`, `PersonalTripCard`.
4. Buyuk UI extraction: `RecordsScreen`, `RmvLogScreen`, `SettingsScreen`, `SummaryScreen`.
5. Data/network extraction: `FirestoreService`, `RmvApiService`, `TripRepository`.
6. Service extraction: `TransitTripForegroundService`.
7. Test split ve yeni unit testler.

## 11. Test/Guvence Onerisi

- Her kucuk refactor adimindan sonra `./gradlew.bat testDebugUnitTest` calistir.
- Data/network splitlerinden sonra mevcut parser, Firestore helper ve repository testlerini genislet.
- Service splitinden sonra en az bir manual smoke test gerekir: notification start, reminder, proximity fallback, boarding/alighting action.
- UI extraction saf composable bolme oldugu icin davranis degismemeli; compile + ekran smoke test yeterli olur.
