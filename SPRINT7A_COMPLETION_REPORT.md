# Sprint 7A Completion Report — Vehicle Ledger ve Kilometre Çekirdeği

Rapor tarihi: 23 Temmuz 2026  
Proje: `C:\Users\mehme\AndroidStudioProjects\TopluTasima`  
Ana kaynak: `VEHICLE_EXPENSE_MAINTENANCE_ANALYSIS_REPORT.md`

## 1. Yönetici özeti

Sprint 7A için TopluTaşıma içinde sürümlü ve UID kapsamlı bir vehicle-ledger çekirdeği eklendi. Canonical kilometre geçmişi, genel araç giderleri ve araç hatırlatmaları Room üzerinde local-first çalışır; kalıcı operasyon kuyruğu, receipt, provenance, cursor, conflict shadow ve health katmanlarıyla Firestore senkronizasyonuna hazırlanır. `DriveVehicle.currentOdometerKm` silinmedi; canonical kaynaktan türetilen, ayrı ve izlenebilir bir compatibility mirror olarak sınırlandı.

Saf Kotlin contract, Room 12→13 additive migration, gerçek Room schema export, Firestore sözleşmesi/rules, feature gate ve minimum Compose/navigation akışı tamamlandı. Final JVM paketi 505/505 başarılıdır; lint ve iki APK build'i başarılıdır. Instrumented test APK'sı üretildi, ancak SDK `adb.exe` erişimi sandbox tarafından engellendi ve dış-sandbox çağrı onay kotasında proses başlamadan reddedildiği için cihaz testleri yürütülemedi. Node.js, npm ve Firebase CLI bulunmadığından Rules Emulator testi de yürütülemedi. Bu iki doğrulama eksikliği nedeniyle sonuç “kısmen tamamlandı” olarak sınıflandırılmıştır.

Production Firebase/Storage'a bağlanılmadı veya veri yazılmadı. Bellek projesi değiştirilmedi. Git staging ve commit yapılmadı.

## 2. Uygulanan kapsam

- Firebase/Android bağımlılığı olmayan `vehicleledger` Kotlin contract'ı.
- Canonical `VehicleOdometerEntry`, `VehicleExpense` ve `VehicleReminder` modelleri.
- UID kapsamlı yedi Room ledger tablosu ve 12→13 additive migration.
- Kalıcı generic ledger outbox, atomik claim, retry/fatal ayrımı ve exponential backoff.
- Initial/incremental Firestore pull; koleksiyon başına cursor, 200 kayıtlık sayfalama ve document-ID tie-break.
- Whole-record conflict resolution, tombstone/delete-wins, idempotent operation ve conflict shadow.
- Receipt, existing provenance entegrasyonu ve health taraması.
- Legacy `currentOdometerKm` backfill ile canonical current projection/mirror sınırı.
- Expense summary Flow'ları; para birimi ve exponent bazında ayrı toplamlar.
- Araç detay dashboard kartları ve kilometre/gider/hatırlatma route'ları.
- `DRIVE_VEHICLE_LEDGER` build-time feature gate.
- Üç yeni Firestore collection için explicit rules ve emulator test senaryoları.

## 3. Kapsam dışı bırakılanlar

İstek doğrultusunda yakıt, elektrik şarjı, tüketim hesabı, bakım/servis, servis item'ları, belge/fatura fotoğrafı, Storage, Driversnote, GPS, Activity Recognition, foreground service, Cloud Functions, döviz kuru/dönüşüm, PDF/OCR ve Bellek projection'ı eklenmedi. Android alarm/notification scheduler da bu çekirdek sprintte oluşturulmadı.

## 4. Ortak ledger sözleşmesi

`shared.vehicleledger.contract` paketi yalnız saf Kotlin sabitleri, enum'lar, contract modelleri, parser/serializer, validation ve deterministic conflict resolver içerir. Android, Room, Firebase, Compose ve repository bağımlılığı yoktur.

Ortak envelope alanları `ownerUid`, `vehicleId`, `schemaVersion`, `revision`, `operationId`, `source`, `createdAt`, `clientUpdatedAt`, `serverUpdatedAt` projection'ı ve `deletedAt` olarak tanımlandı. İlk schema version `1`; revision `Long`, en az `1`; operation ID UUID ve retry sırasında stabildir. Source değerleri `MANUAL`, `MIGRATED`, `SYSTEM_DERIVED`, `IMPORTED`, `UNKNOWN` olup bilinmeyen remote değerler kayıt kaybına yol açmadan `UNKNOWN` olarak parse edilir.

