package com.nathanb.lock.ui.screens.garden

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nathanb.lock.R
import com.nathanb.lock.data.garden.GardenRules
import com.nathanb.lock.data.garden.PlantKind
import com.nathanb.lock.data.repository.Planted
import com.nathanb.lock.ui.components.LockBottomSheet
import com.nathanb.lock.ui.components.LockSheetFrame
import com.nathanb.lock.ui.theme.LockTheme
import com.nathanb.lock.ui.theme.SatoshiFamily
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val SheetPadding = Modifier
    .fillMaxWidth()
    .padding(horizontal = 16.dp)
    .padding(top = 4.dp, bottom = 16.dp)

/** Empty tile: pick a plant, see what it becomes and what it costs, then plant its sprout. */
@Composable
fun PlantPickerSheet(
    drops: Int,
    onPlant: (PlantKind) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LockTheme.colors
    // Opens on the first plant the balance can pay for.
    var selected by remember {
        mutableStateOf(PlantKind.entries.firstOrNull { it.stepCost <= drops } ?: PlantKind.entries.first())
    }
    val affordable = drops >= selected.stepCost

    LockSheetFrame(onDismiss = onDismiss) {
        Column(
            modifier = SheetPadding
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.garden_plant_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                )
                DropsChip(
                    drops = drops,
                    container = colors.cardContainer,
                    content = colors.onSurface,
                    iconTint = colors.primary,
                    description = pluralStringResource(R.plurals.garden_drops, drops, drops),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlantKind.entries.forEach { kind ->
                    PlantChoice(
                        kind = kind,
                        isSelected = kind == selected,
                        affordable = drops >= kind.stepCost,
                        onClick = { selected = kind },
                    )
                }
            }

            PlantDetail(kind = selected)

            Button(
                onClick = { onPlant(selected) },
                enabled = affordable,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
            ) {
                Text(
                    text = stringResource(R.string.garden_plant_action),
                    fontFamily = SatoshiFamily,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.width(10.dp))
                DropsChip(
                    drops = selected.stepCost,
                    container = Color.White.copy(alpha = 0.2f),
                    content = Color.White,
                )
            }

            Text(
                text = if (affordable) {
                    val left = drops - selected.stepCost
                    pluralStringResource(R.plurals.garden_plant_remaining, left, left)
                } else {
                    val missing = selected.stepCost - drops
                    pluralStringResource(R.plurals.garden_plant_missing, missing, missing)
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PlantChoice(kind: PlantKind, isSelected: Boolean, affordable: Boolean, onClick: () -> Unit) {
    val colors = LockTheme.colors
    val shape = RoundedCornerShape(14.dp)
    val name = stringResource(kind.nameRes())
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(shape)
            .background(colors.cardContainer)
            .then(if (isSelected) Modifier.border(2.dp, colors.primary, shape) else Modifier)
            .clickable(role = Role.Tab, onClickLabel = name, onClick = onClick)
            .semantics { selected = isSelected },
        contentAlignment = Alignment.Center,
    ) {
        SpriteThumbnail(
            sprite = kind.grownSprite(),
            modifier = Modifier.size(66.dp).alpha(if (affordable) 1f else 0.4f),
            description = name,
        )
    }
}

@Composable
private fun PlantDetail(kind: PlantKind) {
    val colors = LockTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.cardContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SpriteThumbnail(sprite = kind.grownSprite(), modifier = Modifier.size(104.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(kind.nameRes()),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.garden_plant_cost,
                        kind.stepCost, kind.stepCost, kind.stages, kind.stepCost * kind.stages,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    lineHeight = 18.sp,
                )
            }
        }

        val sprites = kind.stageSprites()
        val labels = kind.stageNames()
        val thumb: Dp = if (sprites.size > 3) 46.dp else 56.dp
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surface)
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            sprites.forEachIndexed { index, sprite ->
                if (index > 0) {
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp),
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SpriteThumbnail(sprite = sprite, modifier = Modifier.size(thumb))
                    Text(
                        text = stringResource(labels[index]),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                        color = if (index == 0) colors.onSurface else colors.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** A drop and a number: a balance or a price. */
@Composable
private fun DropsChip(
    drops: Int,
    container: Color,
    content: Color,
    iconTint: Color = content,
    description: String? = null,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(container)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .then(
                if (description != null) {
                    Modifier.semantics(mergeDescendants = true) {}
                } else {
                    Modifier
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.WaterDrop,
            contentDescription = description,
            tint = iconTint,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = drops.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = content,
        )
    }
}

/** The drawn part of a sprite frame, without its transparent margins. */
@Composable
private fun SpriteThumbnail(sprite: Int, modifier: Modifier = Modifier, description: String? = null) {
    val resources = LocalContext.current.resources
    val image = remember(sprite) { ImageBitmap.imageResource(resources, sprite) }
    Canvas(
        modifier = if (description != null) {
            modifier.semantics { this.contentDescription = description }
        } else {
            modifier
        },
    ) {
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

/**
 * Planted tile: grow it one step, move it, hide it, and either take the planting back
 * (for [GardenRules.UNDO_PLANT_WINDOW_MS] after it was made) or delete the plant.
 */
@Composable
fun PlotSheet(
    planted: Planted,
    plantedAt: Long,
    drops: Int,
    isHidden: Boolean,
    onGrow: () -> Unit,
    onMove: () -> Unit,
    onToggleHidden: () -> Unit,
    onUndoPlanting: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LockTheme.colors
    val cost = planted.kind.stepCost

    // Ticks while the sheet is open, for the undo countdown.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(250)
            now = System.currentTimeMillis()
        }
    }
    // One second short of the real window, so the row never outlives what the data accepts.
    val undoLeftMs = plantedAt + GardenRules.UNDO_PLANT_WINDOW_MS - 1000 - now
    val canUndo = planted.stage == 1 && undoLeftMs > 0

    LockSheetFrame(onDismiss = onDismiss) {
        Column(
            modifier = SheetPadding
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(planted.kind.nameRes()),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = when {
                        planted.isFullyGrown -> stringResource(R.string.garden_fully_grown)
                        canUndo -> stringResource(R.string.garden_stage, planted.stage, planted.kind.stages) +
                            " " + stringResource(R.string.garden_just_planted)
                        else -> stringResource(R.string.garden_stage, planted.stage, planted.kind.stages)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            if (!planted.isFullyGrown) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onGrow,
                        enabled = drops >= cost,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                    ) {
                        Text(
                            text = stringResource(R.string.garden_grow_action),
                            fontFamily = SatoshiFamily,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Spacer(Modifier.width(10.dp))
                        DropsChip(drops = cost, container = Color.White.copy(alpha = 0.2f), content = Color.White)
                    }
                    if (drops < cost) {
                        val missing = cost - drops
                        Text(
                            text = pluralStringResource(R.plurals.garden_plant_missing, missing, missing),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.cardContainer),
            ) {
                ActionRow(
                    icon = Icons.Outlined.OpenWith,
                    title = stringResource(R.string.garden_move_action),
                    subtitle = stringResource(R.string.garden_move_subtitle),
                    enabled = drops >= GardenRules.MOVE_COST,
                    onClick = onMove,
                    trailing = {
                        DropsChip(
                            drops = GardenRules.MOVE_COST,
                            container = colors.surfaceContainer,
                            content = colors.onSurface,
                        )
                        Chevron()
                    },
                )
                RowDivider()
                ActionRow(
                    icon = if (isHidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                    title = stringResource(if (isHidden) R.string.garden_show_action else R.string.garden_hide_action),
                    subtitle = stringResource(
                        if (isHidden) R.string.garden_show_subtitle else R.string.garden_hide_subtitle,
                    ),
                    onClick = onToggleHidden,
                    trailing = { Chevron() },
                )
                RowDivider()
                if (canUndo) {
                    ActionRow(
                        icon = Icons.AutoMirrored.Outlined.Undo,
                        title = stringResource(R.string.garden_undo_action),
                        subtitle = stringResource(R.string.garden_undo_subtitle),
                        onClick = onUndoPlanting,
                        trailing = {
                            Text(
                                text = countdown(undoLeftMs),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = colors.onSurface,
                            )
                        },
                    )
                    val fraction = (undoLeftMs.toFloat() / GardenRules.UNDO_PLANT_WINDOW_MS).coerceIn(0f, 1f)
                    Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(colors.surfaceContainer)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .height(4.dp)
                                .background(colors.primary),
                        )
                    }
                } else {
                    ActionRow(
                        icon = Icons.Outlined.DeleteOutline,
                        title = stringResource(R.string.garden_delete_action),
                        subtitle = stringResource(R.string.garden_delete_subtitle),
                        tint = colors.error,
                        onClick = onDelete,
                        trailing = { Chevron() },
                    )
                }
            }
        }
    }
}

/** Minutes and seconds left, written the way the app language writes numbers. */
@Composable
private fun countdown(leftMs: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    val seconds = (leftMs.coerceAtLeast(0L) + 999) / 1000
    return String.format(locale, "%d:%02d", seconds / 60, seconds % 60)
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = LockTheme.colors.onSurface,
    trailing: @Composable () -> Unit = {},
) {
    val colors = LockTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = tint,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            trailing()
        }
    }
}

@Composable
private fun Chevron() {
    Icon(
        imageVector = Icons.Outlined.ChevronRight,
        contentDescription = null,
        tint = LockTheme.colors.onSurfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.size(18.dp),
    )
}

@Composable
private fun RowDivider() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(LockTheme.colors.surfaceContainer))
}

/** Deleting a plant does not give its drops back: said before it happens. */
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
                    text = stringResource(R.string.garden_delete_action),
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
