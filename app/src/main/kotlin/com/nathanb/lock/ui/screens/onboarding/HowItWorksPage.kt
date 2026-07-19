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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nathanb.lock.R
import com.nathanb.lock.ui.theme.LockTheme

@Composable
internal fun HowItWorksPage(
    onNext: () -> Unit,
) {
    val colors = LockTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Scrollable content, centered when it fits — the CTA below stays pinned
        // and reachable even at large font scales.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.onboarding_how_title),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.onboarding_how_subtitle),
                    fontSize = 14.sp,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(32.dp))

                StepCard(
                    icon = Icons.Rounded.Nfc,
                    title = stringResource(R.string.onboarding_how_step1_title),
                    description = stringResource(R.string.onboarding_how_step1_desc),
                )
                Spacer(Modifier.height(16.dp))
                StepCard(
                    icon = Icons.Rounded.Shield,
                    title = stringResource(R.string.onboarding_how_step2_title),
                    description = stringResource(R.string.onboarding_how_step2_desc),
                )
                Spacer(Modifier.height(16.dp))
                StepCard(
                    icon = Icons.Rounded.Nfc,
                    title = stringResource(R.string.onboarding_how_step3_title),
                    description = stringResource(R.string.onboarding_how_step3_desc),
                )

                Spacer(Modifier.height(24.dp))
            }
        }

        OnboardingButton(
            text = stringResource(R.string.onboarding_continue),
            onClick = onNext,
        )

        Spacer(Modifier.height(72.dp))
    }
}

@Composable
private fun StepCard(
    icon: ImageVector,
    title: String,
    description: String,
) {
    val colors = LockTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(20.dp))
            .background(colors.cardContainer, RoundedCornerShape(20.dp))
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    colors.primary.copy(alpha = 0.1f),
                    RoundedCornerShape(12.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = colors.primary,
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 13.sp,
                color = colors.onSurfaceVariant,
                lineHeight = 18.sp,
            )
        }
    }
}