Conflict sırası owner/path invariant → vehicle tombstone → aynı operation ID → tombstone → yüksek revision → server timestamp → lexicographic operation ID'dir. Finansal kayıtlar field-level merge edilmez; whole-record winner uygulanır ve kaybeden local aday conflict shadow'da korunur.

## 5. Odometer modeli

Canonical anahtar `(ownerUid, odometerEntryId)`, canonical araç kimliği `DriveVehicle.id`'dir. Model; `observedAt`, integer `odometerMeters`, quality, reading role, series, source record bağlantısı, correction/reset alanları, notes ve ortak ledger envelope'unu içerir.

- Negatif metre reddedilir; canonical floating point kullanılmaz.
- Current seçiminde aktif seride en yeni `observedAt` tarihli confirmed kayıt kullanılır; eşitlik revision/server timestamp/operation ID ile deterministik çözülür.
- Confirmed yoksa estimated değer yalnız etiketli projection olarak dönebilir.
- Eski tarihli yüksek kilometre kaydı saklanır; sırf daha yüksek olduğu için current olmaz ve non-monotonic/implausible health üretilebilir.
- `sourceRecordType + sourceRecordId` duplicate'i unique constraint ile veri silmez; health uyarısıdır.
- Series/reset için typed repository sınırı bırakılmıştır; karmaşık series yönetim ekranı bu sprintte yoktur.

## 6. currentOdometer projection/backfill

`DriveVehicle.currentOdometerKm` korunmuştur fakat canonical değildir. Normal araç edit writer'ı ledger gate açıkken bu alanı göndermez; stale editor state canonical ledger'ı veya mirror'ı ezemez. Confirmed current metre değeri km projection'ına çevrilerek ayrı `ODOMETER_MIRROR` operation ile best-effort yazılır. Mirror başarısızlığı canonical mutation'ı başarısız saymaz ve `ODOMETER_MIRROR_STALE` ile izlenir.

Legacy backfill mevcut finite ve non-negative Double kilometreyi checked `BigDecimal` dönüşümüyle metreye çevirir. Deterministik UUID nedeniyle tekrar çalıştırma duplicate üretmez. Kaynağın tarihi bilinmiyorsa sahte `observedAt` üretilmez; kayıt `MIGRATED`/unknown-quality semantiğiyle saklanır. NaN, infinity, negatif ve overflow değerleri kayda çevrilmez; typed sonuç/health üretilir. `initialOdometerKm` için güvenilir tarih olmadan confirmed historical kayıt oluşturulmaz. İleride “ölçüm olarak kabul et” akışı için repository API sınırı bırakılmıştır.

## 7. Expense modeli

Canonical anahtar `(ownerUid, expenseId)`'dir. Kategoriler insurance, tax, registration fee, road fee, toll, parking, car wash, charging-unmetered, accessory, fine, other ve unknown değerlerini kapsar. Transaction kind `EXPENSE`, `REFUND`, `CREDIT`, `UNKNOWN` olarak ayrıdır.

Para `Long amountMinor + uppercase ISO-4217 currencyCode + currencyExponent` tuple'ı olarak saklanır; floating point para yoktur. `amountMinor > 0` zorunludur; refund/credit işareti amount'a yazılmaz, transaction kind'dan projection olarak türetilir. Period/due alanları epoch-day, odometer snapshot metre, split gider bağlantısı `splitGroupId` ile tutulur. Bir kaydın tek primary category'si vardır. Duplicate fingerprint yalnız aday sorgusu ve health üretir; otomatik merge/delete veya unique constraint uygulanmaz.

Summary sorguları vehicle/date range/category ile Room Flow üzerinden çalışır ve `currencyCode + currencyExponent` gruplarını birleştirmez. Firestore summary veya aylık rollup tablosu yazılmaz.

## 8. Reminder modeli

Canonical anahtar `(ownerUid, reminderId)`'dir. Type, status, due date/metre, recurrence ay/metre, anchor, lead, snooze, gelecekteki service bağlantıları, completion snapshot'ları, notes ve ortak envelope alanları bulunur.

