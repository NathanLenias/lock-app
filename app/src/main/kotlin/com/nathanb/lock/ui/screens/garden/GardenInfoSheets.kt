package com.nathanb.lock.ui.screens.garden

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material.icons.outlined.Yard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nathanb.lock.R
import com.nathanb.lock.ui.components.LockBottomSheet
import com.nathanb.lock.ui.components.LockSheetFrame
import com.nathanb.lock.ui.theme.LockTheme

/** What the garden is for, and what happens to it on a reinstall or a new phone. */
@Composable
fun GardenInfoSheet(onDismiss: () -> Unit) {
    val colors = LockTheme.colors
    LockSheetFrame(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Outlined.Yard,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.garden_info_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                InfoPoint(Icons.Outlined.EventAvailable, stringResource(R.string.garden_info_point_days))
                InfoPoint(Icons.Outlined.RestartAlt, stringResource(R.string.garden_info_point_reinstall))
                InfoPoint(Icons.Outlined.Smartphone, stringResource(R.string.garden_info_point_device))
                InfoPoint(Icons.Outlined.WifiOff, stringResource(R.string.garden_info_point_support))
            }
        }
    }
}

@Composable
private fun InfoPoint(icon: ImageVector, text: String) {
    val colors = LockTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.onSurface,
            modifier = Modifier.padding(top = 2.dp).size(20.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface,
            lineHeight = 21.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

/** How drops are earned and what they pay for. */
@Composable
fun DropsInfoSheet(onDismiss: () -> Unit) {
    LockBottomSheet(
        onDismiss = onDismiss,
        icon = Icons.Outlined.WaterDrop,
        title = stringResource(R.string.garden_drops_title),
        body = stringResource(R.string.garden_drops_body_earn) + "\n\n" +
            stringResource(R.string.garden_drops_body_use),
    )
}

/** State of a terrain size compared with the garden's current one. */
enum class TierState { REACHED, CURRENT, AHEAD }

/** Miniature of a terrain: [size] x [size] squares. */
@Composable
fun TerrainMiniature(size: Int, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val gap = this.size.width / 16f
        val side = (this.size.width - gap * (size - 1)) / size
        val radius = CornerRadius(gap * if (size == 1) 1.5f else 1f)
        for (row in 0 until size) {
            for (col in 0 until size) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(col * (side + gap), row * (side + gap)),
                    size = Size(side, side),
                    cornerRadius = radius,
                )
            }
        }
    }
}

@Composable
fun tierColor(state: TierState): Color {
    val colors = LockTheme.colors
    return when (state) {
        TierState.REACHED -> colors.primary.copy(alpha = 0.45f)
        TierState.CURRENT -> colors.primary
        TierState.AHEAD -> colors.onSurfaceVariant.copy(alpha = 0.25f)
    }
}

/**
 * What a terrain size is: when it was or will be unlocked, and what it changes.
 * [unlockDay] is null for the starting terrain and for a size that was given.
 */
@Composable
fun TerrainTierSheet(
    size: Int,
    state: TierState,
    unlockDay: Int?,
    wasOffered: Boolean,
    currentDay: Int,
    onDismiss: () -> Unit,
) {
    val colors = LockTheme.colors
    val tiles = size * size
    val before = (size - 1) * (size - 1)
    val origin = when {
        wasOffered -> stringResource(R.string.garden_tier_offered_body)
        unlockDay != null -> stringResource(R.string.garden_tier_unlocked, unlockDay)
        else -> stringResource(R.string.garden_tier_start)
    }
    val paragraphs: List<String> = when (state) {
        TierState.REACHED -> listOfNotNull(
            origin,
            if (size > 1) stringResource(R.string.garden_tier_grew, before, tiles) else null,
        )
        TierState.CURRENT -> listOf(
            stringResource(R.string.garden_tier_current) + " " + origin,
            pluralStringResource(R.plurals.garden_tier_count, tiles, tiles),
        )
        TierState.AHEAD -> {
            val day = unlockDay ?: currentDay
            val left = (day - currentDay).coerceAtLeast(0)
            listOf(
                pluralStringResource(R.plurals.garden_tier_ahead, left, day, left),
                stringResource(R.string.garden_tier_will_grow, before, tiles),
            )
        }
    }

    LockSheetFrame(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 32.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TerrainMiniature(size = size, color = tierColor(state), modifier = Modifier.size(44.dp))
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.garden_tier_title, size),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
            )
            paragraphs.forEach { paragraph ->
                Spacer(Modifier.height(10.dp))
                Text(
                    text = paragraph,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 21.sp,
                )
            }
            if (state == TierState.AHEAD && unlockDay != null) {
                Spacer(Modifier.height(20.dp))
                val fraction = (currentDay.toFloat() / unlockDay).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.onSurfaceVariant.copy(alpha = 0.2f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.primary),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = stringResource(R.string.garden_day, currentDay),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.garden_day, unlockDay),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
