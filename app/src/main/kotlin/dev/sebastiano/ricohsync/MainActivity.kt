package dev.sebastiano.ricohsync

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.BLUETOOTH_SCAN
import android.Manifest.permission.POST_NOTIFICATIONS
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import dev.sebastiano.ricohsync.devices.DevicesListScreen
import dev.sebastiano.ricohsync.devices.DevicesListViewModel
import dev.sebastiano.ricohsync.devicesync.registerNotificationChannel
import dev.sebastiano.ricohsync.di.AppGraph
import dev.sebastiano.ricohsync.pairing.PairingScreen
import dev.sebastiano.ricohsync.pairing.PairingViewModel
import dev.sebastiano.ricohsync.permissions.PermissionsScreen
import dev.sebastiano.ricohsync.ui.theme.RicohSyncTheme

class MainActivity : ComponentActivity() {

    private val appGraph: AppGraph by lazy { (application as RicohSyncApp).appGraph }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        registerNotificationChannel(this)

        setContent { RootComposable(appGraph = appGraph, context = this) }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun RootComposable(appGraph: AppGraph, context: Context) {
    val pairedDevicesRepository = appGraph.pairedDevicesRepository
    val locationRepository = appGraph.locationRepository
    val vendorRegistry = appGraph.vendorRegistry
    RicohSyncTheme {
        val allPermissions =
            listOf(ACCESS_FINE_LOCATION, BLUETOOTH_SCAN, BLUETOOTH_CONNECT, POST_NOTIFICATIONS)
        val multiplePermissionsState =
            rememberMultiplePermissionsState(permissions = allPermissions)

        // Initialize backStack - will be updated if permissions are already granted
        val backStack = remember { mutableStateListOf<NavRoute>(NavRoute.NeedsPermissions) }

        // Check if permissions are already granted and navigate to DevicesList
        // This handles both startup (when permissions are already granted) and runtime (when user
        // grants permissions)
        LaunchedEffect(multiplePermissionsState.allPermissionsGranted) {
            if (
                multiplePermissionsState.allPermissionsGranted &&
                    backStack.contains(NavRoute.NeedsPermissions)
            ) {
                // Replace NeedsPermissions with DevicesList
                val needsPermissionsIndex = backStack.indexOf(NavRoute.NeedsPermissions)
                backStack[needsPermissionsIndex] = NavRoute.DevicesList
            }
        }

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
                                vendorRegistry = vendorRegistry,
                            )
                        }

                        DevicesListScreen(
                            viewModel = devicesListViewModel,
                            onAddDeviceClick = { backStack.add(NavRoute.Pairing) },
                        )
                    }

                    NavRoute.Pairing -> {
                        val pairingViewModel = remember {
                            PairingViewModel(
                                pairedDevicesRepository = pairedDevicesRepository,
                                vendorRegistry = vendorRegistry,
                            )
                        }

                        PairingScreen(
                            viewModel = pairingViewModel,
                            onNavigateBack = {
                                // Can't use removeLast before API 35
                                // Ensure we don't remove the last item (must keep at least one
                                // route)
                                if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                            },
                            onDevicePaired = {
                                // Can't use removeLast before API 35
                                // Ensure we don't remove the last item (must keep at least one
                                // route)
                                if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                            },
                        )
                    }
                }
            }
        }
    }
}