En az due date veya due odometer zorunludur. İkisi birlikteyse herhangi bir eşik hatırlatmayı due yapar ve tetik nedeni projection'da tarih/kilometre olarak belirtilir. Recurrence tuple'ları typed validation'dan geçer. Repository create/update/complete/snooze/disable/tombstone/restore sınırlarını sunar; gerçek service completion entegrasyonu Sprint 7C'ye bırakılmıştır. Snoozed kayıtlar snooze gününden önce due sorgusuna girmez.

## 9. Room migration ve gerçek schema

Database version `12` → `13` yükseltildi. Migration yalnız `CREATE TABLE`/`CREATE INDEX` ile additive çalışır; destructive migration ve `fallbackToDestructiveMigration` yoktur. Finansal geçmişe cascade foreign key eklenmedi.

Yeni tablolar:

- `drive_odometer_entries`
- `drive_expenses`
- `drive_reminders`
- `drive_ledger_operations`
- `drive_ledger_sync_metadata`
- `drive_ledger_sync_receipts`
- `drive_ledger_conflicts`

PK'ler gereksinimdeki UID composite anahtarları kullanır. Vehicle/date, owner/vehicle, current selection, currency/category, due date/metre, operation state/next attempt, cursor, receipt ve conflict sorguları için index'ler eklendi. Upsert tarafında unique çakışmada başka kayıt silme riski taşıyan `REPLACE` yerine Room `@Upsert` kullanıldı.

Gerçek KSP/Room export: `app/schemas/com.example.toplutasima.data.local.AppDatabase/13.json`  
Version: `13`  
Identity hash: `9b9c7aa8ba3cd4701174b6e0e9363447`  
Entity sayısı: `24`  
Ledger tablosu: `7/7`  
Dosya boyutu: `112,036` byte

Dosya elle oluşturulmadı; final compile/assemble koşularında KSP tarafından yeniden doğrulandı.

## 10. Outbox/worker

`drive_ledger_operations` owner UID, stabil operation ID, logical batch, entity/record/vehicle ID, kind, target revision, state, attempt/next-attempt, claim owner/zamanı, safe error code ve timestamps taşır. Sensitive entity snapshot'ı, para, not, vendor veya kilometre worker input'una konmaz.

Repository mutation'ı ile entity projection ve operation aynı Room transaction'ında yazılır. Claim compare-and-set sorgusuyla atomiktir. On beş dakikadan eski `RUNNING` claim process-death recovery için yeniden aday olabilir. Retryable/fatal ayrımı ve exponential backoff vardır; retry operation ID/revision artırmaz. Mevcut Drive sync worker entrypoint'i ledger coordinator'ı gate açıkken çalıştırır. Worker başlangıcı, remote öncesi ve remote sonrası aktif UID yeniden doğrulanır. Vehicle tombstone child create/update'ı remote'a göndermez; superseded/conflict receipt ile kapanır.

## 11. Firestore sözleşmesi

Collection yolları:

- `users/{uid}/vehicleOdometerEntries/{odometerEntryId}`
- `users/{uid}/vehicleExpenses/{expenseId}`
- `users/{uid}/vehicleReminders/{reminderId}`

Payload canonical record ID ile document ID eşleşir; `ownerUid` aktif UID ve path UID ile aynı olmak zorundadır. Writer merge/partial update kullanır ve bilinmeyen gelecekteki alanları silmez. `_serverUpdatedAt` server timestamp'tir. Hard delete kullanılmaz; aynı document tombstone revision ile korunur.

Her collection ayrı cursor kullanır. Pull sırası `_serverUpdatedAt ASC + document ID`, page size 200'dür; tam sayfa geldiği sürece pagination devam eder. Remote sayfa ve cursor Room transaction'ında uygulanır. Unsupported schema silinmez; receipt/health üretir.

## 12. Rules durumu

`firestore.rules` içine üç explicit collection match'i eklendi. Same-auth UID, owner/path, document-ID/payload-ID, allowed field listesi, schema `1`, revision, UUID operation, integer canonical units, currency tuple ve request-time server timestamp doğrulanır. Hard delete reddedilir, tombstone update kabul edilir. Update revision artışı veya aynı operation ID şartına bağlıdır. Genel catch-all bu collection'ları exclusion listesiyle bypass edemez.

`firestore-rules-tests/vehicleLedger.rules.test.mjs` same UID allow, other UID/unauthenticated deny, ID/path/shape, integer units, currency, server timestamp, revision, tombstone ve hard-delete senaryolarını içerir. JVM `VehicleLedgerRulesShapeTest` yalnız statik guard'dır ve emulator testi yerine sayılmamıştır.

