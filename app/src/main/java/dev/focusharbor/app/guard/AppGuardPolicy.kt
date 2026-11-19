package dev.focusharbor.app.guard

import dev.focusharbor.app.focus.FocusSession
import dev.focusharbor.app.focus.FocusTime
import dev.focusharbor.app.focus.remainingMillis

object AppGuardPolicy {
    private val SYSTEM_EXEMPT_PACKAGES = setOf(
        "android",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.server.telecom",
        "com.android.incallui",
        "com.android.phone",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.oplus.dialer",
        "com.oneplus.dialer",
        "com.coloros.phonemanager",
        "com.android.emergency",
        "com.google.android.emergency",
    )

    fun shouldBlock(
        session: FocusSession,
        now: FocusTime,
        foregroundPackage: String,
        foregroundClass: String,
        selfPackage: String,
        dialerPackages: Set<String>,
        inputMethodPackages: Set<String>,
    ): Boolean {
        if (!session.isActive || !session.strictAppGuard) return false
        val remaining = session.remainingMillis(now)
        // Safety rule: stop blocking immediately when a strict timer reaches 00:00.
        if (remaining != null && remaining <= 0L) return false
        if (foregroundPackage.isBlank() || foregroundPackage == selfPackage) return false
        if (foregroundPackage in session.allowedPackages) return false
        if (foregroundPackage in dialerPackages || foregroundPackage in inputMethodPackages) return false
        if (isEmergencyOrCallUi(foregroundPackage, foregroundClass)) return false
        if (foregroundPackage == "com.android.systemui") {
            return foregroundClass.contains("Recents", ignoreCase = true)
        }
        if (foregroundPackage in SYSTEM_EXEMPT_PACKAGES) return false
        return true
    }

    private fun isEmergencyOrCallUi(packageName: String, className: String): Boolean =
        packageName.contains("incallui", ignoreCase = true) ||
            packageName.contains("emergency", ignoreCase = true) ||
            className.contains("Emergency", ignoreCase = true) ||
            className.contains("InCall", ignoreCase = true)
}
