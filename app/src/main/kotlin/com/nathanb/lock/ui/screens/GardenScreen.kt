package com.nathanb.lock.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nathanb.lock.BuildConfig
import com.nathanb.lock.R
import com.nathanb.lock.data.garden.GardenRules
import com.nathanb.lock.data.repository.Garden
import com.nathanb.lock.ui.screens.garden.DropsInfoSheet
import com.nathanb.lock.ui.screens.garden.GardenInfoSheet
import com.nathanb.lock.ui.screens.garden.TerrainMiniature
import com.nathanb.lock.ui.screens.garden.TerrainTierSheet
import com.nathanb.lock.ui.screens.garden.TierState
import com.nathanb.lock.ui.screens.garden.tierColor
import com.nathanb.lock.ui.screens.garden.GardenLayout
import com.nathanb.lock.ui.screens.garden.GardenLayout.Cell
import com.nathanb.lock.ui.screens.garden.PlantPickerSheet
import com.nathanb.lock.ui.screens.garden.PlotSheet
import com.nathanb.lock.ui.screens.garden.RemovePlantSheet
import com.nathanb.lock.ui.screens.garden.isTall
import com.nathanb.lock.ui.screens.garden.sprite
import com.nathanb.lock.ui.theme.LockTheme
import com.nathanb.lock.ui.viewmodel.LockViewModel
import kotlin.math.roundToInt

/** Largest size a sprite frame is drawn at, so a small garden stays small on a tablet. */
private val MAX_FRAME = 420.dp

/** Opacity of a plant the user chose to hide. */
private const val HIDDEN_PLANT_ALPHA = 0.25f

@Composable
fun GardenScreen(viewModel: LockViewModel) {
    val colors = LockTheme.colors
    val garden by viewModel.garden.collectAsStateWithLifecycle()

    // Tile whose sheet is open, and whether the delete confirmation replaced it.
    var selected by remember { mutableStateOf<Cell?>(null) }
    var confirmRemoval by remember { mutableStateOf(false) }
    // Plants faded out to see and reach what stands behind. Screen state only: never stored.
    var hidden by remember { mutableStateOf(emptySet<Cell>()) }
    // Plant being moved, waiting for its destination tile.
    var moving by remember { mutableStateOf<Cell?>(null) }
    // Where it would go: chosen, shown outlined, and only applied once confirmed.
    var destination by remember { mutableStateOf<Cell?>(null) }
    var showGardenInfo by remember { mutableStateOf(false) }
    var showDropsInfo by remember { mutableStateOf(false) }
    // Terrain size whose explanation is open.
    var tierInfo by remember { mutableStateOf<Int?>(null) }
    val cancelMove = {
        moving = null
        destination = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.garden_title),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                ),
                color = colors.onSurface,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (hidden.isNotEmpty()) ShowAllButton(onClick = { hidden = emptySet() })
                garden?.let {
                    DropsBadge(
                        drops = it.drops,
                        onClick = { showDropsInfo = true },
                        onDebugLongPress = viewModel::debugGrantGardenDrops,
                    )
                }
            }
        }

        val current = garden ?: return@Column

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(colors.surfaceContainer),
        ) {
            GardenView(
                garden = current,
                hidden = hidden,
                highlighted = setOfNotNull(moving, destination),
                onCellTapped = { cell ->
                    val from = moving
                    when {
                        from == null -> {
                            confirmRemoval = false
                            selected = cell
                        }
                        // Tapping the plant itself cancels the move.
                        cell == from -> cancelMove()
                        else -> destination = cell
                    }
                },
                modifier = Modifier.fillMaxSize().padding(12.dp),
            )
        }

        val from = moving
        if (from != null) {
            val to = destination
            MoveControls(
                message = when {
                    to == null -> stringResource(R.string.garden_move_hint)
                    current.plants.containsKey(to.a to to.b) -> pluralStringResource(
                        R.plurals.garden_move_confirm_swap, GardenRules.MOVE_COST, GardenRules.MOVE_COST,
                    )
                    else -> pluralStringResource(
                        R.plurals.garden_move_confirm_here, GardenRules.MOVE_COST, GardenRules.MOVE_COST,
                    )
                },
                onCancel = cancelMove,
                onConfirm = if (to == null) {
                    null
                } else {
                    {
                        viewModel.moveInGarden(from.a, from.b, to.a, to.b)
                        hidden = hidden - from - to
                        cancelMove()
                    }
                },
            )
        } else {
            TerrainSummary(garden = current, onInfo = { showGardenInfo = true }, onTier = { tierInfo = it })
            Text(
                text = stringResource(
                    if (current.drops == 0) R.string.garden_hint_earn else R.string.garden_hint_touch,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
        }
        // Room for the floating tab bar.
        Spacer(Modifier.height(88.dp))
    }

    val current = garden ?: return
    val cell = selected
    val planted = cell?.let { current.plants[it.a to it.b] }
    val close = {
        selected = null
        confirmRemoval = false
    }
    when {
        showGardenInfo -> GardenInfoSheet(onDismiss = { showGardenInfo = false })
        showDropsInfo -> DropsInfoSheet(onDismiss = { showDropsInfo = false })
        tierInfo != null -> tierInfo?.let { size ->
            TerrainTierSheet(
                size = size,
                state = tierState(size, current.size),
                unlockDay = GardenRules.TERRAIN_THRESHOLD_DAYS.getOrNull(size - 2)
                    ?.takeUnless { size == 2 && current.startedLarger },
                wasOffered = size == 2 && current.startedLarger,
                currentDay = current.day,
                onDismiss = { tierInfo = null },
            )
        }
        cell == null -> Unit
        planted == null -> PlantPickerSheet(
            drops = current.drops,
            onPlant = { kind ->
                viewModel.plantInGarden(cell.a, cell.b, kind)
                close()
            },
            onDismiss = close,
        )
        confirmRemoval -> RemovePlantSheet(
            onConfirm = {
                viewModel.removeFromGarden(cell.a, cell.b)
                hidden = hidden - cell
                close()
            },
            onDismiss = close,
        )
        else -> PlotSheet(
            planted = planted,
            plantedAt = current.plantedAt[cell.a to cell.b] ?: 0L,
            drops = current.drops,
            isHidden = cell in hidden,
            onGrow = {
                viewModel.growInGarden(cell.a, cell.b)
                close()
            },
            onMove = {
                destination = null
                moving = cell
                close()
            },
            onToggleHidden = {
                hidden = if (cell in hidden) hidden - cell else hidden + cell
                close()
            },
            onUndoPlanting = {
                viewModel.undoPlantInGarden(cell.a, cell.b)
                hidden = hidden - cell
                close()
            },
            onDelete = { confirmRemoval = true },
            onDismiss = close,
        )
    }
}

