package dev.sebastiano.ricohsync.fakes

import dev.sebastiano.ricohsync.domain.model.Camera
import dev.sebastiano.ricohsync.domain.model.PairedDevice
import dev.sebastiano.ricohsync.domain.repository.PairedDevicesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Fake implementation of [PairedDevicesRepository] for testing.
 */
class FakePairedDevicesRepository : PairedDevicesRepository {

    private val _devices = MutableStateFlow<List<PairedDevice>>(emptyList())

    override val pairedDevices: Flow<List<PairedDevice>> = _devices

    override val enabledDevices: Flow<List<PairedDevice>> =
        _devices.map { devices -> devices.filter { it.isEnabled } }

    // Tracking for test verification
    var addDeviceCalled = false
        private set
    var removeDeviceCalled = false
        private set
    var setDeviceEnabledCalled = false
        private set
    var lastAddedCamera: Camera? = null
        private set
    var lastRemovedMacAddress: String? = null
        private set

    override suspend fun addDevice(camera: Camera, enabled: Boolean) {
        addDeviceCalled = true
        lastAddedCamera = camera

        val newDevice = PairedDevice(
            macAddress = camera.macAddress,
            name = camera.name,
            vendorId = camera.vendor.vendorId,
            isEnabled = enabled,
        )

        _devices.update { devices ->
            val existingIndex = devices.indexOfFirst { it.macAddress == camera.macAddress }
            if (existingIndex >= 0) {
                devices.toMutableList().apply { set(existingIndex, newDevice) }
            } else {
                devices + newDevice
            }
        }
    }

    override suspend fun removeDevice(macAddress: String) {
        removeDeviceCalled = true
        lastRemovedMacAddress = macAddress

        _devices.update { devices ->
            devices.filter { it.macAddress != macAddress }
        }
    }

    override suspend fun setDeviceEnabled(macAddress: String, enabled: Boolean) {
        setDeviceEnabledCalled = true

        _devices.update { devices ->
            devices.map { device ->
                if (device.macAddress == macAddress) {
                    device.copy(isEnabled = enabled)
                } else {
                    device
                }
            }
        }
    }

    override suspend fun updateDeviceName(macAddress: String, name: String?) {
        _devices.update { devices ->
            devices.map { device ->
                if (device.macAddress == macAddress) {
                    device.copy(name = name)
                } else {
                    device
                }
            }
        }
    }

    override suspend fun isDevicePaired(macAddress: String): Boolean {
        return _devices.value.any { it.macAddress == macAddress }
    }

    override suspend fun getDevice(macAddress: String): PairedDevice? {
        return _devices.value.find { it.macAddress == macAddress }
    }

    override suspend fun hasAnyDevices(): Boolean {
        return _devices.value.isNotEmpty()
    }

    override suspend fun hasEnabledDevices(): Boolean {
        return _devices.value.any { it.isEnabled }
    }

    // Test helpers

    /**
     * Adds a device directly for test setup.
     */
    fun addTestDevice(device: PairedDevice) {
        _devices.update { it + device }
    }

    /**
     * Sets devices directly for test setup.
     */
    fun setTestDevices(devices: List<PairedDevice>) {
        _devices.value = devices
    }

    /**
     * Clears all devices.
     */
    fun clear() {
        _devices.value = emptyList()
    }

    /**
     * Resets all tracking flags.
     */
    fun resetTracking() {
        addDeviceCalled = false
        removeDeviceCalled = false
        setDeviceEnabledCalled = false
        lastAddedCamera = null
        lastRemovedMacAddress = null
    }
}

