package dev.sebastiano.ricohsync.devicesync

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.DeadObjectException
import android.os.IBinder
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sebastiano.ricohsync.domain.model.Camera
import dev.sebastiano.ricohsync.domain.model.LocationSyncInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * ViewModel for the device sync screen.
 *
 * Manages the connection to the [DeviceSyncService] and exposes the sync state to the UI.
 */
internal class DeviceSyncViewModel(
    private val camera: Camera,
    private val onDeviceDisconnected: (Camera) -> Unit,
    private val bindingContextProvider: () -> Context,
) : ViewModel() {

    private val _state = mutableStateOf<DeviceSyncState>(DeviceSyncState.Starting)
    val state: State<DeviceSyncState> = _state

    private val serviceBinder = MutableStateFlow<DeviceSyncService.DeviceSyncServiceBinder?>(null)
    private var collectJob: Job? = null

    init {
        connectAndSync()
        serviceBinder
            .onEach { binder -> if (binder != null) onBound(binder) else onUnbound() }
            .flowOn(Dispatchers.Default)
            .launchIn(viewModelScope)
    }

    /** Initiates connection to the camera and starts syncing. */
    fun connectAndSync() {
        startAndBindService(bindingContextProvider)
    }

    private fun startAndBindService(bindingContextProvider: () -> Context) {
        viewModelScope.launch(Dispatchers.Default) {
            val context = bindingContextProvider()
            val intent = Intent(context, DeviceSyncService::class.java)
            context.startService(intent)

            val connection =
                object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                        serviceBinder.value = service as DeviceSyncService.DeviceSyncServiceBinder
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        serviceBinder.value = null
                    }
                }
            context.bindService(intent, connection, 0)
        }
    }

    private fun onBound(binder: DeviceSyncService.DeviceSyncServiceBinder) {
        collectJob =
            viewModelScope.launch(Dispatchers.Default) {
                try {
                    val service = DeviceSyncService.getInstanceFrom(binder)
                    service.connectAndSync(camera)
                    service.state.collect { deviceSyncState ->
                        _state.value = deviceSyncState
                        if (deviceSyncState is DeviceSyncState.Disconnected) {
                            onDeviceDisconnected(deviceSyncState.camera)
                        }
                    }
                } catch (e: DeadObjectException) {
                    onUnbound()
                }
            }
    }

    /** Stops syncing and disconnects from the camera. */
    fun stopSyncAndDisconnect() {
        viewModelScope.launch { serviceBinder.value?.getService()?.stopAndDisconnect() }
    }

    private fun onUnbound() {
        collectJob?.cancel("Service connection died")
        collectJob = null
        _state.value = DeviceSyncState.Stopped
    }
}

/** UI state for the device sync screen. */
internal sealed interface DeviceSyncState {

    /** Service is starting. */
    data object Starting : DeviceSyncState

    /** Connecting to the camera. */
    data class Connecting(val camera: Camera) : DeviceSyncState

    /** Connection lost or failed. */
    data class Disconnected(val camera: Camera) : DeviceSyncState

    /** Connected and syncing. */
    data class Syncing(
        val camera: Camera,
        val firmwareVersion: String?,
        val syncInfo: LocationSyncInfo?,
    ) : DeviceSyncState

    /** Intentionally stopped. */
    data object Stopped : DeviceSyncState
}
