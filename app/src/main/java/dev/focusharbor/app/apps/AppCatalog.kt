package dev.focusharbor.app.apps

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.os.Process
import androidx.core.graphics.drawable.toBitmap
import java.text.Collator
import java.util.Locale

data class LaunchableApp(
    val packageName: String,
    val label: String,
    val component: ComponentName,
    val icon: Bitmap,
)

class AppCatalog(private val context: Context) {
    fun load(): List<LaunchableApp> {
        val launcher = context.getSystemService(LauncherApps::class.java)
        val collator = Collator.getInstance(Locale.getDefault())
        return launcher.getActivityList(null, Process.myUserHandle())
            .filter { it.componentName.packageName != context.packageName }
            .distinctBy { it.componentName.packageName }
            .map {
                LaunchableApp(
                    packageName = it.componentName.packageName,
                    label = it.label.toString(),
                    component = it.componentName,
                    icon = it.getIcon(context.resources.displayMetrics.densityDpi).toBitmap(96, 96),
                )
            }
            .sortedWith { first, second -> collator.compare(first.label, second.label) }
    }
}
