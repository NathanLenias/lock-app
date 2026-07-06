package com.nathanb.lock.ui.screens.home

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nathanb.lock.R
import com.nathanb.lock.ui.components.LockBottomSheet
import com.nathanb.lock.ui.theme.BrickTheme

@Composable
internal fun ManualUnlockBottomSheet(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = BrickTheme.colors

    LockBottomSheet(
        onDismiss = onDismiss,
        icon = Icons.Outlined.LockOpen,
        title = stringResource(R.string.manual_mode_unlock_title),
        body = stringResource(R.string.manual_mode_unlock_body),
        actions = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ManualOrange,
                    contentColor = Color.White,
                ),
            ) {
                Text(stringResource(R.string.manual_mode_unlock_confirm))
            }

            Spacer(Modifier.height(8.dp))

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = colors.onSurfaceVariant,
                ),
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
