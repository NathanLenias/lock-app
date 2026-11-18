package com.nathanb.lock.data.repository

import com.nathanb.lock.data.backup.GardenBackup
import com.nathanb.lock.data.garden.PlantKind
import com.nathanb.lock.data.model.GardenPlot
import com.nathanb.lock.data.model.Session
import com.nathanb.lock.fake.FakeGardenDao
import com.nathanb.lock.fake.FakeSessionDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneId

class GardenRepositoryTest {

    private val hour = 60 * 60 * 1000L
    private val day = 24 * hour
    private val born = 20_000 * day

    private var now = born
    private var device: String? = "this-phone"
    private lateinit var gardenDao: FakeGardenDao
    private lateinit var sessionDao: FakeSessionDao
    private lateinit var repository: GardenRepository

    @Before
    fun setUp() {
        now = born
        device = "this-phone"
        gardenDao = FakeGardenDao()
        sessionDao = FakeSessionDao()
        repository = GardenRepository(
            gardenDao, sessionDao, now = { now }, zone = { ZoneId.of("UTC") }, deviceId = { device },
        )
    }

    /** Adds finished sessions worth [drops] drops (3 a day) after the birth of the garden. */
    private suspend fun earn(drops: Int) {
        var left = drops
        var dayIndex = 0
        while (left > 0) {
            val today = minOf(3, left)
            val start = born + dayIndex * day + hour
            sessionDao.insert(Session(profileId = 1, startTime = start, endTime = start + today * hour))
            left -= today
            dayIndex++
        }
    }

    private suspend fun garden() = repository.garden.first()!!

    @Test
    fun `no garden before it is created, then one cell and the welcome drop`() = runTest {
        assertNull(repository.garden.first())

        repository.ensureCreated()

        val garden = garden()
        assertEquals(1, garden.size)
        assertEquals(1, garden.drops)
        assertTrue(garden.plants.isEmpty())
        assertEquals(14, garden.daysUntilNextSize)
        assertEquals(1, garden.day)
        assertEquals(false, garden.startedLarger)
    }

    @Test
    fun `creating twice keeps the first birth date`() = runTest {
        repository.ensureCreated()
        now = born + 40 * day
        repository.ensureCreated()

        assertEquals(born, gardenDao.getState()!!.createdAt)
        assertEquals(2, garden().size)
    }

    @Test
    fun `returning user gets three drops and the 2x2 terrain`() = runTest {
        val old = born - 45 * day
        sessionDao.insert(Session(profileId = 1, startTime = old, endTime = old + 4 * hour))
        repository.ensureCreated()

        assertEquals(3, garden().drops)
        assertEquals(2, garden().size)
        assertEquals(true, garden().startedLarger)
        assertEquals(89, garden().daysUntilNextSize)
    }

    @Test
    fun `planting costs one step and puts a sprout`() = runTest {
        repository.ensureCreated()

        assertEquals(GardenActionResult.DONE, repository.plant(0, 0, PlantKind.FLOWERS))

        val garden = garden()
        assertEquals(Planted(PlantKind.FLOWERS, 1), garden.plants[0 to 0])
        assertEquals(0, garden.drops)
    }

    @Test
    fun `planting is refused without enough drops, and nothing changes`() = runTest {
        repository.ensureCreated()

        assertEquals(GardenActionResult.NOT_ENOUGH_DROPS, repository.plant(0, 0, PlantKind.CHERRY))

        assertTrue(garden().plants.isEmpty())
        assertEquals(1, garden().drops)
        assertEquals(0, gardenDao.getState()!!.dropsSpent)
    }

    @Test
    fun `planting is refused outside the terrain and on a taken cell`() = runTest {
        repository.ensureCreated()
        earn(6)

        assertEquals(GardenActionResult.NOT_ALLOWED, repository.plant(1, 0, PlantKind.FLOWERS))
        assertEquals(GardenActionResult.NOT_ALLOWED, repository.plant(-1, 0, PlantKind.FLOWERS))
        assertEquals(GardenActionResult.DONE, repository.plant(0, 0, PlantKind.FLOWERS))
        assertEquals(GardenActionResult.NOT_ALLOWED, repository.plant(0, 0, PlantKind.TULIPS_MIX))
        assertEquals(6, garden().drops)
    }

    @Test
    fun `growing goes one stage at a time up to the last one`() = runTest {
        repository.ensureCreated()
        earn(44)

        assertEquals(GardenActionResult.DONE, repository.plant(0, 0, PlantKind.APPLE))
        assertEquals(GardenActionResult.DONE, repository.grow(0, 0))
        assertEquals(GardenActionResult.DONE, repository.grow(0, 0))
        assertEquals(GardenActionResult.NOT_ALLOWED, repository.grow(0, 0))

        val garden = garden()
        assertEquals(Planted(PlantKind.APPLE, 3), garden.plants[0 to 0])
        assertTrue(garden.plants.getValue(0 to 0).isFullyGrown)
        assertEquals(0, garden.drops)
    }

