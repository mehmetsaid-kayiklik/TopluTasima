# Sprint 6A Completion Report

Tarih: 21 Temmuz 2026  
Projeler:

- `C:\Users\mehme\AndroidStudioProjects\TopluTasima`
- `C:\Users\mehme\AndroidStudioProjects\Bellek`

## 1. Yönetici özeti

Sprint 6A'nın iki uygulamalı kişi–araç ilişki omurgası uygulandı. Canonical ilişki artık
`users/{uid}/vehicleAssignments/{vehicleId}` belgesidir. İki uygulama da mutation'ı önce
UID-kapsamlı Room projection/outbox'a yazar, kalıcı worker daha sonra Firestore transaction
uygular. Assignment hard-delete edilmez; kaldırma aynı belge üzerinde tombstone'dur.

TopluTaşıma Room 10→11, Bellek Room 24→25 additive migration aldı. Bellek kişi detayına
çoklu araç listesi, arama, atomik reassignment onayı ve explicit-package deep link eklendi.
TopluTaşıma araç detayına consent filtreli kişi seçici ve harici `vehicleId` route'u eklendi.

Kod, unit test, lint, APK ve Android test APK derlemeleri başarılıdır. TopluTaşıma cihaz
paketi API 36.1 emülatörde 45/45 geçti. Bellek tam cihaz paketindeki yalnız iki failure,
sprint öncesinden eksik olan güvenilir `9.json` ve `10.json` Room schema asset'leridir;
yeni 24→25 migration testi ayrı cihaz koşusunda geçti. Firestore Rules Emulator bağımlılık
altyapısı eklendi ancak makinede Node/npm/Firebase CLI bulunmadığından çalıştırılamadı.
Authenticated bir test hesabı kullanılmadığı için inter-app route cihazda kabul edildi fakat
araç detayına değil güvenli login fallback'ine yönlendi.

## 2. Kesin mimari kararlar

- Kişi ana verisinin sahibi Bellek'tir.
- Araç ve DriveTrip ana verisinin sahibi TopluTaşıma Drive domain'idir.
- İlişkinin sahibi `vehicleAssignments` sözleşmesidir.
- Bellek araç snapshot'ı yazmaz; yalnız assignment ve best-effort compatibility mirror alanını
  `SetOptions.merge()` ile yazabilir.
- TopluTaşıma kişi snapshot'ı yazmaz.
- İmza eşitliği, ContentProvider veya paylaşılan Room veritabanı kullanılmaz.
- İki bağımsız Firebase oturumu aynı UID'yi ancak aynı Google hesabı seçilmişse üretir.

## 3. Canonical ID sözleşmeleri

- Canonical kişi kimliği `PersonEntity.id` ve normal sözleşmede
  `users/{uid}/persons/{personId}` document ID'sidir.
- Canonical araç kimliği `DriveVehicle.id` ve
  `users/{uid}/vehicles/{vehicleId}` document ID'sidir.
- Person payload `id` ile document ID uyuşmazsa kayıt directory'ye sessiz alınmaz;
  `PERSON_ID_DOCUMENT_ID_MISMATCH` health durumuna dönüşür.
- Kimlik ad, nickname, plaka veya başka değişebilir alandan türetilmez.
- Assignment içine kişi adı veya araç snapshot'ı kopyalanmaz.

## 4. `vehicleAssignments` Firestore sözleşmesi

Path: `users/{uid}/vehicleAssignments/{vehicleId}`  
Şema: v1

Alanlar:

- `vehicleId`
- `personId`
- `schemaVersion`
- `revision`
- `operationId`
- `source`: `BELLEK`, `TOPLU_TASIMA`, güvenli parser fallback'i `UNKNOWN`
- `clientUpdatedAt`
- `_serverUpdatedAt`: Firestore server timestamp
- `deletedAt`

Aktif kayıt `personId != null && deletedAt == null` şeklindedir. Kaldırma, önceki person ID'yi
audit amacıyla koruyan `deletedAt != null` tombstone'dur. Restore/reassignment aynı document ID
üzerinde revision artırır. Remote hard-delete üretilmez ve güvenilir değişiklik sinyali sayılmaz.

## 5. Shared Kotlin contract veya compatibility yaklaşımı

İki ayrı Gradle root kırılgan mutlak module bağıyla birleştirilmedi. Bunun yerine Android,
Firebase, Room ve Compose bağımlılığı olmayan versioned source contract iki projeye aynı
package altında dahil edildi:

