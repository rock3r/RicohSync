package dev.sebastiano.ricohsync

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juul.kable.Advertisement
import com.juul.kable.Scanner
import dev.sebastiano.ricohsync.proto.SelectedDevice
import dev.sebastiano.ricohsync.proto.SelectedDevices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class MainViewModel(private val dataStore: DataStore<SelectedDevices>) : ViewModel() {
    private val _mainState = mutableStateOf<MainState>(MainState.NeedsPermissions)
    val mainState: MutableState<MainState> = _mainState

    fun saveSelectedDevice(macAddress: String, name: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            dataStore.updateData {
                SelectedDevices.newBuilder()
                    .setSelectedDevice(
                        SelectedDevice.newBuilder().setMacAddress(macAddress).setName(name).build()
                    )
                    .setHasSelectedDevice(true)
                    .build()
            }
        }
    }

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

    private suspend fun updateMainState(selectedDevice: SelectedDevice?) =
        if (selectedDevice != null) {
            val scanner = Scanner { filters { match { address = selectedDevice.macAddress } } }
            _mainState.value = MainState.FindingDevice(selectedDevice)
            _mainState.value = MainState.DeviceFound(scanner.advertisements.first())
        } else {
            _mainState.value = MainState.NoDeviceSelected
        }
}

sealed interface MainState {
    data object NeedsPermissions : MainState

    data object NoDeviceSelected : MainState

    data class FindingDevice(val selectedDevice: SelectedDevice) : MainState

    data class DeviceFound(val advertisement: Advertisement) : MainState
}
