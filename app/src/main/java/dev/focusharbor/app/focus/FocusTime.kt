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
    return remaining.coerceAtLeast(0)
}