`shared.vehicleassignment.contract.VehicleAssignmentContract`

İki kopya byte-for-byte eşittir. Doğrulanan SHA-256:
`8102992ca076f780e48ae18fe749427a34d31a6d69c41a02a6dce4664a4efe24`.

Contract; path/field sabitleri, source fallback'i, state, validation, revision/operation ID,
fallback parser, deterministic resolver ve golden map fixture'larını içerir. Android/Firebase
sınıfı, repository, UI veya Room entity içermez.

## 6. TopluTaşıma Room değişiklikleri

Database version 10→11 yükseltildi. Additive tablolar:

- `drive_vehicle_assignments`, PK `ownerUid + vehicleId`
- `drive_assignment_operations`, PK `ownerUid + operationId`
- `drive_assignment_sync_metadata`
- `drive_assignment_sync_receipts`

Owner, person, sync state, pending time, tombstone, operation ve cursor alanlarına indeksler
eklendi. `DriveVehicleDao.setAssignmentMirror()` yalnız projection alanını değiştirir; genel
vehicle revision/updatedAt değerini yükseltmez. Destructive migration kullanılmadı.

## 7. Bellek Room değişiklikleri

Database version 24→25 yükseltildi. Gerçek KSP export'u `app/schemas/.../25.json` üretildi.
Additive tablolar:

- `linked_vehicle_summaries`
- `bellek_vehicle_assignments`
- `bellek_assignment_operations`
- `vehicle_integration_sync_metadata`
- `bellek_assignment_sync_receipts`

`LinkedVehicleSummaryEntity` araç ana verisini salt okunur projection olarak tutar; araç adı,
marka/model, plaka, yakıt, vehicle tombstone, canonical assignment revision/tombstone/sync/health
ve server cursor alanlarını içerir. Birincil anahtar `ownerUid + vehicleId`'dir.

Bellek'in privacy-first `AppDatabase.clearAllTables()` akışı yeni integration tablolarını da
temizler. Eski UID integration row/cursor/receipt'leri yeni kullanıcı için restore edilmez.

## 8. Assignment offline-first akışı

Her iki uygulamada mutation akışı:

1. Feature gate ve aktif Firebase UID doğrulanır.
2. Local owner UID doğrulanır.
3. Araç aktifliği ve kişi canonical kimliği/consent durumu doğrulanır.
4. Bilinen en yüksek revision + 1 ve stabil UUID operation ID üretilir.
5. Assignment projection ve pending operation aynı Room transaction içinde yazılır.
6. UI yalnız Room Flow'dan güncellenir.
7. UID tekrar doğrulanır ve unique WorkManager işi planlanır.
8. Worker remote transaction uygular; retry/fatal/conflict state ve receipt kalıcı yazılır.

Compose içinden doğrudan Firestore çağrısı yapılmaz. `CancellationException` yeniden fırlatılır.

## 9. Initial hydration ve incremental pull

TopluTaşıma assignment sync, Drive worker sırasına ayrı coordinator olarak bağlandı. Bellek'te
vehicle summary ve assignment için ayrı initial/incremental stream metadata'sı vardır.

- Initial hydration document ID sırasıyla batch'lenir; server timestamp'i olmayan legacy kayıtlar
  da ilk geçişte görülür.
- Incremental pull `_serverUpdatedAt + document ID` sırasını kullanır.
- Batch boyutu 200'dür ve sayfa dolu olduğu sürece devam eder; tek batch ile sınırlı değildir.
- Remote apply ve cursor aynı Room transaction sınırında kalır.
- Unknown schema silinmez; health/fatal receipt üretir.
- Auth UID remote erişimden sonra tekrar doğrulanmadan Room apply yapılmaz.

## 10. Conflict resolution

Saf contract resolver ve sync coordinator şu sırayı uygular:

1. Vehicle tombstone assignment create/update'a karşı kazanır.
2. Daha yüksek assignment revision kazanır.
3. Aynı revision'da `_serverUpdatedAt` kazanır.
4. Timestamp eşitliğinde lexicographic `operationId` tie-break uygulanır.
5. Aynı operation ID idempotent başarıdır.

