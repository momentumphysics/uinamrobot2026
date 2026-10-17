package id.uinam.intellink

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import id.uinam.intellink.control.ConnectionState
import id.uinam.intellink.control.ControlViewModel
import id.uinam.intellink.control.RobotClient
import id.uinam.intellink.data.SettingsStore
import id.uinam.intellink.ui.DashboardScreen
import id.uinam.intellink.ui.Haptics

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val store = SettingsStore(this)

        setContent {
            val viewModel: ControlViewModel = viewModel {
                ControlViewModel(RobotClient(), store.loadIp())
            }
            val haptics = remember { Haptics(applicationContext) }
            val state by viewModel.state.collectAsStateWithLifecycle()
            val ip by viewModel.ip.collectAsStateWithLifecycle()

            LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onForeground() }
            LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onBackground() }
            LaunchedEffect(ip) { store.saveIp(ip) }

            var previousState by remember { mutableStateOf<ConnectionState?>(null) }
            LaunchedEffect(state) {
                when {
                    previousState == ConnectionState.ONLINE && state != ConnectionState.ONLINE ->
                        haptics.disconnectPattern()
                    previousState != null && state == ConnectionState.ONLINE ->
                        haptics.connectPattern()
                }
                previousState = state
            }

            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E),
                    primary = Color(0xFF40C4FF),
                ),
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DashboardScreen(viewModel)
                }
            }
        }
    }
}
