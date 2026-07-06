package com.nathanb.lock.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nathanb.lock.R
import com.nathanb.lock.ui.screens.stats.SessionRow
import com.nathanb.lock.ui.screens.stats.StatPill
import com.nathanb.lock.ui.screens.stats.StreakCard
import com.nathanb.lock.ui.screens.stats.TodayCard
import com.nathanb.lock.ui.screens.stats.WeekDaySelector
import com.nathanb.lock.ui.screens.stats.WeeklyBarChart
import com.nathanb.lock.ui.screens.stats.computeDailyStats
import com.nathanb.lock.ui.screens.stats.computeStreak
import com.nathanb.lock.ui.screens.stats.formatDuration
import com.nathanb.lock.ui.screens.stats.formatValue
import com.nathanb.lock.ui.screens.stats.getRecentSessions
import com.nathanb.lock.ui.screens.stats.getWeekForDate
import com.nathanb.lock.ui.theme.BrickTheme
import com.nathanb.lock.ui.viewmodel.LockViewModel
import java.time.DayOfWeek
import java.time.LocalDate

@Composable
fun StatsScreen(
    viewModel: LockViewModel,
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val sessionCount by viewModel.completedSessionCount.collectAsStateWithLifecycle()
    val totalMs by viewModel.totalBlockedMs.collectAsStateWithLifecycle()

    val completedSessions = remember(sessions) {
        sessions.filter { it.endTime != null }
    }

    val dailyStats = remember(completedSessions) {
        computeDailyStats(completedSessions)
    }

    val streak = remember(dailyStats) {
        computeStreak(dailyStats)
    }

    // Today's stats
    val today = LocalDate.now()
    val todayStats = remember(dailyStats, today) {
        dailyStats[today]
    }

    // Current week for day selector
    val currentWeekDays = remember(dailyStats, today) {
        val monday = today.with(DayOfWeek.MONDAY)
        (0..6).map { i ->
            val date = monday.plusDays(i.toLong())
            dailyStats[date] ?: com.nathanb.lock.ui.screens.stats.DayStats(date, 0, 0)
        }
    }

    // Recent sessions
    val recentSessions = remember(completedSessions) {
        getRecentSessions(completedSessions)
    }

    // Week navigation state for chart
    var chartWeekOffset by remember { mutableStateOf(0) }
    val chartWeekDate = remember(chartWeekOffset) {
        today.minusWeeks(chartWeekOffset.toLong())
    }
    val chartWeek = remember(chartWeekDate, dailyStats) {
        getWeekForDate(chartWeekDate, dailyStats)
    }
    val previousChartWeek = remember(chartWeekDate, dailyStats) {
        getWeekForDate(chartWeekDate.minusWeeks(1), dailyStats)
    }

    // Average session duration
    val averageMs = remember(totalMs, sessionCount) {
        if (sessionCount > 0) totalMs / sessionCount else 0L
    }

    val colors = BrickTheme.colors

    Scaffold(
        containerColor = colors.surface,
    ) { padding ->
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Title
        item {
            Text(
                text = stringResource(R.string.stats_title),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                ),
                color = colors.onSurface,
            )
        }

        // Streak card + 3 stat pills
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StreakCard(
                    streak = streak,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatPill(
                        icon = Icons.Outlined.GpsFixed,
                        value = "$sessionCount",
                        label = stringResource(R.string.stats_sessions),
                    )
                    StatPill(
                        icon = Icons.Outlined.Schedule,
                        value = formatValue(totalMs),
                        label = stringResource(R.string.stats_total),
                    )
                    StatPill(
                        icon = Icons.Outlined.Timer,
                        value = if (sessionCount > 0) formatValue(averageMs) else "—",
                        label = "",
                        suffix = stringResource(R.string.stats_average),
                    )
                }
            }
        }

        // "Cette semaine" section
        item {
            Text(
                text = stringResource(R.string.stats_this_week),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                ),
                color = colors.onSurface,
            )
        }

        // Week day selector
        item {
            WeekDaySelector(weekDays = currentWeekDays)
        }

        // Today card
        item {
            TodayCard(
                sessionCount = todayStats?.sessionCount ?: 0,
                totalMs = todayStats?.totalMs ?: 0L,
            )
        }

        // "Dernières sessions" section
        if (recentSessions.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.stats_recent_sessions),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    ),
                    color = colors.onSurface,
                )
            }

            items(
                items = recentSessions,
                key = { it.id },
            ) { session ->
                SessionRow(session = session)
            }
        }

        // Weekly bar chart with navigator
        item {
            WeeklyBarChart(
                week = chartWeek,
                previousWeekMs = previousChartWeek.totalMs,
                isCurrentWeek = chartWeekOffset == 0,
                onPreviousWeek = { chartWeekOffset++ },
                onNextWeek = { if (chartWeekOffset > 0) chartWeekOffset-- },
            )
        }
    }
    }
}
