# Sprint 4 — Araç ve Manuel Sürüş Veri Çekirdeği Tamamlanma Raporu

Rapor tarihi: 2026-07-20

## 1. Yapılan değişiklikler

### Araç

- Transit ve PersonalTrip alanlarından ayrılmış `drive` domain sınırı oluşturuldu.
- Araç ekleme, listeleme, detay, düzenleme ve local-first tombstone silme akışları eklendi.
- Araç adı, marka/model, plaka, model yılı, yakıt türü, başlangıç/manüel kilometre, kişi ataması ID'si ve not alanları desteklendi.
- Araç silinirken bağlı sürüş varsa kullanıcı onayı isteniyor; araç ve aktif sürüşleri aynı Room transaction'ında tombstone yapılıyor.

### Manuel sürüş

- Manuel sürüş ekleme, listeleme, düzenleme ve local-first silme akışları eklendi.
- Elle tarih/saat, başlangıç-bitiş kilometresi, mesafe, amaç, konum adları ve not girişi desteklendi.
- `12,5` ve `12.5` biçimleri, negatif/sonlu olmayan değerler, zaman sırası ve odometre-mesafe tutarlılığı doğrulanıyor.
- Otomatik, GPS, Activity Recognition veya import yolculuğu oluşturulmadı.

### Room

- Araç, sürüş ve sync-operation tabloları ile UID kapsamlı DAO/Flow sorguları eklendi.
- Araç özetleri Room invalidation ile canlı güncelleniyor; hesaplama dönüşümleri `Dispatchers.Default`, veri erişimi `Dispatchers.IO` üzerinde kalıyor.
- Hesap değişimi `flatMapLatest` ile eski UID akışını iptal ediyor.

### Firebase ve sync

- Mevcut Firebase Authentication hesabı ve Firestore projesi kullanıldı.
- Outbound local-first sync kuyruğu, WorkManager worker'ı, retry/fatal hata sınıflandırması, idempotent işlem kimliği ve delete-wins politikası eklendi.
- Kuyruk yalnız UID, kayıt ID'si ve işlem metadata'sı taşıyor; tam araç/sürüş snapshot'ı taşımıyor.

### UI ve navigation

- Build-time `DRIVE_CORE` açıkken mevcut alt navigasyona son öğe olarak **Araçlar** eklendi.
- Mevcut dört transit/ayar sekmesinin indeksleri ve `showPersonal` dalları değiştirilmedi.
- Araç listesi, araç detay, araç editörü ve manuel sürüş editörü eklendi.
- Stabil kayıt ID'leri kullanıldı; araç adı/plaka navigation kimliği yapılmadı ve deep link eklenmedi.

## 2. Veri modeli

Domain modelleri:

- `DriveVehicle`
- `DriveTrip`
- `DriveVehicleDraft`
- `DriveTripDraft`
- `DriveVehicleSummary`
- `DriveVehicleOverview`

Enum/sealed değerleri:

- `VehicleFuelType`: `PETROL`, `DIESEL`, `LPG`, `HYBRID`, `PLUG_IN_HYBRID`, `ELECTRIC`, `OTHER`, `UNKNOWN`
- `DriveTripPurpose`: `PERSONAL`, `BUSINESS`, `COMMUTE`, `OTHER`, `UNCLASSIFIED`
- `DriveTripEntrySource`: yalnız `MANUAL` üretiliyor; `AUTOMATIC` ve `IMPORTED` güvenli parse sınırında mevcut
- `DriveSyncState`
- Kilometre gösterim kaynağı modeli

Room entity'leri:

- `DriveVehicleEntity`
- `DriveTripEntity`
- `DriveSyncOperationEntity`

`VehicleAssignmentEntity` eklenmedi. Mevcut tek-kişi/opsiyonel ilişki `assignedPersonId` ile tutuluyor; kişi adı kopyalanmıyor ve mevcut olmayan kişi dizini için sahte veri üretilmiyor.

## 3. Değiştirilen dosyalar

### Değiştirilen mevcut dosyalar

- `app/src/main/java/com/example/toplutasima/data/local/AppDatabase.kt`
- `app/src/main/java/com/example/toplutasima/di/AppModule.kt`
- `app/src/main/java/com/example/toplutasima/ui/Strings.kt`
- `app/src/main/java/com/example/toplutasima/ui/navigation/MainAppScreen.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/example/toplutasima/data/local/AppDatabaseMigrationTest.kt`
- `app/src/androidTest/java/com/example/toplutasima/DatabaseMigrationTest.kt`

### Yeni üretim dosyaları