    @Test
    fun `growing is refused without enough drops or without a plant`() = runTest {
        repository.ensureCreated()
        earn(4)

        assertEquals(GardenActionResult.NOT_ALLOWED, repository.grow(0, 0))
        assertEquals(GardenActionResult.DONE, repository.plant(0, 0, PlantKind.BUSH))
        assertEquals(GardenActionResult.NOT_ENOUGH_DROPS, repository.grow(0, 0))
        assertEquals(Planted(PlantKind.BUSH, 1), garden().plants[0 to 0])
    }

    @Test
    fun `removing frees the cell and does not refund`() = runTest {
        repository.ensureCreated()
        earn(2)
        repository.plant(0, 0, PlantKind.FLOWERS)
        repository.grow(0, 0)

        assertEquals(GardenActionResult.DONE, repository.remove(0, 0))
        assertEquals(GardenActionResult.NOT_ALLOWED, repository.remove(0, 0))

        assertTrue(garden().plants.isEmpty())
        assertEquals(1, garden().drops)
        assertEquals(GardenActionResult.DONE, repository.plant(0, 0, PlantKind.TULIPS_RED_YELLOW))
    }

    @Test
    fun `a planting undone in time is refunded`() = runTest {
        repository.ensureCreated()
        earn(14)
        repository.plant(0, 0, PlantKind.CHERRY)
        assertEquals(0, garden().drops)
        now += 119_000

        assertEquals(GardenActionResult.DONE, repository.undoPlant(0, 0))

        assertTrue(garden().plants.isEmpty())
        assertEquals(15, garden().drops)
        assertEquals(0, gardenDao.getState()!!.dropsSpent)
    }

    @Test
    fun `a planting cannot be undone too late, once grown, or on an empty tile`() = runTest {
        repository.ensureCreated()
        earn(4)
        assertEquals(GardenActionResult.NOT_ALLOWED, repository.undoPlant(0, 0))

        repository.plant(0, 0, PlantKind.FLOWERS)
        repository.grow(0, 0)
        assertEquals(GardenActionResult.NOT_ALLOWED, repository.undoPlant(0, 0))
        repository.remove(0, 0)

        repository.plant(0, 0, PlantKind.FLOWERS)
        now += 120_001
        assertEquals(GardenActionResult.NOT_ALLOWED, repository.undoPlant(0, 0))

        assertEquals(Planted(PlantKind.FLOWERS, 1), garden().plants[0 to 0])
        assertEquals(2, garden().drops)
    }

    /** Garden old enough for the 2x2 terrain, with [drops] drops to spend. */
    private suspend fun bigGarden(drops: Int) {
        repository.ensureCreated()
        earn(drops - 1) // the welcome drop makes the count
        now = born + 20 * day
    }

    @Test
    fun `moving to an empty tile costs three drops and keeps the stage`() = runTest {
        bigGarden(drops = 5)
        repository.plant(0, 0, PlantKind.FLOWERS)
        repository.grow(0, 0)

        assertEquals(GardenActionResult.DONE, repository.move(0, 0, 1, 1))

        val garden = garden()
        assertEquals(mapOf((1 to 1) to Planted(PlantKind.FLOWERS, 2)), garden.plants)
        assertEquals(0, garden.drops)
    }

    @Test
    fun `moving onto a plant swaps the two, paid once`() = runTest {
        bigGarden(drops = 6)
        repository.plant(0, 0, PlantKind.FLOWERS)
        repository.grow(0, 0)
        repository.plant(1, 0, PlantKind.TULIPS_MIX)

        assertEquals(GardenActionResult.DONE, repository.move(0, 0, 1, 0))

        val garden = garden()
        assertEquals(Planted(PlantKind.TULIPS_MIX, 1), garden.plants[0 to 0])
        assertEquals(Planted(PlantKind.FLOWERS, 2), garden.plants[1 to 0])
        assertEquals(2, garden.plants.size)
        assertEquals(0, garden.drops)
    }

    @Test
    fun `a refused move changes nothing`() = runTest {
        bigGarden(drops = 3)
        repository.plant(0, 0, PlantKind.FLOWERS)
        val before = garden()

        // Two drops left: not enough.
        assertEquals(GardenActionResult.NOT_ENOUGH_DROPS, repository.move(0, 0, 1, 1))
        assertEquals(before, garden())

        earn(9)
        val rich = garden()
        assertEquals(GardenActionResult.NOT_ALLOWED, repository.move(0, 0, 0, 0)) // same tile
        assertEquals(GardenActionResult.NOT_ALLOWED, repository.move(0, 0, 2, 0)) // off the terrain
        assertEquals(GardenActionResult.NOT_ALLOWED, repository.move(1, 1, 0, 1)) // nothing to move
        assertEquals(rich, garden())
    }

