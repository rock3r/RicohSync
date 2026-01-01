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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.sebastiano.ricohsync.data.repository.DataStorePairedDevicesRepository
import dev.sebastiano.ricohsync.data.repository.FusedLocationRepository
import dev.sebastiano.ricohsync.data.repository.pairedDevicesDataStoreV2
import dev.sebastiano.ricohsync.devices.DevicesListScreen
import dev.sebastiano.ricohsync.devices.DevicesListViewModel
import dev.sebastiano.ricohsync.devicesync.registerNotificationChannel
import dev.sebastiano.ricohsync.domain.repository.LocationRepository
import dev.sebastiano.ricohsync.pairing.PairingScreen
import dev.sebastiano.ricohsync.pairing.PairingViewModel
import dev.sebastiano.ricohsync.ui.theme.RicohSyncTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        registerNotificationChannel(this)

        val pairedDevicesRepository = DataStorePairedDevicesRepository(pairedDevicesDataStoreV2)
        val locationRepository = FusedLocationRepository(applicationContext)
        val viewModel = MainViewModel(pairedDevicesRepository)

        setContent {
            RootComposable(
                viewModel = viewModel,
                pairedDevicesRepository = pairedDevicesRepository,
                locationRepository = locationRepository,
                context = this,
            )
        }
    }
}

@Composable
private fun RootComposable(
    viewModel: MainViewModel,
    pairedDevicesRepository: DataStorePairedDevicesRepository,
    locationRepository: LocationRepository,
    context: Context,
) {
    RicohSyncTheme {
        val state by viewModel.mainState

        when (state) {
            MainState.NeedsPermissions -> {
                PermissionsScreen(viewModel)
            }

            MainState.DevicesList -> {
                val devicesListViewModel = remember {
                    DevicesListViewModel(
                        pairedDevicesRepository = pairedDevicesRepository,
                        locationRepository = locationRepository,
                        bindingContextProvider = { context.applicationContext },
                    )
                }

                DevicesListScreen(
                    viewModel = devicesListViewModel,
                    onAddDeviceClick = { viewModel.navigateToPairing() },
                )
            }

            MainState.Pairing -> {
                val pairingViewModel = remember {
                    PairingViewModel(pairedDevicesRepository = pairedDevicesRepository)
                }

                PairingScreen(
                    viewModel = pairingViewModel,
                    onNavigateBack = { viewModel.navigateToDevicesList() },
                    onDevicePaired = { viewModel.navigateToDevicesList() },
                )
            }
        }
    }
}

@Composable
private fun PermissionsScreen(mainViewModel: MainViewModel) {
    PermissionsRequester(mainViewModel::onPermissionsGranted) { _, _, _, _, request ->
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("RicohSync needs permissions to work")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "• Location: To get GPS coordinates for your photos",
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                    Text(
                        "• Bluetooth: To connect to your camera",
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                    Text(
                        "• Notifications: To show sync status",
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(request) { Text("Grant permissions") }
                }
            }
        }
    }
}
