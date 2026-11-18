package com.nathanb.lock.data.garden

import com.nathanb.lock.data.model.Session
import java.time.Instant
import java.time.ZoneId

/**
 * What can grow in the garden. [key] is what is stored in the database: never rename one.
 * [stepCost] drops are paid for planting and again for each growth step, up to [stages].
 */
enum class PlantKind(val key: String, val stepCost: Int, val stages: Int) {
    TULIPS_RED_YELLOW("tulips_red_yellow", 1, 4),
    TULIPS_MIX("tulips_mix", 1, 4),
    FLOWERS("flowers", 1, 4),
    BUSH("bush", 5, 4),
    CHERRY("cherry", 15, 3),
    APPLE("apple", 15, 3);

    companion object {
        fun fromKey(key: String): PlantKind? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Garden economy. Pure Kotlin on purpose: unit-tested on the JVM.
 *
 * Drops are never stored: they are recomputed from the session history, so they cannot be
 * counted twice. Only what was spent is persisted.
 */
object GardenRules {

    const val DAILY_DROP_CAP = 3
    const val WELCOME_DROPS = 1
    const val WELCOME_DROPS_RETURNING = 3
    const val MAX_TERRAIN_SIZE = 4

    /** Price of moving a plant to another tile (or swapping two plants). */
    const val MOVE_COST = 3

    /** How long a fresh planting can be undone, drops refunded (a slip of the finger). */
    const val UNDO_PLANT_WINDOW_MS = 2 * 60 * 1000L

    private const val HOUR_MS = 60 * 60 * 1000L
    private const val DAY_MS = 24 * HOUR_MS

    /** A first session at least this old when the garden is created makes a returning user. */
    private const val RETURNING_AFTER_MS = 30 * DAY_MS

    /** Day of the garden (the first one is day 1) on which the terrain becomes 2x2, 3x3 and 4x4. */
    val TERRAIN_THRESHOLD_DAYS = listOf(15, 90, 180)

    /** Returning users are given the 2x2 terrain from day one; later sizes come at the same age. */
    private const val RETURNING_MIN_SIZE = 2

    /**
     * One drop per full hour of finished sessions, hours being added up per day (the day a
     * session ends), at most [DAILY_DROP_CAP] a day. A running session earns nothing yet.
     */
    fun drops(sessions: List<Session>, zone: ZoneId): Int =
        sessions
            .filter { it.endTime != null }
            .groupBy { Instant.ofEpochMilli(it.endTime!!).atZone(zone).toLocalDate() }
            .values
            .sumOf { day ->
                val hours = day.sumOf { (it.endTime!! - it.startTime).coerceAtLeast(0L) } / HOUR_MS
                hours.coerceAtMost(DAILY_DROP_CAP.toLong()).toInt()
            }

    /** Drops earned by the sessions that ended once the garden existed. */
    fun earnedDrops(sessions: List<Session>, createdAt: Long, zone: ZoneId): Int =
        drops(sessions.filter { (it.endTime ?: Long.MIN_VALUE) >= createdAt }, zone)

    /**
     * Gift at the creation of the garden. The history from before the garden is not paid
     * back (everything could be planted at once): it only raises the gift from 1 to 3.
     */
    fun welcomeDrops(sessions: List<Session>, createdAt: Long, zone: ZoneId): Int {
        val before = drops(sessions.filter { (it.endTime ?: Long.MAX_VALUE) < createdAt }, zone)
        return if (before >= WELCOME_DROPS_RETURNING) WELCOME_DROPS_RETURNING else WELCOME_DROPS
    }

    fun balance(sessions: List<Session>, createdAt: Long, dropsSpent: Int, zone: ZoneId): Int =
        (welcomeDrops(sessions, createdAt, zone) + earnedDrops(sessions, createdAt, zone) - dropsSpent)
            .coerceAtLeast(0)

    fun isReturningUser(sessions: List<Session>, createdAt: Long): Boolean {
        val first = sessions.minOfOrNull { it.startTime } ?: return false
        return first <= createdAt - RETURNING_AFTER_MS
    }

    /** Day of the garden as shown to the user: 1 on the day it is born. */
    fun dayNumber(createdAt: Long, now: Long): Int =
        ((now - createdAt).coerceAtLeast(0L) / DAY_MS).toInt() + 1

    /** Side of the terrain a garden has earned by its age alone on day [day]. */
    private fun sizeOnDay(day: Int): Int = 1 + TERRAIN_THRESHOLD_DAYS.count { day >= it }

    /** Side of the square terrain: 1 at first, up to [MAX_TERRAIN_SIZE]. */
    fun terrainSize(createdAt: Long, now: Long, returning: Boolean): Int {
        val size = sizeOnDay(dayNumber(createdAt, now))
        return if (returning) maxOf(size, RETURNING_MIN_SIZE) else size
    }

    /** Days left before the terrain grows again, or null once it is at its largest. */
    fun daysUntilNextSize(createdAt: Long, now: Long, returning: Boolean): Int? {
        val day = dayNumber(createdAt, now)
        val current = terrainSize(createdAt, now, returning)
        // A size already given (the 2x2 of a returning user) is not announced again.
        val next = TERRAIN_THRESHOLD_DAYS.firstOrNull { sizeOnDay(it) > current } ?: return null
        return next - day
    }
}
