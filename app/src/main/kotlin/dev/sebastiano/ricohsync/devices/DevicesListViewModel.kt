package dev.sebastiano.ricohsync.devices

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sebastiano.ricohsync.devicesync.MultiDeviceSyncService
import dev.sebastiano.ricohsync.domain.model.DeviceConnectionState
import dev.sebastiano.ricohsync.domain.model.GpsLocation
import dev.sebastiano.ricohsync.domain.model.PairedDeviceWithState
import dev.sebastiano.ricohsync.domain.repository.LocationRepository
import dev.sebastiano.ricohsync.domain.repository.PairedDevicesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "DevicesListViewModel"

/**
 * ViewModel for the devices list screen.
 *
 * Manages the list of paired devices and their connection states. Communicates with the
 * [MultiDeviceSyncService] to control device sync.
 */
class DevicesListViewModel(
    private val pairedDevicesRepository: PairedDevicesRepository,
    private val locationRepository: LocationRepository,
    private val bindingContextProvider: () -> Context,
) : ViewModel() {

    private val _state = mutableStateOf<DevicesListState>(DevicesListState.Loading)
    val state: State<DevicesListState> = _state

    private var service: MultiDeviceSyncService? = null
    private var serviceConnection: ServiceConnection? = null
    private val deviceStatesFromService =
        MutableStateFlow<Map<String, DeviceConnectionState>>(emptyMap())
    private val isScanningFromService = MutableStateFlow(false)
    private var stateCollectionJob: Job? = null
    private var scanningCollectionJob: Job? = null

    init {
        observeDevices()
        bindToService()
        locationRepository.startLocationUpdates()
    }

    private fun observeDevices() {
        viewModelScope.launch(Dispatchers.IO) {
            combine(
                    pairedDevicesRepository.pairedDevices,
                    deviceStatesFromService,
                    isScanningFromService,
                    pairedDevicesRepository.isSyncEnabled,
                    locationRepository.locationUpdates,
                ) { pairedDevices, connectionStates, isScanning, isSyncEnabled, currentLocation ->
                    if (pairedDevices.isEmpty()) {
                        DevicesListState.Empty
                    } else {
                        DevicesListState.HasDevices(
                            devices =
                                pairedDevices.map { device ->
                                    val connectionState =
                                        when {
                                            !device.isEnabled -> DeviceConnectionState.Disabled
                                            else ->
                                                connectionStates[device.macAddress]
                                                    ?: DeviceConnectionState.Disconnected
                                        }
                                    PairedDeviceWithState(device, connectionState)
                                },
                            isScanning = isScanning,
                            isSyncEnabled = isSyncEnabled,
                            currentLocation = currentLocation,
                        )
                    }
                }
                .collect { newState -> _state.value = newState }
        }
    }

    private fun bindToService() {
        viewModelScope.launch(Dispatchers.Main) {
            val context = bindingContextProvider()
            val intent = Intent(context, MultiDeviceSyncService::class.java)

            // Start the service if there are enabled devices AND global sync is enabled
            viewModelScope.launch(Dispatchers.IO) {
                if (
                    pairedDevicesRepository.hasEnabledDevices() &&
                        pairedDevicesRepository.isSyncEnabled.first()
                ) {
                    context.startService(intent)
                }
            }

            val connection =
                object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                        Log.i(TAG, "Connected to MultiDeviceSyncService")
                        service =
                            MultiDeviceSyncService.getInstanceFrom(binder as android.os.Binder)

                        // Observe device states from service
                        stateCollectionJob =
                            viewModelScope.launch(Dispatchers.IO) {
                                service?.deviceStates?.collect { states ->
                                    deviceStatesFromService.value = states
                                }
                            }

                        // Observe scanning state from service
                        scanningCollectionJob =
                            viewModelScope.launch(Dispatchers.IO) {
                                service?.isScanning?.collect { isScanning ->
                                    isScanningFromService.value = isScanning
                                }
                            }
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        Log.i(TAG, "Disconnected from MultiDeviceSyncService")
                        service = null
                        stateCollectionJob?.cancel()
                        scanningCollectionJob?.cancel()
                        deviceStatesFromService.value = emptyMap()
                        isScanningFromService.value = false
                    }
                }

            serviceConnection = connection
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    /** Sets whether a device is enabled for sync. */
    fun setDeviceEnabled(macAddress: String, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            pairedDevicesRepository.setDeviceEnabled(macAddress, enabled)

            // Start service if we just enabled a device AND global sync is enabled
            // We call startService even if service is already bound, to ensure onStartCommand is
            // triggered and monitoring starts if it was stopped.
            if (enabled && pairedDevicesRepository.isSyncEnabled.first()) {
                val context = bindingContextProvider()
                context.startService(Intent(context, MultiDeviceSyncService::class.java))
            }
        }
    }

    /** Unpairs (removes) a device. */
    fun unpairDevice(macAddress: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // First disconnect if connected
            service?.disconnectDevice(macAddress)

            // Then remove from storage
            pairedDevicesRepository.removeDevice(macAddress)
        }
    }

    /** Retries connection to a failed device. */
    fun retryConnection(macAddress: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val device = pairedDevicesRepository.getDevice(macAddress) ?: return@launch
            service?.connectDevice(device)
        }
    }

    /** Manually triggers a scan for all enabled but disconnected devices. */
    fun refreshConnections() {
        viewModelScope.launch(Dispatchers.IO) {
            pairedDevicesRepository.setSyncEnabled(true)
            val context = bindingContextProvider()
            val intent = MultiDeviceSyncService.createRefreshIntent(context)
            context.startService(intent)
        }
    }

    override fun onCleared() {
        super.onCleared()
        locationRepository.stopLocationUpdates()
        stateCollectionJob?.cancel()
        scanningCollectionJob?.cancel()
        serviceConnection?.let { connection ->
            try {
                bindingContextProvider().unbindService(connection)
            } catch (e: Exception) {
                Log.w(TAG, "Error unbinding service", e)
            }
        }
    }
}

/** UI state for the devices list screen. */
sealed interface DevicesListState {
    /** Loading devices from storage. */
    data object Loading : DevicesListState

    /** No paired devices. */
    data object Empty : DevicesListState

    /** Has one or more paired devices. */
    data class HasDevices(
        val devices: List<PairedDeviceWithState>,
        val isScanning: Boolean = false,
        val isSyncEnabled: Boolean = true,
        val currentLocation: GpsLocation? = null,
    ) : DevicesListState
}
