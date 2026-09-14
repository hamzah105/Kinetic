package dev.kinetic.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dev.kinetic.app.ui.KineticDeveloperScreen
import dev.kinetic.app.ui.KineticTheme

class MainActivity : ComponentActivity() {
    private val viewModel: KernelViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KineticTheme {
                KineticDeveloperScreen(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.registerResumedActivity(this)
    }

    override fun onPause() {
        viewModel.unregisterPausedActivity(this)
        super.onPause()
    }
}
