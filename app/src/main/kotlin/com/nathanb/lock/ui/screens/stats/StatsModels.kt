package com.nathanb.lock.ui.screens.stats

import com.nathanb.lock.data.model.Session
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal data class DayStats(
    val date: LocalDate,
    val totalMs: Long,
    val sessionCount: Int,
)

internal data class WeekStats(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val days: List<DayStats>,
    val totalMs: Long,
)

internal fun computeDailyStats(sessions: List<Session>): Map<LocalDate, DayStats> {
    val zone = ZoneId.systemDefault()
    val byDay = mutableMapOf<LocalDate, MutableList<Session>>()

    for (session in sessions) {
        val date = Instant.ofEpochMilli(session.startTime).atZone(zone).toLocalDate()
        byDay.getOrPut(date) { mutableListOf() }.add(session)
    }

    return byDay.mapValues { (date, daySessions) ->
        DayStats(
            date = date,
            totalMs = daySessions.sumOf { (it.endTime ?: 0L) - it.startTime },
            sessionCount = daySessions.size,
        )
    }
}

internal fun getWeekForDate(
    date: LocalDate,
    dailyStats: Map<LocalDate, DayStats>,
): WeekStats {
    val monday = date.with(DayOfWeek.MONDAY)
    val sunday = monday.plusDays(6)

    val days = (0..6).map { i ->
        val d = monday.plusDays(i.toLong())
        dailyStats[d] ?: DayStats(d, 0, 0)
    }

    return WeekStats(
        weekStart = monday,
        weekEnd = sunday,
        days = days,
        totalMs = days.sumOf { it.totalMs },
    )
}

internal fun computeStreak(dailyStats: Map<LocalDate, DayStats>): Int {
    if (dailyStats.isEmpty()) return 0

    var streak = 0
    var date = LocalDate.now()

    if (dailyStats[date] == null || dailyStats[date]!!.totalMs == 0L) {
        date = date.minusDays(1)
    }

    while (true) {
        val day = dailyStats[date]
        if (day != null && day.totalMs > 0) {
            streak++
            date = date.minusDays(1)
        } else {
            break
        }
    }

    return streak
}

internal fun getRecentSessions(sessions: List<Session>, limit: Int = 4): List<Session> {
    return sessions
        .filter { it.endTime != null }
        .sortedByDescending { it.startTime }
        .take(limit)
}

internal fun formatDuration(ms: Long): String {
    val totalMinutes = ms / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return "${hours}h ${minutes.toString().padStart(2, '0')}m"
}

internal fun formatValue(ms: Long): String {
    val totalMinutes = ms / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "${hours}h ${minutes.toString().padStart(2, '0')}m"
        minutes > 0 -> "${minutes} min"
        else -> "<1 min"
    }
}

