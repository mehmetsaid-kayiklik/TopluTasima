package com.example.toplutasima.usecase

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.S
import com.example.toplutasima.viewmodel.DayGroup
import com.example.toplutasima.viewmodel.RecordRowUiModel
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class ExportFormat { CSV, JSON, PDF }

object ExportUseCase {

    // ── CSV ─────────────────────────────────────────────────────────────────

    fun generateCsv(dayGroups: List<DayGroup>, lang: AppLanguage): String {
        val sb = StringBuilder()
        // UTF-8 BOM for Excel compatibility
        sb.append("\uFEFF")

        // Header — using existing S.col*() strings
        val headers = listOf(
            S.colDate(lang), S.colDay(lang), S.colType(lang),
            S.colLine(lang), S.colDirection(lang), S.colBoardingStop(lang),
            S.colPlannedDep(lang), S.colActualDep(lang), S.colDelay(lang),
            S.colAlightingStop(lang), S.colPlannedArr(lang), S.colActualArr(lang),
            S.colWeather(lang), S.colSeated(lang), S.colTicketControl(lang),
            S.colPlannedDuration(lang), S.colActualDuration(lang),
            S.colNote(lang)
        )
        sb.appendLine(headers.joinToString(";") { escapeCsvField(it) })

        // Rows
        for (group in dayGroups) {
            for (trip in group.trips) {
                val fields = listOf(
                    trip.date, trip.day, trip.type,
                    trip.line, trip.direction, trip.boardingStop,
                    trip.plannedDep, trip.actualDep, trip.delay,
                    trip.alightingStop, trip.plannedArr, trip.actualArr,
                    trip.weather, trip.seated, trip.ticketControl,
                    trip.plannedDuration, trip.actualDuration,
                    trip.note
                )
                sb.appendLine(fields.joinToString(";") { escapeCsvField(it) })
            }
        }
        return sb.toString()
    }

