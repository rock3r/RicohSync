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
import com.juul.kable.Advertisement
import com.juul.kable.Peripheral
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

internal class DeviceSyncViewModel(
    private val advertisement: Advertisement,
    private val onDeviceDisconnected: (Peripheral) -> Unit,
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
            context.bindService(intent, connection, 0 /* No flags */)
        }
    }

    private fun onBound(binder: DeviceSyncService.DeviceSyncServiceBinder) {
        collectJob =
            viewModelScope.launch(Dispatchers.Default) {
                try {
                    val service = DeviceSyncService.getInstanceFrom(binder)
                    service.connectAndSync(advertisement)
                    service.state.collect {
                        _state.value = it
                        if (it is DeviceSyncState.Disconnected) onDeviceDisconnected(it.peripheral)
                    }
                } catch (e: DeadObjectException) {
                    onUnbound()
                }
            }
    }

    fun stopSyncAndDisconnect() {
        viewModelScope.launch {
            serviceBinder.value?.getService()?.stopAndDisconnect()
        }
    }

    private fun onUnbound() {
        collectJob?.cancel("Service connection died")
        collectJob = null
        _state.value = DeviceSyncState.Stopped
    }
}

internal sealed interface DeviceSyncState {

    data object Starting : DeviceSyncState

    data class Connecting(val advertisement: Advertisement) : DeviceSyncState

    data class Disconnected(val peripheral: Peripheral) : DeviceSyncState

    data class Syncing(
        val peripheral: Peripheral,
        val firmwareVersion: String?,
        val syncInfo: LocationSyncInfo?,
    ) : DeviceSyncState

    data object Stopped : DeviceSyncState
}
