package dev.focusharbor.app.focus

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
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

    private val phrase = "I choose my attention and return to what matters now"
    private val apps = setOf("example.phone", "example.notes")

    private fun dataStore(scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(temporaryFolder.root, "session.preferences_pb") },
        )

    @Test
    fun startsWithAnAtomicAllowlistPhraseAndTimestamp() = runTest {
        val store = FocusStore(dataStore(backgroundScope))
        assertEquals(
            SessionMutation.STARTED,
            store.startSession(setOf("example.notes"), "  $phrase  ", apps, 123),
        )
        val session = store.sessions.first()
        assertTrue(session.isActive)
        assertEquals(setOf("example.notes"), session.allowedPackages)
        assertEquals(phrase, session.exitPhrase)
        assertEquals(123L, session.startedAtMillis)
    }

    @Test
    fun activeSessionCannotBeReconfiguredByStartingAgain() = runTest {
        val store = FocusStore(dataStore(backgroundScope))
        store.startSession(setOf("example.notes"), phrase, apps, 123)
        val before = store.sessions.first()
        assertEquals(
            SessionMutation.ALREADY_ACTIVE,
            store.startSession(setOf("example.phone"), "$phrase please", apps, 999),
        )
        assertEquals(before, store.sessions.first())
    }

    @Test
    fun incorrectTextNeverEndsAnActiveSession() = runTest {
        val store = FocusStore(dataStore(backgroundScope))
        store.startSession(apps, phrase, apps, 123)
        val before = store.sessions.first()
        for (incorrect in listOf("", phrase.lowercase(), "$phrase.", "$phrase please")) {
            assertEquals(SessionMutation.INCORRECT_PHRASE, store.endSession(incorrect))
            assertEquals(before, store.sessions.first())
        }
    }

    @Test
    fun matchingTextEndsFocusButPreservesTheNextSessionConfiguration() = runTest {
        val store = FocusStore(dataStore(backgroundScope))
        store.startSession(apps, phrase, apps, 123)
        assertEquals(SessionMutation.ENDED, store.endSession(phrase.replace(" ", "\n ")))
        val session = store.sessions.first()
        assertFalse(session.isActive)
        assertEquals(0L, session.startedAtMillis)
        assertEquals(apps, session.allowedPackages)
        assertEquals(phrase, session.exitPhrase)
        assertEquals(SessionMutation.NOT_ACTIVE, store.endSession(phrase))
    }

    @Test
    fun invalidSetupNeverPersistsAnActiveSession() = runTest {
        val store = FocusStore(dataStore(backgroundScope))
        assertEquals(
            SessionMutation.INVALID_PHRASE,
            store.startSession(apps, "too few words", apps, 1),
        )
        assertEquals(
            SessionMutation.NO_ALLOWED_APPS,
            store.startSession(emptySet(), phrase, apps, 1),
        )
        assertEquals(
            SessionMutation.APPS_UNAVAILABLE,
            store.startSession(setOf("example.uninstalled"), phrase, apps, 1),
        )
        assertEquals(FocusSession(), store.sessions.first())
    }

    @Test
    fun repositoryRecreationReadsThePersistedSession() = runTest {
        val preferences = dataStore(backgroundScope)
        FocusStore(preferences).startSession(apps, phrase, apps, 123)
        val recreated = FocusStore(preferences).sessions.first()
        assertTrue(recreated.isActive)
        assertEquals(phrase, recreated.exitPhrase)
        assertEquals(apps, recreated.allowedPackages)
    }

    @Test
    fun simultaneousStartsCannotOverwriteTheWinningSession() = runTest {
        val store = FocusStore(dataStore(backgroundScope))
        val results = listOf(
            async { store.startSession(setOf("example.notes"), phrase, apps, 123) },
            async { store.startSession(setOf("example.phone"), phrase, apps, 999) },
        ).awaitAll()
        assertEquals(1, results.count { it == SessionMutation.STARTED })
        assertEquals(1, results.count { it == SessionMutation.ALREADY_ACTIVE })
        val session = store.sessions.first()
        assertTrue(session.isActive)
        assertTrue(
            (session.allowedPackages == setOf("example.notes") && session.startedAtMillis == 123L) ||
                (session.allowedPackages == setOf("example.phone") && session.startedAtMillis == 999L),
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
            failingStore.startSession(apps, phrase, apps, 1)
            fail("A failed write must not report a started session")
        } catch (_: IOException) {
            // The caller must surface the failure; there is no success-shaped fallback.
        }
    }
}
