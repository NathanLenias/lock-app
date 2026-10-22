package com.nathanb.lock.service

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager

/**
 * Holds back a foreground check while the user can't see the screen (screen off or lock
 * screen showing) and runs it once they unlock. Blocking when the pause ends with the
 * screen off would draw the overlay over the lock screen, and the app behind it would
 * still be there after unlocking, since coming back to it opens no new window.
 */
internal class UnlockDeferral(
    private val context: Context,
    private val check: () -> Unit,
) {
    private var pending = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_USER_PRESENT || !pending) return
            pending = false
            check()
        }
    }

    fun register() {
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_USER_PRESENT), Context.RECEIVER_NOT_EXPORTED)
    }

    fun unregister() {
        runCatching { context.unregisterReceiver(receiver) }
    }

    /** True when the check may run now; otherwise it is deferred until the user unlocks. */
    fun userIsPresent(): Boolean {
        val power = context.getSystemService(PowerManager::class.java)
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        pending = !power.isInteractive || keyguard.isKeyguardLocked
        return !pending
    }
}