Assignment tombstone eski update'e karşı revision/timestamp sırasıyla kazanır. Remote winner yerel
pending mutation'ı sessiz silmez; operation `CONFLICT`, assignment conflictOperationId ve receipt
olarak korunur. Person tombstone araç veya assignment'ı otomatik silmez.

## 11. Idempotency ve revision

- Yeni mutation yerel assignment ve outbox'taki en yüksek target revision'ın bir fazlasını hedefler.
- Firestore transaction remote revision ve operation ID'yi okur.
- Aynı operation tekrar gelirse revision artırmadan `AlreadyApplied` döner.
- Receipt owner+operation üzerinde unique/idempotent yazılır.
- Retry mevcut outbox row'unu yeniden kullanır; yeni operation üretmez.
- Aynı revision/farklı operation conflict provenance ile kaydedilir.

## 12. Compatibility mirror davranışı

`DriveVehicle.assignedPersonId` kaldırılmadı; Room ve Firestore'da compatibility mirror olarak
korundu. Yeni kararlar önce canonical assignment belgesini kullanır.

- Genel vehicle writer artık `assignedPersonId` alanını snapshot map'e koymaz.
- Genel active/tombstone vehicle yazıları `SetOptions.merge()` kullanır; gelecekteki alanları silmez.
- Mirror yalnız partial merge ile best-effort yazılır.
- Assignment başarı, mirror failure yüzünden başarısız sayılmaz.
- TopluTaşıma reconciliation canonical assignment'tan stale mirror'ı düzeltir.

## 13. Bellek kişi detay araç bölümü

`PersonDetailScreen` Bilgiler sekmesine gate'li `LinkedVehiclesSection` eklendi. Bölüm:

- Bir kişiye bağlı birden fazla aracı gösterir.
- Araç adı, marka/model, yakıt ve plakayı gösterir.
- Pending/retry/conflict/fatal/health ve silinmiş araç durumunu gösterir.
- Temiz empty state sunar.
- Aktif araçları ad/plaka ile arar.
- Başka kişiye atanmış aracı açıklar ve atomik reassignment öncesi onay ister.
- Bağlantı kaldırır; araç ana verisini düzenlemez.

## 14. TopluTaşıma kişi seçimi

Araç detayına canonical `VehicleAssignmentViewModel/Section` bağlandı. Picker yalnız
`sharedWithTransit == true && archived == false` directory satırlarını seçilebilir gösterir.

Daha önce atanmış kişi artık paylaşılmıyor/silinmiş/erişilemiyorsa ID otomatik temizlenmez ve adı
yeniden çekilmez; kullanıcıya erişilemiyor mesajı ile kaldır/değiştir seçenekleri gösterilir.
Legacy vehicle editor'daki stale form alanı kaldırıldı; assignment artık araç snapshot formundan
yönetilmez. PersonalTrip repository/ViewModel bağı oluşturulmadı.

## 15. Deep-link sözleşmesi ve güvenliği

Route: `toplutasima://drive/vehicle/{vehicleId}`

Bellek intent'i:

- Explicit package `com.example.toplutasima` kullanır.
- Yalnız URI path'inde stabil `vehicleId` taşır.
- UID, plaka, kişi ID, snapshot, not veya extra taşımaz.
- Uygulama yoksa salt-okunur Bellek özeti kalır; uydurma mağaza URL'si yoktur.

TopluTaşıma parser scheme, authority, tam iki path segmenti, query/fragment yokluğu ve opaque ID
formatını doğrular. MainActivity external/BROWSABLE girişidir; route yetkilendirme sayılmaz.
Detay her zaman aktif UID altındaki Drive repository Flow'undan yüklenir; yok/silinmiş/başka hesap
durumunda hassas veri gösterilmez ve aynı Google hesabı açıklaması sunulur.

## 16. Hesap uyuşmazlığı ve account switch

UID link parametresinden alınmaz. TopluTaşıma assignment Flow'ları auth UID değişiminde
`flatMapLatest` ile iptal edilir. Account scope manager eski assignment/outbox/metadata/receipt
tablolarını temizler ve eski worker yeni UID adına çalışamaz.

Bellek UI Room açılmadan önce `UserDataSessionRepository.activateUser()` owner doğrulaması yapar.
UID değişiminde privacy-first local wipe yeni vehicle/assignment tablolarını da kapsar. Worker input
UID ile aktif Firebase/local owner UID'sini remote erişim öncesi ve sonrası karşılaştırır.

