# Araç Giderleri, Yakıt ve Bakım — Mimari ve Veri Modeli Analizi

Bu belge yalnız read-only kaynak incelemesine dayanır. Bu çalışma sırasında üretim kaynağı, test, Room migration/schema, Gradle yapılandırması veya Firebase verisi değiştirilmemiş; build, test, emulator, Firebase bağlantısı, Git staging ve commit çalıştırılmamıştır. Driversnote import bu analizin kapsamı dışındadır; aşağıdaki import/export notları yalnız sağlayıcıdan bağımsız stabil veri sözleşmesi hazırlığıdır.

## 1. Yönetici özeti

Önerilen çözüm, gider verisini `DriveVehicle` belgesine veya tek bir geniş “her şey” tablosuna eklemek yerine sekiz ayrı fakat aynı senkronizasyon zarfını kullanan domain kaydıyla kurmaktır:

| Domain | Canonical kayıt | Karar |
|---|---|---|
| Sıvı yakıt | `VehicleFuelEntry` | Elektrik şarjından ayrı |
| Elektrik | `VehicleChargeEntry` | Yakıtla ortak UI projection'ı olabilir, ortak entity olmamalı |
| Bakım/tamir/muayene | `VehicleServiceRecord` | Lifecycle ve servis semantiğinin sahibi |
| Servis içeriği | `VehicleServiceItem` | Ayrı child entity; çok kalemli servis için zorunlu |
| Sigorta/vergi/otopark/yıkama/diğer | `VehicleExpense` | Tek primary kategori taşıyan genel finansal olay |
| Kilometre | `VehicleOdometerEntry` | Canonical ölçüm geçmişi |
| Hatırlatma | `VehicleReminder` | Servis kaydından ayrı schedule/lifecycle |
| Fatura/fiş görseli | `VehicleDocument` | Sprint 6B görsel hazırlama altyapısını kullanan ayrı metadata sözleşmesi |

Kesin kararlar:

- Mesafe canonical olarak `Long` metre, sıvı hacim `Long` mililitre, enerji `Long` Wh ve para `Long` minor unit olarak saklanmalıdır. Locale metni ve floating point canonical veri değildir.
- `DriveVehicle.currentOdometerKm` yeni sistemde canonical olmamalı; `VehicleOdometerEntry` kayıtlarından türetilen compatibility projection/mirror olmalıdır.
- Yakıt tüketimi yalnız güvenilir iki tam-depo anchor'ı arasında ve monoton kilometreyle hesaplanmalıdır. Kısmi dolumlar bir sonraki tam doluma kadar biriktirilmelidir.
- Elektrik tüketimi yakıt tüketimiyle birleştirilmemeli; PHEV için yakıt ve şarj metrikleri ayrı gösterilmelidir.
- Servis parent toplamı maliyet özetinin canonical tutarıdır. Item tutarları yalnız kırılımdır ve summary'de ikinci kez toplanmaz.
- Her gerçek finansal olay tek primary kategoriye sahip olmalıdır. Bir fiş bölünecekse tutarı bölünmüş birden fazla kayıt oluşturulmalı; aynı tutar birden fazla kategoriye sayılmamalıdır.
- Özetler Firestore'a canonical belge olarak yazılmamalıdır. İlk sürümde Room `Flow` + indeksli `UNION ALL/GROUP BY` sorguları; gerekirse yeniden üretilebilir yerel aylık rollup projection'ı kullanılmalıdır.
- Yeni domain sözleşmeleri Sprint 6A/6B'deki `schemaVersion + revision + operationId + tombstone + server timestamp` modelini kullanmalıdır. Mevcut vehicle/trip snapshot outbox'ı revision taşımadığı için doğrudan genişletilmemelidir.
- Araç tombstone'u yeni child create/update işlemini engeller; ancak mevcut gider, servis, kilometre ve belge metadata'sını silmez. Finansal geçmiş archived/read-only olarak korunur.
- Bellek'e bu sprint ailesinde ham gider, servis, fiş, tutar veya not projection'ı gönderilmemelidir.
- Çalışma beş alt sprint'e ayrılmalıdır. Driversnote bu beş alt sprintin de sonrasındadır.

## 2. Mevcut Drive mimarisi

İncelenen kaynaklar `SPRINT4_COMPLETION_REPORT.md`, `SPRINT6A_COMPLETION_REPORT.md`, `SPRINT6B_COMPLETION_REPORT.md`, Bellek entegrasyon analizi ve güncel Drive kaynaklarıdır. Çalışma ağacında veya Git geçmişinin Markdown dosya listesinde bağımsız bir `SPRINT5_COMPLETION_REPORT.md` bulunmamıştır. Bu nedenle Sprint 5'e karşılık gelen hydration, incremental pull, conflict, provenance ve receipt davranışı doğrudan mevcut kaynaklardan doğrulanmıştır.

Mevcut durum:

- Room database version 12'dir.
- Araç anahtarı `(userId, id)`, sürüş anahtarı `(userId, id)`; sürüş `vehicleId` ile araca `NO ACTION` bağlanır.
- Firestore yolları `users/{uid}/vehicles/{vehicleId}` ve `users/{uid}/driveTrips/{tripId}` şeklindedir.
- `DriveVehicle` ve `DriveTrip` mesafe/kilometre alanlarını `Double km` olarak tutar.
- `DriveVehicle.currentOdometerKm` manuel değerdir. `DriveVehicleSummaryCalculator`, gösterimde bu değeri `initialOdometerKm + aktif sürüş mesafesi` tahminine tercih eder; manuel değer tahminden düşükse health/UI tutarsızlığı gösterir fakat veri değiştirmez.
- Satın alma fiyatı `purchasePriceMinor: Long?` ve `currencyCode` ile tutulur. Validation negatif tutarı ve üç harfli kod biçimini reddeder. UI parse/display şu an iki ondalık basamak varsayar; bu, sıfır veya üç minor basamaklı ISO 4217 para birimleri için genelleştirilemez.
- Tarih/saat domain'de `Instant`, Room'da epoch millisecond `Long` olarak tutulur; UI locale'e göre `dd.MM.yyyy`/`dd.MM.yyyy HH:mm` gösterir.
- Vehicle/trip mutation önce Room transaction'ına, aynı transaction'da kompakt `DriveSyncOperationEntity` outbox'ına yazılır. Create→update compact edilir, delete-wins uygulanır ve WorkManager yalnız stabil ID/UID taşır.
- Vehicle/trip remote writer `SetOptions.merge()` ve `_syncOperationId` kullanır. Remote tombstone eski upsert'i engeller. Ancak generic Drive operation modelinde domain revision yoktur.
- Initial/incremental pull `_serverUpdatedAt + document ID` cursor'ı, transactional Room apply, field provenance ve local receipt kullanır.
- Sprint 6A assignment ve Sprint 6B photo sözleşmeleri daha güçlüdür: `schemaVersion`, `revision`, stabil `operationId`, server timestamp, deterministic tie-break, persistent outbox/receipt ve unsupported-schema health.
- Account scope manager Drive'a ait tüm tabloları aktif UID'ye göre temizler; worker başlangıçta ve remote işlem çevresinde UID doğrular.
- Araç detail ekranı tek `LazyColumn` içinde profil, fotoğraflar, assignment, kilometre özeti, provenance ve sürüşleri gösterir. Yeni gider domain'i tüm kayıtları bu ekrana yüklememelidir.
- Mevcut health sistemi araç/sürüş, assignment ve photo sorunlarını typed kodlarla raporlar; otomatik veri silmez.

Sonuç: Yeni finansal kayıtlar vehicle/trip'in revision'sız snapshot operation modeline eklenmemeli; assignment/photo deneyiminden türetilen tek bir sürümlü “vehicle ledger” senkronizasyon altyapısı kurulmalıdır.

## 3. Önerilen domain sınırları

Veri sahipliği sınırları:

| Sınır | Sahip | Açıklama |
|---|---|---|
| Araç profili | `DriveVehicle` | Marka, model, vehicle lifecycle ve compatibility projection'ları |
| Sürüş | `DriveTrip` | Manuel sürüş olayı; mali kayıt değildir |
| Enerji alımı | Fuel/charge domain | Yakıt ve elektrik ölçümü, maliyet ve tüketim anchor'ları |
| Servis | Service domain | Plan, tamamlanma, tamir, muayene ve item composition |
| Genel gider | Expense domain | Servis/yakıt dışındaki finansal olaylar |
| Kilometre | Odometer domain | Tüm kaynaklardan gelen canonical ölçüm olayları |
| Hatırlatma | Reminder domain | Tarih/km schedule; maliyet veya completion kaydı değildir |
| Binary belge | Document domain | Metadata, upload lifecycle ve parent bağlantısı |
| Özet | Yerel projection | Türetilmiş, silinip yeniden üretilebilir |

Tek bir genel expense entity'sine yakıt hacmi, şarj enerjisi, servis kalemleri ve reminder alanları eklemek önerilmez. Böyle bir model nullable alan patlaması, yanlış kombinasyonlar, zor validation ve yanlış summary üretir. Bunun yerine tüm kayıtlar ortak bir senkronizasyon zarfı ve ortak bir read-only “ledger row” UI projection'ı üretmelidir.

Ortak Firestore zarfı her entity'de en az şunları taşımalıdır:

- Kayıt tipine özel canonical ID; document ID ile birebir aynı.
- `ownerUid`, `vehicleId`.
- `schemaVersion` (ilk sürüm `1`).
- `revision` (`Long`, ilk mutation en az 1, negatif olamaz).
- `operationId` (stabil UUID; retry yeni ID üretmez).
- `source`: `MANUAL`, `MIGRATED`, `SYSTEM_DERIVED`, genel `IMPORTED`, `UNKNOWN` fallback.
- `createdAt`, `clientUpdatedAt`, `_serverUpdatedAt`, `deletedAt`.