    // --- Backup ---

    /** A garden saved on [from]: born 100 days before these tests, one grown cherry tree. */
    private fun savedGarden(from: String?) = GardenBackup(
        device = from,
        createdAt = born - 100 * day,
        dropsSpent = 45,
        plots = listOf(GardenPlot(cellA = 1, cellB = 2, plant = "cherry", stage = 3, plantedAt = born - 50 * day)),
    )

    @Test
    fun `the export carries the garden and this phone's fingerprint`() = runTest {
        assertNull(repository.exportForBackup())

        repository.ensureCreated()
        repository.plant(0, 0, PlantKind.FLOWERS)

        val backup = repository.exportForBackup()!!
        assertEquals("this-phone", backup.device)
        assertEquals(born, backup.createdAt)
        assertEquals(1, backup.dropsSpent)
        assertEquals(listOf("flowers"), backup.plots.map { it.plant })
    }

    @Test
    fun `a backup from another phone replaces the garden`() = runTest {
        repository.ensureCreated()
        repository.plant(0, 0, PlantKind.FLOWERS)
        // 60 drops earned on the old phone, since its garden was born.
        (1..20).forEach { d ->
            val start = born - d * day
            sessionDao.insert(Session(profileId = 1, startTime = start, endTime = start + 3 * hour))
        }

        assertTrue(repository.restoreFromBackup(savedGarden(from = "old-phone")))

        val garden = garden()
        assertEquals(mapOf((1 to 2) to Planted(PlantKind.CHERRY, 3)), garden.plants)
        assertEquals(101, garden.day)
        assertEquals(3, garden.size)
        // Welcome drop + 60 earned - 45 spent.
        assertEquals(16, garden.drops)
    }

    @Test
    fun `a backup from this phone leaves the garden as it is`() = runTest {
        repository.ensureCreated()
        repository.plant(0, 0, PlantKind.FLOWERS)
        val before = garden()

        assertEquals(false, repository.restoreFromBackup(savedGarden(from = "this-phone")))

        assertEquals(before, garden())
        assertEquals(born, gardenDao.getState()!!.createdAt)
    }

    @Test
    fun `a backup without a garden or a fingerprint changes nothing`() = runTest {
        repository.ensureCreated()
        val before = garden()

        assertEquals(false, repository.restoreFromBackup(null))
        assertEquals(false, repository.restoreFromBackup(savedGarden(from = null)))
        assertEquals(false, repository.restoreFromBackup(savedGarden(from = "")))
        // This phone gives no identifier: nothing can be told apart, nothing is restored.
        device = null
        assertEquals(false, repository.restoreFromBackup(savedGarden(from = "old-phone")))

        assertEquals(before, garden())
    }

    @Test
    fun `a restored file is not trusted blindly`() = runTest {
        repository.ensureCreated()
        val tampered = GardenBackup(
            device = "old-phone",
            createdAt = born + 30 * day, // born in the future
            dropsSpent = -500,
            plots = listOf(
                GardenPlot(cellA = 0, cellB = 0, plant = "flowers", stage = 0, plantedAt = born),
                GardenPlot(cellA = 9, cellB = 0, plant = "cherry", stage = 3, plantedAt = born),
            ),
        )

        assertTrue(repository.restoreFromBackup(tampered))

        assertEquals(born, gardenDao.getState()!!.createdAt)
        assertEquals(0, gardenDao.getState()!!.dropsSpent)
        assertEquals(1, garden().drops)
        assertEquals(listOf(GardenPlot(0, 0, "flowers", 1, born)), gardenDao.getAllPlots())
    }

    @Test
    fun `an unknown plant shows as an empty cell`() = runTest {
        repository.ensureCreated()
        gardenDao.upsertPlot(GardenPlot(cellA = 0, cellB = 0, plant = "baobab", stage = 2, plantedAt = born))

        assertTrue(garden().plants.isEmpty())
        assertEquals(GardenActionResult.NOT_ALLOWED, repository.grow(0, 0))
    }

    @Test
    fun `losing sessions lowers the balance without touching the plants`() = runTest {
        repository.ensureCreated()
        earn(3)
        repository.plant(0, 0, PlantKind.FLOWERS)
        repository.grow(0, 0)

        sessionDao.deleteAll() // what a backup import with fewer sessions does

        assertEquals(0, garden().drops)
        assertEquals(Planted(PlantKind.FLOWERS, 2), garden().plants[0 to 0])
    }
}
