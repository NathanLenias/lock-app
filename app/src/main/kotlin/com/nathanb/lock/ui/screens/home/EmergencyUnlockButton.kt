package com.nathanb.lock.ui.screens.home

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.nathanb.lock.R
import com.nathanb.lock.ui.theme.LockTheme
import kotlinx.coroutines.delay

private const val LONG_PRESS_DURATION_MS = 1000L

@Composable
internal fun EmergencyUnlockButton(
    remainingUnlocks: Int,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    showRemainingLabel: Boolean = true,
) {
    val colors = LockTheme.colors
    val view = LocalView.current
    var isPressed by remember { mutableStateOf(false) }
    var shakeTrigger by remember { mutableIntStateOf(0) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        label = "emergencyBtnScale",
    )
    val bgAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.16f else 0.08f,
        label = "emergencyBtnBg",
    )

    // Shake animation on early release
    val shakeOffset = remember { Animatable(0f) }
    LaunchedEffect(shakeTrigger) {
        if (shakeTrigger > 0) {
            for (v in listOf(-10f, 10f, -7f, 7f, -4f, 4f, 0f)) {
                shakeOffset.animateTo(v, tween(50))
            }
        }
    }

    // Progressive haptic pulsing (accelerating) + 1s activation
    LaunchedEffect(isPressed) {
        if (isPressed) {
            var completed = false
            try {
                val start = System.currentTimeMillis()
                delay(80)
                while (System.currentTimeMillis() - start < LONG_PRESS_DURATION_MS) {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    val elapsed = System.currentTimeMillis() - start
                    val progress = (elapsed.toFloat() / LONG_PRESS_DURATION_MS).coerceIn(0f, 1f)
                    // Accelerate: 120ms → 30ms
                    val interval = (120 - 90 * progress).toLong().coerceAtLeast(30)
                    delay(interval)
                }
                completed = true
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                onLongPress()
            } finally {
                if (!completed) {
                    view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                    shakeTrigger++
                }
            }
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(72.dp)
                    .offset(x = shakeOffset.value.dp)
                    .scale(scale)
                    .background(
                        color = colors.onSurface.copy(alpha = bgAlpha),
                        shape = CircleShape,
                    )
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown()
                            isPressed = true
                            waitForUpOrCancellation()
                            isPressed = false
                        }
                    },
            ) {
                Icon(
                    imageVector = Icons.Default.LockOpen,
                    contentDescription = stringResource(R.string.emergency_unlock_cd),
                    modifier = Modifier.size(24.dp),
                    tint = colors.onSurface.copy(alpha = 0.7f),
                )
            }

            if (showRemainingLabel) {
                Spacer(Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.emergency_remaining, remainingUnlocks),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}
