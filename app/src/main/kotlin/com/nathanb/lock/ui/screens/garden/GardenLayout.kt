package com.nathanb.lock.ui.screens.garden

/**
 * Isometric placement of the garden sprites. Pure Kotlin on purpose: unit-tested on the JVM.
 *
 * Every sprite is rendered with the same camera on the same square frame (see
 * design/garden/README.md), so one anchor fits them all: the frame centre. Distances are
 * expressed as fractions of the frame side, which makes them valid at any export size.
 */
object GardenLayout {

    /** Horizontal step between two neighbouring cells, as a fraction of the frame side. */
    const val STEP_X = 0.70710678f / 2.6f

    /** Vertical step: half the horizontal one (2:1 isometric). */
    const val STEP_Y = STEP_X / 2f

    // Where the drawing sits inside the frame, measured from its centre on the tallest and
    // widest sprites (the adult cherry tree overflows its tile sideways).
    private const val CONTENT_LEFT = 0.31f
    private const val CONTENT_RIGHT = 0.31f
    private const val CONTENT_TOP = 0.36f
    private const val CONTENT_BOTTOM = 0.352f

    /** A garden cell: [a] grows towards the bottom right of the screen, [b] towards the top right. */
    data class Cell(val a: Int, val b: Int)

    data class Point(val x: Float, val y: Float)

    data class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
    }

    /** Centre of the frame of [cell], for frames [frame] wide, relative to cell (0, 0). */
    fun center(cell: Cell, frame: Float): Point = Point(
        x = STEP_X * frame * (cell.a + cell.b),
        y = STEP_Y * frame * (cell.a - cell.b),
    )

    /** Far cells first, so a plant overflowing its tile covers the ones behind it. */
    fun <T> drawOrder(cells: Map<Cell, T>): List<Pair<Cell, T>> =
        cells.toList().sortedWith(compareBy({ it.first.a - it.first.b }, { it.first.a + it.first.b }))

    /** Box around everything drawn for [cells], relative to the centre of cell (0, 0). */
    fun bounds(cells: Collection<Cell>, frame: Float): Bounds {
        if (cells.isEmpty()) return Bounds(0f, 0f, 0f, 0f)
        val centers = cells.map { center(it, frame) }
        return Bounds(
            left = centers.minOf { it.x } - CONTENT_LEFT * frame,
            top = centers.minOf { it.y } - CONTENT_TOP * frame,
            right = centers.maxOf { it.x } + CONTENT_RIGHT * frame,
            bottom = centers.maxOf { it.y } + CONTENT_BOTTOM * frame,
        )
    }

    /**
     * Frame side that makes the garden fill a [width] x [height] area, capped at [maxFrame]
     * so a small garden on a tablet does not blow its sprites up.
     */
    fun fitFrame(cells: Collection<Cell>, width: Float, height: Float, maxFrame: Float): Float {
        val unit = bounds(cells, 1f)
        if (unit.width <= 0f || unit.height <= 0f || width <= 0f || height <= 0f) return 0f
        return minOf(width / unit.width, height / unit.height, maxFrame)
    }
}
