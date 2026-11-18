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
}
