package com.example.toplutasima.ui

/**
 * Centralized translatable strings for the app.
 * All UI-facing text should come from here.
 */
object S {
    private fun m(l: AppLanguage, tr: String, de: String, en: String) = when (l) {
        AppLanguage.TR -> tr; AppLanguage.DE -> de; AppLanguage.EN -> en
    }

    // ── Navigation ──
    fun navRecord(l: AppLanguage) = m(l, "Kayıt", "Aufnahme", "Record")
    fun navSummary(l: AppLanguage) = m(l, "Özet", "Übersicht", "Summary")
    fun navSettings(l: AppLanguage) = m(l, "Ayarlar", "Einstellungen", "Settings")

    // ── RmvLogScreen — Header ──
    fun logHeader(l: AppLanguage) = m(l, "RMV Kayıt", "RMV Aufnahme", "RMV Record")
    fun logSubheader(l: AppLanguage) = m(l, "Yolculuk bilgilerini kaydet", "Reiseinformationen speichern", "Save trip information")
    fun manualLogTitle(l: AppLanguage) = m(l, "Manuel Kayıt", "Manuelle Aufnahme", "Manual Record")
    fun manualLogSubheader(l: AppLanguage) = m(l, "Bilgileri kendiniz girin", "Informationen manuell eingeben", "Enter info manually")
    fun modeAuto(l: AppLanguage) = m(l, "Otomatik", "Automatisch", "Auto")
    fun modeManual(l: AppLanguage) = m(l, "Manuel", "Manuell", "Manual")
    fun directionLabel(l: AppLanguage) = m(l, "Yön", "Richtung", "Direction")

    // ── Stop Selection ──
    fun stopSelection(l: AppLanguage) = m(l, "Durak Seçimi", "Haltestellenauswahl", "Stop Selection")
    fun boardingStop(l: AppLanguage) = m(l, "Biniş durağı", "Einstiegshaltestelle", "Boarding stop")
    fun alightingStop(l: AppLanguage) = m(l, "İniş durağı", "Ausstiegshaltestelle", "Alighting stop")
    fun clear(l: AppLanguage) = m(l, "Temizle", "Löschen", "Clear")
    fun search(l: AppLanguage) = m(l, "Ara", "Suchen", "Search")
    fun selected(l: AppLanguage) = m(l, "Seçildi", "Ausgewählt", "Selected")

    // ── Date & Time ──
    fun dateTime(l: AppLanguage) = m(l, "Tarih & Saat", "Datum & Zeit", "Date & Time")
    fun date(l: AppLanguage) = m(l, "Tarih", "Datum", "Date")
    fun time(l: AppLanguage) = m(l, "Saat", "Zeit", "Time")
    fun fetchTimes(l: AppLanguage) = m(l, "Saatleri Getir", "Zeiten abrufen", "Fetch Times")

    // ── Departures ──
    fun departures(l: AppLanguage) = m(l, "Kalkışlar", "Abfahrten", "Departures")

    // ── Planned Route ──
    fun plannedRoute(l: AppLanguage) = m(l, "Planlanan Güzergah", "Geplante Route", "Planned Route")
    fun editDone(l: AppLanguage) = m(l, "Bitti", "Fertig", "Done")
    fun editEdit(l: AppLanguage) = m(l, "Düzenle", "Bearbeiten", "Edit")
    fun departure(l: AppLanguage) = m(l, "Kalkış", "Abfahrt", "Departure")
    fun arrival(l: AppLanguage) = m(l, "Varış", "Ankunft", "Arrival")
    fun duration(l: AppLanguage) = m(l, "Süre", "Dauer", "Duration")
    fun minutesShort(l: AppLanguage) = m(l, "dk", "Min", "min")
    fun stops(l: AppLanguage) = m(l, "durak", "Haltestellen", "stops")
    fun noRouteYet(l: AppLanguage) = m(l, "Henüz güzergah yok", "Noch keine Route", "No route yet")
    fun transferDirect(l: AppLanguage) = m(l, "Aktarmasız", "Direkt", "Direct")
    fun transferCount(n: Int, l: AppLanguage) = m(l, "$n aktarmalı", "$n Umstieg" + if (n > 1) "e" else "", "$n transfer" + if (n > 1) "s" else "")

    // ── Additional Info ──
    fun additionalInfo(l: AppLanguage) = m(l, "Ek Bilgiler", "Zusatzinfos", "Additional Info")
    fun weatherLabel(l: AppLanguage) = m(l, "Hava", "Wetter", "Weather")
    fun seatedToggle(l: AppLanguage) = m(l, "Oturabildim mi?", "Sitzplatz bekommen?", "Got a seat?")
    fun ticketControl(l: AppLanguage) = m(l, "Bilet Kontrolü", "Fahrkartenkontrolle", "Ticket Control")
    fun noteOptional(l: AppLanguage) = m(l, "Not (opsiyonel)", "Notiz (optional)", "Note (optional)")

    // ── Weather items (internal key → display) ──
    val weatherOptions = listOf(
        "Bilinmiyor" to "",
        "Bilinmiyor / Gece" to "",
        "Güneşli" to "",
        "Parçalı Bulutlu" to "",
        "Bulutlu" to "",
        "Yağmurlu" to "",
        "Karlı" to "",
        "Fırtınalı" to "",
        "Sisli" to "",
        "Rüzgarlı" to ""
    )

    fun weatherName(key: String, l: AppLanguage): String = when (key) {
        "Bilinmiyor" -> m(l, "Bilinmiyor", "Unbekannt", "Unknown")
        "Bilinmiyor / Gece" -> m(l, "Gece", "Nacht", "Night")
        "Güneşli" -> m(l, "Güneşli", "Sonnig", "Sunny")
        "Parçalı Bulutlu" -> m(l, "Parçalı Bulutlu", "Teilweise bewölkt", "Partly Cloudy")
        "Bulutlu" -> m(l, "Bulutlu", "Bewölkt", "Cloudy")
        "Yağmurlu" -> m(l, "Yağmurlu", "Regnerisch", "Rainy")
        "Karlı" -> m(l, "Karlı", "Schnee", "Snowy")
        "Fırtınalı" -> m(l, "Fırtınalı", "Stürmisch", "Stormy")
        "Sisli" -> m(l, "Sisli", "Neblig", "Foggy")
        "Rüzgarlı" -> m(l, "Rüzgarlı", "Windig", "Windy")
        else -> key
    }

    // ── Save / Actions ──
    fun saveToSheets(l: AppLanguage) = m(l, "Kaydet", "Speichern", "Save")
    fun updateRecord(l: AppLanguage) = m(l, "Güncelle", "Aktualisieren", "Update")

    // ── Actual Times ──
    fun actualTimes(l: AppLanguage) = m(l, "Gerçek Saatler", "Tatsächliche Zeiten", "Actual Times")
    fun now(l: AppLanguage) = m(l, "Şu an", "Jetzt", "Now")
    fun boarded(l: AppLanguage) = m(l, "Bindim", "Eingestiegen", "Boarded")
    fun alighted(l: AppLanguage) = m(l, "İndim", "Ausgestiegen", "Alighted")
    fun undoBoarded(l: AppLanguage) = m(l, "Bindim'i geri al", "Einstieg zurücknehmen", "Undo boarded")
    fun undoAlighted(l: AppLanguage) = m(l, "İndim'i geri al", "Ausstieg zurücknehmen", "Undo alighted")

    // ── Status ──
    fun statusLabel(l: AppLanguage) = m(l, "Durum", "Status", "Status")

    // ── All Stops (temporary) ──
    fun allStopsTemp(l: AppLanguage) = m(l, "Tüm Duraklar (Geçici)", "Alle Haltestellen (Temp.)", "All Stops (Temp.)")
    fun lineStops(line: String, l: AppLanguage) = m(l, "$line durağı:", "$line Haltestellen:", "$line stops:")

