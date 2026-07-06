package com.nathanb.lock.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.nathanb.lock.R
import com.nathanb.lock.data.model.Profile
import com.nathanb.lock.data.model.ProfileType
import com.nathanb.lock.ui.theme.LockTheme
import com.nathanb.lock.ui.theme.SatoshiFamily

@Composable
internal fun ProfileCard(
    profile: Profile,
    appIconBitmaps: List<ImageBitmap>,
    onClick: () -> Unit,
) {
    val colors = LockTheme.colors
    val isNoEscape = ProfileType.fromValue(profile.type) == ProfileType.NO_ESCAPE
    val blockedCount = profile.blockedPackages.size

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = Color.Black.copy(alpha = 0.05f),
                spotColor = Color.Black.copy(alpha = 0.05f),
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Type icon: shield (standard) or timer (no-escape)
                val iconBg = if (isNoEscape) Color(0xFFFFF1E6) else Color(0xFFE8F5E9)
                val iconTint = if (isNoEscape) Color(0xFFEF6C00) else colors.primaryDark
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(iconBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isNoEscape) Icons.Outlined.Timer else Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = profile.name,
                        fontFamily = SatoshiFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = colors.onSurface,
                    )
                    val subtitle = if (isNoEscape) {
                        stringResource(R.string.profile_apps_count_inaccessible, blockedCount)
                    } else {
                        stringResource(R.string.profile_apps_count_blocked, blockedCount)
                    }
                    Text(
                        text = subtitle,
                        fontFamily = SatoshiFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.sp,
                        color = colors.onSurfaceVariant,
                    )
                }

                // Badge: default (green) or no-escape (red)
                when {
                    profile.isDefault -> ProfileBadge(
                        text = stringResource(R.string.profile_badge_default),
                        textColor = colors.primaryDark,
                        bgColor = colors.primary.copy(alpha = 0.15f),
                    )
                    isNoEscape -> ProfileBadge(
                        text = stringResource(R.string.profile_badge_no_escape),
                        textColor = colors.lockedPrimary,
                        bgColor = colors.lockedPrimary.copy(alpha = 0.12f),
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp),
                )
            }

            AppIconsRow(icons = appIconBitmaps, totalCount = blockedCount)
        }
    }
}

@Composable
internal fun AllProfilesCard(
    profiles: List<Profile>,
    defaultName: String,
    onClick: () -> Unit,
) {
    val colors = LockTheme.colors
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Overlapping profile-type icons
            val shown = profiles.take(4)
            val circle = 34
            val overlap = 22
            Box(modifier = Modifier.width((circle + (shown.size - 1).coerceAtLeast(0) * overlap).dp).height(circle.dp)) {
                shown.forEachIndexed { i, p ->
                    val isNoEscape = ProfileType.fromValue(p.type) == ProfileType.NO_ESCAPE
                    val iconBg = if (isNoEscape) Color(0xFFFFF1E6) else Color(0xFFE8F5E9)
                    val iconTint = if (isNoEscape) Color(0xFFEF6C00) else colors.primaryDark
                    Box(
                        modifier = Modifier
                            .offset(x = (i * overlap).dp)
                            .size(circle.dp)
                            .clip(CircleShape)
                            .background(colors.cardContainer)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(iconBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (isNoEscape) Icons.Outlined.Timer else Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.profiles_all_title),
                    fontFamily = SatoshiFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = colors.onSurface,
                )
                Text(
                    text = stringResource(R.string.profiles_all_subtitle, profiles.size, defaultName),
                    fontFamily = SatoshiFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 13.sp,
                    color = colors.onSurfaceVariant,
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ProfileBadge(text: String, textColor: Color, bgColor: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            fontFamily = SatoshiFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            color = textColor,
        )
    }
}

@Composable
private fun AppIconsRow(
    icons: List<ImageBitmap>,
    totalCount: Int,
    maxVisible: Int = 5,
) {
    val colors = LockTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val visibleCount = minOf(icons.size, maxVisible, totalCount)
        icons.take(visibleCount).forEach { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.size(34.dp),
            )
        }
        val remaining = totalCount - visibleCount
        if (remaining > 0) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+$remaining",
                    fontFamily = SatoshiFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}
