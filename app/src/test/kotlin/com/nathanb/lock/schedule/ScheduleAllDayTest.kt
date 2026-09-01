package com.nathanb.lock.schedule

import com.nathanb.lock.data.model.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** All-day windows: whole selected days, chaining across consecutive days at midnight. */
class ScheduleAllDayTest {

    private val paris: ZoneId = ZoneId.of("Europe/Paris")
    private val wednesday: LocalDate = LocalDate.of(2026, 9, 2)

    private fun allDay(id: Long, days: Int) = Schedule(
        id = id, daysOfWeek = days, startMinuteOfDay = 540, endMinuteOfDay = 1020,
        createdAt = 0L, allDay = true,
    )

    private fun at(day: LocalDate, time: LocalTime): ZonedDateTime =
        ZonedDateTime.of(day, time, paris)

    @Test
    fun `all-day occurrence covers the whole selected day, ignoring start and end minutes`() {
        val schedule = allDay(1L, 0b1111111)
        for (time in listOf(LocalTime.MIDNIGHT, LocalTime.of(3, 0), LocalTime.of(23, 59))) {
            val covering = ScheduleWindowCalculator.coveringOccurrences(listOf(schedule), at(wednesday, time))
            assertEquals("at $time", 1, covering.size)
        }
    }

    @Test
    fun `all-day occurrence belongs to its own day at midnight`() {
        // Wednesday + Thursday selected: at Thursday 00:00 the covering occurrence is
        // Thursday's (new consumption key), so blocking chains without unlocking.
        val schedule = allDay(1L, 0b0001100) // Wed + Thu
        val covering = ScheduleWindowCalculator.coveringOccurrences(
            listOf(schedule), at(wednesday.plusDays(1), LocalTime.MIDNIGHT),
        )
        assertEquals(1, covering.size)
        assertEquals(wednesday.plusDays(1), covering.single().start.toLocalDate())
    }

    @Test
    fun `day not selected is not covered`() {
        val schedule = allDay(1L, 0b0000100) // Wednesday only
        val covering = ScheduleWindowCalculator.coveringOccurrences(
            listOf(schedule), at(wednesday.plusDays(1), LocalTime.NOON),
        )
        assertTrue(covering.isEmpty())
    }

    @Test
    fun `next boundary of an all-day window is midnight`() {
        val schedule = allDay(1L, 0b0000100) // Wednesday only
        val next = ScheduleWindowCalculator.nextBoundary(listOf(schedule), at(wednesday, LocalTime.NOON))
        assertEquals(at(wednesday.plusDays(1), LocalTime.MIDNIGHT), next)
    }
}