    // ── Summary Screen ──
    fun allStopsTitle(l: AppLanguage) = m(l, "Duraklar", "Haltestellen", "Stops")
    fun totalTrips(l: AppLanguage) = m(l, "Toplam Sefer", "Gesamtfahrten", "Total Trips")
    fun seated(l: AppLanguage) = m(l, "oturma", "Sitzplatz", "seated")
    fun control(l: AppLanguage) = m(l, "kontrol", "Kontrolle", "control")
    fun vehicleTypes(l: AppLanguage) = m(l, "Araç Türleri", "Fahrzeugtypen", "Vehicle Types")
    fun tripsByDay(l: AppLanguage) = m(l, "Günlere Göre Sefer", "Fahrten nach Wochentag", "Trips by Day")
    fun weekdayWeekendComparison(l: AppLanguage) = m(l, "Hafta İçi vs Hafta Sonu", "Werktage vs Wochenende", "Weekday vs Weekend")
    fun weekday(l: AppLanguage) = m(l, "Hafta İçi", "Werktage", "Weekday")
    fun weekend(l: AppLanguage) = m(l, "Hafta Sonu", "Wochenende", "Weekend")
    fun busier(l: AppLanguage) = m(l, "Daha yoğun", "Stärker genutzt", "Busier")
    fun equalDensity(l: AppLanguage) = m(l, "Dengeli", "Ausgeglichen", "Balanced")
    fun avgDistance(l: AppLanguage) = m(l, "Ort. mesafe", "Ø Entfernung", "Avg. distance")
    fun tripsByLine(l: AppLanguage) = m(l, "Hatlara Göre Sefer", "Fahrten nach Linie", "Trips by Line")
    fun personalRecords(l: AppLanguage) = m(l, "Kişisel Rekorlar", "Persönliche Rekorde", "Personal Records")
    fun recordLongestDay(l: AppLanguage) = m(l, "En Uzun Yolculuk Günü", "Längster Reisetag", "Longest Travel Day")
    fun recordMostTripsDay(l: AppLanguage) = m(l, "En Çok Sefer Yapılan Gün", "Tag mit den meisten Fahrten", "Most Trips Day")
    fun tripsCount(l: AppLanguage) = m(l, "sefer", "Fahrten", "trips")
    fun recordMostDelayed(l: AppLanguage) = m(l, "Tek Seferde En Çok Geciken Hat", "Größte Einzelverspätung", "Largest Single Delay")
    fun recordTotalDelayed(l: AppLanguage) = m(l, "Toplamda En Geciken Hat", "Meiste Gesamtverspätung", "Most Total Delay")
    fun recordFreqLine(l: AppLanguage) = m(l, "En Sık Kullanılan Hat", "Meistgenutzte Linie", "Most Used Line")
    fun recordFreqFrom(l: AppLanguage) = m(l, "En Sık Biniş Durağı", "Häufigste Einstiegshaltestelle", "Most Used Boarding Stop")
    fun punctualityRates(l: AppLanguage) = m(l, "Dakiklik Oranları", "Pünktlichkeitsraten", "Punctuality Rates")
    fun totalPlanned(l: AppLanguage) = m(l, "Planlanan Toplam Süre", "Geplante Gesamtdauer", "Total Planned Duration")
    fun totalActual(l: AppLanguage) = m(l, "Gerçek Toplam Süre", "Tatsächliche Gesamtdauer", "Total Actual Duration")
    fun totalDelay(l: AppLanguage) = m(l, "Toplam Gecikme", "Gesamtverspätung", "Total Delay")
    fun minutes(l: AppLanguage) = m(l, "Dakika", "Minuten", "Minutes")
    fun avgDelay(l: AppLanguage) = m(l, "Ortalama Gecikme", "Durchschnittliche Verspätung", "Average Delay")
    fun refreshing(l: AppLanguage) = m(l, "⟳  Yenileniyor...", "⟳  Wird aktualisiert...", "⟳  Refreshing...")
    fun refreshData(l: AppLanguage) = m(l, "Verileri Yenile", "Daten aktualisieren", "Refresh Data")
    fun weatherStats(l: AppLanguage) = m(l, "Hava Durumu", "Wetter", "Weather")
    fun totalDistance(l: AppLanguage) = m(l, "Toplam Mesafe", "Gesamtentfernung", "Total Distance")
    fun tabTripsRecords(l: AppLanguage) = m(l, "Yolculuk & Rekorlar", "Fahrten & Rekorde", "Trips & Records")
    fun tabDurationDelay(l: AppLanguage) = m(l, "Süre & Gecikme", "Dauer & Verspätung", "Duration & Delay")
    fun smartInsights(l: AppLanguage) = m(l, "Akıllı Özet", "Kurzübersicht", "Smart Insights")
    fun reportCards(l: AppLanguage) = m(l, "Rapor Kartları", "Berichtskarten", "Report Cards")
    fun monthlyTrendTitle(l: AppLanguage) = m(l, "Aylık Sefer Trendi", "Monatlicher Fahrtentrend", "Monthly Trip Trend")
    fun monthlyTrendBusiest(l: AppLanguage) = m(l, "En Yoğun Ay", "Aktivster Monat", "Busiest Month")
    fun monthlyReport(l: AppLanguage) = m(l, "Aylık Rapor", "Monatsbericht", "Monthly Report")
    fun weeklyReport(l: AppLanguage) = m(l, "Haftalık Rapor", "Wochenbericht", "Weekly Report")
    fun reportTopLine(l: AppLanguage) = m(l, "En Çok Hat", "Top-Linie", "Top Line")
    fun reportBusiestDay(l: AppLanguage) = m(l, "En Yoğun Gün", "Stärkster Tag", "Busiest Day")
    fun reportWeekTrips(l: AppLanguage) = m(l, "Haftalık Sefer", "Wochenfahrten", "Weekly Trips")
    fun reportActiveDays(l: AppLanguage) = m(l, "Aktif Gün", "Aktive Tage", "Active Days")
    fun reportDayNumber(day: Int, l: AppLanguage) = m(l, "$day. gün", "$day. Tag", "Day $day")
    fun reportWeekRange(startDay: Int, endDay: Int, l: AppLanguage) = m(l, "$startDay-$endDay. gün", "$startDay.-$endDay. Tag", "Days $startDay-$endDay")
    fun insightWeakLine(l: AppLanguage) = m(l, "En riskli hat", "Riskanteste Linie", "Riskiest line")
    fun insightBusySlot(l: AppLanguage) = m(l, "En yoğun saat", "Häufigster Zeitraum", "Busiest slot")
    fun insightSlowRoute(l: AppLanguage) = m(l, "En geciken rota", "Verspätungsreichste Route", "Most delayed route")
    fun timeSlotAnalysis(l: AppLanguage) = m(l, "Saat Dilimi Analizi", "Analyse nach Tageszeit", "Time Slot Analysis")
    fun routePairAnalysis(l: AppLanguage) = m(l, "Durak Çifti Analizi", "Haltestellenpaar-Analyse", "Stop Pair Analysis")
    fun lineReliability(l: AppLanguage) = m(l, "Hat Güvenilirliği", "Linienzuverlässigkeit", "Line Reliability")
    fun lineDelayByTime(l: AppLanguage) = m(l, "Saat bazlı gecikme", "Verspätung nach Tageszeit", "Delay by time")
    fun lineMostDelayedDays(l: AppLanguage) = m(l, "En çok geciken günler", "Tage mit den meisten Verspätungen", "Most delayed days")
    fun lineNoDelayedDays(l: AppLanguage) = m(l, "Geciken gün yok", "Keine verspäteten Tage", "No delayed days")
    fun delayDistribution(l: AppLanguage) = m(l, "Gecikme Dağılımı", "Verspätungsverteilung", "Delay Distribution")
    fun recordShortestTrip(l: AppLanguage) = m(l, "En Kısa Yolculuk", "Kürzeste Fahrt", "Shortest Trip")
    fun recordLongestTrip(l: AppLanguage) = m(l, "En Uzun Yolculuk", "Längste Fahrt", "Longest Trip")
    fun recordLongestDistance(l: AppLanguage) = m(l, "En Uzun Mesafe", "Längste Strecke", "Longest Distance")
    fun recordFreqTo(l: AppLanguage) = m(l, "En Sık İniş Durağı", "Häufigste Ausstiegshaltestelle", "Most Used Alighting Stop")
    fun avgShort(l: AppLanguage) = m(l, "Ort.", "Ø", "Avg.")
    fun maxShort(l: AppLanguage) = m(l, "Maks.", "Max.", "Max")
    fun punctualShort(l: AppLanguage) = m(l, "Dakik", "Pünktlich", "On time")
    fun tripsShort(l: AppLanguage) = m(l, "sefer", "Fahrten", "trips")
    fun timeSlotName(key: String, l: AppLanguage): String = when (key) {
        "morning" -> m(l, "Sabah", "Morgen", "Morning")
        "noon" -> m(l, "Öğlen", "Mittag", "Noon")
        "evening" -> m(l, "Akşam", "Abend", "Evening")
        "night" -> m(l, "Gece", "Nacht", "Night")
        else -> key
    }
    fun delayBucketName(key: String, l: AppLanguage): String = when (key) {
        "early" -> m(l, "Erken", "Früh", "Early")
        "zero" -> m(l, "0 dk", "0 Min.", "0 min")
        "low" -> m(l, "1-5 dk", "1-5 Min.", "1-5 min")
        "medium" -> m(l, "6-10 dk", "6-10 Min.", "6-10 min")
        "high" -> m(l, "10+ dk", "10+ Min.", "10+ min")
        else -> key
    }

    // ── Vehicle type display names ──
    fun vehicleTypeName(key: String, l: AppLanguage): String = when (key) {
        "Otobüs" -> m(l, "Otobüs", "Bus", "Bus")
        "Straßenbahn" -> m(l, "Tramvay", "Straßenbahn", "Tram")
        "Fernzug" -> m(l, "Hızlı Tren", "Fernzug", "High-speed Train")
        else -> key // S-Bahn, U-Bahn, Re/Rb stay the same
    }

    // ── Day names (internal key → display) ──
    fun dayName(key: String, l: AppLanguage): String = when (key) {
        "Pazartesi" -> m(l, "Pazartesi", "Montag", "Monday")
        "Salı" -> m(l, "Salı", "Dienstag", "Tuesday")
        "Çarşamba" -> m(l, "Çarşamba", "Mittwoch", "Wednesday")
        "Perşembe" -> m(l, "Perşembe", "Donnerstag", "Thursday")
        "Cuma" -> m(l, "Cuma", "Freitag", "Friday")
        "Cumartesi" -> m(l, "Cumartesi", "Samstag", "Saturday")
        "Pazar" -> m(l, "Pazar", "Sonntag", "Sunday")
        else -> key
    }

    // ── Sheet name translation ──
    fun sheetAll(l: AppLanguage) = m(l, "Tümü", "Alle", "All")

    // ── Month names (internal Turkish key → localized display) ──
    fun monthName(key: String, l: AppLanguage): String = when (key) {
        "Ocak" -> m(l, "Ocak", "Januar", "January")
        "Şubat" -> m(l, "Şubat", "Februar", "February")
        "Mart" -> m(l, "Mart", "März", "March")
        "Nisan" -> m(l, "Nisan", "April", "April")
        "Mayıs" -> m(l, "Mayıs", "Mai", "May")
        "Haziran" -> m(l, "Haziran", "Juni", "June")
        "Temmuz" -> m(l, "Temmuz", "Juli", "July")
        "Ağustos" -> m(l, "Ağustos", "August", "August")
        "Eylül" -> m(l, "Eylül", "September", "September")
        "Ekim" -> m(l, "Ekim", "Oktober", "October")
        "Kasım" -> m(l, "Kasım", "November", "November")
        "Aralık" -> m(l, "Aralık", "Dezember", "December")
        else -> key
    }