Final ortamda `node`, `npm` ve `firebase` komutları bulunmadı. Bu nedenle Rules Emulator çalıştırılmadı; sonuç **doğrulanamadı**, başarılı değildir.

## 13. Conflict/tombstone

Whole-record resolver gereksinimdeki deterministik sırayı uygular. Local kaybeden candidate JSON conflict shadow'da kalır; sessizce silinmez. Tombstone eski veya eşit revision update'e karşı kazanır. Restore, daha yüksek revision ile aynı canonical document'ta yapılır. Aynı operation retry idempotent başarıdır. Remote hard delete güvenilir sync sinyali kabul edilmez ve uygulama hard delete üretmez.

`LEDGER_REMOTE_HARD_DELETE` health kodu sözleşmede ayrılmıştır; timestamp tabanlı incremental query hard-delete'i doğrudan gözleyemediğinden server-side delete event'i olmadan otomatik tespit bu sprintin bilinen sınırıdır.

## 14. Account switch

Repository Flow'ları auth UID değişiminde önce boş state yayınlar, sonra yeni UID scope'una bağlanır. Account-scope cleanup; ledger entity, operation, receipt, metadata/cursor ve conflict tablolarını eski UID için temizler. Existing Drive WorkManager cancellation/scheduling sınırı kullanılır. Worker operation owner UID ile aktif auth UID eşleşmeden remote erişim yapmaz ve remote sonrası UID'yi tekrar kontrol eder. Ham UID loglanmaz.

## 15. Health

Sözleşmede istenen tüm health kodları tanımlandı: genel ledger vehicle/owner/schema/hard-delete/conflict/operation/receipt/provenance kodları; odometer non-monotonic, jump, series, mirror, source duplicate ve legacy-invalid kodları; expense currency/period/duplicate/overflow kodları; reminder trigger/recurrence/overdue/stale kodları.

Scanner araç durumu, kilometre sırası/jump/series/source duplicate, mirror, provenance, currency/duplicate/overflow, reminder due/recurrence, stuck operation, missing receipt, unsupported schema ve unresolved conflict kontrollerini yapar. Health taraması finansal kayıt veya reminder silmez. Otomatik düzeltme yalnız yeniden üretilebilir mirror/sync projection sınırında tutulmuştur.

## 16. UI/navigation

Root gate `DRIVE_VEHICLE_LEDGER` debug build'de açıktır. Gate kapalıyken ledger kartları/route collector'ları, coordinator, backfill ve Firestore erişimi başlamaz; yeni ledger kaydı yazılmaz.

Araç detayında güncel kilometre, yaklaşan hatırlatmalar ve bu ayın currency-bazlı genel gider kartları gösterilir. Ayrı stabil route'lar:

- `drive/vehicle/{vehicleId}/odometer`
- `drive/vehicle/{vehicleId}/expenses`
- `drive/vehicle/{vehicleId}/reminders`

Route yalnız vehicle ID taşır; UID, plaka veya snapshot taşımaz ve ID formatını doğrular. Minimum ekranlar kilometre listesi/manuel ekleme, gider listesi-filtre-edit, reminder listesi-ekleme-snooze-complete-disable ile pending/retry/conflict state'lerini içerir. Teknik exception metni doğrudan gösterilmez. Normal vehicle editor'daki current odometer girişi gate açıkken canonical ledger ekranına yönlendirir.

Deleted/archived vehicle mutasyonları repository seviyesinde reddedilir ve veriler korunur. Existing araç listesi tombstone kaydını detay route'unda göstermediği için özel deleted-vehicle read-only ekranı bu sprintte tam görsel cihaz doğrulamasından geçmemiştir.

## 17. Değiştirilen dosyalar

Sprint 7A için yeni ana dosyalar:

