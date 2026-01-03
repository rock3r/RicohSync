package dev.sebastiano.ricohsync.devicesync

import android.os.Build
import com.juul.khronicle.Log
import dev.sebastiano.ricohsync.domain.model.Camera
import dev.sebastiano.ricohsync.domain.model.GpsLocation
import dev.sebastiano.ricohsync.domain.model.LocationSyncInfo
import dev.sebastiano.ricohsync.domain.model.SyncState
import dev.sebastiano.ricohsync.domain.repository.CameraConnection
import dev.sebastiano.ricohsync.domain.repository.CameraRepository
import dev.sebastiano.ricohsync.domain.repository.LocationRepository
import dev.sebastiano.ricohsync.domain.repository.PairedDevicesRepository
import java.time.ZonedDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "SyncCoordinator"

/**
 * Coordinates the synchronization process between the phone and Ricoh camera.
 *
 * This class contains the core sync business logic, decoupled from Android services for easier
 * testing.
 *
 * @param cameraRepository Repository for camera BLE operations.
 * @param locationRepository Repository for GPS location updates.
 * @param pairedDevicesRepository Repository for managing paired devices.
 * @param coroutineScope Scope for launching coroutines.
 * @param deviceNameProvider Provider for the device name to set on the camera.
 */
class SyncCoordinator(
    private val cameraRepository: CameraRepository,
    private val locationRepository: LocationRepository,
    private val pairedDevicesRepository: PairedDevicesRepository,
    private val coroutineScope: CoroutineScope,
    private val deviceNameProvider: () -> String = { "${Build.MODEL} RicohSync" },
) {
    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    private var syncJob: Job? = null
    private var currentConnection: CameraConnection? = null

    /**
     * Starts the sync process with the specified camera.
     *
     * This will:
     * 1. Connect to the camera
     * 2. Read firmware version
     * 3. Set the paired device name
     * 4. Sync current date/time
     * 5. Enable geo-tagging
     * 6. Continuously sync GPS location
     */
    fun startSync(camera: Camera) {
        if (syncJob != null) {
            Log.warn(tag = TAG) { "Sync already in progress, ignoring startSync call" }
            return
        }

        _state.value = SyncState.Connecting(camera)

        syncJob =
            coroutineScope.launch {
                try {
                    val connection = cameraRepository.connect(camera)
                    currentConnection = connection

                    val firmwareVersion = performInitialSetup(connection)

                    _state.value =
                        SyncState.Syncing(
                            camera = camera,
                            firmwareVersion = firmwareVersion,
                            lastSyncInfo = null,
                        )

                    startLocationSync(connection, camera, firmwareVersion)
                } catch (e: CancellationException) {
                    Log.info(tag = TAG) { "Sync cancelled" }
                    throw e
                } catch (e: Exception) {
                    Log.error(tag = TAG, throwable = e) { "Sync error" }
                    _state.value = SyncState.Disconnected(camera)
                } finally {
                    currentConnection?.disconnect()
                    currentConnection = null
                    syncJob = null
                }
            }
    }

    /**
     * Performs the initial setup after connecting to the camera.
     *
     * @return The firmware version of the camera.
     */
    private suspend fun performInitialSetup(connection: CameraConnection): String {
        // Read current camera date/time (for logging)
        connection.readDateTime()

        // Read firmware version
        val firmwareVersion = connection.readFirmwareVersion()

        // Set paired device name
        connection.setPairedDeviceName(deviceNameProvider())

        // Sync date/time
        connection.syncDateTime(ZonedDateTime.now())

        // Enable geo-tagging
        connection.setGeoTaggingEnabled(true)

        return firmwareVersion
    }

    /** Starts continuous location sync with the camera. */
    private suspend fun startLocationSync(
        connection: CameraConnection,
        camera: Camera,
        firmwareVersion: String,
    ) {
        locationRepository.startLocationUpdates()

        val connectionMonitoringJob =
            coroutineScope.launch {
                connection.isConnected.filter { !it }.first()
                Log.info(tag = TAG) { "Connection lost for ${camera.macAddress}" }
                syncJob?.cancel(CancellationException("Connection lost"))
            }

        try {
            locationRepository.locationUpdates.filterNotNull().collect { location ->
                syncLocationToCamera(connection, location, camera, firmwareVersion)
            }
        } catch (e: CancellationException) {
            Log.info(tag = TAG) { "Location sync cancelled" }
            if (_state.value != SyncState.Stopped) {
                _state.value = SyncState.Disconnected(camera)
            }
            throw e
        } finally {
            connectionMonitoringJob.cancel()
            locationRepository.stopLocationUpdates()
        }
    }

    private suspend fun syncLocationToCamera(
        connection: CameraConnection,
        location: GpsLocation,
        camera: Camera,
        firmwareVersion: String,
    ) {
        connection.syncLocation(location)

        // Update persistent last sync timestamp
        pairedDevicesRepository.updateLastSyncedAt(camera.macAddress, System.currentTimeMillis())

        _state.update { currentState ->
            val syncing =
                currentState as? SyncState.Syncing
                    ?: SyncState.Syncing(camera, firmwareVersion, null)

            syncing.copy(
                lastSyncInfo = LocationSyncInfo(syncTime = ZonedDateTime.now(), location = location)
            )
        }
    }

    /** Stops syncing and disconnects from the camera. */
    suspend fun stopSync() {
        Log.info(tag = TAG) { "Stopping sync" }
        _state.value = SyncState.Stopped

        // Cancel and wait for the sync job to complete
        // The job's cancellation will trigger the finally block in startLocationSync
        // which will call stopLocationUpdates()
        syncJob?.let { job ->
            job.cancel()
            job.join()
        }
        syncJob = null

        currentConnection?.disconnect()
        currentConnection = null

        // Stop location updates in case no job was running
        locationRepository.stopLocationUpdates()
    }

    /** Checks if a sync is currently in progress. */
    fun isSyncing(): Boolean = syncJob != null

    /**
     * Finds a camera by MAC address and starts syncing with it.
     *
     * @param macAddress The MAC address of the camera to find.
     */
    suspend fun findAndSync(macAddress: String) {
        val camera = cameraRepository.findCameraByMacAddress(macAddress).first()
        startSync(camera)
    }
}
