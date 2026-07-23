# Sprint 6B Completion Report

## 1. Yönetici özeti

Sprint 6B kapsamında TopluTaşıma'ya additive ayrıntılı araç profili, UID kapsamlı çoklu araç fotoğrafı modeli, yerel hazırlama/EXIF temizleme hattı, kalıcı fotoğraf outbox'ı, WorkManager senkronizasyonu, tombstone, primary fotoğraf, sıralama, health ve receipt desteği eklendi. Bellek tarafında Room tabanlı salt okunur profil/fotoğraf projection'ı, incremental hydration, UID kapsamlı Storage cache'i ve mevcut `vehicleId` deep-link akışı genişletildi.

TopluTaşıma'nın tüm compile, unit, lint, APK, test APK ve instrumented doğrulamaları geçti. Bellek'in compile, unit, lint, APK ve test APK doğrulamaları geçti; yeni Sprint 6B migration/DAO instrumented testleri 3/3 geçti. Bellek'in tam instrumented paketi, sprint öncesinden bilinen ve değiştirilmeyen eksik `9.json` ile `10.json` varlıkları nedeniyle 16 testin 2'sinde başarısız oldu. Storage Rules emulator testi için proje komutu ve testleri hazırdır; makinede Node.js, npm ve Firebase CLI bulunmadığından emulator komutu çalıştırılamadı. Production Firebase veya Storage'a yazılmadı, staging/commit yapılmadı.

## 2. Sprint kapsamı

Uygulanan kapsam:

- Ayrıntılı araç profil alanları ve güvenli partial Firestore yazımı.
- Çoklu fotoğraf, primary seçimi, sıralama, local-first ekleme/silme/retry.
- JPEG hazırlama, yeniden boyutlandırma, sıkıştırma, EXIF/GPS temizleme ve SHA-256 içerik özeti.
- UID kapsamlı Room, Storage path, prepared dosya ve cache politikası.
- Photo metadata initial/incremental pull, tombstone, conflict, health, provenance ve receipt.
- TopluTaşıma düzenlenebilir profil/galeri UI'sı ve Bellek salt okunur projection/UI'sı.
- Room 11→12 ve 25→26 additive migration'ları ile gerçek Room schema export'ları.
- Firestore/Storage Rules kaynakları ve rules-unit-testing test senaryoları.

Sprint 7 kapsamındaki yakıt, bakım, sigorta ve gider iş mantığı; GPS, Activity Recognition, foreground service, harita, Driversnote, video/OCR, Cloud Functions veya yeni backend eklenmedi. Bellek'e araç ya da fotoğraf düzenleme yetkisi verilmedi.

## 3. Araç profil modeli

`DriveVehicle.id` canonical `vehicleId` olarak korundu; plaka kimlik olarak kullanılmıyor. Mevcut model additive biçimde şu alanlarla genişletildi: `countryCode`, `transmissionType`, `bodyType`, `color`, `vin`, `engineDisplacementCc`, `enginePowerKw`, `purchaseDate`, `purchasePriceMinor`, `currencyCode`, `primaryPhotoId`, `trimLevel`, `engineCode`, `registrationDate`, `inspectionDueDate`, `insuranceDueDate`, `tireSize`, `schemaVersion`, `primaryPhotoRevision` ve `primaryPhotoOperationId`. Önceden var olan ad, marka, model, yıl, plaka, yakıt, kilometre, not, assignment ve lifecycle alanları korunuyor.

Satın alma fiyatı floating point yerine minor unit olarak tutuluyor. VIN opsiyonel, kilometre negatif olamaz ve model yılı makul aralıkla doğrulanır. Vehicle schema sürümü 2'dir. Vehicle edit, bilinmeyen remote alanları silmeyen partial/merge alan yazımı kullanır; `assignedPersonId` stale form state'inden yazılmaz ve Sprint 6A `vehicleAssignments` kaynağına dokunulmaz.

## 4. Fotoğraf veri modeli

TopluTaşıma Room modeli `ownerUid + photoId` anahtarlı `drive_vehicle_photos` tablosudur. Model; `vehicleId`, kaynak URI, prepared path, `storagePath`, `contentHash`, MIME, boyutlar, byte boyutu, sıra, primary projection, schema/revision/operation/source, client/server zamanları, tombstone, upload/remote durumları, hata ve health kodlarını taşır.

Kalıcı işlem katmanı şu tablolardan oluşur:

- `drive_photo_operations`: UID kapsamlı upload/delete/metadata operation outbox'ı ve atomik claim bilgisi.
- `drive_photo_sync_metadata`: initial hydration ve `_serverUpdatedAt + document path` cursor'ı.
- `drive_photo_sync_receipts`: operation sonucu, provenance, revision, kazanan operation ve hata sınıfı.

