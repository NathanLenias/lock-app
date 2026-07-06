package com.nathanb.lock.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nathanb.lock.ui.theme.LockTheme
import java.time.LocalDate

private val DAY_LABELS = listOf("L", "M", "M", "J", "V", "S", "D")

@Composable
internal fun WeekDaySelector(
    weekDays: List<DayStats>,
    modifier: Modifier = Modifier,
) {
    val colors = LockTheme.colors
    val today = LocalDate.now()

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        weekDays.forEachIndexed { index, day ->
            val isToday = day.date == today
            val hasActivity = day.sessionCount > 0

            Column(
                modifier = Modifier
                    .width(36.dp)
                    .height(44.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Day circle
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .then(
                            if (isToday) Modifier.background(colors.primary)
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = DAY_LABELS[index],
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                        ),
                        color = if (isToday) Color.White else colors.onSurfaceVariant,
                    )
                }

                // Activity dot
                if (hasActivity) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(colors.primary),
                    )
                } else {
                    // Invisible spacer dot to keep alignment
                    Box(modifier = Modifier.size(4.dp))
                }
            }
        }
    }
}