    // ── Settings Screen ──
    fun settingsTitle(l: AppLanguage) = m(l, "Ayarlar", "Einstellungen", "Settings")
    fun settingsSectionBasic(l: AppLanguage) = m(l, "Temel", "Allgemein", "General")
    fun settingsSectionAdvanced(l: AppLanguage) = m(l, "Gelişmiş", "Erweitert", "Advanced")
    fun themeTitle(l: AppLanguage) = m(l, "Tema", "Design", "Theme")
    fun darkThemeActive(l: AppLanguage) = m(l, "Koyu tema aktif", "Dunkles Design aktiv", "Dark theme active")
    fun lightThemeActive(l: AppLanguage) = m(l, "Açık tema aktif", "Helles Design aktiv", "Light theme active")
    fun colorSettings(l: AppLanguage) = m(l, "Renk Ayarları", "Farbeinstellungen", "Color Settings")
    fun bgColorHex(l: AppLanguage) = m(l, "Arka Plan (HEX)", "Hintergrund (HEX)", "Background (HEX)")
    fun btnColorHex(l: AppLanguage) = m(l, "Buton Rengi (HEX)", "Buttonfarbe (HEX)", "Button Color (HEX)")
    fun saveColors(l: AppLanguage) = m(l, "Renkleri Kaydet", "Farben speichern", "Save Colors")
    fun restartHint(l: AppLanguage) = m(l, "Renk değişikliği için uygulamayı yeniden başlatın", "App neustarten für Farbänderung", "Restart app for color changes")
    fun languageTitle(l: AppLanguage) = m(l, "Dil", "Sprache", "Language")
    fun clearFormButton(l: AppLanguage) = m(l, "Temizle", "Löschen", "Clear")

    // ── ViewModel Status Messages ──
    fun statusReady(l: AppLanguage) = m(l, "Hazır", "Bereit", "Ready")
    fun statusSearchingFrom(l: AppLanguage) = m(l, "Biniş durağı aranıyor...", "Einstiegshaltestelle wird gesucht...", "Searching boarding stop...")
    fun statusFromNoResult(l: AppLanguage) = m(l, "Biniş: sonuç yok", "Einstieg: keine Ergebnisse", "Boarding: no results")
    fun statusFromReady(l: AppLanguage) = m(l, "Biniş: seçenekler hazır", "Einstieg: Optionen bereit", "Boarding: options ready")
    fun statusSearchingTo(l: AppLanguage) = m(l, "İniş durağı aranıyor...", "Ausstiegshaltestelle wird gesucht...", "Searching alighting stop...")
    fun statusToNoResult(l: AppLanguage) = m(l, "İniş: sonuç yok", "Ausstieg: keine Ergebnisse", "Alighting: no results")
    fun statusToReady(l: AppLanguage) = m(l, "İniş: seçenekler hazır", "Ausstieg: Optionen bereit", "Alighting: options ready")
    fun statusFetchingDepartures(l: AppLanguage) = m(l, "Kalkışlar getiriliyor...", "Abfahrten werden abgerufen...", "Fetching departures...")
    fun errorSelectStops(l: AppLanguage) = m(l, "Biniş ve iniş durağı seçmelisin", "Wähle Ein- und Ausstiegshaltestelle", "Select boarding and alighting stops")
    fun statusNoDepartures(l: AppLanguage) = m(l, "Kalkış bulunamadı", "Keine Abfahrten gefunden", "No departures found")
    fun statusDeparturesReady(count: Int, l: AppLanguage) = m(l, "Kalkışlar geldi ($count sefer)", "Abfahrten bereit ($count Fahrten)", "Departures ready ($count trips)")
    fun statusFetchingPlan(l: AppLanguage) = m(l, "RMV'den plan çekiliyor...", "Plan wird von RMV abgerufen...", "Fetching plan from RMV...")
    fun errorSelectFromList(l: AppLanguage) = m(l, "Listeden durak seçmelisin", "Wähle eine Haltestelle aus der Liste", "Select a stop from the list")
    fun statusPlanReady(count: Int, l: AppLanguage) = m(l, "Plan geldi ($count araç)", "Plan bereit ($count Fahrzeuge)", "Plan ready ($count vehicles)")
    fun statusSavingSheets(l: AppLanguage) = m(l, "Kaydediliyor...", "Wird gespeichert...", "Saving...")
    fun errorGetPlanFirst(l: AppLanguage) = m(l, "Önce plan çek", "Zuerst Plan abrufen", "Get plan first")
    fun errorSaveFailed(l: AppLanguage) = m(l, "Kayıt hatası", "Speicherfehler", "Save error")
    fun statusSaved(l: AppLanguage) = m(l, "Kaydedildi", "Gespeichert", "Saved")
    fun statusSaving(l: AppLanguage) = m(l, "Kaydediliyor...", "Wird gespeichert...", "Saving...")
    fun statusBoarded(time: String, l: AppLanguage) = m(l, "Bindim ($time)", "Eingestiegen ($time)", "Boarded ($time)")
    fun statusAlighted(time: String, l: AppLanguage) = m(l, "İndim ($time)", "Ausgestiegen ($time)", "Alighted ($time)")
    fun statusUndoDone(l: AppLanguage) = m(l, "Geri alındı", "Zurückgenommen", "Undone")
    fun errorPrefix(l: AppLanguage) = m(l, "Hata", "Fehler", "Error")
    fun unknownError(l: AppLanguage) = m(l, "Bilinmeyen Hata", "Unbekannter Fehler", "Unknown Error")

    // ── formatMin localized ──
    fun dayUnit(l: AppLanguage) = m(l, "Gün", "Tage", "Days")
    fun hourUnit(l: AppLanguage) = m(l, "Saat", "Std", "Hrs")

    // ── Change Stop Dialog ──
    fun changeStop(l: AppLanguage) = m(l, "Durak Değiştir", "Haltestelle ändern", "Change Stop")
    fun changeBoardingStop(l: AppLanguage) = m(l, "Biniş Durağını Değiştir", "Einstieg ändern", "Change Boarding Stop")
    fun changeAlightingStop(l: AppLanguage) = m(l, "İniş Durağını Değiştir", "Ausstieg ändern", "Change Alighting Stop")
    fun selectNewStop(l: AppLanguage) = m(l, "Yeni durak seçin:", "Neue Haltestelle wählen:", "Select new stop:")
    fun oldValue(l: AppLanguage) = m(l, "Eski", "Alt", "Old")
    fun newValue(l: AppLanguage) = m(l, "Yeni", "Neu", "New")
    fun confirmChange(l: AppLanguage) = m(l, "Onayla", "Bestätigen", "Confirm")
    fun cancelChange(l: AppLanguage) = m(l, "İptal", "Abbrechen", "Cancel")
    fun stopUpdated(l: AppLanguage) = m(l, "Durak güncellendi", "Haltestelle aktualisiert", "Stop updated")
    fun stopUpdateFailed(l: AppLanguage) = m(l, "Durak güncellenemedi", "Aktualisierung fehlgeschlagen", "Stop update failed")
    fun savingStopChange(l: AppLanguage) = m(l, "Durak güncelleniyor...", "Haltestelle wird aktualisiert...", "Updating stop...")
    fun enterNewStopManually(l: AppLanguage) = m(l, "Yeni durak adını girin:", "Neuen Haltestellennamen eingeben:", "Enter new stop name:")
    fun loadingStopList(l: AppLanguage) = m(l, "Durak listesi yükleniyor...", "Haltestellenliste wird geladen...", "Loading stop list...")
    fun errorStopNotFound(l: AppLanguage) = m(l, "Durak bulunamadı", "Haltestelle nicht gefunden", "Stop not found")

    // ── Restore Record ──
    fun restoreRecord(l: AppLanguage) = m(l, "Geri Yükle", "Wiederherstellen", "Restore")

