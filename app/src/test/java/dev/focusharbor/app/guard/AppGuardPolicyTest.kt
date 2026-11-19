package dev.focusharbor.app.guard

import dev.focusharbor.app.focus.FocusSession
import dev.focusharbor.app.focus.FocusTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppGuardPolicyTest {
    private val ownPackage = "dev.focusharbor.app"
    private val dialers = setOf("com.google.android.dialer")
    private val imes = setOf("com.google.android.inputmethod.latin")
    private val now = FocusTime(epochMillis = 100_000, elapsedRealtimeMillis = 50_000, bootCount = 1)

    private val strictSession = FocusSession(
        allowedPackages = setOf("com.whatsapp"),
        strictAppGuard = true,
        isActive = true,
        startedAtMillis = 90_000,
        durationMinutes = 25,
        deadlineEpochMillis = 1_590_000,
        deadlineElapsedMillis = 1_540_000,
        deadlineBootCount = 1,
    )

    @Test
    fun blocksNonAllowlistedBrowsersSettingsAndLauncherRecentsDuringActiveGuard() {
        assertTrue(
            AppGuardPolicy.shouldBlock(
                session = strictSession,
                now = now,
                foregroundPackage = "com.android.chrome",
                foregroundClass = "org.chromium.chrome.browser.ChromeTabbedActivity",
                selfPackage = ownPackage,
                dialerPackages = dialers,
                inputMethodPackages = imes,
            ),
        )
        assertTrue(
            AppGuardPolicy.shouldBlock(
                session = strictSession,
                now = now,
                foregroundPackage = "com.android.launcher3",
                foregroundClass = "com.android.quickstep.RecentsActivity",
                selfPackage = ownPackage,
                dialerPackages = dialers,
                inputMethodPackages = imes,
            ),
        )
        assertTrue(
            AppGuardPolicy.shouldBlock(
                session = strictSession,
                now = now,
                foregroundPackage = "com.android.systemui",
                foregroundClass = "com.android.systemui.recents.RecentsActivity",
                selfPackage = ownPackage,
                dialerPackages = dialers,
                inputMethodPackages = imes,
            ),
        )
        assertTrue(
            AppGuardPolicy.shouldBlock(
                session = strictSession,
                now = now,
                foregroundPackage = "com.android.settings",
                foregroundClass = "com.android.settings.Settings",
                selfPackage = ownPackage,
                dialerPackages = dialers,
                inputMethodPackages = imes,
            ),
        )
    }

    @Test
    fun alwaysAllowsOwnAppAllowlistedAppsDialerEmergencyKeyboardsAndNonRecentsSystemUi() {
        val allowedTargets = listOf(
            ownPackage to "dev.focusharbor.app.MainActivity",
            "com.whatsapp" to "com.whatsapp.HomeActivity",
            "com.google.android.dialer" to "com.google.android.dialer.extensions.GoogleDialtactsActivity",
            "com.google.android.inputmethod.latin" to "android.inputmethodservice.SoftInputWindow",
            "com.android.server.telecom" to "com.android.server.telecom.EmergencyCallActivity",
            "com.vendor.phone" to "com.vendor.phone.EmergencyDialerActivity",
            "com.android.systemui" to "com.android.systemui.globalactions.GlobalActionsDialogLite",
            "android" to "com.android.internal.app.ResolverActivity",
        )
        for ((pkg, cls) in allowedTargets) {
            assertFalse(
                "Expected $pkg ($cls) to be exempt from blocking",
                AppGuardPolicy.shouldBlock(
                    session = strictSession,
                    now = now,
                    foregroundPackage = pkg,
                    foregroundClass = cls,
                    selfPackage = ownPackage,
                    dialerPackages = dialers,
                    inputMethodPackages = imes,
                ),
            )
        }
    }

    @Test
    fun stopsBlockingImmediatelyWhenInactiveGuardDisabledOrTimerReachesZero() {
        assertFalse(
            AppGuardPolicy.shouldBlock(
                session = strictSession.copy(isActive = false),
                now = now,
                foregroundPackage = "com.android.chrome",
                foregroundClass = "",
                selfPackage = ownPackage,
                dialerPackages = dialers,
                inputMethodPackages = imes,
            ),
        )
        assertFalse(
            AppGuardPolicy.shouldBlock(
                session = strictSession.copy(strictAppGuard = false),
                now = now,
                foregroundPackage = "com.android.chrome",
                foregroundClass = "",
                selfPackage = ownPackage,
                dialerPackages = dialers,
                inputMethodPackages = imes,
            ),
        )
        val expired = now.copy(epochMillis = 2_000_000, elapsedRealtimeMillis = 2_000_000)
        assertFalse(
            AppGuardPolicy.shouldBlock(
                session = strictSession,
                now = expired,
                foregroundPackage = "com.android.chrome",
                foregroundClass = "",
                selfPackage = ownPackage,
                dialerPackages = dialers,
                inputMethodPackages = imes,
            ),
        )
    }
}
