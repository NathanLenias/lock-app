package com.nathanb.lock.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Rating entry points. The explicit "Rate on the store" button uses [openPlayStore]: the
 * In-App Review card is quota-limited and silently skipped by Google (already reviewed,
 * sideloaded build, opaque eligibility rules) with no way to detect it, so a button wired
 * to it regularly does nothing at all.
 *
 * [requestReview] is kept for a future contextual prompt (e.g. after a successful session),
 * the use case the In-App Review API is actually designed for. Not wired to any UI today.
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
