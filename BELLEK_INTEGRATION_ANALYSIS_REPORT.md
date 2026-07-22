# Bellek Uygulaması Yapı Analizi — Sprint 6 Ön Hazırlık

Tarih: 21 Temmuz 2026

İnceleme türü: Salt okunur mimari/veri entegrasyonu analizi

İncelenen uygulamalar: Bellek ve TopluTaşıma

Bu çalışma sırasında kaynak kod, Gradle yapılandırması, Room şeması veya Firebase verisi değiştirilmedi. Kullanıcının istediği bu rapor, “dosya oluşturma” yasağının tek ve açık çıktı istisnası olarak oluşturuldu. Başlangıçta her iki çalışma ağacında da önceden var olan değişiklikler bulunuyordu; bunlar bu analizin değişiklikleri değildir.

## 1. Yönetici özeti

- Bellek ve TopluTaşıma aynı Firebase projesine (`toplutasima-e48b7`) ve aynı varsayılan Storage bucket’ına bağlıdır. İki uygulama ayrı Firebase Android app kaydı ve ayrı application ID kullanır.
- İki uygulama da Google Sign-In üzerinden aynı Firebase Authentication projesine bağlanır. Kullanıcı iki uygulamada aynı Google hesabını seçerse aynı Firebase UID’yi alabilir; bağımsız oturumlarda farklı hesap seçilmesini kod engellemez.
- Bellek’te ayrı domain modeli veya Firestore DTO’su yoktur. `PersonEntity`, Room modeli olmasının yanında repository, ViewModel ve UI’da ana kişi modeli olarak da kullanılır; Firestore dönüşümü özel extension fonksiyonlarıyla yapılır.
- Kişi kimliği `PersonViewModel.newPersonId()` içinde UUID olarak yerel üretilir. Aynı değer Room primary key, Firestore belge kimliği ve Firestore `id` alanı olarak kullanılmak üzere tasarlanmıştır.
- `DriveVehicle.assignedPersonId`, **Bellek `PersonEntity.id` alanına; bunun Firestore’daki `users/{uid}/persons/{personId}` belge kimliğiyle eşit olduğu sözleşmeye** bağlanmalıdır.
- Bellek yerel kişi tablolarında UID taşımaz. Hesap izolasyonu, oturum değişiminde tüm kullanıcı domain tablolarını temizleyen ve UI’yı aktivasyon tamamlanana kadar açmayan `UserDataSessionRepository` üzerinden sağlanır.
- Bellek local-first/outbox/tombstone yaklaşımına sahiptir; ancak her başlangıçta tam Firestore taraması yapar. Incremental pull, cursor, `_serverUpdatedAt`, alan bazlı provenance, kayıt bazlı receipt ve health altyapısı yoktur.
- TopluTaşıma Drive v10 altyapısı Bellek’ten daha olgundur: UID bileşik anahtarları, initial/incremental pull, `_serverUpdatedAt` cursor’ları, tombstone, conflict resolver, provenance, receipt, health ve account-scope temizliği vardır.
- Mevcut durumda Bellek’in Drive araç belgesini doğrudan düzenlemesi güvenli değildir. Drive yazıcısı araç belgesinde tam `transaction.set(...)` kullanır; iki uygulamanın farklı sürümleri `assignedPersonId` veya gelecekteki alanları kaybedebilir.
- En güvenli hedef, araç verisinin Drive koleksiyonunda kalması; ilişki için UID + vehicle ID kapsamlı ayrı, sürümlü bir `vehicleAssignments` sözleşmesi kullanılması; Bellek’in yalnız ilişkiyi ve salt-okunur araç özetini yönetmesidir.
- Mevcut kişi fotoğraf sistemi araç fotoğrafı için doğrudan yeniden kullanılacak olgunlukta değildir. Yalnız UID kapsamlı Storage path yaklaşımı ve Coil gösterimi fikir olarak kullanılabilir.
- Harici deep link/app link iki uygulamada da mevcut değildir. TopluTaşıma araç detayı stabil `vehicleId` ile iç state içinde açılabilir, fakat dış intent’i bu state’e bağlayan giriş noktası yoktur.
- Host kontrolleri başarılıdır: Kotlin compile, 91/91 unit test, lint, debug APK ve Android test APK derlemeleri geçti. API 36 emülatörde 11 instrumented testin 9’u geçti; 2 eski migration testi eksik `9.json` ve `10.json` şema asset’leri nedeniyle başarısız oldu. Kod değiştirilmedi.

## 2. İncelenen proje yolları

| Uygulama | Proje yolu | Gradle root | Modül | Ana kaynak paketi |
|---|---|---|---|---|
| Bellek | `C:\Users\mehme\AndroidStudioProjects\Bellek` | `Bellek` | `:app` | `com.example.bellek` |
| TopluTaşıma | `C:\Users\mehme\AndroidStudioProjects\TopluTasima` | `TopluTasima` | `:app` | `com.example.toplutasima` |

Her iki proje de tek Android application modülünden oluşur. Ortak Gradle modülü, ortak schema modülü veya ortak source-set bulunmamaktadır.

## 3. Application ID, variant, Firebase ve imzalama karşılaştırması

| Konu | Bellek | TopluTaşıma | Sonuç |
|---|---|---|---|
| Namespace / application ID | `com.example.bellek` | `com.example.toplutasima` | Ayrı Android uygulamaları |
| Debug application ID | `com.example.bellek` | `com.example.toplutasima` | Suffix yok |
| Release application ID | `com.example.bellek` | `com.example.toplutasima` | Debug ile aynı ID |
| Product flavor | Yok | Yok | Variant’a göre Firebase proje değişmiyor |
| compile SDK | 36 | Android API 36, minor API 1 | Uyumlu modern SDK tabanı |
| min SDK | 26 | 26 | Aynı minimum API |
| target SDK | 36 | 36 | Aynı target |
| Firebase project ID | `toplutasima-e48b7` | `toplutasima-e48b7` | Aynı proje |
| Storage bucket | `toplutasima-e48b7.firebasestorage.app` | Aynı | Aynı bucket |
| Firebase Android app ID | `1:856887120882:android:b9ca331bdae1b36d15916b` | `1:856887120882:android:9643ddee023595de15916b` | Aynı projede ayrı app kayıtları |
| Firestore database seçimi | `FirebaseFirestore.getInstance()` | `FirebaseFirestore.getInstance()` | İkisi de varsayılan database’i kullanıyor |
| Auth sağlayıcı | Google credential → Firebase Auth | Google credential → Firebase Auth | Aynı hesap seçilirse aynı UID mümkün |
| Firebase emulator kullanımı | Kaynakta bulunmadı | Kaynakta bulunmadı | Production/default instance kullanılıyor |
| App Check kurulumu | Kaynakta bulunmadı | Kaynakta bulunmadı | Console enforcement durumu bilinmiyor |

`google-services.json` bağlantısı:

- Bellek modülünün etkin dosyası `Bellek/app/google-services.json` dosyasıdır. Ayrıca root’ta ikinci bir kopya vardır; proje/client kimlikleri aynı olsa da iki kopya byte düzeyinde aynı değildir ve root kopyasında daha az OAuth client girdisi vardır. Bu, bakım sırasında yanlış dosyanın güncellenmesi riski yaratır.
- TopluTaşıma etkin dosyası `TopluTasima/app/google-services.json` dosyasıdır.
- Etkin iki modül dosyası aynı Firebase project ID’yi, bucket’ı ve her iki Android package kaydını içerir.

