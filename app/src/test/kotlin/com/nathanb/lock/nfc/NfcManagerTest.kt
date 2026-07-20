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
import org.junit.Assert.assertNull
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
    private var now = 1_000_000L

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
        nfc = NfcManager(repository, clock = { now })
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
        repository.createProfile("Std", listOf("com.a")) // default
        val se = repository.createProfile("SE", listOf("com.b"), ProfileType.NO_ESCAPE, 600_000L)
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

    @Test
    fun `tag resolving to a profile with no apps returns Error and does not lock`() = testScope.runTest {
        // Onboarding skip case: the default profile exists but has no blocked apps yet.
        val p = repository.createProfile("Std", emptyList())
        repository.addNfcTag("U1", "Tag", p)

        assertTrue(nfc.processKnownTag("U1") is NfcResult.Error)
        assertFalse(repository.getLockState().isLocked)
    }

    @Test
    fun `master tag falling back to an empty default profile returns Error and does not lock`() = testScope.runTest {
        repository.createProfile("Std", emptyList())
        repository.addNfcTag("U1", "Tag") // no profile bound (onboarding master switch)

        assertTrue(nfc.processKnownTag("U1") is NfcResult.Error)
        assertFalse(repository.getLockState().isLocked)
    }

    // --- Sticky pairing mode + write outcomes ---

    @Test
    fun `transient write failure keeps pairing mode on for a retry`() = testScope.runTest {
        repository.createProfile("Std", listOf("com.a"))
        nfc.enablePairingMode()

        val first = nfc.handleScan("U1") { NdefWriteResult.TRANSIENT_FAILURE }
        assertEquals(NfcResult.TagPaired("U1", NdefWriteResult.TRANSIENT_FAILURE), first)

        // Recontacting the tag retries the write instead of toggling a session.
        val retry = nfc.handleScan("U1") { NdefWriteResult.SUCCESS }
        assertEquals(NfcResult.TagPaired("U1", NdefWriteResult.SUCCESS), retry)
    }

    @Test
    fun `write-protected tag still pairs and ends pairing mode`() = testScope.runTest {
        repository.createProfile("Std", listOf("com.a"))
        nfc.enablePairingMode()

        val result = nfc.handleScan("U1") { NdefWriteResult.WRITE_PROTECTED }

        assertEquals(NfcResult.TagPaired("U1", NdefWriteResult.WRITE_PROTECTED), result)
        // Pairing mode consumed: a later scan (past grace) is a normal toggle.
        repository.addNfcTag("U1", "Tag")
        now += 5_000L
        assertTrue(nfc.handleScan("U1") { NdefWriteResult.SUCCESS } is NfcResult.Started)
    }

    @Test
    fun `tag bouncing right after pairing does not start a session`() = testScope.runTest {
        val p = repository.createProfile("Std", listOf("com.a"))
        nfc.enablePairingMode()
        nfc.handleScan("U1") { NdefWriteResult.SUCCESS }
        repository.addNfcTag("U1", "Tag", p)

        // Rebound within the grace period: ignored.
        now += 500L
        assertNull(nfc.handleScan("U1") { NdefWriteResult.SUCCESS })
        assertFalse(repository.getLockState().isLocked)

        // Past the grace period, the tag behaves normally again.
        now += PAIRING_GRACE_TEST_MS
        assertTrue(nfc.handleScan("U1") { NdefWriteResult.SUCCESS } is NfcResult.Started)
        assertTrue(repository.getLockState().isLocked)
    }

    @Test
    fun `rewrite mode only writes the targeted tag and never toggles`() = testScope.runTest {
        val p = repository.createProfile("Std", listOf("com.a"))
        repository.addNfcTag("U1", "Tag", p)
        repository.addNfcTag("U2", "Other", p)

        nfc.enableRewriteMode("U1")
        // A different tag is ignored entirely (no session started).
        assertNull(nfc.handleScan("U2") { NdefWriteResult.SUCCESS })
        assertFalse(repository.getLockState().isLocked)

        val result = nfc.handleScan("U1") { NdefWriteResult.SUCCESS }
        assertEquals(NfcResult.TagPaired("U1", NdefWriteResult.SUCCESS), result)
        assertFalse(repository.getLockState().isLocked) // rewriting never locks
    }

    @Test
    fun `write failures accumulate then reset on success`() = testScope.runTest {
        repository.createProfile("Std", listOf("com.a"))
        nfc.enablePairingMode()

        repeat(NfcManager.MAX_WRITE_RETRIES) {
            nfc.handleScan("U1") { NdefWriteResult.TRANSIENT_FAILURE }
        }
        // The UI can now offer "pair anyway".
        assertEquals(NfcManager.MAX_WRITE_RETRIES, nfc.consecutiveWriteFailures())

        // Pairing mode is still on: the tag can still be written on a later contact.
        nfc.handleScan("U1") { NdefWriteResult.SUCCESS }
        assertEquals(0, nfc.consecutiveWriteFailures())
    }

    @Test
    fun `forcePairWithoutWrite ends pairing mode`() = testScope.runTest {
        val p = repository.createProfile("Std", listOf("com.a"))
        nfc.enablePairingMode()
        nfc.handleScan("U1") { NdefWriteResult.TRANSIENT_FAILURE }

        nfc.forcePairWithoutWrite()
        repository.addNfcTag("U1", "Tag", p)

        // Pairing consumed: the tag toggles normally again (no grace period was armed).
        assertTrue(nfc.handleScan("U1") { NdefWriteResult.SUCCESS } is NfcResult.Started)
    }

    private companion object {
        const val PAIRING_GRACE_TEST_MS = 3_000L
    }
}