Finansal olaylar cohesive/atomic kabul edilmelidir. Conflict resolver, örneğin volume'ü bir cihazdan ve total amount'ı başka cihazdan field-level birleştirip hiç girilmemiş bir yakıt fişi yaratmamalıdır. Yeni ledger entity'lerinde whole-record deterministic winner kullanılmalı; kaybeden candidate kalıcı conflict kaydında korunmalıdır.

## 4. Yakıt kayıt modeli

### Ürün modeli

`VehicleFuelEntry`, yalnız sıvı yakıt alımını temsil etmelidir. Hybrid araç yakıt alımı burada, plug-in hybrid elektrik alımı `VehicleChargeEntry` içinde tutulur.

Zorunlu alanlar:

- `fuelEntryId`, `ownerUid`, `vehicleId`.
- `occurredAt` (UTC epoch millis).
- `fuelProduct`: `PETROL`, `DIESEL`, `LPG`, `OTHER`, `UNKNOWN` güvenli fallback.
- `volumeMl: Long > 0`.
- `fillType`: `FULL`, `PARTIAL`, `UNKNOWN`.
- Ortak version/revision/operation/source/timestamp zarfı.

Opsiyonel alanlar:

- `odometerEntryId` ve yalnız projection/diagnostic amaçlı `odometerMetersSnapshot`.
- `totalAmountMinor`, `currencyCode`, `currencyExponent`. Üçü birlikte var veya birlikte null olmalıdır. Ücretsiz alım `amount=0` ile açıkça tutulabilir.
- Kullanıcının girdiğini kaybetmemek için `unitPriceMicrosPerLiter`; summary için authoritative alan değildir.
- `stationName`, `notes`, `tankLevelBeforePermille`, `tankLevelAfterPermille`.
- `duplicateFingerprint`, `documentCount` yalnız yerel projection olabilir.

Maliyet bilinmiyorsa kullanıcı yakıt/consumption kaydını yine oluşturabilmelidir; kayıt maliyet özetinden dışlanır ve bilgi seviyesinde `FUEL_COST_MISSING` health üretir. Hacimsiz kayıt ise `VehicleFuelEntry` değildir; gerekirse genel gider olarak kaydedilir.

### Kimlik, Room ve Firestore

- Canonical ID: `fuelEntryId`, normalde `DriveIdGenerator.UUID` ile üretilen UUID; ad, plaka, tarih veya fiş numarasından türetilmez.
- Room PK: `(ownerUid, fuelEntryId)`.
- Önerilen index'ler: `(ownerUid, vehicleId, deletedAt, occurredAt, fuelEntryId)`, `(ownerUid, vehicleId, odometerMetersSnapshot)`, `(ownerUid, syncState, clientUpdatedAt)`, unique `(ownerUid, operationId)`, `(ownerUid, duplicateFingerprint)`.
- Firestore: `users/{uid}/vehicleFuelEntries/{fuelEntryId}`.
- Schema version: 1.
- Tombstone: aynı belge üzerinde `deletedAt != null`; hard delete yok.
- Provenance: record-level source zorunlu, değişen alanlar mevcut `drive_field_provenance` yapısında `FUEL_ENTRY` entity type ile izlenebilir.
- Receipt: operation, revision, winning operation, attempt, retry/fatal/conflict ve source saklanır.
- Conflict: vehicle tombstone yeni mutation'a karşı kazanır; sonra photo/assignment ile aynı revision → server timestamp → operation ID sırası. Whole-record winner.
- Health: invalid/missing volume, invalid currency tuple, vehicle fuel mismatch, missing odometer, non-monotonic odometer, duplicate suspicion, unsupported schema, orphan/deleted vehicle ve consumption segment exclusion.

## 5. Elektrik şarj modeli

Yakıt ve elektrik aynı entity'de tutulmamalıdır. Hacim ile enerji farklı fiziksel boyutlardır; “FULL” semantiği, SOC, charger ve tüketim hesapları da farklıdır. Ortak liste için `VehicleEnergyLedgerRow` adlı salt okunur projection kullanılabilir.

Zorunlu alanlar:

- `chargeEntryId`, `ownerUid`, `vehicleId`, `occurredAt`.
- `energyWh: Long > 0`. Enerji bilinmiyorsa kayıt tüketim kaydı olarak güvenilir değildir; yalnız maliyet varsa `VehicleExpense` içinde `ENERGY_OTHER`/`CHARGING_UNMETERED` primary kategorisi kullanılabilir.
- `chargeCompletion`: `FULL`, `PARTIAL`, `UNKNOWN`.
- Ortak version/revision/operation/source/timestamp zarfı.

Opsiyonel alanlar:

- `odometerEntryId`, `odometerMetersSnapshot`.
- `totalAmountMinor`, `currencyCode`, `currencyExponent` tuple'ı.
- `startSocPermille`, `endSocPermille` (0..1000).
- `durationSeconds`, `chargerType` (`AC`, `DC`, `HOME`, `OTHER`, `UNKNOWN`), `providerName`, `stationName`, `notes`.
- `meterStartWh`, `meterEndWh`; ikisi varsa fark `energyWh` ile health seviyesinde karşılaştırılır.
- `unitPriceMicrosPerKwh` yalnız input audit/projection; total amount authoritative.

Kimlik ve sync sözleşmesi:

- Canonical ID: `chargeEntryId`, UUID.
- Room PK: `(ownerUid, chargeEntryId)`.
- Index'ler: `(ownerUid, vehicleId, deletedAt, occurredAt, chargeEntryId)`, `(ownerUid, vehicleId, odometerMetersSnapshot)`, `(ownerUid, syncState, clientUpdatedAt)`, unique operation ve duplicate fingerprint index'leri.
- Firestore: `users/{uid}/vehicleChargeEntries/{chargeEntryId}`.
- Schema/revision/operation/tombstone/provenance/receipt/conflict: fuel entry ile aynı ortak zarf ve whole-record policy.
- Health: invalid/missing energy, invalid SOC, end SOC before start without explicit discharge explanation, meter delta mismatch, vehicle energy-carrier mismatch, duplicate, missing odometer, unsupported schema, orphan vehicle.

Tam elektrikli araç yalnız charge entry; klasik hybrid çoğunlukla fuel entry; plug-in hybrid iki ayrı seriyi destekler. İki enerji türü tek “eşdeğer tüketim” değerine otomatik çevrilmemelidir.

## 6. Bakım ve tamir modeli

Bakım, tamir ve muayene lifecycle taşıdığı için genel giderle aynı entity olmamalıdır. `VehicleServiceRecord` şu `recordType` değerlerini desteklemelidir: `MAINTENANCE`, `REPAIR`, `INSPECTION`, `DIAGNOSTIC`, `TIRE_SERVICE`, `OTHER`, `UNKNOWN`.

Planlanan ve tamamlanmış bakım ayrı entity değildir. Aynı record şu status lifecycle'ını izler:

- `PLANNED`: tarih/km hedefi veya linked reminder vardır; actual cost summary'ye girmez.
- `IN_PROGRESS`: servis başlamış, henüz tamamlanmamıştır.
- `COMPLETED`: `completedAt` zorunludur; actual summary'ye girer.
- `CANCELLED`: geçmiş/audit korunur, actual summary'ye girmez.
- `UNKNOWN`: parse fallback; UI otomatik düzeltmez.

Zorunlu alanlar:

- `serviceRecordId`, `ownerUid`, `vehicleId`, `recordType`, `status`.
- Kullanıcı tarafından girilen `title`; türden sahte başlık üretilmez.
- Ortak version/revision/operation/source/timestamp zarfı.

Koşullu ve opsiyonel alanlar:

- `scheduledForEpochDay` veya `scheduledOdometerMeters` (`PLANNED` için en az biri önerilir).
- `startedAt`, `completedAt` (`COMPLETED` için zorunlu).
- `odometerEntryId`, `odometerMetersSnapshot`.
- `vendorName`, `notes`, `invoiceReference`.
- `subtotalMinor`, `taxMinor`, `discountMinor`, `totalAmountMinor`, `currencyCode`, `currencyExponent`. Actual summary'de yalnız parent `totalAmountMinor` kullanılır.
- `reminderId`, `duplicateFingerprint`, item count ve document count projection'ları.

Kimlik ve storage:

- Canonical ID: `serviceRecordId`, UUID.
- Room PK: `(ownerUid, serviceRecordId)`.
- Index'ler: `(ownerUid, vehicleId, deletedAt, status, completedAt)`, `(ownerUid, vehicleId, scheduledForEpochDay)`, `(ownerUid, vehicleId, scheduledOdometerMeters)`, `(ownerUid, syncState, clientUpdatedAt)`, unique operation ve fingerprint.
- Firestore: `users/{uid}/vehicleServiceRecords/{serviceRecordId}`.
- Schema version 1; revision/operation/tombstone/provenance/receipt ortak zarfı.
- Conflict whole-record'tur. Status transition bir revision'dır; iki cihazın completion/edit yarışı field merge edilmez. Kaybeden candidate conflict receipt/shadow'da korunur.
- Health: invalid lifecycle, missing completion time, currency breakdown mismatch, item total mismatch, orphan item, overdue planned record, duplicate invoice, non-monotonic odometer, unsupported schema ve vehicle tombstone.

## 7. Servis kalemleri modeli

Yağ, yağ filtresi ve hava filtresi tek servis kaydının üç `VehicleServiceItem` child kaydı olmalıdır. Inline Firestore array önerilmez; item-level conflict, tombstone, sıralama ve büyük servis kayıtları zorlaşır.