- `app/src/main/java/shared/vehicleledger/contract/VehicleLedgerContract.kt`
- `app/src/main/java/com/example/toplutasima/data/local/entity/DriveVehicleLedgerEntities.kt`
- `app/src/main/java/com/example/toplutasima/data/local/dao/DriveVehicleLedgerDaos.kt`
- `app/src/main/java/com/example/toplutasima/drive/ledger/VehicleLedgerModels.kt`
- `VehicleLedgerEntityMappers.kt`, `VehicleLedgerRepository.kt`, `OfflineFirstVehicleLedgerRepository.kt`
- `VehicleLedgerRemoteDataSource.kt`, `VehicleLedgerSyncCoordinator.kt`, `VehicleLedgerHealthScanner.kt`
- `app/src/main/java/com/example/toplutasima/drive/ledger/VehicleLedgerRoute.kt`
- `app/src/main/java/com/example/toplutasima/drive/ui/VehicleLedgerViewModel.kt`
- `app/src/main/java/com/example/toplutasima/drive/ui/VehicleLedgerScreen.kt`
- `app/schemas/com.example.toplutasima.data.local.AppDatabase/13.json`
- `firestore-rules-tests/vehicleLedger.rules.test.mjs`
- Sprint 7A unit/instrumented test dosyaları.

Sprint 7A için additive hunk eklenen existing dosyalar:

- `app/build.gradle.kts`
- `AppDatabase.kt`, `DriveVehicleDao.kt`
- `DriveFeatureModule.kt`, `DriveFeatureFlags.kt`
- `DriveFirestoreDataSource.kt`, `OfflineFirstDriveRepository.kt`
- `DriveAccountScopeManager.kt`, `RoomDriveSyncRepository.kt`
- `DriveVehicleDetailScreen.kt`, `DriveVehicleEditorScreen.kt`, `DriveVehiclesScreen.kt`
- JVM ve instrumented database migration testleri ile `RoomDriveSyncRepositoryTest.kt`
- `firestore.rules`, `firestore-rules-tests/package.json`.

Çalışma ağacındaki Sprint 6B photo dosyaları ve önceki dirty hunk'lar kullanıcı verisi olarak korundu; geri alınmadı, temizlenmedi, staged edilmedi ve Sprint 7A değişikliği gibi sahiplenilmedi.

## 18. Unit test sonuçları

Final komut: `gradlew.bat testDebugUnitTest --rerun-tasks` (tek worker, in-process Kotlin, düşük bellek profili).  
Sonuç: **BUILD SUCCESSFUL**, 9 dk 27 sn, 29/29 task executed.  
Toplam: **505 test, 0 failure, 0 error, 0 skipped**, 86 suite.

Sprint 7A ledger testleri: 27/27 başarılı:

- Contract: 11
- DAO: 5
- Offline-first repository: 6
- Sync coordinator: 3
- Health scanner: 1
- Rules static shape guard: 1

İlk tam unit denemesinde 505 testten biri, confirmed current verilirken stale reminder bekleyen yeni test fixture'ı nedeniyle başarısız oldu. Ürün kodu değiştirilmeden fixture'a confirmed-current olmayan ayrı senaryo eklendi; final tam paket baştan çalıştırıldı ve 505/505 geçti. Bu ilk başarısız koşu gizlenmemiştir.

JVM `AppDatabaseMigrationTest`: 4/4 başarılı; 12→13 preservation doğrulaması dahildir.

## 19. Instrumented test sonuçları

`assembleDebugAndroidTest --rerun-tasks` başarılıdır; yeni instrumented kaynaklar derlenip test APK'sına paketlenmiştir. Sprint 7A'ya özel 6 test metodu vardır: dört Room/UID/Flow/summary/reminder/route testi ve iki Compose/navigation testi. Existing `DatabaseMigrationTest` içindeki 12→13 testi de derlenmiştir.

`connectedDebugAndroidTest` ise **çalıştırılamadı**. PATH'te `adb` yoktu; SDK içindeki `adb.exe` bulundu fakat sandbox erişimi reddetti. Dış-sandbox Gradle çağrıları onay kotası nedeniyle proses başlamadan reddedildi. Dolayısıyla hiçbir instrumented test cihazda yürütülmüş veya başarılı sayılmış değildir.

## 20. Build/lint/APK sonuçları

- `compileDebugKotlin`: **başarılı**, final koşu 2 dk 11 sn.
- `testDebugUnitTest --rerun-tasks`: **başarılı**, 505/505.
- `lintDebug`: **başarılı**, 8 dk 9 sn; 0 error, 82 warning. Warning'ler baseline/suppression ile gizlenmedi; çoğunluğu existing dependency/UseKtx/version uyarılarıdır.
- `assembleDebug --rerun-tasks`: **başarılı**, 10 dk 12 sn, 40/40 task.
- `assembleDebugAndroidTest --rerun-tasks`: **başarılı**, 8 dk 31 sn, 56/56 task.
- `connectedDebugAndroidTest`: **çalıştırılamadı**, ortam erişim/onay kotası engeli.
- Rules Emulator: **çalıştırılamadı**, Node/npm/Firebase CLI yok.

