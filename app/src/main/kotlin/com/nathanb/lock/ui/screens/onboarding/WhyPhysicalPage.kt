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
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nathanb.lock.R
import com.nathanb.lock.ui.theme.BrickTheme

@Composable
internal fun WhyPhysicalPage(
    onNext: () -> Unit,
) {
    val colors = BrickTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))

        Box(
            modifier = Modifier
                .size(96.dp)
                .background(colors.primary.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.DirectionsWalk,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = colors.primary,
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.onboarding_why_title),
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_why_accent),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.primary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(40.dp))

        ArgumentRow(
            icon = Icons.AutoMirrored.Rounded.DirectionsWalk,
            title = stringResource(R.string.onboarding_why_arg1_title),
            description = stringResource(R.string.onboarding_why_arg1_desc),
        )
        Spacer(Modifier.height(20.dp))
        ArgumentRow(
            icon = Icons.Rounded.Shield,
            title = stringResource(R.string.onboarding_why_arg2_title),
            description = stringResource(R.string.onboarding_why_arg2_desc),
        )
        Spacer(Modifier.height(20.dp))
        ArgumentRow(
            icon = Icons.Rounded.Psychology,
            title = stringResource(R.string.onboarding_why_arg3_title),
            description = stringResource(R.string.onboarding_why_arg3_desc),
        )

        Spacer(Modifier.weight(1f))

        OnboardingButton(
            text = stringResource(R.string.onboarding_continue),
            onClick = onNext,
        )

        Spacer(Modifier.height(72.dp))
    }
}

@Composable
private fun ArgumentRow(
    icon: ImageVector,
    title: String,
    description: String,
) {
    val colors = BrickTheme.colors

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(colors.primary.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
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
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 14.sp,
                color = colors.onSurfaceVariant,
                lineHeight = 20.sp,
            )
        }
    }
}
