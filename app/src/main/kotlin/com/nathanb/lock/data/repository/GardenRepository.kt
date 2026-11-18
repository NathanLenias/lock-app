package com.nathanb.lock.data.repository

import com.nathanb.lock.data.backup.GardenBackup
import com.nathanb.lock.data.database.GardenDao
import com.nathanb.lock.data.database.SessionDao
import com.nathanb.lock.data.garden.GardenRules
import com.nathanb.lock.data.garden.PlantKind
import com.nathanb.lock.data.model.GardenPlot
import com.nathanb.lock.data.model.GardenState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.ZoneId

/** A plant standing on a cell: [stage] goes from 1 (sprout) to [PlantKind.stages]. */
data class Planted(val kind: PlantKind, val stage: Int) {
    val isFullyGrown: Boolean get() = stage >= kind.stages
}

/** The garden as the screen needs it. Cells are (a, b) with both in 0 until [size]. */
data class Garden(
    val size: Int,
    val plants: Map<Pair<Int, Int>, Planted>,
    val drops: Int,
    /** Null once the terrain is at its largest. */
    val daysUntilNextSize: Int?,
    /** 1 on the day the garden is born. */
    val day: Int = 1,
    /** True when the 2x2 terrain was given at birth (Lock already used for a month). */
    val startedLarger: Boolean = false,
    /** When each plant was put in the ground, for the undo countdown. */
    val plantedAt: Map<Pair<Int, Int>, Long> = emptyMap(),
)

enum class GardenActionResult { DONE, NOT_ENOUGH_DROPS, NOT_ALLOWED }

/**
 * Garden state and actions. Kept apart from [LockRepository]: nothing here takes part in
 * blocking. Spending drops and changing a plant always happen in one transaction.
 */