- `app/src/main/java/com/example/toplutasima/drive/DriveFeatureFlags.kt`
- `app/src/main/java/com/example/toplutasima/drive/model/DriveModels.kt`
- `app/src/main/java/com/example/toplutasima/drive/validation/DriveValidation.kt`
- `app/src/main/java/com/example/toplutasima/drive/summary/DriveVehicleSummaryCalculator.kt`
- `app/src/main/java/com/example/toplutasima/drive/data/DriveEntityMappers.kt`
- `app/src/main/java/com/example/toplutasima/drive/data/remote/DriveFirestoreDataSource.kt`
- `app/src/main/java/com/example/toplutasima/drive/repository/DriveAuthSession.kt`
- `app/src/main/java/com/example/toplutasima/drive/repository/DriveIdGenerator.kt`
- `app/src/main/java/com/example/toplutasima/drive/repository/DriveRepositories.kt`
- `app/src/main/java/com/example/toplutasima/drive/repository/OfflineFirstDriveRepository.kt`
- `app/src/main/java/com/example/toplutasima/drive/sync/DriveSyncFailureClassifier.kt`
- `app/src/main/java/com/example/toplutasima/drive/sync/DriveSyncModels.kt`
- `app/src/main/java/com/example/toplutasima/drive/sync/DriveSyncPlanner.kt`
- `app/src/main/java/com/example/toplutasima/drive/sync/RoomDriveSyncRepository.kt`
- `app/src/main/java/com/example/toplutasima/drive/sync/WorkManagerDriveSyncScheduler.kt`
- `app/src/main/java/com/example/toplutasima/drive/ui/DriveUiState.kt`
- `app/src/main/java/com/example/toplutasima/drive/ui/DriveUiText.kt`
- `app/src/main/java/com/example/toplutasima/drive/ui/DriveViewModel.kt`
- `app/src/main/java/com/example/toplutasima/drive/ui/DriveVehiclesScreen.kt`
- `app/src/main/java/com/example/toplutasima/drive/ui/DriveVehicleDetailScreen.kt`
- `app/src/main/java/com/example/toplutasima/drive/ui/DriveVehicleEditorScreen.kt`
- `app/src/main/java/com/example/toplutasima/drive/ui/DriveTripEditorScreen.kt`
- `app/src/main/java/com/example/toplutasima/data/local/entity/DriveVehicleEntity.kt`
- `app/src/main/java/com/example/toplutasima/data/local/entity/DriveTripEntity.kt`
- `app/src/main/java/com/example/toplutasima/data/local/entity/DriveSyncOperationEntity.kt`
- `app/src/main/java/com/example/toplutasima/data/local/dao/DriveVehicleDao.kt`
- `app/src/main/java/com/example/toplutasima/data/local/dao/DriveTripDao.kt`
- `app/src/main/java/com/example/toplutasima/data/local/dao/DriveSyncOperationDao.kt`
- `app/src/main/java/com/example/toplutasima/di/DriveFeatureModule.kt`
- `app/src/main/java/com/example/toplutasima/worker/DriveSyncWorker.kt`

### Yeni test dosyaları

- `app/src/test/java/com/example/toplutasima/drive/DriveValidationTest.kt`
- `app/src/test/java/com/example/toplutasima/drive/DriveVehicleSummaryCalculatorTest.kt`
- `app/src/test/java/com/example/toplutasima/drive/OfflineFirstDriveRepositoryTest.kt`
- `app/src/test/java/com/example/toplutasima/drive/data/remote/DriveRemoteWriteValidatorTest.kt`
- `app/src/test/java/com/example/toplutasima/drive/sync/DriveSyncPlannerTest.kt`
- `app/src/test/java/com/example/toplutasima/drive/sync/RoomDriveSyncRepositoryTest.kt`
- `app/src/test/java/com/example/toplutasima/drive/ui/DriveViewModelTest.kt`
- `app/src/test/java/com/example/toplutasima/data/local/DriveRoomDaoTest.kt`
- `app/src/test/java/com/example/toplutasima/worker/DriveSyncWorkerTest.kt`
- `app/src/androidTest/java/com/example/toplutasima/DriveCoreRepositoryDeviceTest.kt`
- `app/src/androidTest/java/com/example/toplutasima/DriveRoomDeviceTest.kt`
- `app/src/androidTest/java/com/example/toplutasima/drive/ui/DriveLocalizedResourceDeviceTest.kt`
- `app/src/androidTest/java/com/example/toplutasima/drive/ui/DriveVehiclesScreenDeviceTest.kt`

### Rapor artefaktı

