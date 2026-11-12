package dev.focusharbor.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.focusharbor.app.home.homeIntent
import dev.focusharbor.app.ui.FocusHarborApp
import dev.focusharbor.app.ui.FocusHarborTheme
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val model: FocusViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model.acceptEntryIntent(intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            FocusHarborTheme {
                LifecycleResumeEffect(model) {
                    model.refresh()
                    onPauseOrDispose { }
                }
                FocusHarborApp(model)
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                model.homeRequests.filterNotNull().collect { request ->
                    val target = model.resolveHomeRequest(request)
                    if (model.consumeHomeRequest(request) && target != null) {
                        if (!model.allowHomeHandoff(request)) {
                            Log.w("FocusHarbor", "Repeated normal-Home handoff stopped to avoid a launcher loop.")
                            model.showLauncherRecovery(R.string.launcher_handoff_loop)
                        } else {
                            try {
                                startActivity(target.homeIntent())
                            } catch (error: ActivityNotFoundException) {
                                Log.w("FocusHarbor", "The chosen normal Home launcher could not be opened.", error)
                                model.showLauncherRecovery(R.string.normal_launcher_open_error)
                            } catch (error: SecurityException) {
                                Log.w("FocusHarbor", "Android denied opening the chosen normal Home launcher.", error)
                                model.showLauncherRecovery(R.string.normal_launcher_open_error)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        model.acceptEntryIntent(intent)
    }
}
