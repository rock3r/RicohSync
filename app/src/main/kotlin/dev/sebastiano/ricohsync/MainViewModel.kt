package dev.sebastiano.ricohsync

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sebastiano.ricohsync.domain.model.RicohCamera
import dev.sebastiano.ricohsync.proto.SelectedDevice
import dev.sebastiano.ricohsync.proto.SelectedDevices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Main ViewModel that manages the app's navigation state.
 */
class MainViewModel(private val dataStore: DataStore<SelectedDevices>) : ViewModel() {

    private val _mainState = mutableStateOf<MainState>(MainState.NeedsPermissions)
    val mainState: MutableState<MainState> = _mainState

    /** Saves the selected device to persistent storage. */
    fun saveSelectedDevice(camera: RicohCamera) {
        viewModelScope.launch(Dispatchers.IO) {
            dataStore.updateData {
                SelectedDevices.newBuilder()
                    .setSelectedDevice(
                        SelectedDevice.newBuilder()
                            .setMacAddress(camera.macAddress)
                            .setName(camera.name)
                            .build()
                    )
                    .setHasSelectedDevice(true)
                    .build()
            }
        }
    }

    /** Called when all required permissions have been granted. */
    fun onPermissionsGranted() {
        dataStore.data
            .onEach { selectedDevices ->
                val selectedDevice =
                    if (selectedDevices.hasSelectedDevice) selectedDevices.selectedDevice else null
                updateMainState(selectedDevice)
            }
            .flowOn(Dispatchers.IO)
            .launchIn(viewModelScope)
    }

    private suspend fun updateMainState(selectedDevice: SelectedDevice?) {
        if (selectedDevice != null) {
            _mainState.value = MainState.FindingDevice(selectedDevice.toRicohCamera())
            // Once the device is found during sync, the DeviceSyncViewModel will handle it
            _mainState.value = MainState.DeviceFound(selectedDevice.toRicohCamera())
        } else {
            _mainState.value = MainState.NoDeviceSelected
        }
    }

    /** Called when the camera disconnects. */
    fun onDeviceDisconnected() {
        viewModelScope.launch { updateMainState(dataStore.data.first().selectedDevice) }
    }

    /** Attempts to reconnect to the previously selected camera. */
    fun reconnect() {
        onDeviceDisconnected()
    }

    private fun SelectedDevice.toRicohCamera(): RicohCamera = RicohCamera(
        identifier = macAddress,
        name = name,
        macAddress = macAddress,
    )
}

/** Main navigation state of the app. */
sealed interface MainState {
    /** Permissions need to be requested. */
    data object NeedsPermissions : MainState

    /** No device has been selected yet. */
    data object NoDeviceSelected : MainState

    /** User has stopped the sync. */
    data object Stopped : MainState

    /** Searching for the previously selected device. */
    data class FindingDevice(val camera: RicohCamera) : MainState

    /** Device found, ready to sync. */
    data class DeviceFound(val camera: RicohCamera) : MainState
}
