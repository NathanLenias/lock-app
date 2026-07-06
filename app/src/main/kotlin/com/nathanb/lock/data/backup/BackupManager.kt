package com.nathanb.lock.data.backup

import android.content.Context
import android.net.Uri
import com.nathanb.lock.data.model.NfcTag
import com.nathanb.lock.data.model.Profile
import com.nathanb.lock.data.model.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class BackupData(
    val profiles: List<Profile>,
    val nfcTags: List<NfcTag>,
    val sessions: List<Session>,
)

object BackupManager {

    private const val BACKUP_VERSION = 2
    private val FILE_NAME_FORMAT = DateTimeFormatter.ofPattern("ddMMyyyyHHmm")

    fun suggestFileName(): String {
        val timestamp = LocalDateTime.now().format(FILE_NAME_FORMAT)
        return "lock-backup-$timestamp.json"
    }

    suspend fun export(
        context: Context,
        uri: Uri,
        profiles: List<Profile>,
        nfcTags: List<NfcTag>,
        sessions: List<Session>,
    ): Unit = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("version", BACKUP_VERSION)
            put("exportDate", Instant.now().toString())
            put("profiles", serializeProfiles(profiles))
            put("nfcTags", serializeNfcTags(nfcTags))
            put("sessions", serializeSessions(sessions))
        }

        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(json.toString(2).toByteArray())
        } ?: throw IllegalStateException("Failed to open output stream")
    }

    suspend fun import(context: Context, uri: Uri): BackupData = withContext(Dispatchers.IO) {
        val jsonString = context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader().readText()
        } ?: throw IllegalStateException("Failed to read backup file")

        val json = JSONObject(jsonString)
        val version = json.optInt("version", 0)
        if (version < 1) throw IllegalArgumentException("Invalid backup file")

        BackupData(
            profiles = parseProfiles(json.getJSONArray("profiles")),
            nfcTags = parseNfcTags(json.getJSONArray("nfcTags")),
            sessions = parseSessions(json.optJSONArray("sessions") ?: JSONArray()),
        )
    }

    // --- Serialization ---

    private fun serializeProfiles(profiles: List<Profile>): JSONArray {
        return JSONArray().apply {
            profiles.forEach { profile ->
                put(JSONObject().apply {
                    put("id", profile.id)
                    put("name", profile.name)
                    put("blockedPackages", JSONArray(profile.blockedPackages))
                    put("type", profile.type)
                    put("isDefault", profile.isDefault)
                    if (profile.durationMs != null) put("durationMs", profile.durationMs)
                })
            }
        }
    }

    private fun serializeNfcTags(tags: List<NfcTag>): JSONArray {
        return JSONArray().apply {
            tags.forEach { tag ->
                put(JSONObject().apply {
                    put("uid", tag.uid)
                    put("name", tag.name)
                    put("createdAt", tag.createdAt)
                    if (tag.profileId != null) put("profileId", tag.profileId)
                })
            }
        }
    }

    private fun serializeSessions(sessions: List<Session>): JSONArray {
        return JSONArray().apply {
            sessions.forEach { session ->
                put(JSONObject().apply {
                    put("profileId", session.profileId)
                    put("startTime", session.startTime)
                    if (session.endTime != null) put("endTime", session.endTime)
                    if (session.endReason != null) put("endReason", session.endReason)
                })
            }
        }
    }

    // --- Parsing ---

    private fun parseProfiles(array: JSONArray): List<Profile> {
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            val packages = obj.getJSONArray("blockedPackages")
            Profile(
                id = obj.optLong("id", 0L),
                name = obj.getString("name"),
                blockedPackages = (0 until packages.length()).map { packages.getString(it) },
                type = obj.optString("type", "standard"),
                isDefault = obj.optBoolean("isDefault", false),
                durationMs = if (obj.has("durationMs")) obj.getLong("durationMs") else null,
            )
        }
    }

    private fun parseNfcTags(array: JSONArray): List<NfcTag> {
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            NfcTag(
                uid = obj.getString("uid"),
                name = obj.getString("name"),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                profileId = if (obj.has("profileId")) obj.getLong("profileId") else null,
            )
        }
    }

    private fun parseSessions(array: JSONArray): List<Session> {
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            Session(
                profileId = obj.getLong("profileId"),
                startTime = obj.getLong("startTime"),
                endTime = if (obj.has("endTime")) obj.getLong("endTime") else null,
                endReason = if (obj.has("endReason")) obj.getString("endReason") else null,
            )
        }
    }
}
