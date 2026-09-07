package com.nathanb.lock.schedule

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.nathanb.lock.data.model.EndReason
import com.nathanb.lock.data.model.Profile
import com.nathanb.lock.data.repository.LockRepository
import com.nathanb.lock.fake.FakeNfcTagDao
import com.nathanb.lock.fake.FakeProfileDao
import com.nathanb.lock.fake.FakeScheduleDao
import com.nathanb.lock.fake.FakeScheduleProfileDao
import com.nathanb.lock.fake.FakeSessionDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * End-to-end engine tests with fake platform effects and a controllable clock.
 * The nominal window-end path once deadlocked the engine (it re-entered its own
 * run guard through the session-ended hook) — these tests replay that exact flow.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleManagerTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val paris: ZoneId = ZoneId.of("Europe/Paris")
    private val monday: LocalDate = LocalDate.of(2026, 7, 13)

    private class FakeEffects : ScheduleEffects {
        var armedAtMillis: Long? = null
        var cancelled = false
        var notifierRunning = false

        override fun armWindowBoundary(triggerAtEpochMillis: Long) {
            armedAtMillis = triggerAtEpochMillis
            cancelled = false
        }

        override fun cancelWindowBoundary() {
            armedAtMillis = null
            cancelled = true
        }

        override suspend fun startSessionNotifier() { notifierRunning = true }
        override fun stopSessionNotifier() { notifierRunning = false }
    }

    private lateinit var profileDao: FakeProfileDao
    private lateinit var scheduleDao: FakeScheduleDao
    private lateinit var repository: LockRepository
    private lateinit var effects: FakeEffects
    private lateinit var manager: ScheduleManager
    private var clock: ZonedDateTime = ZonedDateTime.now()

    @Before
    fun setup() {
        profileDao = FakeProfileDao()
        scheduleDao = FakeScheduleDao()
        val scheduleProfileDao = FakeScheduleProfileDao()
        repository = LockRepository(
            profileDao = profileDao,
            sessionDao = FakeSessionDao(),
            nfcTagDao = FakeNfcTagDao(),
            scheduleDao = scheduleDao,
            scheduleProfileDao = scheduleProfileDao,
            dataStore = PreferenceDataStoreFactory.create(
                scope = testScope,
                produceFile = { tmpFolder.newFile("mgr_prefs.preferences_pb") },
            ),
            ioDispatcher = testDispatcher,
            // Same pinned clock as the manager: consumption keys must be computed
            // on the test date, not the wall-clock date the suite happens to run on.
            zonedNow = { clock },
        )
        effects = FakeEffects()
        manager = ScheduleManager(repository, effects, now = { clock })
        // Production wiring: external session ends re-enter the engine through this hook.
        repository.onSessionEnded = { manager.evaluateAndRearm() }
    }

    @After
    fun teardown() = repository.close()

    private fun at(hour: Int, minute: Int = 0): ZonedDateTime =
        ZonedDateTime.of(LocalDateTime.of(monday, LocalTime.of(hour, minute)), paris)

    private fun epoch(hour: Int, minute: Int = 0): Long = at(hour, minute).toInstant().toEpochMilli()

    private suspend fun givenProfileAndSchedule(startMin: Int, endMin: Int): Long {
        val profileId = profileDao.insert(Profile(name = "P", blockedPackages = listOf("com.a")))
        repository.createSchedule(0b1111111, startMin, endMin, listOf(profileId))
        return profileId
    }

    // --- The regression: nominal window end must terminate, stop the service, arm the next boundary ---

    @Test
    fun `window end closes the session and arms the next occurrence without hanging`() = testScope.runTest {
        givenProfileAndSchedule(9 * 60, 17 * 60)

        clock = at(10)
        manager.evaluateAndRearm()
        assertTrue(repository.getLockState().isLocked)
        assertTrue(effects.notifierRunning)
        assertEquals(epoch(17), effects.armedAtMillis)

        clock = at(17)
        manager.evaluateAndRearm() // used to deadlock here (self re-entry through the hook)

        val state = repository.getLockState()
        assertFalse(state.isLocked)
        assertFalse(state.isScheduleOrigin)
        assertFalse(effects.notifierRunning)
        // Next boundary = tomorrow 09:00 (daily schedule).
        assertEquals(at(9).plusDays(1).toInstant().toEpochMilli(), effects.armedAtMillis)
        val endedSession = repository.sessions.first().firstOrNull { it.endTime != null }
        assertEquals("schedule", endedSession?.endReason)
    }

    @Test
    fun `back-to-back schedules chain across the gap`() = testScope.runTest {
        // Nathan's QA scenario: A 13:30-13:35, then B 13:40-13:45.
        val profileId = profileDao.insert(Profile(name = "P", blockedPackages = listOf("com.a")))
        repository.createSchedule(0b1111111, 13 * 60 + 30, 13 * 60 + 35, listOf(profileId))
        repository.createSchedule(0b1111111, 13 * 60 + 40, 13 * 60 + 45, listOf(profileId))

        clock = at(13, 30)
        manager.evaluateAndRearm()
        assertTrue(repository.getLockState().isLocked)
        assertEquals(epoch(13, 35), effects.armedAtMillis)

        clock = at(13, 35)
        manager.evaluateAndRearm()
        assertFalse(repository.getLockState().isLocked)
        assertFalse(effects.notifierRunning)
        assertEquals(epoch(13, 40), effects.armedAtMillis) // B's start IS armed

        clock = at(13, 40)
        manager.evaluateAndRearm()
        assertTrue(repository.getLockState().isLocked) // B started
        assertTrue(effects.notifierRunning)
        assertEquals(epoch(13, 45), effects.armedAtMillis)
    }

    @Test
    fun `removing all profiles mid-window ends the session without hanging`() = testScope.runTest {
        givenProfileAndSchedule(9 * 60, 17 * 60)
        clock = at(10)
        manager.evaluateAndRearm()
        assertTrue(repository.getLockState().isLocked)

        val schedule = scheduleDao.getAllOnce().single()
        repository.updateSchedule(schedule, emptyList()) // Nathan's 11:00 move
        manager.evaluateAndRearm()

        assertFalse(repository.getLockState().isLocked)
        assertFalse(effects.notifierRunning)
    }

    // --- Existing behaviors stay intact ---

    @Test
    fun `external end through the hook re-evaluates and does not relock a consumed window`() = testScope.runTest {
        givenProfileAndSchedule(9 * 60, 17 * 60)
        clock = at(10)
        manager.evaluateAndRearm()
        assertTrue(repository.getLockState().isLocked)

        // NFC-style end: external caller, hook fires, engine re-evaluates.
        repository.endLockSession(EndReason.NFC.value)

        assertFalse(repository.getLockState().isLocked)
        // Consumed for the day: the re-evaluation must NOT restart the window.
        assertEquals(1, repository.getConsumedWindowKeys().size)
        assertNotNull(effects.armedAtMillis) // still watching boundaries
    }

    @Test
    fun `window is ignored while a manual session runs`() = testScope.runTest {
        val profileId = givenProfileAndSchedule(9 * 60, 17 * 60)
        repository.startLockSession(profileId) // manual/NFC origin

        clock = at(10)
        manager.evaluateAndRearm()

        val state = repository.getLockState()
        assertTrue(state.isLocked)
        assertFalse(state.isScheduleOrigin) // untouched, no merge (spec)
    }

    @Test
    fun `no enabled schedule cancels the boundary alarm`() = testScope.runTest {
        givenProfileAndSchedule(9 * 60, 17 * 60)
        val schedule = scheduleDao.getAllOnce().single()
        repository.setScheduleEnabled(schedule.id, false)

        clock = at(10)
        manager.evaluateAndRearm()

        assertNull(effects.armedAtMillis)
        assertTrue(effects.cancelled)
        assertFalse(repository.getLockState().isLocked)
    }
}
