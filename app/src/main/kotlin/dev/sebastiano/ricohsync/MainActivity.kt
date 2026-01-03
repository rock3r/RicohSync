package dev.sebastiano.ricohsync

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.runtime.NavEntry
import androidx.compose.runtime.mutableStateListOf
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
        val backStack = remember { mutableStateListOf<NavRoute>(NavRoute.NeedsPermissions) }

        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            transitionSpec = {
                (slideInHorizontally(initialOffsetX = { it / 4 }) + fadeIn()) togetherWith
                    (slideOutHorizontally(targetOffsetX = { -it / 4 }) + fadeOut())
            },
            popTransitionSpec = {
                (slideInHorizontally(initialOffsetX = { -it / 4 }) + fadeIn()) togetherWith
                    (slideOutHorizontally(targetOffsetX = { it / 4 }) + fadeOut())
            },
            predictivePopTransitionSpec = {
                (slideInHorizontally(initialOffsetX = { -it / 4 }) + fadeIn()) togetherWith
                    (slideOutHorizontally(targetOffsetX = { it / 4 }) + fadeOut())
            },
        ) { key ->
            NavEntry(key) {
                when (key) {
                    NavRoute.NeedsPermissions -> {
                        PermissionsScreen(
                            onPermissionsGranted = {
                                backStack.add(NavRoute.DevicesList)
                                backStack.remove(NavRoute.NeedsPermissions)
                            }
                        )
                    }

                    NavRoute.DevicesList -> {
                        val devicesListViewModel = remember {
                            DevicesListViewModel(
                                pairedDevicesRepository = pairedDevicesRepository,
                                locationRepository = locationRepository,
                                bindingContextProvider = { context.applicationContext },
                            )
                        }

                        DevicesListScreen(
                            viewModel = devicesListViewModel,
                            onAddDeviceClick = {
                                backStack.add(NavRoute.Pairing)
                            },
                        )
                    }

                    NavRoute.Pairing -> {
                        val pairingViewModel = remember {
                            PairingViewModel(pairedDevicesRepository = pairedDevicesRepository)
                        }

                        PairingScreen(
                            viewModel = pairingViewModel,
                            onNavigateBack = {
                                if (backStack.isNotEmpty()) backStack.removeLast()
                            },
                            onDevicePaired = {
                                if (backStack.isNotEmpty()) backStack.removeLast()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionsScreen(onPermissionsGranted: () -> Unit) {
    PermissionsRequester(onPermissionsGranted) { _, _, _, _, request ->
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
