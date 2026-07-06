package com.nathanb.lock.ui.screens.apppicker

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nathanb.lock.ui.theme.LockTheme
import com.nathanb.lock.ui.theme.SatoshiFamily
import com.nathanb.lock.ui.viewmodel.InstalledApp

@Composable
internal fun AppCard(
    app: InstalledApp,
    isSelected: Boolean,
    icon: ImageBitmap?,
    onToggle: () -> Unit,
) {
    val colors = LockTheme.colors

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clickable(onClick = onToggle),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Center content: icon + name
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (icon != null) {
                    Image(
                        bitmap = icon,
                        contentDescription = app.label,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    // Placeholder
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(colors.surfaceContainer, RoundedCornerShape(10.dp)),
                    )
                }
                Text(
                    app.label,
                    fontFamily = SatoshiFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            // Checkbox top right
            CustomCheckbox(
                checked = isSelected,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
            )
        }
    }
}

@Composable
private fun CustomCheckbox(
    checked: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LockTheme.colors

    Box(
        modifier = modifier
            .size(18.dp)
            .then(
                if (checked) {
                    Modifier.background(colors.primary, RoundedCornerShape(6.dp))
                } else {
                    Modifier
                        .background(colors.surfaceContainer, RoundedCornerShape(6.dp))
                        .border(1.5.dp, colors.onSurfaceVariant, RoundedCornerShape(6.dp))
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}
