package dev.focusharbor.app.focus

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class FocusSession(
    val allowedPackages: Set<String> = emptySet(),
    val exitPhrase: String = "",
    val isActive: Boolean = false,
    val startedAtMillis: Long = 0,
)

enum class SessionMutation {
    STARTED,
    ENDED,
    ALREADY_ACTIVE,
    NOT_ACTIVE,
    INVALID_PHRASE,
    NO_ALLOWED_APPS,
    APPS_UNAVAILABLE,
    INCORRECT_PHRASE,
}

class FocusStore(private val dataStore: DataStore<Preferences>) {
    val sessions: Flow<FocusSession> = dataStore.data.map { it.toSession() }

    suspend fun startSession(
        allowedPackages: Set<String>,
        phrase: String,
        availablePackages: Set<String>,
        nowMillis: Long,
    ): SessionMutation {
        var result = SessionMutation.ALREADY_ACTIVE
        dataStore.edit { preferences ->
            when {
                preferences[ACTIVE] == true -> result = SessionMutation.ALREADY_ACTIVE
                !ExitPhrase.isValid(phrase) -> result = SessionMutation.INVALID_PHRASE
                allowedPackages.isEmpty() -> result = SessionMutation.NO_ALLOWED_APPS
                !availablePackages.containsAll(allowedPackages) ->
                    result = SessionMutation.APPS_UNAVAILABLE
                else -> {
                    preferences[ALLOWED] = allowedPackages.toSet()
                    preferences[PHRASE] = ExitPhrase.normalize(phrase)
                    preferences[STARTED_AT] = nowMillis
                    preferences[ACTIVE] = true
                    result = SessionMutation.STARTED
                }
            }
        }
        return result
    }

    suspend fun endSession(enteredPhrase: String): SessionMutation {
        var result = SessionMutation.NOT_ACTIVE
        dataStore.edit { preferences ->
            when {
                preferences[ACTIVE] != true -> result = SessionMutation.NOT_ACTIVE
                !ExitPhrase.matches(preferences[PHRASE].orEmpty(), enteredPhrase) ->
                    result = SessionMutation.INCORRECT_PHRASE
                else -> {
                    preferences[ACTIVE] = false
                    preferences.remove(STARTED_AT)
                    result = SessionMutation.ENDED
                }
            }
        }
        return result
    }

    private fun Preferences.toSession() = FocusSession(
        allowedPackages = this[ALLOWED].orEmpty().toSet(),
        exitPhrase = this[PHRASE].orEmpty(),
        isActive = this[ACTIVE] ?: false,
        startedAtMillis = this[STARTED_AT] ?: 0,
    )

    private companion object {
        val ALLOWED = stringSetPreferencesKey("allowed_packages")
        val PHRASE = stringPreferencesKey("exit_phrase")
        val ACTIVE = booleanPreferencesKey("active")
        val STARTED_AT = longPreferencesKey("started_at_millis")
    }
}
