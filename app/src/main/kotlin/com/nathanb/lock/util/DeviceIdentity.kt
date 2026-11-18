package com.nathanb.lock.util

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.security.MessageDigest

/**
 * Tells "this phone" from "another phone" when a backup is imported, for the garden: it is
 * restored on a new phone, not after a reinstall on the same one.
 *
 * ANDROID_ID is scoped to the app's signing key and the device: it survives an uninstall
 * and changes on another device or after a factory reset. Nothing is ever sent anywhere;
 * the backup file only carries a salted hash of it, never the identifier itself.
 */
object DeviceIdentity {

    private const val SALT = "lock.garden.v1"

    /** Null when the system gives no identifier: the garden is then never restored. */
    @SuppressLint("HardwareIds") // Not for tracking: compared locally with a local file.
    fun fingerprint(context: Context): String? {
        val id = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        return if (id.isNullOrEmpty()) null else hash(id)
    }

    internal fun hash(id: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest("$SALT:$id".toByteArray())
            .take(16)
            .joinToString("") { "%02x".format(it) }
}