    // ── Records Screen ──
    fun navRecords(l: AppLanguage) = m(l, "Kayıtlar", "Einträge", "Records")
    fun recordsTitle(l: AppLanguage) = m(l, "Kayıtlar", "Einträge", "Records")
    fun recordsGlobalSearchHint(l: AppLanguage) = m(l, "Tüm kayıtlarda ara (hat, durak, not...)", "Alle Einträge durchsuchen (Linie, Haltestelle, Notiz...)", "Search all records (line, stop, note...)")
    fun recordsSearchRun(l: AppLanguage) = m(l, "Ara", "Suchen", "Search")
    fun recordsLastRecordQuick(l: AppLanguage) = m(l, "Son kayıt", "Letzter Eintrag", "Latest record")
    fun recordsGlobalSearchResults(l: AppLanguage) = m(l, "Eşleşen kayıtlar", "Treffer", "Matches")
    fun filterMonth(l: AppLanguage) = m(l, "Ay", "Monat", "Month")
    fun filterType(l: AppLanguage) = m(l, "Tür", "Typ", "Type")
    fun filterAll(l: AppLanguage) = m(l, "Tümü", "Alle", "All")
    fun sortNewest(l: AppLanguage) = m(l, "Yeni → Eski", "Neu → Alt", "New → Old")
    fun sortOldest(l: AppLanguage) = m(l, "Eski → Yeni", "Alt → Neu", "Old → New")
    fun colDate(l: AppLanguage) = m(l, "Tarih", "Datum", "Date")
    fun colDay(l: AppLanguage) = m(l, "Gün", "Tag", "Day")
    fun colType(l: AppLanguage) = m(l, "Tür", "Typ", "Type")
    fun colLine(l: AppLanguage) = m(l, "Hat", "Linie", "Line")
    fun colDirection(l: AppLanguage) = m(l, "Yön", "Richtung", "Direction")
    fun colBoardingStop(l: AppLanguage) = m(l, "Biniş Durağı", "Einstieg", "Boarding Stop")
    fun colPlannedDep(l: AppLanguage) = m(l, "Plan. Biniş", "Plan. Abf.", "Plan. Dep.")
    fun colActualDep(l: AppLanguage) = m(l, "Gerçek Biniş", "Tats. Abf.", "Act. Dep.")
    fun colDelay(l: AppLanguage) = m(l, "Gecikme", "Versp.", "Delay")
    fun colAlightingStop(l: AppLanguage) = m(l, "İniş Durağı", "Ausstieg", "Alighting Stop")
    fun colPlannedArr(l: AppLanguage) = m(l, "Plan. İniş", "Plan. Ank.", "Plan. Arr.")
    fun colActualArr(l: AppLanguage) = m(l, "Gerçek İniş", "Tats. Ank.", "Act. Arr.")
    fun colDayType(l: AppLanguage) = m(l, "Gün Tipi", "Tagestyp", "Day Type")
    fun colWeather(l: AppLanguage) = m(l, "Hava", "Wetter", "Weather")
    fun colSeated(l: AppLanguage) = m(l, "Oturma", "Sitzplatz", "Seated")
    fun colPlannedDuration(l: AppLanguage) = m(l, "Plan. Süre", "Plan. Dauer", "Plan. Dur.")
    fun colActualDuration(l: AppLanguage) = m(l, "Gerçek Süre", "Tats. Dauer", "Act. Dur.")
    fun colNote(l: AppLanguage) = m(l, "Not", "Notiz", "Note")
    fun colTicketControl(l: AppLanguage) = m(l, "Bilet K.", "Fahrk.", "Ticket")
    fun colDistance(l: AppLanguage) = m(l, "Mesafe", "Entf.", "Dist.")
    fun colStops(l: AppLanguage) = m(l, "Durak", "Halt.", "Stops")
    fun colId(l: AppLanguage) = m(l, "ID", "ID", "ID")
    fun plannedDurationLabel(l: AppLanguage) = m(l, "Planlanan Süre", "Geplante Dauer", "Planned Duration")
    fun actualDurationLabel(l: AppLanguage) = m(l, "Gerçek süre", "Tatsächliche Dauer", "Actual Duration")
    fun editRecord(l: AppLanguage) = m(l, "Kaydı Düzenle", "Eintrag bearbeiten", "Edit Record")
    fun deleteRecord(l: AppLanguage) = m(l, "Sil", "Löschen", "Delete")
    fun deleteConfirm(l: AppLanguage) = m(l, "Bu kaydı silmek istediğinize emin misiniz?", "Möchten Sie diesen Eintrag wirklich löschen?", "Are you sure you want to delete this record?")
    fun save(l: AppLanguage) = m(l, "Kaydet", "Speichern", "Save")
    fun cancel(l: AppLanguage) = m(l, "İptal", "Abbrechen", "Cancel")
    fun noRecords(l: AppLanguage) = m(l, "Kayıt bulunamadı", "Keine Einträge gefunden", "No records found")
    fun recordDeleted(l: AppLanguage) = m(l, "Kayıt silindi", "Eintrag gelöscht", "Record deleted")
    fun recordUpdated(l: AppLanguage) = m(l, "Kayıt güncellendi", "Eintrag aktualisiert", "Record updated")
    fun yes(l: AppLanguage) = m(l, "Evet", "Ja", "Yes")
    fun no(l: AppLanguage) = m(l, "Hayır", "Nein", "No")
    fun happened(l: AppLanguage) = m(l, "Oldu", "Ja", "Yes")
    fun didNotHappen(l: AppLanguage) = m(l, "Olmadı", "Nein", "No")

    // ── Time Migration ──
    fun stripSecondsButton(l: AppLanguage) = m(l, "Saatlerden Saniyeleri Temizle", "Sekunden aus Uhrzeiten entfernen", "Strip Seconds from Times")
    fun stripSecondsConfirmTitle(l: AppLanguage) = m(l, "Saniyeleri Temizle", "Sekunden entfernen", "Strip Seconds")
    fun stripSecondsConfirmText(l: AppLanguage) = m(l, "Tüm kayıtlardaki saat alanlarından saniyeler kaldırılacak (ör: 07:17:00 → 07:17). Devam etmek istiyor musunuz?", "Sekunden werden aus allen Zeitfeldern entfernt (z.B. 07:17:00 → 07:17). Fortfahren?", "Seconds will be stripped from all time fields (e.g. 07:17:00 → 07:17). Continue?")
    fun stripSecondsRunning(l: AppLanguage) = m(l, "Saniyeler temizleniyor...", "Sekunden werden entfernt...", "Stripping seconds...")
    fun stripSecondsDone(count: Int, l: AppLanguage) = m(l, "$count kayıt güncellendi", "$count Einträge aktualisiert", "$count records updated")
    fun stripSecondsFailed(l: AppLanguage) = m(l, "Hata oluştu", "Fehler aufgetreten", "An error occurred")

    // ── Yol Suresi Migration ──
    fun migrateYolSuresiButton(l: AppLanguage) = m(l, "Eksik Süreleri Hesapla", "Fehlende Dauer berechnen", "Calculate Missing Durations")
    fun migrateYolSuresiConfirmTitle(l: AppLanguage) = m(l, "Süreleri Hesapla", "Dauer berechnen", "Calculate Durations")
    fun migrateYolSuresiConfirmText(l: AppLanguage) = m(l, "Veritabanındaki tüm eski kayıtlar için planlanan ve gerçek yolculuk süreleri tekrar hesaplanacak. Devam etmek istiyor musunuz?", "Für alle alten Einträge in der Datenbank werden die geplante und tatsächliche Fahrzeit neu berechnet. Fortfahren?", "Planned and actual trip durations will be recalculated for all old records in the database. Continue?")
    fun migrateYolSuresiRunning(l: AppLanguage) = m(l, "Süreler hesaplanıyor...", "Dauer wird berechnet...", "Calculating durations...")
    fun migrateYolSuresiDone(count: Int, total: Int, l: AppLanguage) = m(l, "$count/$total kayıt güncellendi", "$count/$total Einträge aktualisiert", "$count/$total records updated")

    // ── Derived Fields Migration ──
    fun migrateDerivedFieldsButton(l: AppLanguage) = m(l, "Turetilmis Alanlari Yeniden Hesapla", "Abgeleitete Felder neu berechnen", "Recalculate Derived Fields")
    fun migrateDerivedFieldsConfirmTitle(l: AppLanguage) = m(l, "Hesaplamalari Yenile", "Berechnungen erneuern", "Refresh Calculations")
    fun migrateDerivedFieldsConfirmText(l: AppLanguage) = m(l, "Tum eski seyahatlerde gun, ay/siralama, gecikme, sure ve mesafe alanlari yeniden hesaplanacak. Devam etmek istiyor musunuz?", "Tag, Monat/Sortierung, Verspaetung, Dauer und Distanz werden fuer alle alten Fahrten neu berechnet. Fortfahren?", "Day, month/sort, delay, duration, and distance fields will be recalculated for all old trips. Continue?")
    fun migrateDerivedFieldsRunning(l: AppLanguage) = m(l, "Hesaplamalar yenileniyor...", "Berechnungen werden erneuert...", "Refreshing calculations...")
    fun migrateDerivedFieldsDone(count: Int, total: Int, l: AppLanguage) = m(l, "$count/$total kayıt güncellendi", "$count/$total Einträge aktualisiert", "$count/$total records updated")

    // ── YearMonth Migration ──
    fun migrateYearMonthButton(l: AppLanguage) = m(l, "Eski Kayıtları Ay Alanıyla Güncelle", "Alte Einträge mit Monatsfeld aktualisieren", "Backfill Month Field on Old Records")
    fun migrateYearMonthConfirmTitle(l: AppLanguage) = m(l, "Ay Alanını Ekle", "Monatsfeld hinzufügen", "Add Month Field")
    fun migrateYearMonthConfirmText(l: AppLanguage) = m(l, "yearMonth alanı olmayan eski kayıtlara 'YYYY-MM' formatında ay bilgisi eklenecek. Bu işlem bir kez yapılması yeterlidir. Devam etmek istiyor musunuz?", "Alte Einträge ohne yearMonth-Feld erhalten das Format 'YYYY-MM'. Dieser Vorgang muss nur einmal durchgeführt werden. Fortfahren?", "Old records missing the yearMonth field will be updated with 'YYYY-MM' format. This only needs to be done once. Continue?")
    fun migrateYearMonthRunning(l: AppLanguage) = m(l, "Ay alanı ekleniyor...", "Monatsfeld wird hinzugefügt...", "Adding month field...")
    fun migrateYearMonthDone(count: Int, total: Int, l: AppLanguage) = m(l, "$count/$total kayıt güncellendi", "$count/$total Einträge aktualisiert", "$count/$total records updated")

    // ── SortDate Migration ──
    fun migrateSortDateButton(l: AppLanguage) = m(l, "Eski Kayıtlara Sıralama Alanı Ekle", "Sortierfeld auf alte Einträge anwenden", "Backfill Sort Date on Old Records")
    fun migrateSortDateConfirmTitle(l: AppLanguage) = m(l, "Sıralama Alanı Ekle", "Sortierfeld hinzufügen", "Add Sort Date Field")
    fun migrateSortDateConfirmText(l: AppLanguage) = m(l, "Eski kayıtlara 'YYYY-MM-DD' formatında sortDate alanı eklenecek. Bu alan sayesinde kayıtlar kronolojik sırada listelenir. Bu işlem bir kez yapılması yeterlidir. Devam?", "Alte Einträge erhalten ein 'YYYY-MM-DD' sortDate-Feld für korrekte chronologische Sortierung. Nur einmal erforderlich. Fortfahren?", "Old records will receive a 'YYYY-MM-DD' sortDate field for correct chronological ordering. Only needs to be done once. Continue?")
    fun migrateSortDateRunning(l: AppLanguage) = m(l, "Sıralama alanı ekleniyor...", "Sortierfeld wird hinzugefügt...", "Adding sort date field...")
    fun migrateSortDateDone(count: Int, total: Int, l: AppLanguage) = m(l, "$count/$total kayıt güncellendi", "$count/$total Einträge aktualisiert", "$count/$total records updated")

