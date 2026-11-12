package dev.focusharbor.app.home

data class HomeRequest(val id: Long, val automatic: Boolean)

class HomeHandoffGuard {
    private var lastHandoffMillis: Long? = null

    fun allow(elapsedMillis: Long, automatic: Boolean): Boolean {
        val last = lastHandoffMillis
        // A launcher can bounce a Home intent back to the default Home app.
        if (automatic && last != null && elapsedMillis - last < 1_500) return false
        lastHandoffMillis = elapsedMillis
        return true
    }

    fun reset() {
        lastHandoffMillis = null
    }
}
