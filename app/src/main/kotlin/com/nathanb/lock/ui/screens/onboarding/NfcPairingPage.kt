package com.nathanb.lock.ui.screens.onboarding

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nathanb.lock.R
import com.nathanb.lock.ui.components.NfcScanCard
import com.nathanb.lock.ui.theme.LockTheme

@Composable
internal fun NfcPairingPage(
    isSuccess: Boolean = false,
    writeInterrupted: Boolean = false,
    writeExhausted: Boolean = false,
    onPairAnyway: () -> Unit = {},
    onSkip: () -> Unit,
    onSuccessAnimationEnd: (() -> Unit)? = null,
) {
    val colors = LockTheme.colors

    val infiniteTransition = rememberInfiniteTransition(label = "nfcPulse")

    // Staggered pulse animations for each ring
    val outerScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "outerScale",
    )
    val midScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "midScale",
    )
    val outerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "outerAlpha",
    )
    val midAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "midAlpha",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))

        // Large NFC visual (160dp concentric circles)
        Box(
            modifier = Modifier.size(160.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Outer ring — filled + stroke
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .scale(outerScale)
                    .alpha(outerAlpha)
                    .background(colors.primary.copy(alpha = 0.06f), CircleShape)
                    .drawBehind {
                        drawCircle(
                            color = colors.primary.copy(alpha = 0.12f),
                            radius = size.minDimension / 2,
                            style = Stroke(width = 1.dp.toPx()),
                        )
                    },
            )
            // Middle ring — filled + stroke
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .scale(midScale)
                    .alpha(midAlpha)
                    .background(colors.primary.copy(alpha = 0.08f), CircleShape)
                    .drawBehind {
                        drawCircle(
                            color = colors.primary.copy(alpha = 0.19f),
                            radius = size.minDimension / 2,
                            style = Stroke(width = 1.dp.toPx()),
                        )
                    },
            )
            // Inner circle — filled
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(colors.primary.copy(alpha = 0.10f), CircleShape),
            )
            // NFC icon
            Icon(
                painter = painterResource(R.drawable.ic_contactless),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = colors.primary,
            )
        }

        Spacer(Modifier.height(32.dp))

        // Title
        Text(
            text = stringResource(R.string.onboarding_nfc_title),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))

        // Description
        Text(
            text = stringResource(R.string.onboarding_nfc_desc),
            fontSize = 14.sp,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(40.dp))

        // NFC scan card
        NfcScanCard(
            title = if (isSuccess) stringResource(R.string.nfc_tags_paired_success) else stringResource(R.string.onboarding_nfc_waiting),
            subtitle = if (isSuccess) stringResource(R.string.nfc_tags_paired_success_subtitle) else stringResource(R.string.onboarding_nfc_hint),
            isSuccess = isSuccess,
            warning = when {
                writeExhausted -> stringResource(R.string.nfc_write_failed_body)
                writeInterrupted -> stringResource(R.string.nfc_write_interrupted)
                else -> null
            },
            secondaryLabel = if (writeExhausted) stringResource(R.string.nfc_write_failed_cta) else null,
            onSecondaryClick = onPairAnyway,
            onSuccessAnimationEnd = onSuccessAnimationEnd,
        )

        Spacer(Modifier.weight(1f))

        // Skip
        Text(
            text = stringResource(R.string.onboarding_nfc_skip),
            fontSize = 14.sp,
            color = colors.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.clickable(onClick = onSkip),
        )

        Spacer(Modifier.height(72.dp))
    }
}
