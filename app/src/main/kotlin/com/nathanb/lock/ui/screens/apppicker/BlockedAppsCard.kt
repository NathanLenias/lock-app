package com.nathanb.lock.ui.screens.apppicker

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.nathanb.lock.R
import com.nathanb.lock.ui.theme.LockTheme
import com.nathanb.lock.ui.theme.SatoshiFamily
import com.nathanb.lock.ui.viewmodel.InstalledApp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BlockedAppsCard(
    blockedApps: List<InstalledApp>,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onRemoveApp: (String) -> Unit,
) {
    val colors = LockTheme.colors
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        label = "chevron",
    )

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AvatarStack()
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.app_picker_blocked_count, blockedApps.size),
                        fontFamily = SatoshiFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = colors.onSurface,
                    )
                    Text(
                        if (expanded) stringResource(R.string.app_picker_collapse) else stringResource(R.string.app_picker_expand),
                        fontFamily = SatoshiFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(chevronRotation),
                )
            }

            // Expandable content
            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = colors.surfaceContainer, thickness = 1.dp)
                    Spacer(Modifier.height(12.dp))
                    if (blockedApps.isEmpty()) {
                        Text(
                            stringResource(R.string.app_picker_no_blocked),
                            fontFamily = SatoshiFamily,
                            fontSize = 13.sp,
                            color = colors.onSurfaceVariant,
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            blockedApps.forEach { app ->
                                BlockedChip(
                                    label = app.label,
                                    onRemove = { onRemoveApp(app.packageName) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AvatarStack() {
    val colors = LockTheme.colors
    val icons = listOf(Icons.Outlined.Block, Icons.Outlined.Lock, Icons.Outlined.Shield)

    Box(modifier = Modifier.size(width = 68.dp, height = 36.dp)) {
        icons.forEachIndexed { index, icon ->
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .offset(x = (index * 16).dp)
                    .then(
                        if (index > 0) {
                            Modifier
                                .border(2.dp, colors.cardContainer, CircleShape)
                        } else Modifier
                    )
                    .background(colors.lockedContainer, CircleShape)
                    .border(1.dp, colors.lockedPrimary.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.lockedPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun BlockedChip(
    label: String,
    onRemove: () -> Unit,
) {
    val colors = LockTheme.colors

    Row(
        modifier = Modifier
            .height(32.dp)
            .background(colors.lockedContainer, RoundedCornerShape(20.dp))
            .clickable(onClick = onRemove)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            Icons.Default.Close,
            contentDescription = stringResource(R.string.app_picker_remove_cd, label),
            tint = colors.lockedPrimary,
            modifier = Modifier.size(14.dp),
        )
        Text(
            label,
            fontFamily = SatoshiFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            color = colors.lockedPrimary,
        )
    }
}
