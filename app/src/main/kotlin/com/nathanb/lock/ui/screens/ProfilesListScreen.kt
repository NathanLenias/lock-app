package com.nathanb.lock.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nathanb.lock.R
import com.nathanb.lock.data.model.Profile
import com.nathanb.lock.data.model.ProfileType
import com.nathanb.lock.ui.theme.LockTheme
import com.nathanb.lock.ui.theme.SatoshiFamily
import com.nathanb.lock.ui.viewmodel.LockViewModel

private const val MAX_PROFILES = 5

@Composable
fun ProfilesListScreen(
    viewModel: LockViewModel,
    onBack: () -> Unit,
    onNavigateToProfileDetail: (Long) -> Unit,
    onNewProfile: () -> Unit,
) {
    val colors = LockTheme.colors
    val profiles by viewModel.profilesSorted.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = colors.surface,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .height(56.dp)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = colors.primary,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.profiles_section_title),
                        fontFamily = SatoshiFamily,
                        fontWeight = FontWeight.Black,
                        fontSize = 24.sp,
                        color = colors.onSurface,
                        letterSpacing = (-0.5).sp,
                    )
                    Text(
                        text = stringResource(R.string.profiles_manage_subtitle),
                        fontFamily = SatoshiFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.sp,
                        color = colors.onSurfaceVariant,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surfaceContainer)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.profiles_count_badge, profiles.size, MAX_PROFILES),
                        fontFamily = SatoshiFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            profiles.forEach { p ->
                ProfileListItem(profile = p, onClick = { onNavigateToProfileDetail(p.id) })
            }

            Spacer(Modifier.height(6.dp))

            if (profiles.size < MAX_PROFILES) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    onClick = onNewProfile,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = null,
                            tint = colors.primaryDark,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = stringResource(R.string.profiles_new),
                            fontFamily = SatoshiFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = colors.primaryDark,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.profiles_max_reached_text, MAX_PROFILES),
                    fontFamily = SatoshiFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 13.sp,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
            }
        }
    }

}

@Composable
private fun ProfileListItem(
    profile: Profile,
    onClick: () -> Unit,
) {
    val colors = LockTheme.colors
    val isNoEscape = ProfileType.fromValue(profile.type) == ProfileType.NO_ESCAPE
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
            val iconBg = if (isNoEscape) Color(0xFFFFF1E6) else Color(0xFFE8F5E9)
            val iconTint = if (isNoEscape) Color(0xFFEF6C00) else colors.primaryDark
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(iconBg),
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = profile.name,
                        fontFamily = SatoshiFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = colors.onSurface,
                    )
                    if (profile.isDefault) {
                        ProfileListBadge(
                            text = stringResource(R.string.profile_badge_default),
                            textColor = colors.primaryDark,
                            bgColor = colors.primary.copy(alpha = 0.15f),
                        )
                    }
                }
                val typeShort = stringResource(
                    if (isNoEscape) R.string.profile_type_short_no_escape
                    else R.string.profile_type_short_standard,
                )
                Text(
                    text = stringResource(R.string.profile_row_subtitle, typeShort, profile.blockedPackages.size),
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
private fun ProfileListBadge(text: String, textColor: Color, bgColor: Color) {
    Box(
        modifier = Modifier
            .padding(start = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp),
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
