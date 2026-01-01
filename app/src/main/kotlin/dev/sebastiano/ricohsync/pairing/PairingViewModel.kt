package dev.sebastiano.ricohsync.pairing

import android.bluetooth.le.ScanSettings
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juul.kable.ObsoleteKableApi
import com.juul.kable.PlatformAdvertisement
import com.juul.kable.Scanner
import com.juul.kable.logs.Logging
import com.juul.kable.logs.SystemLogEngine
import dev.sebastiano.ricohsync.RicohSyncApp
import dev.sebastiano.ricohsync.domain.model.Camera
import dev.sebastiano.ricohsync.domain.repository.PairedDevicesRepository
import dev.sebastiano.ricohsync.domain.vendor.CameraVendorRegistry
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

private const val TAG = "PairingViewModel"

/**
 * ViewModel for the pairing screen.
 *
 * Manages BLE scanning for supported cameras and handles the pairing process. Excludes
 * already-paired devices from the scan results.
 */
@OptIn(ExperimentalUuidApi::class)
class PairingViewModel(private val pairedDevicesRepository: PairedDevicesRepository) : ViewModel() {

    private val _state = mutableStateOf<PairingScreenState>(PairingScreenState.Idle)
    val state: State<PairingScreenState> = _state

    private var scanJob: Job? = null
    private var pairingJob: Job? = null

    private val vendorRegistry: CameraVendorRegistry = RicohSyncApp.createVendorRegistry()

    @OptIn(ObsoleteKableApi::class)
    private val scanner = Scanner {
        // We don't use filters here because some cameras might not advertise
        // the service UUID in the advertisement packet.
        // We filter discovered devices in onDiscovery instead.
        logging {
            engine = SystemLogEngine
            level = Logging.Level.Events
            format = Logging.Format.Multiline
        }
        scanSettings =
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
    }

    private val discoveredDevices = mutableMapOf<String, Camera>()

    /** Starts scanning for cameras. */
    fun startScanning() {
        if (scanJob != null) return

        discoveredDevices.clear()
        _state.value = PairingScreenState.Scanning(emptyList())

        scanJob =
            scanner.advertisements
                .onEach { advertisement -> onDiscovery(advertisement) }
                .onStart { Log.i(TAG, "BLE scan started") }
                .onCompletion { Log.i(TAG, "BLE scan completed") }
                .flowOn(Dispatchers.IO)
                .launchIn(viewModelScope)
    }

    /** Stops the current scan. */
    fun stopScanning() {
        scanJob?.cancel("Stopping scan")
        scanJob = null

        val currentState = _state.value
        if (currentState is PairingScreenState.Scanning) {
            _state.value = currentState.copy(isScanning = false)
        }
    }

    private suspend fun onDiscovery(advertisement: PlatformAdvertisement) {
        val camera = advertisement.toCamera() ?: return

        // Check if already paired
        if (pairedDevicesRepository.isDevicePaired(camera.macAddress)) {
            Log.d(TAG, "Device ${camera.macAddress} already paired, skipping")
            return
        }

        discoveredDevices[camera.macAddress] = camera

        val currentState = _state.value
        if (currentState is PairingScreenState.Scanning) {
            _state.value =
                PairingScreenState.Scanning(
                    discoveredDevices =
                        discoveredDevices.values.toList().sortedByDescending { it.name }
                )
        }
    }

    /** Starts pairing with the selected camera. */
    fun pairDevice(camera: Camera) {
        stopScanning()
        _state.value = PairingScreenState.Pairing(camera)

        pairingJob =
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    // Add device to repository (this is the "pairing" - we store the device)
                    // The actual BLE connection will happen when the device is enabled
                    pairedDevicesRepository.addDevice(camera, enabled = true)

                    Log.i(TAG, "Device paired successfully: ${camera.name ?: camera.macAddress}")
                    _state.value = PairingScreenState.Pairing(camera, success = true)
                } catch (e: Exception) {
                    Log.e(TAG, "Pairing failed", e)
                    val error =
                        when {
                            e.message?.contains("reject", ignoreCase = true) == true ->
                                PairingError.REJECTED
                            e.message?.contains("timeout", ignoreCase = true) == true ->
                                PairingError.TIMEOUT
                            else -> PairingError.UNKNOWN
                        }
                    _state.value = PairingScreenState.Pairing(camera, error = error)
                }
            }
    }

    /** Cancels the current pairing attempt. */
    fun cancelPairing() {
        pairingJob?.cancel()
        pairingJob = null
        _state.value = PairingScreenState.Idle
    }

    /** Converts a BLE advertisement to a Camera by identifying the vendor. */
    private fun PlatformAdvertisement.toCamera(): Camera? {
        val vendor =
            vendorRegistry.identifyVendor(deviceName = peripheralName ?: name, serviceUuids = uuids)

        if (vendor == null) {
            Log.w(TAG, "No vendor recognized for device: ${peripheralName ?: name}")
            return null
        }

        Log.i(TAG, "Discovered ${vendor.vendorName} camera: ${peripheralName ?: name}")
        return Camera(
            identifier = identifier,
            name = peripheralName ?: name,
            macAddress = identifier,
            vendor = vendor,
        )
    }

    override fun onCleared() {
        super.onCleared()
        stopScanning()
        pairingJob?.cancel()
    }
}

/** State of the pairing screen. */
sealed interface PairingScreenState {
    /** Initial state, ready to scan. */
    data object Idle : PairingScreenState

    /** Actively scanning for cameras. */
    data class Scanning(val discoveredDevices: List<Camera>, val isScanning: Boolean = true) :
        PairingScreenState

    /** Pairing with a selected camera. */
    data class Pairing(
        val camera: Camera,
        val error: PairingError? = null,
        val success: Boolean = false,
    ) : PairingScreenState
}
