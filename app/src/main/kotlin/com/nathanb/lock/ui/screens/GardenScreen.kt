package com.nathanb.lock.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import com.nathanb.lock.R
import com.nathanb.lock.data.repository.Garden
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

@Composable
fun GardenScreen(viewModel: LockViewModel) {
    val colors = LockTheme.colors
    val garden by viewModel.garden.collectAsStateWithLifecycle()

    // Tile whose sheet is open, and whether the removal confirmation replaced it.
    var selected by remember { mutableStateOf<Cell?>(null) }
    var confirmRemoval by remember { mutableStateOf(false) }

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
            garden?.let { DropsBadge(drops = it.drops) }
        }

        val current = garden ?: return@Column

        GardenView(
            garden = current,
            onCellTapped = { cell ->
                confirmRemoval = false
                selected = cell
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 16.dp),
        )

        val footer = when {
            current.plants.isEmpty() -> stringResource(R.string.garden_empty_hint)
            current.daysUntilNextSize != null -> pluralStringResource(
                R.plurals.garden_next_size, current.daysUntilNextSize, current.daysUntilNextSize,
            )
            else -> ""
        }
        Text(
            text = footer,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                // Keeps the line clear of the floating tab bar.
                .padding(bottom = 96.dp),
        )
    }

    val current = garden ?: return
    val cell = selected ?: return
    val planted = current.plants[cell.a to cell.b]
    val close = {
        selected = null
        confirmRemoval = false
    }
    when {
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
                close()
            },
            onDismiss = close,
        )
        else -> PlotSheet(
            planted = planted,
            drops = current.drops,
            onGrow = {
                viewModel.growInGarden(cell.a, cell.b)
                close()
            },
            onReplace = { confirmRemoval = true },
            onDismiss = close,
        )
    }
}

@Composable
private fun DropsBadge(drops: Int) {
    val colors = LockTheme.colors
    val description = pluralStringResource(R.plurals.garden_drops, drops, drops)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(colors.cardContainer)
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
        tiles.values.distinct().associateWith { ImageBitmap.imageResource(resources, it) }
    }
    val ordered = remember(tiles) { GardenLayout.drawOrder(tiles) }
    val tallPlants = remember(garden.plants) {
        garden.plants.filterValues { it.isTall }.keys.map { Cell(it.first, it.second) }.toSet()
    }
    val lowPlants = remember(garden.plants) {
        garden.plants.filterValues { !it.isTall }.keys.map { Cell(it.first, it.second) }.toSet()
    }
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
            drawImage(
                image = image,
                dstOffset = IntOffset(
                    (placement.originX + center.x - placement.frame / 2f).roundToInt(),
                    (placement.originY + center.y - placement.frame / 2f).roundToInt(),
                ),
                dstSize = IntSize(side, side),
                filterQuality = FilterQuality.High,
            )
        }
    }
}
