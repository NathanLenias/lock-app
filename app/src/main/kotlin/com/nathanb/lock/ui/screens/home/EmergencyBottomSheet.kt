package com.nathanb.lock.ui.screens.home

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.nathanb.lock.R
import com.nathanb.lock.ui.components.LockBottomSheet
import com.nathanb.lock.ui.theme.BrickTheme

@Composable
internal fun EmergencyBottomSheet(
    remainingUnlocks: Int,
    emergencyDurationMs: Long,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = BrickTheme.colors
    val durationMinutes = (emergencyDurationMs / 60_000).toInt()

    LockBottomSheet(
        onDismiss = onDismiss,
        icon = Icons.Default.LockOpen,
        title = stringResource(R.string.emergency_title),
        body = stringResource(R.string.emergency_body, durationMinutes, if (durationMinutes > 1) "s" else "", remainingUnlocks),
        actions = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.surface,
                ),
            ) {
                Text(stringResource(R.string.emergency_confirm))
            }

            Spacer(Modifier.height(8.dp))

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = colors.primary,
                ),
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
