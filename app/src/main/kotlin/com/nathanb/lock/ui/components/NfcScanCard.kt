package com.nathanb.lock.ui.components

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import com.nathanb.lock.R
import com.nathanb.lock.ui.theme.BrickTheme
import kotlinx.coroutines.delay

/**
 * Reusable NFC scan card with radar animation, title, subtitle, and optional CTA button.
 *
 * Supports a success animation: circle draws → fills green → checkmark appears.
 * Set [isSuccess] to true to trigger it; [onSuccessAnimationEnd] fires when done.
 */
@Composable
fun NfcScanCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    isSuccess: Boolean = false,
    ctaLabel: String? = null,
    onCtaClick: (() -> Unit)? = null,
    onSuccessAnimationEnd: (() -> Unit)? = null,
) {
    val colors = BrickTheme.colors

    // --- Radar pulse animations (active when not success) ---
    val infiniteTransition = rememberInfiniteTransition(label = "nfcRadar")

    val innerProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = EaseInOut),
            repeatMode = RepeatMode.Restart,
        ),
        label = "innerProgress",
    )
    val midProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, delayMillis = 600, easing = EaseInOut),
            repeatMode = RepeatMode.Restart,
        ),
        label = "midProgress",
    )
    val outerProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, delayMillis = 1200, easing = EaseInOut),
            repeatMode = RepeatMode.Restart,
        ),
        label = "outerProgress",
    )

    // --- Success animation (Lottie) ---
    val lottieComposition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.success_check))
    val lottieProgress by animateLottieCompositionAsState(
        composition = lottieComposition,
        isPlaying = isSuccess,
    )
    val primaryArgb = colors.primary.toArgb()
    val lottieDynamicProperties = rememberLottieDynamicProperties(
        // Green circle fill → primary
        rememberLottieDynamicProperty(
            property = LottieProperty.COLOR,
            value = primaryArgb,
            keyPath = arrayOf("Shape Layer 2", "Ellipse 1", "Fill 1"),
        ),
        // Expanding ring color → primary
        rememberLottieDynamicProperty(
            property = LottieProperty.COLOR,
            value = primaryArgb,
            keyPath = arrayOf("Shape Layer 1", "Ellipse 1", "Fill 1"),
        ),
        // Expanding ring opacity → 30%
        rememberLottieDynamicProperty(
            property = LottieProperty.OPACITY,
            value = 30,
            keyPath = arrayOf("Shape Layer 1", "Ellipse 1", "Fill 1"),
        ),
        // Hide white BG rectangle
        rememberLottieDynamicProperty(
            property = LottieProperty.OPACITY,
            value = 0,
            keyPath = arrayOf("BG", "**"),
        ),
    )

    LaunchedEffect(lottieProgress) {
        if (isSuccess && lottieProgress >= 1f) {
            delay(400)
            onSuccessAnimationEnd?.invoke()
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colors.cardContainer,
        border = BorderStroke(1.dp, colors.onSurfaceVariant.copy(alpha = 0.08f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier.size(88.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (!isSuccess) {
                    // --- Radar mode ---
                    PulseRing(
                        progress = outerProgress,
                        maxSizeDp = 88f,
                        strokeWidth = 1f,
                        color = colors.primary,
                    )
                    PulseRing(
                        progress = midProgress,
                        maxSizeDp = 88f,
                        strokeWidth = 1.5f,
                        color = colors.primary,
                    )
                    PulseRing(
                        progress = innerProgress,
                        maxSizeDp = 88f,
                        strokeWidth = 2f,
                        color = colors.primary,
                    )

                    // Solid center with NFC icon
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(colors.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_contactless),
                            contentDescription = null,
                            modifier = Modifier.size(26.dp),
                            tint = Color.White,
                        )
                    }
                } else {
                    // --- Success mode (Lottie) ---
                    LottieAnimation(
                        composition = lottieComposition,
                        progress = { lottieProgress },
                        modifier = Modifier.size(64.dp),
                        dynamicProperties = lottieDynamicProperties,
                    )
                }
            }

            // Title
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
            )

            // Subtitle
            Text(
                text = subtitle,
                fontSize = 14.sp,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
            )

            // Optional CTA (hidden during success)
            if (ctaLabel != null && !isSuccess) {
                Button(
                    onClick = { onCtaClick?.invoke() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = Color.White,
                    ),
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
}

/**
 * A single ring that scales from center to [maxSizeDp] and fades out as it expands.
 */
@Composable
private fun PulseRing(
    progress: Float,
    maxSizeDp: Float,
    strokeWidth: Float,
    color: Color,
) {
    val minSize = 44f // starts from center solid circle size
    val currentSize = minSize + (maxSizeDp - minSize) * progress
    val alpha = (1f - progress).coerceIn(0f, 0.6f)

    Box(
        modifier = Modifier
            .size(currentSize.dp)
            .drawBehind {
                drawCircle(
                    color = color.copy(alpha = alpha),
                    radius = size.minDimension / 2,
                    style = Stroke(width = strokeWidth.dp.toPx()),
                )
            },
    )
}
