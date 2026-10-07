package com.nathanb.lock.util

import android.app.ActivityManager
import android.content.Context

/**
 * Android's app pinning (Settings > Security > App pinning) keeps one app on screen and
 * refuses to show any other, including the home screen Lock sends blocked apps to.
 */
object AppPinning {

    fun isActive(context: Context): Boolean =
        context.getSystemService(ActivityManager::class.java)
            ?.lockTaskModeState
            ?.let { it != ActivityManager.LOCK_TASK_MODE_NONE }
            ?: false
}
