// Google Apps Script (Code.gs)

var SPREADSHEET_ID = "1zVVuu34wrcjKVhoSSedTaies9AzttIWZb7RuQNhRNLU"; 

function doPost(e) {
  var lock = LockService.getScriptLock();
  lock.tryLock(10000);
  
  try {
    var output = { ok: false, message: "" };
    var jsonString = e.postData.contents;
    var data = JSON.parse(jsonString);
    
    var ss = SpreadsheetApp.openById(SPREADSHEET_ID);

    if (data.action == "ping") {
      output = { ok: true, ts: new Date().toISOString() };
    }

    // --- SHEET İSİMLERİNİ DÖNDÜR ---
    else if (data.action == "getSheetNames") {
      var sheets = ss.getSheets();
      var names = [];
      for (var i = 0; i < sheets.length; i++) {
        names.push(sheets[i].getName());
      }
      output = { ok: true, sheetNames: names };
    }
    
    // --- KAYDET (SAVE - AKILLI MOD V3) ---
    else if (data.action == "savePlan") {
      // sheetName verilmişse o sheet'e yaz, yoksa ilk sheet'e
      var sheet;
      if (data.sheetName) {
        sheet = ss.getSheetByName(data.sheetName);
        if (!sheet) sheet = ss.getSheets()[0];
      } else {
        sheet = ss.getSheets()[0];
      }

      var r = data.row;
      var inputDate = r["Tarih"];   
      var inputLine = r["Hat"];     
      var inputDep  = r["Planlanan Biniş/Hareket Saati"]; 
      var inputFrom = r["Biniş Durağı"];
      var allData = sheet.getDataRange().getDisplayValues();
      var existingRowIndex = -1;
      
      // Aynı kayıt var mı kontrolü
      for (var i = 1; i < allData.length; i++) {
        var row = allData[i];
        if (row[0] == inputDate && row[3] == inputLine && row[5] == inputFrom && row[6].substring(0,5) == inputDep.substring(0,5)) {
            existingRowIndex = i + 1;
            break;
        }
      }

      if (existingRowIndex > 0) {
        // --- GÜNCELLEME (UPDATE) ---
        sheet.getRange(existingRowIndex, 14).setValue(r["Hava Durumu"]);
        sheet.getRange(existingRowIndex, 15).setValue(r["Oturabildim mi?"]);
        sheet.getRange(existingRowIndex, 18).setValue(r["Not"]);
        sheet.getRange(existingRowIndex, 19).setValue(r["Bilet Kontrolü"]);
        sheet.getRange(existingRowIndex, 20).setValue(r["Mesafe (km)"] || "");
        sheet.getRange(existingRowIndex, 21).setValue(r["Durak Sayısı"] || "");
        sheet.getRange(existingRowIndex, 22).setValue(r["ID"]);
        
        output = { ok: true, message: "Mevcut satır güncellendi" };
      } 
      else {
        // --- YENİ KAYIT (INSERT) ---
        var targetRow = allData.length + 1;
        for (var k = 1; k < allData.length; k++) {
           if (allData[k][0] == "") {
             targetRow = k + 1;
             break;
           }
        }
        
        var updates = [
          {col: 1, val: r["Tarih"]},
          {col: 3, val: r["Tür"]},
          {col: 4, val: r["Hat"]},
          {col: 5, val: r["Yön"]},
          {col: 6, val: r["Biniş Durağı"]},
          {col: 7, val: r["Planlanan Biniş/Hareket Saati"]},
          {col: 10, val: r["İniş Durağı"]},
          {col: 11, val: r["Planlanan İniş"]},
          {col: 14, val: r["Hava Durumu"]},
          {col: 15, val: r["Oturabildim mi?"]},
          {col: 18, val: r["Not"]},
          {col: 19, val: r["Bilet Kontrolü"]},
          {col: 20, val: r["Mesafe (km)"]  || ""},
          {col: 21, val: r["Durak Sayısı"] || ""},
          {col: 22, val: r["ID"]}
        ];

        updates.forEach(function(u) {
          sheet.getRange(targetRow, u.col).setValue(u.val);
        });

        output = { ok: true, message: "Yeni satır eklendi" };
      }
    }
    
    // --- GÜNCELLE (UPDATE ACTUAL) - TÜM SHEET'LERDE ARAR ---
    else if (data.action == "updateActual") {
      var id = data.id;
      var actual = data.actual;
      var sheets = ss.getSheets();
      var found = false;

      for (var s = 0; s < sheets.length && !found; s++) {
        var sheet = sheets[s];
        var allIds = sheet.getRange("V:V").getValues();
        
        for (var i = 0; i < allIds.length; i++) {
          if (String(allIds[i][0]).trim() === String(id).trim()) {
            var realRowIndex = i + 1;
            if (actual["Gerçek Biniş/Kalkış Saati"]) 
               sheet.getRange(realRowIndex, 8).setValue(actual["Gerçek Biniş/Kalkış Saati"]);
            if (actual["Gerçek İniş"]) 
               sheet.getRange(realRowIndex, 12).setValue(actual["Gerçek İniş"]);
            found = true;
            break;
          }
        }
      }
      output = { ok: found };
    }

    // --- TOPLU GÜNCELLEME: EKSİK SATIRLARI GETİR ---
    else if (data.action == "getRowsForBulkUpdate") {
      var sheets = ss.getSheets();
      var rows = [];
      for (var s = 0; s < sheets.length; s++) {
        var sheet = sheets[s];
        var sheetName = sheet.getName();
        var allData = sheet.getDataRange().getDisplayValues();
        for (var i = 1; i < allData.length; i++) {
          var row = allData[i];
          if (row[0] == "" || row[0].toLowerCase() === "tarih") continue;
          var mesafe = row[19]; // column T (0-indexed: 19)
          var durak = row[20];  // column U (0-indexed: 20)
          if ((mesafe == "" || mesafe == null) || (durak == "" || durak == null)) {
            rows.push({
              rowIndex: i + 1,
              sheetName: sheetName,
              hat: row[3],
              tur: row[2],
              yon: row[4],
              binisDuragi: row[5],
              inisDuragi: row[9],
              planlananBinis: row[6],
              tarih: row[0]
            });
          }
        }
      }
      output = { ok: true, rows: rows };
    }

    // --- TOPLU GÜNCELLEME: TEK SATIR GÜNCELLE ---
    else if (data.action == "bulkUpdateRow") {
      var sheet = ss.getSheetByName(data.sheetName);
      if (!sheet) {
        output = { ok: false, message: "Sheet bulunamadı: " + data.sheetName };
      } else {
        var ri = data.rowIndex;
        if (data.mesafe != null && data.mesafe !== "") sheet.getRange(ri, 20).setValue(data.mesafe);
        if (data.durakSayisi != null && data.durakSayisi !== "") sheet.getRange(ri, 21).setValue(data.durakSayisi);
        output = { ok: true, message: "Satır " + ri + " güncellendi" };
      }
    }

    // --- ÖZET VERİLERİNİ ÇEK (TÜM SHEET'LER VEYA TEK SHEET) ---
    else if (data.action == "getSummary") {
      // Always collect all sheet names for the dropdown
      var allSheets = ss.getSheets();
      var sheetNames = [];
      for (var n = 0; n < allSheets.length; n++) {
        sheetNames.push(allSheets[n].getName());
      }

      var sheetsToProcess = [];
      
      if (data.sheetName && data.sheetName !== "Tümü") {
        var targetSheet = ss.getSheetByName(data.sheetName);
        if (targetSheet) {
          sheetsToProcess.push(targetSheet);
        } else {
          output = { ok: false, message: "Sheet bulunamadı: " + data.sheetName };
          return ContentService.createTextOutput(JSON.stringify(output)).setMimeType(ContentService.MimeType.JSON);
        }
      } else {
        sheetsToProcess = allSheets;
      }

      // Birleştirilmiş özet objesi
      var summary = {
        totalTrips: 0,
        seatedCount: 0,
        ticketControlCount: 0,
        types: { "Otobüs":0, "U-Bahn":0, "S-Bahn":0, "Re/Rb":0, "Straßenbahn":0 },
        lines: {}, fromStops: {}, toStops: {},
        days: { "Pazartesi":0, "Salı":0, "Çarşamba":0, "Perşembe":0, "Cuma":0, "Cumartesi":0, "Pazar":0 },
        totalPlanned: 0, totalActual: 0, maxDelay: 0, totalDelay: 0, delayCount: 0
      };
      var weatherCounts = {};
      var totalDistanceKm = 0;

      var typeOnTimeStats = { "Otobüs":{total:0, onTime:0}, "U-Bahn":{total:0, onTime:0}, "S-Bahn":{total:0, onTime:0}, "Re/Rb":{total:0, onTime:0}, "Straßenbahn":{total:0, onTime:0} };
      var dailyTrips = {};
      var dailyDuration = {};
      var lineMaxDelays = {};

      // Her sheet'i işle — getValues() is faster than getDisplayValues()
      for (var sh = 0; sh < sheetsToProcess.length; sh++) {
        var sheet = sheetsToProcess[sh];
        var allData = sheet.getDataRange().getValues();

        for (var i = 1; i < allData.length; i++) {
          var row = allData[i];
          var cell0 = String(row[0]);
          if (cell0 != "" && cell0.toLowerCase() !== "tarih") { 
            summary.totalTrips++;
            if (String(row[14]) == "Evet") summary.seatedCount++;
            if (String(row[18]) == "Oldu") summary.ticketControlCount++;

            var dateStr = cell0;
            // getValues() may return Date objects for date cells
            if (row[0] instanceof Date) {
              var dt = row[0];
              var dd = dt.getDate(); var mm = dt.getMonth() + 1; var yy = dt.getFullYear();
              dateStr = (dd < 10 ? "0" + dd : dd) + "." + (mm < 10 ? "0" + mm : mm) + "." + yy;
            }
            var type = String(row[2]);
            var line = String(row[3]);
            var from = String(row[5]);
            var to = String(row[9]);

            if (summary.types[type] !== undefined) summary.types[type]++;
            if(line) summary.lines[line] = (summary.lines[line] || 0) + 1;
            if(from) summary.fromStops[from] = (summary.fromStops[from] || 0) + 1;
            if(to) summary.toStops[to] = (summary.toStops[to] || 0) + 1;

            // Weather counts
            var weather = String(row[13]);
            if (weather && weather !== "" && weather !== "undefined") {
              weatherCounts[weather] = (weatherCounts[weather] || 0) + 1;
            }

            // Total distance
            var mesafe = String(row[19]);
            if (mesafe && mesafe !== "" && mesafe !== "undefined") {
              var numPart = parseFloat(mesafe.replace(/[^0-9.,]/g, '').replace(',', '.'));
              if (!isNaN(numPart)) totalDistanceKm += numPart;
            }

            var day = String(row[1]);
            if ((!day || day == "undefined") && row[0]) {
               if (row[0] instanceof Date) {
                  var daysMap = ["Pazar", "Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi"];
                  day = daysMap[row[0].getDay()];
               } else {
                  var parts = cell0.split(".");
                  if (parts.length === 3) {
                     var d = new Date(parts[2], parts[1]-1, parts[0]);
                     var daysMap2 = ["Pazar", "Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi"];
                     day = daysMap2[d.getDay()];
                  }
               }
            }
            if(day && summary.days[day] !== undefined) summary.days[day]++;

            var delay = parseFloat(row[8]);
            var plannedMin = parseFloat(row[15]);
            var actualMin = parseFloat(row[16]);

            if (!isNaN(plannedMin)) summary.totalPlanned += plannedMin;
            if (!isNaN(actualMin)) {
              summary.totalActual += actualMin;
              dailyDuration[dateStr] = (dailyDuration[dateStr] || 0) + actualMin;
            }

            dailyTrips[dateStr] = (dailyTrips[dateStr] || 0) + 1;

            if (!isNaN(delay)) {
              summary.totalDelay += delay;
              summary.delayCount++;
              if (delay > summary.maxDelay) summary.maxDelay = delay;
              
              if (typeOnTimeStats[type]) {
                 typeOnTimeStats[type].total++;
                 if (delay <= 3) typeOnTimeStats[type].onTime++;
              }
              
              if (line) {
                 if (!lineMaxDelays[line] || delay > lineMaxDelays[line]) lineMaxDelays[line] = delay;
              }
            }
          }
        }
      }

      function getTop(obj) { var maxKey = "-"; var maxVal = 0; for (var k in obj) { if (obj[k] > maxVal) { maxVal = obj[k]; maxKey = k; } } return maxKey; }

      var recordLongestDay = "-"; var recordLongestDayMin = 0;
      for (var d in dailyDuration) { if (dailyDuration[d] > recordLongestDayMin) { recordLongestDayMin = dailyDuration[d]; recordLongestDay = d; } }
      
      var recordMostTripsDay = "-"; var recordMostTripsCount = 0;
      for (var d in dailyTrips) { if (dailyTrips[d] > recordMostTripsCount) { recordMostTripsCount = dailyTrips[d]; recordMostTripsDay = d; } }
      
      var recordMostDelayedLine = "-"; var recordMostDelayedLineMin = 0;
      for (var l in lineMaxDelays) { if (lineMaxDelays[l] > recordMostDelayedLineMin) { recordMostDelayedLineMin = lineMaxDelays[l]; recordMostDelayedLine = l; } }

      var punctualityRates = {};
      for (var t in typeOnTimeStats) {
         if (typeOnTimeStats[t].total > 0) {
             punctualityRates[t] = Math.round((typeOnTimeStats[t].onTime / typeOnTimeStats[t].total) * 100);
         } else {
             punctualityRates[t] = 0;
         }
      }

      var avgDelay = summary.delayCount > 0 ? (summary.totalDelay / summary.delayCount) : 0;
      
      output = { 
        ok: true, 
        sheetNames: sheetNames,
        summary: {
          totalTrips: summary.totalTrips, seatedCount: summary.seatedCount, ticketControlCount: summary.ticketControlCount,
          types: summary.types, freqLine: getTop(summary.lines), freqFrom: getTop(summary.fromStops), freqTo: getTop(summary.toStops),
          days: summary.days, totalPlanned: Math.round(summary.totalPlanned), totalActual: Math.round(summary.totalActual),
          maxDelay: Math.round(summary.maxDelay), totalDelay: Math.round(summary.totalDelay), avgDelay: avgDelay,
          punctualityRates: punctualityRates, recordLongestDay: recordLongestDay, recordLongestDayMin: Math.round(recordLongestDayMin),
          recordMostTripsDay: recordMostTripsDay, recordMostTripsCount: recordMostTripsCount,
          recordMostDelayedLine: recordMostDelayedLine, recordMostDelayedLineMin: Math.round(recordMostDelayedLineMin),
          weatherCounts: weatherCounts, totalDistanceKm: Math.round(totalDistanceKm * 100) / 100
        }
      };
    }

    // --- TOPLU GÜNCELLEME: TÜM SATIRLARI GETİR (DURAK ADI GÜNCELLEMEİÇİN) ---
    else if (data.action == "getRowsForStopNameUpdate") {
      var sheets = ss.getSheets();
      var rows = [];
      for (var s = 0; s < sheets.length; s++) {
        var sheet = sheets[s];
        var sheetName = sheet.getName();
        var allData = sheet.getDataRange().getDisplayValues();
        for (var i = 1; i < allData.length; i++) {
          var row = allData[i];
          if (row[0] == "" || row[0].toLowerCase() === "tarih") continue;
          rows.push({
            rowIndex: i + 1,
            sheetName: sheetName,
            hat: row[3],
            tur: row[2],
            yon: row[4],
            binisDuragi: row[5],
            inisDuragi: row[9],
            planlananBinis: row[6],
            tarih: row[0]
          });
        }
      }
      output = { ok: true, rows: rows };
    }

    // --- TOPLU GÜNCELLEME: DURAK ADI & YÖN GÜNCELLE ---
    else if (data.action == "bulkUpdateStopNames") {
      var sheet = ss.getSheetByName(data.sheetName);
      if (!sheet) {
        output = { ok: false, message: "Sheet bulunamadı: " + data.sheetName };
      } else {
        var ri = data.rowIndex;
        if (data.binisDuragi != null && data.binisDuragi !== "") sheet.getRange(ri, 6).setValue(data.binisDuragi);
        if (data.inisDuragi != null && data.inisDuragi !== "") sheet.getRange(ri, 10).setValue(data.inisDuragi);
        if (data.yon != null && data.yon !== "") sheet.getRange(ri, 5).setValue(data.yon);
        output = { ok: true, message: "Satır " + ri + " durak adları güncellendi" };
      }
    }

    // --- TÜM SATIRLARI GETİR (MIGRATION İÇİN) ---
    else if (data.action == "getAllRows") {
      var sheets = ss.getSheets();
      var allRows = [];
      for (var s = 0; s < sheets.length; s++) {
        var sheet = sheets[s];
        var allData = sheet.getDataRange().getDisplayValues();
        for (var i = 1; i < allData.length; i++) {
          var row = allData[i];
          if (row[0] == "" || row[0].toLowerCase() === "tarih") continue;
          allRows.push({
            tarih: row[0],
            gun: row[1],
            tur: row[2],
            hat: row[3],
            yon: row[4],
            binisDuragi: row[5],
            planlananBinis: row[6],
            gercekBinis: row[7],
            gununTipi: row[8],
            inisDuragi: row[9],
            planlananInis: row[10],
            gercekInis: row[11],
            havaDurumu: row[13],
            oturabildimMi: row[14],
            planlananYolSuresi: row[15],
            gercekYolSuresi: row[16],
            not: row[17],
            "biletKontrolü": row[18],
            mesafe: row[19],
            durakSayisi: row[20],
            id: row[21]
          });
        }
      }
      output = { ok: true, rows: allRows };
    }

    // --- DURAK DEĞİŞTİR: TEK SATIR DURAK/SAAT GÜNCELLE (ID İLE) ---
    else if (data.action == "updateStops") {
      var id = data.id;
      var sheets = ss.getSheets();
      var found = false;

      for (var s = 0; s < sheets.length && !found; s++) {
        var sheet = sheets[s];
        var allIds = sheet.getRange("V:V").getValues();
        
        for (var i = 0; i < allIds.length; i++) {
          if (String(allIds[i][0]).trim() === String(id).trim()) {
            var realRowIndex = i + 1;
            if (data.binisDuragi != null && data.binisDuragi !== "") 
               sheet.getRange(realRowIndex, 6).setValue(data.binisDuragi);
            if (data.binisTime != null && data.binisTime !== "") 
               sheet.getRange(realRowIndex, 7).setValue(data.binisTime);
            if (data.inisDuragi != null && data.inisDuragi !== "") 
               sheet.getRange(realRowIndex, 10).setValue(data.inisDuragi);
            if (data.inisTime != null && data.inisTime !== "") 
               sheet.getRange(realRowIndex, 11).setValue(data.inisTime);
            found = true;
            break;
          }
        }
      }
      output = { ok: found, message: found ? "Durak güncellendi" : "ID bulunamadı" };
    }

    return ContentService.createTextOutput(JSON.stringify(output)).setMimeType(ContentService.MimeType.JSON);

  } catch (e) {
    return ContentService.createTextOutput(JSON.stringify({ ok: false, error: e.toString() })).setMimeType(ContentService.MimeType.JSON);
  } finally {
    lock.releaseLock();
  }
}