- `SPRINT4_COMPLETION_REPORT.md`

Çalışma ağacındaki `TransitEncryptedStoresDeviceTest.kt` ve `TransitSafExportDeviceTest.kt` önceki Sprint 3.5 çalışmasından kalan untracked testlerdir; Sprint 4 kapsamında oluşturulmadı veya değiştirilmedi.

## 4. Room migration

- Eski sürüm: **8**
- Yeni sürüm: **9**
- Eklenen tablolar: `drive_vehicles`, `drive_trips`, `drive_sync_operations`
- Migration yalnız `CREATE TABLE`/`CREATE INDEX` kullanıyor; `DROP`, destructive migration veya mevcut transit tablo değişikliği yok.
- Host migration: 2/2 başarılı; `migration 8 to 9 creates drive tables without changing existing rows` dahil.
- API 36 cihaz migration paketi: 6/6 başarılı; `migration8To9_preservesExistingRowsAndCreatesDriveSchema` dahil.
- Mevcut transit/profile/link verisinin korunması test edildi; veri kaybı tespit edilmedi.
- `DRIVE_CORE=false` rollback'i UI/işlem katmanını kapatır; Room şeması otomatik olarak v8'e düşmez ve bu beklenen güvenli davranıştır.

## 5. Firebase yapısı

- Araç: `users/{uid}/vehicles/{vehicleId}`
- Manuel sürüş: `users/{uid}/driveTrips/{tripId}`
- Assignment koleksiyonu oluşturulmadı; bu sprintte gereksiz ayrı altyapı yerine araç üzerindeki stabil `assignedPersonId` kullanıldı.
- Firestore kuralları değiştirilmedi. Mevcut `users/{userId}/{document=**}` kuralı `request.auth.uid == userId` koşuluyla yeni alt koleksiyonları UID kapsamında tutuyor.
- Yerelde üretilen stabil aggregate ID, açık bir domain kimliği olarak Firestore document ID'sinde kullanılıyor; görünen ad/plaka kimlik değildir.
- Remote işlem öncesinde entity UID ile hedef path UID eşleşmesi doğrulanıyor.

## 6. Offline-first davranış

- Create/update/delete önce Room transaction'ına yazılıyor ve aynı transaction içinde kompakt sync operation oluşturuluyor.
- UI Room Flow üzerinden hemen güncelleniyor.
- Scheduler başlatma hatası yerel kaydı geri almıyor; sonuç `LocalSavedSyncSchedulingFailed` olarak ayrıştırılıyor.
- Araç ve sürüş silmeleri hard delete yerine `deletedAt` + sync state tombstone kullanıyor.
- Araç+bağlı sürüş silme planı tek yerel transaction içinde uygulanıyor; kısmi yerel orphan bırakılmıyor.
- Uygulama/worker yeniden başlatıldığında pending operation Room'da korunuyor.

## 7. Sync yarışları

- Kuyruk anahtarı `(userId, entityType, recordId)`; her yeni mutasyon önceki bekleyen işi kompakt ediyor.
- `CREATE -> UPDATE` create/upsert olarak kalıyor; `CREATE/UPDATE -> DELETE` delete-wins oluyor.
- Her kuyruk satırının yeni `operationId` token'ı var. Eski worker yalnız kendi token'ını koşullu ack/fail edebildiği için yeni DELETE'i silemiyor veya eski update'i geri getiremiyor.
- Firestore transaction'ları `_syncOperationId` ile idempotent; remote tombstone eski update ile dirilmeyi engelliyor.
- Trip upsert için parent araç remote olarak `SYNCED` olmalı; tombstone delete işlemleri parent silinmişken de tamamlanabiliyor.
- Worker input UID'si ile mevcut Firebase Auth UID birebir eşleşmeden işlem yapılmıyor.
- `CancellationException` yeniden fırlatılıyor.

## 8. UI

- Araç listesi: ad, marka/model, plaka, kişi fallback'i, toplam mesafe, son kullanım ve erişilebilir sync durumu.
- Araç editörü: sprintte istenen tüm araç alanları ve açıklayıcı doğrulama.
- Araç detayı: temel bilgi, toplam mesafe, sürüş sayısı, son kullanım, başlangıç/manüel/tahmini kilometre kaynağı, son sürüşler ve eylemler.
- Manuel sürüş editörü: araç, elle başlangıç/bitiş tarih-saat, odometre, mesafe, amaç, yer adları ve not.
- Yakıt/amaç metinleri enum adına bağlanmadı; TR/DE/EN açık string-resource eşlemesi kullanıldı.
- Kişi kaynağı bulunmadığı için sahte kişi oluşturulmadı; atanmamış fallback gösteriliyor.
- Kaydet/sil işlemlerinde ikinci tıklama `AtomicBoolean` mutasyon kilidiyle engelleniyor.