İmzalama:

- Her iki debug build açık bir özel debug signing config tanımlamadığı için aynı kullanıcı profilindeki varsayılan Android debug keystore’u kullanır; debug sertifikası yapılandırma gereği ortaktır.
- Her iki release build `keystore.properties` üzerinden gerçek release anahtarı bekler. Keystore dosyaları ve public sertifikaları okunabilir durumdadır; public sertifika karşılaştırması iki release sertifikasının **farklı** olduğunu doğrulamıştır.
- Play App Signing kullanılıp kullanılmadığı repository’den doğrulanamaz.
- Firebase/Firestore erişimi ve normal deep link için aynı imza gerekmez. Signature-permission tabanlı ContentProvider paylaşımı aynı release imzasına bağlanır ve mevcut farklı sertifikalar nedeniyle önerilmez.

## 4. Bellek mimarisi

Bellek’in doğrulanan mimarisi:

- UI: Jetpack Compose ekranları ve Compose Navigation.
- State: ViewModel + `StateFlow`/Room `Flow`; genel yaklaşım MVVM’dir.
- DI: Koin (`di/AppModule.kt`).
- Veri: Room + Firebase Auth/Firestore/Storage.
- Repository pattern: kişi, ilişki takibi, arşiv, aile ağacı, countdown, sync ve user-session repository’leri vardır.
- Background: WorkManager tabanlı `SyncRetryWorker` ve `ReminderWorker`; ayrıca alarm/boot receiver’ları.
- Offline yaklaşımı: Room-first/local-first mutasyon, kalıcı pending-operation kuyruğu, tombstone ve sonradan Firestore denemesi.
- Navigation: tek `BellekNavHost`, string route’lar ve stabil ID argümanları.
- Feature flag: build-time veya runtime feature flag altyapısı bulunmadı.

Katman değerlendirmesi:

| Yaklaşım | Durum | Kanıt/değerlendirme |
|---|---|---|
| MVVM | Var | Compose ekranları ViewModel’lerden state tüketiyor |
| MVI | Yok | Tek yönlü intent/reducer sözleşmesi genel mimari değil |
| Repository pattern | Var | `PersonRepository`, `SyncRepository`, diğer repository’ler |
| Clean Architecture | Kısmi değil / uygulanmamış | Ayrı domain modelleri ve use-case katmanı yok; Room entity UI’ya kadar çıkıyor |
| Room-first | Mutasyonlarda var | Önce Room/outbox, sonra remote deneme |
| Firebase-first | İlk hydration’da remote kaynak kullanılır | Başlangıçta tüm remote veri çekiliyor |
| Offline-first | Büyük ölçüde var | Local write, outbox, retry ve tombstone |
| Genel sonuç | Hibrit MVVM + Repository + Room-first sync | Olgun ama katman sınırları Drive kadar ayrışmış değil |

Ana paket dağılımı: `data` 43, `ui` 23, `repository` 16, `util` 10, `viewmodel` 7, `network` 4, `auth` 3, `worker` 2, `receiver` 2, `di` 1, `security` 1 Kotlin dosyası.

## 5. Kişi domain modeli

- Ana kişi modeli: `PersonEntity`.
- Ayrı domain modeli: Yok.
- Room entity: `PersonEntity`.
- Ayrı Firestore DTO/model: Yok.
- Firestore mapper: özel `PersonEntity.toMap()` ve `DocumentSnapshot.toPersonEntity()` fonksiyonları.
- Stabil ID: `PersonEntity.id`.
- Üretim: `PersonViewModel.newPersonId()` → `UUID.randomUUID().toString()`.
- İsim: `displayName` ve opsiyonel `nickname`. Ayrı ad/soyad alanı yoktur.
- Görünen ad: `nickname` boş değilse nickname, aksi halde `displayName` (`visibleName`).
- Doğum tarihi: ISO `yyyy-MM-dd`; bilinmeyen yıl `0000-MM-dd` olarak desteklenir.
- Telefon, e-posta ve adres: Yok.
- Etiket sistemi: Kişi entity’sinde yok. `relationshipType`, kişi klasörleri/membership ve aile ağacı ayrı yapılardır.
- Fotoğraf: `photoUrl`.
- Silme: Entity içinde `deletedAt` yok; `archived` silme değil arşivdir. Gerçek silme hard local/remote + ayrı tombstone ile yapılır.
- Provenance ve per-record sync state: Yok.

## 6. Kişi Room modeli

| Alan | Tür | Zorunlu mu | Room | Firestore | Açıklama |
|---|---|---:|---:|---:|---|
| `id` | `String` | Evet | PK | Evet, ayrıca doc ID | Yerel UUID; stabil kimlik |
| `displayName` | `String` | Evet | Evet | Evet | Ad/soyad tek metin alanında |
| `nickname` | `String?` | Hayır | Evet | Evet | Görünen ad önceliği |
| `birthday` | `String?` | Hayır | Evet | Evet | ISO tarih; yıl bilinmiyorsa `0000` |
| `birthdayReminderEnabled` | `Boolean` | Evet/default | Evet | Evet | Varsayılan `true` |
| `birthdayTimeZoneId` | `String?` | Hayır | Evet | Evet | Kişi bazlı doğum günü saat dilimi |
| `relationshipType` | `String` | Evet/default | Evet | Evet | FRIEND/FAMILY/COLLEAGUE veya özel değer |
| `photoUrl` | `String?` | Hayır | Evet | Evet | Firebase Storage download URL |
| `memoryNote` | `String?` | Hayır | Evet | Evet | Genel not |
| `favoritThings` | `String?` | Hayır | Evet | Evet | Yorumda JSON string; tip güvenliği yok |
| `sensitivities` | `String?` | Hayır | Evet | Evet | Yorumda JSON string |
| `dreamNote` | `String?` | Hayır | Evet | Evet | Hayaller/not |
| `discordUserId` | `String?` | Hayır | Evet | Evet | Discord kimliği |
| `discordUsername` | `String?` | Hayır | Evet | Evet | Discord kullanıcı adı |
| `lastContactDate` | `String?` | Hayır | Evet | Evet | ISO tarih |
| `plaka` | `String?` | Hayır | Evet | Evet | Mevcut/legacy kişi-plaka ilişkisi |
| `plakaUlkesi` | `String?` | Hayır | Evet | Evet | Plaka ülke kodu |
| `sonGuncellemeTarihi` | `Long?` | Hayır | Evet | Hayır | Kod yorumuna göre kullanılmıyor |
| `sharedWithTransit` | `Boolean` | Evet/default | Evet | Evet | TopluTaşıma’ya kişi görünürlüğü/izin sinyali |
| `archived` | `Boolean` | Evet/default | Evet | Evet | Arşiv; silme tombstone’u değildir |
| `createdAt` | `Long` | Evet | Evet | Evet | Client epoch millis |
| `updatedAt` | `Long` | Evet | Evet | Evet | Conflict için client epoch millis |

`persons` tablosu:

