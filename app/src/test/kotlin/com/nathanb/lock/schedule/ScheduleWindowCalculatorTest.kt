package com.nathanb.lock.schedule

import com.nathanb.lock.data.model.Profile
import com.nathanb.lock.data.model.Schedule
import com.nathanb.lock.data.model.ScheduleProfileLink
import com.nathanb.lock.schedule.ScheduleWindowCalculator.activePackages
import com.nathanb.lock.schedule.ScheduleWindowCalculator.coveringOccurrences
import com.nathanb.lock.schedule.ScheduleWindowCalculator.nextBoundary
import com.nathanb.lock.schedule.ScheduleWindowCalculator.pruneConsumed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleWindowCalculatorTest {

    private val paris: ZoneId = ZoneId.of("Europe/Paris")

    /** Monday 2026-07-13 is a fixed reference week. */
    private val monday: LocalDate = LocalDate.of(2026, 7, 13)

    private fun at(day: LocalDate, hour: Int, minute: Int = 0): ZonedDateTime =
        ZonedDateTime.of(LocalDateTime.of(day, java.time.LocalTime.of(hour, minute)), paris)

    private fun schedule(
        id: Long = 1L,
        days: Int = 0b1111111,
        start: Int,
        end: Int,
        enabled: Boolean = true,
    ) = Schedule(
        id = id, daysOfWeek = days, startMinuteOfDay = start, endMinuteOfDay = end,
        enabled = enabled, createdAt = 0L,
    )

    private val ALL_DAYS = 0b1111111
    private val MONDAY_ONLY = 0b0000001
    private val SUNDAY_ONLY = 0b1000000

    // --- coveringOccurrences ---

    @Test
    fun `simple window covers inside and not outside`() {
        val s = schedule(start = 9 * 60, end = 17 * 60)

        assertEquals(1, coveringOccurrences(listOf(s), at(monday, 12)).size)
        assertTrue(coveringOccurrences(listOf(s), at(monday, 8, 59)).isEmpty())
        assertTrue(coveringOccurrences(listOf(s), at(monday, 17, 0)).isEmpty()) // end exclusive
    }

    @Test
    fun `start boundary is inclusive`() {
        val s = schedule(start = 9 * 60, end = 17 * 60)
        assertEquals(1, coveringOccurrences(listOf(s), at(monday, 9, 0)).size)
    }

    @Test
    fun `overnight window covers before and after midnight with start-day key`() {
        val s = schedule(start = 22 * 60, end = 6 * 60)

        val at23 = coveringOccurrences(listOf(s), at(monday, 23)).single()
        assertEquals(monday, at23.start.toLocalDate())

        val tuesday5am = coveringOccurrences(listOf(s), at(monday.plusDays(1), 5)).single()
        assertEquals(monday, tuesday5am.start.toLocalDate()) // occurrence belongs to Monday
        assertEquals(ScheduleWindowCalculator.consumptionKey(1L, monday), tuesday5am.consumptionKey)

        assertTrue(coveringOccurrences(listOf(s), at(monday.plusDays(1), 6)).isEmpty()) // end exclusive
    }

    @Test
    fun `overnight window respects day selection of its start day`() {
        // Sunday-only 22:00 -> 06:00: covers Monday 05:00 (started Sunday), not Tuesday 05:00.
        val s = schedule(days = SUNDAY_ONLY, start = 22 * 60, end = 6 * 60)
        val sunday = monday.minusDays(1)

        assertEquals(1, coveringOccurrences(listOf(s), at(sunday, 23)).size)
        assertEquals(1, coveringOccurrences(listOf(s), at(monday, 5)).size)
        assertTrue(coveringOccurrences(listOf(s), at(monday, 23)).isEmpty())
    }

    @Test
    fun `disabled schedule never covers`() {
        val s = schedule(start = 0, end = 24 * 60 - 1, enabled = false)
        assertTrue(coveringOccurrences(listOf(s), at(monday, 12)).isEmpty())
    }

    @Test
    fun `day not selected never covers`() {
        val s = schedule(days = MONDAY_ONLY, start = 9 * 60, end = 17 * 60)
        assertTrue(coveringOccurrences(listOf(s), at(monday.plusDays(1), 12)).isEmpty())
    }

    @Test
    fun `start equals end is a defensive 24h window`() {
        val s = schedule(start = 10 * 60, end = 10 * 60)
        assertEquals(1, coveringOccurrences(listOf(s), at(monday, 12)).size)
        assertEquals(1, coveringOccurrences(listOf(s), at(monday.plusDays(1), 9, 59)).size)
    }

    // --- activePackages ---

    private val profileA = Profile(id = 10, name = "A", blockedPackages = listOf("com.a", "com.shared"))
    private val profileB = Profile(id = 20, name = "B", blockedPackages = listOf("com.b", "com.shared"))
    private val noEscape = Profile(id = 30, name = "NE", blockedPackages = listOf("com.ne"), type = "no_escape")

    @Test
    fun `union of overlapping schedules`() {
        val s1 = schedule(id = 1, start = 9 * 60, end = 17 * 60)
        val s2 = schedule(id = 2, start = 12 * 60, end = 18 * 60)
        val now = at(monday, 13)
        val occurrences = coveringOccurrences(listOf(s1, s2), now)

        val packages = activePackages(
            occurrences,
            consumedKeys = emptySet(),
            links = listOf(ScheduleProfileLink(1, 10), ScheduleProfileLink(2, 20)),
            profilesById = mapOf(10L to profileA, 20L to profileB),
        )

        assertEquals(setOf("com.a", "com.b", "com.shared"), packages)
    }

    @Test
    fun `consumed occurrence contributes nothing`() {
        val s1 = schedule(id = 1, start = 9 * 60, end = 17 * 60)
        val occurrences = coveringOccurrences(listOf(s1), at(monday, 13))

        val packages = activePackages(
            occurrences,
            consumedKeys = setOf(ScheduleWindowCalculator.consumptionKey(1L, monday)),
            links = listOf(ScheduleProfileLink(1, 10)),
            profilesById = mapOf(10L to profileA),
        )

        assertTrue(packages.isEmpty())
    }

    @Test
    fun `no-escape profiles are excluded defensively`() {
        val s1 = schedule(id = 1, start = 9 * 60, end = 17 * 60)
        val occurrences = coveringOccurrences(listOf(s1), at(monday, 13))

        val packages = activePackages(
            occurrences,
            consumedKeys = emptySet(),
            links = listOf(ScheduleProfileLink(1, 10), ScheduleProfileLink(1, 30)),
            profilesById = mapOf(10L to profileA, 30L to noEscape),
        )

        assertEquals(setOf("com.a", "com.shared"), packages)
    }

    @Test
    fun `schedule without profiles is inert`() {
        val s1 = schedule(id = 1, start = 9 * 60, end = 17 * 60)
        val occurrences = coveringOccurrences(listOf(s1), at(monday, 13))

        assertTrue(activePackages(occurrences, emptySet(), emptyList(), emptyMap()).isEmpty())
    }

    // --- nextBoundary ---

    @Test
    fun `next boundary is today's start when before the window`() {
        val s = schedule(start = 9 * 60, end = 17 * 60)
        assertEquals(at(monday, 9), nextBoundary(listOf(s), at(monday, 7)))
    }

    @Test
    fun `next boundary is the end when inside the window`() {
        val s = schedule(start = 9 * 60, end = 17 * 60)
        assertEquals(at(monday, 17), nextBoundary(listOf(s), at(monday, 12)))
    }

    @Test
    fun `next boundary crosses midnight for overnight windows`() {
        val s = schedule(start = 22 * 60, end = 6 * 60)
        assertEquals(at(monday.plusDays(1), 6), nextBoundary(listOf(s), at(monday, 23)))
    }

    @Test
    fun `next boundary wraps to next week`() {
        // Monday-only 9-17, asked on Monday evening -> next Monday 9:00.
        val s = schedule(days = MONDAY_ONLY, start = 9 * 60, end = 17 * 60)
        assertEquals(at(monday.plusDays(7), 9), nextBoundary(listOf(s), at(monday, 18)))
    }

    @Test
    fun `next boundary picks the earliest across schedules`() {
        val s1 = schedule(id = 1, start = 9 * 60, end = 17 * 60)
        val s2 = schedule(id = 2, start = 8 * 60, end = 10 * 60)
        assertEquals(at(monday, 8), nextBoundary(listOf(s1, s2), at(monday, 7)))
    }

    @Test
    fun `no enabled schedule means no boundary`() {
        val s = schedule(start = 9 * 60, end = 17 * 60, enabled = false)
        assertNull(nextBoundary(listOf(s), at(monday, 7)))
        assertNull(nextBoundary(emptyList(), at(monday, 7)))
    }

    @Test
    fun `boundary strictly after now`() {
        val s = schedule(start = 9 * 60, end = 17 * 60)
        assertEquals(at(monday, 17), nextBoundary(listOf(s), at(monday, 9)))
    }

    // --- DST (Europe/Paris) ---

    @Test
    fun `spring forward gap resolves to a valid instant`() {
        // 2026-03-29: clocks jump 02:00 -> 03:00 in Paris. A 02:30 start lands on the shifted instant.
        val dstSunday = LocalDate.of(2026, 3, 29)
        val s = schedule(days = ALL_DAYS, start = 2 * 60 + 30, end = 8 * 60)

        // 02:30 doesn't exist that day; ZonedDateTime resolves it inside hour 3.
        val boundary = nextBoundary(listOf(s), at(dstSunday, 1))!!
        assertEquals(3, boundary.hour)
        // What matters: the instant is valid and the window still covers afterwards.
        assertEquals(1, coveringOccurrences(listOf(s), at(dstSunday, 4)).size)
    }

    @Test
    fun `fall back overlap keeps window coverage consistent`() {
        // 2026-10-25: clocks fall back 03:00 -> 02:00 in Paris.
        val dstSunday = LocalDate.of(2026, 10, 25)
        val s = schedule(days = ALL_DAYS, start = 22 * 60, end = 6 * 60)

        // Saturday 22:00 -> Sunday 06:00 spans the repeated hour; still covers at 04:00.
        assertEquals(1, coveringOccurrences(listOf(s), at(dstSunday, 4)).size)
    }

    // --- pruneConsumed ---

    @Test
    fun `prune keeps today and yesterday only`() {
        val keys = setOf(
            ScheduleWindowCalculator.consumptionKey(1, monday),
            ScheduleWindowCalculator.consumptionKey(2, monday.minusDays(1)),
            ScheduleWindowCalculator.consumptionKey(3, monday.minusDays(2)),
            "garbage",
        )

        assertEquals(
            setOf(
                ScheduleWindowCalculator.consumptionKey(1, monday),
                ScheduleWindowCalculator.consumptionKey(2, monday.minusDays(1)),
            ),
            pruneConsumed(keys, monday),
        )
    }
}
