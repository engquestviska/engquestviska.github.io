package com.engquest.teacher.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.security.KeyStore

data class Creds(val username: String, val password: String)

/**
 * The teacher's login, kept across app restarts. [user] is null when logged out or expired.
 * [expired] means the saved password stopped working: the username is kept so the login
 * screen can be pre-filled, and unsent changes wait for the next login.
 */
class Session(private val prefs: SharedPreferences, private val api: Api) {
    private val _user = MutableStateFlow(load())
    val user: StateFlow<Creds?> = _user.asStateFlow()

    private val _expired = MutableStateFlow(prefs.getBoolean(KEY_EXPIRED, false))
    val expired: StateFlow<Boolean> = _expired.asStateFlow()

    /**
     * True when the server accepts the username and password; only then are they saved.
     * Throws [ApiError] (e.g. [ApiError.Offline]) when the server could not be asked.
     */
    suspend fun login(username: String, password: String): Boolean {
        // POST keeps the password out of the URL.
        val reply = api.write("checkLogin", mapOf("username" to username, "password" to password))
        if ((reply["ok"] as? JsonPrimitive)?.booleanOrNull != true) return false
        prefs.edit()
            .putString(KEY_USERNAME, username)
            .putString(KEY_PASSWORD, password)
            .putBoolean(KEY_EXPIRED, false)
            .apply()
        _user.value = Creds(username, password)
        _expired.value = false
        return true
    }

    fun logout() {
        prefs.edit().remove(KEY_USERNAME).remove(KEY_PASSWORD).remove(KEY_EXPIRED).apply()
        _user.value = null
        _expired.value = false
    }

    fun creds(): Creds? = _user.value

    /**
     * True unless the teacher logged out. An expired password still counts: changes made then
     * wait for the next login. After a log out, closing screens must not queue anything.
     */
    fun hasLogin(): Boolean = _user.value != null || _expired.value

    /** Last username used, kept after [markExpired] to pre-fill the login screen. */
    fun savedUsername(): String? = prefs.getString(KEY_USERNAME, null)

    fun markExpired() {
        prefs.edit().remove(KEY_PASSWORD).putBoolean(KEY_EXPIRED, true).apply()
        _user.value = null
        _expired.value = true
    }

    private fun load(): Creds? {
        val u = prefs.getString(KEY_USERNAME, null) ?: return null
        val p = prefs.getString(KEY_PASSWORD, null) ?: return null
        return Creds(u, p)
    }

    companion object {
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_EXPIRED = "expired"

        private const val FILE = "session"

        /**
         * Opens the store with [create]; if that throws (the phone's key store or the file got
         * damaged), [reset] wipes it and it is created once more, so the teacher just logs in again.
         * If it still cannot be opened, the login is kept in memory only — never a crash.
         */
        fun openPrefs(create: () -> SharedPreferences, reset: () -> Unit): SharedPreferences =
            try {
                create()
            } catch (e: Exception) {
                runCatching(reset)
                try {
                    create()
                } catch (stillBroken: Exception) {
                    MemoryPrefs()
                }
            }

        /** The app's login store: [securePrefs], reset or kept in memory when it cannot be opened. */
        fun openSecurePrefs(context: Context): SharedPreferences =
            openPrefs(create = { securePrefs(context) }, reset = { resetSecureStore(context) })

        /** Deletes the encrypted file and its master key so both are made fresh. */
        private fun resetSecureStore(context: Context) {
            context.deleteSharedPreferences(FILE)
            runCatching {
                KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                    .deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            }
        }

        /** The app's real prefs: encrypted on the phone with a key from the Android Keystore. */
        fun securePrefs(context: Context): SharedPreferences = EncryptedSharedPreferences.create(
            context,
            FILE,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
}