- Primary key: tek kolon `id`.
- Index’ler: `archived`, `sharedWithTransit`, `birthday`.
- UID kolonu yoktur.
- Person’a bağlı çoğu tablo fiziksel foreign key yerine repository/`PersonCascadeDao` ile temizlenir. `countdowns.personId` Room foreign key ve CASCADE kullanır; arşiv entry/account tablolarında da kendi CASCADE zinciri vardır.

## 7. Kişi Firestore modeli

Gerçek kişi yolu:

`users/{uid}/persons/{personId}`

Alt koleksiyonlar:

- `users/{uid}/persons/{personId}/conversation_logs/{logId}`
- `users/{uid}/persons/{personId}/topics/{topicId}`
- `users/{uid}/persons/{personId}/pending_questions/{questionId}`
- `users/{uid}/persons/{personId}/remembered_me/{entryId}`

Diğer kişi ilişkili user-alt koleksiyonları arasında `gifts`, `special_days`, `contact_logs`, `person_folders`, `person_folder_memberships`, `family_trees`, `family_tree_nodes`, `family_tree_relations`, `archive_lists` bulunur.

Silme kanıtı:

`users/{uid}/sync_tombstones/{sha256(entityType:entityId)}`

Kişi için `entityType = "person"`; tombstone alanları `entityId`, `entityType`, `parentId`, `relatedId`, `deletedAt` şeklindedir.

Firestore özellikleri:

- Person document ID normal akışta `PersonEntity.id` ile aynıdır.
- Payload içinde ayrıca `id` saklanır.
- Yazma `SetOptions.merge()` kullanır; bilinmeyen alanlar normal kişi kaydında silinmez.
- Person için `_serverUpdatedAt`, server timestamp, sync version veya schema version yoktur.
- `createdAt`/`updatedAt` client `Long` değerleridir.
- Eski belgede timestamp yoksa reader `System.currentTimeMillis()` fallback’i kullanır; bu legacy kaydı conflict çözümünde yanlışlıkla yeni gösterebilir.
- `sonGuncellemeTarihi` entity/constant olarak vardır ancak aktif person Firestore mapper’ına yazılmaz/okunmaz.
- Belge `id` alanı document ID’den farklıysa reader payload `id` değerini tercih eder. Normal writer ikisini eşit üretse de legacy/veri bozulması durumunda entegrasyon riski vardır.
- Person listesi için `sharedWithTransit == true` ve `archived == false` eşitlik sorgusu TopluTaşıma’da kullanılır. Repository’de Firestore index tanım dosyası yoktur; production index durumu koddan doğrulanamaz.

## 8. Stabil kişi ID analizi

| Soru | Sonuç |
|---|---|
| Cihazlar arasında stabil mi? | Evet; aynı Firestore belge hydrate edildiğinde aynı UUID gelir. |
| Aynı hesapta iki cihazda aynı mı? | Evet; document/payload ID uyumlu kayıtlar için. |
| Yeniden kurulumda korunur mu? | Firestore kaydı duruyorsa ve aynı UID ile hydrate edilirse evet. |
| Firestore document ID ile kişi ID aynı mı? | Writer sözleşmesinde evet; reader bunu zorunlu doğrulamıyor. |
| Room PK ile Firestore ID aynı mı? | Normal akışta evet. |
| Görünen ad değişince ID değişir mi? | Hayır. Edit aynı `id` değerini korur. |
| Duplicate merge ID’yi etkiler mi? | Merge sistemi yok. |
| Silinen kişi yeniden oluşturulunca eski ID gelir mi? | Normal “yeni kişi” akışında hayır; yeni UUID üretilir. Aynı ID ile açık restore UI’sı yok. |
| Başka uygulamada referans olabilir mi? | Evet, fakat document ID/payload ID eşitliği ve tombstone davranışı sözleşmeye bağlanmalıdır. |

**Net öneri:** `DriveVehicle.assignedPersonId`, Bellek `PersonEntity.id` alanına bağlanmalıdır. Sprint 6 sözleşmesinde bu değer aynı zamanda `users/{uid}/persons/{personId}` Firestore document ID’si olmalıdır. Entegrasyon katmanı payload `id` ile document ID uyuşmazlığını sessizce kabul etmemeli; legacy kayıtları health/audit sonucuna ayırmalıdır.

## 9. Authentication ve UID kapsamı

- Bellek yalnız Google Sign-In → Firebase `signInWithCredential` akışını uygular.
- Anonymous auth, e-posta/şifre auth veya custom auth kodu bulunmadı.
- UID `AuthService.currentUser?.uid` üzerinden okunur.
- Bellek genel bir auth-state Flow/listener kullanmaz. LoginActivity ve MainActivity, UI açılmadan `activateUser(uid)` çağırır.
- `UserDataSessionRepository.activateUser` global mutex altında çalışır. Yerel owner UID ile Firebase UID farklıysa domain tabloları temizlenir ve yeni owner marker yazılır.
- Yerel aktivasyon başarısızsa uygulama fail-closed davranır ve kullanıcıyı tekrar girişe yönlendirir.
- Logout önce Firebase sign-out, sonra Room kullanıcı verileri, prefs, alarm/notification ve cache temizliği yapar.
- Normal domain tabloları UID’siz olduğu için izolasyonun kritik sınırı bu temizleme işlemidir.
- UID kapsamlı tombstone/outbox kayıtları korunur; başka UID’nin sorgularına girmez ve current-user kontrolleri olmadan çalıştırılmaz.
- `SyncRetryWorker` çalışırken o anki Firebase user’ı kontrol eder; `SyncRepository` aktif UID’ye ait pending operasyonları seçer ve local owner marker eşleşmesini doğrular.
- DAO kişi Flow’ları auth değişimini `flatMapLatest` ile gözlemlemez; ancak UI aktivasyondan önce render edilmez ve `clearAllTables()` mevcut collector’lara boş liste emit eder. Yine de Drive’daki explicit auth Flow/account scope kadar doğrudan değildir.

Drive Sprint 5 ile uyumluluk:

- Drive `DriveAuthSession.authenticatedUidChanges()` ve `DriveAccountScopeManager.collectLatest` kullanır.
- Drive tablolarının her biri UID ile anahtarlanır ve başka UID satırları transaction içinde silinir.
- Bellek ile aynı UID alınabildiğinde entegrasyon mümkündür; ancak iki uygulama oturumlarının aynı Google hesabı olduğunun UI seviyesinde açıkça doğrulanması gerekir.

## 10. Room ve migration yapısı

- Database: `AppDatabase`.
- Dosya: `bellek_db`.
- Sürüm: 24.
- `exportSchema = true`.
- Kodda 1→24 kesintisiz additive/rebuild migration zinciri vardır; destructive migration kullanılmaz.
- Repository’de export edilmiş şemalar yalnız 20, 21, 22, 23 ve 24 için bulunur.

19 entity:

`persons`, `person_folders`, `person_folder_memberships`, `special_days`, `gifts`, `contact_logs`, `deleted_records`, `archive_lists`, `archive_entries`, `archive_social_accounts`, `family_trees`, `family_tree_nodes`, `family_tree_relations`, `conversation_logs`, `topics`, `pending_questions`, `remembered_me`, `countdowns`, `pending_sync_operations`.

UID kapsamı:

- `persons` ve normal domain tabloları UID içermez.
- `deleted_records` PK: `ownerUid + entityType + entityId`.
- `pending_sync_operations` PK: `operationId`; index’ler `ownerUid + nextAttemptAt` ve `ownerUid + entityType + entityId`.
- Sync receipt, provenance veya health tablosu yoktur.

