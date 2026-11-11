package dev.focusharbor.app.focus

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataMigration
import androidx.datastore.core.CorruptionException
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale
import java.util.UUID

data class FocusSession(
    val allowedPackages: Set<String> = emptySet(),
    val requireExitText: Boolean = false,
    val isActive: Boolean = false,
    val startedAtMillis: Long = 0,
    val presetName: String? = null,
    val intention: String = "",
    val deadlineEpochMillis: Long? = null,
    val deadlineElapsedMillis: Long? = null,
    val deadlineBootCount: Int? = null,
)

data class FocusPreset(val id: String, val name: String, val allowedPackages: Set<String> = emptySet())

data class FocusState(
    val session: FocusSession,
    val presets: List<FocusPreset> = emptyList(),
    val selectedPresetId: String? = null,
    val lastDurationMinutes: Int? = null,
)

data class FocusPlan(
    val presetId: String? = null,
    val intention: String = "",
    val durationMinutes: Int? = null,
)

enum class SessionMutation {
    STARTED,
    ENDED,
    ALREADY_ACTIVE,
    NOT_ACTIVE,
    NO_ALLOWED_APPS,
    APPS_UNAVAILABLE,
    EXIT_TEXT_TOO_SHORT,
    CONFIGURATION_SAVED,
    PRESET_NOT_FOUND,
    INVALID_PRESET_NAME,
    DUPLICATE_PRESET_NAME,
    LAST_PRESET,
    INVALID_INTENTION,
    INVALID_DURATION,
    EXTENDED,
    TIMER_NOT_FINISHED,
}

class FocusStore(private val dataStore: DataStore<Preferences>) {
    val states: Flow<FocusState> = dataStore.data.map { preferences ->
        val ids = preferences[PRESET_IDS]
        val presets = ids.orEmpty().map { id ->
            FocusPreset(
                id,
                preferences[presetNameKey(id)] ?: throw CorruptionException("A saved preset has no name."),
                preferences[presetAppsKey(id)].orEmpty().toSet(),
            )
        }.sortedBy { it.name.lowercase(Locale.ROOT) }
        val selected = preferences[SELECTED_PRESET]
        if (ids != null && (ids.isEmpty() || selected !in ids)) {
            throw CorruptionException("The selected saved preset is unavailable.")
        }
        FocusState(preferences.toSession(), presets, selected, preferences[LAST_DURATION])
    }
    val sessions: Flow<FocusSession> = states.map { it.session }

    suspend fun initializePresets(defaults: List<FocusPreset>) {
        require(defaults.isNotEmpty() && defaults.map { it.id }.distinct().size == defaults.size)
        dataStore.edit { preferences ->
            if (preferences[PRESET_IDS] == null) {
                preferences[PRESET_IDS] = defaults.map { it.id }.toSet()
                preferences[SELECTED_PRESET] = defaults.first().id
                defaults.forEachIndexed { index, preset ->
                    preferences[presetNameKey(preset.id)] = preset.name
                    preferences[presetAppsKey(preset.id)] =
                        if (index == 0) preferences[ALLOWED].orEmpty() else preset.allowedPackages
                }
            }
        }
    }

    suspend fun saveSelection(id: String, apps: Set<String>): SessionMutation = configure { preferences ->
        if (id !in preferences[PRESET_IDS].orEmpty()) {
            SessionMutation.PRESET_NOT_FOUND
        } else {
            preferences[presetAppsKey(id)] = apps.toSet()
            SessionMutation.CONFIGURATION_SAVED
        }
    }

    suspend fun selectPreset(id: String, currentApps: Set<String>): SessionMutation = configure { preferences ->
        val current = preferences[SELECTED_PRESET]
        val ids = preferences[PRESET_IDS].orEmpty()
        if (id !in ids || current !in ids) {
            SessionMutation.PRESET_NOT_FOUND
        } else {
            preferences[presetAppsKey(requireNotNull(current))] = currentApps.toSet()
            preferences[SELECTED_PRESET] = id
            SessionMutation.CONFIGURATION_SAVED
        }
    }

