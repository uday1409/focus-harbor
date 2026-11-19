package dev.focusharbor.app.focus

data class FocusTime(
    val epochMillis: Long,
    val elapsedRealtimeMillis: Long = 0,
    val bootCount: Int? = null,
)

fun FocusSession.remainingMillis(now: FocusTime): Long? {
    val epochDeadline = deadlineEpochMillis ?: return null
    val remaining = if (
        deadlineBootCount != null && deadlineBootCount == now.bootCount &&
        deadlineElapsedMillis != null
    ) {
        deadlineElapsedMillis - now.elapsedRealtimeMillis
    } else {
        epochDeadline - now.epochMillis
    }
    val maxMinutes = (durationMinutes ?: FocusStore.MAX_DURATION_MINUTES)
        .coerceIn(1, FocusStore.MAX_DURATION_MINUTES)
    return remaining.coerceIn(0L, maxMinutes * 60_000L)
}
