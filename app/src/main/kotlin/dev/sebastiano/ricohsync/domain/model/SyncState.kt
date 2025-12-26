package dev.sebastiano.ricohsync.domain.model

import java.time.ZonedDateTime

/** Represents the state of the camera synchronization process. */
sealed interface SyncState {

    /** Initial state before any connection attempt. */
    data object Idle : SyncState

    /** Currently connecting to the camera. */
    data class Connecting(val camera: Camera) : SyncState

    /** Connected and actively syncing data. */
    data class Syncing(
        val camera: Camera,
        val firmwareVersion: String?,
        val lastSyncInfo: LocationSyncInfo?,
    ) : SyncState

    /** Connection was lost or failed. */
    data class Disconnected(val camera: Camera) : SyncState

    /** Intentionally stopped by user. */
    data object Stopped : SyncState
}

/** Information about the last successful location sync. */
data class LocationSyncInfo(
    val syncTime: ZonedDateTime,
    val location: GpsLocation,
)
