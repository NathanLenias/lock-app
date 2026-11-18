package com.nathanb.lock.ui.screens.garden

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Yard
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.nathanb.lock.R
import com.nathanb.lock.data.garden.GardenRules
import com.nathanb.lock.data.garden.PlantKind
import com.nathanb.lock.data.repository.Planted
import com.nathanb.lock.ui.components.LockBottomSheet
import com.nathanb.lock.ui.theme.LockTheme
import com.nathanb.lock.ui.theme.SatoshiFamily
import kotlin.math.roundToInt

/** Empty tile: what to plant. Plants the balance cannot pay for are shown but dimmed. */
@Composable
fun PlantPickerSheet(
    drops: Int,
    onPlant: (PlantKind) -> Unit,
    onDismiss: () -> Unit,
) {
    LockBottomSheet(
        onDismiss = onDismiss,
        icon = Icons.Outlined.Yard,
        title = stringResource(R.string.garden_plant_title),
        body = pluralStringResource(R.plurals.garden_balance, drops, drops) + " " +
            stringResource(R.string.garden_earn_hint),
        actions = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlantKind.entries.forEach { kind ->
                    PlantRow(kind = kind, affordable = drops >= kind.stepCost, onClick = { onPlant(kind) })
                }
            }
        },
    )
}

@Composable
private fun PlantRow(kind: PlantKind, affordable: Boolean, onClick: () -> Unit) {
    val colors = LockTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.cardContainer)
            .clickable(enabled = affordable, role = Role.Button, onClick = onClick)
            .alpha(if (affordable) 1f else 0.4f)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SpriteThumbnail(sprite = kind.grownSprite(), modifier = Modifier.size(52.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(kind.nameRes()),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
            )
            Text(
                text = pluralStringResource(R.plurals.garden_plant_cost, kind.stepCost, kind.stepCost, kind.stages),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

/** The drawn part of a sprite frame, without its transparent margins. */
@Composable
private fun SpriteThumbnail(sprite: Int, modifier: Modifier = Modifier) {
    val resources = LocalContext.current.resources
    val image = remember(sprite) { ImageBitmap.imageResource(resources, sprite) }
    Canvas(modifier = modifier) {
        // Same square crop for every sprite: wide enough for the cherry tree's crown.
        val side = (image.width * 0.70f).roundToInt()
        drawImage(
            image = image,
            srcOffset = IntOffset((image.width - side) / 2, (image.height * 0.15f).roundToInt()),
            srcSize = IntSize(side, side),
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            filterQuality = FilterQuality.High,
        )
    }
}

/** Planted tile: grow it one step, or free the tile. */
@Composable
fun PlotSheet(
    planted: Planted,
    drops: Int,
    isHidden: Boolean,
    onGrow: () -> Unit,
    onMove: () -> Unit,
    onToggleHidden: () -> Unit,
    onReplace: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LockTheme.colors
    val cost = planted.kind.stepCost
    val affordable = drops >= cost
    val body = buildString {
        if (planted.isFullyGrown) {
            append(stringResource(R.string.garden_fully_grown))
        } else {
            append(stringResource(R.string.garden_stage, planted.stage, planted.kind.stages))
            append(" ")
            append(pluralStringResource(R.plurals.garden_balance, drops, drops))
            if (!affordable) {
                append("\n\n")
                append(stringResource(R.string.garden_not_enough))
                append(" ")
                append(stringResource(R.string.garden_earn_hint))
            }
        }
    }
    LockBottomSheet(
        onDismiss = onDismiss,
        icon = Icons.Outlined.WaterDrop,
        title = stringResource(planted.kind.nameRes()),
        body = body,
        actions = {
            if (!planted.isFullyGrown) {
                Button(
                    onClick = onGrow,
                    enabled = affordable,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                ) {
                    Text(
                        text = pluralStringResource(R.plurals.garden_grow_action, cost, cost),
                        fontFamily = SatoshiFamily,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            val canMove = drops >= GardenRules.MOVE_COST
            SheetTextAction(
                label = pluralStringResource(
                    R.plurals.garden_move_action, GardenRules.MOVE_COST, GardenRules.MOVE_COST,
                ),
                enabled = canMove,
                onClick = onMove,
            )
            SheetTextAction(
                label = stringResource(if (isHidden) R.string.garden_show_action else R.string.garden_hide_action),
                onClick = onToggleHidden,
            )
            SheetTextAction(label = stringResource(R.string.garden_replace_action), onClick = onReplace)
        },
    )
}

@Composable
private fun SheetTextAction(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    val colors = LockTheme.colors
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontFamily = SatoshiFamily,
            color = colors.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.4f),
        )
    }
}

/** Removing a plant does not give its drops back: said before it happens. */
@Composable
fun RemovePlantSheet(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LockTheme.colors
    LockBottomSheet(
        onDismiss = onDismiss,
        icon = Icons.Outlined.DeleteOutline,
        title = stringResource(R.string.garden_remove_confirm_title),
        body = stringResource(R.string.garden_remove_confirm_body),
        actions = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.lockedPrimary),
            ) {
                Text(
                    text = stringResource(R.string.garden_remove_confirm_action),
                    fontFamily = SatoshiFamily,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    fontFamily = SatoshiFamily,
                    color = colors.onSurfaceVariant,
                )
            }
        },
    )
}
