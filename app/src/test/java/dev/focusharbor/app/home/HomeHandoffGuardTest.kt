package dev.focusharbor.app.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeHandoffGuardTest {
    @Test
    fun rapidAutomaticHandoffsStopButAnExplicitRetryIsAllowed() {
        val guard = HomeHandoffGuard()
        assertTrue(guard.allow(1_000, automatic = true))
        assertFalse(guard.allow(1_100, automatic = true))
        assertFalse(guard.allow(1_200, automatic = true))
        assertTrue(guard.allow(1_200, automatic = false))
        assertFalse(guard.allow(1_300, automatic = true))
        assertTrue(guard.allow(2_700, automatic = true))
    }

    @Test
    fun openingSetupAllowsAFreshHomeRequestWithoutReplayingThePreviousHandoff() {
        val guard = HomeHandoffGuard()
        assertTrue(guard.allow(1_000, automatic = false))
        guard.reset()
        assertTrue(guard.allow(1_100, automatic = true))
        assertFalse(guard.allow(1_200, automatic = true))
    }
}