Bir fotoğraf yalnız bir araca aittir. DAO transaction'ları aynı araçta primary projection'ını tekilleştirir; sıralama `sortOrder`, ardından yaratılma zamanı ve `photoId` ile deterministiktir.

## 5. Firestore photo sözleşmesi

Canonical metadata yolu:

`users/{uid}/vehicles/{vehicleId}/photos/{photoId}`

Sözleşme; `ownerUid`, `photoId`, `vehicleId`, `storagePath`, `contentHash`, `mimeType`, `width`, `height`, `sizeBytes`, `sortOrder`, `isPrimary`, `schemaVersion`, `revision`, `operationId`, `source`, `clientUpdatedAt`, `_serverUpdatedAt` ve `deletedAt` alanlarını kapsar. Belge ID/payload `photoId`, parent `vehicleId` ve owner UID invariant'ları doğrulanır. Download URL canonical veri değildir. Hard delete metadata uygulanmaz; silme `deletedAt` tombstone'udur. Bilinmeyen alanlar parser tarafından göz ardı edilir ve partial writer tarafından silinmez; desteklenmeyen schema kayıtları korunup health/receipt'e dönüştürülür.

Android/Firebase bağımsız saf Kotlin contract iki projede byte-identical'dır. Her iki kopyanın SHA-256 değeri `C761553AA71F35A4EF63E77B6BC21FA1DC93D6B4DA58880B669D4738CF1F3A98`'dir.

## 6. Storage path sözleşmesi

Pipeline çıktısı daima JPEG olduğu için canonical Storage yolu:

`users/{uid}/vehicles/{vehicleId}/photos/{photoId}.jpg`

Metadata yalnız `vehicleId`, `photoId`, 64 karakter lowercase SHA-256 `contentHash` ve `schemaVersion=1` teknik alanlarını taşır. Plaka, kullanıcı adı, e-posta, kişi adı, not, orijinal dosya adı veya GPS verisi Storage metadata'sına yazılmaz. Aynı operation tekrarlandığında aynı `photoId` ve aynı path kullanılır.

## 7. Yerel hazırlama pipeline'ı

Fotoğraf ekleme Compose ekranından remote çağrı yapmaz. Akış; URI/MIME erişim doğrulaması, bounds okuma, sample-size ile decode, EXIF orientation uygulama, resize, temiz JPEG re-encode, SHA-256 üretme ve app-private UID/vehicle klasörüne atomik prepared dosya yazma adımlarından oluşur. Room photo ile upload operation aynı transaction içinde oluşturulur ve UI prepared dosyayı `PENDING_UPLOAD` durumunda hemen gösterebilir.

Stream'ler `use` ile kapatılır, ağır işlem IO dispatcher'da yürür ve `CancellationException` yeniden fırlatılır. Kaynak erişilemezliği, decode, boyut, MIME, hazırlama, retryable upload ve fatal upload ayrı typed domain sonuçlarına çevrilir.

## 8. Resize ve compression

Maksimum uzun kenar 2048 px'dir. Bounds önce okunur; tam boyutlu bitmap gereksiz yere decode edilmez. JPEG kalite hedefi 85, 5 MiB hazırlanan dosya sınırına sığmayan sonuç için kontrollü kalite düşürme hedefi 72'dir. Sonuç hâlâ sınırı aşarsa teknik exception yerine typed `PhotoTooLarge` sonucu üretilir. Girdi JPEG, PNG veya WebP olabilir; remote çıktı ve MIME `image/jpeg`'dir.

## 9. EXIF temizliği

Orientation 90/180/270 dönüşümleri bitmap'e uygulanır. Hazırlanan dosya yeni JPEG olarak encode edildiği için kaynak EXIF bloğu kopyalanmaz; GPS koordinatı/irtifa/zaman, cihaz seri/owner alanları, user comment, unique image ID, orijinal dosya adı ve tarih metadata'sı taşınmaz. Re-encode sonrası orientation yalnız `UNDEFINED` veya `NORMAL` kabul edilir. Unit testler orientation, resize ve EXIF/GPS temizliğini doğrular.

## 10. Offline-first upload

Aktif UID ve aktif vehicle doğrulandıktan sonra prepared dosya, photo projection ve stabil operation birlikte yazılır. WorkManager yalnız `ownerUid`, `operationId`, `photoId` ve `vehicleId` ID'lerini taşır; gerçek payload Room'dan okunur. Worker operation'ı atomik claim eder, Storage upload'ı aynı path'e uygular, Firestore metadata'yı transaction/merge ile tamamlar, UID'yi remote çağrı sonrasında yeniden doğrular ve receipt/final state yazar.

Storage başarılı fakat Firestore metadata yazımı başarısızsa operation retry durumunda kalır ve aynı objeyi idempotent biçimde reconcile eder. Retryable/fatal ayrımı ve exponential backoff vardır. Duplicate worker veya aynı operation revision'ı ikinci kez artırmaz.