    private fun escapeCsvField(field: String): String {
        return if (field.contains(";") || field.contains("\"") || field.contains("\n")) {
            "\"${field.replace("\"", "\"\"")}\""
        } else {
            field
        }
    }

    // ── JSON ────────────────────────────────────────────────────────────────

    fun generateJson(dayGroups: List<DayGroup>): String {
        val jsonArray = JSONArray()
        for (group in dayGroups) {
            for (trip in group.trips) {
                val obj = JSONObject()
                obj.put("tarih", trip.date)
                obj.put("gun", trip.day)
                obj.put("tur", trip.type)
                obj.put("hat", trip.line)
                obj.put("yon", trip.direction)
                obj.put("binisDuragi", trip.boardingStop)
                obj.put("planlananBinis", trip.plannedDep)
                obj.put("gercekBinis", trip.actualDep)
                obj.put("gecikme", trip.delay)
                obj.put("inisDuragi", trip.alightingStop)
                obj.put("planlananInis", trip.plannedArr)
                obj.put("gercekInis", trip.actualArr)
                obj.put("havaDurumu", trip.weather)
                obj.put("oturabildimMi", trip.seated)
                obj.put("biletKontrolü", trip.ticketControl)
                obj.put("planlananYolSuresi", trip.plannedDuration)
                obj.put("gercekYolSuresi", trip.actualDuration)
                obj.put("mesafe", trip.distance)
                obj.put("orsMesafe", trip.orsDistance)
                obj.put("rmvMesafe", trip.rmvDistance)
                obj.put("rmvMesafeDurumu", trip.rmvDistanceStatus)
                obj.put("durakSayisi", trip.stopCount)
                obj.put("not", trip.note)
                jsonArray.put(obj)
            }
        }
        return jsonArray.toString(2) // pretty print
    }

    // ── PDF ─────────────────────────────────────────────────────────────────

    fun generatePdf(
        context: Context,
        dayGroups: List<DayGroup>,
        monthTitle: String,
        lang: AppLanguage
    ): File {
        val allTrips = dayGroups.flatMap { it.trips }
        val pdfDocument = PdfDocument()

        val pageWidth = 595  // A4 width in points
        val pageHeight = 842 // A4 height in points
        val margin = 40f
        val lineHeight = 16f

        val titlePaint = Paint().apply {
            textSize = 18f; isFakeBoldText = true; isAntiAlias = true
        }
        val subtitlePaint = Paint().apply {
            textSize = 12f; isAntiAlias = true; color = 0xFF666666.toInt()
        }
        val headerPaint = Paint().apply {
            textSize = 10f; isFakeBoldText = true; isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            textSize = 9f; isAntiAlias = true
        }
        val separatorPaint = Paint().apply {
            color = 0xFFCCCCCC.toInt(); strokeWidth = 0.5f
        }

        var pageNumber = 1
        var currentY = margin
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        // ── Title page header ──
        canvas.drawText(monthTitle, margin, currentY + 18f, titlePaint)
        currentY += 28f
        val summaryText = "${allTrips.size} ${S.tripsCount(lang)}"
        canvas.drawText(summaryText, margin, currentY + 12f, subtitlePaint)
        currentY += 30f
        canvas.drawLine(margin, currentY, pageWidth - margin, currentY, separatorPaint)
        currentY += 10f

        // ── Column positions ──
        val colPositions = listOf(margin, margin + 70, margin + 120, margin + 170,
            margin + 280, margin + 390, margin + 460)

        fun drawTableHeader(c: Canvas, y: Float) {
            val cols = listOf(
                S.colDate(lang), S.colType(lang), S.colLine(lang),
                S.colBoardingStop(lang), S.colAlightingStop(lang),
                S.colPlannedDep(lang), S.colDelay(lang)
            )
            cols.forEachIndexed { i, col ->
                if (i < colPositions.size) {
                    c.drawText(col.take(15), colPositions[i], y, headerPaint)
                }
            }
        }

        fun drawTripRow(c: Canvas, y: Float, trip: RecordRowUiModel) {
            val values = listOf(
                trip.date, trip.type, trip.line,
                trip.boardingStop.take(18), trip.alightingStop.take(18),
                "${trip.plannedDep}-${trip.plannedArr}",
                if (trip.delay.isNotBlank() && trip.delay != "0") "+${trip.delay}dk" else "-"
            )
            values.forEachIndexed { i, v ->
                if (i < colPositions.size) {
                    c.drawText(v, colPositions[i], y, bodyPaint)
                }
            }
        }

        fun newPage(): Canvas {
            pdfDocument.finishPage(page)
            pageNumber++
            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            page = pdfDocument.startPage(pageInfo)
            currentY = margin
            return page.canvas
        }

        drawTableHeader(canvas, currentY + 10f)
        currentY += lineHeight + 4f
        canvas.drawLine(margin, currentY, pageWidth - margin, currentY, separatorPaint)
        currentY += 6f

        // ── Draw trip rows ──
        for (group in dayGroups) {
            for (trip in group.trips) {
                if (currentY + lineHeight > pageHeight - margin) {
                    canvas = newPage()
                    drawTableHeader(canvas, currentY + 10f)
                    currentY += lineHeight + 4f
                    canvas.drawLine(margin, currentY, pageWidth - margin, currentY, separatorPaint)
                    currentY += 6f
                }
                drawTripRow(canvas, currentY + 10f, trip)
                currentY += lineHeight
            }
        }

        pdfDocument.finishPage(page)

        // Save to cache dir
        val file = File(context.cacheDir, "export_${System.currentTimeMillis()}.pdf")
        pdfDocument.writeTo(file.outputStream())
        pdfDocument.close()
        return file
    }

    // ── Helper: write text to cache file ──

    fun writeToCache(context: Context, content: String, extension: String): File {
        val file = File(context.cacheDir, "export_${System.currentTimeMillis()}.$extension")
        file.writeText(content, Charsets.UTF_8)
        return file
    }
}
