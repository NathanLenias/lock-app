package com.nathanb.lock.ui.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nathanb.lock.R
import com.nathanb.lock.data.model.SetupStatus
import com.nathanb.lock.ui.components.NfcScanCard
import com.nathanb.lock.ui.theme.LockTheme

@Composable
fun SetupChecklistSection(
    setupStatus: SetupStatus,
    appCount: Int,
    nfcPairingSuccess: Boolean,
    writeInterrupted: Boolean,
    writeExhausted: Boolean,
    onPairAnyway: () -> Unit,
    onNavigateToApps: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onStartNfcScan: () -> Unit,
    onCancelNfcScan: () -> Unit,
    onNfcScanSuccess: () -> Unit,
    onActivateManualMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LockTheme.colors
    var nfcScanExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Progress row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val borderColor = colors.onSurfaceVariant.copy(alpha = 0.4f)
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .drawBehind {
                        drawCircle(
                            color = borderColor,
                            style = Stroke(
                                width = 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(
                                    floatArrayOf(4.dp.toPx(), 3.dp.toPx()),
                                    0f,
                                ),
                            ),
                        )
                    },
            )
            Text(
                text = stringResource(
                    R.string.setup_progress,
                    setupStatus.completedCount,
                    setupStatus.totalCount,
                ),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = colors.onSurfaceVariant,
            )
        }

        // 1. Apps card
        if (setupStatus.hasApps) {
            CompletedCard(
                icon = Icons.Outlined.Shield,
                title = if (appCount <= 1) stringResource(R.string.setup_apps_done_one)
                else stringResource(R.string.setup_apps_done_many),
                subtitle = if (appCount <= 1) stringResource(R.string.setup_apps_done_count_one, appCount)
                else stringResource(R.string.setup_apps_done_count_many, appCount),
            )
        } else {
            SetupCard(
                icon = Icons.Outlined.Shield,
                title = stringResource(R.string.setup_apps_title),
                description = stringResource(R.string.setup_apps_desc),
                ctaLabel = stringResource(R.string.setup_apps_cta),
                onCtaClick = onNavigateToApps,
            )
        }

        // 2. NFC card
        if (setupStatus.hasNfcTag) {
            CompletedCard(
                icon = Icons.Outlined.Nfc,
                title = stringResource(R.string.setup_nfc_done),
            )
        } else {
            AnimatedContent(
                targetState = nfcScanExpanded,
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
                label = "nfcChecklistMorph",
            ) { isExpanded ->
                if (!isExpanded) {
                    SetupCard(
                        icon = Icons.Outlined.Nfc,
                        title = stringResource(R.string.setup_nfc_title),
                        description = stringResource(R.string.setup_nfc_desc),
                        ctaLabel = stringResource(R.string.setup_nfc_cta),
                        onCtaClick = {
                            nfcScanExpanded = true
                            onStartNfcScan()
                        },
                    )
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
                            nfcScanExpanded = false
                            onCancelNfcScan()
                        },
                        onSuccessAnimationEnd = {
                            nfcScanExpanded = false
                            onNfcScanSuccess()
                        },
                    )
                }
            }
        }

        // 3. Permissions card (hidden when OK)
        if (!setupStatus.permissionsOk) {
            SetupCard(
                icon = Icons.Outlined.Security,
                title = stringResource(R.string.setup_permissions_title),
                description = stringResource(R.string.setup_permissions_desc),
                ctaLabel = stringResource(R.string.setup_permissions_cta),
                onCtaClick = onNavigateToPermissions,
            )
        }

        // Manual mode option — visible when permissions OK + has apps + no NFC
        if (setupStatus.permissionsOk && setupStatus.hasApps && !setupStatus.hasNfcTag) {
            // Separator "ou"
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(colors.onSurfaceVariant.copy(alpha = 0.12f)),
                )
                Text(
                    text = stringResource(R.string.manual_mode_separator),
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant,
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(colors.onSurfaceVariant.copy(alpha = 0.12f)),
                )
            }

            ManualModeCard(onActivate = onActivateManualMode)
        }
    }
}

@Composable
private fun CompletedCard(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
) {
    val colors = LockTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = colors.primary.copy(alpha = 0.05f),
                shape = RoundedCornerShape(16.dp),
            )
            .drawBehind {
                drawRoundRect(
                    color = colors.primary.copy(alpha = 0.2f),
                    style = Stroke(width = 1.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()),
                )
            }
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Green check circle
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(colors.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = Color.White,
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = colors.primary,
                )
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.primary,
                )
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = colors.primary,
                )
            }
        }
    }
}

@Composable
private fun SetupCard(
    icon: ImageVector,
    title: String,
    description: String,
    ctaLabel: String,
    onCtaClick: () -> Unit,
) {
    val colors = LockTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = colors.cardContainer,
                shape = RoundedCornerShape(16.dp),
            )
            .drawBehind {
                drawRoundRect(
                    color = colors.onSurfaceVariant.copy(alpha = 0.08f),
                    style = Stroke(width = 1.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()),
                )
            }
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Empty circle checkbox
        val borderColor = colors.onSurfaceVariant.copy(alpha = 0.25f)
        Box(
            modifier = Modifier
                .size(24.dp)
                .drawBehind {
                    drawCircle(
                        color = borderColor,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                },
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Header with icon + title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = colors.primary,
                )
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                )
            }

            // Description
            Text(
                text = description,
                fontSize = 13.sp,
                color = colors.onSurfaceVariant,
            )

            // CTA button
            Button(
                onClick = onCtaClick,
                modifier = Modifier.height(32.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = Color.White,
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
            ) {
                Text(
                    text = ctaLabel,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