## 11. Delete ve tombstone

Silme local-first'tür: Room photo tombstone'u ve delete operation birlikte yazılır, UI kaydı etkin galeriden çıkarır, Firestore metadata tombstone edilir ve Storage objesi sonra silinir. Storage `object-not-found` idempotent başarıdır. Remote silme başarısızsa local tombstone kaybolmaz. Eski remote update tombstone'u diriltemez.

Silinen fotoğraf primary ise replacement aynı transaction'da en düşük `sortOrder`, sonra `createdAt`, sonra `photoId` ile seçilir; fotoğraf kalmadığında `primaryPhotoId=null` olur. Local prepared/cache dosyası remote sonuca bağlı güvenli temizlik aşamasında kaldırılır.

## 12. Primary fotoğraf ve sıralama

Canonical primary kaynağı vehicle belgesindeki sürümlü `primaryPhotoId + primaryPhotoRevision + primaryPhotoOperationId` üçlüsüdür. Photo belgelerindeki `isPrimary` hızlı projection/compatibility alanıdır; ikinci bağımsız canonical kaynak değildir. Primary seçme transaction'ı vehicle revision/operation'ını doğrular ve Room'da diğer fotoğrafları false yapar.

Sıralama local-first metadata operation'larıyla revision artırır. İki cihaz farklı primary seçerse revision, server timestamp ve operation ID sırasıyla deterministik kazanan belirlenir; kaybeden durum receipt/health üzerinden görünür kalır.

## 13. Conflict resolution

Politika sırası şöyledir:

1. Vehicle tombstone yeni upload/update'e karşı kazanır.
2. Photo tombstone eski update'e karşı kazanır; delete-wins uygulanır.
3. Yüksek revision kazanır.
4. Aynı revision'da `_serverUpdatedAt` kazanır.
5. Timestamp de eşitse lexicographic `operationId` deterministik tie-break'tir.

Upload sırasında delete operation oluşması upload'ı supersede eder. Primary yapılırken silinen fotoğraf primary olamaz. Unknown schema silinmez. Aynı content hash farklı `photoId` ile geldiyse kullanıcı niyeti korunur; otomatik dedup yapılmaz, yalnız duplicate-content health uyarısı oluşur. Araç tombstone olduğunda yeni upload remote'a gönderilmez ve pending işlem fatal/superseded sonuca dönüştürülür.

## 14. Process death ve WorkManager

Kalıcı Room outbox process death sonrasında yeniden okunur. Unique work adı UID, vehicle, photo ve operation ID'nin güvenli stabil birleşimidir. Worker atomik claim olmadan remote işlem yapmaz; claim süresi dolan retryable işlemler tekrar alınabilir. Worker başlangıç ve remote dönüşünde aktif Firebase UID'yi yeniden doğrular. Hızlı sonsuz retry yoktur; WorkManager exponential backoff kullanır.

## 15. Cache güvenliği

TopluTaşıma prepared dosyaları `filesDir/vehicle_photos/{uid}/{vehicleId}/...`, geçici cache'i `cacheDir/vehicle_photos/{uid}/{vehicleId}/...` altında tutulur. Bellek salt okunur cache'i aynı UID/vehicle kapsamını kullanır. Canonical-path containment kontrolleri path traversal ve scope karışmasını engeller.

Bellek cache anahtarı owner UID, vehicle ID, photo ID ve içerik hash/revision bilgisini kapsar; download URL cache kimliği değildir. Scope dışı `cachedPath` kabul edilmez ve `PHOTO_CACHE_SCOPE_MISMATCH` health kodu yazılır. UID ham biçimde loglanmaz.

## 16. Account switch

Auth değişiminde eski UID Flow'ları/işleri iptal edilir, photo/vehicle Room projection'ları temizlenir, image request state'i yenilenir ve eski UID'nin prepared/cache klasörleri kaldırılır. Worker operation owner UID ile aktif UID eşleşmezse remote işlem yapmaz. TopluTaşıma DI başlangıcında photo repository lazy çözümlendi; böylece oturum yokken eager DB açılması ve account-scope başlangıç çökmesi engellendi. Bu düzeltme sonrasında tam 46 testlik cihaz paketi geçti.

## 17. TopluTaşıma UI değişiklikleri

Araç editor/detail akışı genel, teknik, satın alma, notlar, assignment ve fotoğraf bilgilerini Drive domain üzerinden gösterir. Galeri; boş durum, picker ile ekleme, prepared local preview, pending/retry/fatal durumları, primary seçme, sıralama ve silme onayı sağlar. Fotoğraf işlemleri vehicle form transaction'ına karıştırılmaz. UI teknik exception metni göstermez; typed durumlar kullanıcı metinlerine çevrilir.

## 18. Bellek salt okunur profil

