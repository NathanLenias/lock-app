package com.nathanb.lock.ui.screens.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.nathanb.lock.R
import com.nathanb.lock.ui.theme.LockTheme
import com.nathanb.lock.ui.theme.SatoshiFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BreathingBottomSheet(
    onDismiss: () -> Unit,
) {
    val colors = LockTheme.colors

    // 5-1-5-1 cycle (12s total)
    val transition = rememberInfiniteTransition(label = "breathing")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 12_000
                0f at 0 using LinearEasing        // inhale start
                1f at 5_000 using LinearEasing    // inhale end
                1f at 6_000 using LinearEasing    // hold top
                0f at 11_000 using LinearEasing   // exhale end
                0f at 12_000 using LinearEasing   // hold bottom
            },
        ),
        label = "breathProgress",
    )

    // Phase tracking for label
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 12_000
                0f at 0 using LinearEasing         // inhale
                0f at 4_999 using LinearEasing
                0.33f at 5_000 using LinearEasing  // hold top
                0.33f at 5_999 using LinearEasing
                0.66f at 6_000 using LinearEasing  // exhale
                0.66f at 10_999 using LinearEasing
                1f at 11_000 using LinearEasing    // hold bottom
                1f at 11_999 using LinearEasing
            },
        ),
        label = "phase",
    )

    val label = when {
        phase < 0.17f -> stringResource(R.string.breathing_inhale)
        phase < 0.50f -> stringResource(R.string.breathing_hold)
        phase < 0.83f -> stringResource(R.string.breathing_exhale)
        else -> stringResource(R.string.breathing_hold)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surfaceContainer,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 14.dp, bottom = 10.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.3f)),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .padding(bottom = 40.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))

            // Breathing circle with triangle
            val circleMinSize = 100f
            val circleMaxSize = 180f
            val circleSize = circleMinSize + (circleMaxSize - circleMinSize) * progress
            val circleAlpha = 0.3f + 0.7f * progress

            Box(
                modifier = Modifier.size(200.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.size(200.dp)) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = (circleSize / 2f).dp.toPx()

                    // Outer glow ring
                    drawCircle(
                        color = colors.primary.copy(alpha = circleAlpha * 0.15f),
                        radius = radius + 12.dp.toPx(),
                        center = center,
                    )

                    // Main circle
                    drawCircle(
                        color = colors.primary.copy(alpha = circleAlpha * 0.6f),
                        radius = radius,
                        center = center,
                    )

                    // Triangle (L▲CK branding) centered in the circle
                    val triSize = radius * 0.55f
                    val triCenterY = center.y + triSize * 0.05f
                    val topCenter = Offset(center.x, triCenterY - triSize * 0.65f)
                    val bottomRight = Offset(center.x + triSize * 0.6f, triCenterY + triSize * 0.5f)
                    val bottomLeft = Offset(center.x - triSize * 0.6f, triCenterY + triSize * 0.5f)
                    val gapEnd = Offset(
                        center.x - triSize * 0.6f + (topCenter.x - (center.x - triSize * 0.6f)) * 0.32f,
                        triCenterY + triSize * 0.5f - (triCenterY + triSize * 0.5f - topCenter.y) * 0.32f,
                    )

                    val trianglePath = Path().apply {
                        moveTo(topCenter.x, topCenter.y)
                        lineTo(bottomRight.x, bottomRight.y)
                        lineTo(bottomLeft.x, bottomLeft.y)
                        lineTo(gapEnd.x, gapEnd.y)
                    }

                    // Ghost stroke
                    drawPath(
                        path = trianglePath,
                        color = Color.White.copy(alpha = 0.3f),
                        style = Stroke(
                            width = 2.5.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )

                    // Filled triangle with breathing alpha
                    val fillPath = Path().apply {
                        moveTo(topCenter.x, topCenter.y)
                        lineTo(bottomRight.x, bottomRight.y)
                        lineTo(bottomLeft.x, bottomLeft.y)
                        close()
                    }
                    drawPath(
                        path = fillPath,
                        color = Color.White.copy(alpha = circleAlpha * 0.4f),
                        style = Fill,
                    )

                    // Bright stroke
                    drawPath(
                        path = trianglePath,
                        color = Color.White.copy(alpha = circleAlpha),
                        style = Stroke(
                            width = 2.5.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // Phase label
            Text(
                text = label,
                fontFamily = SatoshiFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.breathing_subtitle),
                fontFamily = SatoshiFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}
