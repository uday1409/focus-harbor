package dev.focusharbor.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.focusharbor.app.ui.FocusHarborApp
import dev.focusharbor.app.ui.FocusHarborTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            FocusHarborTheme {
                val model: FocusViewModel = viewModel()
                LifecycleResumeEffect(model) {
                    model.refresh()
                    onPauseOrDispose { }
                }
                FocusHarborApp(model)
            }
        }
    }
}
