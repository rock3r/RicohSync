package dev.sebastiano.ricohsync.domain.model

import java.time.ZonedDateTime

/**
 * Represents a GPS location with coordinates, altitude, and timestamp.
 *
 * This is a domain model decoupled from Android's Location class.
 */
data class GpsLocation(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val accuracy: Float = 0f,
    val timestamp: ZonedDateTime,
)