`LinkedVehicleSummaryEntity` model yılı, ülke, şanzıman, kasa, renk, güncel kilometre, `primaryPhotoId`, profile schema ve primary revision alanlarıyla genişletildi. `LinkedVehiclePhotoEntity` yalnız metadata/cursor/cache projection'ıdır. Bellek; ad, marka/model, model yılı, plaka, yakıt, şanzıman, renk, kilometre ve fotoğrafları gösterir.

Veri minimizasyonu kararı olarak Bellek projection'ına VIN, satın alma fiyatı ve araç notları alınmadı. Bellek vehicle/photo belgesine yazmaz; fotoğraf ekleme, silme, sıralama veya primary seçme UI'sı yoktur. TopluTaşıma yüklü değilse local salt okunur özet görünmeye devam eder.

## 19. Deep-link entegrasyonu

Mevcut sözleşme değişmedi: `toplutasima://drive/vehicle/{vehicleId}`. Bellek explicit package `com.example.toplutasima` ile yalnız stabil vehicle ID taşır; UID, plaka, kişi veya snapshot taşınmaz. TopluTaşıma route'u parse eder ve aktif UID + vehicle ID ile repository'den tekrar doğrular.

API 36.1 emulatoründe iki debug APK kuruldu ve `test-vehicle-6b` ile explicit VIEW intent'i gönderildi. `com.example.toplutasima/.MainActivity` top-resumed olarak doğrulandı ve süreç çökmedi. Production hesap/vehicle kullanılmadığı için yetkili gerçek fotoğraflı kayıt indirme akışı manuel E2E yapılmadı; bulunmayan/başka UID vehicle reddi instrumented/unit paketinde doğrulandı.

## 20. Firestore projection

TopluTaşıma photo pull ve Bellek salt okunur photo pull, her araç için ayrı listener yerine collection-group query kullanır. Sorgu `ownerUid` filtresi ile `_serverUpdatedAt` ve document path cursor'ı üzerinden initial/incremental çalışır. Her iki uygulamada page size 200'dür ve son sayfa dolu olduğu sürece pagination devam eder. Tombstone'lar transactional Room apply içinde korunur.

Bellek unknown vehicle/photo alanlarını yok sayar ve asla geri yazmaz. Unsupported veya invariant'ı bozuk photo metadata projection'a sessizce kabul edilmez; health/invalid kayıt akışına gider.

## 21. Storage Rules

`storage.rules` yalnız authenticated kullanıcının `users/{uid}/vehicles/{vehicleId}/photos/{photoId}.jpg` yoluna erişmesine izin verir. Yazım için maksimum 5 MiB, `image/jpeg`, güvenli ID biçimi, dosya adı/photo ID eşliği ve yalnız izin verilen dört teknik metadata anahtarı doğrulanır. Başka UID ve unauthenticated erişim reddedilir; owner delete'e izin verilir.

`firebase.json`, Firestore ve Storage emulator tanımlarını içerir. `firestore-rules-tests/vehiclePhotos.rules.test.mjs`; aynı UID read/write/delete, başka UID, unauthenticated, oversized, MIME, path ve metadata senaryolarını içerir. Kurallar production'a deploy edilmedi.

## 22. Health kontrolleri

TopluTaşıma health katmanına `PHOTO_STORAGE_OBJECT_MISSING`, `PHOTO_METADATA_MISSING`, `PHOTO_UPLOAD_STUCK`, `PHOTO_DELETE_STUCK`, `PHOTO_INVALID_DIMENSIONS`, `PHOTO_INVALID_SIZE`, `PHOTO_UNSUPPORTED_MIME_TYPE`, `PHOTO_DUPLICATE_CONTENT`, `PHOTO_PRIMARY_CONFLICT`, `PHOTO_ORPHANED_FROM_VEHICLE`, `PHOTO_UNSUPPORTED_SCHEMA` ve `PHOTO_CACHE_SCOPE_MISMATCH` eklendi.

Health taraması Storage objesini otomatik silmez. Yalnız primary projection/mirror ve tamamlanmış operation state'i gibi deterministik, geri izlenebilir reconciliation uygulanır.

## 23. Receipt ve provenance

Her remote operation sonucu UID, receipt ID, operation/photo/vehicle ID, kind, status, provenance, revision, winning operation, attempt count, zamanlar ve güvenli hata koduyla saklanır. Ham URI, local path, download URL, plaka, kişi adı veya payload loglanmaz. Duplicate operation aynı receipt/operation kimliğiyle idempotent sonuçlanır.

## 24. Feature gate'ler

TopluTaşıma build-time gate'leri `DRIVE_VEHICLE_PHOTOS` ve `DRIVE_EXTENDED_VEHICLE_PROFILE`; Bellek gate'i `DRIVE_READ_ONLY_VEHICLE_PROFILE`'dır. Debug yapılandırmasında üçü de açıktır. Gate kapalıyken photo worker/collector/Storage erişimi ve ilgili UI başlamaz; Bellek photo hydration yapmaz. Additive Room tabloları rollback veya gate-off durumda DB'de kalır, fakat yeni kayıt üretilmez.