Zorunlu alanlar:

- `serviceItemId`, `ownerUid`, `vehicleId`, `serviceRecordId`.
- `itemType`: `ENGINE_OIL`, `OIL_FILTER`, `AIR_FILTER`, `CABIN_FILTER`, `BRAKE`, `TIRE`, `BATTERY`, `LABOR`, `INSPECTION_ITEM`, `OTHER`, `UNKNOWN`.
- `description`, `sortOrder`.
- Ortak version/revision/operation/source/timestamp zarfı.

Opsiyonel alanlar:

- `partNumber`.
- `quantityMicros` ve `quantityUnitCode` (`PIECE`, `LITER`, `HOUR`, `METER`, `OTHER`). Quantity için floating point kullanılmaz.
- `partsAmountMinor`, `laborAmountMinor`, `totalAmountMinor`, currency tuple'ı.
- `notes`.

Parent total authoritative olduğu için item toplamları yalnız breakdown'dır. Parent `totalAmountMinor` ile item toplamı farklıysa kayıt reddedilmez; `SERVICE_ITEM_TOTAL_MISMATCH` health oluşur. Vergi, indirim veya yuvarlama farkı meşru olabilir.

- Canonical ID: `serviceItemId`, UUID.
- Room PK: `(ownerUid, serviceRecordId, serviceItemId)`; `vehicleId` PK'ye gerekmez fakat invariant olarak tutulur.
- Index'ler: `(ownerUid, serviceRecordId, deletedAt, sortOrder, serviceItemId)`, `(ownerUid, vehicleId, deletedAt)`, `(ownerUid, syncState, clientUpdatedAt)`, unique operation.
- Firestore: `users/{uid}/vehicleServiceItems/{serviceItemId}`. Flat collection, collection-group/N+1 ihtiyacını kaldırır; parent `serviceRecordId` alanla doğrulanır.
- Schema version 1; item tombstone'u bağımsızdır. Parent tombstone child metadata'yı hard-delete etmez fakat child update/create'e karşı kazanır.
- Provenance/receipt item operation'ı için ayrı kaydedilir. Conflict item-level whole-record winner'dır.
- Health: missing parent, parent/child vehicle mismatch, duplicate sort order, invalid quantity, currency mismatch, unsupported schema.

## 8. Sigorta, muayene ve vergi modeli

Bu üç konu aynı lifecycle'a sahip değildir:

- Muayene, sonuç/servis sağlayıcı/odometer ve olası item'lar taşıdığı için `VehicleServiceRecord(recordType=INSPECTION)` olmalıdır. Muayene ücreti parent service total'ında tutulur.
- Sigorta ödemesi `VehicleExpense(category=INSURANCE)` olmalıdır. Yenileme tarihi `VehicleReminder` ile yönetilir.
- Vergi ve harç ödemesi `VehicleExpense(category=TAX|REGISTRATION_FEE|ROAD_FEE)` olmalıdır. Sonraki ödeme tarihi gerekiyorsa reminder kullanılır.

İlk sürümde tam sigorta poliçesi domain'i oluşturulmamalıdır. `providerName`, `referenceNumber`, `periodStartEpochDay`, `periodEndEpochDay` expense üzerinde opsiyonel olabilir; kapsam, sigortalı kişi, hasar ve poliçe versiyonlama ihtiyaçları oluşursa ileride ayrı `VehiclePolicy` domain'i değerlendirilmelidir. Bu analiz yalnız ödeme ve reminder sınırını tanımlar.

Muayene sonucu için `inspectionResult = PASSED | FAILED | CONDITIONAL | UNKNOWN` service record'a opsiyonel typed alan olarak eklenebilir. Başarısız muayene otomatik tamir veya expense oluşturmaz; kullanıcı açıkça yeni service record açar.

## 9. Genel gider modeli

`VehicleExpense`, yakıt, metered charge ve service dışındaki bağımsız finansal olayı temsil eder.

Primary kategori enum'u:

`INSURANCE`, `TAX`, `REGISTRATION_FEE`, `ROAD_FEE`, `TOLL`, `PARKING`, `CAR_WASH`, `CHARGING_UNMETERED`, `ACCESSORY`, `FINE`, `OTHER`, `UNKNOWN`.

Bir expense tam olarak bir primary kategoriye ait olmalıdır. Çoklu sınıflandırma gerekiyorsa non-financial tag'ler sonradan ayrı ilişki olarak eklenebilir; summary tag üzerinden aynı tutarı tekrar saymamalıdır. Tek fiş farklı kategorilere bölünecekse toplamı paylaşan birden fazla expense ve ortak `splitGroupId` oluşturulur.

Zorunlu alanlar:

- `expenseId`, `ownerUid`, `vehicleId`, `occurredAt`, `category`.
- `transactionKind`: `EXPENSE`, `REFUND`, `CREDIT`. Tutar daima non-negative saklanır; işaret summary'de kind'dan türetilir.
- `amountMinor > 0`, `currencyCode`, `currencyExponent`.
- Ortak version/revision/operation/source/timestamp zarfı.

Opsiyonel alanlar:

- `vendorName`, `notes`, `referenceNumber`.
- `periodStartEpochDay`, `periodEndEpochDay`, `dueEpochDay`.
- `odometerEntryId`, `odometerMetersSnapshot`.
- `splitGroupId`, `duplicateFingerprint`.

- Canonical ID: `expenseId`, UUID.
- Room PK: `(ownerUid, expenseId)`.
- Index'ler: `(ownerUid, vehicleId, deletedAt, occurredAt, expenseId)`, `(ownerUid, vehicleId, category, occurredAt)`, `(ownerUid, vehicleId, currencyCode, occurredAt)`, `(ownerUid, syncState, clientUpdatedAt)`, unique operation ve fingerprint.
- Firestore: `users/{uid}/vehicleExpenses/{expenseId}`.
- Schema version 1; common revision/operation/tombstone/provenance/receipt.
- Conflict whole-record winner'dır. Refund ile original expense arasında `relatedExpenseId` opsiyonel olabilir; original kayıt mutasyona uğratılmaz.
- Health: invalid currency/exponent, invalid period, duplicate receipt, category/field mismatch, orphan vehicle, unsupported schema, amount overflow.

## 10. Kilometre geçmişi

Canonical kilometre kaynağı `VehicleOdometerEntry` olmalıdır. Fuel/charge/service kayıtlarındaki snapshot yalnız hızlı görüntüleme ve mismatch tespiti içindir.

Zorunlu alanlar:

- `odometerEntryId`, `ownerUid`, `vehicleId`.
- `observedAt` (gerçek ölçüm zamanı), `odometerMeters: Long >= 0`.
- `quality`: `CONFIRMED`, `ESTIMATED`, `UNKNOWN`.
- `readingRole`: `AT_EVENT`, `TRIP_START`, `TRIP_END`, `MANUAL`, `MIGRATED`.
- `odometerSeriesId`: normalde vehicle'a ait default seri; sayaç değişimi/rollover ayrı seri açar.
- Ortak version/revision/operation/source/timestamp zarfı.

Opsiyonel alanlar:

- `sourceRecordType`, `sourceRecordId`; fuel/charge/service ile stable ilişki.
- `correctionOfEntryId`, `resetReason`, `notes`.

- Canonical ID: `odometerEntryId`, UUID; source record için normal create sırasında rastgele, migration/backfill için owner+vehicle+source'dan idempotent güvenli hash/UUIDv5 üretilebilir.
- Room PK: `(ownerUid, odometerEntryId)`.
- Index'ler: `(ownerUid, vehicleId, deletedAt, observedAt, odometerEntryId)`, `(ownerUid, vehicleId, odometerSeriesId, odometerMeters)`, unique `(ownerUid, sourceRecordType, sourceRecordId, readingRole)`, `(ownerUid, syncState, clientUpdatedAt)` ve unique operation.
- Firestore: `users/{uid}/vehicleOdometerEntries/{odometerEntryId}`.
- Schema version 1; common revision/operation/tombstone/provenance/receipt/conflict.
- Health: non-monotonic series, implausible jump, duplicate source link, snapshot mismatch, stale vehicle mirror, invalid reset, unsupported schema.

“Current” seçimi en yüksek sayıyı seçmemelidir. Aktif odometer serisinde en yeni `observedAt` tarihli, tombstone olmayan `CONFIRMED` kayıt seçilir; eşitlikte server timestamp ve ID tie-break uygulanır. Confirmed yoksa estimated değer açıkça estimated olarak gösterilebilir.

Eski tarihli ama yüksek kilometreli kayıt kaydedilebilir; kullanıcı verisi reddedilmez. Kayıt tarih sırasındaki komşularla non-monotonic health üretir, current projection'ı sırf sayı yüksek diye değiştirmez ve bu tutarsızlığı kesen tüketim segmentleri hesaplanmaz. Kullanıcı tarihi/kilometreyi düzeltmeli veya gerçek sayaç değişimiyse yeni `odometerSeriesId` başlatmalıdır.

`DriveVehicle.currentOdometerKm`, yeni ledger aktif olduktan sonra yalnız best-effort compatibility mirror olmalıdır. Canonical entry değeri metre→km gösterimine çevrilir. Eski uygulamanın mirror'ı değiştirmesi canonical ledger'ı sessizce değiştirmemeli; `ODOMETER_MIRROR_STALE` health ve açık “ölçüm olarak kabul et” aksiyonu kullanılmalıdır.

## 11. Hatırlatma modeli

`VehicleReminder` bir finansal olay veya service completion değildir. Schedule semantiğinin sahibidir.

Zorunlu alanlar:

