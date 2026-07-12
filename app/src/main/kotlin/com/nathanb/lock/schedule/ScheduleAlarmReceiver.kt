package com.nathanb.lock.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nathanb.lock.LockApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Wakes the schedule engine on window boundaries (explicit alarm PendingIntent) and on
 * every event that invalidates armed alarms or shifts wall-clock time: app update,
 * time/timezone change, exact-alarm permission change. Each trigger is a full
 * re-evaluation, so spurious or duplicate deliveries are harmless.
 */
class ScheduleAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_WINDOW_BOUNDARY,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            android.app.AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            -> Unit
            else -> return
        }

        val pendingResult = goAsync()
        val app = context.applicationContext as LockApplication
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.scheduleManager.evaluateAndRearm()
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_WINDOW_BOUNDARY = "com.nathanb.lock.action.SCHEDULE_WINDOW_BOUNDARY"
    }
}
