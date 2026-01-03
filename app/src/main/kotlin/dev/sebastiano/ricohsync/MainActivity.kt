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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import dev.sebastiano.ricohsync.data.repository.DataStorePairedDevicesRepository
import dev.sebastiano.ricohsync.data.repository.FusedLocationRepository
import dev.sebastiano.ricohsync.data.repository.pairedDevicesDataStoreV2
import dev.sebastiano.ricohsync.devices.DevicesListScreen
import dev.sebastiano.ricohsync.devices.DevicesListViewModel
import dev.sebastiano.ricohsync.devicesync.registerNotificationChannel
import dev.sebastiano.ricohsync.domain.repository.LocationRepository
import dev.sebastiano.ricohsync.pairing.PairingScreen
import dev.sebastiano.ricohsync.pairing.PairingViewModel
import dev.sebastiano.ricohsync.permissions.PermissionsScreen
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
                            onAddDeviceClick = { backStack.add(NavRoute.Pairing) },
                        )
                    }

                    NavRoute.Pairing -> {
                        val pairingViewModel = remember {
                            PairingViewModel(pairedDevicesRepository = pairedDevicesRepository)
                        }

                        PairingScreen(
                            viewModel = pairingViewModel,
                            onNavigateBack = {
                                // Can't use removeLast before API 35
                                if (backStack.isNotEmpty()) backStack.removeAt(backStack.lastIndex)
                            },
                            onDevicePaired = {
                                // Can't use removeLast before API 35
                                if (backStack.isNotEmpty()) backStack.removeAt(backStack.lastIndex)
                            },
                        )
                    }
                }
            }
        }
    }
}
