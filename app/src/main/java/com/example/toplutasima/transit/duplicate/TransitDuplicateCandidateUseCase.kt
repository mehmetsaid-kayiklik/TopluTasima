package com.example.toplutasima.transit.duplicate

import com.example.toplutasima.data.local.entity.TripEntity
import com.example.toplutasima.usecase.TransitRecordCalculations
import com.example.toplutasima.usecase.TransitTimeUtils
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

/** The single duplicate-candidate algorithm shared by health warnings and resolution UI. */
class TransitDuplicateCandidateUseCase(
    private val minimumScore: Double = DEFAULT_MINIMUM_SCORE,
    private val enabled: Boolean = true
) {
    fun findCandidates(
        records: List<TripEntity>,
        excludedRecordIds: Set<String> = emptySet(),
        decisionLookup: TransitDuplicateDecisionLookup? = null
    ): List<TransitDuplicateCandidate> {
        if (!enabled || records.size < 2) return emptyList()
        return records.asSequence()
            .filterNot { it.id in excludedRecordIds }
            .filter { it.id.isNotBlank() && !it.tarih.isNullOrBlank() }
            .groupBy { it.userId to normalized(it.tarih) }
            .values
            .flatMap(::candidatePairsForDay)
            .filterNot { candidate ->
                decisionLookup?.isKeptSeparate(candidate.userId, candidate.decisionFingerprint) == true
            }
            .sortedWith(
                compareByDescending<TransitDuplicateCandidate> { it.similarityScore }
                    .thenBy { it.stablePairId }
            )
    }

    /** Prevents a UI candidate from being applied after either source record has changed. */
    fun matchesCurrentRecords(
        expected: TransitDuplicateCandidate,
        first: TripEntity,
        second: TripEntity
    ): Boolean {
        if (!enabled || expected.userId != first.userId || first.userId != second.userId) return false
        val current = findCandidates(listOf(first, second)).singleOrNull() ?: return false
        return current.stablePairId == expected.stablePairId &&
            current.decisionFingerprint == expected.decisionFingerprint
    }

    private fun candidatePairsForDay(dayRecords: List<TripEntity>): List<TransitDuplicateCandidate> {
        if (dayRecords.size < 2) return emptyList()
        val candidates = linkedMapOf<String, TransitDuplicateCandidate>()

        // Route + line buckets prevent an all-pairs scan for large daily fixtures. A short
        // chronological window is enough because time proximity is required for weak matches.
        dayRecords.groupBy(::routeAndLineBucket).values.forEach { bucket ->
            boundedCandidatePairs(bucket).forEach { candidates[it.stablePairId] = it }
        }

        // A manually entered record can legitimately have no line yet. Compare only a bounded
        // time neighbourhood for routes containing such a record, without making all routes O(nÂ²).
        dayRecords.groupBy(::routeBucket).values
            .filter { route -> route.any { it.hat.isNullOrBlank() } }
            .forEach { bucket ->
                boundedCandidatePairs(bucket).forEach { candidates[it.stablePairId] = it }
            }

        // RMV can occasionally rename a line while retaining the stable journey reference.
        dayRecords.asSequence()
            .filter { !it.journeyRef.isNullOrBlank() }
            .groupBy { normalized(it.journeyRef) }
            .values
            .forEach { bucket ->
                boundedCandidatePairs(bucket).forEach { candidates[it.stablePairId] = it }
            }

        return candidates.values.toList()
    }

    private fun boundedCandidatePairs(records: List<TripEntity>): List<TransitDuplicateCandidate> {
        if (records.size < 2) return emptyList()
        val sorted = records.sortedWith(
            compareBy<TripEntity> { TransitTimeUtils.parseMinutesOrNull(it.planlananBinis) ?: Int.MAX_VALUE }
                .thenBy { it.id }
        )
        val candidates = mutableListOf<TransitDuplicateCandidate>()
        for (firstIndex in 0 until sorted.lastIndex) {
            val lastCandidateIndex = min(sorted.lastIndex, firstIndex + MAX_NEIGHBOURS_PER_RECORD)
            for (secondIndex in firstIndex + 1..lastCandidateIndex) {
                score(sorted[firstIndex], sorted[secondIndex])?.let(candidates::add)
            }
        }
        // Midnight-adjacent records are at opposite ends of the chronological ordering.
        val first = sorted.first()
        val last = sorted.last()
        if (circularTimeGap(first.planlananBinis, last.planlananBinis)
                ?.let { it <= MAX_NEAR_TIME_MINUTES } == true
        ) {
            score(first, last)?.let(candidates::add)
        }
        return candidates.distinctBy { it.stablePairId }
    }

    private fun routeAndLineBucket(record: TripEntity): String = listOf(
        routeBucket(record),
        normalized(record.hat)
    ).joinToString("|")

    private fun routeBucket(record: TripEntity): String = listOf(
        normalized(record.fromStopId ?: record.binisDuragi),
        normalized(record.toStopId ?: record.inisDuragi)
    ).joinToString("|")

    private fun score(first: TripEntity, second: TripEntity): TransitDuplicateCandidate? {
        if (first.userId != second.userId) return null
        val reasons = linkedSetOf(TransitDuplicateReason.SAME_DATE)
        var score = 0.08

        if (same(first.binisDuragi, second.binisDuragi) || same(first.fromStopId, second.fromStopId)) {
            reasons += TransitDuplicateReason.SAME_BOARDING_STOP
            score += 0.16
        }
        if (same(first.inisDuragi, second.inisDuragi) || same(first.toStopId, second.toStopId)) {
            reasons += TransitDuplicateReason.SAME_ALIGHTING_STOP
            score += 0.16
        }
        if (same(first.hat, second.hat)) {
            reasons += TransitDuplicateReason.SAME_LINE
            score += 0.13
        } else if (!first.hat.isNullOrBlank() && !second.hat.isNullOrBlank()) {
            score -= 0.28
        }

        val departureGap = circularTimeGap(first.planlananBinis, second.planlananBinis)
        if (departureGap != null && departureGap <= MAX_NEAR_TIME_MINUTES) {
            reasons += TransitDuplicateReason.NEAR_PLANNED_DEPARTURE
            score += if (departureGap <= 2) 0.15 else 0.09
        }
        if (timePairGap(first.planlananBinis, first.planlananInis, second.planlananBinis, second.planlananInis) <= 4) {
            reasons += TransitDuplicateReason.SIMILAR_PLANNED_TIMES
            score += 0.10
        }
        val actualGap = timePairGap(first.gercekBinis, first.gercekInis, second.gercekBinis, second.gercekInis)
        if (actualGap <= 6) {
            reasons += TransitDuplicateReason.SIMILAR_ACTUAL_TIMES
            score += 0.08
        }
        if (similarDistance(first, second)) {
            reasons += TransitDuplicateReason.SIMILAR_DISTANCE
            score += 0.06
        }
        if (same(first.journeyRef, second.journeyRef)) {
            reasons += TransitDuplicateReason.SAME_RMV_JOURNEY
            score += 0.17
        } else if (!first.journeyRef.isNullOrBlank() && !second.journeyRef.isNullOrBlank()) {
            score -= 0.18
        }
        if (segmentFingerprint(first) == segmentFingerprint(second)) {
            reasons += TransitDuplicateReason.SAME_SEGMENT_FINGERPRINT
            score += 0.12
        }
        if (first.journeyRef.isNullOrBlank() != second.journeyRef.isNullOrBlank()) {
            reasons += TransitDuplicateReason.MANUAL_AND_AUTOMATIC_PAIR
            score += 0.03
        }
        if (completeness(first) != completeness(second)) {
            reasons += TransitDuplicateReason.COMPLEMENTARY_COMPLETENESS
            score += 0.03
        }

        val requiredRouteEvidence = TransitDuplicateReason.SAME_BOARDING_STOP in reasons &&
            TransitDuplicateReason.SAME_ALIGHTING_STOP in reasons
        if (!requiredRouteEvidence || score < minimumScore) return null
        val fingerprint = decisionFingerprint(first, second)
        return TransitDuplicateCandidate(
            firstRecordId = first.id,
            secondRecordId = second.id,
            similarityScore = score.coerceIn(0.0, 1.0),
            reasons = reasons,
            decisionFingerprint = fingerprint,
            userId = first.userId
        )
    }

    private fun similarDistance(first: TripEntity, second: TripEntity): Boolean {
        val firstKm = preferredDistance(first) ?: return false
        val secondKm = preferredDistance(second) ?: return false
        val largest = maxOf(firstKm, secondKm)
        if (largest <= 0.0) return false
        return abs(firstKm - secondKm) <= 0.25 || abs(firstKm - secondKm) / largest <= 0.08
    }

    private fun preferredDistance(record: TripEntity): Double? =
        record.orsMesafeKm?.takeIf { it > 0.0 }
            ?: record.rmvMesafeKm?.takeIf { it > 0.0 }
            ?: TransitRecordCalculations.parseDistanceKm(record.mesafe)?.takeIf { it > 0.0 }

    private fun timePairGap(aStart: String?, aEnd: String?, bStart: String?, bEnd: String?): Int {
        val start = circularTimeGap(aStart, bStart) ?: return Int.MAX_VALUE
        val end = circularTimeGap(aEnd, bEnd) ?: return Int.MAX_VALUE
        return start + end
    }

    private fun circularTimeGap(first: String?, second: String?): Int? {
        val a = TransitTimeUtils.parseMinutesOrNull(first) ?: return null
        val b = TransitTimeUtils.parseMinutesOrNull(second) ?: return null
        val direct = abs(a - b)
        return min(direct, 24 * 60 - direct)
    }

    private fun segmentFingerprint(record: TripEntity): String = listOf(
        normalized(record.tarih), normalized(record.fromStopId ?: record.binisDuragi),
        normalized(record.toStopId ?: record.inisDuragi), normalized(record.hat),
        TransitTimeUtils.stripSeconds(record.planlananBinis.orEmpty())
    ).joinToString("|")

    private fun decisionFingerprint(first: TripEntity, second: TripEntity): String {
        val records = listOf(first, second).sortedBy { it.id }
        val canonical = records.joinToString("||") { record ->
            listOf(
                record.id, segmentFingerprint(record), record.planlananInis, record.gercekBinis,
                record.gercekInis, record.mesafe, record.not, record.journeyRef
            ).joinToString("|") { normalized(it) }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun completeness(record: TripEntity): Int = listOf(
        record.tarih, record.hat, record.binisDuragi, record.inisDuragi,
        record.planlananBinis, record.planlananInis, record.gercekBinis, record.gercekInis,
        record.mesafe, record.not
    ).count { !it.isNullOrBlank() }

    private fun same(first: String?, second: String?): Boolean =
        first?.takeIf { it.isNotBlank() }?.let(::normalized) ==
            second?.takeIf { it.isNotBlank() }?.let(::normalized) && !first.isNullOrBlank()

    private fun normalized(value: Any?): String =
        value?.toString()?.trim()?.lowercase(Locale.ROOT).orEmpty()

    companion object {
        private const val DEFAULT_MINIMUM_SCORE = 0.55
        private const val MAX_NEAR_TIME_MINUTES = 10
        private const val MAX_NEIGHBOURS_PER_RECORD = 4
    }
}
