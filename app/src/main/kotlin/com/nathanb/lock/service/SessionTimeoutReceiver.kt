package com.nathanb.lock.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nathanb.lock.BuildConfig
import com.nathanb.lock.LockApplication
import com.nathanb.lock.data.model.EndReason
import com.nathanb.lock.data.model.LockState
import com.nathanb.lock.util.setWakeupAlarm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Ends a session when its timeout (global safety timeout or no-escape duration) elapses.
 *
 * Driven by an AlarmManager wake-up alarm, not a coroutine delay: `delay` counts on the
 * uptime clock, which stops in deep sleep, so a 5 h timeout could run 6 h or more on a
 * phone left idle. A wall-clock alarm fires on time whether the phone sleeps or not.
 *
 * The alarm carries the start time of the session it was armed for and only ends the
 * session still matching it, so a stale alarm never kills a later session.
 */
class SessionTimeoutReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SESSION_TIMEOUT) return
        val expectedStartTime = intent.getLongExtra(EXTRA_SESSION_START_TIME, -1L)
        val reason = intent.getStringExtra(EXTRA_END_REASON) ?: EndReason.TIMEOUT.value

        val pendingResult = goAsync()
        val app = context.applicationContext as LockApplication
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Hand control back to Android well before the broadcast ANR limit.
                withTimeoutOrNull(RECEIVER_TIMEOUT_MS) {
                    val state = app.repository.getLockState()
                    if (matchesSession(state, expectedStartTime)) {
                        if (app.repository.endOrContinueTimedSession(reason)) {
                            // Blocking continuity: the session now runs as a standard one
                            // until the next tag scan. Re-posting refreshes the notification
                            // and re-arms the global safety timeout.
                            if (BuildConfig.DEBUG) Log.d(TAG, "Session continued until scan")
                            SessionNotifier.start(context)
                        } else {
                            if (BuildConfig.DEBUG) Log.d(TAG, "Session auto-unlock ($reason)")
                            SessionNotifier.stop(context)
                        }
                    } else if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Stale timeout alarm ignored (expected start=$expectedStartTime)")
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "SessionTimeoutReceiver"
        const val ACTION_SESSION_TIMEOUT = "com.nathanb.lock.action.SESSION_TIMEOUT"
        private const val EXTRA_SESSION_START_TIME = "session_start_time"
        private const val EXTRA_END_REASON = "end_reason"
        private const val REQUEST_CODE = 300
        private const val RECEIVER_TIMEOUT_MS = 8_000L

        /** True when the alarm belongs to the session currently active. Pure, unit-tested. */
        fun matchesSession(state: LockState, expectedStartTime: Long): Boolean {
            val start = state.sessionStartTime ?: return false
            return state.isLocked && start == expectedStartTime
        }

        /** Arms (or re-arms, replacing any previous one) the timeout alarm for a session. */
        fun arm(context: Context, triggerAtEpochMillis: Long, sessionStartTime: Long, reason: String) {
            val intent = Intent(context, SessionTimeoutReceiver::class.java)
                .setAction(ACTION_SESSION_TIMEOUT)
                .putExtra(EXTRA_SESSION_START_TIME, sessionStartTime)
                .putExtra(EXTRA_END_REASON, reason)
            alarmManager(context).setWakeupAlarm(triggerAtEpochMillis, pendingIntent(context, intent))
        }

        /** Cancels the timeout alarm. Extras don't matter for matching, only action + class. */
        fun cancel(context: Context) {
            val intent = Intent(context, SessionTimeoutReceiver::class.java)
                .setAction(ACTION_SESSION_TIMEOUT)
            alarmManager(context).cancel(pendingIntent(context, intent))
        }

        private fun alarmManager(context: Context): AlarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        private fun pendingIntent(context: Context, intent: Intent): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
