package com.nathanb.lock.nfc

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import com.nathanb.lock.BuildConfig
import com.nathanb.lock.data.model.EndReason
import com.nathanb.lock.data.model.ProfileType
import com.nathanb.lock.data.repository.LockRepository

/** Outcome of trying to write our routing data to a tag during pairing. */
enum class NdefWriteResult {
    /** Data written: the tag works everywhere, including when the app is closed. */
    SUCCESS,

    /** Contact lost mid-write (tag moved too soon). Recontact retries automatically. */
    TRANSIENT_FAILURE,

    /** Tag is write-protected or not NDEF-capable: writing will never succeed. */
    WRITE_PROTECTED,
}

sealed interface NfcResult {
    /**
     * A tag was presented in pairing mode. [writeResult] tells whether the routing data
     * could be written. On TRANSIENT_FAILURE the pairing screen keeps waiting so the next
     * contact retries; SUCCESS / WRITE_PROTECTED are terminal for this tag.
     */
    data class TagPaired(val uid: String, val writeResult: NdefWriteResult) : NfcResult
    data class Started(val profileId: Long, val tagName: String?, val isNoEscape: Boolean) : NfcResult
    data class Stopped(val tagName: String?) : NfcResult
    data object IgnoredNoEscapeActive : NfcResult
    data object UnknownTag : NfcResult
    data class Error(val message: String) : NfcResult
}