    // ── Distance Fields Migration ──
    fun migrateDistanceFieldsButton(l: AppLanguage) = m(l, "Eski Kayıtları ORS/RMV Alanlarıyla Güncelle", "Alte Einträge mit ORS/RMV-Feldern aktualisieren", "Backfill ORS/RMV Fields on Old Records")
    fun migrateDistanceFieldsConfirmTitle(l: AppLanguage) = m(l, "Mesafe Alanlarını Ekle", "Distanzfelder hinzufügen", "Add Distance Fields")
    fun migrateDistanceFieldsConfirmText(l: AppLanguage) = m(l, "Eski kayıtlardaki mevcut mesafe değeri ORS alanlarına kopyalanacak, RMV mesafe alanları da 'bekliyor' olarak hazırlanacak. Devam etmek istiyor musunuz?", "Die vorhandene Distanz alter Einträge wird in ORS-Felder kopiert, RMV-Distanzfelder werden als wartend vorbereitet. Fortfahren?", "Existing distance values on old records will be copied into ORS fields, and RMV distance fields will be prepared as pending. Continue?")
    fun migrateDistanceFieldsRunning(l: AppLanguage) = m(l, "Mesafe alanları hazırlanıyor...", "Distanzfelder werden vorbereitet...", "Preparing distance fields...")
    fun migrateDistanceFieldsDone(count: Int, total: Int, l: AppLanguage) = m(l, "$count/$total kayıt güncellendi", "$count/$total Einträge aktualisiert", "$count/$total records updated")

    // ── Seatmate Uuid Migration ──
    fun migrateSeatmateUuidButton(l: AppLanguage) = m(l, "Eski Kayıtlara Yanıma Oturan Kişi UUID Ekle", "Seatmate-UUID zu alten Einträgen hinzufügen", "Backfill Seatmate UUID on Old Records")
    fun migrateSeatmateUuidConfirmTitle(l: AppLanguage) = m(l, "Seatmate UUID Ekle", "Seatmate-UUID hinzufügen", "Add Seatmate UUID")
    fun migrateSeatmateUuidConfirmText(l: AppLanguage) = m(l, "Yanıma oturan kişi UUID alanı olmayan eski kayıtlara boş alan eklenecek. Devam etmek istiyor musunuz?", "Eine leere Seatmate-UUID wird zu allen alten Einträgen hinzugefügt. Fortfahren?", "An empty seatmate UUID field will be added to all old records. Continue?")
    fun migrateSeatmateUuidRunning(l: AppLanguage) = m(l, "Alanlar güncelleniyor...", "Felder werden aktualisiert...", "Updating fields...")
    fun migrateSeatmateUuidDone(updated: Int, scanned: Int, l: AppLanguage) = m(l, "$scanned kayıt tarandı, $updated güncellendi", "$scanned Einträge gescannt, $updated aktualisiert", "$scanned records scanned, $updated updated")

    // ── Early Departure Delay Migration ──
    fun migrateEarlyDeparturesButton(l: AppLanguage) = m(l, "Erken Biniş Gecikmelerini Düzelt", "Frühzeitige Abfahrtsverspätungen korrigieren", "Fix Early Departure Delays")
    fun migrateEarlyDeparturesConfirmTitle(l: AppLanguage) = m(l, "Erken Biniş Düzeltmesi", "Frühzeitige Abfahrtskorrektur", "Early Departure Fix")
    fun migrateEarlyDeparturesConfirmText(l: AppLanguage) = m(l, "Bu işlem geçmiş kayıtları güncelleyecek. Devam edilsin mi?", "Diese Aktion aktualisiert historische Einträge. Fortfahren?", "This will update historical records. Continue?")
    fun migrateEarlyDeparturesRunning(l: AppLanguage) = m(l, "Erken biniş kayıtları düzeltiliyor...", "Frühzeitige Abfahrten werden korrigiert...", "Fixing early departure records...")
    fun migrateEarlyDeparturesDone(updated: Int, total: Int, l: AppLanguage) = m(l, "$updated/$total kayıt güncellendi", "$updated/$total Einträge aktualisiert", "$updated/$total records updated")

    // ── Favorites ──
    fun favoritesTitle(l: AppLanguage) = m(l, "Favori Duraklar", "Lieblingshaltestellen", "Favorite Stops")
    fun favBoardingStops(l: AppLanguage) = m(l, "Biniş Favorileri", "Einstieg-Favoriten", "Boarding Favorites")
    fun favAlightingStops(l: AppLanguage) = m(l, "İniş Favorileri", "Ausstieg-Favoriten", "Alighting Favorites")
    fun addToFavorites(l: AppLanguage) = m(l, "Favorilere Ekle", "Zu Favoriten", "Add to Favorites")
    fun favLabel(l: AppLanguage) = m(l, "Etiket", "Bezeichnung", "Label")
    fun favLabelHint(l: AppLanguage) = m(l, "Ev, İş, Okul...", "Zuhause, Arbeit, Schule...", "Home, Work, School...")
    fun favUsageType(l: AppLanguage) = m(l, "Kullanım Türü", "Nutzungsart", "Usage Type")
    fun favUsageBoarding(l: AppLanguage) = m(l, "Biniş", "Einstieg", "Boarding")
    fun favUsageAlighting(l: AppLanguage) = m(l, "İniş", "Ausstieg", "Alighting")
    fun favUsageBoth(l: AppLanguage) = m(l, "Her İkisi", "Beides", "Both")
    fun favAdded(l: AppLanguage) = m(l, "Favoriye eklendi", "Zu Favoriten hinzugefügt", "Added to favorites")
    fun favRemoved(l: AppLanguage) = m(l, "Silindi", "Entfernt", "Removed")
    fun favEmpty(l: AppLanguage) = m(l, "Henüz favori durak yok", "Noch keine Lieblingshaltestellen", "No favorite stops yet")
    fun favManage(l: AppLanguage) = m(l, "Favori Yönetimi", "Favoritenverwaltung", "Manage Favorites")
    fun favRename(l: AppLanguage) = m(l, "Yeniden Adlandır", "Umbenennen", "Rename")
    fun favDelete(l: AppLanguage) = m(l, "Sil", "Löschen", "Delete")
    fun favDeleteConfirm(l: AppLanguage) = m(l, "Bu favoriyi silmek istediğinize emin misiniz?", "Diesen Favoriten wirklich löschen?", "Are you sure you want to delete this favorite?")
    fun favEditTitle(l: AppLanguage) = m(l, "Favoriyi Düzenle", "Favorit bearbeiten", "Edit Favorite")

    // ── Nearby Stops ──
    fun nearbyStopsTitle(l: AppLanguage) = m(l, "Yakındaki Duraklar", "Nahegelegene Haltestellen", "Nearby Stops")
    fun nearbyLoading(l: AppLanguage) = m(l, "Konum alınıyor...", "Standort wird ermittelt...", "Getting location...")
    fun nearbyNone(l: AppLanguage) = m(l, "Yakında durak bulunamadı", "Keine Haltestellen in der Nähe", "No stops found nearby")
    fun nearbyRefreshHint(l: AppLanguage) = m(l, "Yakındaki durakları görmek için Yenile'ye bas", "Tippe auf Aktualisieren, um nahegelegene Haltestellen zu laden", "Tap Refresh to load nearby stops")
    fun nearbyPermissionNeeded(l: AppLanguage) = m(l, "Konum izni gerekli", "Standortberechtigung erforderlich", "Location permission required")
    fun nearbyUse(l: AppLanguage) = m(l, "Seç", "Wählen", "Select")
    fun nearbyMeters(dist: Int, l: AppLanguage) = m(l, "${dist}m", "${dist}m", "${dist}m")
    fun nearbyRefresh(l: AppLanguage) = m(l, "Yenile", "Aktualisieren", "Refresh")

    // ── Theme Modes ──
    fun themeModeSystem(l: AppLanguage) = m(l, "Sistem", "System", "System")
    fun themeModeLight(l: AppLanguage) = m(l, "Açık", "Hell", "Light")
    fun themeModeDark(l: AppLanguage) = m(l, "Koyu", "Dunkel", "Dark")

    // ── General ──
    fun add(l: AppLanguage) = m(l, "Ekle", "Hinzufügen", "Add")

    // ── Favorite Restore ──
    fun favRestoreButton(l: AppLanguage) = m(l, "Favorileri Geri Yükle", "Favoriten wiederherstellen", "Restore Favorites")
    fun favRestoreConfirmTitle(l: AppLanguage) = m(l, "Favorileri Geri Yükle", "Favoriten wiederherstellen", "Restore Favorites")
    fun favRestoreConfirmText(l: AppLanguage) = m(l, "Firebase'deki favori duraklar lokale birleştirilecek. Mevcut favorileriniz korunur. Devam etmek istiyor musunuz?", "Favoriten aus Firebase werden mit lokalen zusammengeführt. Ihre bestehenden Favoriten bleiben erhalten. Fortfahren?", "Favorite stops from Firebase will be merged with local ones. Your existing favorites will be preserved. Continue?")
    fun favRestoreRunning(l: AppLanguage) = m(l, "Geri yükleniyor...", "Wird wiederhergestellt...", "Restoring...")
    fun favRestoreDone(count: Int, l: AppLanguage) = m(l, "$count yeni favori eklendi", "$count neue Favoriten hinzugefügt", "$count new favorites added")
    fun favRestoreEmpty(l: AppLanguage) = m(l, "Firebase'de favori bulunamadı", "Keine Favoriten in Firebase gefunden", "No favorites found in Firebase")
    fun favRestoreFailed(l: AppLanguage) = m(l, "Geri yükleme başarısız oldu", "Wiederherstellung fehlgeschlagen", "Restore failed")

