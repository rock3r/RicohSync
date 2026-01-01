package dev.sebastiano.ricohsync.devicesync

import android.os.Build
import android.util.Log
import dev.sebastiano.ricohsync.domain.model.Camera
import dev.sebastiano.ricohsync.domain.model.DeviceConnectionState
import dev.sebastiano.ricohsync.domain.model.GpsLocation
import dev.sebastiano.ricohsync.domain.model.LocationSyncInfo
import dev.sebastiano.ricohsync.domain.model.PairedDevice
import dev.sebastiano.ricohsync.domain.model.toCamera
import dev.sebastiano.ricohsync.domain.repository.CameraConnection
import dev.sebastiano.ricohsync.domain.repository.CameraRepository
import dev.sebastiano.ricohsync.domain.vendor.CameraVendorRegistry
import java.time.ZonedDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "MultiDeviceSyncCoordinator"

/**
 * Coordinates synchronization with multiple camera devices simultaneously.
 *
 * This coordinator manages:
 * - Multiple concurrent camera connections
 * - Centralized location collection shared across all devices
 * - Per-device connection state
 * - Broadcasting location updates to all connected devices
 *
 * @param cameraRepository Repository for BLE camera operations.
 * @param locationCollector Centralized location collector.
 * @param vendorRegistry Registry for resolving camera vendors.
 * @param coroutineScope Scope for launching coroutines.
 * @param deviceNameProvider Provider for the device name to set on cameras.
 */
