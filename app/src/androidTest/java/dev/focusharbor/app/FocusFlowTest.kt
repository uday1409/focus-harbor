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
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.focusharbor.app.focus.FocusStore
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

    private val phrase = "I choose my attention and return to what matters now"
    private lateinit var context: Context
    private lateinit var allowedPackage: String
    private lateinit var otherPackage: String
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
        runBlocking {
            context.focusDataStore.edit { it.clear() }
            assertEquals(
                SessionMutation.STARTED,
                FocusStore(context.focusDataStore).startSession(
                    setOf(allowedPackage),
                    phrase,
                    apps.toSet(),
                    System.currentTimeMillis(),
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
        compose.onNodeWithTag("focus-apps")
            .performScrollToNode(hasTestTag("allowed-$allowedPackage"))
        compose.onNodeWithTag("allowed-$allowedPackage").assertIsDisplayed()
        compose.onNodeWithTag("allowed-$otherPackage").assertDoesNotExist()
    }

    @Test
    fun onlyMatchingTenWordTextEndsFocus() {
        compose.onNodeWithTag("leave-focus").performClick()
        compose.onNodeWithTag("confirm-exit").assertIsNotEnabled()
        compose.onNodeWithTag("exit-input")
            .performTextInput("these ten words are not the ones I chose today")
        compose.onNodeWithTag("confirm-exit").assertIsNotEnabled()
        assertTrue(runBlocking { FocusStore(context.focusDataStore).sessions.first().isActive })
        compose.onNodeWithTag("exit-input").performTextReplacement(phrase)
        compose.onNodeWithTag("confirm-exit").assertIsEnabled().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("start-focus").fetchSemanticsNodes().isNotEmpty()
        }
        assertFalse(runBlocking { FocusStore(context.focusDataStore).sessions.first().isActive })
    }

    @Test
    fun backDismissalAndActivityRecreationDoNotEndFocus() {
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
        compose.onNodeWithTag("setup-phrase").assertDoesNotExist()
        assertTrue(runBlocking { FocusStore(context.focusDataStore).sessions.first().isActive })
    }
}