    // ── Maintenance Screen ──
    fun maintenanceButton(l: AppLanguage) = m(l, "Veri Bakımı", "Datenwartung", "Data Maintenance")
    fun maintenanceTitle(l: AppLanguage) = m(l, "Veri Bakımı", "Datenwartung", "Data Maintenance")
    fun maintenanceBack(l: AppLanguage) = m(l, "← Geri", "← Zurück", "← Back")

    // ── Record Filters ──
    fun filterTitle(l: AppLanguage) = m(l, "Filtreler", "Filter", "Filters")
    fun filterSearchHint(l: AppLanguage) = m(l, "Hat, durak, yön, not ara...", "Linie, Haltestelle, Richtung, Notiz...", "Search line, stop, direction, note...")
    fun filterVehicleType(l: AppLanguage) = m(l, "Araç Türü", "Fahrzeugtyp", "Vehicle Type")
    fun filterWeather(l: AppLanguage) = m(l, "Hava Durumu", "Wetter", "Weather")
    fun filterSeated(l: AppLanguage) = m(l, "Oturma", "Sitzplatz", "Seated")
    fun filterSeatedYes(l: AppLanguage) = m(l, "Oturdu", "Sitzplatz", "Seated")
    fun filterSeatedNo(l: AppLanguage) = m(l, "Ayakta", "Stehplatz", "Standing")
    fun filterTicket(l: AppLanguage) = m(l, "Bilet Kontrolü", "Fahrkartenkontrolle", "Ticket Control")
    fun filterTicketYes(l: AppLanguage) = m(l, "Kontrol Oldu", "Kontrolliert", "Checked")
    fun filterTicketNo(l: AppLanguage) = m(l, "Kontrol Olmadı", "Nicht kontrolliert", "Not Checked")
    fun filterDelayRange(l: AppLanguage) = m(l, "Gecikme Aralığı", "Verspätungsbereich", "Delay Range")
    fun filterDelayMin(l: AppLanguage) = m(l, "Min (dk)", "Min (Min)", "Min (min)")
    fun filterDelayMax(l: AppLanguage) = m(l, "Max (dk)", "Max (Min)", "Max (min)")
    fun filterStopName(l: AppLanguage) = m(l, "Durak Adı", "Haltestellenname", "Stop Name")
    fun filterStopNameHint(l: AppLanguage) = m(l, "Biniş veya iniş durağı...", "Ein- oder Ausstieg...", "Boarding or alighting stop...")
    fun filterClearAll(l: AppLanguage) = m(l, "Filtreleri Temizle", "Filter zurücksetzen", "Clear Filters")
    fun filterResultCount(count: Int, l: AppLanguage) = m(l, "$count kayıt bulundu", "$count Einträge gefunden", "$count records found")
    fun filterNoResults(l: AppLanguage) = m(l, "Filtrelere uygun kayıt bulunamadı", "Keine passenden Einträge gefunden", "No records match the filters")
    fun filterNoResultsHint(l: AppLanguage) = m(l, "Farklı filtreler deneyin veya filtreleri temizleyin", "Versuche andere Filter oder setze sie zurück", "Try different filters or clear them")
    fun filterShow(l: AppLanguage) = m(l, "Filtrele", "Filtern", "Filter")
    fun filterHide(l: AppLanguage) = m(l, "Gizle", "Ausblenden", "Hide")
    fun filterActiveCount(count: Int, l: AppLanguage) = m(l, "$count aktif filtre", "$count aktive Filter", "$count active filters")

    // ── Data Export ──
    fun exportTitle(l: AppLanguage) = m(l, "Dışa Aktar", "Exportieren", "Export")
    fun exportCsv(l: AppLanguage) = m(l, "CSV (Tablo)", "CSV (Tabelle)", "CSV (Table)")
    fun exportJson(l: AppLanguage) = m(l, "JSON (Geliştirici)", "JSON (Entwickler)", "JSON (Developer)")
    fun exportPdf(l: AppLanguage) = m(l, "PDF (Rapor)", "PDF (Bericht)", "PDF (Report)")
    fun exportChooseFormat(l: AppLanguage) = m(l, "Format Seçin", "Format wählen", "Choose Format")
    fun exportSuccess(l: AppLanguage) = m(l, "Dışa aktarma hazır", "Export bereit", "Export ready")
    fun exportFailed(l: AppLanguage) = m(l, "Dışa aktarma başarısız", "Export fehlgeschlagen", "Export failed")
    fun exportShare(l: AppLanguage) = m(l, "Paylaş", "Teilen", "Share")

    // ── Incomplete Records ──
    fun incompleteTitle(l: AppLanguage) = m(l, "Eksik Kayıtlar", "Unvollständige Einträge", "Incomplete Records")
    fun incompleteCount(count: Int, l: AppLanguage) = m(l, "$count eksik", "$count unvollständig", "$count incomplete")
    fun incompleteDesc(l: AppLanguage) = m(l, "Gerçek biniş veya iniş saati eksik", "Tatsächliche Ein-/Ausstiegszeit fehlt", "Actual boarding or alighting time missing")
    fun incompleteMissingDep(l: AppLanguage) = m(l, "Gerçek biniş eksik", "Tatsächliche Abfahrt fehlt", "Actual departure missing")
    fun incompleteMissingArr(l: AppLanguage) = m(l, "Gerçek iniş eksik", "Tatsächliche Ankunft fehlt", "Actual arrival missing")
    fun incompleteMissingBoth(l: AppLanguage) = m(l, "Her iki saat eksik", "Beide Zeiten fehlen", "Both times missing")
    fun incompleteTapToComplete(l: AppLanguage) = m(l, "Tamamlamak için dokun", "Zum Vervollständigen tippen", "Tap to complete")
    fun incompleteShowAll(l: AppLanguage) = m(l, "Tümünü Göster", "Alle anzeigen", "Show All")
    fun incompleteHide(l: AppLanguage) = m(l, "Gizle", "Ausblenden", "Hide")

    // ── Data Health ──
    fun dataHealthTitle(l: AppLanguage) = m(l, "Veri Sağlığı", "Datengesundheit", "Data Health")
    fun dataHealthRun(l: AppLanguage) = m(l, "Kontrol Et", "Prüfen", "Run Check")
    fun dataHealthRunning(l: AppLanguage) = m(l, "Kontrol ediliyor...", "Wird geprüft...", "Checking...")
    fun dataHealthNoIssues(l: AppLanguage) = m(l, "Sorun bulunamadı!", "Keine Probleme gefunden!", "No issues found!")
    fun dataHealthIssuesFound(count: Int, l: AppLanguage) = m(l, "$count sorun bulundu", "$count Probleme gefunden", "$count issues found")
    fun healthDuplicate(l: AppLanguage) = m(l, "Yinelenen Kayıtlar", "Duplikate", "Duplicates")
    fun healthMissingField(l: AppLanguage) = m(l, "Eksik Alanlar", "Fehlende Felder", "Missing Fields")
    fun healthBadDate(l: AppLanguage) = m(l, "Bozuk Tarih", "Ungültiges Datum", "Bad Date")
    fun healthBadTime(l: AppLanguage) = m(l, "Bozuk Saat", "Ungültige Zeit", "Bad Time")
    fun healthInconsistentDuration(l: AppLanguage) = m(l, "Tutarsız Süre", "Inkonsistente Dauer", "Inconsistent Duration")
    fun healthMissingDerived(l: AppLanguage) = m(l, "Eksik Türetilmiş Alan", "Fehlende abgeleitete Felder", "Missing Derived Fields")
    fun healthAbnormalDelay(l: AppLanguage) = m(l, "Anormal Gecikme", "Abnormale Verspätung", "Abnormal Delay")

    // ── Monthly Comparison ──
    fun tabComparison(l: AppLanguage) = m(l, "Karşılaştırma", "Vergleich", "Comparison")
    fun comparisonTitle(l: AppLanguage) = m(l, "Aylık Karşılaştırma", "Monatsvergleich", "Monthly Comparison")
    fun comparisonPrevMonth(l: AppLanguage) = m(l, "Önceki Ay", "Vorheriger Monat", "Previous Month")
    fun comparisonCurrentMonth(l: AppLanguage) = m(l, "Bu Ay", "Dieser Monat", "This Month")
    fun comparisonNoData(l: AppLanguage) = m(l, "Karşılaştırma için önceki ay verisi yok", "Keine Vormonatsdaten zum Vergleichen", "No previous month data for comparison")
    fun comparisonLoading(l: AppLanguage) = m(l, "Önceki ay yükleniyor...", "Vormonat wird geladen...", "Loading previous month...")

    // ── Heatmap / Streak ──
    fun heatmapTitle(l: AppLanguage) = m(l, "Aktivite Haritası", "Aktivitätskarte", "Activity Map")
    fun heatmapMetricTrips(l: AppLanguage) = m(l, "Sefer", "Fahrten", "Trips")
    fun heatmapMetricDelay(l: AppLanguage) = m(l, "Gecikme", "Versp.", "Delay")
    fun heatmapMetricTicket(l: AppLanguage) = m(l, "Bilet", "Kontrolle", "Ticket")
    fun heatmapMetricSeated(l: AppLanguage) = m(l, "Oturma", "Sitzplatz", "Seated")
    fun heatmapMetricValue(metric: com.example.toplutasima.usecase.HeatmapMetric, value: Int, l: AppLanguage): String = when (metric) {
        com.example.toplutasima.usecase.HeatmapMetric.TRIPS -> m(l, "$value sefer", "$value Fahrten", "$value trips")
        com.example.toplutasima.usecase.HeatmapMetric.AVG_DELAY -> m(l, "$value dk ort. gecikme", "$value Min. Ø Versp.", "$value min avg delay")
        com.example.toplutasima.usecase.HeatmapMetric.TICKET_CONTROL -> m(l, "$value kontrol", "$value Kontrollen", "$value checks")
        com.example.toplutasima.usecase.HeatmapMetric.SEATED -> m(l, "$value oturma", "$value Sitzplätze", "$value seated")
    }
    fun streakCurrent(l: AppLanguage) = m(l, "Mevcut Seri", "Aktuelle Serie", "Current Streak")
    fun streakLongest(l: AppLanguage) = m(l, "En Uzun Seri", "Längste Serie", "Longest Streak")
    fun streakActiveDays(l: AppLanguage) = m(l, "Aktif Gün", "Aktive Tage", "Active Days")
    fun streakDays(l: AppLanguage) = m(l, "gün", "Tage", "days")
    fun streakNone(l: AppLanguage) = m(l, "Henüz seri yok", "Noch keine Serie", "No streak yet")