- `reminderId`, `ownerUid`, `vehicleId`, kullanıcı başlığı.
- `reminderType`: `MAINTENANCE`, `INSPECTION`, `INSURANCE`, `TAX`, `REGISTRATION`, `OTHER`, `UNKNOWN`.
- `status`: `ACTIVE`, `SNOOZED`, `DISABLED`, `COMPLETED`, `UNKNOWN`.
- En az biri: `dueEpochDay` veya `dueOdometerMeters`.
- Ortak version/revision/operation/source/timestamp zarfı.

Opsiyonel alanlar:

- `recurrenceMonths`, `recurrenceDistanceMeters`; ikisi aynı reminder'da bulunabilir.
- `recurrenceAnchor`: `LAST_COMPLETION` veya `FIXED_SCHEDULE`.
- `leadDays`, `leadDistanceMeters`, `snoozedUntilEpochDay`.
- `linkedServiceRecordId`, `lastCompletedServiceRecordId`, `lastCompletedAt`, `lastCompletedOdometerMeters`.
- `notes`.

Tarih ve kilometre birlikteyse reminder, eşiklerden herhangi biri geldiğinde due olmalıdır; UI hangi eşik nedeniyle tetiklendiğini göstermelidir. Completion transaction'ı last-completion anchor'ını günceller ve recurrence'a göre bir sonraki hedefleri üretir. Bu işlem service completion ile aynı local Room transaction'ında iki stabil operation olarak yazılır; Firestore'da eventual reconciliation yapılır.

- Canonical ID: `reminderId`, UUID.
- Room PK: `(ownerUid, reminderId)`.
- Index'ler: `(ownerUid, vehicleId, status, dueEpochDay)`, `(ownerUid, vehicleId, status, dueOdometerMeters)`, `(ownerUid, linkedServiceRecordId)`, `(ownerUid, syncState, clientUpdatedAt)`, unique operation.
- Firestore: `users/{uid}/vehicleReminders/{reminderId}`.
- Schema version 1 ve ortak tombstone/conflict/provenance/receipt.
- Health: no due target, invalid recurrence, completed service missing, next due before anchor, stale odometer, overdue, unsupported schema, orphan vehicle.

## 12. Belge ve fatura fotoğrafları

Sprint 6B'nin güvenli decode, orientation, 2048 px resize, JPEG re-encode, EXIF/GPS temizleme, content hash, UID kapsamlı file/cache, idempotent Storage upload/delete ve receipt yaklaşımı yeniden kullanılabilir. Ancak `DriveVehiclePhotoEntity` veya `vehicles/{vehicleId}/photos` sözleşmesi doğrudan kullanılmamalıdır; araç galerisi primary/sort semantiği ile fiş retention/parent semantiği farklıdır.

`VehicleDocument` metadata modeli:

- Zorunlu: `documentId`, `ownerUid`, `vehicleId`, `parentType`, `parentId`, `documentKind`, `storagePath`, `contentHash`, `mimeType`, `sizeBytes`, `schemaVersion/revision/operation/source/timestamps`.
- Opsiyonel: `width`, `height`, `pageNumber`, `sortOrder`, `capturedAt`, `localPreparedPath`, local `cachePath`, `healthCode`.
- `parentType`: `FUEL_ENTRY`, `CHARGE_ENTRY`, `SERVICE_RECORD`, `EXPENSE`, `ODOMETER_ENTRY`, `OTHER`.
- `documentKind`: `RECEIPT`, `INVOICE`, `POLICY`, `INSPECTION_RESULT`, `TAX_DOCUMENT`, `OTHER`, `UNKNOWN`.

İlk binary sürüm yalnız gerçekten yeniden encode edilen `image/jpeg` desteklemelidir. İleride PDF desteği schema/MIME/rules değişikliğiyle eklenebilir; bu rapor sahte PDF desteği varsaymaz.

- Canonical ID: `documentId`, UUID.
- Room PK: `(ownerUid, documentId)`.
- Index'ler: `(ownerUid, parentType, parentId, deletedAt, sortOrder)`, `(ownerUid, vehicleId, deletedAt)`, `(ownerUid, uploadState, updatedAt)`, `(ownerUid, contentHash)`, unique operation.
- Firestore metadata: `users/{uid}/vehicleDocuments/{documentId}`.
- Storage: `users/{uid}/vehicles/{vehicleId}/documents/{documentId}.jpg`.
- Download URL canonical değildir.
- Tombstone önce Firestore metadata'ya, sonra binary delete'e uygulanır; object-not-found idempotent başarıdır.
- Provenance/receipt binary ve metadata aşamalarını ayırır. Conflict photo contract policy'sini kullanır; parent tombstone belgeyi otomatik silmez.
- Health: storage object missing, metadata missing, unsupported MIME/schema, invalid dimensions/size, duplicate content, orphan parent, cache scope mismatch, upload/delete stuck.

## 13. Para, hacim, enerji ve mesafe birimleri

Canonical birimler:

| Boyut | Storage | Tip | UI dönüşümü |
|---|---|---|---|
| Mesafe/odometer | metre | `Long` | km, mile |
| Sıvı yakıt | mililitre | `Long` | litre, US gallon, imperial gallon |
| Elektrik | Wh | `Long` | kWh |
| Süre | saniye veya epoch millis bağlama göre | `Long` | saat/dakika |
| Para | ISO minor unit | `Long` | ISO 4217 exponent ile major amount |
| Genel quantity | micro-unit | `Long` | unit code'a göre |
| Date-only | epoch day | `Long` | locale date |
| Event time | UTC epoch millis | `Long` | timezone/locale datetime |

Mesafe dönüşümünde 1 mile = 1609.344 metre; gallon türü kullanıcı tercihinde açık olmalıdır. US ve imperial gallon aynı label altında tutulmamalıdır. Dönüşümler `BigDecimal`/rational arithmetic ile yapılmalı, sonuç UI'da yuvarlanmalıdır. Canonical kayıt locale virgül/nokta formatı içermez.

Para tuple'ı `amountMinor + currencyCode + currencyExponent` olmalıdır. `currencyExponent`, kaydın oluşturulduğu anda kullanılan ISO minor basamak sayısını korur; `JPY=0`, çoğu para birimi `2`, bazıları `3` olabilir. Mevcut purchase-price UI'sının sabit iki basamak varsayımı yeni domain'e kopyalanmamalıdır. Kod uppercase ISO 4217 olarak normalize edilmeli; biçim doğru fakat runtime ISO listesinde olmayan kod fatal silme yerine unsupported-currency health üretmelidir.

Farklı para birimleri çevrim kuru olmadan toplanmamalıdır. Summary `currencyCode + currencyExponent` bazında ayrı satırlar gösterir. Exchange-rate domain'i bu kapsamda yoktur.

## 14. Yakıt tüketimi hesaplama politikası

Canonical tüketim değeri Firestore'a yazılmamalıdır; aktif Room kayıtlarından deterministik hesaplanmalıdır.

Geçerli segment için koşullar:

1. Aynı UID, vehicle ve odometer series içinde iki `FULL` fuel entry bulunmalıdır.
2. Her iki anchor doğrulanmış odometer entry'ye bağlanmalıdır.
3. Bitiş kilometresi başlangıçtan büyük olmalı ve mesafe en az güvenli minimumu aşmalıdır; başlangıç önerisi 1.000 metredir.
4. İki anchor arasındaki tüm aktif fuel entry volume'leri bilinmelidir.
5. Odometer health segmenti reset/non-monotonic olarak işaretlememelidir.
6. Vehicle tombstone geçmiş segmenti silmez; archived summary'de hesaplanabilir.

Önceki full kaydın kendi volume'ü tüketim numerator'ına girmez. Önceki full'dan sonra gelen partial/unknown ara alımlar ile bitiş full alımının volume'leri toplanır.

Rational formül:

`litrePer100Km = totalVolumeMl * 100 / distanceMeters`

Hesap checked integer/`BigDecimal` ile yapılmalı, division yalnız display aşamasında uygulanmalıdır. MPG gösterimi aynı canonical volume/distance'tan US veya imperial preference'a göre türetilir; ayrı canonical MPG saklanmaz.

Şunlar tüketimden dışlanır:

- Tombstone veya unsupported-schema kayıtlar.
- Endpoint'i `PARTIAL/UNKNOWN` olan segment.
- Eksik/zero/negative volume.
- Eksik, estimated-only veya non-monotonic odometer anchor.
- Farklı odometer series.
- Aynı/azalan kilometre, aşırı kısa segment.
- Vehicle/fuel linkage invariant'ı bozuk kayıt.
- Çözülmemiş conflict'in kaybeden candidate'ı.

Pending local kayıtla hesaplanan sonuç “provisional” olarak gösterilebilir. Plausibility üst/alt sınırı ağır araçlar ve farklı yakıtlar nedeniyle hard validation olmamalı; health warning olmalıdır.

## 15. Elektrik tüketimi hesaplama politikası

Elektrik için iki ayrı metrik tanımlanmalıdır:

- `deliveredEnergy`: seçili dönemde kaydedilen toplam Wh; mesafeden bağımsız gerçek sayaç toplamı.
- `deliveredEnergyPer100Km`: karşılaştırılabilir iki full-charge anchor arasında hesaplanan şebekeden verilen enerji/mesafe. Bu değer drivetrain efficiency olarak adlandırılmamalıdır; şarj kaybını içerir.

Full-to-full koşulları fuel algoritmasına benzer. Endpoint'ler `chargeCompletion=FULL` olmalı; varsa end SOC değerleri birbirine yakın ve yüksek olmalıdır. Ara partial charge'ların Wh değerleri numerator'a eklenir. Formül:

`kWhPer100Km = totalEnergyWh * 100 / distanceMeters`

Hesaplanmaması gereken durumlar:

