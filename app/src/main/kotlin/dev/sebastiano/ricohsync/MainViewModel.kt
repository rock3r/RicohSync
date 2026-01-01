package dev.sebastiano.ricohsync

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sebastiano.ricohsync.domain.repository.PairedDevicesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Main ViewModel that manages the app's navigation state.
 *
 * Handles permission state and navigation between:
 * - Permissions screen (when permissions not granted)
 * - Devices list screen (main screen with paired devices)
 * - Pairing screen (for adding new devices)
 */
class MainViewModel(
    private val pairedDevicesRepository: PairedDevicesRepository,
) : ViewModel() {

    private val _mainState = mutableStateOf<MainState>(MainState.NeedsPermissions)
    val mainState: MutableState<MainState> = _mainState

    /** Called when all required permissions have been granted. */
    fun onPermissionsGranted() {
        // Once permissions are granted, go to device list
        // The DevicesListViewModel will handle showing empty state or devices
        _mainState.value = MainState.DevicesList
    }

    /** Navigate to the pairing screen. */
    fun navigateToPairing() {
        _mainState.value = MainState.Pairing
    }

    /** Navigate back to the devices list. */
    fun navigateToDevicesList() {
        _mainState.value = MainState.DevicesList
    }
}

/**
 * Main navigation state of the app.
 */
sealed interface MainState {
    /** Permissions need to be requested. */
    data object NeedsPermissions : MainState

    /** Main screen showing paired devices list. */
    data object DevicesList : MainState

    /** Pairing screen for adding new devices. */
    data object Pairing : MainState
}
