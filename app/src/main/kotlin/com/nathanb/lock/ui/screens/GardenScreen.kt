package com.nathanb.lock.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nathanb.lock.R
import com.nathanb.lock.ui.screens.garden.GardenLayout
import com.nathanb.lock.ui.screens.garden.GardenLayout.Cell
import com.nathanb.lock.ui.theme.LockTheme
import kotlin.math.roundToInt

/**
 * Demo garden, only there to judge the sprites on a device.
 * TODO(garden): replace with the user's garden once the data model exists.
 */
private val DEMO_GARDEN: Map<Cell, Int> = mapOf(
    Cell(0, 2) to R.drawable.garden_bush_4_hedge,
    Cell(1, 2) to R.drawable.garden_apple_2_young,
    Cell(2, 2) to R.drawable.garden_apple_3_adult,
    Cell(0, 1) to R.drawable.garden_cherry_3_adult,
    Cell(1, 1) to R.drawable.garden_tulip_4_field_redyellow,
    Cell(2, 1) to R.drawable.garden_flowers_3_young,
    Cell(0, 0) to R.drawable.garden_tulip_4_field_mix,
    Cell(1, 0) to R.drawable.garden_flowers_4_bed,
    Cell(2, 0) to R.drawable.garden_flowers_2_single,
)

/** Largest size a sprite frame is drawn at, so a small garden stays small on a tablet. */
private val MAX_FRAME = 420.dp

@Composable
fun GardenScreen() {
    val colors = LockTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.garden_title),
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
            ),
            color = colors.onSurface,
            modifier = Modifier.padding(top = 16.dp),
        )
        GardenView(
            garden = DEMO_GARDEN,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                // Keeps the garden clear of the floating tab bar.
                .padding(top = 16.dp, bottom = 88.dp),
        )
    }
}

/** Draws [garden] (cell -> sprite) as large as the available area allows, centred. */
@Composable
private fun GardenView(garden: Map<Cell, Int>, modifier: Modifier = Modifier) {
    val sprites: Map<Int, ImageBitmap> = garden.values.distinct().associateWith { sprite(it) }
    val ordered = GardenLayout.drawOrder(garden)
    val maxFrame = with(LocalDensity.current) { MAX_FRAME.toPx() }
    val description = stringResource(R.string.garden_title)

    Canvas(modifier = modifier.semantics { contentDescription = description }) {
        val frame = GardenLayout.fitFrame(garden.keys, size.width, size.height, maxFrame)
        if (frame <= 0f) return@Canvas
        val bounds = GardenLayout.bounds(garden.keys, frame)
        // Screen position of the centre of cell (0, 0) that centres the whole garden.
        val originX = (size.width - bounds.width) / 2f - bounds.left
        val originY = (size.height - bounds.height) / 2f - bounds.top
        val side = frame.roundToInt()

        ordered.forEach { (cell, spriteRes) ->
            val image = sprites[spriteRes] ?: return@forEach
            val center = GardenLayout.center(cell, frame)
            drawImage(
                image = image,
                dstOffset = IntOffset(
                    (originX + center.x - frame / 2f).roundToInt(),
                    (originY + center.y - frame / 2f).roundToInt(),
                ),
                dstSize = IntSize(side, side),
                filterQuality = FilterQuality.High,
            )
        }
    }
}

@Composable
private fun sprite(@DrawableRes id: Int): ImageBitmap = ImageBitmap.imageResource(id)