class MultiDeviceSyncCoordinator(
    private val cameraRepository: CameraRepository,
    private val locationCollector: LocationCollectionCoordinator,
    private val vendorRegistry: CameraVendorRegistry,
    private val coroutineScope: CoroutineScope,
    private val deviceNameProvider: () -> String = { "${Build.MODEL} RicohSync" },
) {
    private val _deviceStates = MutableStateFlow<Map<String, DeviceConnectionState>>(emptyMap())

    /**
     * Flow of connection states for all managed devices.
     * Key is the device MAC address.
     */
    val deviceStates: StateFlow<Map<String, DeviceConnectionState>> = _deviceStates.asStateFlow()

    private val deviceJobs = mutableMapOf<String, Job>()
    private val deviceConnections = mutableMapOf<String, CameraConnection>()
    private val jobsMutex = Mutex()

    private var locationSyncJob: Job? = null

    /**
     * Starts syncing with a paired device.
     *
     * This will:
     * 1. Connect to the camera
     * 2. Perform initial setup (firmware read, device name, datetime, geo-tagging)
     * 3. Register for location updates
     * 4. Continuously sync location to the camera
     *
     * @param device The paired device to connect to.
     */
    fun startDeviceSync(device: PairedDevice) {
        val macAddress = device.macAddress

        // Check if already syncing synchronously to avoid race conditions
        val alreadySyncing = synchronized(deviceJobs) {
            deviceJobs.containsKey(macAddress)
        }

        if (alreadySyncing) {
            Log.w(TAG, "Device $macAddress already syncing, ignoring")
            return
        }

        val vendor = vendorRegistry.getVendorById(device.vendorId)
        if (vendor == null) {
            Log.e(TAG, "Unknown vendor ${device.vendorId} for device $macAddress")
            updateDeviceState(
                macAddress,
                DeviceConnectionState.Error(
                    message = "Unknown camera vendor",
                    isRecoverable = false,
                ),
            )
            return
        }

        val camera = device.toCamera(vendor)
        startCameraSync(camera)
    }

    /**
     * Starts syncing with a camera.
     */
    private fun startCameraSync(camera: Camera) {
        val macAddress = camera.macAddress

        val job = coroutineScope.launch {
            updateDeviceState(macAddress, DeviceConnectionState.Connecting)

            try {
                val connection = connectToCamera(camera)

                jobsMutex.withLock {
                    deviceConnections[macAddress] = connection
                }

                val firmwareVersion = performInitialSetup(connection)

                updateDeviceState(
                    macAddress,
                    DeviceConnectionState.Syncing(firmwareVersion = firmwareVersion),
                )

                // Register for location updates
                locationCollector.registerDevice(macAddress)

                // Start the global location sync if not already running
                ensureLocationSyncRunning()

                // Wait for cancellation (device will be removed via stopDeviceSync)
                // The location sync is handled globally, not per-device
                awaitCancellation()
            } catch (e: CancellationException) {
                Log.i(TAG, "Sync cancelled for $macAddress")
                cleanup(macAddress, preserveErrorState = false)
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Connection error for $macAddress", e)
                val errorMessage = when {
                    e.message?.contains("pairing", ignoreCase = true) == true ->
                        "Pairing rejected. Enable pairing on your camera."
                    e.message?.contains("timeout", ignoreCase = true) == true ->
                        "Connection timed out. Is the camera nearby?"
                    else -> e.message ?: "Connection failed"
                }
                updateDeviceState(
                    macAddress,
                    DeviceConnectionState.Error(message = errorMessage),
                )
                cleanup(macAddress, preserveErrorState = true)
            }
        }

        // Store the job synchronously to avoid race conditions
        synchronized(deviceJobs) {
            deviceJobs[macAddress] = job
        }
    }

    private suspend fun connectToCamera(camera: Camera): CameraConnection {
        Log.i(TAG, "Connecting to ${camera.name ?: camera.macAddress}...")
        return cameraRepository.connect(camera)
    }

    private suspend fun performInitialSetup(connection: CameraConnection): String {
        val capabilities = connection.camera.vendor.getCapabilities()

        // Read firmware version if supported
        val firmwareVersion = if (capabilities.supportsFirmwareVersion) {
            try {
                connection.readFirmwareVersion()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read firmware version", e)
                null
            }
        } else null

        // Set paired device name if supported
        if (capabilities.supportsDeviceName) {
            try {
                connection.setPairedDeviceName(deviceNameProvider())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set paired device name", e)
            }
        }

        // Sync date/time if supported
        if (capabilities.supportsDateTimeSync) {
            try {
                connection.syncDateTime(ZonedDateTime.now())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync date/time", e)
            }
        }

        // Enable geo-tagging if supported
        if (capabilities.supportsGeoTagging) {
            try {
                connection.setGeoTaggingEnabled(true)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to enable geo-tagging", e)
            }
        }

        return firmwareVersion ?: "Unknown"
    }

    /**
     * Ensures the global location sync job is running.
     * This job broadcasts location updates to all connected devices.
     */
    private fun ensureLocationSyncRunning() {
        if (locationSyncJob != null) return

        locationSyncJob = coroutineScope.launch {
            locationCollector.locationUpdates
                .filterNotNull()
                .collect { location ->
                    syncLocationToAllDevices(location)
                }
        }
    }

    /**
     * Syncs a location update to all connected devices.
     */
    private suspend fun syncLocationToAllDevices(location: GpsLocation) {
        val connections = jobsMutex.withLock { deviceConnections.toMap() }

        connections.forEach { (macAddress, connection) ->
            try {
                if (connection.camera.vendor.getCapabilities().supportsLocationSync) {
                    connection.syncLocation(location)

                    // Update state with sync info
                    updateDeviceState(macAddress) { currentState ->
                        when (currentState) {
                            is DeviceConnectionState.Syncing -> currentState.copy(
                                lastSyncInfo = LocationSyncInfo(
                                    syncTime = ZonedDateTime.now(),
                                    location = location,
                                ),
                            )
                            is DeviceConnectionState.Connected -> DeviceConnectionState.Syncing(
                                firmwareVersion = currentState.firmwareVersion,
                                lastSyncInfo = LocationSyncInfo(
                                    syncTime = ZonedDateTime.now(),
                                    location = location,
                                ),
                            )
                            else -> currentState
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync location to $macAddress", e)
                // Don't update state to error for sync failures - device is still connected
            }
        }
    }

    /**
     * Stops syncing with a specific device.
     *
     * @param macAddress The MAC address of the device to stop.
     */
    suspend fun stopDeviceSync(macAddress: String) {
        Log.i(TAG, "Stopping sync for $macAddress")

        val job = synchronized(deviceJobs) {
            deviceJobs[macAddress]
        }

        if (job != null) {
            job.cancel()
            job.join() // Wait for the finally block to complete
        }
    }

    /**
     * Stops syncing with all devices.
     */
    suspend fun stopAllDevices() {
        Log.i(TAG, "Stopping all device syncs")

        val jobs = synchronized(deviceJobs) {
            deviceJobs.values.toList()
        }

        // Cancel all jobs
        jobs.forEach { it.cancel() }
        // Wait for all to complete
        jobs.forEach { it.join() }

        locationSyncJob?.cancel()
        locationSyncJob = null
    }

    /**
     * Cleans up resources for a device.
     * 
     * @param macAddress The MAC address of the device to clean up.
     * @param preserveErrorState If true, won't update state to Disconnected if currently in Error state.
     */
    private suspend fun cleanup(macAddress: String, preserveErrorState: Boolean = false) {
        // Remove job from tracking
        synchronized(deviceJobs) {
            deviceJobs.remove(macAddress)
        }

        // Disconnect and remove connection
        jobsMutex.withLock {
            deviceConnections[macAddress]?.let { connection ->
                try {
                    connection.disconnect()
                } catch (e: Exception) {
                    Log.w(TAG, "Error disconnecting from $macAddress", e)
                }
            }
            deviceConnections.remove(macAddress)
        }

        // Unregister from location updates
        locationCollector.unregisterDevice(macAddress)

        // Stop location sync if no more devices
        if (locationCollector.getRegisteredDeviceCount() == 0) {
            locationSyncJob?.cancel()
            locationSyncJob = null
        }

        // Update state to disconnected (unless we want to preserve error state)
        val currentState = getDeviceState(macAddress)
        if (!(preserveErrorState && currentState is DeviceConnectionState.Error)) {
            updateDeviceState(macAddress, DeviceConnectionState.Disconnected)
        }
    }

    /**
     * Gets the current connection state for a device.
     */
    fun getDeviceState(macAddress: String): DeviceConnectionState {
        return _deviceStates.value[macAddress] ?: DeviceConnectionState.Disconnected
    }

    /**
     * Checks if a device is currently connected.
     */
    fun isDeviceConnected(macAddress: String): Boolean {
        val state = getDeviceState(macAddress)
        return state is DeviceConnectionState.Connected ||
            state is DeviceConnectionState.Syncing
    }

    /**
     * Gets the count of currently connected devices.
     */
    fun getConnectedDeviceCount(): Int {
        return _deviceStates.value.count { (_, state) ->
            state is DeviceConnectionState.Connected ||
                state is DeviceConnectionState.Syncing
        }
    }

    private fun updateDeviceState(macAddress: String, state: DeviceConnectionState) {
        _deviceStates.update { currentStates ->
            currentStates + (macAddress to state)
        }
    }

    private inline fun updateDeviceState(
        macAddress: String,
        transform: (DeviceConnectionState) -> DeviceConnectionState,
    ) {
        _deviceStates.update { currentStates ->
            val currentState = currentStates[macAddress] ?: DeviceConnectionState.Disconnected
            currentStates + (macAddress to transform(currentState))
        }
    }

    /**
     * Removes a device state entry (used when device is unpaired).
     */
    fun clearDeviceState(macAddress: String) {
        _deviceStates.update { currentStates ->
            currentStates - macAddress
        }
    }

    /**
     * Attempts to reconnect to a device that failed.
     */
    fun retryDeviceConnection(device: PairedDevice) {
        val macAddress = device.macAddress
        val currentState = getDeviceState(macAddress)

        if (currentState is DeviceConnectionState.Error && currentState.isRecoverable) {
            Log.i(TAG, "Retrying connection for $macAddress")
            startDeviceSync(device)
        }
    }

    companion object {
        /**
         * Suspends until cancellation. Used to keep a coroutine alive.
         */
        private suspend fun awaitCancellation(): Nothing {
            while (true) {
                delay(Long.MAX_VALUE)
            }
        }
    }
}
