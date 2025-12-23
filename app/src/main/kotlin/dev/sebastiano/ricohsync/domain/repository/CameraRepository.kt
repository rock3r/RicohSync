package dev.sebastiano.ricohsync.domain.repository

import dev.sebastiano.ricohsync.domain.model.GpsLocation
import dev.sebastiano.ricohsync.domain.model.RicohCamera
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Ricoh camera BLE operations.
 *
 * Abstracts the BLE communication layer to allow for easier testing.
 */
interface CameraRepository {

    /** Flow of discovered cameras during scanning. */
    val discoveredCameras: Flow<RicohCamera>

    /** Starts scanning for Ricoh cameras. */
    fun startScanning()

    /** Stops scanning for Ricoh cameras. */
    fun stopScanning()

    /**
     * Scans for a specific camera by MAC address and returns it when found.
     *
     * @return Flow that emits the camera when found.
     */
    fun findCameraByMacAddress(macAddress: String): Flow<RicohCamera>

    /**
     * Connects to the specified camera.
     *
     * @return A [CameraConnection] for interacting with the connected camera.
     */
    suspend fun connect(camera: RicohCamera): CameraConnection
}

/**
 * Represents an active connection to a Ricoh camera.
 *
 * This interface provides methods to interact with a connected camera.
 */
interface CameraConnection {

    /** The camera this connection is for. */
    val camera: RicohCamera

    /** Reads the camera's firmware version. */
    suspend fun readFirmwareVersion(): String

    /** Sets the paired device name on the camera. */
    suspend fun setPairedDeviceName(name: String)

    /** Syncs the current date/time to the camera. */
    suspend fun syncDateTime(dateTime: ZonedDateTime)

    /** Reads the current date/time from the camera (for debugging). */
    suspend fun readDateTime(): ByteArray

    /** Enables or disables geo-tagging on the camera. */
    suspend fun setGeoTaggingEnabled(enabled: Boolean)

    /** Reads whether geo-tagging is currently enabled. */
    suspend fun isGeoTaggingEnabled(): Boolean

    /** Syncs a GPS location to the camera. */
    suspend fun syncLocation(location: GpsLocation)

    /** Disconnects from the camera. */
    suspend fun disconnect()
}
