package com.nathanb.lock.ui.screens

import com.nathanb.lock.ui.components.SupportBottomSheet
import com.nathanb.lock.ui.components.SupportCard
import com.nathanb.lock.ui.components.ChangelogCard
import com.nathanb.lock.ui.components.openSupportPage
import com.nathanb.lock.ui.components.SupportPill
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.nathanb.lock.R
import com.nathanb.lock.data.model.SetupStatus
import com.nathanb.lock.nfc.NdefWriteResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import com.nathanb.lock.ui.components.NfcScanCard
import com.nathanb.lock.ui.screens.home.AnimatedTriangleLogo
import com.nathanb.lock.ui.screens.home.EmergencyBottomSheet
import com.nathanb.lock.ui.screens.home.EmergencyUnlockButton
import com.nathanb.lock.ui.screens.home.GracePeriodIndicator
import com.nathanb.lock.ui.screens.home.ManualLockButton
import com.nathanb.lock.ui.screens.home.ManualOrange
import com.nathanb.lock.ui.screens.home.ManualUnlockBottomSheet
import com.nathanb.lock.ui.screens.home.SetupChecklistSection
import com.nathanb.lock.ui.theme.LockTheme
import com.nathanb.lock.ui.viewmodel.LockViewModel
import com.nathanb.lock.util.PermissionHelper
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    viewModel: LockViewModel,
    onNavigateToApps: () -> Unit = {},
    onNavigateToPermissions: () -> Unit = {},
    onNavigateToNfcTags: () -> Unit = {},
) {
    val colors = LockTheme.colors
    val context = LocalContext.current
    val lockState by viewModel.lockState.collectAsStateWithLifecycle()
    val isEmergencyActive by viewModel.isEmergencyActive.collectAsStateWithLifecycle()
    val emergencyTimeRemaining by viewModel.emergencyTimeRemaining.collectAsStateWithLifecycle()
    val isGracePeriod by viewModel.isGracePeriod.collectAsStateWithLifecycle()
    val graceTimeRemaining by viewModel.graceTimeRemaining.collectAsStateWithLifecycle()
    val gracePeriodMs by viewModel.gracePeriodMs.collectAsStateWithLifecycle()
    val emergencyDurationMs by viewModel.emergencyUnlockDurationMs.collectAsStateWithLifecycle()

    val isLocked = lockState.isLocked
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    // When locked, reflect the ACTIVE session's profile; otherwise the default profile.
    val displayProfile = lockState.activeProfileId?.let { id -> profiles.find { it.id == id } }
        ?: profiles.firstOrNull { it.isDefault } ?: profiles.firstOrNull()
    // Locked count = the set actually enforced (a scheduled session can block the union of
    // several profiles, not just the carrier profile). Profile fallback covers the brief
    // window where the blocked set hasn't been derived yet.
    val liveBlockedPackages by viewModel.liveBlockedPackages.collectAsStateWithLifecycle()
    val profileAppCount = displayProfile?.blockedPackages?.size ?: 0
    val appCount = if (isLocked) maxOf(liveBlockedPackages.size, profileAppCount) else profileAppCount
    val nfcTags by viewModel.nfcTags.collectAsStateWithLifecycle()
    val hasNfcTags = nfcTags.isNotEmpty()
    val completedSessionCount by viewModel.completedSessionCount.collectAsStateWithLifecycle()
    val supportPromptStage by viewModel.supportPromptStage.collectAsStateWithLifecycle()
    val lastSeenVersionCode by viewModel.lastSeenVersionCode.collectAsStateWithLifecycle()
    val pendingUid by viewModel.pendingPairingUid.collectAsStateWithLifecycle()
    val pairingWriteResult by viewModel.pairingWriteResult.collectAsStateWithLifecycle()
    val pairingWriteExhaustedUid by viewModel.pairingWriteExhaustedUid.collectAsStateWithLifecycle()
    var nfcPairingSuccess by remember { mutableStateOf(false) }
    var nfcScanActive by remember { mutableStateOf(false) }
    var showEmergencyDialog by remember { mutableStateOf(false) }
    var showManualUnlockDialog by remember { mutableStateOf(false) }

    val setupStatus = SetupStatus(
        permissionsOk = PermissionHelper.isAccessibilityServiceEnabled(context)
            && PermissionHelper.canDrawOverlays(context),
        hasApps = appCount > 0,
        hasNfcTag = hasNfcTags,
    )

    // Handle NFC pairing from home checklist — only when scan is active
    LaunchedEffect(pendingUid, nfcScanActive) {
        if (pendingUid != null && nfcScanActive) {
            viewModel.confirmPairingWithName(
                pendingUid!!,
                context.getString(R.string.nfc_tags_default_name),
            )
            viewModel.cancelPairing() // consume pendingUid
            nfcPairingSuccess = true
            nfcScanActive = false
        }
    }
    val manualLockFill = remember { Animatable(0f) }

    // Visual state lags behind isLocked — stays "locked" until unlock animation finishes
    val visualLocked by viewModel.visualLocked.collectAsStateWithLifecycle()
    val isUnlocking = !isLocked && visualLocked

    // Elapsed time ticker
    var elapsedMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(lockState.sessionStartTime) {
        val startTime = lockState.sessionStartTime
        if (startTime != null) {
            while (true) {
                elapsedMs = System.currentTimeMillis() - startTime
                delay(1000)
            }
        } else {
            elapsedMs = 0
        }
    }

    // Foreground safety net: the moment a no-escape countdown reaches 0, end the session.
    // Guarantees no visible "stuck at 00:00" state even if the background timers missed it.
    val seExpired = lockState.isLocked && lockState.isNoEscape &&
        (lockState.lockDurationMs?.let { elapsedMs >= it } == true)
    LaunchedEffect(seExpired) {
        if (seExpired) viewModel.endTimedSessionIfExpired()
    }

    val isManualMode = lockState.isManualMode
    // "Soft" lock = unlockable from the app (manual mode, or no NFC tag at all). Orange instead of red.
    // No-escape sessions are always a hard lock.
    val isSoftLock = !lockState.isNoEscape && (isManualMode || !hasNfcTags)

    // Schedule pause: blocked-by-default windows are suspended until this deadline.
    // Same visual language as the emergency pause (neutral background, countdown, resume).
    val schedulePausedUntil by viewModel.schedulePausedUntil.collectAsStateWithLifecycle()
    var pauseRemainingMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(schedulePausedUntil) {
        while (true) {
            pauseRemainingMs = (schedulePausedUntil - System.currentTimeMillis()).coerceAtLeast(0L)
            if (pauseRemainingMs <= 0L) break
            delay(1000)
        }
    }
    val schedulePauseActive = pauseRemainingMs > 0L && !lockState.isLocked

    // Animated background — uses visualLocked so it waits for unlock animation
    val manualLockedBg = if (colors.surface.luminance() < 0.5f) Color(0xFF2A1A08) else Color(0xFFFFF8F0)
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSoftLock && visualLocked && !isUnlocking -> manualLockedBg
            visualLocked && !isUnlocking && !isEmergencyActive -> colors.lockedContainer
            isEmergencyActive -> colors.surfaceContainerHigh
            schedulePauseActive && !visualLocked -> colors.surfaceContainerHigh
            else -> colors.surface
        },
        label = "bgColor",
    )

    val iconScale by animateFloatAsState(
        targetValue = if (visualLocked) 1.2f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "iconScale",
    )

    var showSupportSheet by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
    ) {
        // Short screens: the bottom-anchored actions would overlap the centered content,
        // so below this height they join the column flow instead of floating over it.
        val isCompactHeight = maxHeight < 760.dp
        val isLandscape = maxWidth > maxHeight

        // Support pill (top-right), hidden during an active lock to keep the focus screen clean.
        if (!visualLocked && !isUnlocking) {
            SupportPill(
                onClick = { showSupportSheet = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 12.dp, end = 16.dp),
            )
        }

        if (showSupportSheet) {
            SupportBottomSheet(onDismiss = { showSupportSheet = false })
        }

        // --- In-app cards (support reminder + changelog) — never during an active block,
        // and at most one card per app launch ---
        val inAppCardShown by viewModel.inAppCardShownThisLaunch.collectAsStateWithLifecycle()
        val notLocked = !lockState.isLocked && !visualLocked
        val changelogPending = notLocked &&
            com.nathanb.lock.BuildConfig.VERSION_CODE > lastSeenVersionCode
        val supportThresholds = listOf(3, 10, 20, 30)
        val supportPending = notLocked && !changelogPending && !inAppCardShown &&
            supportPromptStage < supportThresholds.size &&
            completedSessionCount >= supportThresholds[supportPromptStage]

        if (changelogPending) {
            ChangelogCard(
                versionName = com.nathanb.lock.BuildConfig.VERSION_NAME,
                onDiscover = { viewModel.markVersionSeen(com.nathanb.lock.BuildConfig.VERSION_CODE) },
                onDismiss = { viewModel.markVersionSeen(com.nathanb.lock.BuildConfig.VERSION_CODE) },
            )
        } else if (supportPending) {
            SupportCard(
                onSupport = {
                    viewModel.completeSupportPrompt()
                    openSupportPage(context)
                },
                onLater = { viewModel.declineSupportPrompt() },
                onDismiss = { viewModel.declineSupportPrompt() },
            )
        }

        // Bottom actions (lock / emergency unlock / grace cancel). One definition, two
        // placements: floating overlay on regular screens, inline in the scrollable column
        // on compact ones. Callers pass the modifiers for the two padding cases (48/120dp).
        val homeActions: @Composable (Modifier, Modifier) -> Unit = { unlockModifier, lockModifier ->
            // Unlock button (emergency or manual) — never during a no-escape session.
            // Manual unlock is also offered with zero tags so a standard lock is never a trap.
            if (visualLocked && !isUnlocking && !isGracePeriod && !lockState.isNoEscape) {
                if (isManualMode || !hasNfcTags) {
                    EmergencyUnlockButton(
                        remainingUnlocks = 0,
                        onLongPress = { showManualUnlockDialog = true },
                        showRemainingLabel = false,
                        modifier = unlockModifier,
                    )
                } else if (!isEmergencyActive && lockState.emergencyUnlocksRemaining > 0) {
                    EmergencyUnlockButton(
                        remainingUnlocks = lockState.emergencyUnlocksRemaining,
                        onLongPress = { showEmergencyDialog = true },
                        modifier = unlockModifier,
                    )
                }
            }
            if (isGracePeriod) {
                GracePeriodIndicator(
                    graceTimeRemaining = graceTimeRemaining,
                    gracePeriodMs = gracePeriodMs,
                    onCancel = { viewModel.cancelLock() },
                    modifier = unlockModifier,
                )
            }
            if (!visualLocked && !schedulePauseActive && appCount > 0 && hasNfcTags) {
                ManualLockButton(
                    onLock = { viewModel.manualLock() },
                    fillProgress = manualLockFill,
                    modifier = lockModifier,
                )
            }
        }
        val inlineActions: @Composable () -> Unit = {
            if (isCompactHeight) {
                Spacer(Modifier.height(32.dp))
                homeActions(Modifier, Modifier)
                // Clearance below the actions: the floating nav bar overlays the bottom of
                // the screen when unlocked; without it the lock button hides behind the bar.
                Spacer(Modifier.height(if (visualLocked) 24.dp else 104.dp))
            }
        }

        if (!visualLocked && !isManualMode && !setupStatus.isComplete) {
            // Setup incomplete — show checklist
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedTriangleLogo(
                    isLocked = isLocked,
                    iconScale = iconScale,
                    onVisualLockedChange = { viewModel.setVisualLocked(it) },
                    fillProgress = 0f,
                )

                Spacer(Modifier.height(32.dp))

                SetupChecklistSection(
                    setupStatus = setupStatus,
                    appCount = appCount,
                    nfcPairingSuccess = nfcPairingSuccess,
                    writeInterrupted = pairingWriteResult == NdefWriteResult.TRANSIENT_FAILURE,
                    writeExhausted = pairingWriteExhaustedUid != null,
                    onPairAnyway = { viewModel.pairAnywayWithoutWrite() },
                    onNavigateToApps = onNavigateToApps,
                    onNavigateToPermissions = onNavigateToPermissions,
                    onStartNfcScan = {
                        nfcScanActive = true
                        viewModel.enableNfcPairing()
                    },
                    onCancelNfcScan = {
                        nfcScanActive = false
                        viewModel.nfcManager.disablePairingMode()
                        viewModel.clearPairingWriteResult()
                    },
                    onNfcScanSuccess = {
                        nfcPairingSuccess = false
                        viewModel.clearPairingWriteResult()
                    },
                    onActivateManualMode = { viewModel.enableManualMode() },
                )

                inlineActions()
            }
        } else if (isManualMode && !visualLocked) {
            // Manual mode — unlocked
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(80.dp)) // Space for banner

                AnimatedTriangleLogo(
                    isLocked = isLocked,
                    iconScale = iconScale,
                    onVisualLockedChange = { viewModel.setVisualLocked(it) },
                    fillProgress = manualLockFill.value,
                    accentColor = ManualOrange,
                )

                Spacer(Modifier.height(28.dp))

                Text(
                    text = stringResource(R.string.home_status_free),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = ManualOrange,
                    letterSpacing = 1.sp,
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = if (appCount == 1) stringResource(R.string.home_apps_to_block_one, appCount)
                    else stringResource(R.string.home_apps_to_block_many, appCount),
                    fontSize = 15.sp,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(28.dp))

                // NFC scan card (expandable)
                ManualModeNfcNudge(
                    nfcPairingSuccess = nfcPairingSuccess,
                    writeInterrupted = pairingWriteResult == NdefWriteResult.TRANSIENT_FAILURE,
                    writeExhausted = pairingWriteExhaustedUid != null,
                    onPairAnyway = { viewModel.pairAnywayWithoutWrite() },
                    onStartNfcScan = {
                        nfcScanActive = true
                        viewModel.enableNfcPairing()
                    },
                    onCancelNfcScan = {
                        nfcScanActive = false
                        viewModel.nfcManager.disablePairingMode()
                        viewModel.clearPairingWriteResult()
                    },
                    onNfcScanSuccess = {
                        nfcPairingSuccess = false
                        viewModel.clearPairingWriteResult()
                    },
                )

                Spacer(Modifier.height(16.dp))

                // Lock button (same as normal home, orange, no subtitle)
                ManualLockButton(
                    onLock = { viewModel.manualLock() },
                    fillProgress = manualLockFill,
                    showSubtitle = false,
                    accentColor = ManualOrange,
                )

            }
        } else {
            // Normal home content (or manual mode locked — same layout, different colors).
            // Shared pieces, laid out as a column in portrait and side by side in landscape.
            val homeLogo: @Composable () -> Unit = {
                AnimatedTriangleLogo(
                    isLocked = isLocked,
                    iconScale = iconScale,
                    onVisualLockedChange = { viewModel.setVisualLocked(it) },
                    fillProgress = if (!visualLocked && appCount > 0 && hasNfcTags) manualLockFill.value else 0f,
                    accentColor = if (isSoftLock && visualLocked) ManualOrange else null,
                )
            }
            val homeStatus: @Composable () -> Unit = {
                // Status text
                Text(
                    text = when {
                        isEmergencyActive -> stringResource(R.string.home_status_pause)
                        schedulePauseActive -> stringResource(R.string.home_status_pause)
                        isUnlocking -> stringResource(R.string.home_status_unlocking)
                        visualLocked -> if (appCount <= 1) stringResource(R.string.home_status_locked_one, appCount) else stringResource(R.string.home_status_locked_many, appCount)
                        else -> stringResource(R.string.home_status_free)
                    },
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black,
                    color = when {
                        isUnlocking -> colors.primary
                        isSoftLock && visualLocked -> ManualOrange
                        visualLocked -> colors.lockedPrimary
                        schedulePauseActive -> colors.onSurfaceVariant
                        else -> colors.primary
                    },
                    letterSpacing = 4.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 56.sp,
                )

                Spacer(Modifier.height(16.dp))

                // Timer or prompt
                if (isUnlocking) {
                    Text(
                        text = "",
                        style = MaterialTheme.typography.displaySmall,
                    )
                    Spacer(Modifier.height(32.dp))
                } else if (visualLocked) {
                    if (isEmergencyActive) {
                        val mins = (emergencyTimeRemaining / 60_000).toInt()
                        val secs = ((emergencyTimeRemaining % 60_000) / 1000).toInt()
                        Text(
                            text = stringResource(R.string.home_emergency_return, mins, secs.toString().padStart(2, '0')),
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.onSurface,
                        )

                        Spacer(Modifier.height(24.dp))

                        FilledTonalButton(onClick = { viewModel.endEmergencyEarly() }) {
                            Text(stringResource(R.string.home_resume_blocking))
                        }
                    } else {
                        val seDuration = lockState.lockDurationMs
                        val timerText = if (lockState.isNoEscape && seDuration != null) {
                            // No-escape: count DOWN to the auto-unlock.
                            val remainingMs = (seDuration - elapsedMs).coerceAtLeast(0L)
                            val totalSeconds = (remainingMs / 1000).toInt()
                            val minutes = totalSeconds / 60
                            val seconds = totalSeconds % 60
                            "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
                        } else {
                            val totalSeconds = (elapsedMs / 1000).toInt()
                            val hours = totalSeconds / 3600
                            val minutes = (totalSeconds % 3600) / 60
                            val seconds = totalSeconds % 60
                            buildString {
                                if (hours > 0) append("${hours}h ")
                                append("${minutes.toString().padStart(2, '0')}:")
                                append(seconds.toString().padStart(2, '0'))
                            }
                        }
                        Text(
                            text = timerText,
                            style = MaterialTheme.typography.displaySmall,
                            color = colors.onSurface.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Light,
                        )
                    }

                    Spacer(Modifier.height(32.dp))
                } else if (schedulePauseActive) {
                    val mins = (pauseRemainingMs / 60_000).toInt()
                    val secs = ((pauseRemainingMs % 60_000) / 1000).toInt()
                    Text(
                        text = stringResource(R.string.home_emergency_return, mins, secs.toString().padStart(2, '0')),
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.onSurface,
                    )

                    Spacer(Modifier.height(24.dp))

                    FilledTonalButton(onClick = { viewModel.resumeScheduledBlocking() }) {
                        Text(stringResource(R.string.home_resume_blocking))
                    }
                } else {
                    Text(
                        text = if (appCount == 1) stringResource(R.string.home_apps_to_block_one, appCount) else stringResource(R.string.home_apps_to_block_many, appCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
            if (isLandscape) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        homeStatus()
                        homeActions(Modifier, Modifier)
                    }
                    Spacer(Modifier.width(64.dp))
                    homeLogo()
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 32.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    homeLogo()
                    Spacer(Modifier.height(32.dp))
                    homeStatus()
                    inlineActions()
                }
            }
        }

        if (!isCompactHeight) {
            homeActions(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 48.dp),
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 120.dp),
            )
        }
    }

    // Emergency bottom sheet
    if (showEmergencyDialog) {
        EmergencyBottomSheet(
            remainingUnlocks = lockState.emergencyUnlocksRemaining,
            emergencyDurationMs = emergencyDurationMs,
            onConfirm = {
                showEmergencyDialog = false
                viewModel.emergencyUnlock()
            },
            onDismiss = { showEmergencyDialog = false },
        )
    }

    // Manual unlock bottom sheet
    if (showManualUnlockDialog) {
        ManualUnlockBottomSheet(
            onConfirm = {
                showManualUnlockDialog = false
                viewModel.manualUnlock()
            },
            onDismiss = { showManualUnlockDialog = false },
        )
    }
}