## 25. TopluTaşıma Room migration

Database version 11'den 12'ye additive migration uygulanmıştır. Vehicle tablosuna profil/primary alanları eklenmiş; `drive_vehicle_photos`, `drive_photo_operations`, `drive_photo_sync_metadata` ve `drive_photo_sync_receipts` ile UID, vehicle, state, tombstone, sıra ve cursor index'leri oluşturulmuştur. Destructive migration ve `fallbackToDestructiveMigration` yoktur.

Gerçek KSP/Room export'u `app/schemas/com.example.toplutasima.data.local.AppDatabase/12.json` dosyasıdır: 69.753 byte, SHA-256 `A8908B7DFE48721307103F4122B5BC4A6BE08D661904E198F69C8D5F5E960AA8`. 11→12 migration instrumented paket içinde geçti.

## 26. Bellek Room migration

Database version 25'ten 26'ya additive migration uygulanmıştır. `linked_vehicle_summaries` salt okunur profil/primary alanlarıyla genişletilmiş ve `linked_vehicle_photos` UID kapsamlı tablosu/index'leri eklenmiştir. Existing assignment/outbox/receipt tabloları korunmuştur.

Gerçek KSP/Room export'u `app/schemas/com.example.bellek.data.local.AppDatabase/26.json` dosyasıdır: 70.984 byte, SHA-256 `9F1FB431F5ACEACF8F687A23C88FF6D96BFE3CBDB9D34925FAF938513ED60B9C`. 25→26 migration ile photo DAO UID/Flow testleri hedefli instrumented koşuda 3/3 geçti. Legacy `9.json` ve `10.json` uydurulmadı.

## 27. Değiştirilen dosyalar — TopluTaşıma

Tracked değişiklikler:

- `app/build.gradle.kts`
- `app/src/androidTest/java/com/example/toplutasima/DatabaseMigrationTest.kt`
- `app/src/main/java/com/example/toplutasima/data/local/AppDatabase.kt`
- `app/src/main/java/com/example/toplutasima/data/local/entity/DriveVehicleEntity.kt`
- `app/src/main/java/com/example/toplutasima/di/DriveFeatureModule.kt`
- `app/src/main/java/com/example/toplutasima/drive/DriveFeatureFlags.kt`
- `app/src/main/java/com/example/toplutasima/drive/data/DriveEntityMappers.kt`
- `app/src/main/java/com/example/toplutasima/drive/data/remote/DriveFirestoreDataSource.kt`
- `app/src/main/java/com/example/toplutasima/drive/health/DriveHealthChecker.kt`
- `app/src/main/java/com/example/toplutasima/drive/model/DriveAdvancedModels.kt`
- `app/src/main/java/com/example/toplutasima/drive/model/DriveModels.kt`
- `app/src/main/java/com/example/toplutasima/drive/provenance/DriveLocalProvenance.kt`
- `app/src/main/java/com/example/toplutasima/drive/provenance/DriveProvenance.kt`
- `app/src/main/java/com/example/toplutasima/drive/repository/OfflineFirstDriveRepository.kt`
- `app/src/main/java/com/example/toplutasima/drive/sync/DriveAccountScopeManager.kt`
- `app/src/main/java/com/example/toplutasima/drive/sync/RoomDriveSyncRepository.kt`
- `app/src/main/java/com/example/toplutasima/drive/ui/DriveUiState.kt`
- `app/src/main/java/com/example/toplutasima/drive/ui/DriveUiText.kt`
- `app/src/main/java/com/example/toplutasima/drive/ui/DriveVehicleDetailScreen.kt`
- `app/src/main/java/com/example/toplutasima/drive/ui/DriveVehicleEditorScreen.kt`
- `app/src/main/java/com/example/toplutasima/drive/ui/DriveVehiclesScreen.kt`
- `app/src/main/java/com/example/toplutasima/drive/ui/DriveViewModel.kt`
- `app/src/main/java/com/example/toplutasima/drive/validation/DriveValidation.kt`
- `app/src/main/java/com/example/toplutasima/ui/Strings.kt`
- `app/src/test/java/com/example/toplutasima/data/local/AppDatabaseMigrationTest.kt`
- `firebase.json`
- `firestore-rules-tests/package.json`
- `firestore-rules-tests/vehicleAssignments.rules.test.mjs`
- `firestore.rules`
- `gradle/libs.versions.toml`

Yeni dosyalar:

