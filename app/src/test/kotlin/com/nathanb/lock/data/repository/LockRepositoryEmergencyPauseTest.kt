package com.nathanb.lock.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.nathanb.lock.data.model.EndReason
import com.nathanb.lock.data.model.Profile
import com.nathanb.lock.fake.FakeNfcTagDao
import com.nathanb.lock.fake.FakeProfileDao
import com.nathanb.lock.fake.FakeScheduleDao
import com.nathanb.lock.fake.FakeScheduleProfileDao
import com.nathanb.lock.fake.FakeSessionDao
import com.nathanb.lock.util.Constants
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The emergency pause is owned by the repository, not by a screen: it must end on its
 * own at the deadline (Lock swiped from recents mid-pause used to leave blocking off
 * for good), and end with the session.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LockRepositoryEmergencyPauseTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val start: ZonedDateTime = ZonedDateTime.of(2026, 10, 22, 10, 0, 0, 0, ZoneId.of("Europe/Paris"))

    /** Wall clock that follows the virtual time of the test scheduler. */
    private fun nowMillis(): Long = start.toInstant().toEpochMilli() + testScope.testScheduler.currentTime

    private val pauseMs = Constants.DEFAULT_EMERGENCY_UNLOCK_DURATION_MS

    private lateinit var profileDao: FakeProfileDao
    private lateinit var repository: LockRepository

    @Before
    fun setup() {
        profileDao = FakeProfileDao()
        repository = LockRepository(
            profileDao = profileDao,
            sessionDao = FakeSessionDao(),
            nfcTagDao = FakeNfcTagDao(),
            scheduleDao = FakeScheduleDao(),
            scheduleProfileDao = FakeScheduleProfileDao(),
            dataStore = PreferenceDataStoreFactory.create(
                scope = testScope,
                produceFile = { tmpFolder.newFile("test_prefs.preferences_pb") },
            ),
            ioDispatcher = testDispatcher,
            zonedNow = { start.plusNanos(testScope.testScheduler.currentTime * 1_000_000) },
        )
    }

    @After
    fun teardown() {
        repository.close()
    }

    private suspend fun lock() {
        val profileId = profileDao.insert(Profile(name = "Focus", blockedPackages = listOf("com.google.android.youtube")))
        repository.startLockSession(profileId)
    }

    @Test
    fun `starting a pause spends one unlock and pauses until the deadline`() = testScope.runTest {
        lock()
        val before = repository.getLockState().emergencyUnlocksRemaining

        assertTrue(repository.startEmergencyPause())

        assertTrue(repository.emergencyPause.value)
        assertEquals(nowMillis() + pauseMs, repository.emergencyPauseEndsAt.value)
        assertEquals(before - 1, repository.getLockState().emergencyUnlocksRemaining)
    }

    @Test
    fun `pause ends on its own at the deadline`() = testScope.runTest {
        lock()
        repository.startEmergencyPause()

        advanceTimeBy(pauseMs - 1)
        assertTrue(repository.emergencyPause.value)

        advanceTimeBy(2)
        assertFalse(repository.emergencyPause.value)
        assertEquals(0L, repository.emergencyPauseEndsAt.value)
    }

    @Test
    fun `no pause without a locked session`() = testScope.runTest {
        assertFalse(repository.startEmergencyPause())
        assertFalse(repository.emergencyPause.value)
    }

    @Test
    fun `no pause once the budget is spent`() = testScope.runTest {
        lock()
        repeat(repository.getLockState().emergencyUnlocksRemaining) {
            repository.startEmergencyPause()
            repository.endEmergencyPause()
        }

        assertFalse(repository.startEmergencyPause())
        assertFalse(repository.emergencyPause.value)
    }

    @Test
    fun `ending the session ends the pause`() = testScope.runTest {
        lock()
        repository.startEmergencyPause()

        repository.endLockSession(EndReason.NFC.value)

        assertFalse(repository.emergencyPause.value)
        assertEquals(0L, repository.emergencyPauseEndsAt.value)
    }

    @Test
    fun `an early end does not cut a later pause at the old deadline`() = testScope.runTest {
        lock()
        repository.startEmergencyPause()
        advanceTimeBy(pauseMs / 2)
        repository.endEmergencyPause()
        assertFalse(repository.emergencyPause.value)

        repository.startEmergencyPause()
        advanceTimeBy(pauseMs / 2 + 1) // past the first pause's deadline
        assertTrue(repository.emergencyPause.value)

        advanceTimeBy(pauseMs / 2)
        assertFalse(repository.emergencyPause.value)
    }
}
