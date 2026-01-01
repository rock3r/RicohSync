package dev.sebastiano.ricohsync.devicesync

import android.util.Log
import dev.sebastiano.ricohsync.domain.model.GpsLocation
import dev.sebastiano.ricohsync.domain.repository.LocationRepository
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "LocationCollector"

/**
 * Default implementation of [LocationCollectionCoordinator].
 *
 * Manages location collection lifecycle based on registered devices. Location updates are collected
 * from a [LocationRepository] and exposed to all registered consumers.
 *
 * @param locationRepository The repository providing location updates.
 * @param coroutineScope Scope for launching collection coroutines.
 */
class DefaultLocationCollector(
    private val locationRepository: LocationRepository,
    private val coroutineScope: CoroutineScope,
) : LocationCollectionCoordinator {

    private val _locationUpdates = MutableStateFlow<GpsLocation?>(null)
    override val locationUpdates: StateFlow<GpsLocation?> = _locationUpdates.asStateFlow()

    private val _isCollecting = MutableStateFlow(false)
    override val isCollecting: StateFlow<Boolean> = _isCollecting.asStateFlow()

    private val registeredDevices = ConcurrentHashMap.newKeySet<String>()
    private var collectionJob: Job? = null

    override fun startCollecting() {
        if (collectionJob != null) {
            Log.d(TAG, "Location collection already active")
            return
        }

        Log.i(TAG, "Starting location collection")
        _isCollecting.value = true
        locationRepository.startLocationUpdates()

        collectionJob =
            coroutineScope.launch {
                locationRepository.locationUpdates.collect { location ->
                    _locationUpdates.value = location
                    if (location != null) {
                        Log.d(TAG, "New location: ${location.latitude}, ${location.longitude}")
                    }
                }
            }
    }

    override fun stopCollecting() {
        Log.i(TAG, "Stopping location collection")
        collectionJob?.cancel()
        collectionJob = null
        locationRepository.stopLocationUpdates()
        _isCollecting.value = false
    }

    override fun registerDevice(deviceId: String) {
        val wasEmpty = registeredDevices.isEmpty()
        registeredDevices.add(deviceId)
        Log.i(TAG, "Device registered: $deviceId (total: ${registeredDevices.size})")

        if (wasEmpty) {
            startCollecting()
        }
    }

    override fun unregisterDevice(deviceId: String) {
        registeredDevices.remove(deviceId)
        Log.i(TAG, "Device unregistered: $deviceId (total: ${registeredDevices.size})")

        if (registeredDevices.isEmpty()) {
            stopCollecting()
        }
    }

    override fun getRegisteredDeviceCount(): Int = registeredDevices.size
}
