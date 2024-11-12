package dev.sebastiano.ricohsync.scanning

import android.bluetooth.le.ScanSettings
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juul.kable.Advertisement
import com.juul.kable.ObsoleteKableApi
import com.juul.kable.PlatformAdvertisement
import com.juul.kable.Scanner
import com.juul.kable.logs.Logging
import com.juul.kable.logs.SystemLogEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart

internal class ScanningViewModel : ViewModel() {
    private val _state = mutableStateOf<PairingState>(PairingState.Loading)
    val state: State<PairingState> = _state

    private var scanJob: Job? = null

    @OptIn(ObsoleteKableApi::class)
    private val scanner = Scanner {
        //        filters {
        //            match {
        //                services = listOf(UUID.fromString("84A0DD62-E8AA-4D0F-91DB-819B6724C69E"))
        //            }
        //        }

        logging {
            engine = SystemLogEngine
            level = Logging.Level.Events
            format = Logging.Format.Multiline
        }

        scanSettings =
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
    }

    init {
        doScan()
    }

    fun doScan() {
        if (scanJob != null) return
        _state.value = PairingState.Scanning(mutableStateMapOf())

        scanJob =
            scanner.advertisements
                .onEach {
                    if (_state.value !is PairingState.Scanning) {
                        _state.value = PairingState.Scanning(mutableStateMapOf())
                    }

                    onDiscovery(it)
                }
                .onStart { Log.i("RicohSync", "BLE scan started") }
                .onCompletion { Log.i("RicohSync", "BLE scan completed") }
                .flowOn(Dispatchers.IO)
                .launchIn(viewModelScope)
    }

    private fun onDiscovery(advertisement: PlatformAdvertisement) {
        val currentState = _state.value as PairingState.Scanning
        currentState.found[advertisement.identifier] = advertisement
    }

    fun stopScan() {
        if (scanJob == null) return
        scanJob?.cancel("Stopping scan")
        scanJob = null

        val previousState = _state.value
        val discoveredDevices =
            if (previousState is PairingState.Scanning) {
                previousState.found
            } else {
                mutableStateMapOf()
            }

        _state.value = PairingState.Done(discoveredDevices)
    }
}

internal sealed interface PairingState {

    data object Loading : PairingState

    data class Scanning(override val found: SnapshotStateMap<String, Advertisement>) :
        PairingState, WithResults

    data class Done(override val found: SnapshotStateMap<String, Advertisement>) :
        PairingState, WithResults

    interface WithResults {
        val found: Map<String, Advertisement>
    }
}
