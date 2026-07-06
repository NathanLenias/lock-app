package com.nathanb.lock.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nathanb.lock.R
import com.nathanb.lock.ui.theme.LockTheme
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    isLocked: Boolean,
    isUnlocking: Boolean = false,
    onSplashFinished: () -> Unit,
) {
    val colors = LockTheme.colors

    // All animatables start at 0 — unlocking branch snaps them to locked state before animating.
    // Keyed on isLocked so the effect restarts when the real value arrives from DataStore.
    val circleColorAnim = remember { Animatable(0f) } // 0 = green, 1 = red
    val strokeProgress = remember { Animatable(0f) }
    val fillAlpha = remember { Animatable(0f) }
    val bgTransition = remember { Animatable(0f) } // 0 = free bg, 1 = locked bg

    LaunchedEffect(isLocked) {
        // Reset on restart (e.g. when isLocked settles to real value)
        strokeProgress.snapTo(0f)
        fillAlpha.snapTo(0f)

        if (isUnlocking) {
            // Snap to fully locked state, then animate to unlocked
            circleColorAnim.snapTo(1f)
            strokeProgress.snapTo(1f)
            fillAlpha.snapTo(0.85f)
            bgTransition.snapTo(1f)
            delay(400)

            // Circle: red → green
            circleColorAnim.animateTo(
                0f,
                animationSpec = tween(durationMillis = 500),
            )
            // Background transitions to free
            bgTransition.animateTo(
                0f,
                animationSpec = tween(durationMillis = 500),
            )
            // Fill fades out
            fillAlpha.animateTo(
                0f,
                animationSpec = tween(durationMillis = 800),
            )
            // Stroke retracts
            strokeProgress.animateTo(
                0f,
                animationSpec = tween(
                    durationMillis = 1000,
                    easing = CubicBezierEasing(0.55f, 0.085f, 0.68f, 0.53f),
                ),
            )
            delay(300)
            onSplashFinished()
        } else {
            // Normal splash: draw stroke from 0 to 1
            delay(300)
            strokeProgress.animateTo(
                1f,
                animationSpec = tween(
                    durationMillis = 1200,
                    easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f),
                ),
            )
            if (isLocked) {
                fillAlpha.animateTo(
                    0.85f,
                    animationSpec = tween(durationMillis = 400),
                )
            }
            delay(500)
            onSplashFinished()
        }
    }

    // Unlocking mode: animated colors (red → green transition)
    // Normal mode: reactive colors from isLocked (picks up real value even if first frame had default)
    val circleColor: Color
    val bgColor: Color
    if (isUnlocking) {
        circleColor = lerp(colors.primary, colors.lockedPrimary, circleColorAnim.value)
        bgColor = lerp(colors.surface, colors.lockedContainer, bgTransition.value)
    } else {
        circleColor = if (isLocked) colors.lockedPrimary else colors.primary
        bgColor = if (isLocked) colors.lockedContainer else colors.surface
    }
    val accentColor = circleColor
    val text = if (isUnlocking) stringResource(R.string.splash_unlocking) else stringResource(R.string.splash_lock)
    val textSize = if (isUnlocking) 36.sp else 48.sp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(
                        color = circleColor,
                        shape = CircleShape,
                    ),
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

                    // Fill (locked / unlocking state)
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

                    // Ghost stroke (muted)
                    drawPath(
                        path = trianglePath,
                        color = strokeColor.copy(alpha = 0.35f),
                        style = Stroke(
                            width = 3.5.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )

                    // Animated stroke
                    val currentStroke = strokeProgress.value
                    if (currentStroke > 0f) {
                        val visibleLength = pathLength * currentStroke
                        drawPath(
                            path = trianglePath,
                            color = strokeColor,
                            style = Stroke(
                                width = 3.5.dp.toPx(),
                                cap = StrokeCap.Butt,
                                join = StrokeJoin.Round,
                                pathEffect = PathEffect.dashPathEffect(
                                    floatArrayOf(visibleLength, pathLength),
                                    0f,
                                ),
                            ),
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            Text(
                text = text,
                fontSize = textSize,
                fontWeight = FontWeight.Black,
                color = accentColor,
                letterSpacing = 4.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
