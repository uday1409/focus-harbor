package dev.focusharbor.app.home

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.text.Collator
import java.util.Locale

data class LauncherTarget(val packageName: String, val className: String)

data class NormalLauncher(val target: LauncherTarget, val label: String)

class NormalLauncherCatalog(private val context: Context) {
    fun load(): List<NormalLauncher> {
        val manager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val matches = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.queryIntentActivities(
                intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
            )
        } else {
            manager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        val collator = Collator.getInstance(Locale.getDefault())
        return matches.mapNotNull { it.activityInfo }
            .filter {
                it.packageName != context.packageName && it.exported && it.enabled &&
                    it.applicationInfo.enabled &&
                    (it.permission.isNullOrEmpty() ||
                        context.checkSelfPermission(it.permission) == PackageManager.PERMISSION_GRANTED)
            }
            .map {
                NormalLauncher(
                    LauncherTarget(it.packageName, it.name),
                    it.loadLabel(manager).toString(),
                )
            }
            .distinctBy { it.target }
            .sortedWith { first, second -> collator.compare(first.label, second.label) }
    }
}

fun LauncherTarget.homeIntent(): Intent = Intent(Intent.ACTION_MAIN)
    .addCategory(Intent.CATEGORY_HOME)
    .setComponent(ComponentName(packageName, className))
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
