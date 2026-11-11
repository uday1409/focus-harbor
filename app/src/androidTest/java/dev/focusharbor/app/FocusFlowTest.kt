package dev.focusharbor.app

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Process
import android.view.KeyEvent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertTextEquals
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.focusharbor.app.focus.FocusStore
import dev.focusharbor.app.focus.AndroidFocusClock
import dev.focusharbor.app.focus.FocusPlan
import dev.focusharbor.app.focus.FocusPreset
import dev.focusharbor.app.focus.FocusTime
import dev.focusharbor.app.focus.remainingMillis
import dev.focusharbor.app.focus.SessionMutation
import dev.focusharbor.app.focus.focusDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FocusFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val tenWords = "Any ten words I write should let me leave now"
    private lateinit var context: Context
    private lateinit var allowedPackage: String
    private lateinit var otherPackage: String
    private lateinit var availablePackages: Set<String>
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun prepareSession() {
        assertTrue("These tests must only change data on an emulator", Build.HARDWARE == "ranchu")
        context = ApplicationProvider.getApplicationContext()
        val apps = context.getSystemService(LauncherApps::class.java)
            .getActivityList(null, Process.myUserHandle())
            .map { it.componentName.packageName }
            .filter { it != context.packageName }
            .distinct()
        assertTrue("The test emulator needs at least two launchable apps", apps.size >= 2)
        allowedPackage = apps[0]
        otherPackage = apps[1]
        availablePackages = apps.toSet()
    }

    private fun launchSession(
        requireExitText: Boolean,
        plan: FocusPlan = FocusPlan(),
        start: FocusTime = AndroidFocusClock(context).now(),
    ) {
        runBlocking {
            context.focusDataStore.edit { it.clear() }
            val store = FocusStore(context.focusDataStore)
            store.initializePresets(listOf(FocusPreset("work", context.getString(R.string.preset_work))))
            assertEquals(
                SessionMutation.STARTED,
                store.startSession(
                    setOf(allowedPackage),
                    requireExitText,
                    availablePackages,
                    start,
                    plan.copy(presetId = "work"),
                ),
            )
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("leave-focus").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @After
    fun cleanUp() {
        scenario?.close()
        if (::context.isInitialized) {
            runBlocking { context.focusDataStore.edit { it.clear() } }
        }
    }

    @Test
    fun focusHomeOnlyShowsAllowlistedApps() {
        launchSession(true)
        awaitAppCatalog(compose, requireNotNull(scenario))
        compose.onNodeWithTag("focus-apps")
            .performScrollToNode(hasTestTag("allowed-$allowedPackage"))
        compose.onNodeWithTag("allowed-$allowedPackage").assertIsDisplayed()
        compose.onNodeWithTag("allowed-$otherPackage").assertDoesNotExist()
    }

    @Test
    fun anyTenOrMoreWordsEndAnOptedInSession() {
        launchSession(true)
        compose.onNodeWithTag("leave-focus").performClick()
        compose.onNodeWithText(context.getString(R.string.exit_title)).assertIsDisplayed()
        compose.onNodeWithTag("confirm-exit").assertIsNotEnabled()
        compose.onNodeWithTag("exit-input")
            .performTextInput("one two three four five six seven eight nine")
        compose.onNodeWithTag("confirm-exit").assertIsNotEnabled()
        assertTrue(runBlocking { FocusStore(context.focusDataStore).sessions.first().isActive })
        compose.onNodeWithTag("exit-input").performTextReplacement(tenWords)
        compose.onNodeWithTag("confirm-exit").assertIsEnabled()
        compose.onNodeWithTag("exit-input").performTextReplacement("$tenWords today")
        compose.onNodeWithTag("confirm-exit").assertIsEnabled().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("start-focus").fetchSemanticsNodes().isNotEmpty()
        }
        assertFalse(runBlocking { FocusStore(context.focusDataStore).sessions.first().isActive })
    }

    @Test
    fun backDismissalAndActivityRecreationDoNotEndFocus() {
        launchSession(true)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        compose.waitUntil(10_000) {
            var focused = false
            scenario?.onActivity { focused = it.hasWindowFocus() }
            focused
        }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("exit-input").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("exit-input").assertIsDisplayed()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("exit-input").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("exit-input").assertDoesNotExist()
        scenario?.recreate()
        compose.onNodeWithTag("leave-focus").assertIsDisplayed()
        compose.onNodeWithTag("require-exit-text").assertDoesNotExist()
        val saved = runBlocking { FocusStore(context.focusDataStore).sessions.first() }
        assertTrue(saved.isActive)
        assertTrue(saved.requireExitText)
    }

    @Test
    fun optingOutEndsFocusImmediatelyWithoutShowingATextChallenge() {
        launchSession(false)
        compose.onNodeWithTag("leave-focus").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("start-focus").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("exit-input").assertDoesNotExist()
        assertFalse(runBlocking { FocusStore(context.focusDataStore).sessions.first().isActive })
    }

    @Test
    fun timedCompletionPreservesIntentionAndExitPolicyAndCanKeepGoing() {
        val clock = AndroidFocusClock(context)
        val now = clock.now()
        launchSession(
            true,
            FocusPlan(intention = "Finish chapter 3", durationMinutes = 1),
            now.copy(epochMillis = now.epochMillis - 120_000, elapsedRealtimeMillis = now.elapsedRealtimeMillis - 120_000),
        )
        compose.onNodeWithTag("session-intention").assertTextEquals("Finish chapter 3")
        compose.onNodeWithTag("focus-apps").performScrollToNode(hasTestTag("time-reached-finish"))
        compose.onNodeWithTag("time-reached-finish").performClick()
        compose.onNodeWithTag("exit-input").performTextInput("one two three four five six seven eight nine")
        compose.onNodeWithTag("confirm-exit").assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.stay_focused)).performClick()
        compose.onNodeWithTag("focus-apps").performScrollToNode(hasTestTag("keep-going"))
        compose.onNodeWithTag("keep-going").performClick()
        compose.onNodeWithTag("duration-choice-15").performClick()
        compose.onNodeWithTag("duration-save").performClick()
        awaitModel(compose, requireNotNull(scenario)) {
            !it.busy && it.session?.remainingMillis(clock.now())?.let { remaining -> remaining > 0 } == true
        }
        val saved = runBlocking { FocusStore(context.focusDataStore).sessions.first() }
        assertTrue(saved.isActive)
        assertTrue(saved.requireExitText)
        assertEquals("Finish chapter 3", saved.intention)
        scenario?.recreate()
        compose.onNodeWithTag("session-intention").assertTextEquals("Finish chapter 3")
        compose.onNodeWithTag("time-reached").assertDoesNotExist()
    }
}

internal fun awaitAppCatalog(
    compose: ComposeTestRule,
    scenario: ActivityScenario<MainActivity>,
) {
    awaitModel(compose, scenario) { it.catalogLoaded }
    scenario.onActivity {
        assertFalse("The app catalog could not be loaded", ViewModelProvider(it)[FocusViewModel::class.java].state.value.catalogError)
    }
}

internal fun awaitModel(
    compose: ComposeTestRule,
    scenario: ActivityScenario<MainActivity>,
    condition: (FocusUiState) -> Boolean,
) {
    compose.waitUntil(15_000) {
        var ready = false
        scenario.onActivity { ready = condition(ViewModelProvider(it)[FocusViewModel::class.java].state.value) }
        ready
    }
}
