package com.nathanb.lock.ui.screens.stats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.nathanb.lock.R
import com.nathanb.lock.ui.theme.LockTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY_LABELS = listOf("L", "M", "M", "J", "V", "S", "D")

@Composable
internal fun WeeklyBarChart(
    week: WeekStats,
    previousWeekMs: Long,
    isCurrentWeek: Boolean,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
) {
    val colors = LockTheme.colors
    val maxDailyMs = week.days.maxOfOrNull { it.totalMs }?.coerceAtLeast(1L) ?: 1L

    val dateFormatter = DateTimeFormatter.ofPattern("d", Locale.FRANCE)
    val monthFormatter = DateTimeFormatter.ofPattern("MMM", Locale.FRANCE)

    val startDay = week.weekStart.format(dateFormatter)
    val endDay = week.weekEnd.dayOfMonth
    val month = week.weekEnd.format(monthFormatter)
    val rangeText = "$startDay — $endDay $month"

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Section title
        Text(
            text = stringResource(R.string.stats_history),
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            ),
            color = colors.onSurface,
        )

        // Week navigator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.stats_previous_week),
                tint = colors.onSurface,
                modifier = Modifier
                    .size(20.dp)
                    .clickable { onPreviousWeek() },
            )
            Text(
                text = rangeText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                ),
                color = colors.onSurface,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.stats_next_week),
                tint = if (isCurrentWeek) colors.surfaceContainer else colors.onSurface,
                modifier = Modifier
                    .size(20.dp)
                    .then(
                        if (!isCurrentWeek) Modifier.clickable { onNextWeek() }
                        else Modifier
                    ),
            )
        }

        // Chart card with swipe
        var dragAccumulator by remember { mutableFloatStateOf(0f) }
        val swipeThreshold = 100f

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = colors.cardContainer,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .pointerInput(isCurrentWeek) {
                        detectHorizontalDragGestures(
                            onDragStart = { dragAccumulator = 0f },
                            onHorizontalDrag = { _, dragAmount ->
                                dragAccumulator += dragAmount
                            },
                            onDragEnd = {
                                if (dragAccumulator > swipeThreshold) {
                                    onPreviousWeek()
                                } else if (dragAccumulator < -swipeThreshold && !isCurrentWeek) {
                                    onNextWeek()
                                }
                                dragAccumulator = 0f
                            },
                        )
                    },
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Chart header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.stats_this_week),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        ),
                        color = colors.onSurface,
                    )
                    Text(
                        text = formatDuration(week.totalMs),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        ),
                        color = colors.primary,
                    )
                }

                // Bars
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    week.days.forEachIndexed { index, day ->
                        val hasData = day.totalMs > 0
                        val fraction = if (maxDailyMs > 0) {
                            (day.totalMs.toFloat() / maxDailyMs).coerceIn(0f, 1f)
                        } else {
                            0f
                        }

                        val animatedFraction by animateFloatAsState(
                            targetValue = fraction,
                            animationSpec = tween(600),
                            label = "bar_${day.date}",
                        )

                        val minBarFraction = 0.07f
                        val displayFraction = if (hasData) {
                            animatedFraction.coerceAtLeast(minBarFraction)
                        } else {
                            minBarFraction
                        }

                        val isToday = day.date == LocalDate.now()

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                        ) {
                            if (displayFraction < 1f) {
                                Spacer(Modifier.weight(1f - displayFraction))
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(displayFraction)
                                    .background(
                                        color = if (hasData) colors.primary
                                        else colors.surfaceContainer,
                                        shape = RoundedCornerShape(6.dp),
                                    ),
                            )

                            Spacer(Modifier.height(6.dp))

                            Text(
                                text = DAY_LABELS[index],
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.SemiBold,
                                    fontSize = 10.sp,
                                ),
                                color = if (isToday) colors.primary else colors.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                // Comparison pill
                if (previousWeekMs > 0 && week.totalMs > 0) {
                    val diffMs = week.totalMs - previousWeekMs
                    val isPositive = diffMs >= 0
                    val diffHours = diffMs / 3_600_000f
                    val diffText = if (isPositive) {
                        val h = (diffHours + 0.5f).toInt().coerceAtLeast(1)
                        stringResource(R.string.stats_vs_previous_week_positive, h)
                    } else {
                        val h = (-diffHours + 0.5f).toInt().coerceAtLeast(1)
                        stringResource(R.string.stats_vs_previous_week_negative, h)
                    }
                    val pillColor = if (isPositive) colors.primary else colors.error
                    val pillIcon = if (isPositive) {
                        Icons.AutoMirrored.Filled.TrendingUp
                    } else {
                        Icons.AutoMirrored.Filled.TrendingDown
                    }

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = pillColor.copy(alpha = 0.1f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = pillIcon,
                                    contentDescription = null,
                                    tint = pillColor,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = diffText,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 11.sp,
                                    ),
                                    color = pillColor,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
