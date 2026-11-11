package dev.focusharbor.app.focus

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class FocusPresetsAndTimingTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val apps = setOf("example.notes", "example.phone")
    private val defaults = listOf(
        FocusPreset("work", "Work"),
        FocusPreset("study", "Study"),
        FocusPreset("sleep", "Sleep"),
        FocusPreset("family", "Time with family"),
    )
    private val now = FocusTime(1_000_000, 50_000, 2)

    private fun store(scope: CoroutineScope) = FocusStore(
        PreferenceDataStoreFactory.create(
            scope = scope,
            migrations = listOf(FocusStore.exitPolicyMigration),
            produceFile = { File(temporaryFolder.root, "session.preferences_pb") },
        ),
    )

    @Test
    fun presetInitializationPreservesLegacySelectionAndAnActiveSession() = runTest {
        val store = store(backgroundScope)
        store.startSession(apps, true, apps, now)
        val before = store.sessions.first()
        store.initializePresets(defaults)
        val state = store.states.first()
        assertEquals(before, state.session)
        assertEquals("work", state.selectedPresetId)
        assertEquals(apps, state.presets.first { it.id == "work" }.allowedPackages)
        assertTrue(state.presets.first { it.id == "study" }.allowedPackages.isEmpty())
        store.initializePresets(listOf(FocusPreset("replacement", "Replacement")))
        assertEquals(state, store.states.first())
    }

    @Test
    fun switchingPresetsSavesIndependentSelections() = runTest {
        val store = store(backgroundScope)
        store.initializePresets(defaults)
        store.saveSelection("work", setOf("example.notes"))
        store.selectPreset("study", setOf("example.notes"))
        store.saveSelection("study", setOf("example.phone"))
        store.selectPreset("work", setOf("example.phone"))
        val state = store.states.first()
        assertEquals("work", state.selectedPresetId)
        assertEquals(setOf("example.notes"), state.presets.first { it.id == "work" }.allowedPackages)
        assertEquals(setOf("example.phone"), state.presets.first { it.id == "study" }.allowedPackages)
    }

    @Test
    fun presetCreationRenameAndDeletionPreserveAtLeastOneValidChoice() = runTest {
        val store = store(backgroundScope)
        store.initializePresets(defaults)
        assertEquals(SessionMutation.INVALID_PRESET_NAME, store.createPreset(" \n ", apps))
        assertEquals(SessionMutation.DUPLICATE_PRESET_NAME, store.createPreset(" WORK ", apps))
        assertEquals(SessionMutation.CONFIGURATION_SAVED, store.createPreset(" Reading ", apps))
        val id = requireNotNull(store.states.first().selectedPresetId)
        assertEquals("Reading", store.states.first().presets.first { it.id == id }.name)
        assertEquals(SessionMutation.CONFIGURATION_SAVED, store.renamePreset(id, "Deep reading"))
        assertEquals(SessionMutation.DUPLICATE_PRESET_NAME, store.renamePreset(id, "Study"))
        assertEquals(SessionMutation.CONFIGURATION_SAVED, store.deletePreset(id))
        val afterDeletion = store.states.first()
        assertFalse(afterDeletion.presets.any { it.id == id })
        assertTrue(afterDeletion.presets.any { it.id == afterDeletion.selectedPresetId })
        for (preset in store.states.first().presets.drop(1)) store.deletePreset(preset.id)
        assertEquals(
            SessionMutation.LAST_PRESET,
            store.deletePreset(store.states.first().presets.single().id),
        )
    }

    @Test
    fun activePresetAndIntentionAreSnapshotsAndIntentionIsClearedOnExit() = runTest {
        val store = store(backgroundScope)
        store.initializePresets(defaults)
        store.startSession(apps, true, apps, now, FocusPlan("work", "Finish chapter 3", 25))
        val before = store.sessions.first()
        assertEquals("Work", before.presetName)
        assertEquals("Finish chapter 3", before.intention)
        assertEquals(25 * 60_000L, before.remainingMillis(now))
        assertEquals(SessionMutation.ALREADY_ACTIVE, store.saveSelection("work", emptySet()))
        assertEquals(SessionMutation.ALREADY_ACTIVE, store.selectPreset("study", emptySet()))
        assertEquals(SessionMutation.ALREADY_ACTIVE, store.renamePreset("work", "Changed"))
        assertEquals(SessionMutation.ALREADY_ACTIVE, store.deletePreset("work"))
        assertEquals(SessionMutation.ALREADY_ACTIVE, store.createPreset("Changed", emptySet()))
        assertEquals(before, store.sessions.first())
        store.endSession("Any ten words I write should let me leave now")
        val after = store.states.first()
        assertFalse(after.session.isActive)
        assertEquals("", after.session.intention)
        assertNull(after.session.presetName)
        assertNull(after.session.deadlineEpochMillis)
        assertEquals(apps, after.presets.first { it.id == "work" }.allowedPackages)
        assertEquals(25, after.lastDurationMinutes)
    }

    @Test
    fun invalidPlansNeverStartOrMutateASession() = runTest {
        val store = store(backgroundScope)
        assertEquals(
            SessionMutation.INVALID_INTENTION,
            store.startSession(apps, false, apps, now, FocusPlan(intention = "x".repeat(161))),
        )
        for (minutes in listOf(0, -1, 1_441)) {
            assertEquals(
                SessionMutation.INVALID_DURATION,
                store.startSession(apps, false, apps, now, FocusPlan(durationMinutes = minutes)),
            )
        }
        assertEquals(FocusSession(), store.sessions.first())
        assertEquals(
            SessionMutation.STARTED,
            store.startSession(apps, false, apps, now, FocusPlan(intention = "x".repeat(160), durationMinutes = 1_440)),
        )
    }

    @Test
    fun reachingTimeNeverEndsFocusOrBypassesTheWordChallenge() = runTest {
        val store = store(backgroundScope)
        store.startSession(apps, true, apps, now, FocusPlan(intention = "Read", durationMinutes = 1))
        val later = now.copy(epochMillis = now.epochMillis + 70_000, elapsedRealtimeMillis = now.elapsedRealtimeMillis + 70_000)
        val original = store.sessions.first()
        assertEquals(0L, original.remainingMillis(later))
        assertTrue(original.isActive)
        assertEquals(SessionMutation.EXIT_TEXT_TOO_SHORT, store.endSession(""))
        assertEquals(SessionMutation.EXTENDED, store.keepGoing(15, later))
        val extended = store.sessions.first()
        assertEquals(15 * 60_000L, extended.remainingMillis(later))
        assertEquals(original.startedAtMillis, extended.startedAtMillis)
        assertEquals(original.allowedPackages, extended.allowedPackages)
        assertEquals(original.intention, extended.intention)
        assertTrue(extended.requireExitText)
        assertEquals(SessionMutation.TIMER_NOT_FINISHED, store.keepGoing(15, later))
        val stillLater = later.copy(
            epochMillis = later.epochMillis + 1_000_000,
            elapsedRealtimeMillis = later.elapsedRealtimeMillis + 1_000_000,
        )
        assertEquals(SessionMutation.EXTENDED, store.keepGoing(null, stillLater))
        assertNull(store.sessions.first().remainingMillis(stillLater))
        assertTrue(store.sessions.first().isActive)
    }
}
