package com.nathanb.lock.ui.screens.onboarding

import android.content.Context
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.nathanb.lock.R
import com.nathanb.lock.ui.components.LockBottomSheet
import com.nathanb.lock.ui.theme.LockTheme
import com.nathanb.lock.util.PermissionHelper

@Composable
internal fun PermissionsPage(
    onNext: () -> Unit,
) {
    val colors = LockTheme.colors
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var accessibilityOk by remember { mutableStateOf(PermissionHelper.isAccessibilityServiceEnabled(context)) }
    var overlayOk by remember { mutableStateOf(PermissionHelper.canDrawOverlays(context)) }
    var batteryOk by remember { mutableStateOf(PermissionHelper.isBatteryOptimizationIgnored(context)) }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            accessibilityOk = PermissionHelper.isAccessibilityServiceEnabled(context)
            overlayOk = PermissionHelper.canDrawOverlays(context)
            batteryOk = PermissionHelper.isBatteryOptimizationIgnored(context)
        }
    }

    var sheetPermission by remember { mutableStateOf<PermissionInfo?>(null) }

    val permissions = remember {
        listOf(
            PermissionInfo(
                icon = Icons.Rounded.Accessibility,
                titleRes = R.string.onboarding_perm_accessibility_title,
                descRes = R.string.onboarding_perm_accessibility_desc,
                detailRes = R.string.onboarding_perm_accessibility_detail,
                hintRes = R.string.accessibility_reenable_hint,
                isGranted = { accessibilityOk },
                openSettings = { PermissionHelper.openAccessibilitySettings(it) },
            ),
            PermissionInfo(
                icon = Icons.Rounded.Layers,
                titleRes = R.string.onboarding_perm_overlay_title,
                descRes = R.string.onboarding_perm_overlay_desc,
                detailRes = R.string.onboarding_perm_overlay_detail,
                isGranted = { overlayOk },
                openSettings = { PermissionHelper.openOverlaySettings(it) },
            ),
            PermissionInfo(
                icon = Icons.Rounded.BatteryChargingFull,
                titleRes = R.string.onboarding_perm_battery_title,
                descRes = R.string.onboarding_perm_battery_desc,
                detailRes = R.string.onboarding_perm_battery_detail,
                isGranted = { batteryOk },
                openSettings = { PermissionHelper.requestIgnoreBatteryOptimization(it) },
            ),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Scrollable content — the CTA below stays pinned and reachable even
        // when large font scales or verbose locales make this overflow.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(80.dp))

            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(colors.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = Color.White,
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.onboarding_perm_title),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.onboarding_perm_subtitle),
                fontSize = 14.sp,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(28.dp))

            permissions.forEach { perm ->
                PermissionCard(
                    icon = perm.icon,
                    title = stringResource(perm.titleRes),
                    description = stringResource(perm.descRes),
                    isGranted = perm.isGranted(),
                    onCardClick = {
                        if (!perm.isGranted()) perm.openSettings(context)
                    },
                    onInfoClick = { sheetPermission = perm },
                )
                Spacer(Modifier.height(12.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        colors.primary.copy(alpha = 0.05f),
                        RoundedCornerShape(12.dp),
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Icon(
                    Icons.Rounded.Info,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = colors.primary.copy(alpha = 0.6f),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.onboarding_perm_info),
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant.copy(alpha = 0.6f),
                    lineHeight = 17.sp,
                )
            }

            Spacer(Modifier.height(24.dp))
        }

        val allGranted = accessibilityOk && overlayOk && batteryOk
        OnboardingButton(
            text = stringResource(
                if (allGranted) R.string.onboarding_perm_cta_done
                else R.string.onboarding_perm_cta,
            ),
            onClick = {
                if (allGranted) {
                    onNext()
                } else {
                    val firstMissing = permissions.firstOrNull { !it.isGranted() }
                    firstMissing?.openSettings(context)
                }
            },
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.onboarding_perm_skip),
            fontSize = 13.sp,
            color = colors.onSurfaceVariant,
            modifier = Modifier.clickable(onClick = onNext),
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = stringResource(R.string.onboarding_perm_note),
            fontSize = 11.sp,
            color = colors.onSurfaceVariant.copy(alpha = 0.5f),
        )

        Spacer(Modifier.height(72.dp))
    }

    sheetPermission?.let { perm ->
        LockBottomSheet(
            onDismiss = { sheetPermission = null },
            title = stringResource(perm.titleRes),
            body = buildString {
                append(stringResource(perm.detailRes))
                val hint = perm.hintRes
                if (hint != null && !perm.isGranted()) append("\n\n").append(stringResource(hint))
            },
            icon = perm.icon,
        ) {
            Button(
                onClick = {
                    perm.openSettings(context)
                    sheetPermission = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    text = stringResource(R.string.onboarding_perm_open_settings),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private data class PermissionInfo(
    val icon: ImageVector,
    val titleRes: Int,
    val descRes: Int,
    val detailRes: Int,
    val isGranted: () -> Boolean,
    /** Extra line shown in the detail sheet while the permission is missing. */
    val hintRes: Int? = null,
    val openSettings: (Context) -> Unit,
)

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    onCardClick: () -> Unit,
    onInfoClick: () -> Unit,
) {
    val colors = LockTheme.colors
    val bgColor by animateColorAsState(
        if (isGranted) colors.primary.copy(alpha = 0.08f) else colors.cardContainer,
        label = "permBg",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(16.dp))
            .clickable(enabled = !isGranted, onClick = onCardClick)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    colors.primary.copy(alpha = 0.1f),
                    RoundedCornerShape(12.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isGranted) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = colors.primary,
                )
            } else {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = colors.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface,
            )
            Text(
                text = description,
                fontSize = 13.sp,
                color = colors.onSurfaceVariant,
            )
        }

        Icon(
            Icons.Rounded.Info,
            contentDescription = null,
            modifier = Modifier
                .size(20.dp)
                .clickable(onClick = onInfoClick),
            tint = colors.onSurfaceVariant,
        )
    }
}
