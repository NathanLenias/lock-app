package com.nathanb.lock.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nathanb.lock.R
import com.nathanb.lock.ui.theme.BrickTheme
import com.nathanb.lock.ui.theme.SatoshiFamily

/** No-escape session duration options. */
val DURATION_OPTIONS_MS: List<Long> = listOf(5, 10, 15, 20).map { it * 60_000L }

@Composable
fun DurationSelector(
    selectedMs: Long,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BrickTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DURATION_OPTIONS_MS.forEach { ms ->
            val selected = ms == selectedMs
            val minutes = (ms / 60_000L).toInt()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) colors.primary else colors.surfaceContainer)
                    .clickable { onSelect(ms) }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.profile_duration_minutes, minutes),
                    fontFamily = SatoshiFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = if (selected) Color.White else colors.onSurface,
                )
            }
        }
    }
}