## 9. Test sonuçları

### Sprint 4 host test dağılımı

| Alan | Test | Başarılı | Başarısız | Atlanan |
|---|---:|---:|---:|---:|
| Model/doğrulama + summary + offline repository | 16 | 16 | 0 | 0 |
| Room DAO + migration | 9 | 9 | 0 | 0 |
| Sync planner/repository/remote validator/worker | 28 | 28 | 0 | 0 |
| ViewModel/UI state | 10 | 10 | 0 | 0 |
| Sprint 4 host toplamı | 63 | 63 | 0 | 0 |

### Tam paketler

| Paket | Test | Başarılı | Başarısız | Error | Atlanan |
|---|---:|---:|---:|---:|---:|
| `testDebugUnitTest` | 414 | 414 | 0 | 0 | 0 |
| `connectedDebugAndroidTest` | 36 | 36 | 0 | 0 | 0 |
| Host + cihaz toplamı | 450 | 450 | 0 | 0 | 0 |

Instrumented paket `Medium_Phone_API_36.1` AVD'sinde, Android 16 / API 36 üzerinde, `emulator-5554` cihazı `device` durumundayken çalıştırıldı.

Sprint 4 cihaz kapsamı 12 Drive-özel test ve ortak migration sınıfındaki v8→v9 testiyle 13 senaryodur. Room Flow invalidation, araç+sürüş ekleme, canlı summary, database reopen, UID izolasyonu, cross-UID FK, feature gate, form doğrulama, çift mutation engeli ve dil resource eşlemeleri cihazda geçti.

## 10. Build sonuçları

Gerçekten çalıştırılan wrapper komutları:

- `gradlew.bat compileDebugKotlin`: **başarılı**, exit 0.
- `gradlew.bat testDebugUnitTest --rerun-tasks`: **başarılı**, exit 0.
- Son resource düzeltmesinden sonra `gradlew.bat testDebugUnitTest`: **başarılı**, 414/414.
- `gradlew.bat lintDebug`: **başarılı**, exit 0; 0 error, 77 warning, Drive/Sprint 4 yolunda 0 lint issue.
- `gradlew.bat assembleDebug --rerun-tasks`: **başarılı**, exit 0.
- `gradlew.bat assembleDebugAndroidTest --rerun-tasks`: **başarılı**, exit 0.
- `gradlew.bat connectedDebugAndroidTest`: **başarılı**, 36/36.
- `git diff --check`: **başarılı**, exit 0; yalnız Git LF→CRLF bilgilendirmeleri var.

Wrapper proje-local `.gradle-local` cache ile kullanıldı; sistem Gradle veya sürüm değişikliği kullanılmadı.

İlk doğrulamada bulunan ve düzeltilen sorunlar:

- Ayrı `values-de`/`values-en` dizinleri mevcut PersonalTrip kaynaklarında 7 `MissingTranslation` lint hatası oluşturuyordu. PersonalTrip kaynaklarına dokunmadan Drive metinleri varsayılan resource dosyasındaki açık TR/DE/EN ID eşlemelerine taşındı.
- Android test kaynaklarında Room database için geçersiz `.use` ve yanlış Compose test importları düzeltildi.
- API 36.1'de mevcut Espresso sürümünün kaldırılmış `InputManager.getInstance()` çağrısı 5 UI testini ürün koduna gelmeden düşürüyordu. Dependency yükseltmeden, aynı ürün davranışları gerçek-device ViewModel/UI-state testleriyle doğrulandı.
- Hızlı çift kayıt mutasyonu yarışı `DriveViewModel` içinde atomik kilitle kapatıldı.

## 11. APK

- Debug APK: `C:\Users\mehme\AndroidStudioProjects\TopluTasima\app\build\outputs\apk\debug\app-debug.apk`
  - Boyut: **27,239,036 bayt**
  - Zaman damgası: **2026-07-20 08:19:38 +02:00**
- Android test APK: `C:\Users\mehme\AndroidStudioProjects\TopluTasima\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk`
  - Boyut: **1,086,864 bayt**
  - Zaman damgası: **2026-07-20 08:43:41 +02:00**

İki dosya da mevcut ve sıfırdan büyük.

## 12. Güvenlik