    // ── Ortak Yardımcı İfadeler ──
    fun delete(l: AppLanguage) = m(l, "Sil", "Löschen", "Delete")
    fun all(l: AppLanguage) = m(l, "Tümü", "Alle", "All")

    // ── Kişisel Araç Yolculuğu ──
    fun modePersonal(l: AppLanguage) = m(l, "Kişisel", "Persönlich", "Personal")
    fun personalTitle(l: AppLanguage) = m(l, "Kişisel Binişler", "Persönliche Fahrten", "Personal Trips")
    fun personalVehicleType(l: AppLanguage) = m(l, "Araç Türü", "Fahrzeugtyp", "Vehicle Type")
    fun personalPlate(l: AppLanguage) = m(l, "Plaka", "Kennzeichen", "Plate")
    fun personalDriver(l: AppLanguage) = m(l, "Sürücü Ben miydim?", "War ich der Fahrer?", "Was I the driver?")
    fun personalPassengerCount(l: AppLanguage) = m(l, "Yolcu Sayısı", "Fahrgastzahl", "Passenger Count")
    fun personalBindim(l: AppLanguage) = m(l, "Bindim", "Eingestiegen", "Boarded")
    fun personalIndim(l: AppLanguage) = m(l, "İndim", "Ausgestiegen", "Alighted")
    fun personalFrom(l: AppLanguage) = m(l, "Kalkış Yeri", "Abfahrtsort", "Departure Place")
    fun personalTo(l: AppLanguage) = m(l, "Varış Yeri", "Ankunftsort", "Arrival Place")
    fun personalDistance(l: AppLanguage) = m(l, "Yol Mesafesi", "Wegstrecke", "Road Distance")
    fun personalDuration(l: AppLanguage) = m(l, "Süre", "Dauer", "Duration")
    fun personalStatusPending(l: AppLanguage) = m(l, "Beklemede", "Wartend", "Pending")
    fun personalStatusActive(l: AppLanguage) = m(l, "Sürüş Devam Ediyor", "Fahrt läuft", "Trip Active")
    fun personalStatusDone(l: AppLanguage) = m(l, "Tamamlandı", "Abgeschlossen", "Completed")
    fun personalLocating(l: AppLanguage) = m(l, "Konum alınıyor...", "Standort wird ermittelt...", "Getting location...")
    fun personalLocationFailed(l: AppLanguage) = m(l, "Konum alınamadı", "Standort nicht verfügbar", "Location unavailable")
    fun personalAdd(l: AppLanguage) = m(l, "Biniş Ekle", "Fahrt hinzufügen", "Add Trip")
    fun personalSummaryTotal(l: AppLanguage) = m(l, "Toplam Biniş", "Gesamte Fahrten", "Total Trips")
    fun personalSummaryTopType(l: AppLanguage) = m(l, "En Sık Araç", "Häufigstes Fahrzeug", "Most Used")
    fun personalSummaryTotalDist(l: AppLanguage) = m(l, "Toplam Mesafe", "Gesamtstrecke", "Total Distance")
    fun personalDeleteConfirm(l: AppLanguage) = m(l, "Bu kaydı silmek istediğinize emin misiniz?", "Soll dieser Eintrag gelöscht werden?", "Delete this trip record?")
    fun personalNotifTitle(l: AppLanguage) = m(l, "Sürüş kaydediliyor", "Fahrt wird aufgezeichnet", "Recording trip")
    fun personalWaypointInterval(l: AppLanguage) = m(l, "Waypoint Aralığı", "Wegpunkt-Intervall", "Waypoint Interval")
    fun personalSettingsCard(l: AppLanguage) = m(l, "Kişisel Biniş Ayarları", "Persönliche Fahrteinstellungen", "Personal Trip Settings")
    fun personalSec(l: AppLanguage) = m(l, "sn", "Sek.", "sec")
    fun personalMin(l: AppLanguage) = m(l, "dk", "Min.", "min")
    fun personalDepartureTime(l: AppLanguage) = m(l, "Kalkış Saati", "Abfahrtszeit", "Departure Time")
    fun personalArrivalTime(l: AppLanguage) = m(l, "Varış Saati", "Ankunftszeit", "Arrival Time")
    fun personalRecordsHint(l: AppLanguage) = m(l, "Geçmiş binişler \u2192 Kayıtlar sekmesindeki Kişisel butonu", "Vergangene Fahrten im Reiter Kişisel", "Past trips in Records tab")

    // Araç türleri
    val personalVehicleOptions = listOf(
        "Otomobil" to "",
        "Motosiklet" to "",
        "Taksi" to "",
        "Uber / Bolt" to "",
        "Servis" to "",
        "Diğer" to ""
    )

    // ── Toplu Taşıma Bildirim Ayarları ──
    fun transitNotifSettingsTitle(l: AppLanguage) = m(l, "Bildirim Ayarları", "Benachrichtigungseinstellungen", "Notification Settings")
    fun transitNotifEnabled(l: AppLanguage) = m(l, "Toplu taşıma bildirimleri", "ÖPNV-Benachrichtigungen", "Transit notifications")
    fun transitNotifEnabledDesc(l: AppLanguage) = m(l, "Kayıt ve yolculuk sırasında bildirim göster", "Benachrichtigung während der Fahrt anzeigen", "Show notification during trip")
    fun transitReminderTitle(l: AppLanguage) = m(l, "İniş Hatırlatması", "Ausstiegserinnerung", "Alighting Reminder")
    fun transitReminderOption(minutes: Int, l: AppLanguage): String = when (minutes) {
        -2 -> m(l, "2 dk önce", "2 Min. vorher", "2 min before")
        -1 -> m(l, "1 dk önce", "1 Min. vorher", "1 min before")
        0 -> m(l, "Saatinde", "Ankunft", "Arrival")
        1 -> m(l, "1 dk sonra", "1 Min. danach", "1 min after")
        2 -> m(l, "2 dk sonra", "2 Min. danach", "2 min after")
        else -> "$minutes ${m(l, "dk", "Min.", "min")}"
    }

    // ── Hatırlatma Türü ──
    fun transitReminderTypeTitle(l: AppLanguage) = m(l, "Hatırlatma Türü", "Erinnerungstyp", "Reminder Type")
    fun transitReminderTypeLocation(l: AppLanguage) = m(l, "Konuma Dayalı", "Standortbasiert", "Location-Based")
    fun transitReminderTypeTime(l: AppLanguage) = m(l, "Saate Dayalı", "Zeitbasiert", "Time-Based")
    fun transitReminderTypeNone(l: AppLanguage) = m(l, "Kapalı", "Aus", "Off")
    fun transitReminderTypeLocationDesc(l: AppLanguage) = m(l, "Durağa ≤100m yaklaştığında bildirim gelir (GPS gerekir)", "Benachrichtigung bei ≤100m Entfernung (GPS erforderlich)", "Alert when ≤100m from stop (GPS required)")
    fun transitReminderTypeTimeDesc(l: AppLanguage) = m(l, "Planlanan iniş saatinde hatırlatma gönderilir", "Erinnerung zur geplanten Ausstiegszeit", "Reminder at planned alighting time")
    fun gpsJourneyMatchTitle(l: AppLanguage) = m(l, "GPS ile sefer eşleştirme", "GPS-Fahrtabgleich", "GPS journey matching")
    fun gpsJourneyMatchDesc(l: AppLanguage) = m(l, "Kayıt ekranında kısa GPS iziyle sefer bulma kartını göster", "Karte zum Abgleich per kurzer GPS-Spur anzeigen", "Show the short GPS trace matching card on the record screen")
    fun autoActualTimeTitle(l: AppLanguage) = m(l, "Otomatik gerçek saatler", "Automatische Ist-Zeiten", "Automatic actual times")
    fun autoActualTimeDesc(l: AppLanguage) = m(l, "GPS ile algılanan biniş/iniş saatlerinin nasıl işleneceğini seç", "Wähle, wie erkannte Ein-/Ausstiegszeiten verarbeitet werden", "Choose how detected boarding/alighting times are handled")
    fun autoActualOff(l: AppLanguage) = m(l, "Kapalı", "Aus", "Off")
    fun autoActualConfirm(l: AppLanguage) = m(l, "Onay iste", "Bestätigen", "Ask")
    fun autoActualAuto(l: AppLanguage) = m(l, "Otomatik", "Automatisch", "Auto")
    fun autoActualModeDesc(l: AppLanguage) = m(l, "Otomatik modda iniş durağına yaklaştığında İndim saati yazılır; biniş tarafı GPS eşleştirme onayıyla ilerler.", "Im Automatikmodus wird beim Annähern an die Ausstiegshaltestelle die Ausstiegszeit gespeichert; Einstieg läuft über GPS-Abgleich.", "Auto mode records alighting near the destination stop; boarding still goes through GPS match confirmation.")

    // ── Sonraki segment bekleniyor ──
    fun transitWaitingNextSegment(l: AppLanguage) = m(l, "Sonraki aktarma bekleniyor...", "Warte auf nächsten Anschluss...", "Waiting for next connection...")

