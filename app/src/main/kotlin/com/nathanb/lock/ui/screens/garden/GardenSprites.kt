package com.nathanb.lock.ui.screens.garden

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.nathanb.lock.R
import com.nathanb.lock.data.garden.PlantKind
import com.nathanb.lock.data.repository.Planted

/** Sprite of each stage, sprout first. One entry per [PlantKind.stages]. */
private fun PlantKind.sprites(): List<Int> = when (this) {
    PlantKind.TULIPS_RED_YELLOW -> listOf(
        R.drawable.garden_tulip_1_sprout, R.drawable.garden_tulip_2_single,
        R.drawable.garden_tulip_3_young, R.drawable.garden_tulip_4_field_redyellow,
    )
    PlantKind.TULIPS_MIX -> listOf(
        R.drawable.garden_tulip_1_sprout, R.drawable.garden_tulip_2_single,
        R.drawable.garden_tulip_3_young, R.drawable.garden_tulip_4_field_mix,
    )
    PlantKind.FLOWERS -> listOf(
        R.drawable.garden_flowers_1_sprout, R.drawable.garden_flowers_2_single,
        R.drawable.garden_flowers_3_young, R.drawable.garden_flowers_4_bed,
    )
    PlantKind.BUSH -> listOf(
        R.drawable.garden_bush_1_sprout, R.drawable.garden_bush_2_small,
        R.drawable.garden_bush_3_medium, R.drawable.garden_bush_4_hedge,
    )
    PlantKind.CHERRY -> listOf(
        R.drawable.garden_cherry_1_sprout, R.drawable.garden_cherry_2_young, R.drawable.garden_cherry_3_adult,
    )
    PlantKind.APPLE -> listOf(
        R.drawable.garden_apple_1_sprout, R.drawable.garden_apple_2_young, R.drawable.garden_apple_3_adult,
    )
}

@DrawableRes
fun Planted.sprite(): Int = kind.sprites().let { it[(stage - 1).coerceIn(0, it.lastIndex)] }

/** The plant fully grown, to show what is being bought. */
@DrawableRes
fun PlantKind.grownSprite(): Int = sprites().last()

/** Trees past the sprout stand well above their tile: they are touched by their crown too. */
val Planted.isTall: Boolean
    get() = (kind == PlantKind.CHERRY || kind == PlantKind.APPLE) && stage > 1

@StringRes
fun PlantKind.nameRes(): Int = when (this) {
    PlantKind.TULIPS_RED_YELLOW -> R.string.garden_plant_tulips_red_yellow
    PlantKind.TULIPS_MIX -> R.string.garden_plant_tulips_mix
    PlantKind.FLOWERS -> R.string.garden_plant_flowers
    PlantKind.BUSH -> R.string.garden_plant_bush
    PlantKind.CHERRY -> R.string.garden_plant_cherry
    PlantKind.APPLE -> R.string.garden_plant_apple
}
