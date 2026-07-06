package com.nathanb.lock.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Triggers Google's native In-App Review card, with a Play Store fallback.
 *
 * Note: the native card is quota-limited and controlled by Google — it may not appear even when
 * requested (e.g. already reviewed, quota exceeded, debug builds). When the request itself fails,
 * we fall back to opening the Play Store listing so the action always leads somewhere.
 */
object RateHelper {

    fun requestReview(activity: Activity) {
        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                // Card may or may not show (Google's call); completion fires either way.
                manager.launchReviewFlow(activity, task.result)
            } else {
                openPlayStore(activity)
            }
        }
    }

    fun openPlayStore(context: Context) {
        val pkg = context.packageName
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).apply {
            setPackage("com.android.vending")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(market)
        } catch (_: ActivityNotFoundException) {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

/** Walk up the context wrappers to find the hosting Activity (needed by the review flow). */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
