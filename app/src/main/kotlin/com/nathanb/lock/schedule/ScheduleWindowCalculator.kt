package com.nathanb.lock.schedule

import com.nathanb.lock.data.model.Profile
import com.nathanb.lock.data.model.ProfileType
import com.nathanb.lock.data.model.Schedule
import com.nathanb.lock.data.model.ScheduleProfileLink
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Pure schedule-window logic. All time math happens in the device's local zone via
 * [ZonedDateTime] (callers pass `ZonedDateTime.now()`), so DST transitions resolve to
 * valid instants automatically.
 *
 * Conventions:
 * - `daysOfWeek` is a bitmask: bit 0 = Monday … bit 6 = Sunday (`1 shl (DayOfWeek.value - 1)`).
 * - An occurrence belongs to the day its START falls on. `endMinuteOfDay <= startMinuteOfDay`
 *   means the window ends the NEXT day (overnight); start == end is a defensive 24h window
 *   (the UI forbids saving it).
 * - Intervals are half-open: `[start, end)`.
 * - A consumed occurrence (cancelled by the user for the day) is identified by
 *   `"scheduleId:epochDay"` where epochDay is the day of the occurrence's start.
 */
object ScheduleWindowCalculator {

    data class Occurrence(
        val scheduleId: Long,
        val start: ZonedDateTime,
        val end: ZonedDateTime,
    ) {
        val consumptionKey: String get() = consumptionKey(scheduleId, start.toLocalDate())
    }

    fun consumptionKey(scheduleId: Long, startDay: LocalDate): String =
        "$scheduleId:${startDay.toEpochDay()}"

    private fun Schedule.isActiveOn(day: LocalDate): Boolean =
        enabled && (daysOfWeek and (1 shl (day.dayOfWeek.value - 1))) != 0

    /** The occurrence of [schedule] starting on [day], or null if [day] isn't selected. */
    private fun occurrenceOn(schedule: Schedule, day: LocalDate, zone: java.time.ZoneId): Occurrence? {
        if (!schedule.isActiveOn(day)) return null
        if (schedule.allDay) {
            // Whole selected day: [00:00, next day 00:00). Consecutive selected days chain
            // into continuous blocking (the midnight boundary re-evaluation keeps the
            // session alive through the overlap-changed path).
            return Occurrence(schedule.id, day.atStartOfDay(zone), day.plusDays(1).atStartOfDay(zone))
        }
        val start = day.atStartOfDay(zone).plusMinutes(schedule.startMinuteOfDay.toLong())
        val endDay = if (schedule.endMinuteOfDay <= schedule.startMinuteOfDay) day.plusDays(1) else day
        val end = endDay.atStartOfDay(zone).plusMinutes(schedule.endMinuteOfDay.toLong())
        return Occurrence(schedule.id, start, end)
    }

    /** Occurrences covering [now]: today's occurrence, plus yesterday's for overnight windows. */
    fun coveringOccurrences(schedules: List<Schedule>, now: ZonedDateTime): List<Occurrence> {
        val today = now.toLocalDate()
        return schedules.flatMap { schedule ->
            listOfNotNull(
                occurrenceOn(schedule, today.minusDays(1), now.zone),
                occurrenceOn(schedule, today, now.zone),
            )
        }.filter { !now.isBefore(it.start) && now.isBefore(it.end) }
    }

    /**
     * The union of blocked packages for [occurrences], excluding consumed ones.
     * Only STANDARD profiles contribute (defensive: no-escape profiles can't be attached).
     */
    fun activePackages(
        occurrences: List<Occurrence>,
        consumedKeys: Set<String>,
        links: List<ScheduleProfileLink>,
        profilesById: Map<Long, Profile>,
    ): Set<String> {
        val activeScheduleIds = occurrences
            .filter { it.consumptionKey !in consumedKeys }
            .map { it.scheduleId }
            .toSet()
        return links.asSequence()
            .filter { it.scheduleId in activeScheduleIds }
            .mapNotNull { profilesById[it.profileId] }
            .filter { ProfileType.fromValue(it.type) == ProfileType.STANDARD }
            .flatMap { it.blockedPackages }
            .toSet()
    }

    /**
     * The next window boundary (start or end) strictly after [now], or null if no enabled
     * schedule has any selected day. Consumption is ignored on purpose: waking up on the
     * boundary of a consumed window is an idempotent no-op, and it keeps arming simple.
     */
    fun nextBoundary(schedules: List<Schedule>, now: ZonedDateTime): ZonedDateTime? {
        val today = now.toLocalDate()
        var best: ZonedDateTime? = null
        // Yesterday (overnight ends) through next week: covers every possible next boundary.
        for (offset in -1L..7L) {
            val day = today.plusDays(offset)
            for (schedule in schedules) {
                val occ = occurrenceOn(schedule, day, now.zone) ?: continue
                for (boundary in listOf(occ.start, occ.end)) {
                    if (boundary.isAfter(now) && (best == null || boundary.isBefore(best))) {
                        best = boundary
                    }
                }
            }
        }
        return best
    }

    /**
     * True when today's occurrence of [schedule] starts strictly after [now]. Used when the
     * user edits a schedule cancelled today: a future start lifts the consumption (intent:
     * "run it"), a past start keeps it (clearing would lock instantly on save).
     */
    fun startsLaterToday(schedule: Schedule, now: ZonedDateTime): Boolean {
        val start = now.toLocalDate()
            .atStartOfDay(now.zone)
            .plusMinutes(schedule.startMinuteOfDay.toLong())
        return start.isAfter(now)
    }

    /** Keeps only consumption keys for occurrences starting today or yesterday. */
    fun pruneConsumed(keys: Set<String>, today: LocalDate): Set<String> {
        val threshold = today.minusDays(1).toEpochDay()
        return keys.filter { key ->
            key.substringAfter(':').toLongOrNull()?.let { it >= threshold } == true
        }.toSet()
    }
}
