package ir.hawraa.quran.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * The persisted session: the credential, the account it belongs to, and the cached account itself.
 *
 * One class owns all three because they are one fact and must never disagree — a token with no
 * account is a launch check that cannot be answered offline, and an account with no token is a
 * screen that claims a reader is signed in while every call fails. [AuthRepository] writes them;
 * everything else only reads [token].
 *
 * [account] is an **opaque JSON blob**: this class stores it, it does not interpret it. The shape
 * belongs to the wire contract, and putting that knowledge here would mean two places to change
 * when the server adds a field.
 *
 * The token is a cache of a server-side session, so it may be dropped at any time. Nothing here
 * ever blocks the UI — [token] returns null when signed out, and the sync engine reads that as
 * "not yet" rather than as a failure.
 *
 * (This class used to mint a `deviceId` and register an anonymous user with it. Accounts replaced
 * that; the field is gone from the client, and the backend keeps it only for legacy rows.)
 */
@Singleton
class IdentityRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val tokenKey = stringPreferencesKey("identity_token")
    private val userIdKey = stringPreferencesKey("identity_user_id")
    private val accountKey = stringPreferencesKey("auth_account")

    suspend fun token(): String? = dataStore.data.first()[tokenKey]

    suspend fun userId(): String? = dataStore.data.first()[userIdKey]

    /** The cached account, as the JSON [AuthRepository] last wrote. */
    suspend fun account(): String? = dataStore.data.first()[accountKey]

    suspend fun storeSession(token: String, userId: String?) {
        dataStore.edit { prefs ->
            prefs[tokenKey] = token
            if (userId != null) prefs[userIdKey] = userId
        }
    }

    suspend fun storeAccount(json: String) {
        dataStore.edit { it[accountKey] = json }
    }

    /** Called when the session ends, so the next sync sends nothing. */
    suspend fun clearSession() {
        dataStore.edit { prefs ->
            prefs.remove(tokenKey)
            prefs.remove(userIdKey)
            prefs.remove(accountKey)
        }
    }
}