@Composable
private fun ManualModeNfcNudge(
    nfcPairingSuccess: Boolean,
    writeInterrupted: Boolean,
    writeExhausted: Boolean,
    onPairAnyway: () -> Unit,
    onStartNfcScan: () -> Unit,
    onCancelNfcScan: () -> Unit,
    onNfcScanSuccess: () -> Unit,
) {
    val colors = LockTheme.colors
    var expanded by remember { mutableStateOf(false) }

    AnimatedContent(
        targetState = expanded,
        transitionSpec = {
            fadeIn(tween(250, delayMillis = 100))
                .togetherWith(fadeOut(tween(150)))
                .using(
                    SizeTransform(clip = true) { _, _ ->
                        tween(400)
                    },
                )
        },
        contentAlignment = Alignment.TopCenter,
        label = "nfcNudgeMorph",
    ) { isExpanded ->
        if (!isExpanded) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = colors.cardContainer,
                border = BorderStroke(1.dp, colors.onSurfaceVariant.copy(alpha = 0.08f)),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = colors.primary.copy(alpha = 0.1f),
                            modifier = Modifier.size(36.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_contactless),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = colors.primary,
                                )
                            }
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.manual_mode_nfc_nudge_title),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.onSurface,
                            )
                            Text(
                                text = stringResource(R.string.manual_mode_nfc_nudge_desc),
                                fontSize = 12.sp,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            expanded = true
                            onStartNfcScan()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.4f)),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_contactless),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = colors.primary,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.manual_mode_nfc_nudge_cta),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.primary,
                        )
                    }
                }
            }
        } else {
            NfcScanCard(
                title = if (nfcPairingSuccess) stringResource(R.string.nfc_tags_paired_success)
                else stringResource(R.string.nfc_tags_waiting),
                subtitle = if (nfcPairingSuccess) stringResource(R.string.nfc_tags_paired_success_subtitle)
                else stringResource(R.string.nfc_tags_waiting_subtitle),
                isSuccess = nfcPairingSuccess,
                warning = when {
                    writeExhausted -> stringResource(R.string.nfc_write_failed_body)
                    writeInterrupted -> stringResource(R.string.nfc_write_interrupted)
                    else -> null
                },
                secondaryLabel = if (writeExhausted) stringResource(R.string.nfc_write_failed_cta) else null,
                onSecondaryClick = onPairAnyway,
                ctaLabel = if (!nfcPairingSuccess) stringResource(R.string.action_cancel) else null,
                onCtaClick = {
                    expanded = false
                    onCancelNfcScan()
                },
                onSuccessAnimationEnd = {
                    expanded = false
                    onNfcScanSuccess()
                },
            )
        }
    }
}
