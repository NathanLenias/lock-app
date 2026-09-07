package com.nathanb.lock.ui.screens.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.res.stringResource
import com.nathanb.lock.R
import com.nathanb.lock.ui.theme.LockTheme
import com.nathanb.lock.util.PermissionHelper

@Composable
fun PermissionsScreen(
    onBack: () -> Unit,
) {
    val colors = LockTheme.colors
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var accessibilityOk by remember { mutableStateOf(PermissionHelper.isAccessibilityServiceEnabled(context)) }
    var batteryOk by remember { mutableStateOf(PermissionHelper.isBatteryOptimizationIgnored(context)) }
    var overlayOk by remember { mutableStateOf(PermissionHelper.canDrawOverlays(context)) }
    var notificationsOk by remember { mutableStateOf(PermissionHelper.areNotificationsEnabled(context)) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationsOk = granted
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            accessibilityOk = PermissionHelper.isAccessibilityServiceEnabled(context)
            batteryOk = PermissionHelper.isBatteryOptimizationIgnored(context)
            overlayOk = PermissionHelper.canDrawOverlays(context)
            notificationsOk = PermissionHelper.areNotificationsEnabled(context)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .padding(horizontal = 20.dp),
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
                text = stringResource(R.string.permissions_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = colors.onSurface,
                letterSpacing = (-0.5).sp,
            )
        }

        Spacer(Modifier.height(22.dp))

        // Permissions card
        SettingsCard {
            PermissionRow(
                icon = Icons.Outlined.Accessibility,
                title = stringResource(R.string.permissions_accessibility_title),
                // After a process kill ("Active apps" > Stop, force stop) the Android toggle can
                // still read as enabled while the service is unbound: say how to get it back.
                subtitle = if (accessibilityOk) {
                    stringResource(R.string.permissions_accessibility_subtitle)
                } else {
                    stringResource(R.string.accessibility_reenable_hint)
                },
                isGranted = accessibilityOk,
                onClick = { PermissionHelper.openAccessibilitySettings(context) },
            )

            SettingsDivider()

            PermissionRow(
                icon = Icons.Outlined.BatterySaver,
                title = stringResource(R.string.permissions_battery_title),
                subtitle = stringResource(R.string.permissions_battery_subtitle),
                isGranted = batteryOk,
                onClick = { PermissionHelper.requestIgnoreBatteryOptimization(context) },
            )

            SettingsDivider()

            PermissionRow(
                icon = Icons.Outlined.Layers,
                title = stringResource(R.string.permissions_overlay_title),
                subtitle = stringResource(R.string.permissions_overlay_subtitle),
                isGranted = overlayOk,
                onClick = { PermissionHelper.openOverlaySettings(context) },
            )
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isGranted: Boolean,
    onClick: () -> Unit,
) {
    val colors = LockTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.primary.copy(alpha = 0.07f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(20.dp),
            )
        }

        // Text
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }

        // Status badge
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (isGranted) colors.primary.copy(alpha = 0.09f)
                    else colors.error.copy(alpha = 0.09f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Outlined.CheckCircle else Icons.Outlined.Warning,
                contentDescription = null,
                tint = if (isGranted) colors.primary else colors.error,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
