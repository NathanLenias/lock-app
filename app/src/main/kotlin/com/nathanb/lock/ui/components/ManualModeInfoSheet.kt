package com.nathanb.lock.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PanTool
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
import com.nathanb.lock.ui.screens.home.ManualOrange
import com.nathanb.lock.ui.theme.BrickTheme

@Composable
fun ManualModeInfoSheet(
    onDismiss: () -> Unit,
    onDisableManualMode: () -> Unit,
) {
    LockBottomSheet(
        onDismiss = onDismiss,
        icon = Icons.Outlined.PanTool,
        title = stringResource(R.string.manual_mode_info_title),
        body = stringResource(R.string.manual_mode_info_body),
        actions = {
            Button(
                onClick = {
                    onDisableManualMode()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ManualOrange,
                    contentColor = Color.White,
                ),
            ) {
                Text(stringResource(R.string.manual_mode_disable))
            }

            Spacer(Modifier.height(8.dp))

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = BrickTheme.colors.onSurfaceVariant,
                ),
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