    fun diagnosticsTitle(l: AppLanguage) = m(l, "Tanılama ve Yerel Veri", "Diagnose und lokale Daten", "Diagnostics and Local Data")
    fun offlineQueueStatus(count: Int, l: AppLanguage) = m(l, "Bekleyen offline işlem: $count", "Ausstehende Offline-Aktionen: $count", "Pending offline actions: $count")
    fun offlineSyncNow(l: AppLanguage) = m(l, "Şimdi eşitle", "Jetzt synchronisieren", "Sync now")
    fun offlineQueueClear(l: AppLanguage) = m(l, "Kuyruğu temizle", "Warteschlange leeren", "Clear queue")
    fun stopCacheStatus(count: Int, l: AppLanguage) = m(l, "Durak arama cache: $count sorgu", "Haltestellen-Cache: $count Suchen", "Stop search cache: $count queries")
    fun stopCacheClear(l: AppLanguage) = m(l, "Durak cache temizle", "Haltestellen-Cache leeren", "Clear stop cache")
    fun noCrashReport(l: AppLanguage) = m(l, "Kayıtlı çökme raporu yok", "Kein Absturzbericht gespeichert", "No crash report saved")
    fun lastCrashReport(l: AppLanguage) = m(l, "Son hata raporu", "Letzter Fehlerbericht", "Latest error report")
    fun clearCrashReport(l: AppLanguage) = m(l, "Hata raporunu temizle", "Fehlerbericht löschen", "Clear error report")

    fun personalSummaryAvgDuration(l: AppLanguage) = m(l, "Ort. Süre", "Ø Dauer", "Avg. Duration")
    fun personalSummaryMonthly(l: AppLanguage) = m(l, "Aylık Kırılım", "Monatliche Aufschlüsselung", "Monthly Breakdown")

    // ── Profil Yedeği ──
    fun profileBackupSectionTitle(l: AppLanguage) = m(l, "Profil Yedeği", "Profil-Backup", "Profile Backup")
    fun profileBackupDesc(l: AppLanguage) = m(l, "Profil ve yanıma oturan kişi bilgileri sadece bu cihazda saklanır. Şifreli dosya ile yedekleyebilirsiniz.", "Profil- und Sitzplatzdaten werden nur auf diesem Gerät gespeichert. Sie können sie als verschlüsselte Datei sichern.", "Profile and seatmate details are only stored on this device. You can back them up as an encrypted file.")
    fun profileExportButton(l: AppLanguage) = m(l, "Yedeği Dışa Aktar", "Backup exportieren", "Export Backup")
    fun profileImportButton(l: AppLanguage) = m(l, "Yedeği İçe Aktar", "Backup importieren", "Import Backup")
    fun profileWipeButton(l: AppLanguage) = m(l, "Tüm Profil Verilerini Sil", "Alle Profildaten löschen", "Wipe All Profile Data")
    fun profileWipeConfirmTitle(l: AppLanguage) = m(l, "Profil Verilerini Sil", "Profildaten löschen", "Wipe Profile Data")
    fun profileWipeConfirmText(l: AppLanguage) = m(l, "Tüm yerel profiller ve bunlara bağlı seyahat eşleşmeleri kalıcı olarak silinecektir. Seyahat kayıtlarınız silinmez. Devam etmek istiyor musunuz?", "Alle lokalen Profile und deren Verknüpfungen werden unwiderruflich gelöscht. Ihre Fahrten werden nicht gelöscht. Fortfahren?", "All local profiles and their travel associations will be permanently deleted. Your trip logs will not be deleted. Do you want to proceed?")
    fun profilePasswordTitle(l: AppLanguage) = m(l, "Parola Belirleyin / Girin", "Passwort festlegen / eingeben", "Set / Enter Password")
    fun profilePasswordExportDesc(l: AppLanguage) = m(l, "Yedek dosyasını şifrelemek için en az 4 karakterli bir parola girin. Bu parolayı unutursanız yedek geri yüklenemez!", "Gib ein Passwort mit mindestens 4 Zeichen ein, um das Backup zu verschlüsseln. Wenn du dieses Passwort vergisst, kann das Backup nicht wiederhergestellt werden!", "Enter a password of at least 4 characters to encrypt the backup file. If you forget this password, the backup cannot be restored!")
    fun profilePasswordImportDesc(l: AppLanguage) = m(l, "Yedek dosyasını çözmek için şifreleme parolasını girin.", "Gib das Passwort ein, um die Backup-Datei zu entschlüsseln.", "Enter the password to decrypt the backup file.")
    fun profileImportSuccessTitle(l: AppLanguage) = m(l, "İçe Aktarma Tamamlandı", "Import abgeschlossen", "Import Completed")
    fun profileImportSuccessText(addedP: Int, updatedP: Int, addedL: Int, updatedL: Int, skippedL: Int, l: AppLanguage) = m(
        l,
        "Yedek başarıyla içe aktarıldı:\n• Eklenen Profil: $addedP\n• Güncellenen Profil: $updatedP\n• Eklenen Bağlantı: $addedL\n• Güncellenen Bağlantı: $updatedL\n• Atlanan Yetim Bağlantı: $skippedL",
        "Backup erfolgreich importiert:\n• Profile hinzugefügt: $addedP\n• Profile aktualisiert: $updatedP\n• Verbindungen hinzugefügt: $addedL\n• Verbindungen aktualisiert: $updatedL\n• Übersprungene verwaiste Verbindungen: $skippedL",
        "Backup successfully imported:\n• Profiles added: $addedP\n• Profiles updated: $updatedP\n• Links added: $addedL\n• Links updated: $updatedL\n• Skipped orphan links: $skippedL"
    )
    fun profileErrorTitle(l: AppLanguage) = m(l, "Hata Oluştu", "Fehler aufgetreten", "Error Occurred")
    fun profilePasswordHint(l: AppLanguage) = m(l, "Parola", "Passwort", "Password")

    fun profileManagementTitle(l: AppLanguage) = m(l, "Yolculuk Arkadaşları", "Mitreisende", "Travel Companions")
    fun profileManagementDesc(l: AppLanguage) = m(l, "Yolculuklarda seçebileceğin kişileri oluştur, düzenle veya arşivle.", "Erstelle, bearbeite oder archiviere Personen, die du bei Fahrten auswählen kannst.", "Create, edit or archive people you can select on trips.")
    fun profileManageButton(l: AppLanguage) = m(l, "Yolculuk Arkadaşlarını Yönet", "Mitreisende verwalten", "Manage Travel Companions")

    fun profileAddNewTitle(l: AppLanguage) = m(l, "Yeni Kişi Ekle", "Neue Person hinzufügen", "Add New Person")
    fun profileEditTitle(l: AppLanguage) = m(l, "Kişiyi Düzenle", "Person bearbeiten", "Edit Person")
    fun profileFieldDisplayName(l: AppLanguage) = m(l, "Kayıtta Görünecek Ad", "Angezeigter Name", "Display Name")
    fun profileFieldNameKind(l: AppLanguage) = m(l, "Ad Bilgisi", "Namensangabe", "Name Info")
    fun profileFieldInfoSource(l: AppLanguage) = m(l, "Nasıl Öğrendin?", "Wie erfahren?", "How Learned?")
    fun profileFieldBirthHint(l: AppLanguage) = m(l, "Yaş / Doğum Notu", "Alter / Geburtsnotiz", "Age / Birth Note")
    fun profileFieldMemoryNote(l: AppLanguage) = m(l, "Kişi Notu", "Personennotiz", "Person Note")
    fun profileWarningMemoryNote(l: AppLanguage) = m(l, "Kişi notu yerel olarak şifreli saklanır. Lütfen hassas kişisel verileri kaydederken dikkatli ol.", "Die Personennotiz wird lokal verschlüsselt gespeichert. Bitte sei vorsichtig bei der Eingabe sensibler Daten.", "Person notes are stored encrypted locally. Please be careful when saving sensitive personal data.")

    fun profileNameKindNickname(l: AppLanguage) = m(l, "Takma İsim", "Spitzname", "Nickname")
    fun profileNameKindFirstName(l: AppLanguage) = m(l, "Ad Soyad", "Vor- und Nachname", "Full Name")
    fun profileNameKindUnknown(l: AppLanguage) = m(l, "Belirsiz", "Unklar", "Unclear")

    fun profileInfoSourceAsked(l: AppLanguage) = m(l, "Kendisine Sordum", "Nachgefragt", "Asked Them")
    fun profileInfoSourceObserved(l: AppLanguage) = m(l, "Yolculuktan Hatırlıyorum", "Von der Fahrt gemerkt", "Remembered from Trip")
    fun profileInfoSourceUnknown(l: AppLanguage) = m(l, "Emin Değilim", "Nicht sicher", "Not Sure")

    fun profileAddButton(l: AppLanguage) = m(l, "Yeni Kişi Ekle", "Person hinzufügen", "Add Person")
    fun profileNoProfilesYet(l: AppLanguage) = m(l, "Henüz kişi eklenmemiş.", "Noch keine Personen hinzugefügt.", "No people added yet.")
    fun profileBirthHintPrefix(l: AppLanguage) = m(l, "Yaş / Doğum Notu", "Alter / Geburtsnotiz", "Age / Birth Note")
    fun profileShareWithTransitTitle(l: AppLanguage) = m(l, "Yolculuklarda seçilebilir", "Bei Fahrten auswählbar", "Selectable on Trips")
    fun profileShareWithTransitDesc(l: AppLanguage) = m(l, "Biniş kayıtlarında bu kişiyi yolculuk arkadaşı olarak seçebilirsin.", "Du kannst diese Person in Fahrtprotokollen als Mitreisende auswählen.", "You can select this person as a travel companion in trip records.")

    fun profileSelectionLabel(l: AppLanguage) = m(l, "Yolculuk Arkadaşı", "Reisebegleiter", "Travel Companion")
    fun profileNone(l: AppLanguage) = m(l, "Hiçbiri", "Keine", "None")
    fun profileSeatmateNoteLabel(l: AppLanguage) = m(l, "Yolculuk Arkadaşı Notu", "Reisebegleiter-Notiz", "Travel Companion Note")
}

