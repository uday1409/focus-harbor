package dev.focusharbor.app.guard

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import androidx.core.net.toUri
import dev.focusharbor.app.MainActivity
import dev.focusharbor.app.focus.AndroidFocusClock
import dev.focusharbor.app.focus.FocusSession
import dev.focusharbor.app.focus.FocusStore
import dev.focusharbor.app.focus.focusDataStore
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

object FocusGuardSignals {
    private val mutableBlockedCount = MutableStateFlow(0L)
    val blockedCount = mutableBlockedCount.asStateFlow()

    fun recordBlocked() {
        mutableBlockedCount.update { it + 1L }
    }
}

class FocusAppGuardService : AccessibilityService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var clock: AndroidFocusClock
    @Volatile private var currentSession = FocusSession()
    private var lastBlockedPackage = ""
    private var lastBlockedElapsedMillis = -10_000L

    override fun onServiceConnected() {
        super.onServiceConnected()
        clock = AndroidFocusClock(applicationContext)
        serviceScope.launch {
            try {
                FocusStore(applicationContext.focusDataStore).sessions.collect { saved ->
                    currentSession = saved
                }
            } catch (error: IOException) {
                Log.e(TAG, "App Guard could not read the saved focus session; pausing enforcement.", error)
                currentSession = FocusSession()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString().orEmpty()
        if (!::clock.isInitialized) clock = AndroidFocusClock(applicationContext)
        val now = clock.now()
        val shouldBlock = AppGuardPolicy.shouldBlock(
            session = currentSession,
            now = now,
            foregroundPackage = pkg,
            foregroundClass = cls,
            selfPackage = packageName,
            dialerPackages = resolveDialerPackages(),
            inputMethodPackages = resolveInputMethodPackages(),
        )
        if (!shouldBlock) return
        if (pkg == lastBlockedPackage && now.elapsedRealtimeMillis - lastBlockedElapsedMillis < DEBOUNCE_MS) {
            return
        }
        lastBlockedPackage = pkg
        lastBlockedElapsedMillis = now.elapsedRealtimeMillis
        val wentHome = isDefaultHome() && performGlobalAction(GLOBAL_ACTION_HOME)
        if (!wentHome) {
            startActivity(
                Intent(this, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                ),
            )
        }
        FocusGuardSignals.recordBlocked()
    }

    private fun isDefaultHome(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                return roleManager.isRoleHeld(RoleManager.ROLE_HOME)
            }
        }
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return home.resolveActivity(packageManager)?.packageName == packageName
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun resolveDialerPackages(): Set<String> {
        val intent = Intent(Intent.ACTION_DIAL, "tel:".toUri())
        val matches = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            packageManager.queryIntentActivities(intent, 0)
        }
        return matches.mapNotNull { it.activityInfo?.packageName }.toSet()
    }

    private fun resolveInputMethodPackages(): Set<String> =
        getSystemService(InputMethodManager::class.java)
            ?.enabledInputMethodList
            ?.map { it.packageName }
            ?.toSet()
            .orEmpty()

    companion object {
        private const val TAG = "FocusHarborGuard"
        private const val DEBOUNCE_MS = 350L

        fun isEnabled(context: Context): Boolean {
            val manager = context.getSystemService(AccessibilityManager::class.java)
            val activeInManager = manager
                ?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                ?.any {
                    val info = it.resolveInfo?.serviceInfo
                    info?.packageName == context.packageName && info.name == FocusAppGuardService::class.java.name
                } == true
            if (activeInManager) return true
            val enabled = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                0,
            ) == 1
            if (!enabled) return false
            val raw = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty()
            val component = ComponentName(context, FocusAppGuardService::class.java)
            val full = component.flattenToString()
            val short = component.flattenToShortString()
            return raw.split(':').any { it.equals(full, ignoreCase = true) || it.equals(short, ignoreCase = true) }
        }

        fun isBatteryUnrestricted(context: Context): Boolean =
            context.getSystemService(PowerManager::class.java)
                ?.isIgnoringBatteryOptimizations(context.packageName) == true
    }
}