Uygulamalar arası referans bütünlüğü:

- Bellek ve TopluTaşıma ayrı sandbox ve ayrı Room dosyaları kullanır; bir uygulamanın Room foreign key’i diğer uygulamanın tablosuna bağlanamaz.
- `assignedPersonId` için fiziksel cross-app FK mümkün değildir ve denenmemelidir.
- Bütünlük Firestore’daki canonical person ID, UID kapsamı, tombstone gözlemi ve local health doğrulamasıyla eventual biçimde korunmalıdır.

## 11. Offline-first ve sync yapısı

Bellek kişi kaydı:

1. Repository kullanıcı scope’unu doğrular.
2. Room transaction içinde upsert uygulanır.
3. UPSERT veya RESTORE pending operation kaydedilir.
4. WorkManager retry planlanır.
5. Firestore yazımı denenir; hata durumunda Room kaydı korunur.

İlk sync:

1. Aktif UID’nin pending outbound işlemleri önce yürütülür.
2. Persons, alt koleksiyonlar, ilişkili koleksiyonlar ve tombstone’lar tam olarak çekilir.
3. Remote tombstone’lar ayrı Room transaction’ında uygulanır.
4. Remote aktif veri tek ana Room transaction’ında conflict resolver ile uygulanır.
5. Remote’da bulunmayan local kayıtlar outbound queue’ya alınır.

Özellik karşılaştırması:

| Özellik | Bellek | Drive | Uyum/risk |
|---|---|---|---|
| Local-first create/update/delete | Var | Var | Uyumlu |
| Kalıcı outbound queue | Var | Var | Modeller farklı, doğrudan paylaşılmamalı |
| Initial hydration | Tam fetch | Tam fetch + metadata | Bellek her girişte tekrar tam okur |
| Atomic remote apply | Büyük ölçüde transaction | Transaction/koordinatör | Bellek tombstone ve aktif apply iki ayrı transaction |
| Incremental pull | Yok | `_serverUpdatedAt` + doc ID cursor | En önemli olgunluk farkı |
| Server timestamp | Yok | Var | Person conflict saat sapmasına açık |
| Conflict | Whole-record LWW + deterministic hash tie-break | Delete-wins + alan bazlı merge | Aynı belgeyi iki app yazarsa Drive sözleşmesi tercih edilmeli |
| Delete-wins | Tombstone ile var | Tombstone ile var | Uyumlu kavram, farklı temsil |
| Remote restore | Remote tombstone kalkarsa destekleniyor | Conflict/tombstone politikası | Açık sözleşme gerekli |
| Idempotency | Deterministik operation ID | Operation ID + receipt | Drive daha gözlemlenebilir |
| Sync receipt | Global geçici status | UID/record kapsamlı kalıcı receipt | Bellek’te kayıt bazlı receipt yok |
| Retry/fatal ayrımı | Var | Var | Uyumlu |
| Worker restart | Kalıcı outbox | Kalıcı outbox/metadata | Uyumlu |
| Provenance | Yok | Alan bazlı Room provenance | Bellek assignment yazısı için ortak kaynak sözleşmesi gerekli |
| Health | Yok | Var | Silinmiş person linki Drive health’e eklenmeli |
| Account scope | DB temizleme + marker | UID bileşik anahtar + collectLatest purge | Drive daha güçlü/yerel olarak doğrulanabilir |

## 12. Silme, tombstone ve duplicate davranışı

Kişi silme:

- UI silme onayı aldıktan sonra kişi ve bağımlı local veriler `PersonCascadeDao` ile hard delete edilir.
- Aynı transaction içinde UID kapsamlı person tombstone ve DELETE outbox kaydı oluşturulur.
- Kişi UI’dan hemen kalkar.
- Remote retry akışı kişi fotoğrafını, person alt koleksiyonlarını/ilişkilerini ve person belgesini hard delete eder.
- Firestore `sync_tombstones` kaydı remote cihazlara silmeyi taşır.
- Storage “object not found” silme sonucu idempotent başarı kabul edilir.
- Silme başarısızsa local tombstone ve pending operation korunur.

Duplicate/merge:

- Kişiler için duplicate tespit sistemi bulunmadı.
- Kişi merge fonksiyonu bulunmadı.
- Eski ID → yeni ID alias/redirect tablosu bulunmadı.
- Aile ağacındaki “aynı kişiyi iki kez ekleme” kontrolü kişi duplicate merge sistemi değildir.

Silinmiş kişiye bağlı araç için öneri:

1. Araç kaydı silinmemeli.
2. `assignedPersonId` otomatik temizlenmemeli; aksi halde audit bilgisi ve olası restore ilişkisi kaybolur.
3. Person tombstone veya “person bulunamadı” sonucu ilişkiyi `DANGLING/DELETED_PERSON` health uyarısına çevirmeli.
4. UI “Kişi silinmiş veya erişilemiyor” göstermeli ve kullanıcıya yeniden ata/atamayı kaldır seçenekleri sunmalı.
5. Aynı kişi ID’si gerçek restore ile geri gelirse ilişki otomatik yeniden çözülebilmeli.

Gelecekte kişi merge eklenirse durable Drive referansları nedeniyle alias/redirect zorunlu hale gelir. Önerilen kayıt `oldPersonId -> canonicalPersonId`, UID kapsamlı, tombstone’dan farklı ve döngüsüz olmalıdır.

## 13. Fotoğraf altyapısı

Mevcut kişi fotoğraf akışı:

- UI kaynakları: `ActivityResultContracts.GetContent()` ve `TakePicture()`.
- Kamera geçici dosyası: `cacheDir/photos/photo_<timestamp>.jpg`, FileProvider ile paylaşılır.
- Storage path: `users/{uid}/persons/{personId}/photo.jpg`.
- Upload: UI doğrudan singleton `StorageService.uploadPersonPhoto(...)` çağırır.
- Remote temsil: download URL `PersonEntity.photoUrl` ve person Firestore belgesinde saklanır.
- Görüntüleme: Coil `AsyncImage`.
- Kişi silme: Storage objesi remote delete retry akışına dahildir.

Bulunmayan/eksik özellikler:

- Thumbnail yok.
- Boyut sınırı yok.
- Decode/resize/compression yok.
- EXIF ve konum metadata temizliği yok.
- Upload outbox/retry yok.
- Upload iptali ve progress domain state’i yok.
- Upload hataları edit ekranında sessizce yutuluyor.
- Fotoğraf kişi kaydedilmeden upload edildiği için kullanıcı formdan vazgeçerse orphan Storage objesi oluşabilir.
- Özel Coil cache anahtarı veya invalidation yok; yalnız Coil varsayılanları kullanılır.
- Offline görüntüleme yalnız daha önce Coil cache’e alınmışsa mümkündür; garanti edilmez.
- Hesap değişiminde app `cacheDir` temizlenir ve Room kişi URL’leri silinir, fakat Coil memory cache için açık kullanıcı-scope purge kodu yoktur.
- Storage security rules dosyası repository’lerde yok; production Storage rules doğrulanamadı.

Yeniden kullanım kararı:

Mevcut `StorageService` araç fotoğrafları için doğrudan kopyalanmamalı veya genişletilmemelidir. Yeniden kullanılabilir fikirler UID kapsamlı path ve Coil gösterimidir. Araç fotoğrafı için ayrı repository/outbox, boyutlandırma, EXIF temizliği, cancellation, idempotent upload/delete ve kullanıcı-scope cache anahtarı gerekir.

Önerilen gelecek path:

`users/{uid}/vehicles/{vehicleId}/photos/{photoId}.jpg`

Tek “primary” fotoğraf istenirse metadata ayrı tutulmalı ve primary fotoğraf ID’si araç belgesine eklenmelidir. Uzun ömürlü download URL yerine mümkünse `storagePath` saklanmalı, URL gerektiğinde çözülmelidir.

## 14. Kişi detay ekranı ve navigation

`PersonDetailScreen` şu içerikleri gösterir:

- Başlıkta `visibleName`, fotoğraf veya baş harf.
- Kişi ayarları, düzenleme ve silme aksiyonları.
- Bilgiler sekmesi: ad soyad, nickname, doğum tarihi, reminder, saat dilimi, ilişki türü, favoriler, hassasiyetler, hayaller, not, Discord bilgileri ve `sharedWithTransit` durumu.
- Son konuşma kaydı, konular, bekleyen sorular ve “beni hatırladı” kayıtları.
- Ayrı sekmeler: özel günler, hediyeler ve iletişim logları.
- Düzenleme, arşiv/silme ve reminder yönetimi.

Bulunmayanlar:

- Telefon, e-posta ve adres alanı/modeli yoktur.
- Kişi paylaşma aksiyonu yoktur.
- Araç listesi yoktur.
- Person detail için harici deep link/app link yoktur.

İç navigation route:

`person_detail/{personId}`

Bu route stabil person ID kullanır. Ancak yalnız Compose NavHost içinde kayıtlıdır; Android manifest intent filter veya dış intent köprüsü yoktur.

## 15. Kişi detayında araç gösterme seçenekleri

| Seçenek | Offline | Kod tekrarı | Tutarlılık | Güvenlik | İki app’te edit | Bağımsız release | Migration/maliyet | UX | Karar |
|---|---|---|---|---|---|---|---|---|---|
| 1. Bellek Firestore `vehicles` doğrudan okur | Yalnız SDK cache kadar | Başta düşük | Conflict/outbox bypass riski | UID rules’a bağlı | Güvensiz | Orta | Okuma maliyeti orta | Online’da hızlı | Tek başına önerilmez |
| 2. Ortak repository/modül | Local adapter ile güçlü | En düşük uzun vadeli | Yüksek | Ortak doğrulama | Evet | Sürümleme ister | İlk yatırım orta | En tutarlı | Önerilen çekirdek |
| 3. TopluTaşıma deep link | TopluTaşıma cache’i kadar | Çok düşük | Drive tek owner | Güçlü doğrulama mümkün | Bellek içinde edit yok | Yüksek | Düşük | Uygulama geçişi var | Detay için önerilir |
| 4. Bellek salt-okunur araç özeti | Local projection ile iyi | Düşük/orta | Kaynak Drive kalır | UID scope | Hayır | İyi | Additive Room projection | İyi | 6A için uygun |
| 5. Bellek tam araç edit | İyi yapılabilir | Çok yüksek | Schema drift yüksek | İki yazıcı riski | Evet | Zayıf | İki migration/sync motoru | Tek app hissi | Önerilmez |
| 6. ContentProvider | Provider app yüklüyse | Orta | Yerel veriye bağlı | Signature/permission karmaşık | Sınırlı | Zayıf | Process/izin riski | Kırılgan | Reddedildi |
| 7. Hibrit: local özet + assignment-only sözleşme + deep link | Güçlü | Kontrollü | En yüksek | UID + dar yazım | İlişki iki app’te | İyi | Orta | Dengeli | Önerilen |

Önerilen kullanıcı akışı:

- Bellek kişi detayında local Room projection’dan plaka/araç adı listelenir.
- Kullanıcı ilişkiyi Bellek içinde assignment-only akışla değiştirebilir.
- Plakaya dokununca TopluTaşıma yüklüyse stabil `vehicleId` deep link’i açılır.
- TopluTaşıma yoksa Bellek salt-okunur özet göstermeye devam eder; kurulum bağlantısı ancak güvenilir mağaza URL’si tanımlandığında sunulur.

## 16. İki uygulamada düzenleme uygunluğu

Mevcut durumda güvenli değildir:

- Bellek Drive DTO’larını tanımaz.
- Ortak schema modülü yoktur.
- Bellek’te Drive local cache/outbox yoktur.
- Drive araç remote writer’ı `transaction.set(document, fields)` ile tam belge yazar; `SetOptions.merge()` kullanmaz.
- Bellek aynı araç belgesine kendi eksik DTO’suyla tam set yaparsa Drive alanları kaybolur.
- Bellek yalnız `assignedPersonId` update etse bile offline TopluTaşıma’nın daha sonra yaptığı stale full write ilişkiyi geri çevirebilir.
- Drive alan provenance’i Room’da tutulur; Firestore’da ortak alan-level provenance sözleşmesi yoktur.

Güvenli yazım sözleşmesi önerisi:

1. Araç ve trip alanlarının sahibi TopluTaşıma Drive domain’i olarak kalır.
2. İlişki için canonical belge eklenir: `users/{uid}/vehicleAssignments/{vehicleId}`.
3. Minimum alanlar: `vehicleId`, `personId`, `schemaVersion`, `revision`, `updatedAt`, `_serverUpdatedAt`, `_operationId`, `source`, opsiyonel `deletedAt`.
4. Her iki app local-first assignment outbox kullanır; offline UI local projection’ı günceller.
5. Worker online olduğunda transaction içinde aracın aynı UID’ye ait ve tombstone olmadığını doğrular, assignment revision’ı karşılaştırır ve idempotent operation uygular.
6. `users/{uid}/vehicles/{vehicleId}.assignedPersonId` eski sürümler için compatibility mirror olabilir; yeni app’ler assignment belgesini canonical kabul eder.
7. Yeni Drive writer genel araç güncellemesinde assignment alanını yazmamalı veya canonical assignment’ı transaction içinde korumalı; bilinmeyen alanları full overwrite etmemelidir.
8. Bellek araç belgesine full set yapmamalı. Yalnız assignment repository’si dar alan sözleşmesini kullanmalıdır.
9. Provenance yalnız assignment olayı için `source = BELLEK/TOPLUTASIMA` ve revision üzerinden korunmalı; tüm araç snapshot’ı kopyalanmamalıdır.
10. Eski sürüm mirror’ı bozarsa yeni istemci canonical assignment belgesinden mirror’ı iyileştirebilir.

Firestore transaction offline çalışmadığı için transaction doğrudan UI işlemi olamaz; local outbox + retry zorunludur.

## 17. Drive ile teknik karşılaştırma

