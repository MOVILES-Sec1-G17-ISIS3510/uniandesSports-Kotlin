package com.uniandes.sport.data.local

import android.content.Context
import org.json.JSONObject
import java.util.UUID

private const val PREFS_NAME = "pending_onboarding_prefs"
private const val KEY_PENDING_ONBOARDING = "pending_onboarding"

data class PendingOnboardingPayload(
    val localId: String = UUID.randomUUID().toString(),
    val fullName: String,
    val email: String,
    val password: String,
    val program: String,
    val semester: String,
    val mainSport: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)

object PendingOnboardingStore {

    fun get(context: Context): PendingOnboardingPayload? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_PENDING_ONBOARDING, null) ?: return null
        return try {
            JSONObject(raw).toPendingOnboardingPayload()
        } catch (_: Exception) {
            null
        }
    }

    fun hasPending(context: Context): Boolean = get(context) != null

    fun save(context: Context, payload: PendingOnboardingPayload) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PENDING_ONBOARDING, payload.toJson().toString())
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PENDING_ONBOARDING)
            .apply()
    }

    private fun PendingOnboardingPayload.toJson(): JSONObject {
        return JSONObject().apply {
            put("localId", localId)
            put("fullName", fullName)
            put("email", email)
            put("password", password)
            put("program", program)
            put("semester", semester)
            put("mainSport", mainSport)
            put("createdAtMillis", createdAtMillis)
        }
    }

    private fun JSONObject.toPendingOnboardingPayload(): PendingOnboardingPayload {
        return PendingOnboardingPayload(
            localId = optString("localId", UUID.randomUUID().toString()),
            fullName = getString("fullName"),
            email = getString("email"),
            password = getString("password"),
            program = getString("program"),
            semester = getString("semester"),
            mainSport = getString("mainSport"),
            createdAtMillis = optLong("createdAtMillis", System.currentTimeMillis())
        )
    }
}
