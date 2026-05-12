# Changelog

## 2026-05-12

### Yeni özellikler
- Toplu taşıma bildirimlerindeki `Bindim` ve `İndim` aksiyonları artık doğrudan foreground servis üzerinden işleniyor; aksiyonlar mevcut yolculuk kimliğiyle güvenli şekilde eşleştiriliyor.
- İniş durağı yaklaştığında gösterilen hatırlatma bildirimi titreşimli uyarı verecek şekilde güncellendi.
- Özet ekranındaki durak çifti analizinde uzun güzergah adları tıklanarak genişletilebiliyor; biniş ve iniş durakları ayrıntılı olarak görüntülenebiliyor.

### İyileştirmeler
- Yolculuk zaman güncellemeleri `TripRepository` üzerinden yapılarak uygulamanın mevcut veri katmanı ile uyumlu hale getirildi.
- Bildirim aksiyonları için ağ bağlantısı koşulu kaldırıldı; kullanıcı aksiyonları çevrimdışı durumda da iş kuyruğuna alınabiliyor.
- Akıllı içgörüler bölümü, özet akışı içinde analiz kartlarından sonra gösterilecek şekilde yeniden konumlandırıldı.
