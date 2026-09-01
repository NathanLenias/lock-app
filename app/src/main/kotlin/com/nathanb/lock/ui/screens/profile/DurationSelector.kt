package com.nathanb.lock.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nathanb.lock.R
import com.nathanb.lock.ui.theme.LockTheme
import com.nathanb.lock.ui.theme.SatoshiFamily

/** No-escape session duration presets. Any other value is a custom duration. */
val DURATION_OPTIONS_MS: List<Long> = listOf(5, 10, 15, 20).map { it * 60_000L }

private const val MINUTE_MS = 60_000L
private const val CUSTOM_MIN_MS = 5 * MINUTE_MS
private const val CUSTOM_MAX_MS = 120 * MINUTE_MS
private const val CUSTOM_STEP_MS = 5 * MINUTE_MS
private const val CUSTOM_DEFAULT_MS = 30 * MINUTE_MS

/**
 * No-escape duration picker: four preset chips (big number over "min") and a
 * custom card that expands into a minus/plus stepper when selected.
 */
@Composable
fun DurationPicker(
    selectedMs: Long,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LockTheme.colors
    val isCustom = selectedMs !in DURATION_OPTIONS_MS

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DURATION_OPTIONS_MS.forEach { ms ->
                DurationChip(
                    value = "${ms / MINUTE_MS}",
                    unit = stringResource(R.string.profile_duration_unit),
                    selected = !isCustom && ms == selectedMs,
                    onClick = { onSelect(ms) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.cardContainer),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isCustom) colors.primary else Color.Transparent)
                    .clickable { if (!isCustom) onSelect(CUSTOM_DEFAULT_MS) }
                    .padding(horizontal = 16.dp, vertical = 17.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Tune,
                    contentDescription = null,
                    tint = if (isCustom) Color.White else colors.onSurface,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.profile_duration_custom),
                    fontFamily = SatoshiFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = if (isCustom) Color.White else colors.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (isCustom) Icons.Outlined.CheckCircle else Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = if (isCustom) Color.White else colors.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
            if (isCustom) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    StepperButton(
                        icon = Icons.Outlined.Remove,
                        contentDescription = stringResource(R.string.profile_duration_decrease),
                        background = colors.onSurfaceVariant.copy(alpha = 0.08f),
                        tint = colors.onSurface,
                        enabled = selectedMs > CUSTOM_MIN_MS,
                        onClick = { onSelect((selectedMs - CUSTOM_STEP_MS).coerceAtLeast(CUSTOM_MIN_MS)) },
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            text = "${selectedMs / MINUTE_MS}",
                            fontFamily = SatoshiFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp,
                            color = colors.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.profile_duration_unit),
                            fontFamily = SatoshiFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    StepperButton(
                        icon = Icons.Outlined.Add,
                        contentDescription = stringResource(R.string.profile_duration_increase),
                        background = colors.primary,
                        tint = Color.White,
                        enabled = selectedMs < CUSTOM_MAX_MS,
                        onClick = { onSelect((selectedMs + CUSTOM_STEP_MS).coerceAtMost(CUSTOM_MAX_MS)) },
                    )
                }
            }
        }
    }
}

/** Preset duration chip: big number over its unit, shared by profile and schedule pickers. */
@Composable
fun DurationChip(
    value: String,
    unit: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LockTheme.colors
    Column(
        modifier = modifier
            .height(66.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) colors.primary else colors.cardContainer)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            fontFamily = SatoshiFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = if (selected) Color.White else colors.onSurface,
        )
        Text(
            text = unit,
            fontFamily = SatoshiFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            color = if (selected) Color.White.copy(alpha = 0.85f) else colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun StepperButton(
    icon: ImageVector,
    contentDescription: String,
    background: Color,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (enabled) background else background.copy(alpha = 0.35f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.4f),
            modifier = Modifier.size(22.dp),
        )
    }
}
