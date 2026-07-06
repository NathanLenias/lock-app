package com.nathanb.lock.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nathanb.lock.data.model.Session
import com.nathanb.lock.ui.theme.LockTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val sessionDateFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRANCE)

@Composable
internal fun SessionRow(session: Session, modifier: Modifier = Modifier) {
    val colors = LockTheme.colors
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(session.startTime).atZone(zone).toLocalDate()
    val dateText = date.format(sessionDateFormatter)
        .replaceFirstChar { it.uppercase() }
    val durationMs = (session.endTime ?: System.currentTimeMillis()) - session.startTime

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.cardContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Green dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(colors.primary),
            )
            // Date
            Text(
                text = dateText,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            // Duration
            Text(
                text = formatDuration(durationMs),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                ),
                color = colors.onSurface,
            )
        }
    }
}
