package com.nathanb.lock.ui.garden

import com.nathanb.lock.ui.screens.garden.GardenLayout
import com.nathanb.lock.ui.screens.garden.GardenLayout.Cell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GardenLayoutTest {

    private val grid3 = (0..2).flatMap { a -> (0..2).map { b -> Cell(a, b) } }

    @Test
    fun `neighbouring cells are one step apart on a 2 to 1 diamond`() {
        val frame = 1000f
        val origin = GardenLayout.center(Cell(0, 0), frame)
        val alongA = GardenLayout.center(Cell(1, 0), frame)
        val alongB = GardenLayout.center(Cell(0, 1), frame)

        assertEquals(0f, origin.x, 0f)
        assertEquals(0f, origin.y, 0f)
        assertEquals(271.96f, alongA.x, 0.01f)
        assertEquals(135.98f, alongA.y, 0.01f)
        assertEquals(271.96f, alongB.x, 0.01f)
        assertEquals(-135.98f, alongB.y, 0.01f)
    }

    @Test
    fun `cells are drawn from the back to the front`() {
        val order = GardenLayout.drawOrder(grid3.associateWith { it }).map { it.first }

        assertEquals(Cell(0, 2), order.first())
        assertEquals(Cell(2, 0), order.last())
        // A cell is always drawn after the ones behind it (lower on screen = later).
        val ys = order.map { GardenLayout.center(it, 1f).y }
        assertEquals(ys.sorted(), ys)
    }

    @Test
    fun `bounds grow with the grid and stay centred on its diagonal`() {
        val b = GardenLayout.bounds(grid3, 1f)

        assertEquals(4 * GardenLayout.STEP_X + 0.62f, b.width, 0.001f)
        assertEquals(4 * GardenLayout.STEP_Y + 0.712f, b.height, 0.001f)
        assertTrue(GardenLayout.bounds(listOf(Cell(0, 0)), 1f).width < b.width)
        assertEquals(0f, GardenLayout.bounds(emptyList(), 1f).width, 0f)
    }

    @Test
    fun `garden fits the area on both axes`() {
        val wide = GardenLayout.fitFrame(grid3, 2000f, 600f, 5000f)
        val narrow = GardenLayout.fitFrame(grid3, 600f, 2000f, 5000f)

        assertEquals(600f, GardenLayout.bounds(grid3, wide).height, 0.5f)
        assertTrue(GardenLayout.bounds(grid3, wide).width <= 2000f)
        assertEquals(600f, GardenLayout.bounds(grid3, narrow).width, 0.5f)
        assertTrue(GardenLayout.bounds(grid3, narrow).height <= 2000f)
    }

    @Test
    fun `a small garden is capped and an empty area gives nothing`() {
        assertEquals(400f, GardenLayout.fitFrame(listOf(Cell(0, 0)), 3000f, 3000f, 400f), 0f)
        assertEquals(0f, GardenLayout.fitFrame(grid3, 0f, 500f, 400f), 0f)
        assertEquals(0f, GardenLayout.fitFrame(emptyList(), 500f, 500f, 400f), 0f)
    }

    /** Touch point on the soil of [cell], [dx] and [dy] away from its centre. */
    private fun onSoil(cell: Cell, frame: Float, dx: Float = 0f, dy: Float = 0f): Pair<Float, Float> {
        val c = GardenLayout.center(cell, frame)
        return (c.x + dx) to (c.y + 0.174f * frame + dy)
    }

    @Test
    fun `a touch on the soil finds its cell`() {
        val frame = 500f
        grid3.forEach { cell ->
            val (x, y) = onSoil(cell, frame)
            assertEquals(cell, GardenLayout.cellAt(x, y, frame, 3))
        }
        // Near the right corner of the diamond, still the same cell.
        val (x, y) = onSoil(Cell(1, 1), frame, dx = 120f)
        assertEquals(Cell(1, 1), GardenLayout.cellAt(x, y, frame, 3))
    }

    @Test
    fun `a touch outside the terrain finds nothing`() {
        val frame = 500f
        val (x, y) = onSoil(Cell(1, 1), frame)
        assertEquals(null, GardenLayout.cellAt(x, y, frame, 1))
        assertEquals(null, GardenLayout.cellAt(-400f, 0f, frame, 3))
        assertEquals(null, GardenLayout.cellAt(0f, 0f, 0f, 3))
    }

    @Test
    fun `a tree is touched by its crown, over the ground behind it`() {
        val frame = 500f
        val tree = Cell(1, 0)
        // 150 px above the tree's soil: on the ground this is the cell behind, (0, 1).
        val (x, y) = onSoil(tree, frame, dy = -150f)

        assertEquals(Cell(0, 1), GardenLayout.cellAt(x, y, frame, 3))
        assertEquals(tree, GardenLayout.cellAt(x, y, frame, 3, tallPlants = setOf(tree)))
        // A low plant does not reach that high.
        assertEquals(Cell(0, 1), GardenLayout.cellAt(x, y, frame, 3, lowPlants = setOf(tree)))
    }

    @Test
    fun `a hidden tree lets the touch through to the tile behind`() {
        val frame = 500f
        val tree = Cell(1, 0)
        val soil = GardenLayout.soilCenter(tree, frame)
        val x = soil.x
        val y = soil.y - 150f

        assertEquals(tree, GardenLayout.cellAt(x, y, frame, 3, tallPlants = setOf(tree)))
        // Hidden: the screen no longer lists it among the plants.
        assertEquals(Cell(0, 1), GardenLayout.cellAt(x, y, frame, 3, tallPlants = emptySet()))
        // Its own soil still answers, to bring it back.
        assertEquals(tree, GardenLayout.cellAt(soil.x, soil.y, frame, 3))
    }

    @Test
    fun `the plant in front wins when two overlap`() {
        val frame = 500f
        val back = Cell(0, 1)
        val front = Cell(1, 0)
        val (x, y) = onSoil(front, frame, dy = -20f)

        assertEquals(front, GardenLayout.cellAt(x, y, frame, 3, tallPlants = setOf(back, front)))
    }
}
