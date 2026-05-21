package com.example.toplutasima.repository

import com.example.toplutasima.model.SeatingStatus
import com.example.toplutasima.model.Segment
import com.example.toplutasima.model.TicketStatus
import com.example.toplutasima.network.FirestoreService
import com.example.toplutasima.usecase.TransitRecordCalculations
import com.example.toplutasima.usecase.TransitTimeUtils
import java.time.LocalDate

object TripRecordMapper {
    fun buildSegmentData(
        id: String,
        date: String,
        seg: Segment,
        havaDurumu: String,
        oturabildim: Boolean,
        biletKontrolu: Boolean,
        note: String,
        seatmateUuid: String = ""
    ): LinkedHashMap<String, Any?> {
        val data = LinkedHashMap<String, Any?>()
        data["tarih"] = date
        data["gun"] = TransitRecordCalculations.computeGun(date)
        data["tur"] = seg.typeTr
        data["hat"] = seg.line
        data["yon"] = seg.direction
        data["binisDuragi"] = seg.fromStop
        data["planlananBinis"] = TransitTimeUtils.stripSeconds(seg.dep)
        data["gercekBinis"] = ""
        data["gecikme"] = 0
        data["inisDuragi"] = seg.toStop
        data["planlananInis"] = TransitTimeUtils.stripSeconds(seg.arr)
        data["gercekInis"] = ""
        data["gununTipi"] = TransitRecordCalculations.computeGununTipi(date)
        data["havaDurumu"] = havaDurumu
        data["oturabildimMi"] = SeatingStatus.fromBoolean(oturabildim).key
        data["planlananYolSuresi"] = TransitRecordCalculations.computeYolSuresi(seg.dep, seg.arr)
        data["gercekYolSuresi"] = ""
        data["not"] = note
        data["biletKontrolü"] = TicketStatus.fromBoolean(biletKontrolu).key
        data["seatmateUuid"] = seatmateUuid
        val mesafeText = TransitRecordCalculations.formatDistanceKm(seg.distanceKm)
        data["mesafe"] = mesafeText
        data.putAll(TransitRecordCalculations.calculatedDistanceFields(seg.distanceKm, resetRmvDistance = true))
        data[FirestoreService.FIELD_JOURNEY_REF] = seg.journeyRef
        data[FirestoreService.FIELD_FROM_STOP_ID] = seg.fromStopId
        data[FirestoreService.FIELD_TO_STOP_ID] = seg.toStopId
        data["durakSayisi"] = if (seg.stopCount > 0) seg.stopCount.toString() else ""
        data["id"] = id
        data["yearMonth"] = TransitRecordCalculations.computeYearMonth(date)
        data["sortDate"] = TransitRecordCalculations.computeSortDate(date).ifBlank { LocalDate.now().toString() }
        return data
    }
}
