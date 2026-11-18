package com.nathanb.lock.ui.screens.garden

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material.icons.outlined.Yard
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nathanb.lock.R
import com.nathanb.lock.ui.components.LockBottomSheet
import com.nathanb.lock.ui.components.LockSheetFrame
import com.nathanb.lock.ui.theme.LockTheme
import com.nathanb.lock.ui.theme.SatoshiFamily

/** What the garden is for, and what happens to it on a reinstall or a new phone. */
@Composable
fun GardenInfoSheet(onDismiss: () -> Unit) {
    val colors = LockTheme.colors
    LockSheetFrame(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Outlined.Yard,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.garden_info_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                InfoPoint(Icons.Outlined.EventAvailable, stringResource(R.string.garden_info_point_days))
                InfoPoint(Icons.Outlined.RestartAlt, stringResource(R.string.garden_info_point_reinstall))
                InfoPoint(Icons.Outlined.Smartphone, stringResource(R.string.garden_info_point_device))
                InfoPoint(Icons.Outlined.WifiOff, stringResource(R.string.garden_info_point_support))
            }
        }
    }
}

@Composable
private fun InfoPoint(icon: ImageVector, text: String) {
    val colors = LockTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.onSurface,
            modifier = Modifier.padding(top = 2.dp).size(20.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface,
            lineHeight = 21.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

/** How drops are earned and what they pay for. */
@Composable
fun DropsInfoSheet(onDismiss: () -> Unit) {
    LockBottomSheet(
        onDismiss = onDismiss,
        icon = Icons.Outlined.WaterDrop,
        title = stringResource(R.string.garden_drops_title),
        body = stringResource(R.string.garden_drops_body_earn) + "\n\n" +
            stringResource(R.string.garden_drops_body_use),
    )
}

/** Shown once to long-time users: why their garden starts with four tiles. */
@Composable
fun GardenIntroSheet(onDismiss: () -> Unit) {
    val colors = LockTheme.colors
    LockBottomSheet(
        onDismiss = onDismiss,
        icon = Icons.Outlined.CardGiftcard,
        title = stringResource(R.string.garden_intro_title),
        body = stringResource(R.string.garden_intro_body) + "\n\n" +
            stringResource(R.string.garden_intro_body_next),
        actions = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
            ) {
                Text(
                    text = stringResource(R.string.garden_intro_action),
                    fontFamily = SatoshiFamily,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        },
    )
}
