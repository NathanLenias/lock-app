package com.nathanb.lock.ui.screens.onboarding

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nathanb.lock.R
import com.nathanb.lock.ui.theme.LockTheme

@Composable
internal fun WelcomePage(
    onNext: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")

    // Parallax breathing — gentle scale pulse, no translation (no edge artifacts)
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Background photo with Ken Burns animation
        Image(
            painter = painterResource(R.drawable.onboarding_bg),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
        )

        // Gradient overlay — transparent top, black bottom
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.5f to Color.Transparent,
                            0.75f to Color.Black.copy(alpha = 0.8f),
                            1.0f to Color.Black.copy(alpha = 0.93f),
                        ),
                    ),
                ),
        )

        // Top logo: app icon triangle + LOCK
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 68.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_lock_logo),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                colorFilter = ColorFilter.tint(LockTheme.colors.primary),
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = "LOCK",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = Color.Black,
                letterSpacing = 1.sp,
            )
        }

        // Bottom content: title + CTA
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .padding(bottom = 72.dp),
        ) {
            Text(
                text = stringResource(R.string.onboarding_welcome_title),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                lineHeight = 38.sp,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(24.dp))

            OnboardingButton(
                text = stringResource(R.string.onboarding_welcome_cta),
                onClick = onNext,
            )
        }
    }
}