/** Day of the garden, when the terrain grows next, and the four terrain sizes. */
@Composable
private fun TerrainSummary(garden: Garden, onInfo: () -> Unit, onTier: (Int) -> Unit) {
    val colors = LockTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.garden_day, garden.day),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 36.sp,
                    ),
                    color = colors.primary,
                )
                val infoLabel = stringResource(R.string.garden_info_title)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceContainer)
                        .clickable(role = Role.Button, onClickLabel = infoLabel, onClick = onInfo),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = infoLabel,
                        tint = colors.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            garden.daysUntilNextSize?.let { days ->
                Text(
                    text = pluralStringResource(R.plurals.garden_next_tier, days, garden.size + 1, days),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..GardenRules.MAX_TERRAIN_SIZE).forEach { size ->
                val label = when {
                    size == 1 -> stringResource(R.string.garden_day, 1)
                    size == 2 && garden.startedLarger -> stringResource(R.string.garden_tier_offered)
                    else -> stringResource(R.string.garden_day, GardenRules.TERRAIN_THRESHOLD_DAYS[size - 2])
                }
                TerrainTier(
                    size = size,
                    label = label,
                    state = tierState(size, garden.size),
                    onClick = { onTier(size) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun tierState(size: Int, current: Int): TierState = when {
    size == current -> TierState.CURRENT
    size < current -> TierState.REACHED
    else -> TierState.AHEAD
}

@Composable
private fun TerrainTier(
    size: Int,
    label: String,
    state: TierState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LockTheme.colors
    val shape = RoundedCornerShape(14.dp)
    val textColor = if (state == TierState.AHEAD) colors.onSurfaceVariant.copy(alpha = 0.7f) else colors.onSurface
    Column(
        modifier = modifier
            .clip(shape)
            .then(
                if (state == TierState.CURRENT) {
                    Modifier.background(colors.cardContainer).border(1.5.dp, colors.primary, shape)
                } else {
                    Modifier
                },
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TerrainMiniature(size = size, color = tierColor(state), modifier = Modifier.size(32.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.garden_tier_size, size),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (state == TierState.CURRENT) FontWeight.Black else FontWeight.Bold,
                color = textColor,
                maxLines = 1,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (state == TierState.AHEAD) textColor else colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** Shown in place of the terrain summary while a plant is being moved. */
@Composable
private fun MoveControls(message: String, onCancel: () -> Unit, onConfirm: (() -> Unit)?) {
    val colors = LockTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onCancel) {
                Text(text = stringResource(R.string.action_cancel), color = colors.onSurfaceVariant)
            }
            if (onConfirm != null) {
                Button(
                    onClick = onConfirm,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                ) {
                    Text(
                        text = stringResource(R.string.action_confirm),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun ShowAllButton(onClick: () -> Unit) {
    val colors = LockTheme.colors
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(colors.cardContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Visibility,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = stringResource(R.string.garden_show_all),
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurface,
            maxLines = 1,
        )
    }
}

/** Balance of drops. A tap explains them; debug builds credit 100 on a long press. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DropsBadge(drops: Int, onClick: () -> Unit, onDebugLongPress: () -> Unit) {
    val colors = LockTheme.colors
    val description = pluralStringResource(R.plurals.garden_drops, drops, drops)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(colors.cardContainer)
            .combinedClickable(
                role = Role.Button,
                onLongClick = if (BuildConfig.DEBUG) onDebugLongPress else null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.WaterDrop,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = drops.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface,
        )
    }
}

/** Where the garden sits in its area: frame side and screen position of cell (0, 0). */
private data class Placement(val frame: Float, val originX: Float, val originY: Float)

private fun place(cells: Collection<Cell>, area: Size, maxFrame: Float): Placement? {
    val frame = GardenLayout.fitFrame(cells, area.width, area.height, maxFrame)
    if (frame <= 0f) return null
    val bounds = GardenLayout.bounds(cells, frame)
    return Placement(
        frame = frame,
        originX = (area.width - bounds.width) / 2f - bounds.left,
        originY = (area.height - bounds.height) / 2f - bounds.top,
    )
}

/** Draws the terrain as large as the area allows, centred, and reports the tile touched. */
@Composable
private fun GardenView(
    garden: Garden,
    hidden: Set<Cell>,
    highlighted: Set<Cell>,
    onCellTapped: (Cell) -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalContext.current.resources
    val cells = remember(garden.size) {
        (0 until garden.size).flatMap { a -> (0 until garden.size).map { b -> Cell(a, b) } }
    }
    // Sprite of every tile: the plant standing on it, or bare soil.
    val tiles: Map<Cell, Int> = remember(garden.plants, cells) {
        cells.associateWith { garden.plants[it.a to it.b]?.sprite() ?: R.drawable.garden_tile }
    }
    val images: Map<Int, ImageBitmap> = remember(tiles) {
        (tiles.values + R.drawable.garden_tile).distinct()
            .associateWith { ImageBitmap.imageResource(resources, it) }
    }
    val ordered = remember(tiles) { GardenLayout.drawOrder(tiles) }
    // Hidden plants are left out: touches go through them to whatever stands behind.
    val tallPlants = remember(garden.plants, hidden) {
        garden.plants.filterValues { it.isTall }.keys.map { Cell(it.first, it.second) }.toSet() - hidden
    }
    val lowPlants = remember(garden.plants, hidden) {
        garden.plants.filterValues { !it.isTall }.keys.map { Cell(it.first, it.second) }.toSet() - hidden
    }
    val highlightColor = LockTheme.colors.primary
    val maxFrame = with(LocalDensity.current) { MAX_FRAME.toPx() }
    val description = stringResource(R.string.garden_title)

    Canvas(
        modifier = modifier
            .semantics { contentDescription = description }
            .pointerInput(cells, lowPlants, tallPlants, maxFrame) {
                detectTapGestures { tap: Offset ->
                    val area = Size(size.width.toFloat(), size.height.toFloat())
                    val placement = place(cells, area, maxFrame) ?: return@detectTapGestures
                    GardenLayout.cellAt(
                        x = tap.x - placement.originX,
                        y = tap.y - placement.originY,
                        frame = placement.frame,
                        size = garden.size,
                        lowPlants = lowPlants,
                        tallPlants = tallPlants,
                    )?.let(onCellTapped)
                }
            },
    ) {
        val placement = place(cells, size, maxFrame) ?: return@Canvas
        val side = placement.frame.roundToInt()
        ordered.forEach { (cell, spriteRes) ->
            val image = images[spriteRes] ?: return@forEach
            val center = GardenLayout.center(cell, placement.frame)
            val offset = IntOffset(
                (placement.originX + center.x - placement.frame / 2f).roundToInt(),
                (placement.originY + center.y - placement.frame / 2f).roundToInt(),
            )
            val isHidden = cell in hidden && spriteRes != R.drawable.garden_tile
            if (isHidden) {
                // Bare soil underneath, then the plant as a ghost.
                images[R.drawable.garden_tile]?.let {
                    drawImage(it, dstOffset = offset, dstSize = IntSize(side, side), filterQuality = FilterQuality.High)
                }
            }
            drawImage(
                image = image,
                dstOffset = offset,
                dstSize = IntSize(side, side),
                alpha = if (isHidden) HIDDEN_PLANT_ALPHA else 1f,
                filterQuality = FilterQuality.High,
            )
            if (cell in highlighted) {
                // Outline of the soil of the tiles a move is about.
                val soil = GardenLayout.soilCenter(cell, placement.frame)
                val cx = placement.originX + soil.x
                val cy = placement.originY + soil.y
                val halfW = GardenLayout.STEP_X * placement.frame * 0.9f
                val halfH = GardenLayout.STEP_Y * placement.frame * 0.9f
                val diamond = Path().apply {
                    moveTo(cx, cy - halfH); lineTo(cx + halfW, cy); lineTo(cx, cy + halfH); lineTo(cx - halfW, cy); close()
                }
                drawPath(diamond, color = highlightColor, style = Stroke(width = 3.dp.toPx()))
            }
        }
    }
}
