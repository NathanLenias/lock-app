package com.nathanb.lock.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nathanb.lock.R
import com.nathanb.lock.ui.theme.LockTheme
import com.nathanb.lock.ui.theme.SatoshiFamily

/** Shared centered modal card: rounded container, close (X), a banner slot, then content. */
@Composable
fun InAppCardDialog(
    onDismiss: () -> Unit,
    banner: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LockTheme.colors
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(colors.surfaceContainer)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                banner()
                content()
            }
            // Close button (top-right)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(colors.surface.copy(alpha = 0.9f))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.action_cancel),
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** Placeholder banner (rounded, green gradient + icon) until a real illustration is dropped in. */
@Composable
private fun BannerPlaceholder(icon: ImageVector) {
    val colors = LockTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(colors.primary.copy(alpha = 0.22f), colors.primary.copy(alpha = 0.08f)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(48.dp))
    }
}

@Composable
fun SupportCard(
    onSupport: () -> Unit,
    onLater: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LockTheme.colors
    InAppCardDialog(
        onDismiss = onDismiss,
        banner = {
            Image(
                painter = painterResource(R.drawable.support_banner),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(20.dp)),
                contentScale = ContentScale.Crop,
            )
        },
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.inapp_support_title),
            fontFamily = SatoshiFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.inapp_support_body),
            fontFamily = SatoshiFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onSupport,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
        ) {
            Text(
                text = stringResource(R.string.inapp_support_cta),
                fontFamily = SatoshiFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White,
            )
        }
        TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.inapp_support_later),
                fontFamily = SatoshiFamily,
                fontSize = 14.sp,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ChangelogCard(
    versionName: String,
    onDiscover: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LockTheme.colors
    InAppCardDialog(
        onDismiss = onDismiss,
        banner = { BannerPlaceholder(Icons.Outlined.AutoAwesome) },
    ) {
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(colors.primary.copy(alpha = 0.15f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.inapp_changelog_version, versionName),
                fontFamily = SatoshiFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = colors.primaryDark,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.inapp_changelog_title),
            fontFamily = SatoshiFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        ChangelogRow(Icons.Outlined.Build, R.string.inapp_changelog_1_title, R.string.inapp_changelog_1_body)
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onDiscover,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
        ) {
            Text(
                text = stringResource(R.string.inapp_changelog_cta),
                fontFamily = SatoshiFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun ChangelogRow(icon: ImageVector, titleRes: Int, bodyRes: Int) {
    val colors = LockTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(colors.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.primaryDark, modifier = Modifier.size(18.dp))
        }
        Column {
            Text(
                text = stringResource(titleRes),
                fontFamily = SatoshiFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = colors.onSurface,
            )
            Text(
                text = stringResource(bodyRes),
                fontFamily = SatoshiFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp,
                color = colors.onSurfaceVariant,
            )
        }
    }
}
