package com.nathanb.lock.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.res.stringResource
import com.nathanb.lock.BuildConfig
import com.nathanb.lock.R
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import com.nathanb.lock.ui.components.LockBottomSheet
import com.nathanb.lock.ui.components.SupportPill
import com.nathanb.lock.ui.components.SupportBottomSheet
import com.nathanb.lock.ui.screens.settings.BreathingBottomSheet
import com.nathanb.lock.ui.screens.settings.ProfileCard
import com.nathanb.lock.ui.screens.settings.AllProfilesCard
import com.nathanb.lock.ui.screens.settings.SectionHeader
import com.nathanb.lock.ui.screens.settings.SettingsCard
import com.nathanb.lock.ui.screens.settings.SettingsDivider
import com.nathanb.lock.ui.screens.settings.SettingsRow
import com.nathanb.lock.ui.screens.settings.ThemeSelector
import com.nathanb.lock.ui.theme.LockTheme
import com.nathanb.lock.ui.theme.SatoshiFamily
import com.nathanb.lock.ui.theme.ThemeMode
import com.nathanb.lock.ui.viewmodel.LockViewModel
import com.nathanb.lock.util.Constants
import com.nathanb.lock.util.RateHelper
import com.nathanb.lock.util.PermissionHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: LockViewModel,
    onNavigateToProfileDetail: (Long) -> Unit,
    onNavigateToProfiles: () -> Unit,
    onNavigateToNfcTags: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToData: () -> Unit,
    onNavigateToSessionSettings: () -> Unit = {},
    onNavigateToSchedules: () -> Unit = {},
) {
    val colors = LockTheme.colors
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val sortedProfiles by viewModel.profilesSorted.collectAsStateWithLifecycle()
    val nfcTags by viewModel.nfcTags.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsStateWithLifecycle()
    val appIconBitmaps by viewModel.blockedAppIcons.collectAsStateWithLifecycle()
    val profile = profiles.firstOrNull()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var overlayOk by remember { mutableStateOf(PermissionHelper.canDrawOverlays(context)) }
    var accessibilityOk by remember { mutableStateOf(PermissionHelper.isAccessibilityServiceEnabled(context)) }
    var showThemeSheet by remember { mutableStateOf(false) }
    var showBreathingSheet by remember { mutableStateOf(false) }
    var showSupportSheet by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.setNotificationsEnabled(context, true)
        }
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            overlayOk = PermissionHelper.canDrawOverlays(context)
            accessibilityOk = PermissionHelper.isAccessibilityServiceEnabled(context)
        }
    }

    // Pre-load installed apps cache so AppPicker opens instantly
    LaunchedEffect(Unit) {
        viewModel.ensureInstalledAppsLoaded()
    }

    val blockedPackages = profile?.blockedPackages.orEmpty()
    val allPermissionsOk = overlayOk && accessibilityOk

    val themeModeLabel = when (themeMode) {
        ThemeMode.LIGHT -> stringResource(R.string.theme_light)
        ThemeMode.DARK -> stringResource(R.string.theme_dark)
        ThemeMode.SYSTEM -> stringResource(R.string.theme_auto)
    }

    Scaffold(
        containerColor = colors.surface,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 100.dp),
        ) {
            // --- Title Row: "Réglages" + Support pill ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.settings_title),
                    fontFamily = SatoshiFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    color = colors.onSurface,
                )

                // Support pill
                SupportPill(onClick = { showSupportSheet = true })
            }

            Spacer(Modifier.height(12.dp))

            // --- Content wrapper ---
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // --- Profils : hero (défaut) + entrée "Tous les profils" ---
                SectionHeader(stringResource(R.string.profiles_section_title))
                val heroProfile = sortedProfiles.firstOrNull()
                val heroIcons = heroProfile?.blockedPackages?.take(5)
                    ?.mapNotNull { viewModel.getAppIcon(it) } ?: emptyList()
                if (heroProfile != null) {
                    ProfileCard(
                        profile = heroProfile,
                        appIconBitmaps = heroIcons,
                        onClick = { onNavigateToProfileDetail(heroProfile.id) },
                    )
                }
                AllProfilesCard(
                    profiles = sortedProfiles,
                    defaultName = heroProfile?.name.orEmpty(),
                    onClick = onNavigateToProfiles,
                )
                val schedules by viewModel.schedules.collectAsStateWithLifecycle()
                val activeScheduleCount = schedules.count { it.enabled }
                SettingsCard {
                    SettingsRow(
                        icon = Icons.Outlined.CalendarMonth,
                        title = stringResource(R.string.schedule_settings_entry),
                        subtitle = stringResource(R.string.schedule_settings_subtitle),
                        onClick = onNavigateToSchedules,
                        trailing = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                if (activeScheduleCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(colors.primary.copy(alpha = 0.12f))
                                            .padding(horizontal = 8.dp, vertical = 3.dp),
                                    ) {
                                        Text(
                                            text = activeScheduleCount.toString(),
                                            fontFamily = SatoshiFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            color = colors.primaryDark,
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = colors.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        },
                    )
                }

                // --- 2x2 Action Cards Grid ---
                // Row 1: Tags NFC + Session
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ActionCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.Nfc,
                        title = stringResource(R.string.settings_nfc_tags),
                        subtitle = stringResource(R.string.settings_nfc_tags_count, nfcTags.size, if (nfcTags.size > 1) "s" else "", if (nfcTags.size > 1) "s" else ""),
                        badge = "${nfcTags.size}",
                        onClick = onNavigateToNfcTags,
                    )
                    ActionCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.Timer,
                        title = stringResource(R.string.settings_session),
                        subtitle = stringResource(R.string.settings_session_subtitle),
                        showChevron = true,
                        onClick = onNavigateToSessionSettings,
                    )
                }

                // Row 2: Apparence + Notifications
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ActionCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.LightMode,
                        title = stringResource(R.string.settings_appearance),
                        subtitle = themeModeLabel,
                        showChevron = true,
                        onClick = { showThemeSheet = true },
                    )
                    ActionCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.Notifications,
                        title = stringResource(R.string.settings_notifications),
                        subtitle = if (notificationsEnabled) stringResource(R.string.settings_notifications_on) else stringResource(R.string.settings_notifications_off),
                        subtitleColor = if (notificationsEnabled) colors.primary else colors.onSurfaceVariant,
                        trailing = {
                            Switch(
                                checked = notificationsEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                                        && !PermissionHelper.areNotificationsEnabled(context)
                                    ) {
                                        notificationPermissionLauncher.launch(
                                            Manifest.permission.POST_NOTIFICATIONS,
                                        )
                                    } else {
                                        viewModel.setNotificationsEnabled(context, enabled)
                                    }
                                },
                                modifier = Modifier.scale(0.8f),
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = colors.primary,
                                    checkedThumbColor = colors.cardContainer,
                                ),
                            )
                        },
                    )
                }

                // --- SYSTÈME section label ---
                Text(
                    text = stringResource(R.string.settings_system),
                    fontFamily = SatoshiFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )

                // --- System Cards ---
                SettingsCard {
                    SettingsRow(
                        icon = Icons.Outlined.VerifiedUser,
                        title = stringResource(R.string.settings_permissions),
                        subtitle = if (allPermissionsOk) {
                            stringResource(R.string.settings_permissions_ok)
                        } else {
                            buildString {
                                val missing = mutableListOf<String>()
                                if (!accessibilityOk) missing += stringResource(R.string.permissions_missing_accessibility)
                                if (!overlayOk) missing += stringResource(R.string.permissions_missing_overlay)
                                append(missing.joinToString(", ") + " " + stringResource(R.string.permissions_missing_suffix))
                            }
                        },
                        subtitleColor = if (allPermissionsOk) colors.primary else colors.error,
                        onClick = onNavigateToPermissions,
                    )
                }

                SettingsCard {
                    SettingsRow(
                        icon = Icons.Outlined.Storage,
                        title = stringResource(R.string.settings_data),
                        onClick = onNavigateToData,
                    )
                }

                SettingsCard {
                    SettingsRow(
                        icon = Icons.Outlined.SelfImprovement,
                        title = stringResource(R.string.settings_breathing),
                        onClick = { showBreathingSheet = true },
                    )
                }

                SettingsCard {
                    SettingsRow(
                        icon = Icons.Outlined.PrivacyTip,
                        title = stringResource(R.string.settings_privacy),
                        onClick = {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(Constants.PRIVACY_URL),
                            )
                            context.startActivity(intent)
                        },
                        trailing = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                contentDescription = null,
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }

                // --- À propos (standalone, no card) ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = stringResource(R.string.settings_about),
                            fontFamily = SatoshiFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp,
                            color = colors.onSurface,
                        )
                        Text(
                            text = "v${BuildConfig.VERSION_NAME}",
                            fontFamily = SatoshiFamily,
                            fontWeight = FontWeight.Normal,
                            fontSize = 13.sp,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }

                // --- Nous noter (standalone, no card) ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clickable {
                            RateHelper.openPlayStore(context)
                        }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFFFF8E1)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Star,
                            contentDescription = null,
                            tint = Color(0xFFF5A623),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings_rate),
                        fontFamily = SatoshiFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp,
                        color = colors.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }

    // --- Apparence Bottom Sheet ---
    if (showThemeSheet) {
        ModalBottomSheet(
            onDismissRequest = { showThemeSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.surfaceContainer,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 14.dp, bottom = 10.dp)
                        .size(width = 40.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.onSurface.copy(alpha = 0.15f)),
                )
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 16.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.settings_appearance),
                    fontFamily = SatoshiFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(20.dp))
                ThemeSelector(
                    currentMode = themeMode,
                    onModeSelected = { viewModel.setThemeMode(it) },
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // --- Breathing Easter Egg Bottom Sheet ---
    if (showBreathingSheet) {
        BreathingBottomSheet(onDismiss = { showBreathingSheet = false })
    }

    // --- Support Bottom Sheet ---
    if (showSupportSheet) {
        SupportBottomSheet(onDismiss = { showSupportSheet = false })
    }
}

// --- Action Card (for 2x2 grid) ---

@Composable
private fun ActionCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    subtitleColor: Color = LockTheme.colors.onSurfaceVariant,
    badge: String? = null,
    showChevron: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = LockTheme.colors

    Card(
        modifier = modifier
            .height(130.dp)
            .shadow(
                elevation = 3.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = Color.Black.copy(alpha = 0.04f),
                spotColor = Color.Black.copy(alpha = 0.04f),
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        onClick = onClick ?: {},
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Header: icon + badge/chevron/trailing
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Icon box
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }

                // Right side
                when {
                    trailing != null -> trailing()
                    badge != null -> {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.primary.copy(alpha = 0.1f))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = badge,
                                fontFamily = SatoshiFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = colors.primary,
                            )
                        }
                    }
                    showChevron -> {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            // Title + Subtitle at bottom
            Column {
                Text(
                    text = title,
                    fontFamily = SatoshiFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = colors.onSurface,
                )

                Spacer(Modifier.height(2.dp))

                Text(
                    text = subtitle,
                    fontFamily = SatoshiFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 13.sp,
                    color = subtitleColor,
                )
            }
        }
    }
}
