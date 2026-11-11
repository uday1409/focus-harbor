package dev.focusharbor.app.focus

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesFileSerializer
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class FocusStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val text = "Any ten words I write should let me leave now"
    private val apps = setOf("example.phone", "example.notes")

    private fun dataStore(scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = scope,
            migrations = listOf(FocusStore.exitPolicyMigration),
            produceFile = { File(temporaryFolder.root, "session.preferences_pb") },
        )

    @Test
    fun startsWithAnAtomicAllowlistExitChoiceAndTimestamp() = runTest {
        val store = FocusStore(dataStore(backgroundScope))
        assertFalse(store.sessions.first().requireExitText)
        assertEquals(
            SessionMutation.STARTED,
            store.startSession(setOf("example.notes"), true, apps, FocusTime(123)),
        )
        val session = store.sessions.first()
        assertTrue(session.isActive)
        assertEquals(setOf("example.notes"), session.allowedPackages)
        assertTrue(session.requireExitText)
        assertEquals(123L, session.startedAtMillis)
    }

    @Test
    fun activeSessionCannotBeReconfiguredByStartingAgain() = runTest {
        val store = FocusStore(dataStore(backgroundScope))
        store.startSession(setOf("example.notes"), true, apps, FocusTime(123))
        val before = store.sessions.first()
        assertEquals(
            SessionMutation.ALREADY_ACTIVE,
            store.startSession(setOf("example.phone"), false, apps, FocusTime(999)),
        )
        assertEquals(before, store.sessions.first())
    }

    @Test
    fun shortTextNeverEndsAnOptedInSession() = runTest {
        val store = FocusStore(dataStore(backgroundScope))
        store.startSession(apps, true, apps, FocusTime(123))
        val before = store.sessions.first()
        for (short in listOf("", "one two three four five six seven eight nine", "!!! ".repeat(10))) {
            assertEquals(SessionMutation.EXIT_TEXT_TOO_SHORT, store.endSession(short))
            assertEquals(before, store.sessions.first())
        }
    }

    @Test
    fun anyTenOrMoreWordsEndFocusAndPreserveTheNextSessionChoice() = runTest {
        val store = FocusStore(dataStore(backgroundScope))
        store.startSession(apps, true, apps, FocusTime(123))
        assertEquals(SessionMutation.ENDED, store.endSession(text))
        val session = store.sessions.first()
        assertFalse(session.isActive)
        assertEquals(0L, session.startedAtMillis)
        assertEquals(apps, session.allowedPackages)
        assertTrue(session.requireExitText)
        assertEquals(SessionMutation.NOT_ACTIVE, store.endSession(text))
        store.startSession(apps, true, apps, FocusTime(456))
        assertEquals(
            SessionMutation.ENDED,
            store.endSession("Different words do not need to match any preset anymore today"),
        )
    }

    @Test
    fun optingOutAllowsImmediateExitWithoutAnyText() = runTest {
        val store = FocusStore(dataStore(backgroundScope))
        store.startSession(apps, false, apps, FocusTime(123))
        assertFalse(store.sessions.first().requireExitText)
        assertEquals(SessionMutation.ENDED, store.endSession(""))
        assertFalse(store.sessions.first().isActive)
    }

    @Test
    fun invalidSetupNeverPersistsAnActiveSession() = runTest {
        val store = FocusStore(dataStore(backgroundScope))
        assertEquals(
            SessionMutation.NO_ALLOWED_APPS,
            store.startSession(emptySet(), false, apps, FocusTime(1)),
        )
        assertEquals(
            SessionMutation.APPS_UNAVAILABLE,
            store.startSession(setOf("example.uninstalled"), true, apps, FocusTime(1)),
        )
        assertEquals(FocusSession(), store.sessions.first())
    }

    @Test
    fun repositoryRecreationReadsThePersistedSession() = runTest {
        val preferences = dataStore(backgroundScope)
        FocusStore(preferences).startSession(apps, true, apps, FocusTime(123))
        val recreated = FocusStore(preferences).sessions.first()
        assertTrue(recreated.isActive)
        assertTrue(recreated.requireExitText)
        assertEquals(apps, recreated.allowedPackages)
    }

    @Test
    fun simultaneousStartsCannotOverwriteTheWinningSession() = runTest {
        val store = FocusStore(dataStore(backgroundScope))
        val results = listOf(
            async { store.startSession(setOf("example.notes"), true, apps, FocusTime(123)) },
            async { store.startSession(setOf("example.phone"), false, apps, FocusTime(999)) },
        ).awaitAll()
        assertEquals(1, results.count { it == SessionMutation.STARTED })
        assertEquals(1, results.count { it == SessionMutation.ALREADY_ACTIVE })
        val session = store.sessions.first()
        assertTrue(session.isActive)
        assertTrue(
            (session.allowedPackages == setOf("example.notes") && session.requireExitText &&
                session.startedAtMillis == 123L) ||
                (session.allowedPackages == setOf("example.phone") && !session.requireExitText &&
                    session.startedAtMillis == 999L),
        )
    }

    @Test
    fun storageErrorsPropagateInsteadOfPretendingFocusWasSaved() = runTest {
        val failingStore = FocusStore(
            object : DataStore<Preferences> {
                override val data: Flow<Preferences> = flow { throw IOException("disk unavailable") }

                override suspend fun updateData(
                    transform: suspend (t: Preferences) -> Preferences,
                ): Preferences = throw IOException("disk unavailable")
            },
        )
        try {
            failingStore.startSession(apps, true, apps, FocusTime(1))
            fail("A failed write must not report a started session")
        } catch (_: IOException) {
            // The caller must surface the failure; there is no success-shaped fallback.
        }
    }

    @Test
    fun upgradingAnActiveLegacySessionKeepsTheGateButDeletesTheOldText() = runTest {
        val legacyText = stringPreferencesKey("exit_phrase")
        val legacy = mutablePreferencesOf(
            stringSetPreferencesKey("allowed_packages") to apps,
            legacyText to "old custom text that is no longer needed",
            booleanPreferencesKey("active") to true,
            longPreferencesKey("started_at_millis") to 123L,
        )
        File(temporaryFolder.root, "session.preferences_pb").outputStream().use {
            PreferencesFileSerializer.writeTo(legacy, it)
        }
        val preferences = dataStore(backgroundScope)
        val store = FocusStore(preferences)
        val session = store.sessions.first()
        assertTrue(session.isActive)
        assertTrue(session.requireExitText)
        assertEquals(apps, session.allowedPackages)
        assertEquals(123L, session.startedAtMillis)
        assertFalse(preferences.data.first().contains(legacyText))
        assertEquals(SessionMutation.ENDED, store.endSession(text))
    }

    @Test
    fun upgradingIdleLegacySettingsDefaultsTheOptionalGateOff() = runTest {
        val legacyText = stringPreferencesKey("exit_phrase")
        val legacy = mutablePreferencesOf(legacyText to "obsolete text")
        File(temporaryFolder.root, "session.preferences_pb").outputStream().use {
            PreferencesFileSerializer.writeTo(legacy, it)
        }
        val preferences = dataStore(backgroundScope)
        val session = FocusStore(preferences).sessions.first()
        assertFalse(session.isActive)
        assertFalse(session.requireExitText)
        assertFalse(preferences.data.first().contains(legacyText))
    }

    @Test
    fun migrationPreservesAnExplicitNewExitChoiceAndIsIdempotent() = runTest {
        val requireText = booleanPreferencesKey("require_exit_text")
        val legacyText = stringPreferencesKey("exit_phrase")
        val mixedSettings = mutablePreferencesOf(
            booleanPreferencesKey("active") to true,
            requireText to false,
            legacyText to "obsolete text",
        )
        val migrated = FocusStore.exitPolicyMigration.migrate(mixedSettings)
        assertEquals(false, migrated[requireText])
        assertFalse(migrated.contains(legacyText))
        assertFalse(FocusStore.exitPolicyMigration.shouldMigrate(migrated))
    }
}
