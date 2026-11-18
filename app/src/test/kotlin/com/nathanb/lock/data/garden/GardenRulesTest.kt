package com.nathanb.lock.data.garden

import com.nathanb.lock.data.model.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class GardenRulesTest {

    private val utc = ZoneId.of("UTC")
    private val hour = 60 * 60 * 1000L
    private val day = 24 * hour

    /** Midnight UTC of an arbitrary day; the garden of these tests is born there. */
    private val born = 20_000 * day

    /** A finished session starting [startHour] hours into day [dayIndex] (0 = birth day). */
    private fun session(dayIndex: Int, startHour: Double, hours: Double): Session {
        val start = born + dayIndex * day + (startHour * hour).toLong()
        return Session(profileId = 1, startTime = start, endTime = start + (hours * hour).toLong())
    }

    @Test
    fun `one drop per full hour`() {
        assertEquals(0, GardenRules.drops(listOf(session(0, 8.0, 0.99)), utc))
        assertEquals(1, GardenRules.drops(listOf(session(0, 8.0, 1.0)), utc))
        assertEquals(2, GardenRules.drops(listOf(session(0, 8.0, 2.5)), utc))
    }

    @Test
    fun `hours of the same day add up`() {
        val twoHalves = listOf(session(0, 8.0, 0.5), session(0, 14.0, 0.5))
        assertEquals(1, GardenRules.drops(twoHalves, utc))
    }

    @Test
    fun `a day gives three drops at most`() {
        val longDay = listOf(session(0, 6.0, 5.0), session(0, 14.0, 4.0))
        assertEquals(3, GardenRules.drops(longDay, utc))
        assertEquals(6, GardenRules.drops(longDay + session(1, 6.0, 5.0), utc))
    }

    @Test
    fun `a session counts on the day it ends, in the given time zone`() {
        // 23:00 to 01:00 UTC: both hours go to the second day.
        val overnight = listOf(session(0, 23.0, 2.0), session(1, 10.0, 2.0))
        assertEquals(3, GardenRules.drops(overnight, utc))
        // Seen from UTC-3 the first session ends on the first day: 2 + 2.
        assertEquals(4, GardenRules.drops(overnight, ZoneId.of("-03:00")))
    }

    @Test
    fun `running sessions earn nothing`() {
        val running = Session(profileId = 1, startTime = born, endTime = null)
        assertEquals(0, GardenRules.drops(listOf(running), utc))
        assertEquals(1, GardenRules.balance(listOf(running), born, 0, utc))
    }

    @Test
    fun `only sessions ended after the birth of the garden are earned`() {
        val sessions = listOf(session(-2, 8.0, 3.0), session(0, 8.0, 2.0))
        assertEquals(2, GardenRules.earnedDrops(sessions, born, utc))
    }

    @Test
    fun `welcome gift is one drop, three with enough history`() {
        assertEquals(1, GardenRules.welcomeDrops(emptyList(), born, utc))
        assertEquals(1, GardenRules.welcomeDrops(listOf(session(-1, 8.0, 2.0)), born, utc))
        assertEquals(3, GardenRules.welcomeDrops(listOf(session(-1, 8.0, 3.0)), born, utc))
        val longHistory = (1..60).map { session(-it, 8.0, 5.0) }
        assertEquals(3, GardenRules.welcomeDrops(longHistory, born, utc))
        // Sessions of the garden era do not raise the gift.
        assertEquals(1, GardenRules.welcomeDrops(listOf(session(0, 8.0, 5.0)), born, utc))
    }

    @Test
    fun `balance is gift plus earned minus spent, never negative`() {
        val sessions = listOf(session(-1, 8.0, 3.0), session(0, 8.0, 2.0), session(1, 8.0, 1.0))
        assertEquals(6, GardenRules.balance(sessions, born, 0, utc))
        assertEquals(1, GardenRules.balance(sessions, born, 5, utc))
        assertEquals(0, GardenRules.balance(sessions, born, 40, utc))
    }

    @Test
    fun `returning user has a first session at least 30 days old`() {
        assertFalse(GardenRules.isReturningUser(emptyList(), born))
        assertFalse(GardenRules.isReturningUser(listOf(session(-29, 8.0, 1.0)), born))
        assertTrue(GardenRules.isReturningUser(listOf(session(-31, 8.0, 1.0), session(-1, 8.0, 1.0)), born))
    }

    @Test
    fun `terrain grows at 15, 90 and 180 days`() {
        fun size(days: Int, returning: Boolean = false) =
            GardenRules.terrainSize(born, born + days * day, returning)

        assertEquals(1, size(0))
        assertEquals(1, size(14))
        assertEquals(2, size(15))
        assertEquals(2, size(89))
        assertEquals(3, size(90))
        assertEquals(3, size(179))
        assertEquals(4, size(180))
        assertEquals(4, size(2000))
        // A clock set back never shrinks below the starting terrain.
        assertEquals(1, GardenRules.terrainSize(born, born - 5 * day, false))
    }

    @Test
    fun `returning user is given the 2x2 terrain, later sizes come at the same age`() {
        assertEquals(2, GardenRules.terrainSize(born, born, true))
        assertEquals(2, GardenRules.terrainSize(born, born + 89 * day, true))
        assertEquals(3, GardenRules.terrainSize(born, born + 90 * day, true))
        assertEquals(4, GardenRules.terrainSize(born, born + 180 * day, true))
        // The 2x2 is already there: the next size announced is the 3x3, at day 90.
        assertEquals(90, GardenRules.daysUntilNextSize(born, born, true))
        assertEquals(80, GardenRules.daysUntilNextSize(born, born + 10 * day, true))
        assertEquals(90, GardenRules.daysUntilNextSize(born, born + 90 * day, true))
        assertNull(GardenRules.daysUntilNextSize(born, born + 180 * day, true))
    }

    @Test
    fun `days until the next terrain size`() {
        assertEquals(15, GardenRules.daysUntilNextSize(born, born, false))
        assertEquals(1, GardenRules.daysUntilNextSize(born, born + 14 * day, false))
        assertEquals(75, GardenRules.daysUntilNextSize(born, born + 15 * day, false))
        assertNull(GardenRules.daysUntilNextSize(born, born + 180 * day, false))
    }

    @Test
    fun `plant prices and keys`() {
        assertEquals(4, PlantKind.TULIPS_MIX.let { it.stepCost * it.stages })
        assertEquals(20, PlantKind.BUSH.let { it.stepCost * it.stages })
        assertEquals(45, PlantKind.CHERRY.let { it.stepCost * it.stages })
        assertEquals(45, PlantKind.APPLE.let { it.stepCost * it.stages })
        assertEquals(PlantKind.entries.size, PlantKind.entries.map { it.key }.toSet().size)
        assertEquals(PlantKind.FLOWERS, PlantKind.fromKey("flowers"))
        assertNull(PlantKind.fromKey("baobab"))
    }
}