- 28 Drive DAO sorgusunun tamamı `userId` parametresiyle kapsamlı.
- Araç/sürüş PK'leri ve araç-sürüş foreign key'i `(userId, id)` bileşik anahtar kullanıyor.
- Auth değişimi eski Flow'u iptal ediyor; worker eski UID işini yeni UID adına çalıştırmıyor.
- Drive kodunda UID, plaka, kişi ID, not, Firestore ID, queue payload veya tüm nesneyi yazan `Log`/`println`/Timber çağrısı bulunmadı.
- Queue tam record snapshot'ı saklamıyor.
- Firestore security rule, Gradle/SDK/dependency ve AndroidManifest değiştirilmedi.
- NaN/infinite kilometre remote yazımdan önce reddediliyor.

## 13. Feature gates

- Tek build-time gate eklendi: `DRIVE_CORE`.
- Kapalıyken Araçlar navigation öğesi görünmüyor, repository Flow'ları boş sonuç veriyor, ViewModel pending sync başlatmıyor, scheduler no-op oluyor ve worker başarıyla no-op dönüyor.
- Alt gate çoğaltılmadı.
- Rollback yeni build'de gate kapatılarak yapılabilir; v9 additive Room tabloları korunur, otomatik schema downgrade yapılmaz.

## 14. Bilinen sınırlamalar

- Bu sprint outbound **Room → Firestore** sync çekirdeğini içeriyor. Firebase → Room pull/hydration henüz yok; yeni cihazın buluttaki Drive kayıtlarını indirmesi sonraki sprint işidir.
- Canlı Firebase projesine karşı ağ tabanlı uçtan uca sync testi yapılmadı; queue/planner/worker/UID davranışı fake tabanlı unit ve device testleriyle doğrulandı.
- Bellek veya ortak kişi dizinine erişim olmadığı için kişi seçme ekranı yok; mevcut stabil ID korunabiliyor/temizlenebiliyor ve UI güvenli fallback gösteriyor.
- Otomatik sürüş, GPS, Activity Recognition, foreground service, geofence, Bluetooth, import, yakıt/bakım, harita/ORS ve export özellikle eklenmedi.
- Mevcut Espresso sürümü API 36.1 input API'siyle uyumsuz olduğu ve dependency değişikliği yasak olduğu için gerçek Compose tıklama enjeksiyonu kullanılmadı. UI-state/validation senaryoları cihaz testinde geçti; gerçek Drive ekranının manuel gezinmesi fresh install'da Firebase oturumu olmadığı için yapılmadı ve sahte kullanıcı oluşturulmadı.
- Kısa cold-start logcat taramasında crash/ANR/StrictMode/OOM eşleşmesi görülmedi; bu, uzun süreli profiler/soak testi yerine geçmez.

## 15. PersonalTrip doğrulaması

- PersonalTrip entity değişti mi? **Hayır.**
- PersonalTrip DAO değişti mi? **Hayır.**
- PersonalTrip repository değişti mi? **Hayır.**
- GPS altyapısı değişti mi? **Hayır.**
- Activity Recognition değişti mi? **Hayır.**
- Foreground service değişti mi? **Hayır.**
- Quick Tile değişti mi? **Hayır.**
- Waypoint değişti mi? **Hayır.**
- Kişisel ORS değişti mi? **Hayır.**
- PersonalTrip navigation değişti mi? **Hayır.**
- PersonalTrip ViewModel değişti mi? **Hayır.**

Karma `MainAppScreen` dosyasında yalnız gate'li Drive sekmesi son öğe olarak eklendi; mevcut `showPersonal` state/dalları korunuyor.

## 16. Transit doğrulaması

- Transit üretim iş mantığı değiştirilmedi.
- `AppModule`, mevcut `transitFeatureModule` binding'ini aynen koruyup yanına bağımsız Drive modülünü ekliyor.
- Mevcut Sprint 1–3 host regresyonları tam 414 testlik pakette geçti.
- Transit Room/Flow ve Sprint 3.5 şifreli store/SAF cihaz testleri dahil tam cihaz paketi 36/36 geçti.
- Firestore transit koleksiyonları, transit offline queue/tombstone/sync receipt/provenance/health/insights/history/duplicate/export kodu değiştirilmedi.

## 17. Genel değerlendirme

Durum: **Tamamlandı.**

Araç CRUD, manuel sürüş CRUD, local-first queue, tombstone/delete-wins, UID izolasyonu, additive Room migration, canlı kilometre/son kullanım özeti, unit/lint/APK ve mevcut API 36.1 emülatöründeki instrumented paket doğrulandı. Otomatik sürüş ve inbound Firebase hydration bu sprintin dışında bırakıldı. Git commit oluşturulmadı.