- `SPRINT6B_COMPLETION_REPORT.md`
- `app/schemas/com.example.toplutasima.data.local.AppDatabase/12.json`
- `app/src/main/java/com/example/toplutasima/data/local/dao/DriveVehiclePhotoDaos.kt`
- `app/src/main/java/com/example/toplutasima/data/local/entity/DriveVehiclePhotoEntities.kt`
- `app/src/main/java/com/example/toplutasima/drive/photo/AndroidVehiclePhotoPreparer.kt`
- `app/src/main/java/com/example/toplutasima/drive/photo/OfflineFirstVehiclePhotoRepository.kt`
- `app/src/main/java/com/example/toplutasima/drive/photo/VehiclePhotoFileStore.kt`
- `app/src/main/java/com/example/toplutasima/drive/photo/VehiclePhotoModels.kt`
- `app/src/main/java/com/example/toplutasima/drive/photo/VehiclePhotoRemoteDataSource.kt`
- `app/src/main/java/com/example/toplutasima/drive/photo/VehiclePhotoRepository.kt`
- `app/src/main/java/com/example/toplutasima/drive/photo/VehiclePhotoSyncCoordinator.kt`
- `app/src/main/java/com/example/toplutasima/drive/photo/VehiclePhotoWorkScheduler.kt`
- `app/src/main/java/com/example/toplutasima/drive/ui/VehiclePhotoGallerySection.kt`
- `app/src/main/java/com/example/toplutasima/drive/ui/VehiclePhotoViewModel.kt`
- `app/src/main/java/com/example/toplutasima/worker/VehiclePhotoSyncWorker.kt`
- `app/src/main/java/shared/vehiclephoto/contract/VehiclePhotoContract.kt`
- `app/src/test/java/com/example/toplutasima/data/local/VehiclePhotoDaoTest.kt`
- `app/src/test/java/com/example/toplutasima/drive/photo/AndroidVehiclePhotoPreparerTest.kt`
- `app/src/test/java/com/example/toplutasima/drive/photo/OfflineFirstVehiclePhotoRepositoryTest.kt`
- `app/src/test/java/shared/vehiclephoto/contract/VehiclePhotoContractTest.kt`
- `firestore-rules-tests/vehiclePhotos.rules.test.mjs`
- `storage.rules`

## 28. Değiştirilen dosyalar — Bellek

Tracked değişiklikler:

- `app/build.gradle.kts`
- `app/src/androidTest/java/com/example/bellek/data/local/AppDatabaseMigrationTest.kt`
- `app/src/main/java/com/example/bellek/MainActivity.kt`
- `app/src/main/java/com/example/bellek/data/local/AppDatabase.kt`
- `app/src/main/java/com/example/bellek/data/local/dao/DrivePersonLinkDaos.kt`
- `app/src/main/java/com/example/bellek/data/local/entity/DrivePersonLinkEntities.kt`
- `app/src/main/java/com/example/bellek/di/AppModule.kt`
- `app/src/main/java/com/example/bellek/drive/DrivePersonLinksFeature.kt`
- `app/src/main/java/com/example/bellek/drive/VehicleIntegrationSyncScheduler.kt`
- `app/src/main/java/com/example/bellek/drive/sync/VehicleIntegrationRemoteDataSource.kt`
- `app/src/main/java/com/example/bellek/drive/sync/VehicleIntegrationSyncCoordinator.kt`
- `app/src/main/java/com/example/bellek/drive/ui/LinkedVehiclesSection.kt`
- `app/src/main/java/com/example/bellek/drive/ui/LinkedVehiclesViewModel.kt`
- `app/src/main/java/com/example/bellek/worker/VehicleIntegrationSyncWorker.kt`

Yeni dosyalar:

- `app/schemas/com.example.bellek.data.local.AppDatabase/26.json`
- `app/src/androidTest/java/com/example/bellek/drive/VehiclePhotoProjectionDaoInstrumentedTest.kt`
- `app/src/main/java/com/example/bellek/data/local/dao/LinkedVehiclePhotoDao.kt`
- `app/src/main/java/com/example/bellek/drive/ReadOnlyVehiclePhotoRepository.kt`
- `app/src/main/java/shared/vehiclephoto/contract/VehiclePhotoContract.kt`
- `app/src/test/java/shared/vehiclephoto/contract/VehiclePhotoContractTest.kt`

## 29. Unit test sonuçları

| Proje | Komut | Sonuç | XML toplamı |
|---|---|---|---:|
| TopluTaşıma | `gradlew.bat testDebugUnitTest --rerun-tasks` | Başarılı | 477 test, 0 hata, 0 skip |
| Bellek | `gradlew.bat testDebugUnitTest --rerun-tasks` | Başarılı | 114 test, 0 hata, 0 skip |

Contract testleri iki projede field/path/schema, enum fallback, unknown field, tombstone, ID invariant, revision ve deterministic conflict/idempotency fixture'larını çalıştırır. TopluTaşıma ayrıca preparer/EXIF/orientation/resize/hash, photo DAO ve offline-first repository upload/delete/primary/retry senaryolarını kapsar.

## 30. Instrumented test sonuçları

