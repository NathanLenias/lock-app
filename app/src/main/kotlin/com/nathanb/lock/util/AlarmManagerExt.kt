package com.nathanb.lock.util

import android.app.AlarmManager
import android.app.PendingIntent

/**
 * Arms a wall-clock alarm that fires even while the phone is in Doze.
 *
 * Exact when the user granted the exact-alarm permission. Otherwise inexact but still
 * allowed while idle: a plain [AlarmManager.set] would be deferred to the next Doze
 * maintenance window, which can be hours late on a phone left asleep overnight.
 */
fun AlarmManager.setWakeupAlarm(triggerAtEpochMillis: Long, operation: PendingIntent) {
    if (canScheduleExactAlarms()) {
        setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtEpochMillis, operation)
    } else {
        setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtEpochMillis, operation)
    }
}
