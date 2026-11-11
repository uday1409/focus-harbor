package dev.focusharbor.app

import android.app.role.RoleManager
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.assertTextEquals
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import dev.focusharbor.app.focus.FocusStore
import dev.focusharbor.app.focus.FocusSession
import dev.focusharbor.app.focus.AndroidFocusClock
import dev.focusharbor.app.focus.remainingMillis
import dev.focusharbor.app.focus.focusDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
class SetupFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var context: Context
    private var previousHome = ""
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun prepareDedicatedEmulator() {
        assertTrue("This test must never change Home on a real device", Build.HARDWARE == "ranchu")
        assertTrue("This acceptance test requires the Home role", Build.VERSION.SDK_INT >= 29)
        context = ApplicationProvider.getApplicationContext()
        previousHome = shell("cmd role get-role-holders --user 0 android.app.role.HOME").trim()
        assertTrue("A stock Home app is required for recovery testing", previousHome.matches(PackageName))
        runBlocking { context.focusDataStore.edit { it.clear() } }
        shell("cmd role add-role-holder --user 0 android.app.role.HOME ${context.packageName}")
        assertTrue(
            "The emulator did not grant the Home role",
            context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME),
        )
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("start-focus").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @After
    fun restoreEmulatorHome() {
        scenario?.close()
        if (previousHome.matches(PackageName)) {
            shell("cmd role add-role-holder --user 0 android.app.role.HOME $previousHome")
            assertTrue(
                "The emulator's original launcher was not restored",
                shell("cmd role get-role-holders --user 0 android.app.role.HOME").trim() == previousHome,
            )
        }
        if (::context.isInitialized) {
            runBlocking { context.focusDataStore.edit { it.clear() } }
        }
    }

    @Test
    fun userCanOptInBeforeFocusAndAndroidCanRestoreHome() {
        val session = configureAndStart(true)
        assertTrue(session.requireExitText)
        scenario?.recreate()
        compose.onNodeWithTag("leave-focus").assertIsDisplayed()
    }

    @Test
    fun userCanStartWithTheChallengeOffAndLeaveImmediately() {
        val session = configureAndStart(false)
        assertFalse(session.requireExitText)
        compose.onNodeWithTag("leave-focus").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("start-focus").fetchSemanticsNodes().isNotEmpty()
        }
        assertFalse(runBlocking { FocusStore(context.focusDataStore).sessions.first().isActive })
    }

    @Test
    fun presetsKeepSeparateAppsAndTheSessionShowsItsIntention() {
        val apps = context.getSystemService(LauncherApps::class.java)
            .getActivityList(null, Process.myUserHandle())
            .map { it.componentName.packageName }
            .filter { it != context.packageName }
            .distinct()
        assertTrue(apps.size >= 2)
        chooseApp(apps[0])
        selectPreset("study")
        chooseApp(apps[1])
        selectPreset("work")
        awaitModel(compose, requireNotNull(scenario)) { it.draftAllowed == setOf(apps[0]) }
        compose.onNodeWithTag("setup-list").performScrollToNode(hasTestTag("duration-selector"))
        compose.onNodeWithTag("duration-selector").performClick()
        compose.onNodeWithTag("duration-choice-25").performClick()
        compose.onNodeWithTag("duration-save").performClick()
        compose.onNodeWithTag("setup-list").performScrollToNode(hasTestTag("intention-input"))
        compose.onNodeWithTag("intention-input").performTextInput("Finish chapter 3")
        compose.onNodeWithTag("setup-list").performScrollToNode(hasTestTag("safety-ack"))
        compose.onNodeWithTag("safety-ack").performClick()
        compose.onNodeWithTag("start-focus").assertIsEnabled().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("session-intention").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("session-intention").assertTextEquals("Finish chapter 3")
        val session = runBlocking { FocusStore(context.focusDataStore).sessions.first() }
        assertEquals(context.getString(R.string.preset_work), session.presetName)
        assertEquals(setOf(apps[0]), session.allowedPackages)
        assertTrue(requireNotNull(session.remainingMillis(AndroidFocusClock(context).now())) > 0)
    }

    @Test
    fun presetsCanBeCreatedRenamedAndDeletedFromSetup() {
        compose.onNodeWithTag("setup-list").performScrollToNode(hasTestTag("preset-new"))
        compose.onNodeWithTag("preset-new").performClick()
        compose.onNodeWithTag("preset-name-input").performTextInput("Reading")
        compose.onNodeWithTag("preset-save").performClick()
        awaitModel(compose, requireNotNull(scenario)) {
            !it.busy && it.presets.any { preset -> preset.id == it.selectedPresetId && preset.name == "Reading" }
        }
        compose.onNodeWithTag("preset-rename").performClick()
        compose.onNodeWithTag("preset-name-input").performTextReplacement("Reading today")
        compose.onNodeWithTag("preset-save").performClick()
        awaitModel(compose, requireNotNull(scenario)) { !it.busy && it.presets.any { preset -> preset.name.contains("today") } }
        compose.onNodeWithTag("preset-delete").performClick()
        compose.onNodeWithTag("preset-delete-confirm").performClick()
        awaitModel(compose, requireNotNull(scenario)) { !it.busy && it.presets.size == 4 }
    }

    private fun selectPreset(id: String) {
        compose.onNodeWithTag("setup-list").performScrollToNode(hasTestTag("preset-selector"))
        compose.onNodeWithTag("preset-selector").performClick()
        compose.onNodeWithTag("preset-option-$id").performClick()
        awaitModel(compose, requireNotNull(scenario)) { !it.busy && it.selectedPresetId == id }
    }

    private fun chooseApp(packageName: String) {
        compose.onNodeWithTag("setup-list")
            .performScrollToNode(hasText(context.getString(R.string.choose_apps)))
        compose.onNodeWithText(context.getString(R.string.choose_apps)).performClick()
        awaitAppCatalog(compose, requireNotNull(scenario))
        compose.onNodeWithTag("app-picker-list").performScrollToNode(hasTestTag("pick-$packageName"))
        compose.onNodeWithTag("pick-$packageName").performClick()
        compose.onNodeWithText(context.getString(R.string.done)).performClick()
        awaitModel(compose, requireNotNull(scenario)) { !it.busy }
    }

    private fun configureAndStart(requireExitText: Boolean): FocusSession {
        val selected = context.getSystemService(LauncherApps::class.java)
            .getActivityList(null, Process.myUserHandle())
            .first { it.componentName.packageName != context.packageName }
            .componentName.packageName
        compose.onNodeWithTag("start-focus").assertIsNotEnabled()
        compose.onNodeWithTag("setup-list")
            .performScrollToNode(hasTestTag("require-exit-text"))
        compose.onNodeWithTag("require-exit-text").assertIsOff()
        if (requireExitText) {
            compose.onNodeWithTag("require-exit-text").performClick().assertIsOn()
        }
        compose.onNodeWithTag("setup-list")
            .performScrollToNode(hasText(context.getString(R.string.choose_apps)))
        compose.onNodeWithText(context.getString(R.string.choose_apps)).performClick()
        awaitAppCatalog(compose, requireNotNull(scenario))
        compose.onNodeWithTag("app-picker-list")
            .performScrollToNode(hasTestTag("pick-$selected"))
        compose.onNodeWithTag("pick-$selected").performClick()
        compose.onNodeWithText(context.getString(R.string.done)).performClick()
        awaitModel(compose, requireNotNull(scenario)) { !it.busy }
        compose.onNodeWithTag("setup-list")
            .performScrollToNode(hasTestTag("safety-ack"))
        compose.onNodeWithTag("safety-ack").performClick()
        compose.onNodeWithTag("start-focus").assertIsEnabled().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("leave-focus").fetchSemanticsNodes().isNotEmpty()
        }
        val session = runBlocking { FocusStore(context.focusDataStore).sessions.first() }
        assertTrue(session.isActive)
        assertEquals(setOf(selected), session.allowedPackages)
        assertEquals(requireExitText, session.requireExitText)
        return session
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command),
        ).bufferedReader().use { it.readText() }

    private companion object {
        val PackageName = Regex("[a-zA-Z0-9_]+(?:\\.[a-zA-Z0-9_]+)+")
    }
}