| Proje/koşu | Cihaz | Sonuç |
|---|---|---|
| TopluTaşıma tam `connectedDebugAndroidTest` | Medium Phone API 36.1, Android 16 / SDK 36 | 46/46 başarılı, 0 skip |
| Bellek tam `connectedDebugAndroidTest` | Aynı emulator | 14/16 başarılı; yalnız legacy 9→10 ve 10→11 testleri başarısız |
| Bellek hedefli Sprint 6B migration + photo DAO | Aynı emulator | 3/3 başarılı, 0 skip |

TopluTaşıma tam koşusu 11→12 migration ve mevcut deep-link/UID izolasyon testlerini içerir. Bellek hedefli koşu `migration25To26PreservesProjectionAndCreatesUidScopedPhotoSchema` ile `VehiclePhotoProjectionDaoInstrumentedTest` testlerini içerir. Tam Bellek komutunun başarısız exit code'u başarı olarak yeniden sınıflandırılmamıştır.

## 31. Storage Rules test sonuçları

Hazır komut:

`firebase --config ../firebase.json emulators:exec --only firestore,storage --project toplutasima-sprint6b-test "node vehicleAssignments.rules.test.mjs && node vehiclePhotos.rules.test.mjs"`

Makinede `node`, `npm` ve `firebase` komutları yoktur; `firestore-rules-tests/node_modules` da bulunmamaktadır. Bu nedenle gerçek Firebase/Storage emulator testi **çalıştırılamadı**. JVM/static shape kontrolü emulator başarısı olarak raporlanmamıştır. Production Rules deploy'u veya production Storage erişimi yapılmadı.

## 32. Build ve lint sonuçları

| Proje | Doğrulama | Son final sonuç |
|---|---|---|
| TopluTaşıma | `compileDebugKotlin` | Başarılı (2 dk 20 sn) |
| TopluTaşıma | `testDebugUnitTest --rerun-tasks` | Başarılı (11 dk 58 sn) |
| TopluTaşıma | `lintDebug` | Başarılı (9 dk 38 sn) |
| TopluTaşıma | `assembleDebug --rerun-tasks` | Başarılı (11 dk 40 sn) |
| TopluTaşıma | `assembleDebugAndroidTest --rerun-tasks` | Başarılı (9 dk 37 sn) |
| TopluTaşıma | `connectedDebugAndroidTest` | Başarılı (7 dk 41 sn) |
| Bellek | `compileDebugKotlin` | Başarılı (53,5 sn) |
| Bellek | `testDebugUnitTest --rerun-tasks` | Başarılı (6 dk 43 sn) |
| Bellek | `lintDebug` | Başarılı (5 dk 39 sn) |
| Bellek | `assembleDebug --rerun-tasks` | Başarılı (7 dk 6 sn) |
| Bellek | `assembleDebugAndroidTest --rerun-tasks` | Başarılı (6 dk 12 sn) |
| Bellek | `connectedDebugAndroidTest` | Başarısız: yalnız iki pre-existing legacy schema testi |

Windows pagefile error 1455 nedeniyle bazı önceki Gradle denemeleri tekrarlandı; yalnız yukarıdaki tamamlanmış final koşular sonuç kabul edildi. Kaynak değişikliklerinden sonra gerekli TopluTaşıma matrisi yeniden çalıştırıldı. Bellek'te final kaynak değişikliğinden sonra yukarıdaki matris çalıştırıldı.

## 33. APK yolları ve boyutları

| Artefakt | Boyut | SHA-256 |
|---|---:|---|
| `TopluTasima/app/build/outputs/apk/debug/app-debug.apk` | 28.107.875 byte | `B25EE2F8AE249B08623DBF201BB8B69167FAF4EC9B747065DD21D2C80455AD8E` |
| `TopluTasima/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | 1.116.581 byte | `2BEDC2D481C8764425BB863F29CF43D1C49C045D3BBB4F5C1150F9ED5A3EAAA5` |
| `Bellek/app/build/outputs/apk/debug/app-debug.apk` | 27.185.020 byte | `B0F38F30F4A40690A0723CEB33B2BC58E52DA66D46A48A08802B3F2BFE9B1F8F` |
| `Bellek/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | 924.109 byte | `B02B38C7D6591A66A78097E9A40BDB319C285AEF48840456223C8A0F14BB282E` |

İki debug APK da `emulator-5554` cihazına `adb install -r` ile başarıyla kuruldu.

## 34. Transit regresyonu

Transit entity/DAO/repository iş mantığı değiştirilmedi. TopluTaşıma compile, 477 unit, lint, APK ve 46 instrumented testlik tam paket geçti. Transit kapsamına GPS, Activity Recognition, foreground service, Quick Tile veya harita eklenmedi.

## 35. PersonalTrip regresyonu

