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

            MainState.NoDeviceSelected -> {
                val scanningViewModel = remember { ScanningViewModel() }
                ScanningScreen(scanningViewModel) {
                    mainViewModel.saveSelectedDevice(
                        macAddress = it.identifier,
                        name = it.name ?: it.peripheralName,
                    )
                }
            }

            is MainState.FindingDevice -> SearchingDevice(currentState)

            is MainState.DeviceFound -> {
                val deviceSyncViewModel = remember {
                    DeviceSyncViewModel(
                        advertisement = currentState.advertisement,
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
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
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
private fun SearchingDevice(currentState: MainState.FindingDevice) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Looking for ${currentState.selectedDevice.name}...",
                    style = MaterialTheme.typography.titleLarge,
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    currentState.selectedDevice.macAddress,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