- Missing/zero energy.
- Full anchor yokluğu veya karşılaştırılamayan SOC.
- Non-monotonic/missing odometer.
- Farklı odometer series.
- Tombstone/unsupported/conflict kayıtları.
- Kullanıcının charge history'sinin eksik olduğunun işaretlendiği dönem.

PHEV için fuel L/100km ve electric kWh/100km ayrı gösterilir; tek “combined” sayı üretmek enerji eşdeğeri ve sürüş modu verisi olmadan yanıltıcıdır.

## 16. Tam depo ve kısmi depo algoritması

Sıralama `occurredAt`, sonra canonical record ID ile deterministik yapılır; hesapta odometer monotonluğu ayrıca doğrulanır.

Durum makinesi:

1. İlk `FULL` kayıt anchor olur, tüketim üretmez.
2. `PARTIAL` kayıtların volume'ü accumulator'a eklenir.
3. `UNKNOWN` ara kayıt volume'ü biliniyorsa accumulator'a eklenebilir fakat anchor olamaz; sonuç “data quality limited” health etiketi taşıyabilir.
4. Sonraki `FULL` kaydın volume'ü accumulator'a eklenir.
5. İki full anchor kilometre farkı geçerliyse tüketim hesaplanır.
6. Bitiş full yeni başlangıç anchor'ı olur ve accumulator sıfırlanır.

Kısmi dolumdan hemen sonra tüketim hesaplanmaz. Önceki güvenilir full anchor ve sonraki full kapanış kaydı oluştuğunda, aradaki bütün partial volume'lerle birlikte hesaplanır. Partial kayıt silinir/düzenlenir veya retroaktif eklenirse etkilenen iki full anchor arasındaki segment yeniden hesaplanır; persisted derived değer güncellenmez çünkü derived değer saklanmaz.

Charge için aynı state-machine yalnız açık `FULL` charge anchor'ları ve Wh accumulator ile uygulanabilir.

## 17. Veri doğrulama kuralları

Hard validation yalnız yapısal olarak anlamsız veya güvenlik açısından tehlikeli veriyi engellemelidir:

- UID/ID/vehicle ID boş olamaz; payload ID document ID ile eşleşmelidir.
- Integer miktarlar overflow-safe aralıkta ve non-negative olmalıdır; fuel volume/charge energy gibi temel ölçüler >0 olmalıdır.
- Para tuple'ı eksiksiz olmalı; currency uppercase üç harf, exponent 0..4 makul aralıkta olmalıdır.
- `occurredAt`, `completedAt`, schedule ve period sıraları kendi lifecycle'ına uymalıdır.
- SOC 0..1000, yüzde floating point değildir.
- Document MIME/path/hash contract'a uymalıdır.
- Service item parent ve vehicle scope'u eşleşmelidir.
- Revision negatif olamaz, operation ID boş olamaz.

Soft warning/health olarak ele alınması gerekenler:

- Eski tarihli kayıt, geleceğe küçük clock skew, olağandışı tutar veya tüketim.
- Kilometre kronolojisinde azalma/yüksek sıçrama.
- Vehicle fuel type ile entry carrier uyuşmazlığı; PHEV/retrofit/yanlış profil olabilir.
- Parent service total ile item toplamı farkı.
- Currency runtime ISO listesinde bulunmaması.
- Duplicate ihtimali.

Teknik exception mesajı kullanıcıya verilmemeli; typed domain error ve alan bazlı localized UI mesajı kullanılmalıdır. `CancellationException` her katmanda yeniden fırlatılmalıdır.

## 18. Duplicate ve idempotency

İki ayrı problem ayrılmalıdır:

1. Aynı mutation'ın retry edilmesi: stabil `operationId`, target revision ve unique outbox/receipt index'iyle kesin idempotency.
2. Kullanıcının aynı gerçek fişi iki ayrı record ID ile girmesi: heuristic duplicate detection; otomatik silme/merge yok.

Önerilen fingerprint girdileri:

- Fuel: owner+vehicle, zaman bucket'ı, odometer metres, volume ml, amount/currency ve varsa document content hash.
- Charge: owner+vehicle, zaman bucket'ı, energy Wh, amount/currency, provider/reference hash.
- Service/expense: normalized vendor, date, total/currency, invoice reference hash ve document content hash.

Fingerprint PII içeren ham not/vendor/fiş numarasını loglamamalıdır. Cross-device duplicate için deterministic değer gerekir; fingerprint yalnız health index'idir, unique constraint değildir. Kullanıcı iki eşit tutarlı otopark veya aynı görseli bilinçli ekleyebilir.

Import/export hazırlığı için provider-specific alan eklenmemelidir. Genel `source`, opsiyonel `externalSourceType` ve `externalRecordId` çifti gelecekte kullanılabilir; unique `(ownerUid, externalSourceType, externalRecordId)` yalnız source gerçekten stabil kimlik sağladığında uygulanmalıdır. Bu belge herhangi bir Driversnote eşleme tasarlamaz.

## 19. Offline-first akış

Her mutation için önerilen akış:

1. Feature gate ve aktif Firebase UID doğrulanır.
2. Vehicle local Room'da bulunur; tombstone ise create/update reddedilir.
3. Draft validation ve canonical unit conversion yapılır.
4. Bilinen en yüksek local/remote revision'ın bir fazlası ve stabil operation ID üretilir.
5. Domain entity, ilişkili odometer entry ve `drive_ledger_operations` satırları tek Room transaction'ında yazılır.
6. UI yalnız Room `Flow`'dan anında güncellenir; network sonucu beklemez.
7. Unique WorkManager işi yalnız owner UID ve operation ID gibi stabil ID'lerle planlanır.
8. Worker initial/incremental pull yapar, conflict uygular, operation'ı atomik claim eder ve remote transaction yürütür.
9. Remote sonrası aktif UID tekrar doğrulanır.
10. Receipt, provenance, sync state, health ve gerekiyorsa compatibility projection aynı Room transaction'ında güncellenir.

Bir fuel/service mutation ile odometer entry birlikte oluştuğunda local transaction atomiktir. Firestore çok-belgeli transaction mümkün olsa da offline mutation doğrudan remote transaction'a bağımlı olmamalıdır. Outbox aynı `logicalBatchId` altındaki operation'ları parent önce, odometer/link reconciliation sonra uygulayabilir. Bir parça başarısızsa diğerini silmek yerine pending health ile tamamlar.

Worker önerilen sırası:

1. UID/gate.
2. Ledger collection cursor'larının initial/incremental pull'u.
3. Transactional resolver apply.
4. Odometer current projection ve vehicle tombstone reconciliation.
5. Outbound metadata operations.
6. Document binary operations.
7. Receipt/health.
8. UID yeniden doğrulama.

## 20. Firestore sözleşmesi

Flat, user-scoped collection yapısı önerilir:

| Entity | Path |
|---|---|
| Fuel | `users/{uid}/vehicleFuelEntries/{fuelEntryId}` |
| Charge | `users/{uid}/vehicleChargeEntries/{chargeEntryId}` |
| Service | `users/{uid}/vehicleServiceRecords/{serviceRecordId}` |
| Service item | `users/{uid}/vehicleServiceItems/{serviceItemId}` |
| Expense | `users/{uid}/vehicleExpenses/{expenseId}` |
| Odometer | `users/{uid}/vehicleOdometerEntries/{odometerEntryId}` |
| Reminder | `users/{uid}/vehicleReminders/{reminderId}` |
| Document metadata | `users/{uid}/vehicleDocuments/{documentId}` |

Bu tercih, her vehicle için ayrı listener veya nested subcollection N+1 okuması yerine UID altındaki her domain collection'ının 200 kayıtlık sayfalarla hydrate edilmesini sağlar. `vehicleId` her belgede zorunlu/indexlenebilir filtre olarak kalır. Existing `driveTrips` flat collection yaklaşımıyla uyumludur.

Her collection ayrı cursor taşır: `_serverUpdatedAt ASC, documentId ASC`. Initial ve incremental pull pagination'ı son sayfa page size'dan küçük olana kadar sürmelidir. Hard delete güvenilir sinyal değildir; tombstone zorunludur.

Remote transaction kuralları:

- Aynı operation ID remote'da varsa `AlreadyApplied`.
- Beklenen revision remote revision ile eşleşirse mutation uygulanır.
- Remote daha yüksekse conflict/pull.
- Aynı revision farklı operation için server timestamp/operation ID resolver sonucu kullanılır.
- `_serverUpdatedAt` server timestamp olmalıdır.
- Writer yalnız izin verilen alanları merge/update eder; unknown gelecekteki alanları silmez.
- Summary, receipt ve local health Firestore canonical collection'ı değildir.

## 21. Room tabloları ve index'ler

Önerilen domain tabloları:

| Tablo | Primary key | Kritik index'ler |
|---|---|---|
| `drive_fuel_entries` | `ownerUid,fuelEntryId` | vehicle+deleted+date; vehicle+odometer; sync; operation; fingerprint |
| `drive_charge_entries` | `ownerUid,chargeEntryId` | vehicle+deleted+date; vehicle+odometer; sync; operation; fingerprint |
| `drive_service_records` | `ownerUid,serviceRecordId` | vehicle+status+completed; due date; due odometer; sync; fingerprint |
| `drive_service_items` | `ownerUid,serviceRecordId,serviceItemId` | parent+deleted+sort; vehicle; sync; operation |
| `drive_expenses` | `ownerUid,expenseId` | vehicle+date; category+date; currency+date; sync; fingerprint |
| `drive_odometer_entries` | `ownerUid,odometerEntryId` | vehicle+observed; series+meters; source record unique; sync |
| `drive_reminders` | `ownerUid,reminderId` | vehicle+status+due date; vehicle+status+due meters; linked record; sync |
| `drive_documents` | `ownerUid,documentId` | parent+sort; vehicle; upload state; content hash; operation |