## 17. Kişi silme ve dangling assignment

Person silme cascade'ine araç veya assignment tablosu bağlanmadı. Assignment hard-delete veya
sessiz mirror null yapılmaz. TopluTaşıma person tombstone/share state refresh'i ve Bellek local
health scan'i assignment'ı dangling duruma getirir; kullanıcı düzeltme kararı verir.

## 18. Health, provenance ve receipt

TopluTaşıma health kodları:

- `ASSIGNED_PERSON_NOT_FOUND`
- `ASSIGNED_PERSON_DELETED`
- `ASSIGNED_PERSON_NOT_SHARED`
- `PERSON_ID_DOCUMENT_ID_MISMATCH`
- `ASSIGNMENT_VEHICLE_NOT_FOUND`
- `ASSIGNMENT_SCHEMA_UNSUPPORTED`

Operation receipt'leri applied, idempotent, retry, conflict ve fatal sonucu; source/provenance,
revision ve winning/conflicting operation ID'yi tutar. Teknik exception veya Firestore payload'ı UI'a
verilmez; loglara UID, plaka, kişi adı veya not eklenmedi.

## 19. Security Rules değişiklikleri ve testleri

`firestore.rules` içinde assignment path catch-all owner kuralından önce ayrıldı ve catch-all kuralı
`vehicleAssignments` koleksiyonunu açıkça hariç tutar.

Kurallar owner UID, document/payload vehicle ID, izinli alanlar, schema v1, nonnegative revision,
source enum, aktif person ID, tombstone shape ve `_serverUpdatedAt == request.time` doğrular.
Hard delete daima reddedilir. Create unknown field kabul etmez; update daha yeni istemciden kalmış
unknown alanı koruyabilir fakat v1 istemcinin onu değiştirmesine izin vermez.

Cross-document `get()` ile person/vehicle existence kontrolü rules'a eklenmedi; ek read maliyeti ve
offline mutation davranışı yerine transaction/repository health doğrulaması kullanıldı.

- JVM rules-shape testi başarılıdır.
- `firebase.json`, `firestore-rules-tests/package.json` ve Rules Unit Testing senaryosu eklendi.
- Node/npm/Firebase CLI makine PATH'inde bulunmadığından emulator rules testi çalıştırılmadı.
- Production Firebase'e test verisi yazılmadı.

## 20. Feature gate'ler

- Bellek: generated `BuildConfig.DRIVE_PERSON_LINKS`
- TopluTaşıma: generated `BuildConfig.DRIVE_PERSON_DIRECTORY`

Gate kapalı kod yolunda navigation/section görünmez, collector/repository boş Flow döndürür,
worker planlanmaz ve Firestore/Room integration mutation başlamaz. Additive Room tabloları rollback
sırasında fiziksel DB'de kalabilir; gate kapalıyken inerttir.

## 21. Değiştirilen dosyalar — Bellek

Sprint 6A tarafından oluşturulan ana dosyalar:

- `app/schemas/com.example.bellek.data.local.AppDatabase/25.json`
- `app/src/main/java/shared/vehicleassignment/contract/VehicleAssignmentContract.kt`
- `app/src/main/java/com/example/bellek/data/local/entity/DrivePersonLinkEntities.kt`
- `app/src/main/java/com/example/bellek/data/local/dao/DrivePersonLinkDaos.kt`
- `app/src/main/java/com/example/bellek/drive/DrivePersonLinksFeature.kt`
- `app/src/main/java/com/example/bellek/drive/VehicleAssignmentModels.kt`
- `app/src/main/java/com/example/bellek/drive/VehicleAssignmentRepository.kt`
- `app/src/main/java/com/example/bellek/drive/OfflineFirstVehicleAssignmentRepository.kt`
- `app/src/main/java/com/example/bellek/drive/VehicleIntegrationSyncScheduler.kt`
- `app/src/main/java/com/example/bellek/drive/sync/VehicleIntegrationRemoteDataSource.kt`
- `app/src/main/java/com/example/bellek/drive/sync/VehicleIntegrationSyncCoordinator.kt`
- `app/src/main/java/com/example/bellek/drive/ui/LinkedVehiclesViewModel.kt`
- `app/src/main/java/com/example/bellek/drive/ui/LinkedVehiclesSection.kt`
- `app/src/main/java/com/example/bellek/drive/ui/TransitVehicleDeepLink.kt`
- `app/src/main/java/com/example/bellek/worker/VehicleIntegrationSyncWorker.kt`
- `app/src/test/java/shared/vehicleassignment/contract/VehicleAssignmentContractTest.kt`
- `app/src/test/java/com/example/bellek/drive/TransitVehicleDeepLinkTest.kt`
- `app/src/androidTest/java/com/example/bellek/drive/VehicleProjectionDaoInstrumentedTest.kt`