class NfcManager(
    private val repository: LockRepository,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    companion object {
        private const val TAG = "NfcManager"
        private const val MIME_TYPE = "application/vnd.lock.toggle"
        private const val APP_PACKAGE = "com.nathanb.lock"
        private const val PAIRING_GRACE_MS = 3_000L
    }

    private var isPairingMode = false

    /**
     * Set when a tag pairs successfully. Within [PAIRING_GRACE_MS], that tag can't toggle a
     * session: leaving the pairing screen with the tag still near the phone used to fire a
     * lock session on the rebound.
     */
    private var justPairedUid: String? = null
    private var justPairedAt: Long = 0L

    fun enablePairingMode() {
        isPairingMode = true
    }

    fun disablePairingMode() {
        isPairingMode = false
    }

    /**
     * Rewrite the routing data on an already-paired tag (repair path for tags whose write
     * failed, that were erased by another app, or that were paired before we wrote NDEF).
     */
    private var rewriteUid: String? = null

    fun enableRewriteMode(uid: String) {
        rewriteUid = uid
    }

    fun disableRewriteMode() {
        rewriteUid = null
    }

    /**
     * Enable NFC Reader Mode — modern API that delivers tags via direct callback
     * instead of the intent-based foreground dispatch system.
     *
     * Benefits over foreground dispatch:
     * - Direct Tag callback (no PendingIntent/Intent overhead)
     * - Disables platform NFC sounds (app provides its own feedback)
     * - More reliable across lifecycle transitions
     *
     * @param onTagDiscovered called on a binder thread when a tag is detected
     */
    fun enableReaderMode(activity: ComponentActivity, onTagDiscovered: (Tag) -> Unit) {
        val adapter = NfcAdapter.getDefaultAdapter(activity)
        if (adapter == null) {
            if (BuildConfig.DEBUG) Log.w(TAG, "enableReaderMode — adapter is null!")
            return
        }
        try {
            adapter.enableReaderMode(
                activity,
                onTagDiscovered,
                NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_V,
                null,
            )
            if (BuildConfig.DEBUG) Log.d(TAG, "enableReaderMode — OK")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "enableReaderMode — FAILED: ${e.message}", e)
        }
    }

    fun disableReaderMode(activity: ComponentActivity) {
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return
        adapter.disableReaderMode(activity)
        // Don't reset isPairingMode here — onPause/onResume cycle
        // (e.g. going to Settings for permissions) should preserve pairing state
    }

    /**
     * Process a Tag directly (used by Reader Mode in foreground).
     */
    suspend fun handleTag(tag: Tag): NfcResult? =
        handleScan(tag.id.toHexString()) { writeNdefToTag(tag) }

    /**
     * Scan decision tree, independent of the Android [Tag] object so it can be unit-tested.
     * [write] performs the actual NDEF write and is only invoked when we intend to write.
     */
    internal suspend fun handleScan(uid: String, write: () -> NdefWriteResult): NfcResult? {
        if (BuildConfig.DEBUG) Log.d(TAG, "Tag scanned: $uid")

        // Rewrite mode (repair): only the targeted tag is written, nothing else happens.
        rewriteUid?.let { target ->
            if (uid != target) return null
            val writeResult = write()
            if (writeResult != NdefWriteResult.TRANSIENT_FAILURE) rewriteUid = null
            if (BuildConfig.DEBUG) Log.d(TAG, "Tag rewritten: $uid ($writeResult)")
            return NfcResult.TagPaired(uid, writeResult)
        }

        // Pairing mode is STICKY: while the pairing screen is up, every contact is a pairing
        // attempt, never a session toggle. A transient write failure (tag moved too soon) keeps
        // the mode on, so simply recontacting the tag retries the write.
        if (isPairingMode) {
            val writeResult = write()
            if (writeResult != NdefWriteResult.TRANSIENT_FAILURE) {
                isPairingMode = false
                justPairedUid = uid
                justPairedAt = clock()
            }
            if (BuildConfig.DEBUG) Log.d(TAG, "Tag paired: $uid ($writeResult)")
            return NfcResult.TagPaired(uid, writeResult)
        }

        // Grace period: ignore the tag that was just paired (rebound while leaving the screen).
        val paired = justPairedUid
        if (paired != null) {
            if (uid == paired && clock() - justPairedAt < PAIRING_GRACE_MS) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Tap ignored — pairing grace period")
                return null
            }
            justPairedUid = null
        }

        return processKnownTag(uid)
    }

    /**
     * Process an NFC Intent (used for background/manifest-based tag delivery).
     */
    suspend fun handleIntent(intent: Intent): NfcResult? {
        val action = intent.action ?: return null
        if (action != NfcAdapter.ACTION_TAG_DISCOVERED &&
            action != NfcAdapter.ACTION_NDEF_DISCOVERED &&
            action != NfcAdapter.ACTION_TECH_DISCOVERED
        ) return null

        val tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java) ?: return null
        return handleTag(tag)
    }

    /**
     * Resolve what a known tag does against the current lock state:
     * - locked + no-escape  -> ignored (timer is sacred)
     * - locked + standard   -> stop (universal deactivator: any tag stops a standard session)
     * - idle                -> start the tag's own profile
     */
    internal suspend fun processKnownTag(uid: String): NfcResult {
        val knownTag = repository.findNfcTag(uid)
        if (knownTag == null) {
            if (!repository.hasAnyNfcTag()) {
                return NfcResult.Error("Aucun tag associé")
            }
            if (BuildConfig.DEBUG) Log.d(TAG, "Unknown tag: $uid")
            return NfcResult.UnknownTag
        }

        val state = repository.getLockState()
        return if (state.isLocked) {
            if (state.isNoEscape) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Tap ignored — no-escape session active")
                NfcResult.IgnoredNoEscapeActive
            } else {
                repository.endLockSession(EndReason.NFC.value)
                if (BuildConfig.DEBUG) Log.d(TAG, "Stopped via NFC (tag: ${knownTag.name})")
                NfcResult.Stopped(knownTag.name)
            }
        } else {
            val profileId = knownTag.profileId ?: repository.getDefaultProfile()?.id
            if (profileId == null) {
                NfcResult.Error("Aucun profil configuré")
            } else {
                repository.startLockSession(profileId)
                val isNoEscape = repository.getProfile(profileId)
                    ?.let { ProfileType.fromValue(it.type) == ProfileType.NO_ESCAPE } ?: false
                if (BuildConfig.DEBUG) Log.d(TAG, "Started via NFC (profile=$profileId, SE=$isNoEscape)")
                NfcResult.Started(profileId, knownTag.name, isNoEscape)
            }
        }
    }

    /**
     * Write NDEF message to tag so it's routed via NDEF_DISCOVERED (highest priority)
     * instead of TAG_DISCOVERED (lowest priority, causes dual-task bug).
     *
     * The message contains:
     * 1. A custom MIME record (application/vnd.lock.toggle) — used by Android for intent routing
     * 2. An Android Application Record (AAR) — forces Android to launch Lock specifically
     */
    private fun writeNdefToTag(tag: Tag): NdefWriteResult {
        val mimeRecord = NdefRecord.createMime(MIME_TYPE, "lock".toByteArray())
        val aarRecord = NdefRecord.createApplicationRecord(APP_PACKAGE)
        val ndefMessage = NdefMessage(arrayOf(mimeRecord, aarRecord))

        return try {
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.connect()
                if (!ndef.isWritable) {
                    ndef.close()
                    if (BuildConfig.DEBUG) Log.w(TAG, "Tag is read-only")
                    return NdefWriteResult.WRITE_PROTECTED
                }
                ndef.writeNdefMessage(ndefMessage)
                ndef.close()
                if (BuildConfig.DEBUG) Log.d(TAG, "NDEF written to formatted tag")
                NdefWriteResult.SUCCESS
            } else {
                // Tag not NDEF-formatted — format it first
                val formatable = NdefFormatable.get(tag)
                    ?: run {
                        if (BuildConfig.DEBUG) Log.w(TAG, "Tag does not support NDEF")
                        return NdefWriteResult.WRITE_PROTECTED
                    }
                formatable.connect()
                formatable.format(ndefMessage)
                formatable.close()
                if (BuildConfig.DEBUG) Log.d(TAG, "NDEF written to newly formatted tag")
                NdefWriteResult.SUCCESS
            }
        } catch (e: android.nfc.FormatException) {
            // The tag rejected the message itself (bad format, too small): retrying won't help.
            if (BuildConfig.DEBUG) Log.e(TAG, "NDEF format rejected: ${e.message}", e)
            NdefWriteResult.WRITE_PROTECTED
        } catch (e: Exception) {
            // IOException & co: contact was lost mid-write. Recontacting the tag retries.
            if (BuildConfig.DEBUG) Log.e(TAG, "NDEF write interrupted: ${e.message}", e)
            NdefWriteResult.TRANSIENT_FAILURE
        }
    }

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02X".format(it) }
}