| Konu | Bellek | Drive | Sonuç |
|---|---|---|---|
| Ana model | `PersonEntity` her katmanda | `DriveVehicle`/`DriveTrip` domain + entity mapper | Drive sınırı daha temiz |
| Stabil ID | UUID, doc ID ile eşit amaçlanıyor | Stabil local ID, UID bileşik Room PK | Entegrasyon mümkün |
| UID Room | Person’da yok | Tüm Drive tablolarında var | Bellek cache’i ayrı owner reset’e bağlı |
| Firestore path | `users/{uid}/persons/{id}` | `users/{uid}/vehicles/{id}`, `driveTrips/{id}` | Aynı user root |
| `assignedPersonId` | Kaynak `PersonEntity.id` | Vehicle alanı var ama ad çözümlenmiyor | Doğrudan eşleşme noktası hazır |
| Mevcut Toplu kişi cache’i | Bellek producer | `ProfileEntity(userId,id)` ve `ProfileSyncRepository` | Aynı ID zaten Toplu’ya taşınıyor |
| `_serverUpdatedAt` | Yok | Var | Person tarafında saat sapması riski |
| Tombstone | Ayrı `sync_tombstones`, remote doc hard delete | Vehicle/trip belgesinin `deletedAt` tombstone’u | Adapter gerekir |
| Provenance | Yok | Alan bazlı Room tablosu | Assignment sözleşmesi kaynak belirtmeli |
| Receipt | Global/in-memory sync status | Kalıcı UID/record receipt | Drive daha olgun |
| Health | Yok | Odometer/orphan/duplicate vb. | Dangling person health eklenebilir |
| Account switch | DB wipe + owner marker | Auth Flow + UID purge | İki tarafta da koruma var, mekanizma farklı |
| Initial hydration | Her giriş tam tarama | Metadata ile bir kez | Bellek maliyeti yüksek |
| Incremental pull | Yok | Server cursor | Bellek vehicle projection yeni Drive cursor’ını örnek almalı |
| Full document overwrite | Person writes merge | Drive vehicle writes full set | Cross-app assignment için kritik risk |

TopluTaşıma’da hâlihazırda `FirestorePersonService` şu sorguyu yapar:

`users/{uid}/persons` + `sharedWithTransit == true` + `archived == false`

Sonuç `ProfileEntity` tablosuna `userId + id` bileşik anahtarıyla alınır. Bu, Sprint 6’da kişi adını Drive UI’da çözmek için PersonalTrip katmanına bağımlı olmadan kullanılabilecek mevcut entegrasyon noktasıdır. `sharedWithTransit`, TopluTaşıma’da kişi görünürlüğü için consent sınırı olarak korunmalıdır.

## 18. Güvenlik ve privacy analizi

Firestore rules:

Repository’de bulunan tek kural dosyası TopluTaşıma `firestore.rules` dosyasıdır:

`/users/{userId}/{document=**}` altında read/write yalnız `request.auth.uid == userId` olduğunda izinlidir.

Bu kural production’a deploy edilmişse:

- Bellek kendi UID’sinin persons koleksiyonunu okuyup yazabilir.
- TopluTaşıma aynı UID’nin persons ve vehicles koleksiyonlarını okuyabilir.
- Bellek aynı UID’nin vehicles/vehicleAssignments alanına teknik olarak erişebilir.
- Yetki uygulama adına değil kullanıcı UID’sine bağlıdır.

Belirsizlikler:

- Kuralın production’da deploy edilip edilmediği koddan doğrulanamaz.
- Bellek repository’sinde rules dosyası yoktur.
- Storage rules bulunmadığı için kişi/araç fotoğrafı erişimi doğrulanamaz.
- App Check ve custom claims kullanımı kaynakta bulunmadı; console enforcement bilinmiyor.

Privacy önerileri:

- TopluTaşıma kişi directory’si yalnız `sharedWithTransit` kişileri göstermeli.
- Assignment belgesinde kişi adı, fotoğraf URL’si veya not kopyalanmamalı; yalnız stabil `personId` tutulmalı.
- Deep link UID taşımamalı; yalnız vehicle ID almalı ve aktif UID repository tarafından yeniden doğrulanmalı.
- Plaka ve kişi adı loglanmamalı.
- Fotoğraf cache anahtarları UID ile scope edilmeli ve hesap değişiminde memory/disk cache temizlenmeli.
- Security rules assignment belge şekli, owner path ve izin verilen field setini doğrulamalı; yalnız UI kısıtı güvenlik sayılmamalı.

## 19. Risk matrisi

| Risk | Olasılık | Etki | Mevcut koruma | Sprint 6 önerisi |
|---|---|---|---|---|
| Payload person `id` ile doc ID farklılığı | Orta | Yüksek | Normal writer eşit üretir | Eşitliği validate et; legacy health/audit |
| İki app’te farklı Google hesabı | Orta | Kritik | UID path ve local scope | İlişki ekranında hesap uyuşmazlığını açık göster; UID dışı link kabul etme |
| Full document overwrite | Yüksek | Kritik | Person write merge; Drive write full set | Ayrı assignment doc + dar partial write |
| Farklı schema sürümleri | Yüksek | Yüksek | Enum fallback’leri | `schemaVersion`, compatibility tests, ortak contract |
| Silinen kişiye bağlı araç | Yüksek | Orta/Yüksek | Person tombstone var | ID’yi koru, dangling health, kullanıcı onaylı reassign |
| Gelecek duplicate person merge | Orta | Yüksek | Merge yok | Merge gelmeden alias/redirect sözleşmesi |
| Offline iki taraflı ilişki edit’i | Yüksek | Yüksek | Ayrı outbox’lar olabilir | Revision + idempotent op + deterministic conflict |
| Eski app yeni alanları siler | Yüksek | Yüksek | Person merge write; Drive’da yok | Canonical assignment doc; Drive full set’i düzelt |
| Fotoğraf cache sızıntısı | Orta | Yüksek | CacheDir temizliği | UID-keyed ImageLoader/cache purge |
| Hesap değişiminde eski fotoğraf görünmesi | Orta | Yüksek | Room/cacheDir wipe | Memory cache ve UI state’i auth `collectLatest` ile sıfırla |
| Firestore hard delete | Orta | Yüksek | Ayrı person tombstone | Integration tombstone reader ve ilişki health |
| Legacy person kayıtları | Orta | Yüksek | Fallback reader | Canonicalization audit; sessiz tarih/ID tahmini yapma |
| Person `_serverUpdatedAt` eksikliği | Yüksek | Yüksek | Client `updatedAt` + hash tie | Integration projection’da server cursor/version ekle |
| Rules uyumsuz/deploy edilmemiş | Orta | Kritik | Local owner rule dosyası | Emulator rules tests + deploy checklist |
| Deep-link spoofing | Orta | Yüksek | Şu an deep link yok | Explicit package, strict route, auth/ID validation; link güvenlik sınırı değil |
| Aynı imzaya bağımlı tasarım | Düşük (kaçınılırsa) | Yüksek | Firebase UID imzadan bağımsız | Signature-only provider kullanma; release certler farklı |
| Room DB’lerini doğrudan paylaşma | Yüksek (önerilirse) | Kritik | Android sandbox | Yalnız Firestore kimliği + app-local projection |

## 20. Önerilen Sprint 6 mimarisi

### Kimlik ve veri sahipliği

- Person canonical ID: Bellek `PersonEntity.id` = Firestore person document ID.
- Vehicle canonical ID: Drive `DriveVehicle.id` = Firestore vehicle document ID.
- Araç/trip ana verisinin sahibi: TopluTaşıma Drive domain’i.
- Kişi ana verisinin sahibi: Bellek person domain’i.
- Ortak ilişki kaynağı: UID kapsamlı, sürümlü `vehicleAssignments` belgesi.