Sprint 6A tarafından düzenlenen mevcut dosyalar:

- `app/build.gradle.kts`
- `app/src/main/java/com/example/bellek/MainActivity.kt`
- `app/src/main/java/com/example/bellek/data/local/AppDatabase.kt`
- `app/src/main/java/com/example/bellek/data/local/dao/PersonDao.kt`
- `app/src/main/java/com/example/bellek/di/AppModule.kt`
- `app/src/main/java/com/example/bellek/ui/screens/persons/PersonDetailScreen.kt`
- `app/src/androidTest/java/com/example/bellek/data/local/AppDatabaseMigrationTest.kt`

Diğer dirty Bellek dosyaları sprint başlangıcında zaten mevcuttu ve geri alınmadı.

## 22. Değiştirilen dosyalar — TopluTaşıma

Sprint 6A tarafından oluşturulan ana dosyalar:

- `app/src/main/java/shared/vehicleassignment/contract/VehicleAssignmentContract.kt`
- `app/src/main/java/com/example/toplutasima/data/local/entity/DriveVehicleAssignmentEntity.kt`
- `app/src/main/java/com/example/toplutasima/data/local/dao/DriveVehicleAssignmentDao.kt`
- `app/src/main/java/com/example/toplutasima/drive/assignment/*`
- `app/src/main/java/com/example/toplutasima/drive/ui/VehicleAssignmentViewModel.kt`
- `app/src/main/java/com/example/toplutasima/drive/ui/VehicleAssignmentSection.kt`
- `app/src/main/java/com/example/toplutasima/drive/ui/VehicleDeepLinkRoute.kt`
- `app/src/test/java/shared/vehicleassignment/contract/VehicleAssignmentContractTest.kt`
- `app/src/test/java/com/example/toplutasima/drive/assignment/FirestoreRulesShapeTest.kt`
- `app/src/test/java/com/example/toplutasima/drive/ui/VehicleDeepLinkRouteTest.kt`
- `app/src/androidTest/java/com/example/toplutasima/drive/VehicleAssignmentDaoInstrumentedTest.kt`
- `firebase.json`
- `firestore-rules-tests/*`

Sprint 6A'nın düzenlediği ana mevcut/Drive çalışma dosyaları:

- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/example/toplutasima/MainActivity.kt`
- `app/src/main/java/com/example/toplutasima/data/local/AppDatabase.kt`
- `app/src/main/java/com/example/toplutasima/data/local/dao/ProfileDao.kt`
- `app/src/main/java/com/example/toplutasima/network/firestore/FirestorePersonService.kt`
- `app/src/main/java/com/example/toplutasima/di/DriveFeatureModule.kt`
- `app/src/main/java/com/example/toplutasima/drive/DriveFeatureFlags.kt`
- `app/src/main/java/com/example/toplutasima/drive/data/remote/DriveFirestoreDataSource.kt`
- `app/src/main/java/com/example/toplutasima/drive/repository/OfflineFirstDriveRepository.kt`
- `app/src/main/java/com/example/toplutasima/drive/sync/DriveAccountScopeManager.kt`
- `app/src/main/java/com/example/toplutasima/drive/sync/RoomDriveSyncRepository.kt`
- `app/src/main/java/com/example/toplutasima/drive/health/DriveHealthChecker.kt`
- `app/src/main/java/com/example/toplutasima/drive/ui/DriveViewModel.kt`
- `app/src/main/java/com/example/toplutasima/drive/ui/DriveUiState.kt`
- `app/src/main/java/com/example/toplutasima/drive/ui/DriveVehiclesScreen.kt`
- `app/src/main/java/com/example/toplutasima/drive/ui/DriveVehicleDetailScreen.kt`
- `app/src/main/java/com/example/toplutasima/drive/ui/DriveVehicleEditorScreen.kt`
- `app/src/main/java/com/example/toplutasima/ui/navigation/MainAppScreen.kt`
- `app/src/main/java/com/example/toplutasima/worker/DriveSyncWorker.kt`
- migration ve sync repository testleri
- `firestore.rules`

Başlangıçta untracked olan geniş Drive çalışma seti korunmuş, silinmemiş ve mevcut kullanıcı işi
olarak ayrıca değerlendirilmiştir.

## 23. Room migration sürümleri ve sonuçları

- TopluTaşıma 10→11: additive migration; Android test APK derlendi, API 36.1 tam cihaz paketinde
  geçti. UID izolasyonu/tombstone row'ları test edildi.
- Bellek 24→25: additive migration; gerçek 24.json→25.json doğrulaması ayrı API 36.1 cihaz
  koşusunda geçti. Person row korundu, beş tablo ve UID izolasyonu doğrulandı.
- Destructive migration ve `fallbackToDestructiveMigration` kullanılmadı.

## 24. Unit test sonuçları — iki proje ayrı

TopluTaşıma:

- Komut: `gradlew.bat testDebugUnitTest --rerun-tasks`
- Sonuç: başarılı
- Sayım: 455 test, 0 failure, 0 error, 0 skipped
- Contract, resolver/idempotency fixture'ları, deep-link parser, rules shape ve Drive regresyon
  paketleri dahildir.

Bellek:

- Komut: `gradlew.bat testDebugUnitTest --rerun-tasks --no-daemon --no-parallel`
- Sonuç: başarılı
- Sayım: 105 test, 0 failure, 0 error, 0 skipped
- Contract fixture ve explicit intent testleri dahildir.

## 25. Instrumented test sonuçları — iki proje ayrı

TopluTaşıma:

- `assembleDebugAndroidTest --rerun-tasks`: başarılı
- `connectedDebugAndroidTest`: başarılı
- API 36.1: 45/45, 0 skipped, 0 failed

Bellek:

- `assembleDebugAndroidTest --rerun-tasks`: başarılı
- Tam `connectedDebugAndroidTest`: 13 test, 11 passed, 2 failed
- İki failure yalnız eksik legacy `9.json` ve `10.json` schema asset stack trace'leridir.
- Yeni `migration24To25CreatesUidScopedVehicleIntegrationWithoutLosingPersons` hedefli cihaz
  koşusu: 1/1 başarılı.
- Yeni projection DAO/Flow testi tam pakette failure üretmedi.

## 26. Inter-app deep-link test sonucu

- `adb devices -l`: `emulator-5554`, `Medium_Phone_API_36.1` hazır.
- TopluTaşıma debug APK install: başarılı.
- Bellek debug APK install: başarılı.
- Sentetik route:
  `toplutasima://drive/vehicle/sprint6a-test-vehicle`
- `adb shell am start -W ... com.example.toplutasima`: `Status: ok`, cold launch.
- Emülatörde authenticated test hesabı bulunmadığı için güvenli sonuç
  `com.example.toplutasima/.auth.LoginActivity` oldu; production UID/araç kaydı kullanılmadı.

Dolayısıyla manifest/parser/explicit route cihazda doğrulandı; Bellek UI tıklaması → authenticated
TopluTaşıma vehicle detail uçtan uca doğrulaması tamamlanmadı.

## 27. Build ve lint sonuçları

TopluTaşıma:

- `compileDebugKotlin`: başarılı
- `testDebugUnitTest --rerun-tasks`: başarılı
- `lintDebug`: başarılı; 0 error, mevcut kod tabanında 77 warning
- `assembleDebug --rerun-tasks`: başarılı
- `assembleDebugAndroidTest --rerun-tasks`: başarılı
- `connectedDebugAndroidTest`: başarılı
- `git diff --check`: başarılı; yalnız line-ending bilgilendirme uyarıları

Bellek:

- `compileDebugKotlin`: başarılı
- `testDebugUnitTest --rerun-tasks`: başarılı
- `lintDebug`: başarılı; 0 issue
- `assembleDebug --rerun-tasks`: başarılı
- `assembleDebugAndroidTest --rerun-tasks`: başarılı
- `connectedDebugAndroidTest`: yalnız iki bilinen legacy schema failure'ı nedeniyle task başarısız
- Yeni 24→25 hedefli connected test: başarılı
- `git diff --check`: başarılı; yalnız line-ending bilgilendirme uyarıları

## 28. APK yolları ve boyutları

