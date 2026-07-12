package com.nathanb.lock.nfc

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.nathanb.lock.data.model.ProfileType
import com.nathanb.lock.data.repository.LockRepository
import com.nathanb.lock.fake.FakeNfcTagDao
import com.nathanb.lock.fake.FakeProfileDao
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

@OptIn(ExperimentalCoroutinesApi::class)
class NfcManagerTest {

    @get:Rule val tmpFolder = TemporaryFolder()
    private val testScope = TestScope(UnconfinedTestDispatcher())

    private lateinit var repository: LockRepository
    private lateinit var nfc: NfcManager

    @Before
    fun setup() {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tmpFolder.newFile("nfc_prefs.preferences_pb") },
        )
        repository = LockRepository(
            profileDao = FakeProfileDao(),
            sessionDao = FakeSessionDao(),
            nfcTagDao = FakeNfcTagDao(),
            scheduleDao = com.nathanb.lock.fake.FakeScheduleDao(),
            scheduleProfileDao = com.nathanb.lock.fake.FakeScheduleProfileDao(),
            dataStore = dataStore,
        )
        nfc = NfcManager(repository)
    }

    @After fun teardown() = repository.close()

    @Test
    fun `idle plus standard tag starts the tag's profile`() = testScope.runTest {
        val p = repository.createProfile("Std", listOf("com.a"))
        repository.addNfcTag("U1", "Tag", p)

        val result = nfc.processKnownTag("U1")

        assertTrue(result is NfcResult.Started)
        assertEquals(p, (result as NfcResult.Started).profileId)
        assertFalse(result.isNoEscape)
        assertTrue(repository.getLockState().isLocked)
    }

    @Test
    fun `idle plus no-escape tag starts with isNoEscape true`() = testScope.runTest {
        repository.createProfile("Std", emptyList()) // default
        val se = repository.createProfile("SE", emptyList(), ProfileType.NO_ESCAPE, 600_000L)
        repository.addNfcTag("U2", "SE tag", se)

        val result = nfc.processKnownTag("U2")

        assertTrue(result is NfcResult.Started)
        assertTrue((result as NfcResult.Started).isNoEscape)
        assertEquals(0, repository.getLockState().emergencyUnlocksRemaining)
    }

    @Test
    fun `any tag stops an active standard session`() = testScope.runTest {
        val p = repository.createProfile("Std", emptyList())
        val other = repository.createProfile("Other", emptyList())
        repository.addNfcTag("U1", "Tag P", p)
        repository.addNfcTag("U2", "Tag Other", other)
        repository.startLockSession(p)

        // Tap the OTHER profile's tag -> stops, does not switch
        val result = nfc.processKnownTag("U2")

        assertTrue(result is NfcResult.Stopped)
        assertFalse(repository.getLockState().isLocked)
    }

    @Test
    fun `no tag stops a no-escape session`() = testScope.runTest {
        repository.createProfile("Std", emptyList())
        val se = repository.createProfile("SE", emptyList(), ProfileType.NO_ESCAPE, 600_000L)
        repository.addNfcTag("U2", "SE tag", se)
        repository.startLockSession(se)

        val result = nfc.processKnownTag("U2")

        assertTrue(result is NfcResult.IgnoredNoEscapeActive)
        assertTrue(repository.getLockState().isLocked)
    }

    @Test
    fun `unknown uid with tags present returns UnknownTag`() = testScope.runTest {
        val p = repository.createProfile("Std", emptyList())
        repository.addNfcTag("U1", "Tag", p)

        assertTrue(nfc.processKnownTag("NOPE") is NfcResult.UnknownTag)
    }

    @Test
    fun `no tags at all returns Error`() = testScope.runTest {
        assertTrue(nfc.processKnownTag("U1") is NfcResult.Error)
    }
}
