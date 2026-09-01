package com.nathanb.lock.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.nathanb.lock.data.model.EndReason
import com.nathanb.lock.data.model.NfcTag
import com.nathanb.lock.data.model.Profile
import com.nathanb.lock.data.model.ProfileType
import com.nathanb.lock.fake.FakeNfcTagDao
import com.nathanb.lock.fake.FakeProfileDao
import com.nathanb.lock.fake.FakeScheduleDao
import com.nathanb.lock.fake.FakeScheduleProfileDao
import com.nathanb.lock.fake.FakeSessionDao
import com.nathanb.lock.util.Constants
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Blocking continuity: when a no-escape session's timer ends, a profile that opted in
 * (and can be unlocked by a tag) converts in place into a standard session instead of
 * unlocking. Everything else must keep ending normally.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LockRepositoryContinuityTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var profileDao: FakeProfileDao
    private lateinit var sessionDao: FakeSessionDao
    private lateinit var nfcTagDao: FakeNfcTagDao
    private lateinit var repository: LockRepository

    @Before
    fun setup() {
        profileDao = FakeProfileDao()
        sessionDao = FakeSessionDao()
        nfcTagDao = FakeNfcTagDao()
        repository = LockRepository(
            profileDao = profileDao,
            sessionDao = sessionDao,
            nfcTagDao = nfcTagDao,
            scheduleDao = FakeScheduleDao(),
            scheduleProfileDao = FakeScheduleProfileDao(),
            dataStore = PreferenceDataStoreFactory.create(
                scope = testScope,
                produceFile = { tmpFolder.newFile("test_prefs.preferences_pb") },
            ),
            ioDispatcher = testDispatcher,
        )
    }

    @After
    fun teardown() {
        repository.close()
    }

    private suspend fun startNoEscapeSession(continuity: Boolean): Long {
        val profileId = profileDao.insert(
            Profile(
                name = "Focus",
                blockedPackages = listOf("com.instagram.android"),
                type = ProfileType.NO_ESCAPE.value,
                durationMs = 15 * 60_000L,
                continuity = continuity,
            ),
        )
        repository.startLockSession(profileId)
        return profileId
    }

    @Test
    fun `timer end without continuity ends the session`() = testScope.runTest {
        startNoEscapeSession(continuity = false)
        nfcTagDao.insert(NfcTag(uid = "AA", name = "Tag"))

        val continued = repository.endOrContinueTimedSession(EndReason.DURATION.value)

        assertFalse(continued)
        assertFalse(repository.getLockState().isLocked)
        assertEquals(EndReason.DURATION.value, sessionDao.getAllOnce().single().endReason)
    }

    @Test
    fun `timer end with continuity and a tag keeps blocking as a standard session`() = testScope.runTest {
        startNoEscapeSession(continuity = true)
        nfcTagDao.insert(NfcTag(uid = "AA", name = "Tag"))

        val continued = repository.endOrContinueTimedSession(EndReason.DURATION.value)

        assertTrue(continued)
        val state = repository.getLockState()
        assertTrue(state.isLocked)
        assertFalse(state.isNoEscape)
        assertNull(state.lockDurationMs)
        // Standard session now: the emergency safety valve must be back.
        assertEquals(Constants.DEFAULT_MAX_EMERGENCY_UNLOCKS, state.emergencyUnlocksRemaining)
        // Same session row, still open.
        assertNull(sessionDao.getAllOnce().single().endTime)
    }

    @Test
    fun `timer end with continuity but no tag ends the session`() = testScope.runTest {
        startNoEscapeSession(continuity = true)

        val continued = repository.endOrContinueTimedSession(EndReason.DURATION.value)

        assertFalse(continued)
        assertFalse(repository.getLockState().isLocked)
    }

    @Test
    fun `global timeout ends the session even with continuity`() = testScope.runTest {
        startNoEscapeSession(continuity = true)
        nfcTagDao.insert(NfcTag(uid = "AA", name = "Tag"))

        val continued = repository.endOrContinueTimedSession(EndReason.TIMEOUT.value)

        assertFalse(continued)
        assertFalse(repository.getLockState().isLocked)
        assertEquals(EndReason.TIMEOUT.value, sessionDao.getAllOnce().single().endReason)
    }

    @Test
    fun `continued session then stops normally on the next scan path`() = testScope.runTest {
        startNoEscapeSession(continuity = true)
        nfcTagDao.insert(NfcTag(uid = "AA", name = "Tag"))
        repository.endOrContinueTimedSession(EndReason.DURATION.value)

        repository.endLockSession(EndReason.NFC.value)

        assertFalse(repository.getLockState().isLocked)
        assertEquals(EndReason.NFC.value, sessionDao.getAllOnce().single().endReason)
    }
}
