package com.nathanb.lock.schedule

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.nathanb.lock.data.model.EndReason
import com.nathanb.lock.data.model.Profile
import com.nathanb.lock.data.model.ScanBehavior
import com.nathanb.lock.data.model.Schedule
import com.nathanb.lock.data.model.ScheduleProfileLink
import com.nathanb.lock.data.repository.LockRepository
import com.nathanb.lock.fake.FakeNfcTagDao
import com.nathanb.lock.fake.FakeProfileDao
import com.nathanb.lock.fake.FakeScheduleDao
import com.nathanb.lock.fake.FakeScheduleProfileDao
import com.nathanb.lock.fake.FakeSessionDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * End-to-end pause flow through the engine: scan pauses, the resume alarm is armed on
 * pausedUntil, and the expiry re-locks without the window ever being consumed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SchedulePauseFlowTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val paris: ZoneId = ZoneId.of("Europe/Paris")
    private var now: ZonedDateTime =
        ZonedDateTime.of(LocalDate.of(2026, 9, 2), LocalTime.of(10, 0), paris)

    private class FakeEffects : ScheduleEffects {
        var armedAtMillis: Long? = null
        var notifierRunning = false
        override fun armWindowBoundary(triggerAtEpochMillis: Long) { armedAtMillis = triggerAtEpochMillis }
        override fun cancelWindowBoundary() { armedAtMillis = null }
        override suspend fun startSessionNotifier() { notifierRunning = true }
        override fun stopSessionNotifier() { notifierRunning = false }
    }

    private lateinit var repository: LockRepository
    private lateinit var effects: FakeEffects
    private lateinit var manager: ScheduleManager
    private lateinit var profileDao: FakeProfileDao
    private lateinit var scheduleDao: FakeScheduleDao
    private lateinit var linkDao: FakeScheduleProfileDao

    @Before
    fun setup() {
        profileDao = FakeProfileDao()
        scheduleDao = FakeScheduleDao()
        linkDao = FakeScheduleProfileDao()
        repository = LockRepository(
            profileDao = profileDao,
            sessionDao = FakeSessionDao(),
            nfcTagDao = FakeNfcTagDao(),
            scheduleDao = scheduleDao,
            scheduleProfileDao = linkDao,
            dataStore = PreferenceDataStoreFactory.create(
                scope = testScope,
                produceFile = { tmpFolder.newFile("test_prefs.preferences_pb") },
            ),
            ioDispatcher = testDispatcher,
            zonedNow = { now },
        )
        effects = FakeEffects()
        manager = ScheduleManager(repository, effects, now = { now })
        repository.onSessionEnded = { manager.evaluateAndRearm() }
    }

    @After
    fun teardown() {
        repository.close()
    }

    private suspend fun setupAllDayPauseSchedule(pauseMinutes: Long) {
        val profileId = profileDao.insert(Profile(name = "P", blockedPackages = listOf("app.a")))
        val scheduleId = scheduleDao.insert(
            Schedule(
                daysOfWeek = 0b1111111, startMinuteOfDay = 0, endMinuteOfDay = 1439,
                allDay = true, scanBehavior = ScanBehavior.PAUSE.value,
                pauseDurationMs = pauseMinutes * 60_000L, createdAt = 0L,
            ),
        )
        linkDao.insertAll(listOf(ScheduleProfileLink(scheduleId, profileId)))
    }

    @Test
    fun `scan pauses, alarm resumes blocking, window never consumed`() = testScope.runTest {
        setupAllDayPauseSchedule(pauseMinutes = 15)

        manager.evaluateAndRearm()
        assertTrue("all-day window must lock", repository.getLockState().isLocked)
        assertTrue(effects.notifierRunning)

        // NFC scan: the repository pauses, then the hook re-evaluates.
        val pausedUntil = repository.endLockSession(EndReason.NFC.value)
        assertFalse(repository.getLockState().isLocked)
        assertEquals("resume alarm must be on pausedUntil", pausedUntil, effects.armedAtMillis)
        assertTrue(repository.getConsumedWindowKeys().isEmpty())

        // Pause expires (deep-sleep safe: this is the AlarmManager path in production).
        now = now.plusMinutes(16)
        manager.evaluateAndRearm()
        assertTrue("blocking must resume", repository.getLockState().isLocked)
        assertTrue(effects.notifierRunning)
        assertTrue(repository.getConsumedWindowKeys().isEmpty())
    }

    @Test
    fun `evaluation during the pause does not relock and keeps the resume alarm`() = testScope.runTest {
        setupAllDayPauseSchedule(pauseMinutes = 30)
        manager.evaluateAndRearm()
        val pausedUntil = repository.endLockSession(EndReason.NFC.value)

        now = now.plusMinutes(5)
        manager.evaluateAndRearm() // spurious trigger mid-pause
        assertFalse(repository.getLockState().isLocked)
        assertEquals(pausedUntil, effects.armedAtMillis)
    }
}
