package com.nathanb.lock.ui.screens.websites

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nathanb.lock.R
import com.nathanb.lock.ui.theme.LockTheme
import com.nathanb.lock.ui.theme.SatoshiFamily

/**
 * Prominent disclosure shown before the user enables "Lock: websites" (Play policy:
 * in-app, before the permission, explicit user action). Both buttons lead to the list.
 */
@Composable
fun WebsitesIntroScreen(
    profileName: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onLater: () -> Unit,
) {
    val colors = LockTheme.colors
    Scaffold(
        containerColor = colors.surface,
        topBar = {
            WebsitesTopBar(
                subtitle = stringResource(R.string.websites_profile_label, profileName),
                onBack = onBack,
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                ) {
                    Icon(Icons.Outlined.Settings, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.websites_intro_cta),
                        fontFamily = SatoshiFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White,
                    )
                }
                TextButton(onClick = onLater) {
                    Text(
                        text = stringResource(R.string.websites_intro_later),
                        fontFamily = SatoshiFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Language, contentDescription = null, tint = colors.primary, modifier = Modifier.size(28.dp))
            }
            Text(
                text = stringResource(R.string.websites_intro_title),
                fontFamily = SatoshiFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                color = colors.onSurface,
            )
            Text(
                text = stringResource(R.string.websites_intro_body),
                fontFamily = SatoshiFamily,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = colors.onSurfaceVariant,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.cardContainer)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                IntroPoint(Icons.Outlined.Visibility, stringResource(R.string.websites_intro_point_address))
                IntroPoint(Icons.Outlined.Storage, stringResource(R.string.websites_intro_point_local))
                IntroPoint(Icons.Outlined.PhoneAndroid, stringResource(R.string.websites_intro_point_apps))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.onSurfaceVariant.copy(alpha = 0.06f))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Text(
                    text = stringResource(R.string.websites_intro_hint, stringResource(R.string.website_service_label)),
                    fontFamily = SatoshiFamily,
                    fontSize = 13.sp,
                    color = colors.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun IntroPoint(icon: ImageVector, text: String) {
    val colors = LockTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
        }
        Text(
            text = text,
            fontFamily = SatoshiFamily,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = colors.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}