TopluTaşıma:

- `app/build/outputs/apk/debug/app-debug.apk` — 27,517,660 byte (~26.24 MiB)
- `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` — 1,107,590 byte

Bellek:

- `app/build/outputs/apk/debug/app-debug.apk` — 27,119,484 byte (~25.86 MiB)
- `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` — 912,163 byte

## 29. PersonalTrip doğrulaması

PersonalTrip entity/DAO/repository/navigation/ViewModel iş mantığı değiştirilmedi. TopluTaşıma tam
455 unit ve 45 cihaz testi başarılıdır. Yeni assignment code PersonalTrip repository veya ViewModel'e
bağımlı değildir.

## 30. Transit regresyon doğrulaması

Transit entity/DAO/repository iş mantığı değiştirilmedi. Mevcut Transit testleri dahil TopluTaşıma
455 unit test ve API 36.1'de 45 instrumented test geçti. GPS, Activity Recognition, foreground
service, quick tile, waypoint veya ORS kapsamına yeni kod eklenmedi.

## 31. Bellek regresyon doğrulaması

Kişi CRUD, arşiv, hatırlatma, aile ağı, countdown, contact log, tombstone/outbox, Google sign-in,
fotoğraf ve `UserDataSessionRepository` davranışları geri alınmadı. Bellek 105/105 unit test geçti.
Cihaz paketindeki yeni sprint dışı failure yoktur; iki failure legacy schema eksikliğidir.

## 32. Pre-existing migration test durumu

Eksik dosyalar:

- `com.example.bellek.data.local.AppDatabase/9.json`
- `com.example.bellek.data.local.AppDatabase/10.json`

Güvenilir recovery için:

- Bellek proje ağacında eski build artifact tarandı: bulunamadı.
- `git log --all` ilgili path'lerde tarandı: commit bulunamadı.
- `git rev-list --objects --all` tüm Git object geçmişinde tarandı: schema object bulunamadı.

Testler silinmedi, `@Ignore` veya suppression eklenmedi, sahte JSON üretilmedi. Bu iki failure
`pre-existing known failure` olarak korunur. Gerçek KSP 25.json ve yeni 24→25 test sonucu başarılıdır.

## 33. Bilinen sınırlamalar

- Firestore Rules Emulator testi altyapısı mevcut fakat Node/npm/Firebase CLI olmadığı için bu
  makinede çalıştırılmadı.
- Authenticated iki uygulama/aynı UID ve local fixture ile vehicle detail'e tam inter-app cihaz
  geçişi yapılmadı; route login fallback'ine kadar doğrulandı.
- Bellek tam connected task'i iki legacy schema asset failure'ı nedeniyle yeşil değildir.
- Remote Firestore sync için production kullanılmadı. Contract/resolver, Room, worker wiring ve
  rules-shape test edildi; exhaustive fake/emulator remote matrix bu çalışmada tam otomasyona alınmadı.
- Feature gate kapatıldığında additive tablolar DB'de kalır; kullanılmaz.

## 34. Başlangıç/bitiş git durumu

Her iki projede başlangıçta dirty kullanıcı değişiklikleri vardı. TopluTaşıma'da özellikle büyük
untracked Drive/Sprint 4 çalışma seti, Bellek'te sync/date/retry alanlarında tracked ve untracked
değişiklikler bulunuyordu. Bunlar geri alınmadı, topluca formatlanmadı, stage edilmedi ve sprint
değişikliği gibi sahiplenilmedi.

Bitişte iki projede de `git status --short` çalıştırıldı; dirty çalışma ağaçları korunuyor.
`git diff --check` iki projede exit 0 verdi. Git commit oluşturulmadı ve hiçbir dosya stage edilmedi.

## 35. Genel değerlendirme

Canonical assignment mimarisi, iki uygulamanın Room katmanları, offline outbox, hydration,
conflict/idempotency, consent/dangling health, compatibility mirror, UI ve deep-link güvenlik
omurgası uygulanmış ve derlenmiştir. Unit/lint/APK doğrulamaları yeşildir; TopluTaşıma cihaz paketi
tam yeşildir. Bellek yeni migration'ı yeşildir ancak tam cihaz task'i legacy schema eksikleriyle
yeşil değildir. Rules emulator ve authenticated inter-app detail cihaz testi tamamlanmamıştır.

**Açık durum: Kısmen tamamlandı.**

