package dev.sebastiano.ricohsync.fakes

import dev.sebastiano.ricohsync.devicesync.LocationCollectionCoordinator
import dev.sebastiano.ricohsync.domain.model.GpsLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fake implementation of [LocationCollectionCoordinator] for testing.
 */
class FakeLocationCollector : LocationCollectionCoordinator {

    private val _locationUpdates = MutableStateFlow<GpsLocation?>(null)
    override val locationUpdates: StateFlow<GpsLocation?> = _locationUpdates.asStateFlow()

    private val _isCollecting = MutableStateFlow(false)
    override val isCollecting: StateFlow<Boolean> = _isCollecting.asStateFlow()

    private val registeredDevices = mutableSetOf<String>()

    // Tracking for test verification
    var startCollectingCalled = false
        private set
    var stopCollectingCalled = false
        private set
    var registerDeviceCalls = mutableListOf<String>()
        private set
    var unregisterDeviceCalls = mutableListOf<String>()
        private set

    override fun startCollecting() {
        startCollectingCalled = true
        _isCollecting.value = true
    }

    override fun stopCollecting() {
        stopCollectingCalled = true
        _isCollecting.value = false
    }

    override fun registerDevice(deviceId: String) {
        registerDeviceCalls.add(deviceId)
        registeredDevices.add(deviceId)
        if (!_isCollecting.value) {
            startCollecting()
        }
    }

    override fun unregisterDevice(deviceId: String) {
        unregisterDeviceCalls.add(deviceId)
        registeredDevices.remove(deviceId)
        if (registeredDevices.isEmpty()) {
            stopCollecting()
        }
    }

    override fun getRegisteredDeviceCount(): Int = registeredDevices.size

    // Test helpers

    /**
     * Emits a location update for testing.
     */
    fun emitLocation(location: GpsLocation?) {
        _locationUpdates.value = location
    }

    /**
     * Gets the current set of registered devices.
     */
    fun getRegisteredDevices(): Set<String> = registeredDevices.toSet()

    /**
     * Resets all tracking and state.
     */
    fun reset() {
        startCollectingCalled = false
        stopCollectingCalled = false
        registerDeviceCalls.clear()
        unregisterDeviceCalls.clear()
        registeredDevices.clear()
        _isCollecting.value = false
        _locationUpdates.value = null
    }
}