### Bellek tarafı

- UI doğrudan Firestore kullanmamalı.
- `LinkedVehicleSummaryRepository` benzeri integration repository; Bellek’e ait Room projection ve assignment outbox kullanmalı.
- Projection yalnız gerekli alanları içermeli: UID, vehicleId, displayName, licensePlate, fuelType özeti, personId, deletedAt, server cursor/version.
- Kişi detay ekranı projection Flow’unu `personId` ile filtrelemeli.
- Bellek yalnız assignment komutu yazmalı; araç/trip alanlarını düzenlememeli.

### TopluTaşıma tarafı

- Mevcut `ProfileSyncRepository`/`ProfileEntity` kişi adı çözümlemek için kullanılmalı; PersonalTrip repository’ye bağımlılık kurulmamalıdır.
- `sharedWithTransit=false` kişi yeni seçim listesinde görünmemeli. Önceden atanmış ama artık paylaşılmayan ID “erişilemiyor” olarak korunmalı.
- Drive pull coordinator assignment belgelerini incremental olarak hydrate etmeli.
- Room `DriveVehicle.assignedPersonId` canonical assignment projection’ından materyalize edilebilir.
- Assignment değişimi Drive provenance/receipt/health ile bütünleşmeli.

### Conflict resolution

- Delete-wins: vehicle tombstone varsa assignment oluşturma/update reddedilir.
- Person tombstone araç silmez; dangling health üretir.
- Aynı operation ID ikinci kez gelirse idempotent başarı.
- Farklı offline edit’lerde yüksek revision kazanır; aynı revision’da server timestamp + deterministic operation ID tie-break kullanılır.
- Client device saatine tek başına güvenilmez.
- General vehicle edit assignment revision’ını değiştiremez.

### Fotoğraf

- Sprint 6B’ye bırakılmalı.
- Storage path UID/vehicle ID kapsamlı olmalı.
- Metadata’da storage path, content hash, width/height, byte size ve upload state tutulmalı; UID/name/note metadata’ya kopyalanmamalı.
- Upload önce local hazırlık/EXIF strip/resize, sonra kalıcı outbox üzerinden yapılmalı.

### Navigation

- Mevcut dış deep link yoktur.
- Minimum sözleşme: `toplutasima://drive/vehicle/{vehicleId}` veya üretim domain’i varsa verified HTTPS App Link.
- Bellek intent’i explicit package `com.example.toplutasima` ile sınırlandırmalı.
- TopluTaşıma vehicle ID’yi aldıktan sonra aktif Firebase UID altında repository’den yeniden yüklemeli; link UID veya araç snapshot’ı taşımamalı.
- App yoksa salt-okunur Bellek özeti kalmalı; doğrulanmış store URL yoksa uydurma fallback sunulmamalı.
- TopluTaşıma’dan Bellek kişi detayına dönüş Sprint 6 için zorunlu değildir; gerekirse aynı pattern ile sonra eklenebilir.

### Feature flags

İki uygulamada bağımsız build-time gate önerilir:

- Bellek: `DRIVE_PERSON_LINKS`.
- TopluTaşıma: `DRIVE_PERSON_DIRECTORY` ve gerekirse tek assignment integration gate.

Gate kapalıyken collector, worker, disk yazımı ve navigation girişi başlamamalıdır.

## 21. Ortak model/modül seçenekleri

| Seçenek | Avantaj | Risk | Değerlendirme |
|---|---|---|---|
| A — Modeller ayrı | Bağımsız build/release | Alan adı ve enum drift’i, çift validator | Mevcut durum; büyüyen entegrasyon için zayıf |
| B — Ortak Android/Kotlin modülü | Firebase/Android adapter’ları da paylaşabilir | Android/AGP/Compose bağımlılığı ve sıkı release coupling | Fazla geniş |
| C — Ayrı saf Kotlin schema modülü | Alan adları, sürüm, enum, ID/revision kuralı tek yerde; Android bağımsız | Artifact/version yönetimi gerekir | En sağlam öneri |
| D — Yalnız Firestore sözleşme dokümanı | En düşük kurulum maliyeti | Compile-time koruma yok | C’ye geçişte zorunlu ek doküman, tek başına yetersiz |
| E — Backend/Cloud Functions | Merkezi invariant enforcement | Operasyonel maliyet ve gereksiz karmaşıklık | Şimdilik gerekli değil |

Öneri: küçük bir **saf Kotlin contract modülü** yalnız schema sabitleri, version, enum/fallback, ID/revision ve golden test fixture’larını içermelidir. Room entity, Firebase SDK, Android UI ve repository bu modüle girmemelidir. İki repository bağımsız kaldığı için artifact sürümü pinlenmeli; ayrıca insan-okunur Firestore sözleşme dokümanı tutulmalıdır.

## 22. Önerilen Sprint 6 kapsamı

Tek sprintte kişi entegrasyonu, iki yönlü offline edit, fotoğraf, ayrıntılı araç profili ve import yapmak gerçekçi değildir. İki dalga önerilir.

### Sprint 6A — Zorunlu entegrasyon çekirdeği

- Canonical person ID/document ID invariant audit’i.
- Saf Kotlin shared contract veya en azından versioned contract + golden compatibility tests.
- `vehicleAssignments` Firestore sözleşmesi ve UID/shape security rules testleri.
- Bellek local vehicle summary projection + incremental hydration metadata.
- Bellek assignment-only outbox, retry/fatal/idempotency ve account switch.
- TopluTaşıma assignment hydration/conflict/provenance/receipt/health entegrasyonu.
- `sharedWithTransit` consent sınırıyla kişi adını Drive’da çözme.
- Kişi silme/dangling assignment health davranışı.
- Bellek kişi detayında plaka listesi ve ilişki düzenleme.
- Stabil vehicle ID deep link giriş noktası ve auth doğrulaması.
- Eski uygulama sürümleriyle compatibility/mirror stratejisi.
- Feature gate’ler ve iki uygulamada UID switch testleri.

### Sprint 6B — Ertelenebilir zenginleştirme

- Araç fotoğraf repository/outbox/Storage rules/cache güvenliği.
- Ayrıntılı araç profilinin Bellek’te salt-okunur genişletilmesi.
- TopluTaşıma’dan Bellek kişi detayına ters navigation.
- Person merge alias/redirect altyapısı, ancak Bellek’te merge özelliği gerçekten eklenecekse.
- Gelişmiş ilişki geçmişi/audit UI.

Sprint 7 veya sonrası:

- Driversnote import.
- Bellek içinde tam araç/trip düzenleme.
- Çoklu araç fotoğraf galerisi.
- Backend/Cloud Functions ancak gerçek server-side invariant ihtiyacı kanıtlanırsa.

## 23. Test altyapısı

Bellek test envanteri:

| Kaynak seti | Dosya | `@Test` sayısı | İçerik |
|---|---:|---:|---|
| `src/test` | 20 | 91 | Tarih, auth scope, conflict, retry, pending DAO, Robolectric migration/reminder, person display vb. |
| `src/androidTest` | 3 | 11 | Room migration, person cascade, folder membership DAO |

Bulunan altyapı:

- Unit/Robolectric testler var.
- Room migration ve DAO instrumented testleri var.
- UID isolation policy ve owner-scoped pending queue testleri var.
- Firebase error classifier testleri yalnız exception nesnesi kullanır; remote Firebase’e bağlanmaz.

Bulunmayan testler:

- Firebase fake/emulator integration.
- Initial hydration end-to-end.
- Repository auth switch end-to-end.
- Fotoğraf upload/delete/cache testleri.
- Navigation/deep link testleri.
- Compose kişi ekranı testleri.
- Person document ID/payload ID mismatch testi.
- İki uygulama schema compatibility testi.

Production Firebase güvenlik kontrolü: Test kaynaklarında gerçek `FirebaseFirestore`, `FirebaseAuth` veya `FirebaseStorage` write akışı bulunmadı. Bu nedenle cihaz testlerini çalıştırmak güvenli kabul edildi.

## 24. Çalıştırılan komutlar ve sonuçları

| Komut | Gerçek sonuç |
|---|---|
| `git status --short` (iki repo, başlangıç) | Çalıştı; iki repoda da önceden mevcut dirty değişiklikler vardı |
| `git diff --check` (iki repo, başlangıç) | Exit 0; yalnız CRLF dönüşüm uyarıları |
| `git status --short` (iki repo, bitiş) | Çalıştı; başlangıç durumuna ek olarak yalnız bu rapor TopluTaşıma’da yeni dosya olarak göründü |
| `git diff --check` (iki repo, bitiş) | Exit 0; yalnız CRLF dönüşüm uyarıları |
| `adb version` | ADB 1.0.41 / platform-tools 36.0.2 |
| `adb devices -l` | `emulator-5554 device` |
| `gradlew.bat compileDebugKotlin` | Başarılı, exit 0 |
| `gradlew.bat testDebugUnitTest` | Başarılı, 91/91 |
| `gradlew.bat lintDebug` | Başarılı, 0 issue |
| `gradlew.bat assembleDebug` | Başarılı; task UP-TO-DATE |
| `gradlew.bat assembleDebugAndroidTest` | Başarılı; task UP-TO-DATE |
| `gradlew.bat connectedDebugAndroidTest` | Başarısız: 11 toplam, 9 geçti, 2 failed, 0 error, 0 skipped |

Unit test sonucu:

- Total: 91
- Passed: 91
- Failed: 0
- Errors: 0
- Skipped: 0

Instrumented test sonucu:

- Emulator: `Medium_Phone_API_36.1(AVD)`
- Android: 16
- API: 36
- Total: 11
- Passed: 9
- Failed: 2
- Errors: 0
- Skipped: 0

İki başarısız test:

- `migration9To10CreatesFamilyTreeTablesAndKeepsPersons`
- `migration10To11CreatesNestedFolderMembershipSchemaAndMigratesFolderIds`

Ortak neden: `MigrationTestHelper` asset’lerinde sırasıyla `com.example.bellek.data.local.AppDatabase/9.json` ve `10.json` bulunmuyor. Repository’de yalnız 20–24 şemaları mevcut. Talimat gereği test veya kaynak düzeltilmedi.

APK durumları:

- `Bellek/app/build/outputs/apk/debug/app-debug.apk` — 26,955,585 byte.
- `Bellek/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` — 899,309 byte.
- Her iki assemble task’i UP-TO-DATE olduğu için dosya zaman damgaları bu analiz turundan önceye aittir; yeni üretilmiş gibi değerlendirilmemiştir.

## 25. Değiştirilen dosyalar

Bu analiz tarafından değiştirilen kaynak dosya: **Yok**.

Bu analiz tarafından oluşturulan tek dosya:

- `TopluTasima/BELLEK_INTEGRATION_ANALYSIS_REPORT.md` — kullanıcı tarafından istenen analiz çıktısı.

Gradle ve cihaz testleri yalnız ignored `build/` ve `.gradle-local/` çıktıları oluşturdu/güncelledi. Başlangıçta mevcut Bellek ve TopluTaşıma çalışma ağacı değişiklikleri korunmuş, geri alınmamış ve bu analize ait sayılmamıştır. Git commit oluşturulmadı.

## 26. Bilinen belirsizlikler

- Local `firestore.rules` dosyasının production’a deploy durumu bilinmiyor.
- Firebase Storage rules repository’de yok; photo path erişimi doğrulanamadı.
- App Check’in Firebase Console’da zorunlu olup olmadığı doğrulanamadı.
- Google OAuth SHA fingerprint kayıtlarının hem farklı release sertifikaları için Console’da eksiksiz olduğu doğrulanamadı.
- Play App Signing durumu repository’den belirlenemedi.
- Production kişi belgelerinde payload `id`/document ID uyuşmazlığı veya legacy timestamp dağılımı okunmadı; production’a read/write yapılmadı.
- Firestore composite index deployment durumu bilinmiyor.
- İki uygulamada kullanıcının pratikte aynı Google hesabıyla giriş yaptığı yalnız canlı kullanıcı oturumunda doğrulanabilir.
- Mevcut dirty çalışma ağacındaki Sprint 4/5 Drive kodu commit edilmemiştir; analiz mevcut filesystem durumunu esas alır.

## 27. Cevaplanması gereken sorular

1. `sharedWithTransit` Drive kişi seçimi için açık consent sınırı olarak korunacak mı?
2. `vehicleAssignments` canonical kaynak olarak kabul edilip vehicle içindeki `assignedPersonId` yalnız compatibility mirror olacak mı?
3. Production application ID’ler `com.example.*` olarak mı kalacak, yoksa deep link sözleşmesinden önce değişecek mi?
4. Verified App Link için kullanılabilecek sahip olunan bir HTTPS domain var mı?
5. Araç fotoğrafında tek primary fotoğraf mı, çoklu galeri mi hedefleniyor?
6. Production Firestore/Storage rules hangi repository ve deployment pipeline tarafından yönetiliyor?
7. Bellek’te gelecekte kişi merge planı var mı? Varsa redirect sözleşmesi 6A’ya çekilmelidir.
8. Bellek içindeki legacy `plaka/plakaUlkesi` alanları Drive araçlarına bir defalık kullanıcı onaylı öneri olarak mı taşınacak, yoksa tamamen legacy kalacak mı?

## 28. Genel değerlendirme

Entegrasyon teknik olarak mümkündür; temel avantajlar aynı Firebase project/UID alanı, stabil UUID kişi kimliği ve TopluTaşıma’da zaten var olan UID kapsamlı person profile cache’idir. Ancak mevcut iki uygulamanın doğrudan aynı vehicle document’i düzenlemesi güvenli değildir. Kritik ön koşullar canonical person ID invariant’ı, assignment-only sürümlü sözleşme, eski sürüm uyumluluğu, UID-scope testleri ve full document overwrite riskinin giderilmesidir.

Sprint 6 tek sprint olmamalıdır. **Sprint 6A ilişki/sync/deep-link çekirdeği**, **Sprint 6B fotoğraf ve ayrıntılı profil** olarak bölünmelidir. Driversnote import daha sonraki sprintte kalmalıdır.

Analiz kabul kriterlerinin koddan doğrulanabilen bölümü karşılanmıştır. Tek doğrulama açığı cihaz test paketindeki iki eski Room migration asset eksikliğidir; bu, kaynak değiştirmeme kuralı nedeniyle yalnız raporlanmıştır.