    suspend fun createPreset(name: String, apps: Set<String>): SessionMutation = configure { preferences ->
        val cleanName = name.trim()
        when {
            !validPresetName(cleanName) -> SessionMutation.INVALID_PRESET_NAME
            nameExists(preferences, cleanName) -> SessionMutation.DUPLICATE_PRESET_NAME
            else -> {
                preferences[SELECTED_PRESET]?.let { preferences[presetAppsKey(it)] = apps.toSet() }
                val id = UUID.randomUUID().toString()
                preferences[PRESET_IDS] = preferences[PRESET_IDS].orEmpty() + id
                preferences[presetNameKey(id)] = cleanName
                preferences[presetAppsKey(id)] = apps.toSet()
                preferences[SELECTED_PRESET] = id
                SessionMutation.CONFIGURATION_SAVED
            }
        }
    }

    suspend fun renamePreset(id: String, name: String): SessionMutation = configure { preferences ->
        val cleanName = name.trim()
        when {
            id !in preferences[PRESET_IDS].orEmpty() -> SessionMutation.PRESET_NOT_FOUND
            !validPresetName(cleanName) -> SessionMutation.INVALID_PRESET_NAME
            nameExists(preferences, cleanName, id) -> SessionMutation.DUPLICATE_PRESET_NAME
            else -> {
                preferences[presetNameKey(id)] = cleanName
                SessionMutation.CONFIGURATION_SAVED
            }
        }
    }

    suspend fun deletePreset(id: String): SessionMutation = configure { preferences ->
        val ids = preferences[PRESET_IDS].orEmpty()
        when {
            id !in ids -> SessionMutation.PRESET_NOT_FOUND
            ids.size == 1 -> SessionMutation.LAST_PRESET
            else -> {
                val remaining = ids - id
                preferences[PRESET_IDS] = remaining
                preferences.remove(presetNameKey(id))
                preferences.remove(presetAppsKey(id))
                if (preferences[SELECTED_PRESET] == id) {
                    preferences[SELECTED_PRESET] = remaining.sorted().first()
                }
                SessionMutation.CONFIGURATION_SAVED
            }
        }
    }

    private suspend fun configure(change: (MutablePreferences) -> SessionMutation): SessionMutation {
        var result = SessionMutation.ALREADY_ACTIVE
        dataStore.edit { preferences ->
            result = if (preferences[ACTIVE] == true) SessionMutation.ALREADY_ACTIVE else change(preferences)
        }
        return result
    }

    suspend fun startSession(
        allowedPackages: Set<String>,
        requireExitText: Boolean,
        availablePackages: Set<String>,
        now: FocusTime,
        plan: FocusPlan = FocusPlan(),
    ): SessionMutation {
        var result = SessionMutation.ALREADY_ACTIVE
        dataStore.edit { preferences ->
            when {
                preferences[ACTIVE] == true -> result = SessionMutation.ALREADY_ACTIVE
                plan.intention.trim().length > MAX_INTENTION_LENGTH -> result = SessionMutation.INVALID_INTENTION
                !validDuration(plan.durationMinutes) -> result = SessionMutation.INVALID_DURATION
                plan.presetId != null && plan.presetId !in preferences[PRESET_IDS].orEmpty() ->
                    result = SessionMutation.PRESET_NOT_FOUND
                allowedPackages.isEmpty() -> result = SessionMutation.NO_ALLOWED_APPS
                !availablePackages.containsAll(allowedPackages) ->
                    result = SessionMutation.APPS_UNAVAILABLE
                else -> {
                    preferences[ALLOWED] = allowedPackages.toSet()
                    preferences[REQUIRE_EXIT_TEXT] = requireExitText
                    preferences[STARTED_AT] = now.epochMillis
                    preferences[INTENTION] = plan.intention.trim()
                    plan.presetId?.let { id ->
                        preferences[presetAppsKey(id)] = allowedPackages.toSet()
                        preferences[SELECTED_PRESET] = id
                        preferences[SESSION_PRESET_NAME] = requireNotNull(preferences[presetNameKey(id)])
                    } ?: preferences.remove(SESSION_PRESET_NAME)
                    plan.durationMinutes?.let { preferences[LAST_DURATION] = it }
                        ?: preferences.remove(LAST_DURATION)
                    setDeadline(preferences, plan.durationMinutes, now)
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
                    preferences.remove(INTENTION)
                    preferences.remove(SESSION_PRESET_NAME)
                    setDeadline(preferences, null, FocusTime(0))
                    result = SessionMutation.ENDED
                }
            }
        }
        return result
    }