PersonalTrip entity, DAO, repository, navigation, ORS ve ViewModel kaynakları değiştirilmedi. TopluTaşıma'nın tam unit/instrumented paketi ve lint/build matrisi geçti.

## 36. Sprint 6A regresyonu

Canonical kişi ID `PersonEntity.id`, canonical araç ID `DriveVehicle.id`, `vehicleAssignments`, assignment outbox/receipt ve compatibility mirror korunmuştur. Vehicle profile writer `assignedPersonId` alanını form snapshot'ından yazmaz. Shared photo contract assignment contract'ından ayrıdır. Deep-link sözleşmesi değişmemiş, explicit cihaz intent'iyle yeniden doğrulanmıştır. TopluTaşıma 46/46 instrumented ve iki projenin unit paketleri geçti.

## 37. Bellek regresyonu

Person CRUD, kişi fotoğrafı, arşiv, reminder, aile ağacı, countdown, existing outbox/tombstone ve `UserDataSessionRepository` için kapsam dışı refactor yapılmadı. Bellek compile, 114/114 unit, lint ve her iki APK build'i geçti. Tam instrumented pakette görülen iki hata yeni koddan önce var olan schema asset eksikleridir; diğer 14 test geçti ve yeni Sprint 6B testleri ayrıca 3/3 geçti.

## 38. Pre-existing test sorunları

Değiştirilmeden korunan iki test:

- `migration9To10CreatesFamilyTreeTablesAndKeepsPersons`: `com.example.bellek.data.local.AppDatabase/9.json` yok.
- `migration10To11CreatesNestedFolderMembershipSchemaAndMigratesFolderIds`: `com.example.bellek.data.local.AppDatabase/10.json` yok.

Testler silinmedi, `@Ignore` veya suppression eklenmedi, sahte schema JSON üretilmedi ve destructive migration kullanılmadı. Güvenilir eski schema kaynağı bulunmadığı için tam Bellek `connectedDebugAndroidTest` exit code'u başarısızdır. Gerçek `26.json` bu legacy eksikten bağımsız üretilip 25→26 testinde doğrulandı.

## 39. Bilinen sınırlamalar

- Node.js/npm/Firebase CLI olmadığı için Firestore + Storage Rules emulator testi eksiktir.
- Production Firebase/Storage kullanılmadığından gerçek remote upload/download/delete E2E akışı çalıştırılmadı; repository/preparer deterministik fake ve yerel testlerle doğrulandı.
- Manuel inter-app doğrulama route dispatch ve uygulama açılışını kapsar; authenticated gerçek fotoğraflı vehicle fixture'ı kullanılmadı.
- Gerçek kamera donanımı ve sistem galeri picker'ı ile uçtan uca upload instrumented testi yapılmadı; URI hazırlama hattı unit testlerle doğrulandı.
- Bellek'in tam instrumented komutu legacy `9.json`/`10.json` eksikleri nedeniyle yeşil değildir; yeni Sprint 6B instrumented kapsamı yeşildir.
- Additive Sprint 6B tabloları feature gate kapatıldığında fiziksel olarak DB'de kalır.

## 40. Başlangıç ve bitiş Git durumu

Sprint 4, 5 ve 6A'dan gelen mevcut çalışma ağacı kullanıcı verisi olarak kabul edildi; hiçbir değişiklik geri alınmadı, formatlanmadı veya stage edilmedi. Bu devam oturumunun ilk yeniden kontrolünde:

- TopluTaşıma: 30 tracked diff, 22 untracked dosya, 0 staged. Bu 22 dosyadan biri başarısız ara JVM çalıştırmasından kalan `app/hs_err_pid19420.log` idi; çalışma alanı içinde doğrulanıp yalnız generated crash artefaktı olarak kaldırıldı.
- Bellek: 14 tracked diff, 6 untracked dosya, 0 staged.

Rapor eklendikten sonraki final durumda TopluTaşıma 30 tracked diff ve 22 untracked dosya (rapor dahil), Bellek 14 tracked diff ve 6 untracked dosya taşır; crash artefaktı yoktur. Final `git diff --check` iki projede de exit 0 verdi. Final staging sayısı iki projede de 0'dır. Git commit oluşturulmadı.

## 41. Genel değerlendirme

Araç ana verisi TopluTaşıma'da, Bellek görünümü salt okunur projection'da kalmıştır. Fotoğraf metadata'sı sürümlü/tombstone'lu, binary obje UID kapsamlı Storage yolunda, local işlemler Room outbox ve WorkManager üzerinden idempotent yürür. Account switch, conflict, primary, cache ve assignment sınırları kod/test düzeyinde korunmuştur.

Tam Bellek instrumented paketindeki iki pre-existing legacy schema hatası ve bu ortamda çalıştırılamayan Storage Rules emulator testi açık istisnalardır; başarı olarak gizlenmemiştir.

**Son durum: Tamamlandı, Storage emulator doğrulaması eksik**