Ortak altyapı tabloları:

- `drive_ledger_operations`: `(ownerUid, operationId)` PK; unique `(ownerUid, entityType, recordId, targetRevision)`; state/nextAttempt/claim index'i.
- `drive_ledger_sync_metadata`: `(ownerUid, collectionType)` PK; initial flag ve server cursor.
- `drive_ledger_sync_receipts`: `(ownerUid, receiptId)` PK; unique `(ownerUid, operationId)`; entity/status/date index'leri.
- `drive_ledger_conflicts`: her iki candidate'ı veya typed recoverable shadow'ı koruyan local-only conflict kaydı.
- Mevcut `drive_field_provenance` entity type genişletmesiyle yeniden kullanılabilir; tablo yapısı yeterliyse ikinci provenance tablosu gereksizdir.

Foreign key'ler hard cascade kullanmamalıdır. Service item→service ve tüm kayıt→vehicle ilişkileri `NO ACTION` veya uygulama seviyesinde invariant ile korunur; tombstone parent'ın child geçmişini fiziksel silmesine izin verilmez.

Summary sorgusu domain tablolarının active actual-cost satırlarını `UNION ALL` ile tek projection'a dönüştürüp vehicle/date/category/currency bazında `GROUP BY` yapmalıdır. Fuel/charge amount, completed service parent total ve general expense dahil edilir; service item tutarı tekrar eklenmez.

## 22. Sync ve conflict resolution

Yeni ledger resolver sırası:

1. UID/path scope mismatch fatal ve audit'tir.
2. Vehicle tombstone yeni child create/update'e karşı kazanır.
3. Parent service tombstone yeni service-item create/update'e karşı kazanır.
4. Aynı operation ID idempotent başarıdır.
5. Explicit tombstone, daha eski/eşit active update'e karşı kazanır.
6. Daha yüksek revision kazanır.
7. Aynı revision'da `_serverUpdatedAt` kazanır.
8. Timestamp eşitse lexicographic operation ID kazanır.

Client timestamp tek başına winner seçmez. Transactional finansal olaylarda field merge yapılmaz. Same-revision conflict'te local candidate silinmemeli; entity local pending/conflict olarak kalmalı veya kaybeden candidate `drive_ledger_conflicts` içinde tam recoverable biçimde saklanmalıdır. Kullanıcı “remote'u kullan”, “yereli yeni revision olarak uygula” veya alanları manuel karşılaştır seçenekleri alabilir.

Service record ve item'lar localde tek transaction olsa da remote'da eventual composition'dır. `logicalBatchId` ve parent `compositionRevision` ile UI “kalemler senkronize oluyor” gösterebilir. Summary parent total kullandığı için yarım item sync'i maliyeti iki kez saymaz.

Retroaktif fuel/charge/odometer edit'i tüketim sonuçlarını conflict olmadan yerel olarak yeniden hesaplatır; derived sonuç remote mutation değildir.

## 23. Tombstone ve silme politikası

- Hiçbir ledger metadata belgesi normal kullanıcı silmesinde hard-delete edilmez.
- Delete, aynı ID üzerinde revision artıran `deletedAt != null` tombstone mutation'ıdır.
- Restore yalnız kullanıcı tarafından açıkça başlatılan daha yüksek revision'lı operation ile mümkündür; normal edit tombstone'u diriltmez.
- Fuel/charge/expense silindiğinde linked document otomatik silinmez. Parent tombstone belgeyi hidden/orphan-pending-health yapar; kullanıcı belgeyi ayrıca silebilir veya parent'ı restore edebilir.
- Service parent silindiğinde item'lar ve belgeler korunur, UI'da parent ile birlikte gizlenir. Child'ları ayrı hard/tombstone cascade etmek gerekli değildir.
- Reminder silme geçmiş service record'u etkilemez.
- Odometer entry silme current projection ve etkilenen consumption segmentlerini yeniden hesaplatır; source fuel/service kaydı silinmez, snapshot mismatch health oluşabilir.
- Remote hard delete görülürse kayıt yerelde otomatik silinmez; `REMOTE_HARD_DELETE_DETECTED` receipt/health ve full reconciliation gerekir.

## 24. Provenance ve receipt

Her entity record-level `source` taşır. Mutable alanlarda mevcut `DriveFieldProvenanceEntity` şu entity type'larla genişletilebilir: `FUEL_ENTRY`, `CHARGE_ENTRY`, `SERVICE_RECORD`, `SERVICE_ITEM`, `EXPENSE`, `ODOMETER_ENTRY`, `REMINDER`, `DOCUMENT`.

Receipt en az şunları içermelidir:

- `ownerUid`, `receiptId`, `operationId`, `logicalBatchId`.
- `entityType`, `recordId`, `vehicleId`.
- `kind`: initial pull, incremental pull, outbound metadata, upload, delete, reconciliation.
- `status`: started, applied, idempotent, retry, fatal, conflict, superseded.
- `revision`, `winningOperationId`, `attemptCount`, start/finish zamanları.
- Safe `errorCode`; exception mesajı/payload değil.
- `source/provenance`.

Receipt kullanıcı verisini değiştirmez ve Firestore'a gerekmez; local diagnostics/audit için yeterlidir. Retention politikası kayıt adedi/zamanla prune edilebilir fakat unresolved conflict receipt'i silinmemelidir.

## 25. Health kontrolleri

Minimum health kodları:

### Ortak

- `LEDGER_VEHICLE_NOT_FOUND`, `LEDGER_VEHICLE_DELETED`, `LEDGER_OWNER_MISMATCH`.
- `LEDGER_UNSUPPORTED_SCHEMA`, `LEDGER_REMOTE_HARD_DELETE`, `LEDGER_CONFLICT_UNRESOLVED`.
- `LEDGER_OPERATION_STUCK`, `LEDGER_RECEIPT_MISSING`, `LEDGER_UNKNOWN_PROVENANCE`.

### Yakıt/şarj

- `FUEL_INVALID_VOLUME`, `FUEL_COST_MISSING`, `FUEL_DUPLICATE_SUSPECTED`.
- `FUEL_CARRIER_VEHICLE_MISMATCH`, `FUEL_CONSUMPTION_INSUFFICIENT_ANCHORS`.
- `CHARGE_INVALID_ENERGY`, `CHARGE_INVALID_SOC`, `CHARGE_METER_MISMATCH`.
- `CHARGE_DUPLICATE_SUSPECTED`, `ENERGY_CONSUMPTION_INSUFFICIENT_ANCHORS`.

### Servis/gider

- `SERVICE_INVALID_STATUS`, `SERVICE_COMPLETION_MISSING`, `SERVICE_ITEM_ORPHANED`.
- `SERVICE_ITEM_VEHICLE_MISMATCH`, `SERVICE_TOTAL_MISMATCH`, `SERVICE_DUPLICATE_SUSPECTED`.
- `EXPENSE_INVALID_CURRENCY`, `EXPENSE_INVALID_PERIOD`, `EXPENSE_DUPLICATE_SUSPECTED`.

### Kilometre/reminder/document

- `ODOMETER_NON_MONOTONIC`, `ODOMETER_IMPLAUSIBLE_JUMP`, `ODOMETER_SERIES_INVALID`.
- `ODOMETER_SNAPSHOT_MISMATCH`, `ODOMETER_MIRROR_STALE`, `ODOMETER_SOURCE_DUPLICATE`.
- `REMINDER_NO_TRIGGER`, `REMINDER_RECURRENCE_INVALID`, `REMINDER_OVERDUE`.
- `DOCUMENT_STORAGE_OBJECT_MISSING`, `DOCUMENT_METADATA_MISSING`, `DOCUMENT_ORPHANED`.
- `DOCUMENT_DUPLICATE_CONTENT`, `DOCUMENT_UPLOAD_STUCK`, `DOCUMENT_DELETE_STUCK`, `DOCUMENT_CACHE_SCOPE_MISMATCH`.

Health taraması otomatik finansal kayıt, reminder veya Storage objesi silmemelidir. Güvenli auto-fix yalnız rebuildable summary, stale local sync state veya vehicle odometer mirror gibi açık projection'larda uygulanabilir.

## 26. Araç silme davranışı

Araç silindiğinde fuel, charge, service, item, expense, odometer, reminder ve document metadata'sı otomatik tombstone/hard-delete edilmemelidir. Finansal geçmiş, vergi/garanti/audit ve ileride export için korunmalıdır.

Önerilen davranış:

- Vehicle tombstone sonrası yeni child create/update remote'a uygulanmaz.
- Mevcut child kayıtlar archived read-only görünümde kalır; kullanıcı araç restore ederse yeniden normal görünür.
- Kullanıcı tek tek child tombstone oluşturabilir. Account-level “kalıcı veri temizleme” ayrı, açık ve kapsamlı bir ürün kararıdır.
- Offline child mutation ile remote vehicle delete yarışırsa vehicle delete kazanır; local candidate conflict/receipt'te korunur, sessizce kaybolmaz.
- Document binary otomatik silinmez. Storage maliyeti için retention/purge ayrı explicit akış olmalıdır.
- Summary varsayılan aktif araç ekranından çıkar; archived araç maliyet raporunda isteğe bağlı dahil edilebilir.

Bu politika mevcut Sprint 4'teki isteğe bağlı trip cascade davranışından bilinçli olarak farklıdır; finansal belgeler ve mali geçmiş daha güçlü retention gerektirir.

## 27. Account switch ve UID izolasyonu

Her PK ve index owner UID ile başlamalıdır. UID link/intent/form parametresinden alınmaz; yalnız aktif Firebase session kullanılır.

Account switch sırası:

1. Eski UID collector ve image request'leri iptal edilir.
2. Eski UID WorkManager işleri cancel edilir.
3. UI auth state boş/loading'e geçmeden yeni Flow bağlanmaz; eski veri tek frame gösterilmez.
4. Ledger operation, receipt, conflict, cursor ve entity tablolarında `deleteAllExceptUser(newUid)` veya sign-out'ta full Drive wipe uygulanır.
5. Document prepared/cache klasörleri UID scope ile temizlenir.
6. Yeni UID hydration başlar.

Worker başlangıç, her remote transaction öncesi ve remote dönüşünde active UID'yi doğrular. Operation owner UID eşleşmezse retry adı altında yeni kullanıcıya işlem yapılmaz; `AuthenticationChanged/AccountMismatch` typed sonuç üretir. UID, invoice, vendor, tutar, plaka, not veya Storage path loglanmamalıdır.

## 28. Summary ve raporlama sorguları

İlk sürümde canonical summary belgesi olmamalıdır. Room source-of-truth tablolarından şu projection üretilir:

`VehicleCostLedgerRow(ownerUid, vehicleId, occurredAt, primaryCategory, amountMinor, currencyCode, currencyExponent, sourceType, sourceId, syncState)`.

Projection kaynakları:

- Fuel: cost tuple'ı olan active kayıt.
- Charge: cost tuple'ı olan active kayıt.
- Service: yalnız `COMPLETED` ve total tuple'ı olan parent kayıt.
- Expense: active kayıt; refund/credit sign projection'da uygulanır.
- Service item: dahil edilmez.
- Planned service/reminder: actual total'e dahil edilmez; ayrı forecast projection olabilir.

Room Flow sorguları:

- Vehicle + date range + currency toplamı.
- Category + currency toplamı.
- Aylık trend.
- Cost per canonical distance: yalnız aynı tarih aralığında güvenilir odometer farkı varsa.
- Fuel/charge volume-energy trendleri ve full-anchor tüketim segmentleri.

Farklı currencies tek toplamda birleştirilmez. UI birden fazla currency total'i gösterir. Exchange rate yoksa “genel toplam” üretmemek doğrudur.

Büyük veri hacminde ilk optimizasyon doğru composite index ve dar date-range query olmalıdır. Ölçümle ihtiyaç kanıtlanırsa `drive_vehicle_monthly_cost_rollups` yerel, rebuildable projection eklenebilir. Rollup'ın Firestore'a yazılması önerilmez; conflict, stale derived data ve write amplification yaratır. Room transaction'ı veya background rebuild ile tekrar üretilebilir olmalıdır.

## 29. UI ve navigation önerisi

Araç detail mevcut profil/fotoğraf/assignment/odometer/trip bileşenlerini korumalıdır. Tüm gider satırlarını ana `LazyColumn` içine yüklemek yerine üç küçük summary card ve ayrı route önerilir:

- “Bu ay maliyet” — currency bazında.
- “Son yakıt/şarj” — sync/health durumu.
- “Yaklaşan bakım” — tarih/km trigger.

Yeni route yapısı:

- `drive/vehicle/{vehicleId}/costs`: overview ve filter.
- `.../energy`: fuel/charge listesi ve tüketim.
- `.../service`: planned/completed service ve reminders.
- `.../expenses`: insurance/tax/parking/wash/other.
- Record detail/editor route'ları canonical record ID taşır; plaka/başlık/UID taşımaz.

UI durumları loading/empty/content/error yanında `LOCAL_PENDING`, `RETRYABLE`, `FATAL`, `CONFLICT`, `UNSUPPORTED`, `VEHICLE_DELETED` durumlarını açık göstermelidir. Summary pending local kayıtları “provisional” etiketiyle dahil edebilir.

Fuel form fill type'ı kullanıcıdan açıkça sorar. Unit preference yalnız UI parsing/display'e uygulanır; save öncesi canonical integer'a çevrilir. Odometer kronoloji uyarısı kaydı zorunlu olarak engellemez. Duplicate uyarısı “yine de kaydet” seçeneği verir.

## 30. Feature gate önerisi

Gereksiz gate çoğaltmadan iki build-time gate yeterlidir:

- `DRIVE_VEHICLE_LEDGER`: odometer, fuel, charge, service, expense, reminder, summary ve metadata sync kök gate'i.
- `DRIVE_LEDGER_DOCUMENTS`: binary document prepare/Storage worker/UI için ayrı gate.

Root gate kapalıyken navigation görünmez, collector/hydration başlamaz, WorkManager planlanmaz, Firestore okunmaz/yazılmaz ve ledger tablolarına yeni kayıt yazılmaz. Document gate kapalıyken metadata/binary picker/cache/Storage erişimi başlamaz; diğer ledger özellikleri çalışabilir.

Additive Room tabloları gate kapalı rollback'te DB'de kalır. Bu beklenen davranıştır; destructive downgrade uygulanmaz.

## 31. Migration planı

Mevcut Room version 12'den additive ve alt sprint bazlı ilerleme önerilir:

1. 12→13: common ledger operation/metadata/receipt/conflict, odometer, expense ve reminder tabloları.
2. 13→14: fuel ve charge tabloları/index'leri.
3. 14→15: service record ve service item tabloları/index'leri.
4. 15→16: document metadata/binary operation alanları.
5. 16→17 yalnız ölçüm gerektirirse local rollup veya ek performans index'i; gereksiz schema bump yapılmamalıdır.

Her migration yalnız `CREATE TABLE`, `CREATE INDEX` ve gerekli additive `ALTER TABLE ADD COLUMN` kullanmalıdır. Gerçek KSP Room schema export'u üretilmeden migration tamamlanmış sayılmamalıdır. Destructive migration yoktur.

Legacy odometer geçişi:

- Migration SQL yalnız şemayı kurar; UUID/hash üretme ve domain validation gerektiren backfill idempotent local bootstrap job'ında yapılır.
- Her vehicle'ın finite/non-negative `currentOdometerKm` değeri metreye checked `BigDecimal` dönüşümüyle `MIGRATED` odometer entry olabilir.
- `initialOdometerKm` ayrı historical entry olarak değerlendirilebilir; aynı observed date bilinmiyorsa sahte tarih üretilmemeli, migration health/unknown date taşımalıdır.
- Existing trip endpoint'leri otomatik canonical odometer'a topluca çevrilmemelidir; overlap ve manuel tutarsızlık riski vardır. İleride açık reconciliation candidate'ı olabilir.
- `currentOdometerKm` kolonu backward compatibility mirror olarak kalır.

## 32. Test matrisi

Bu analizde test oluşturulmamış veya çalıştırılmamıştır. Uygulama sprintlerinde minimum matris:

### Saf contract/unit

- Her entity field/path/schema/unknown enum/unknown field/document-ID invariant.
- Canonical unit conversion, ISO exponent, overflow ve locale-independent parse.
- Fuel full/partial/unknown state machine; retroactive edit/delete.
- Electric full/partial/SOC algoritması.
- Mixed currency summary'nin birleştirilmemesi.
- Service status transition ve item breakdown mismatch.
- Odometer monotonic, old-date-high-value, series reset ve current selection.
- Duplicate fingerprint/idempotent operation.
- Revision/server timestamp/operation ID conflict ve delete-wins.

### Repository/sync

- Local-first create/edit/delete/restore.
- Process death ve duplicate worker claim.
- Initial/incremental paginated hydration.
- UID switch sırasında pull/push.
- Vehicle tombstone yarışları.
- Parent service/item eventual sync.
- Retryable/fatal/unsupported schema/receipt/provenance/conflict shadow.
- Storage success + metadata failure ve object-not-found delete.

### Room/migration

- Her additive migration ve gerçek schema export.
- UID isolation, Flow invalidation, tombstone ve cursor transaction.
- PK/unique operation davranışı.
- Çok büyük fuel/expense fixture ve summary query planı.
- Service parent/item, odometer source uniqueness, reminder due query.
- Account cleanup ve pending operation restart.

### UI/instrumented

- Unit preference parsing; no locale canonical data.
- Full/partial form, duplicate warning, conflict UI.
- Planned/completed service ve multi-item edit.
- Old-date odometer warning ve vehicle deleted archive.
- Multi-currency summary.
- Document picker/prepared image/cache/account switch.
- Deep link canonical record/vehicle ID doğrulaması.

### Rules emulator

- Aynı UID read/create/update/tombstone.
- Başka UID ve unauthenticated deny.
- ID/path mismatch, unknown fields, negative revision, invalid unit/currency tuple deny.
- Hard delete deny.
- Document MIME/size/metadata/path tests.

Production Firebase/Storage test için kullanılmamalıdır.

## 33. Performans riskleri

- Her vehicle altında ayrı Firestore listener N+1 üretir. Flat UID collections + paginated query kullanılmalıdır.
- Tüm tarihçeyi her detail açılışında belleğe almak yerine date-range DAO ve paging gerekir.
- Tüketim hesabı yalnız etkilenen iki full anchor aralığını yeniden hesaplayabilmelidir; tüm geçmişi sürekli taramak büyük hesapta pahalıdır.
- Çok item'lı service UI parent ve item Flow'larını scope etmelidir; global item listesi UI'ya taşınmamalıdır.
- SQLite `SUM(Long)` sınırına karşı record amount üst bound ve checked aggregate stratejisi gerekir.
- Çoklu currency, category ve date index'leri yazma maliyetini artırır; query plan ölçülmeden gereksiz index eklenmemelidir.
- Document cache toplam boyutu UID bazında quota/LRU ister; unresolved pending prepared dosya eviction'dan korunmalıdır.
- Her mutation için field provenance satırı write amplification yaratır. Yalnız değişen alanlar yazılmalıdır.
- Firestore cursor collection başına tutulduğu için sekiz collection'ı tek dev metadata row'da güncellemek conflict yaratabilir; `(ownerUid, collectionType)` PK doğrudur.
- Local monthly rollup eklenirse source revision/watermark ve full rebuild yolu olmadan canonical kabul edilmemelidir.

