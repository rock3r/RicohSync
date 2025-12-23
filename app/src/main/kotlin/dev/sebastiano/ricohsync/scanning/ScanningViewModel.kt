package dev.sebastiano.ricohsync.scanning

import android.bluetooth.le.ScanSettings
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juul.kable.ObsoleteKableApi
import com.juul.kable.PlatformAdvertisement
import com.juul.kable.Scanner
import com.juul.kable.logs.Logging
import com.juul.kable.logs.SystemLogEngine
import dev.sebastiano.ricohsync.ble.RicohGattSpec
import dev.sebastiano.ricohsync.domain.model.RicohCamera
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart

private const val TAG = "ScanningViewModel"

/**
 * ViewModel for the camera scanning screen.
 *
 * Manages BLE scanning for Ricoh cameras and exposes discovered devices to the UI.
 */
internal class ScanningViewModel : ViewModel() {

    private val _state = mutableStateOf<PairingState>(PairingState.Loading)
    val state: State<PairingState> = _state

    private var scanJob: Job? = null

    @OptIn(ObsoleteKableApi::class)
    private val scanner = Scanner {
        filters {
            match { services = listOf(RicohGattSpec.SCAN_FILTER_SERVICE_UUID) }
        }
        logging {
            engine = SystemLogEngine
            level = Logging.Level.Events
            format = Logging.Format.Multiline
        }
        scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
    }

    init {
        doScan()
    }

    /** Starts scanning for Ricoh cameras. */
    fun doScan() {
        if (scanJob != null) return
        _state.value = PairingState.Scanning(mutableStateMapOf())

        scanJob = scanner.advertisements
            .onEach { advertisement ->
                if (_state.value !is PairingState.Scanning) {
                    _state.value = PairingState.Scanning(mutableStateMapOf())
                }
                onDiscovery(advertisement)
            }
            .onStart { Log.i(TAG, "BLE scan started") }
            .onCompletion { Log.i(TAG, "BLE scan completed") }
            .flowOn(Dispatchers.IO)
            .launchIn(viewModelScope)
    }

    private fun onDiscovery(advertisement: PlatformAdvertisement) {
        val currentState = _state.value as PairingState.Scanning
        currentState.found[advertisement.identifier] = advertisement.toRicohCamera()
    }

    /** Stops the current scan. */
    fun stopScan() {
        if (scanJob == null) return
        scanJob?.cancel("Stopping scan")
        scanJob = null

        val previousState = _state.value
        val discoveredDevices = if (previousState is PairingState.Scanning) {
            previousState.found
        } else {
            mutableStateMapOf()
        }

        _state.value = PairingState.Done(discoveredDevices)
    }

    private fun PlatformAdvertisement.toRicohCamera(): RicohCamera = RicohCamera(
        identifier = identifier,
        name = peripheralName ?: name,
        macAddress = identifier,
    )
}

/** State of the camera pairing/scanning process. */
internal sealed interface PairingState {

    /** Initial loading state. */
    data object Loading : PairingState

    /** Actively scanning for cameras. */
    data class Scanning(override val found: SnapshotStateMap<String, RicohCamera>) :
        PairingState, WithResults

    /** Scanning completed. */
    data class Done(override val found: SnapshotStateMap<String, RicohCamera>) :
        PairingState, WithResults

    /** Interface for states that have scan results. */
    interface WithResults {
        val found: Map<String, RicohCamera>
    }
}
