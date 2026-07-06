package com.nathanb.lock.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.nathanb.lock.R
import com.nathanb.lock.util.PermissionHelper
import com.nathanb.lock.data.model.ProfileType
import com.nathanb.lock.ui.components.LockBottomSheet
import com.nathanb.lock.ui.screens.profile.DURATION_OPTIONS_MS
import com.nathanb.lock.ui.screens.profile.DurationSelector
import com.nathanb.lock.ui.screens.profile.TypeCard
import com.nathanb.lock.ui.theme.BrickTheme
import com.nathanb.lock.ui.theme.SatoshiFamily
import com.nathanb.lock.ui.viewmodel.LockViewModel
import kotlinx.coroutines.launch

@Composable
fun ProfileDetailScreen(
    viewModel: LockViewModel,
    profileId: Long,
    onBack: () -> Unit,
    onEditApps: (Long) -> Unit,
    onAssociateTag: (Long) -> Unit,
) {
    val colors = BrickTheme.colors
    val profiles by viewModel.profilesSorted.collectAsStateWithLifecycle()
    val tags by viewModel.nfcTags.collectAsStateWithLifecycle()
    val profile = profiles.find { it.id == profileId }

    // Profile gone (deleted) -> leave.
    LaunchedEffect(profile == null) {
        if (profile == null) onBack()
    }
    if (profile == null) return

    val isNoEscape = ProfileType.fromValue(profile.type) == ProfileType.NO_ESCAPE
    val durationMs = profile.durationMs ?: DURATION_OPTIONS_MS[2] // default 15 min
    val durationMinutes = (durationMs / 60_000L).toInt()
    // A tag with no explicit profile (profileId == null) resolves to the default profile at
    // runtime, so show it under the default profile's detail too.
    val defaultId = profiles.firstOrNull { it.isDefault }?.id
    val profileTags = tags.filter { (it.profileId ?: defaultId) == profileId }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showSeConfirm by remember { mutableStateOf(false) }
    var showManualInfo by remember { mutableStateOf(false) }
    var showDefaultInfo by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showPermRequired by remember { mutableStateOf(false) }
    val hasAnyTag = tags.isNotEmpty()

    // Accessibility is required for blocking to actually work; re-check on resume.
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var accessibilityOk by remember { mutableStateOf(PermissionHelper.isAccessibilityServiceEnabled(context)) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            accessibilityOk = PermissionHelper.isAccessibilityServiceEnabled(context)
        }
    }

    val blockedDefaultMsg = stringResource(R.string.profile_delete_blocked_default)
    val blockedActiveMsg = stringResource(R.string.profile_delete_blocked_active)

    LaunchedEffect(Unit) {
        viewModel.profileEvents.collect { event ->
            when (event) {
                is LockViewModel.ProfileEvent.Deleted -> onBack()
                is LockViewModel.ProfileEvent.BlockedDefault ->
                    scope.launch { snackbarHostState.showSnackbar(blockedDefaultMsg) }
                is LockViewModel.ProfileEvent.BlockedActiveSession ->
                    scope.launch { snackbarHostState.showSnackbar(blockedActiveMsg) }
            }
        }
    }

    Scaffold(
        containerColor = colors.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
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
                        text = profile.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontFamily = SatoshiFamily,
                        fontWeight = FontWeight.Black,
                        color = colors.onSurface,
                        letterSpacing = (-0.5).sp,
                        maxLines = 1,
                    )
                    Text(
                        text = stringResource(
                            if (isNoEscape) R.string.profile_header_subtitle_no_escape
                            else R.string.profile_header_subtitle_standard,
                        ),
                        fontFamily = SatoshiFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.sp,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colors.cardContainer)
                        .clickable { showRename = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = stringResource(R.string.profile_rename),
                        tint = colors.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Button(
                    onClick = {
                        when {
                            !accessibilityOk -> showPermRequired = true
                            isNoEscape -> showSeConfirm = true
                            !hasAnyTag -> showManualInfo = true
                            else -> viewModel.startProfile(profileId)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                ) {
                    Text(
                        text = if (isNoEscape) {
                            stringResource(R.string.profile_start_with_duration, durationMinutes)
                        } else {
                            stringResource(R.string.profile_start)
                        },
                        fontFamily = SatoshiFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White,
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            TypeCard(isNoEscape = isNoEscape)

            // Apps section
            SectionLabel(stringResource(R.string.app_picker_title))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.cardContainer)
                    .clickable { onEditApps(profileId) }
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val count = profile.blockedPackages.size
                    Text(
                        text = if (isNoEscape) {
                            stringResource(R.string.profile_apps_count_inaccessible, count)
                        } else {
                            stringResource(R.string.profile_apps_count_blocked, count)
                        },
                        fontFamily = SatoshiFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = colors.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(R.string.action_edit),
                        fontFamily = SatoshiFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = colors.primary,
                    )
                }
                val icons = profile.blockedPackages.take(8).mapNotNull { viewModel.getAppIcon(it) }
                if (icons.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        icons.forEach { bmp ->
                            Image(bitmap = bmp, contentDescription = null, modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }

            // Duration (no-escape only)
            if (isNoEscape) {
                SectionLabel(stringResource(R.string.profile_duration_title))
                DurationSelector(
                    selectedMs = durationMs,
                    onSelect = { viewModel.setProfileDuration(profileId, it) },
                )
            }

            // Default toggle (standard) or info (no-escape)
            if (isNoEscape) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surfaceContainer)
                        .clickable { showDefaultInfo = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(R.string.profile_default_locked_info),
                        fontFamily = SatoshiFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.sp,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.cardContainer)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.profile_default_toggle),
                            fontFamily = SatoshiFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = colors.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.profile_default_toggle_subtitle),
                            fontFamily = SatoshiFamily,
                            fontWeight = FontWeight.Normal,
                            fontSize = 13.sp,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = profile.isDefault,
                        // Enabled so the active state stays green; turning OFF is a no-op
                        // (you change the default by enabling it on another profile).
                        onCheckedChange = { if (it) viewModel.setDefaultProfile(profileId) },
                        modifier = Modifier.scale(0.8f),
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = colors.primary,
                            checkedThumbColor = colors.cardContainer,
                        ),
                    )
                }
            }

            // Tag association
            SectionLabel(stringResource(R.string.nfc_tags_title))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.cardContainer),
            ) {
                if (profileTags.isEmpty()) {
                    Text(
                        text = stringResource(R.string.profile_no_tag),
                        fontFamily = SatoshiFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 14.sp,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                } else {
                    profileTags.forEach { tag ->
                        AssociatedTagRow(name = tag.name)
                    }
                }
                HorizontalDivider(color = colors.onSurfaceVariant.copy(alpha = 0.08f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAssociateTag(profileId) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(
                            if (profileTags.isEmpty()) R.string.profile_associate_tag
                            else R.string.nfc_associate_another,
                        ),
                        fontFamily = SatoshiFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = colors.primary,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            // No-escape warning
            if (isNoEscape) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.lockedPrimary.copy(alpha = 0.08f))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        Icons.Outlined.Timer,
                        contentDescription = null,
                        tint = colors.lockedPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(R.string.profile_se_confirm_body, durationMinutes),
                        fontFamily = SatoshiFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.sp,
                        color = colors.lockedPrimary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Delete
            TextButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    tint = colors.lockedPrimary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.profile_delete),
                    fontFamily = SatoshiFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = colors.lockedPrimary,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    if (showSeConfirm) {
        LockBottomSheet(
            onDismiss = { showSeConfirm = false },
            icon = Icons.Outlined.Timer,
            title = stringResource(R.string.profile_se_confirm_title),
            body = stringResource(R.string.profile_se_confirm_body, durationMinutes),
            actions = {
                Button(
                    onClick = {
                        viewModel.startProfile(profileId)
                        showSeConfirm = false
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                ) {
                    Text(
                        text = stringResource(R.string.profile_start_with_duration, durationMinutes),
                        fontFamily = SatoshiFamily,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { showSeConfirm = false },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        fontFamily = SatoshiFamily,
                        color = colors.onSurfaceVariant,
                    )
                }
            },
        )
    }

    if (showDeleteConfirm) {
        LockBottomSheet(
            onDismiss = { showDeleteConfirm = false },
            icon = Icons.Outlined.DeleteOutline,
            title = stringResource(R.string.profile_delete_confirm_title),
            body = stringResource(R.string.profile_delete_confirm_body, profile.name),
            actions = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteProfile(profileId)
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.lockedPrimary),
                ) {
                    Text(
                        text = stringResource(R.string.profile_delete_confirm_action),
                        fontFamily = SatoshiFamily,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { showDeleteConfirm = false },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        fontFamily = SatoshiFamily,
                        color = colors.onSurfaceVariant,
                    )
                }
            },
        )
    }

    if (showManualInfo) {
        LockBottomSheet(
            onDismiss = { showManualInfo = false },
            icon = Icons.Outlined.Info,
            title = stringResource(R.string.profile_manual_mode_title),
            body = stringResource(R.string.profile_manual_mode_body),
            actions = {
                Button(
                    onClick = {
                        viewModel.startProfile(profileId)
                        showManualInfo = false
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                ) {
                    Text(
                        text = stringResource(R.string.profile_start),
                        fontFamily = SatoshiFamily,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { showManualInfo = false },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        fontFamily = SatoshiFamily,
                        color = colors.onSurfaceVariant,
                    )
                }
            },
        )
    }

    if (showDefaultInfo) {
        LockBottomSheet(
            onDismiss = { showDefaultInfo = false },
            icon = Icons.Outlined.Info,
            title = stringResource(R.string.profile_default_toggle),
            body = stringResource(R.string.profile_default_locked_info),
        )
    }

    if (showPermRequired) {
        LockBottomSheet(
            onDismiss = { showPermRequired = false },
            icon = Icons.Outlined.Info,
            title = stringResource(R.string.profile_perm_required_title),
            body = stringResource(R.string.profile_perm_required_body),
            actions = {
                Button(
                    onClick = {
                        PermissionHelper.openAccessibilitySettings(context)
                        showPermRequired = false
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                ) {
                    Text(
                        text = stringResource(R.string.profile_perm_required_cta),
                        fontFamily = SatoshiFamily,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { showPermRequired = false },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        fontFamily = SatoshiFamily,
                        color = colors.onSurfaceVariant,
                    )
                }
            },
        )
    }

    if (showRename) {
        var newName by remember { mutableStateOf(profile.name) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = {
                Text(
                    stringResource(R.string.profile_rename),
                    fontFamily = SatoshiFamily,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = newName.trim()
                    if (trimmed.isNotEmpty()) viewModel.renameProfile(profileId, trimmed)
                    showRename = false
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            containerColor = colors.surfaceContainer,
        )
    }
}

@Composable
private fun AssociatedTagRow(name: String) {
    val colors = BrickTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFE8F5E9)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Nfc,
                contentDescription = null,
                tint = colors.primaryDark,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = name,
                fontFamily = SatoshiFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = colors.onSurface,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(colors.primary))
                Text(
                    text = stringResource(R.string.tag_status_associated),
                    fontFamily = SatoshiFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 13.sp,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp),
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
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        color = BrickTheme.colors.onSurfaceVariant,
    )
}
