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

sealed interface NfcResult {
    data class TagPaired(val uid: String, val ndefWritten: Boolean) : NfcResult
    data class Started(val profileId: Long, val tagName: String?, val isNoEscape: Boolean) : NfcResult
    data class Stopped(val tagName: String?) : NfcResult
    data object IgnoredNoEscapeActive : NfcResult
    data object UnknownTag : NfcResult
    data class Error(val message: String) : NfcResult
}

class NfcManager(
    private val repository: LockRepository,
) {
    companion object {
        private const val TAG = "NfcManager"
        private const val MIME_TYPE = "application/vnd.lock.toggle"
        private const val APP_PACKAGE = "com.nathanb.lock"
    }

    private var isPairingMode = false

    fun enablePairingMode() {
        isPairingMode = true
    }

    fun disablePairingMode() {
        isPairingMode = false
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
    suspend fun handleTag(tag: Tag): NfcResult? {
        val uid = tag.id.toHexString()
        if (BuildConfig.DEBUG) Log.d(TAG, "Tag scanned: $uid")

        // Pairing mode — add this tag and write NDEF data
        if (isPairingMode) {
            isPairingMode = false
            val ndefWritten = writeNdefToTag(tag)
            if (BuildConfig.DEBUG) Log.d(TAG, "Tag paired: $uid (NDEF written: $ndefWritten)")
            return NfcResult.TagPaired(uid, ndefWritten)
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
    private fun writeNdefToTag(tag: Tag): Boolean {
        val mimeRecord = NdefRecord.createMime(MIME_TYPE, "lock".toByteArray())
        val aarRecord = NdefRecord.createApplicationRecord(APP_PACKAGE)
        val ndefMessage = NdefMessage(arrayOf(mimeRecord, aarRecord))

        return try {
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.connect()
                if (ndef.isWritable) {
                    ndef.writeNdefMessage(ndefMessage)
                    ndef.close()
                    if (BuildConfig.DEBUG) Log.d(TAG, "NDEF written to formatted tag")
                    true
                } else {
                    ndef.close()
                    if (BuildConfig.DEBUG) Log.w(TAG, "Tag is read-only")
                    false
                }
            } else {
                // Tag not NDEF-formatted — format it first
                val formatable = NdefFormatable.get(tag)
                if (formatable != null) {
                    formatable.connect()
                    formatable.format(ndefMessage)
                    formatable.close()
                    if (BuildConfig.DEBUG) Log.d(TAG, "NDEF written to newly formatted tag")
                    true
                } else {
                    if (BuildConfig.DEBUG) Log.w(TAG, "Tag does not support NDEF")
                    false
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to write NDEF: ${e.message}", e)
            false
        }
    }

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02X".format(it) }
}
