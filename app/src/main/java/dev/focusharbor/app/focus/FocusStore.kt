package dev.focusharbor.app.focus

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataMigration
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
    val requireExitText: Boolean = false,
    val isActive: Boolean = false,
    val startedAtMillis: Long = 0,
)

enum class SessionMutation {
    STARTED,
    ENDED,
    ALREADY_ACTIVE,
    NOT_ACTIVE,
    NO_ALLOWED_APPS,
    APPS_UNAVAILABLE,
    EXIT_TEXT_TOO_SHORT,
}

class FocusStore(private val dataStore: DataStore<Preferences>) {
    val sessions: Flow<FocusSession> = dataStore.data.map { it.toSession() }

    suspend fun startSession(
        allowedPackages: Set<String>,
        requireExitText: Boolean,
        availablePackages: Set<String>,
        nowMillis: Long,
    ): SessionMutation {
        var result = SessionMutation.ALREADY_ACTIVE
        dataStore.edit { preferences ->
            when {
                preferences[ACTIVE] == true -> result = SessionMutation.ALREADY_ACTIVE
                allowedPackages.isEmpty() -> result = SessionMutation.NO_ALLOWED_APPS
                !availablePackages.containsAll(allowedPackages) ->
                    result = SessionMutation.APPS_UNAVAILABLE
                else -> {
                    preferences[ALLOWED] = allowedPackages.toSet()
                    preferences[REQUIRE_EXIT_TEXT] = requireExitText
                    preferences[STARTED_AT] = nowMillis
                    preferences[ACTIVE] = true
                    result = SessionMutation.STARTED
                }
            }
        }
        return result
    }

    suspend fun endSession(enteredText: String): SessionMutation {
        var result = SessionMutation.NOT_ACTIVE
        dataStore.edit { preferences ->
            when {
                preferences[ACTIVE] != true -> result = SessionMutation.NOT_ACTIVE
                preferences[REQUIRE_EXIT_TEXT] == true && !ExitText.isValid(enteredText) ->
                    result = SessionMutation.EXIT_TEXT_TOO_SHORT
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
        requireExitText = this[REQUIRE_EXIT_TEXT] ?: false,
        isActive = this[ACTIVE] ?: false,
        startedAtMillis = this[STARTED_AT] ?: 0,
    )

    companion object {
        private val ALLOWED = stringSetPreferencesKey("allowed_packages")
        private val REQUIRE_EXIT_TEXT = booleanPreferencesKey("require_exit_text")
        private val LEGACY_PHRASE = stringPreferencesKey("exit_phrase")
        private val ACTIVE = booleanPreferencesKey("active")
        private val STARTED_AT = longPreferencesKey("started_at_millis")

        val exitPolicyMigration: DataMigration<Preferences> = object : DataMigration<Preferences> {
            override suspend fun shouldMigrate(currentData: Preferences): Boolean =
                currentData.contains(LEGACY_PHRASE) ||
                    (currentData[ACTIVE] == true && !currentData.contains(REQUIRE_EXIT_TEXT))

            override suspend fun migrate(currentData: Preferences): Preferences {
                val updated = currentData.toMutablePreferences()
                if (!updated.contains(REQUIRE_EXIT_TEXT)) {
                    updated[REQUIRE_EXIT_TEXT] = updated[ACTIVE] == true
                }
                updated.remove(LEGACY_PHRASE)
                return updated.toPreferences()
            }

            override suspend fun cleanUp() = Unit
        }
    }
}
