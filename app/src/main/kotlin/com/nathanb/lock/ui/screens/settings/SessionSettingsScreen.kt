package com.nathanb.lock.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.nathanb.lock.R
import com.nathanb.lock.ui.components.LockBottomSheet
import com.nathanb.lock.ui.theme.BrickTheme
import com.nathanb.lock.ui.viewmodel.LockViewModel

@Composable
fun SessionSettingsScreen(
    viewModel: LockViewModel,
    onBack: () -> Unit,
) {
    val colors = BrickTheme.colors

    val gracePeriodMs by viewModel.gracePeriodMs.collectAsStateWithLifecycle()
    val maxEmergencyUnlocks by viewModel.maxEmergencyUnlocks.collectAsStateWithLifecycle()
    val emergencyDurationMs by viewModel.emergencyUnlockDurationMs.collectAsStateWithLifecycle()
    val timeoutDurationMs by viewModel.timeoutDurationMs.collectAsStateWithLifecycle()
    var showUnlimitedWarning by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(54.dp))

        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = colors.primary,
                )
            }
            Text(
                text = stringResource(R.string.session_settings_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = colors.onSurface,
                letterSpacing = (-0.5).sp,
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.session_settings_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            lineHeight = 21.sp,
        )

        Spacer(Modifier.height(22.dp))

        // --- Grace Period ---
        SectionLabel(
            text = stringResource(R.string.session_grace_period),
            sheetTitle = stringResource(R.string.session_grace_period_title),
            tooltip = stringResource(R.string.session_grace_period_tooltip),
        )
        Spacer(Modifier.height(8.dp))
        SettingsCard {
            val graceOptions = listOf(
                0L to stringResource(R.string.session_grace_immediate),
                15_000L to stringResource(R.string.session_grace_15s),
                30_000L to stringResource(R.string.session_grace_30s),
                60_000L to stringResource(R.string.session_grace_1m),
                120_000L to stringResource(R.string.session_grace_2m),
            )
            graceOptions.forEachIndexed { index, (value, label) ->
                RadioRow(
                    label = label,
                    selected = gracePeriodMs == value,
                    onClick = { viewModel.setGracePeriodMs(value) },
                )
                if (index < graceOptions.lastIndex) {
                    SettingsDivider()
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        // --- Emergency Unlocks ---
        SectionLabel(
            text = stringResource(R.string.session_emergency_unlocks),
            sheetTitle = stringResource(R.string.session_emergency_unlocks_title),
            tooltip = stringResource(R.string.session_emergency_unlocks_tooltip),
        )
        Spacer(Modifier.height(8.dp))
        SettingsCard {
            val unlockOptions = listOf(0, 1, 2, 3)
            unlockOptions.forEachIndexed { index, value ->
                RadioRow(
                    label = value.toString(),
                    selected = maxEmergencyUnlocks == value,
                    onClick = { viewModel.setMaxEmergencyUnlocks(value) },
                )
                if (index < unlockOptions.lastIndex) {
                    SettingsDivider()
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        // --- Emergency Unlock Duration ---
        SectionLabel(
            text = stringResource(R.string.session_emergency_duration),
            sheetTitle = stringResource(R.string.session_emergency_duration_title),
            tooltip = stringResource(R.string.session_emergency_duration_tooltip),
        )
        Spacer(Modifier.height(8.dp))
        SettingsCard {
            val durationOptions = listOf(
                2 * 60 * 1000L to stringResource(R.string.session_emergency_2m),
                5 * 60 * 1000L to stringResource(R.string.session_emergency_5m),
                10 * 60 * 1000L to stringResource(R.string.session_emergency_10m),
            )
            durationOptions.forEachIndexed { index, (value, label) ->
                RadioRow(
                    label = label,
                    selected = emergencyDurationMs == value,
                    onClick = { viewModel.setEmergencyUnlockDurationMs(value) },
                )
                if (index < durationOptions.lastIndex) {
                    SettingsDivider()
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        // --- Session Timeout ---
        SectionLabel(
            text = stringResource(R.string.session_max_duration),
            sheetTitle = stringResource(R.string.session_max_duration_title),
            tooltip = stringResource(R.string.session_max_duration_tooltip),
        )
        Spacer(Modifier.height(8.dp))
        SettingsCard {
            val timeoutOptions = listOf(
                2 * 60 * 60 * 1000L to stringResource(R.string.session_timeout_2h),
                3 * 60 * 60 * 1000L to stringResource(R.string.session_timeout_3h),
                5 * 60 * 60 * 1000L to stringResource(R.string.session_timeout_5h),
                8 * 60 * 60 * 1000L to stringResource(R.string.session_timeout_8h),
                0L to stringResource(R.string.session_timeout_unlimited),
            )
            timeoutOptions.forEachIndexed { index, (value, label) ->
                RadioRow(
                    label = label,
                    selected = timeoutDurationMs == value,
                    onClick = {
                        if (value == 0L && timeoutDurationMs != 0L) {
                            showUnlimitedWarning = true
                        } else {
                            viewModel.setTimeoutDurationMs(value)
                        }
                    },
                )
                if (index < timeoutOptions.lastIndex) {
                    SettingsDivider()
                }
            }
        }

        Spacer(Modifier.height(100.dp))
    }

    if (showUnlimitedWarning) {
        LockBottomSheet(
            onDismiss = { showUnlimitedWarning = false },
            icon = Icons.Outlined.Warning,
            title = stringResource(R.string.session_unlimited_warning_title),
            body = stringResource(R.string.session_unlimited_warning_body),
            actions = {
                Button(
                    onClick = {
                        viewModel.setTimeoutDurationMs(0L)
                        showUnlimitedWarning = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.error,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(stringResource(R.string.session_unlimited_confirm))
                }

                Spacer(Modifier.height(8.dp))

                TextButton(
                    onClick = { showUnlimitedWarning = false },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = colors.primary,
                    ),
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String, sheetTitle: String = text, tooltip: String? = null) {
    val colors = BrickTheme.colors
    var showSheet by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = colors.onSurfaceVariant,
            letterSpacing = 0.8.sp,
            fontSize = 12.sp,
        )
        if (tooltip != null) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = stringResource(R.string.label_info),
                tint = colors.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .size(16.dp)
                    .clickable { showSheet = true },
            )
        }
    }

    if (showSheet && tooltip != null) {
        LockBottomSheet(
            onDismiss = { showSheet = false },
            icon = Icons.Outlined.Info,
            title = sheetTitle,
            body = tooltip,
        )
    }
}

@Composable
private fun RadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = BrickTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (selected) colors.primary else colors.onSurface,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )

        // Radio indicator
        if (selected) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(colors.primary),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color(0xFFC0C0C0), CircleShape),
            )
        }
    }
}
