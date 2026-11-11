package dev.focusharbor.app.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FocusTimeTest {
    private val session = FocusSession(
        isActive = true,
        deadlineEpochMillis = 100_000,
        deadlineElapsedMillis = 50_000,
        deadlineBootCount = 3,
    )

    @Test
    fun wallClockChangesDoNotMoveTheTimerOnTheSameBoot() {
        assertEquals(30_000L, session.remainingMillis(FocusTime(5_000_000, 20_000, 3)))
        assertEquals(30_000L, session.remainingMillis(FocusTime(1, 20_000, 3)))
    }

    @Test
    fun rebootUsesThePersistedWallClockDeadlineAndExpiryClampsToZero() {
        assertEquals(10_000L, session.remainingMillis(FocusTime(90_000, 5, 4)))
        assertEquals(0L, session.remainingMillis(FocusTime(110_000, 5, 4)))
        assertEquals(0L, session.remainingMillis(FocusTime(1, 60_000, 3)))
    }

    @Test
    fun openEndedAndLegacySessionsDoNotAcquireAnImplicitDeadline() {
        assertNull(FocusSession(isActive = true).remainingMillis(FocusTime(100_000)))
        assertEquals(10_000L, session.remainingMillis(FocusTime(90_000)))
    }
}
