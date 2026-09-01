package com.nathanb.lock.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.nathanb.lock.data.model.EndReason
import com.nathanb.lock.data.model.Profile
import com.nathanb.lock.data.model.ScanBehavior
import com.nathanb.lock.data.model.Schedule
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
import org.junit.Assert.assertNull
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
 * Pause-behavior windows: an NFC end pauses them (never consumes), unlock-behavior
 * windows keep the consume-for-the-day semantics, and re-enabling a schedule lifts
 * its current consumption so blocking restarts immediately.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SchedulePauseTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val paris: ZoneId = ZoneId.of("Europe/Paris")
    private var now: ZonedDateTime =
        ZonedDateTime.of(LocalDate.of(2026, 9, 2), LocalTime.of(10, 0), paris)

    private lateinit var profileDao: FakeProfileDao
    private lateinit var sessionDao: FakeSessionDao
    private lateinit var scheduleDao: FakeScheduleDao
    private lateinit var repository: LockRepository

    @Before
    fun setup() {
        profileDao = FakeProfileDao()
        sessionDao = FakeSessionDao()
        scheduleDao = FakeScheduleDao()
        repository = LockRepository(
            profileDao = profileDao,
            sessionDao = sessionDao,
            nfcTagDao = FakeNfcTagDao(),
            scheduleDao = scheduleDao,
            scheduleProfileDao = FakeScheduleProfileDao(),
            dataStore = PreferenceDataStoreFactory.create(
                scope = testScope,
                produceFile = { tmpFolder.newFile("test_prefs.preferences_pb") },
            ),
            ioDispatcher = testDispatcher,
            zonedNow = { now },
        )
    }

    @After
    fun teardown() {
        repository.close()
    }

    private suspend fun schedule(behavior: ScanBehavior, pauseMs: Long? = null): Long =
        scheduleDao.insert(
            Schedule(
                daysOfWeek = 0b1111111, startMinuteOfDay = 0, endMinuteOfDay = 1439,
                allDay = true, scanBehavior = behavior.value, pauseDurationMs = pauseMs,
                createdAt = 0L,
            ),
        )

    private suspend fun startScheduled() {
        val profileId = profileDao.insert(Profile(name = "P", blockedPackages = listOf("a")))
        repository.startScheduledSession(profileId, setOf("a"))
    }

    @Test
    fun `nfc end over a pause window pauses instead of consuming`() = testScope.runTest {
        schedule(ScanBehavior.PAUSE, 15 * 60_000L)
        startScheduled()

        val pausedUntil = repository.endLockSession(EndReason.NFC.value)

        assertEquals(now.toInstant().toEpochMilli() + 15 * 60_000L, pausedUntil)
        assertEquals(pausedUntil, repository.getSchedulePausedUntil())
        assertTrue(repository.getConsumedWindowKeys().isEmpty())
    }

    @Test
    fun `nfc end over an unlock window consumes it and sets no pause`() = testScope.runTest {
        schedule(ScanBehavior.UNLOCK)
        startScheduled()

        val pausedUntil = repository.endLockSession(EndReason.NFC.value)

        assertNull(pausedUntil)
        assertEquals(1, repository.getConsumedWindowKeys().size)
    }

    @Test
    fun `mixed windows - unlock consumed, pause paused with the shortest duration`() = testScope.runTest {
        schedule(ScanBehavior.UNLOCK)
        schedule(ScanBehavior.PAUSE, 30 * 60_000L)
        schedule(ScanBehavior.PAUSE, 15 * 60_000L)
        startScheduled()

        val pausedUntil = repository.endLockSession(EndReason.NFC.value)

        assertEquals(now.toInstant().toEpochMilli() + 15 * 60_000L, pausedUntil)
        assertEquals(1, repository.getConsumedWindowKeys().size)
    }

    @Test
    fun `timeout end never consumes nor pauses`() = testScope.runTest {
        schedule(ScanBehavior.PAUSE, 15 * 60_000L)
        startScheduled()

        val pausedUntil = repository.endLockSession(EndReason.TIMEOUT.value)

        assertNull(pausedUntil)
        assertTrue(repository.getConsumedWindowKeys().isEmpty())
        assertEquals(0L, repository.getSchedulePausedUntil())
    }

    @Test
    fun `scan during an active pause restarts it, expired pause does not`() = testScope.runTest {
        schedule(ScanBehavior.PAUSE, 15 * 60_000L)
        startScheduled()
        repository.endLockSession(EndReason.NFC.value)

        now = now.plusMinutes(10)
        val restarted = repository.restartActivePause()
        assertEquals(now.toInstant().toEpochMilli() + 15 * 60_000L, restarted)

        now = now.plusMinutes(20) // past the restarted pause
        assertNull(repository.restartActivePause())
    }

    @Test
    fun `disabling the last covering pause window drops the orphaned pause`() = testScope.runTest {
        val id = schedule(ScanBehavior.PAUSE, 15 * 60_000L)
        startScheduled()
        repository.endLockSession(EndReason.NFC.value)
        assertTrue(repository.getSchedulePausedUntil() > 0L)

        repository.setScheduleEnabled(id, false)

        assertEquals(0L, repository.getSchedulePausedUntil())
    }

    @Test
    fun `disabling one pause window keeps the pause while another still covers`() = testScope.runTest {
        val id = schedule(ScanBehavior.PAUSE, 15 * 60_000L)
        schedule(ScanBehavior.PAUSE, 30 * 60_000L)
        startScheduled()
        val pausedUntil = repository.endLockSession(EndReason.NFC.value)

        repository.setScheduleEnabled(id, false)

        assertEquals(pausedUntil, repository.getSchedulePausedUntil())
    }

    @Test
    fun `creating or re-enabling a schedule clears a running pause`() = testScope.runTest {
        val id = schedule(ScanBehavior.PAUSE, 15 * 60_000L)
        startScheduled()
        repository.endLockSession(EndReason.NFC.value)
        assertTrue(repository.getSchedulePausedUntil() > 0L)

        repository.createSchedule(0b1111111, 0, 1439, emptyList(), allDay = true)
        assertEquals(0L, repository.getSchedulePausedUntil())

        repository.endLockSession(EndReason.NFC.value) // no session, but re-pauses via covering window? no: not locked -> no consuming path
        repository.setScheduleEnabled(id, false)
        repository.setScheduleEnabled(id, true)
        assertEquals(0L, repository.getSchedulePausedUntil())
    }

    @Test
    fun `re-enabling a schedule lifts its current consumption`() = testScope.runTest {
        val id = schedule(ScanBehavior.UNLOCK)
        startScheduled()
        repository.endLockSession(EndReason.NFC.value) // consumes today's occurrence
        assertEquals(1, repository.getConsumedWindowKeys().size)

        repository.setScheduleEnabled(id, false)
        repository.setScheduleEnabled(id, true)

        assertTrue(repository.getConsumedWindowKeys().isEmpty())
    }
}