APK'lar:

- `app/build/outputs/apk/debug/app-debug.apk` — 28,451,939 byte.
- `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` — 1,142,812 byte.

İlk final compile çağrısı PowerShell'in `-P` argümanını task adı gibi ayırması nedeniyle Gradle kaynak task'ına başlamadan başarısız oldu; argümanlar diziyle geçirilerek final compile başarıyla tekrarlandı. Daha önce görülen Windows error 1455 denemeleri final başarı sayılmadı; final koşular tek worker ve sınırlı JVM profiliyle tamamlandı.

## 21. Transit regresyonu

Final full unit suite içindeki Transit sınıflarında **211 test, 0 failure**. Transit entity/DAO/repository iş mantığı Sprint 7A kapsamında değiştirilmedi. Instrumented Transit testleri test APK'sına derlendi ancak cihazda yürütülmedi.

## 22. PersonalTrip regresyonu

Final full unit suite içindeki PersonalTrip sınıflarında **9 test, 0 failure**. PersonalTrip entity/DAO/repository, GPS, ORS, waypoint ve navigation kodu değiştirilmedi. Cihaz regresyonu yürütülemedi.

## 23. Sprint 6A regresyonu

Assignment odaklı unit sınıflarında **12 test, 0 failure**. Canonical `vehicleAssignments`, person ID, deep-link ve compatibility mirror sözleşmesi korunmuştur. Existing assignment sync, account cleanup ve Drive worker entegrasyonuna ledger yalnız gate'li/additive şekilde eklenmiştir. Instrumented Sprint 6A cihaz regresyonu yürütülemedi.

## 24. Sprint 6B regresyonu

Vehicle-photo odaklı unit sınıflarında **22 test, 0 failure**. Photo entity/outbox/Storage contract, gallery ve profile kodları geri alınmadı. Test APK'sı Sprint 6B instrumented kaynaklarıyla birlikte başarıyla üretildi; cihazda çalıştırılmadı. Sprint 7A hiçbir Storage veya belge fotoğrafı özelliği eklemedi.

## 25. Bilinen sınırlamalar

- Instrumented testler cihaz/emülatörde yürütülemedi; yalnız compile/package doğrulaması var.
- Firestore Rules Emulator çalıştırılamadı; test dosyaları hazır, JVM shape testi emulator yerine sayılmadı.
- Timestamp-cursor incremental pull remote hard-delete'i gözleyemez; canonical sözleşme hard delete'i yasaklar.
- Deleted vehicle için repository read-only/error davranışı vardır; ayrı tombstoned-detail UI cihazda doğrulanmadı.
- Android alarm/notification scheduling bu sprintin kapsamı dışında bırakıldı.
- Expense summary doğrudan Room Flow kullanır; büyük hesaplarda aylık materialized rollup yoktur. Bu bilinçli ilk sürüm kararıdır.
- Firestore pagination unit fake'leriyle doğrulandı; production Firebase'e bağlanılmadı.

## 26. Başlangıç/bitiş Git durumu

Başlangıç snapshot'ı:

- Status satırı: 45
- Tracked değişiklik: 30
- Untracked giriş: 23
- Staged: 0
- `git diff --check`: exit 0

Bitiş snapshot'ı final rapor yazımından sonra ayrıca doğrulanmıştır:

- Status satırı: 60
- Tracked değişiklik: 32
- Untracked giriş: 28
- Staged: 0
- `git diff --check`: exit 0

Git reset/clean/restore, staging ve commit yapılmadı. Existing dirty dosyalar korunmuştur.

## 27. Son durum

**Kısmen tamamlandı.**

Kaynak, saf contract, Room 12→13 migration, gerçek 13.json schema, offline-first repository/outbox/sync, Firestore rules sözleşmesi, health, UI/navigation, full unit, lint ve iki APK build'i tamamlandı. Kabulün cihaz/emülatör ve gerçek Rules Emulator doğrulamaları ortam kısıtı nedeniyle eksiktir; bu adımlar başarılı olarak sınıflandırılmamıştır.