    suspend fun keepGoing(durationMinutes: Int?, now: FocusTime): SessionMutation {
        var result = SessionMutation.NOT_ACTIVE
        dataStore.edit { preferences ->
            val remaining = preferences.toSession().remainingMillis(now)
            when {
                preferences[ACTIVE] != true -> result = SessionMutation.NOT_ACTIVE
                !validDuration(durationMinutes) -> result = SessionMutation.INVALID_DURATION
                remaining == null || remaining > 0 -> result = SessionMutation.TIMER_NOT_FINISHED
                else -> {
                    setDeadline(preferences, durationMinutes, now)
                    result = SessionMutation.EXTENDED
                }
            }
        }
        return result
    }

    private fun setDeadline(preferences: MutablePreferences, minutes: Int?, now: FocusTime) {
        if (minutes == null) {
            preferences.remove(DEADLINE_EPOCH)
            preferences.remove(DEADLINE_ELAPSED)
            preferences.remove(DEADLINE_BOOT)
        } else {
            val duration = minutes * 60_000L
            preferences[DEADLINE_EPOCH] = now.epochMillis + duration
            if (now.bootCount != null) {
                preferences[DEADLINE_ELAPSED] = now.elapsedRealtimeMillis + duration
                preferences[DEADLINE_BOOT] = now.bootCount
            } else {
                preferences.remove(DEADLINE_ELAPSED)
                preferences.remove(DEADLINE_BOOT)
            }
        }
    }

    private fun nameExists(preferences: Preferences, name: String, except: String? = null): Boolean =
        preferences[PRESET_IDS].orEmpty().any {
            it != except && preferences[presetNameKey(it)]?.equals(name, ignoreCase = true) == true
        }

    private fun Preferences.toSession() = FocusSession(
        allowedPackages = this[ALLOWED].orEmpty().toSet(),
        requireExitText = this[REQUIRE_EXIT_TEXT] ?: false,
        isActive = this[ACTIVE] ?: false,
        startedAtMillis = this[STARTED_AT] ?: 0,
        presetName = this[SESSION_PRESET_NAME],
        intention = this[INTENTION].orEmpty(),
        deadlineEpochMillis = this[DEADLINE_EPOCH],
        deadlineElapsedMillis = this[DEADLINE_ELAPSED],
        deadlineBootCount = this[DEADLINE_BOOT],
    )

    companion object {
        const val MAX_INTENTION_LENGTH = 160
        const val MAX_PRESET_NAME_LENGTH = 40
        const val MAX_DURATION_MINUTES = 1_440

        fun validPresetName(name: String): Boolean =
            name.trim().isNotEmpty() && name.trim().length <= MAX_PRESET_NAME_LENGTH &&
                name.none(Char::isISOControl)

        fun validDuration(minutes: Int?): Boolean = minutes == null || minutes in 1..MAX_DURATION_MINUTES

        private val ALLOWED = stringSetPreferencesKey("allowed_packages")
        private val REQUIRE_EXIT_TEXT = booleanPreferencesKey("require_exit_text")
        private val LEGACY_PHRASE = stringPreferencesKey("exit_phrase")
        private val ACTIVE = booleanPreferencesKey("active")
        private val STARTED_AT = longPreferencesKey("started_at_millis")
        private val PRESET_IDS = stringSetPreferencesKey("preset_ids")
        private val SELECTED_PRESET = stringPreferencesKey("selected_preset_id")
        private val SESSION_PRESET_NAME = stringPreferencesKey("session_preset_name")
        private val INTENTION = stringPreferencesKey("session_intention")
        private val LAST_DURATION = intPreferencesKey("last_duration_minutes")
        private val DEADLINE_EPOCH = longPreferencesKey("deadline_epoch_millis")
        private val DEADLINE_ELAPSED = longPreferencesKey("deadline_elapsed_millis")
        private val DEADLINE_BOOT = intPreferencesKey("deadline_boot_count")
        private fun presetNameKey(id: String) = stringPreferencesKey("preset_name_$id")
        private fun presetAppsKey(id: String) = stringSetPreferencesKey("preset_apps_$id")

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
