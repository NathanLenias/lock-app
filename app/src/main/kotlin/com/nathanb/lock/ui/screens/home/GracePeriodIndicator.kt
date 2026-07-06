package com.nathanb.lock.ui.screens.home

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.nathanb.lock.R
import com.nathanb.lock.ui.theme.LockTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun GracePeriodIndicator(
    graceTimeRemaining: Long,
    gracePeriodMs: Long,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LockTheme.colors
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val totalMs = gracePeriodMs.coerceAtLeast(1L).toFloat()
    val progress = (graceTimeRemaining / totalMs).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
        label = "graceProgress",
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        label = "graceBtnScale",
    )
    val bgAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.12f else 0f,
        label = "graceBtnBg",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(72.dp)
            .scale(scale)
            .background(
                color = colors.onSurface.copy(alpha = bgAlpha),
                shape = CircleShape,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    scope.launch {
                        repeat(3) {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            if (it < 2) delay(50)
                        }
                    }
                    onCancel()
                },
            ),
    ) {
        CircularProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxSize(),
            color = colors.onSurface.copy(alpha = 0.7f),
            strokeWidth = 3.dp,
            trackColor = colors.onSurface.copy(alpha = 0.1f),
        )
        Text(
            text = stringResource(R.string.grace_cancel),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurface.copy(alpha = 0.7f),
        )
    }
}
