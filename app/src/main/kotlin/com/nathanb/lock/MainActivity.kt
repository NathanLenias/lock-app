package com.nathanb.lock

import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.nathanb.lock.nfc.NfcResult
import com.nathanb.lock.ui.LockApp
import com.nathanb.lock.ui.viewmodel.LockViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val viewModel: LockViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) Log.d(TAG, "onCreate — action=${intent.action}, recreated=${savedInstanceState != null}")
        enableEdgeToEdge()

        // A recreation (rotation, theme change, process restore) hands the launch intent
        // back verbatim. Only a fresh launch may treat it as a tag scan: replaying it would
        // toggle the session on every rotation (landscape regression, 1.3.0).
        val isFreshLaunch = savedInstanceState == null
        val launchIntent = intent
        val isNfcLaunch = isFreshLaunch && isNfcAction(launchIntent.action)

        setContent {
            LockApp(viewModel = viewModel, isNfcLaunch = isNfcLaunch)
        }

        // Handle NFC intent if app was launched via tag (background/manifest case)
        if (isFreshLaunch) {
            handleNfcIntent(launchIntent)
            neutralizeNfcIntent(launchIntent)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.cleanupUninstalledPackages()
        if (BuildConfig.DEBUG) Log.d(TAG, "onResume — enabling reader mode")
        viewModel.nfcManager.enableReaderMode(this) { tag ->
            // Reader mode callback runs on a binder thread — post to main via lifecycleScope
            lifecycleScope.launch {
                val result = viewModel.nfcManager.handleTag(tag) ?: return@launch
                if (BuildConfig.DEBUG) Log.d(TAG, "ReaderMode — result=$result")
                handleNfcResult(result)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (BuildConfig.DEBUG) Log.d(TAG, "onPause — disabling reader mode")
        viewModel.nfcManager.disableReaderMode(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (BuildConfig.DEBUG) Log.d(TAG, "onNewIntent — action=${intent.action}")
        setIntent(intent)
        handleNfcIntent(intent)
        neutralizeNfcIntent(intent)
    }

    /**
     * Once a tag intent has been handed to [handleNfcIntent] (which keeps its own reference),
     * the activity must not remember it: `getIntent()` is what a recreation replays, and a
     * scan must never be replayed. Only tag intents are replaced, so a plain launcher intent
     * keeps its categories.
     */
    private fun neutralizeNfcIntent(handled: Intent) {
        if (!isNfcAction(handled.action)) return
        setIntent(Intent(Intent.ACTION_MAIN))
    }

    private fun isNfcAction(action: String?): Boolean =
        action == NfcAdapter.ACTION_NDEF_DISCOVERED ||
            action == NfcAdapter.ACTION_TAG_DISCOVERED ||
            action == NfcAdapter.ACTION_TECH_DISCOVERED

    /**
     * Handle NFC intents delivered via the manifest (background/cold start case).
     * In foreground, Reader Mode handles tags directly via callback.
     */
    private fun handleNfcIntent(intent: Intent) {
        if (BuildConfig.DEBUG) Log.d(TAG, "handleNfcIntent — action=${intent.action}")
        lifecycleScope.launch {
            val result = viewModel.nfcManager.handleIntent(intent)
            if (BuildConfig.DEBUG) Log.d(TAG, "handleNfcIntent — result=$result")
            if (result == null) return@launch
            handleNfcResult(result)
        }
    }

    private fun handleNfcResult(result: NfcResult) {
        viewModel.handleNfcResult(result)

        when (result) {
            is NfcResult.TagPaired -> {
                // Toast handled by NfcTagsScreen / OnboardingScreen
            }
            is NfcResult.Started -> {
                val tagInfo = result.tagName?.let { " ($it)" } ?: ""
                Toast.makeText(this, getString(R.string.toast_blocking_on, tagInfo), Toast.LENGTH_SHORT).show()
            }
            is NfcResult.Stopped -> {
                val tagInfo = result.tagName?.let { " ($it)" } ?: ""
                Toast.makeText(this, getString(R.string.toast_blocking_off, tagInfo), Toast.LENGTH_SHORT).show()
            }
            is NfcResult.Paused -> {
                Toast.makeText(this, getString(R.string.toast_pause_resumes, result.resumeInMinutes), Toast.LENGTH_SHORT).show()
            }
            is NfcResult.IgnoredNoEscapeActive -> {
                Toast.makeText(this, getString(R.string.toast_no_escape_active), Toast.LENGTH_SHORT).show()
            }
            is NfcResult.UnknownTag -> {
                Toast.makeText(this, getString(R.string.toast_unknown_tag), Toast.LENGTH_SHORT).show()
            }
            is NfcResult.Error -> {
                Toast.makeText(this, getString(result.messageRes), Toast.LENGTH_SHORT).show()
            }
        }
    }
}
