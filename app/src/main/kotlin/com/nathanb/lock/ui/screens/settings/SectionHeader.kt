package com.nathanb.lock.ui.screens.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nathanb.lock.ui.theme.BrickTheme

@Composable
internal fun SectionHeader(title: String) {
    val colors = BrickTheme.colors
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp),
        fontWeight = FontWeight.Bold,
        color = colors.onSurfaceVariant,
        letterSpacing = MaterialTheme.typography.labelMedium.letterSpacing * 1.5f,
        modifier = Modifier.padding(bottom = 12.dp),
    )
}
