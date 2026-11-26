package com.example.ridesharingapp

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists JWT and current user id for authenticated API calls.
 */
object SessionManager {
    private const val PREFS = "rideshare_session"
    private const val KEY_TOKEN = "access_token"
    private const val KEY_USER_ID = "user_id"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun saveSession(accessToken: String, userId: String) {
        prefs.edit()
            .putString(KEY_TOKEN, accessToken)
            .putString(KEY_USER_ID, userId)
            .apply()
    }

    fun accessToken(): String? = prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }

    fun userId(): String? = prefs.getString(KEY_USER_ID, null)?.takeIf { it.isNotBlank() }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun isLoggedIn(): Boolean = accessToken() != null
}
