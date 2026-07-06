package com.nathanb.lock.ui.screens.onboarding

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.nathanb.lock.R
import com.nathanb.lock.data.model.SetupStatus
import com.nathanb.lock.ui.theme.BrickTheme

@Composable
internal fun CompletionPage(
    isVisible: Boolean,
    setupStatus: SetupStatus,
    onComplete: () -> Unit,
) {
    val colors = BrickTheme.colors
    var playConfetti by remember { mutableStateOf(false) }
    val confettiComposition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.confetti))
    val confettiProgress by animateLottieCompositionAsState(
        composition = confettiComposition,
        iterations = 1,
        isPlaying = playConfetti,
    )

    LaunchedEffect(isVisible) {
        if (isVisible) playConfetti = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.surface)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            // Success circle with check icon
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(colors.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = Color.White,
                )
            }

            Spacer(Modifier.height(32.dp))

            // Title
            Text(
                text = stringResource(
                    if (setupStatus.isComplete) R.string.onboarding_done_title
                    else R.string.onboarding_incomplete_title,
                ),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(12.dp))

            // Subtitle
            Text(
                text = stringResource(
                    if (setupStatus.isComplete) R.string.onboarding_done_subtitle
                    else R.string.onboarding_incomplete_subtitle,
                ),
                fontSize = 15.sp,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.fillMaxWidth(),
            )

            // Recap card (incomplete only)
            if (!setupStatus.isComplete) {
                Spacer(Modifier.height(32.dp))
                RecapCard(setupStatus)
            }

            Spacer(Modifier.weight(1f))

            // CTA button
            Button(
                onClick = onComplete,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = Color.White,
                ),
            ) {
                Text(
                    text = stringResource(
                        if (setupStatus.isComplete) R.string.onboarding_done_cta
                        else R.string.onboarding_incomplete_cta,
                    ),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(16.dp))

            // Hint text
            Text(
                text = stringResource(
                    if (setupStatus.isComplete) R.string.onboarding_done_hint
                    else R.string.onboarding_incomplete_hint,
                ),
                fontSize = if (setupStatus.isComplete) 12.sp else 13.sp,
                color = if (setupStatus.isComplete) colors.onSurfaceVariant
                else colors.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(48.dp))
        }

        // Confetti overlay
        LottieAnimation(
            composition = confettiComposition,
            progress = { confettiProgress },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun RecapCard(status: SetupStatus) {
    val colors = BrickTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = colors.surfaceContainerLow,
                shape = RoundedCornerShape(18.dp),
            )
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RecapRow(
            label = stringResource(R.string.onboarding_incomplete_permissions),
            isDone = status.permissionsOk,
        )
        RecapRow(
            label = stringResource(R.string.onboarding_incomplete_apps),
            isDone = status.hasApps,
        )
        RecapRow(
            label = stringResource(R.string.onboarding_incomplete_nfc),
            isDone = status.hasNfcTag,
        )
    }
}

@Composable
private fun RecapRow(label: String, isDone: Boolean) {
    val colors = BrickTheme.colors

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (isDone) {
            // Green filled circle with check
            Box(
                modifier = Modifier
                    .size(20.dp)
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
        } else {
            // Empty grey circle
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(
                        color = colors.onSurfaceVariant.copy(alpha = 0.15f),
                        shape = CircleShape,
                    ),
            )
        }

        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (isDone) colors.onSurface else colors.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
}
