package com.nathanb.lock.ui.screens.schedules

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nathanb.lock.R
import com.nathanb.lock.data.model.Profile
import com.nathanb.lock.data.model.Schedule
import com.nathanb.lock.ui.components.LockBottomSheet
import com.nathanb.lock.ui.theme.LockTheme
import com.nathanb.lock.ui.theme.SatoshiFamily
import com.nathanb.lock.ui.viewmodel.LockViewModel
import com.nathanb.lock.util.PermissionHelper

/** "L M M · 09:00 – 17:00" style summary pieces shared with the edit screen. */
internal fun formatMinute(minuteOfDay: Int): String =
    "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

internal fun daySummary(daysOfWeek: Int, initials: Array<String>, everyDay: String): String {
    if (daysOfWeek == 0b1111111) return everyDay
    return (0..6).filter { daysOfWeek and (1 shl it) != 0 }.joinToString(" ") { initials[it] }
}

@Composable
fun SchedulesListScreen(
    viewModel: LockViewModel,
    onBack: () -> Unit,
    onNavigateToEdit: (Long) -> Unit,
) {
    val colors = LockTheme.colors
    val context = LocalContext.current
    val schedules by viewModel.schedules.collectAsStateWithLifecycle()
    val links by viewModel.scheduleLinks.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    var showAlarmPermSheet by remember { mutableStateOf(false) }

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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.schedules_title),
                        fontFamily = SatoshiFamily,
                        fontWeight = FontWeight.Black,
                        fontSize = 24.sp,
                        color = colors.onSurface,
                        letterSpacing = (-0.5).sp,
                    )
                    Text(
                        text = stringResource(R.string.schedules_subtitle),
                        fontFamily = SatoshiFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.sp,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        },
        bottomBar = {
            Button(
                onClick = { onNavigateToEdit(-1L) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .navigationBarsPadding()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Text(
                    text = stringResource(R.string.schedules_new),
                    fontFamily = SatoshiFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White,
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            if (schedules.isEmpty()) {
                EmptyState()
            }

            schedules.forEach { schedule ->
                val linkedProfiles = links
                    .filter { it.scheduleId == schedule.id }
                    .mapNotNull { link -> profiles.find { it.id == link.profileId } }
                ScheduleCard(
                    schedule = schedule,
                    linkedProfiles = linkedProfiles,
                    onClick = { onNavigateToEdit(schedule.id) },
                    onToggle = { enabled ->
                        if (enabled && !PermissionHelper.canScheduleExactAlarms(context)) {
                            showAlarmPermSheet = true
                        }
                        viewModel.setScheduleEnabled(schedule.id, enabled)
                    },
                )
            }

            Spacer(Modifier.height(6.dp))
        }
    }

    if (showAlarmPermSheet) {
        AlarmPermissionSheet(onDismiss = { showAlarmPermSheet = false })
    }
}

@Composable
private fun EmptyState() {
    val colors = LockTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.cardContainer)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(colors.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.CalendarMonth,
                contentDescription = null,
                tint = colors.primaryDark,
                modifier = Modifier.size(26.dp),
            )
        }
        Text(
            text = stringResource(R.string.schedules_empty_title),
            fontFamily = SatoshiFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = colors.onSurface,
        )
        Text(
            text = stringResource(R.string.schedules_empty_body),
            fontFamily = SatoshiFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ScheduleCard(
    schedule: Schedule,
    linkedProfiles: List<Profile>,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val colors = LockTheme.colors
    val initials = stringArrayResource(R.array.schedule_day_initials)
    val overnight = !schedule.allDay && schedule.endMinuteOfDay <= schedule.startMinuteOfDay
    val timeText = if (schedule.allDay) {
        stringResource(R.string.schedule_all_day)
    } else {
        stringResource(
            if (overnight) R.string.schedule_time_range_overnight else R.string.schedule_time_range,
            formatMinute(schedule.startMinuteOfDay),
            formatMinute(schedule.endMinuteOfDay),
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = daySummary(schedule.daysOfWeek, initials, stringResource(R.string.schedule_every_day)),
                        fontFamily = SatoshiFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = colors.onSurface,
                    )
                    Text(
                        text = timeText,
                        fontFamily = SatoshiFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.sp,
                        color = colors.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = schedule.enabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.scale(0.8f),
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = colors.primary,
                        checkedThumbColor = colors.cardContainer,
                    ),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (linkedProfiles.isEmpty()) {
                    ScheduleChip(
                        text = stringResource(R.string.schedule_no_profile),
                        textColor = Color(0xFFEF6C00),
                        bgColor = Color(0xFFFFF1E6),
                    )
                } else {
                    linkedProfiles.take(3).forEach { profile ->
                        ScheduleChip(
                            text = profile.name,
                            textColor = colors.primaryDark,
                            bgColor = colors.primary.copy(alpha = 0.12f),
                        )
                    }
                    if (linkedProfiles.size > 3) {
                        ScheduleChip(
                            text = "+${linkedProfiles.size - 3}",
                            textColor = colors.onSurfaceVariant,
                            bgColor = colors.surfaceContainer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ScheduleChip(text: String, textColor: Color, bgColor: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            fontFamily = SatoshiFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            color = textColor,
        )
    }
}

@Composable
internal fun AlarmPermissionSheet(onDismiss: () -> Unit) {
    val colors = LockTheme.colors
    val context = LocalContext.current
    LockBottomSheet(
        onDismiss = onDismiss,
        title = stringResource(R.string.schedule_alarm_perm_title),
        body = stringResource(R.string.schedule_alarm_perm_body),
        icon = Icons.Outlined.Alarm,
    ) {
        Button(
            onClick = {
                PermissionHelper.openExactAlarmSettings(context)
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = Color.White),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(
                text = stringResource(R.string.onboarding_perm_open_settings),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        androidx.compose.material3.TextButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.schedule_alarm_perm_later),
                fontFamily = SatoshiFamily,
                fontSize = 14.sp,
                color = colors.onSurfaceVariant,
            )
        }
    }
}