class GardenRepository(
    private val gardenDao: GardenDao,
    private val sessionDao: SessionDao,
    /** Runs the block in a database transaction (plain call in unit tests). */
    private val transact: suspend (suspend () -> GardenActionResult) -> GardenActionResult = { it() },
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    /** Fingerprint of this phone (see DeviceIdentity), null when unknown. */
    private val deviceId: () -> String? = { null },
) {

    /** Null until [ensureCreated] has run. */
    val garden: Flow<Garden?> = combine(
        gardenDao.observeState(),
        gardenDao.observePlots(),
        sessionDao.getAll(),
    ) { state, plots, sessions ->
        if (state == null) return@combine null
        val returning = GardenRules.isReturningUser(sessions, state.createdAt)
        val size = GardenRules.terrainSize(state.createdAt, now(), returning)
        Garden(
            size = size,
            plants = plots
                .filter { it.cellA in 0 until size && it.cellB in 0 until size }
                // A plant this version does not know (downgrade) shows as an empty cell.
                .mapNotNull { plot ->
                    PlantKind.fromKey(plot.plant)?.let { kind ->
                        (plot.cellA to plot.cellB) to Planted(kind, plot.stage.coerceIn(1, kind.stages))
                    }
                }
                .toMap(),
            drops = GardenRules.balance(sessions, state.createdAt, state.dropsSpent, zone()),
            daysUntilNextSize = GardenRules.daysUntilNextSize(state.createdAt, now(), returning),
            day = GardenRules.dayNumber(state.createdAt, now()),
            startedLarger = returning,
            plantedAt = plots.associate { (it.cellA to it.cellB) to it.plantedAt },
        )
    }

    /** The garden is born the first time this runs; later calls change nothing. */
    suspend fun ensureCreated() {
        gardenDao.insertStateIfAbsent(GardenState(createdAt = now(), dropsSpent = 0))
    }

    suspend fun plant(a: Int, b: Int, kind: PlantKind): GardenActionResult = transact {
        val state = gardenDao.getState() ?: return@transact GardenActionResult.NOT_ALLOWED
        if (!isOnTerrain(a, b, state) || gardenDao.getPlot(a, b) != null) {
            return@transact GardenActionResult.NOT_ALLOWED
        }
        spend(state, kind.stepCost) {
            gardenDao.upsertPlot(GardenPlot(cellA = a, cellB = b, plant = kind.key, stage = 1, plantedAt = now()))
        }
    }

    suspend fun grow(a: Int, b: Int): GardenActionResult = transact {
        val state = gardenDao.getState() ?: return@transact GardenActionResult.NOT_ALLOWED
        val plot = gardenDao.getPlot(a, b) ?: return@transact GardenActionResult.NOT_ALLOWED
        val kind = PlantKind.fromKey(plot.plant) ?: return@transact GardenActionResult.NOT_ALLOWED
        if (plot.stage >= kind.stages) return@transact GardenActionResult.NOT_ALLOWED
        spend(state, kind.stepCost) { gardenDao.upsertPlot(plot.copy(stage = plot.stage + 1)) }
    }

    /**
     * Takes back a planting made by mistake: the sprout goes and its price is refunded.
     * Only within [GardenRules.UNDO_PLANT_WINDOW_MS] and before the plant has grown.
     */
    suspend fun undoPlant(a: Int, b: Int): GardenActionResult = transact {
        val plot = gardenDao.getPlot(a, b) ?: return@transact GardenActionResult.NOT_ALLOWED
        val kind = PlantKind.fromKey(plot.plant) ?: return@transact GardenActionResult.NOT_ALLOWED
        if (plot.stage != 1 || now() - plot.plantedAt > GardenRules.UNDO_PLANT_WINDOW_MS) {
            return@transact GardenActionResult.NOT_ALLOWED
        }
        gardenDao.deletePlot(a, b)
        gardenDao.addDropsSpent(-kind.stepCost)
        GardenActionResult.DONE
    }

    /** Frees the cell. The drops put into the plant are not given back. */
    suspend fun remove(a: Int, b: Int): GardenActionResult = transact {
        if (gardenDao.getPlot(a, b) == null) return@transact GardenActionResult.NOT_ALLOWED
        gardenDao.deletePlot(a, b)
        GardenActionResult.DONE
    }

    /**
     * Moves the plant of (fromA, fromB) to (toA, toB). A plant already standing there takes
     * its place: both keep their stage. Paid once, whatever the destination holds.
     */
    suspend fun move(fromA: Int, fromB: Int, toA: Int, toB: Int): GardenActionResult = transact {
        val state = gardenDao.getState() ?: return@transact GardenActionResult.NOT_ALLOWED
        if (fromA == toA && fromB == toB) return@transact GardenActionResult.NOT_ALLOWED
        val moved = gardenDao.getPlot(fromA, fromB) ?: return@transact GardenActionResult.NOT_ALLOWED
        if (!isOnTerrain(toA, toB, state)) return@transact GardenActionResult.NOT_ALLOWED
        val displaced = gardenDao.getPlot(toA, toB)
        spend(state, GardenRules.MOVE_COST) {
            gardenDao.deletePlot(fromA, fromB)
            gardenDao.upsertPlot(moved.copy(cellA = toA, cellB = toB))
            if (displaced != null) gardenDao.upsertPlot(displaced.copy(cellA = fromA, cellB = fromB))
        }
    }

    /** The garden as written into a backup, stamped with this phone's fingerprint. */
    suspend fun exportForBackup(): GardenBackup? {
        val state = gardenDao.getState() ?: return null
        return GardenBackup(
            device = deviceId(),
            createdAt = state.createdAt,
            dropsSpent = state.dropsSpent,
            plots = gardenDao.getAllPlots(),
        )
    }

    /**
     * Brings back the garden of a backup, but only one written by another phone: after a
     * reinstall on the same phone the garden starts over, which is the point of it.
     * A backup without a garden or without a fingerprint changes nothing either.
     * Runs inside the import transaction. Returns true when the garden was replaced.
     */
    suspend fun restoreFromBackup(backup: GardenBackup?): Boolean {
        val from = backup?.device
        val here = deviceId()
        if (backup == null || from.isNullOrEmpty() || here.isNullOrEmpty() || from == here) return false
        gardenDao.deleteAllPlots()
        gardenDao.replaceState(
            GardenState(
                // A file is not trusted blindly: no birth in the future, no negative spending.
                createdAt = backup.createdAt.coerceAtMost(now()),
                dropsSpent = backup.dropsSpent.coerceAtLeast(0),
            ),
        )
        backup.plots
            .filter { it.cellA in 0 until GardenRules.MAX_TERRAIN_SIZE && it.cellB in 0 until GardenRules.MAX_TERRAIN_SIZE }
            .forEach { gardenDao.upsertPlot(it.copy(stage = it.stage.coerceAtLeast(1))) }
        return true
    }

    /** Test aid, only reachable from debug builds: credits [drops] without a session. */
    suspend fun debugGrantDrops(drops: Int) {
        gardenDao.addDropsSpent(-drops)
    }

    private suspend fun isOnTerrain(a: Int, b: Int, state: GardenState): Boolean {
        val returning = GardenRules.isReturningUser(sessionDao.getAllOnce(), state.createdAt)
        val size = GardenRules.terrainSize(state.createdAt, now(), returning)
        return a in 0 until size && b in 0 until size
    }

    private suspend fun spend(state: GardenState, cost: Int, change: suspend () -> Unit): GardenActionResult {
        val balance = GardenRules.balance(sessionDao.getAllOnce(), state.createdAt, state.dropsSpent, zone())
        if (balance < cost) return GardenActionResult.NOT_ENOUGH_DROPS
        change()
        gardenDao.addDropsSpent(cost)
        return GardenActionResult.DONE
    }
}
