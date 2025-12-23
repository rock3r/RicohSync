package dev.sebastiano.ricohsync

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.sebastiano.ricohsync.devicesync.DeviceSyncScreen
import dev.sebastiano.ricohsync.devicesync.DeviceSyncViewModel
import dev.sebastiano.ricohsync.devicesync.registerNotificationChannel
import dev.sebastiano.ricohsync.domain.model.RicohCamera
import dev.sebastiano.ricohsync.proto.pairedDevicesDataStore
import dev.sebastiano.ricohsync.scanning.ScanningScreen
import dev.sebastiano.ricohsync.scanning.ScanningViewModel
import dev.sebastiano.ricohsync.ui.theme.RicohSyncTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        registerNotificationChannel(this)

        val viewModel = MainViewModel(pairedDevicesDataStore)
        setContent { RootComposable(viewModel, context = this) }
    }
}

@Composable
private fun RootComposable(mainViewModel: MainViewModel, context: Context) {
    RicohSyncTheme {
        val state by mainViewModel.mainState
        when (val currentState = state) {
            MainState.NeedsPermissions -> PermissionsScreen(mainViewModel)

            MainState.Stopped -> StoppedScreen { mainViewModel.reconnect() }

            MainState.NoDeviceSelected -> {
                val scanningViewModel = remember { ScanningViewModel() }
                ScanningScreen(scanningViewModel) { camera ->
                    mainViewModel.saveSelectedDevice(camera)
                }
            }

            is MainState.FindingDevice -> SearchingDevice(currentState.camera)

            is MainState.DeviceFound -> {
                val deviceSyncViewModel = remember {
                    DeviceSyncViewModel(
                        camera = currentState.camera,
                        onDeviceDisconnected = { mainViewModel.onDeviceDisconnected() },
                        bindingContextProvider = { context.applicationContext },
                    )
                }

                DeviceSyncScreen(deviceSyncViewModel)
            }
        }
    }
}

@Composable
private fun PermissionsScreen(mainViewModel: MainViewModel) {
    PermissionsRequester(mainViewModel::onPermissionsGranted) { _, _, _, _, request ->
        // TODO explain missing permissions
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Please grant permissions")
                    Spacer(Modifier.height(8.dp))
                    Button(request) { Text("Grant now") }
                }
            }
        }
    }
}

@Composable
private fun StoppedScreen(onReconnect: () -> Unit) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Stopped by user")
                Spacer(Modifier.height(8.dp))
                Button(onReconnect) { Text("Reconnect") }
            }
        }
    }
}

@Composable
private fun SearchingDevice(camera: RicohCamera) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Looking for ${camera.name ?: "camera"}...",
                    style = MaterialTheme.typography.titleLarge,
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    camera.macAddress,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
