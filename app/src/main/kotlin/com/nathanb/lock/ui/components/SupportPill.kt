package com.nathanb.lock.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nathanb.lock.R
import com.nathanb.lock.ui.theme.SatoshiFamily

private const val SUPPORT_URL = "https://ko-fi.com/nathanpmdev"

/** Opens the support (Ko-fi) page. */
fun openSupportPage(context: android.content.Context) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SUPPORT_URL)))
}

/** Small "❤️ Support" pill used in the top-right of Settings and Home. */
@Composable
fun SupportPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFFFFF0F0))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = "❤️", fontSize = 12.sp)
        Text(
            text = stringResource(R.string.action_support),
            fontFamily = SatoshiFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = Color(0xFFE53E3E),
        )
    }
}

/** The "Support Lock" bottom sheet (reused by Settings and Home). */
@Composable
fun SupportBottomSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    LockBottomSheet(
        onDismiss = onDismiss,
        icon = Icons.Outlined.Favorite,
        title = stringResource(R.string.support_title),
        body = stringResource(R.string.support_body),
        bodyFontSize = 15.sp,
        actions = {
            Button(
                onClick = {
                    onDismiss()
                    openSupportPage(context)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFDD00),
                    contentColor = Color.Black,
                ),
            ) {
                Text(
                    text = stringResource(R.string.support_cta),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
    )
}
