package com.nathanb.lock.ui.screens.schedules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nathanb.lock.R
import com.nathanb.lock.data.model.ProfileType
import com.nathanb.lock.ui.components.LockBottomSheet
import androidx.compose.ui.platform.LocalContext
import com.nathanb.lock.ui.theme.LockTheme
import com.nathanb.lock.ui.theme.SatoshiFamily
import com.nathanb.lock.ui.viewmodel.LockViewModel
import com.nathanb.lock.util.PermissionHelper

@Composable
fun ScheduleEditScreen(
    viewModel: LockViewModel,
    scheduleId: Long,
    onBack: () -> Unit,
) {
    val colors = LockTheme.colors
    val isNew = scheduleId <= 0L
    val schedules by viewModel.schedules.collectAsStateWithLifecycle()
    val links by viewModel.scheduleLinks.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val existing = schedules.find { it.id == scheduleId }

    // Editable state, seeded once from the existing schedule (or defaults for a new one).
    var seeded by rememberSaveable { mutableStateOf(false) }
    var days by rememberSaveable { mutableStateOf(0b0011111) } // weekdays
    var startMin by rememberSaveable { mutableStateOf(9 * 60) }
    var endMin by rememberSaveable { mutableStateOf(17 * 60) }
    var selectedProfileIds by rememberSaveable { mutableStateOf(listOf<Long>()) }
    if (!seeded && (isNew || existing != null)) {
        if (existing != null) {
            days = existing.daysOfWeek
            startMin = existing.startMinuteOfDay
            endMin = existing.endMinuteOfDay
            selectedProfileIds = links.filter { it.scheduleId == scheduleId }.map { it.profileId }
        }
        seeded = true
    }

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var showProfilePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAlarmPermBeforeExit by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val overnight = endMin <= startMin && startMin != endMin
    val sameTime = startMin == endMin
    val canSave = days != 0 && !sameTime

    Scaffold(
        containerColor = colors.surface,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .height(56.dp)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = colors.primary,
                    )
                }
                Text(
                    text = stringResource(
                        if (isNew) R.string.schedule_edit_title_new else R.string.schedule_edit_title_edit,
                    ),
                    fontFamily = SatoshiFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 24.sp,
                    color = colors.onSurface,
                    letterSpacing = (-0.5).sp,
                    modifier = Modifier.weight(1f),
                )
                if (!isNew) {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            Icons.Outlined.DeleteOutline,
                            contentDescription = stringResource(R.string.schedule_delete),
                            tint = colors.error,
                        )
                    }
                }
            }
        },
        bottomBar = {
            Button(
                onClick = {
                    if (isNew) {
                        viewModel.createSchedule(days, startMin, endMin, selectedProfileIds)
                    } else if (existing != null) {
                        viewModel.updateSchedule(
                            existing.copy(daysOfWeek = days, startMinuteOfDay = startMin, endMinuteOfDay = endMin),
                            selectedProfileIds,
                        )
                    }
                    // Saving an active schedule without the exact-alarm permission:
                    // explain it once before leaving (degraded inexact mode otherwise).
                    if (!PermissionHelper.canScheduleExactAlarms(context)) {
                        showAlarmPermBeforeExit = true
                    } else {
                        onBack()
                    }
                },
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .navigationBarsPadding()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    disabledContainerColor = colors.surfaceContainer,
                ),
            ) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Text(
                    text = stringResource(R.string.schedule_save),
                    fontFamily = SatoshiFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = if (canSave) Color.White else colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            SectionLabel(stringResource(R.string.schedule_days_label))
            DayPills(days = days, onToggle = { bit -> days = days xor (1 shl bit) })
            if (days == 0) {
                HintText(stringResource(R.string.schedule_days_required), colors.error)
            }

            Spacer(Modifier.height(4.dp))
            SectionLabel(stringResource(R.string.schedule_hours_label))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TimeField(
                    label = stringResource(R.string.schedule_start_label),
                    minuteOfDay = startMin,
                    onClick = { showStartPicker = true },
                    modifier = Modifier.weight(1f),
                )
                TimeField(
                    label = stringResource(R.string.schedule_end_label),
                    minuteOfDay = endMin,
                    onClick = { showEndPicker = true },
                    modifier = Modifier.weight(1f),
                )
            }
            if (sameTime) {
                HintText(stringResource(R.string.schedule_same_time_error), colors.error)
            } else if (overnight) {
                OvernightBanner(endText = formatMinute(endMin))
            }

            Spacer(Modifier.height(4.dp))
            SectionLabel(stringResource(R.string.schedule_profiles_label))
            HintText(stringResource(R.string.schedule_profiles_hint), colors.onSurfaceVariant)

            val selectedProfiles = selectedProfileIds.mapNotNull { id -> profiles.find { it.id == id } }
            selectedProfiles.forEach { profile ->
                ProfileRow(
                    name = profile.name,
                    appCount = profile.blockedPackages.size,
                    onRemove = { selectedProfileIds = selectedProfileIds - profile.id },
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surfaceContainer)
                    .clickable { showProfilePicker = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = null,
                    tint = colors.primaryDark,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.schedule_add_profile),
                    fontFamily = SatoshiFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = colors.primaryDark,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            Spacer(Modifier.height(6.dp))
        }
    }

    if (showAlarmPermBeforeExit) {
        AlarmPermissionSheet(
            onDismiss = {
                showAlarmPermBeforeExit = false
                onBack()
            },
        )
    }
    if (showStartPicker) {
        TimePickerSheet(
            title = stringResource(R.string.schedule_start_label),
            minuteOfDay = startMin,
            onDone = { startMin = it; showStartPicker = false },
        )
    }
    if (showEndPicker) {
        TimePickerSheet(
            title = stringResource(R.string.schedule_end_label),
            minuteOfDay = endMin,
            onDone = { endMin = it; showEndPicker = false },
        )
    }
    if (showProfilePicker) {
        ProfilePickerSheet(
            profiles = profiles.filter { ProfileType.fromValue(it.type) == ProfileType.STANDARD },
            selectedIds = selectedProfileIds,
            onToggle = { id ->
                selectedProfileIds =
                    if (id in selectedProfileIds) selectedProfileIds - id else selectedProfileIds + id
            },
            onDismiss = { showProfilePicker = false },
        )
    }
    if (showDeleteConfirm) {
        LockBottomSheet(
            onDismiss = { showDeleteConfirm = false },
            title = stringResource(R.string.schedule_delete_confirm_title),
            body = stringResource(R.string.schedule_delete_confirm_body),
            icon = Icons.Outlined.DeleteOutline,
        ) {
            Button(
                onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteSchedule(scheduleId)
                    onBack()
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.error, contentColor = Color.White),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    text = stringResource(R.string.schedule_delete_confirm_cta),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            TextButton(onClick = { showDeleteConfirm = false }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    fontFamily = SatoshiFamily,
                    fontSize = 14.sp,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontFamily = SatoshiFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 1.sp,
        color = LockTheme.colors.onSurfaceVariant,
    )
}

@Composable
private fun HintText(text: String, color: Color) {
    Text(
        text = text,
        fontFamily = SatoshiFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        color = color,
    )
}

@Composable
private fun DayPills(days: Int, onToggle: (Int) -> Unit) {
    val colors = LockTheme.colors
    val initials = stringArrayResource(R.array.schedule_day_initials)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        (0..6).forEach { bit ->
            val selected = days and (1 shl bit) != 0
            Box(
                modifier = Modifier
                    .weight(1f)
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (selected) colors.primary else colors.surfaceContainer)
                    .clickable { onToggle(bit) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initials[bit],
                    fontFamily = SatoshiFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (selected) Color.White else colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TimeField(
    label: String,
    minuteOfDay: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LockTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.cardContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            fontFamily = SatoshiFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            color = colors.onSurfaceVariant,
        )
        Text(
            text = formatMinute(minuteOfDay),
            fontFamily = SatoshiFamily,
            fontWeight = FontWeight.Black,
            fontSize = 24.sp,
            color = colors.onSurface,
        )
    }
}

@Composable
private fun OvernightBanner(endText: String) {
    val colors = LockTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.primary.copy(alpha = 0.1f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = null,
            tint = colors.primaryDark,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = stringResource(R.string.schedule_overnight_hint, endText),
            fontFamily = SatoshiFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            color = colors.primaryDark,
        )
    }
}

@Composable
private fun ProfileRow(name: String, appCount: Int, onRemove: () -> Unit) {
    val colors = LockTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.cardContainer)
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(colors.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Shield,
                contentDescription = null,
                tint = colors.primaryDark,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = name,
                fontFamily = SatoshiFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = colors.onSurface,
            )
            Text(
                text = stringResource(R.string.schedule_profile_apps, appCount),
                fontFamily = SatoshiFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                color = colors.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = stringResource(R.string.action_cancel),
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun TimePickerSheet(
    title: String,
    minuteOfDay: Int,
    onDone: (Int) -> Unit,
) {
    val colors = LockTheme.colors
    val state = rememberTimePickerState(
        initialHour = minuteOfDay / 60,
        initialMinute = minuteOfDay % 60,
        is24Hour = true,
    )
    LockBottomSheet(
        // Dismissing by tapping outside (or swiping down) also keeps the picked time.
        onDismiss = { onDone(state.hour * 60 + state.minute) },
        title = title,
        body = "",
    ) {
        TimePicker(state = state)
        Button(
            onClick = { onDone(state.hour * 60 + state.minute) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = Color.White),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(
                text = stringResource(R.string.schedule_save),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ProfilePickerSheet(
    profiles: List<com.nathanb.lock.data.model.Profile>,
    selectedIds: List<Long>,
    onToggle: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LockTheme.colors
    LockBottomSheet(
        onDismiss = onDismiss,
        title = stringResource(R.string.schedule_picker_title),
        body = if (profiles.isEmpty()) stringResource(R.string.schedule_picker_empty)
        else stringResource(R.string.schedule_profiles_hint),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            profiles.forEach { profile ->
                val selected = profile.id in selectedIds
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (selected) colors.primary.copy(alpha = 0.12f)
                            else colors.surfaceContainerHigh,
                        )
                        .clickable { onToggle(profile.id) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = profile.name,
                            fontFamily = SatoshiFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = colors.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.schedule_profile_apps, profile.blockedPackages.size),
                            fontFamily = SatoshiFamily,
                            fontWeight = FontWeight.Normal,
                            fontSize = 12.sp,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    if (selected) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = null,
                            tint = colors.primaryDark,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = Color.White),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(
                text = stringResource(R.string.action_done),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
