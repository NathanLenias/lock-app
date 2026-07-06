package com.nathanb.lock.ui.screens.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.nathanb.lock.ui.theme.LockTheme
import kotlinx.coroutines.delay

@Composable
internal fun AnimatedTriangleLogo(
    isLocked: Boolean,
    iconScale: Float,
    onVisualLockedChange: (Boolean) -> Unit,
    fillProgress: Float = 0f,
    accentColor: Color? = null,
) {
    val colors = LockTheme.colors
    val strokeProgress = remember { Animatable(if (isLocked) 1f else 0f) }
    val fillAlpha = remember { Animatable(if (isLocked) 0.85f else 0f) }
    val circleColorAnim = remember { Animatable(if (isLocked) 1f else 0f) }
    val traceProgress = remember { Animatable(-1f) }

    LaunchedEffect(isLocked) {
        if (isLocked) {
            traceProgress.snapTo(-1f)
            onVisualLockedChange(true)
            circleColorAnim.animateTo(1f, animationSpec = tween(durationMillis = 400))
            strokeProgress.animateTo(
                1f,
                animationSpec = tween(durationMillis = 1400, easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)),
            )
            fillAlpha.animateTo(0.85f, animationSpec = tween(durationMillis = 700))
        } else {
            circleColorAnim.animateTo(0f, animationSpec = tween(durationMillis = 500))
            fillAlpha.animateTo(0f, animationSpec = tween(durationMillis = 800))
            strokeProgress.animateTo(
                0f,
                animationSpec = tween(durationMillis = 1000, easing = CubicBezierEasing(0.55f, 0.085f, 0.68f, 0.53f)),
            )
            onVisualLockedChange(false)
        }
    }

    LaunchedEffect(isLocked) {
        if (!isLocked) {
            delay(800)
            while (true) {
                traceProgress.snapTo(0f)
                traceProgress.animateTo(1f, animationSpec = tween(durationMillis = 1200, easing = LinearEasing))
                traceProgress.snapTo(-1f)
                delay(1500)
            }
        }
    }

    // Scale up on press (no pulse, just a single grow)
    val circleScale by animateFloatAsState(
        targetValue = if (fillProgress > 0f) 1.07f else 1f,
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "pressScale",
    )

    // Circle color: accentColor overrides both states if provided
    val baseColor = accentColor ?: colors.primary
    val lockedColor = accentColor ?: colors.lockedPrimary
    val circleColor = lerp(baseColor, lockedColor, circleColorAnim.value)

    val breathTransition = rememberInfiniteTransition(label = "breath")
    val breathProgress by breathTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 12_000
                0f at 0 using LinearEasing
                1f at 5_000 using LinearEasing
                1f at 6_000 using LinearEasing
                0f at 11_000 using LinearEasing
                0f at 12_000 using LinearEasing
            },
        ),
        label = "breathRing",
    )

    Box(
        modifier = Modifier
            .size(190.dp)
            .scale(iconScale),
        contentAlignment = Alignment.Center,
    ) {
        if (isLocked) {
            val ringAlpha = 0.06f + 0.16f * breathProgress
            val ringSize = 125.dp + 50.dp * breathProgress
            Box(
                modifier = Modifier
                    .size(ringSize)
                    .background(color = lockedColor.copy(alpha = ringAlpha), shape = CircleShape),
            )
        }

        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(circleScale)
                .clip(CircleShape)
                .background(color = circleColor, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(56.dp)) {
                val w = size.width
                val h = size.height
                val topCenter = Offset(w * 0.5f, h * 0.18f)
                val bottomRight = Offset(w * 0.87f, h * 0.82f)
                val bottomLeft = Offset(w * 0.13f, h * 0.82f)
                val gapEnd = Offset(w * 0.304f, h * 0.52f)

                val trianglePath = Path().apply {
                    moveTo(topCenter.x, topCenter.y)
                    lineTo(bottomRight.x, bottomRight.y)
                    lineTo(bottomLeft.x, bottomLeft.y)
                    lineTo(gapEnd.x, gapEnd.y)
                }

                val pathMeasure = PathMeasure().apply { setPath(trianglePath, false) }
                val pathLength = pathMeasure.length
                val strokeColor = Color.White
                // Combine lock stroke animation with long-press fill progress
                val currentStroke = maxOf(strokeProgress.value, fillProgress)

                if (fillAlpha.value > 0f) {
                    val fillPath = Path().apply {
                        moveTo(topCenter.x, topCenter.y)
                        lineTo(bottomRight.x, bottomRight.y)
                        lineTo(bottomLeft.x, bottomLeft.y)
                        close()
                    }
                    drawPath(
                        path = fillPath,
                        color = strokeColor.copy(alpha = fillAlpha.value * 0.6f),
                        style = Fill,
                    )
                }

                drawPath(
                    path = trianglePath,
                    color = strokeColor.copy(alpha = 0.35f),
                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )

                if (currentStroke > 0f) {
                    val visibleLength = pathLength * currentStroke
                    drawPath(
                        path = trianglePath,
                        color = strokeColor,
                        style = Stroke(
                            width = 3.5.dp.toPx(),
                            cap = StrokeCap.Butt,
                            join = StrokeJoin.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(visibleLength, pathLength), 0f),
                        ),
                    )
                }

                val tp = traceProgress.value
                if (tp >= 0f && currentStroke == 0f && fillAlpha.value == 0f && fillProgress == 0f) {
                    val maxTraceLength = pathLength * 0.12f
                    val traceStart = pathLength * tp
                    val remainingPath = pathLength - traceStart
                    val traceLength = maxTraceLength.coerceAtMost(remainingPath)
                    if (traceLength > 0f) {
                        val phase = pathLength - traceStart
                        drawPath(
                            path = trianglePath,
                            color = Color.White.copy(alpha = 0.6f),
                            style = Stroke(
                                width = 2.5f.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                                pathEffect = PathEffect.dashPathEffect(
                                    floatArrayOf(traceLength, pathLength - traceLength),
                                    phase,
                                ),
                            ),
                        )
                    }
                }
            }
        }
    }
}