## 34. Güvenlik ve gizlilik

Mevcut Firestore rules dosyasında genel user-subcollection allow kuralı vardır. Yeni collection'lar yalnız bu geniş fallback'e bırakılırsa shape/revision validation bypass edilir. Her ledger collection için explicit rule yazılmalı ve genel fallback exclusion listesine eklenmelidir.

Rules minimumları:

- `request.auth.uid == userId`.
- Payload `ownerUid == userId` ve document ID/kayıt ID eşliği.
- `vehicleId`, parent/source ID'leri safe opaque ID.
- Allowed fields only.
- Supported schema range, non-negative revision ve known source/fallback policy.
- Canonical integer unit/range ve currency tuple shape.
- `_serverUpdatedAt == request.time`.
- Update revision monotonic; delete request reddi, tombstone update'i kabulü.

Rules içinde vehicle varlığını her yazımda cross `get()` ile doğrulamak maliyet, offline retry ve tombstone retention'ı karmaşıklaştırır. UID/path ve shape rules güçlü tutulmalı; vehicle existence/tombstone remote transaction + health katmanında doğrulanmalıdır.

Gizli/sensitif alanlar:

- Sigorta reference, vendor, fiş, not ve tutar loglanmamalıdır.
- Firestore/Storage payload teknik exception'a veya analytics'e eklenmemelidir.
- Document EXIF/GPS yeniden encode ile temizlenmelidir.
- Storage metadata yalnız owner path'ten türeyen teknik ID/hash/schema alanları taşımalıdır.
- Bellek'e ham gider/document projection'ı yapılmamalıdır.
- Export açık kullanıcı aksiyonu, scoped output ve hassas veri uyarısı gerektirir.

## 35. Sprint bölme önerisi

Beş alt sprint önerilir:

### Sprint 7A — Ledger ve kilometre çekirdeği

- Saf Kotlin common contract/envelope.
- `VehicleOdometerEntry`, `VehicleExpense`, `VehicleReminder`.
- Generic ledger outbox/cursor/receipt/conflict/provenance.
- Account scope, rules ve base navigation.
- `currentOdometerKm` projection stratejisi ve legacy backfill.

### Sprint 7B — Yakıt ve elektrik

- Fuel/charge entities, repository/UI.
- Canonical unit converter.
- Full/partial ve electric consumption algorithms.
- Duplicate/health ve energy summary.

### Sprint 7C — Bakım, tamir ve muayene

- Service record/item, planned/completed lifecycle.
- Date/km recurrence ve reminder completion reconciliation.
- Multi-item editor ve service health.

### Sprint 7D — Genel maliyet ve raporlama

- Insurance/tax/fees/parking/wash flows.
- Multi-currency Room Flow summary, category/month views.
- Archived vehicle financial history, performance/paging.

### Sprint 7E — Belge metadata ve hardening

- Sprint 6B prepare pipeline'ın güvenli genelleştirilmesi.
- Document outbox/Storage rules/cache.
- Full rules emulator, process-death/account-switch/device tests.
- Large fixture ve reconciliation hardening.

Driversnote import bu dizinin parçası değildir ve 7E sonrasına ertelenir.

## 36. Açık sorular

### İstenen 14 soruya açık cevap

1. **Yakıt ve elektrik şarjı aynı entity'de mi tutulmalı?** Hayır. Ayrı `VehicleFuelEntry` ve `VehicleChargeEntry`; ortak salt okunur energy projection olabilir.
2. **Bakım ile genel gider aynı entity'de mi tutulmalı?** Hayır. Bakım/tamir/muayene `VehicleServiceRecord`; sigorta/vergi/otopark/yıkama `VehicleExpense`.
3. **Servis kalemleri ayrı child entity olmalı mı?** Evet. `VehicleServiceItem` ayrı ID/revision/tombstone ile tutulmalı.
4. **Kilometrenin canonical kaynağı ne olmalı?** `VehicleOdometerEntry` event ledger'ı.
5. **`currentOdometerKm` canonical mı, projection mı olmalı?** Projection/compatibility mirror olmalı; canonical olmamalı.
6. **Eski tarihli ama yüksek kilometreli kayıt nasıl ele alınmalı?** Kaydedilmeli, current sırf yüksek olduğu için değişmemeli, non-monotonic health üretmeli ve kesişen consumption segmentleri dışlanmalı; gerçek sayaç değişimiyse yeni series açılmalı.
7. **Tüketim hesabında hangi kayıtlar dışlanmalı?** Tombstone/unsupported/conflict, eksik veya geçersiz ölçü, missing/non-monotonic odometer, farklı series, güvenilir endpoint full anchor'ı olmayan ve aşırı kısa/azalan mesafeli segmentler.
8. **Kısmi depo sonrası tüketim ne zaman hesaplanmalı?** Önceki full anchor'dan sonraki partial volume'ler, sonraki full kayıtla segment kapandığında.
9. **Bir gider birden fazla kategoriye ait olabilir mi?** Canonical primary kategori yalnız bir olmalı. Tutar bölünecekse split group altında ayrı kayıtlar; tag aynı tutarı ikinci kez saymamalı.
10. **Planlanan bakım ve tamamlanmış bakım ayrı entity mi olmalı?** Hayır. Aynı service record status transition'ı; reminder schedule için ayrı entity.
11. **Belge fotoğrafları mevcut araç fotoğraf altyapısıyla mı yönetilmeli?** Hazırlama, EXIF, hash, cache ve retry primitive'leri yeniden kullanılmalı; fakat ayrı `VehicleDocument` contract/table/path kullanılmalı.
12. **Summary değerleri saklanmalı mı, sorguyla mı hesaplanmalı?** Önce Room sorgusu/Flow ile. Yalnız performans kanıtlanırsa local rebuildable rollup; Firestore canonical summary yok.
13. **Bellek uygulamasında bu verilerden herhangi biri gösterilmeli mi?** İlk kapsamda hayır. Ham maliyet, fiş, servis notu ve sigorta verisi veri minimizasyonu nedeniyle TopluTaşıma'da kalmalı.
14. **Bu çalışma kaç alt sprint'e ayrılmalı?** Beş: 7A ledger/odometer, 7B fuel/charge, 7C service/reminder, 7D expense/summary, 7E documents/hardening.

### Uygulama öncesi gerçek ürün soruları

- Kullanıcı bir “varsayılan para birimi” seçebilecek mi, yoksa her kayıt son kullanılan code'u mu önerecek? Bu tercih canonical kayıtları değiştirmemeli.
- Refund/reimbursement UI ilk sürümde gerekli mi? Model desteklemeli, UI alt sprint kapsamına göre açılabilir.
- Odometer değişimi/rollover için kullanıcıya series yönetimi ilk sürümde gösterilecek mi, yalnız health çözüm akışı mı olacak?
- Charge full anchor'ı yalnız kullanıcı seçimiyle mi, end SOC eşiğiyle öneri olarak mı belirlenecek? Otomatik karar canonical alanı sessiz değiştirmemeli.
- Insurance için yalnız ödeme/renewal yeterli mi, yoksa gelecekte ayrı policy lifecycle beklentisi var mı?
- PDF document desteği ne zaman gerekli? İlk document sprint'i JPEG ile sınırlı kalmalıdır.
- Archived vehicle financial history normal summary'ye varsayılan dahil mi, kullanıcı filtresiyle mi dahil olacak? Öneri: varsayılan hariç, açık filtreyle dahil.

## 37. Önerilen uygulama sırası

1. Ürün kararlarını onayla: ayrı fuel/charge, service vs expense sınırı, currency behavior, odometer series ve vehicle-delete retention.
2. Firebase-independent common ledger contract'ı ve golden fixtures'ı tanımla; provider-specific import alanı ekleme.
3. Canonical integer unit/value object'leri ve overflow-safe converter'ları oluştur.
4. `VehicleOdometerEntry` ve `currentOdometerKm` projection/backfill tasarımını uygula; diğer domain'ler buna bağlanmadan önce test et.
5. Generic ledger outbox, per-collection cursor, receipt, conflict shadow, provenance ve account-scope tablolarını kur.
6. General expense/reminder foundation'ı ekle; multi-currency Room summary'nin temelini doğrula.
7. Fuel ve charge domain'lerini, ardından full/partial consumption hesaplarını ekle.
8. Service parent/item lifecycle ve reminder completion reconciliation'ı ekle.
9. Vehicle detail'e küçük summary kartları ve ayrı ledger navigation ekle; büyük listeleri paging/date scope ile aç.
10. Document metadata'yı ayrı contract ile ekle; yalnız sonra Sprint 6B image primitive'lerini testli biçimde genelleştir.
11. Explicit Firestore/Storage rules ve emulator testlerini ekle; geniş catch-all rule bypass'ını kapat.
12. Large fixture, account-switch, process-death, same-revision conflict, archived vehicle ve multi-currency device testleriyle hardening yap.
13. Her alt sprintte gerçek additive Room schema export, migration test, compile/unit/lint/APK/instrumented doğrulaması ve dürüst completion report üret.

Bu sırada Driversnote import tasarlanmamalı veya uygulanmamalıdır. İleride herhangi bir import/export sistemi, burada tanımlanan stabil IDs, schema version, canonical integer units, ISO currency tuple, source/external reference ve tombstone contract'ını tüketmelidir; mevcut domain bu amaçla provider-specific hale getirilmemelidir.
