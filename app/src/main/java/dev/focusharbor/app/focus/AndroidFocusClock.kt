package dev.focusharbor.app.focus

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.util.Log

class AndroidFocusClock(context: Context) {
    private val bootCount = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
    } catch (error: Settings.SettingNotFoundException) {
        Log.w("FocusHarbor", "Boot counter unavailable; timers will use the saved wall-clock deadline.", error)
        null
    } catch (error: SecurityException) {
        Log.w("FocusHarbor", "Boot counter access denied; timers will use the saved wall-clock deadline.", error)
        null
    }

    fun now() = FocusTime(System.currentTimeMillis(), SystemClock.elapsedRealtime(), bootCount)
}
