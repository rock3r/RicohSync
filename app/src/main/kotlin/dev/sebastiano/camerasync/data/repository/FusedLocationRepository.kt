package dev.sebastiano.camerasync.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dev.sebastiano.camerasync.domain.model.GpsLocation
import dev.sebastiano.camerasync.domain.repository.LocationRepository
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Implementation of [LocationRepository] using Google Play Services FusedLocationProvider.
 *
 * @param context Application context for accessing location services.
 * @param updateIntervalSeconds Interval between location updates in seconds.
 * @param minUpdateDistanceMeters Minimum distance between updates in meters.
 */
class FusedLocationRepository(
    private val context: Context,
    private val updateIntervalSeconds: Long = DEFAULT_UPDATE_INTERVAL_SECONDS,
    private val minUpdateDistanceMeters: Float = DEFAULT_MIN_UPDATE_DISTANCE_METERS,
) : LocationRepository {

    private val locationClient by lazy { LocationServices.getFusedLocationProviderClient(context) }

    private val _locationUpdates = MutableStateFlow<GpsLocation?>(null)
    override val locationUpdates: Flow<GpsLocation?> = _locationUpdates.asStateFlow()

    private var locationCallback: LocationCallback? = null

    @SuppressLint("MissingPermission") // Permission should be checked by caller
    override fun startLocationUpdates() {
        if (locationCallback != null) return

        val request =
            LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    TimeUnit.SECONDS.toMillis(updateIntervalSeconds),
                )
                .setMinUpdateDistanceMeters(minUpdateDistanceMeters)
                .build()

        val callback =
            object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { location ->
                        _locationUpdates.value = location.toGpsLocation()
                    }
                }
            }

        locationClient
            .requestLocationUpdates(request, callback, Looper.getMainLooper())
            .addOnCanceledListener { locationCallback = null }
            .addOnFailureListener { locationCallback = null }

        locationCallback = callback
    }

    override fun stopLocationUpdates() {
        locationCallback?.let { callback ->
            locationClient.removeLocationUpdates(callback)
            locationCallback = null
        }
    }

    private fun Location.toGpsLocation(): GpsLocation =
        GpsLocation(
            latitude = latitude,
            longitude = longitude,
            altitude = altitude,
            accuracy = accuracy,
            timestamp = ZonedDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.of("UTC")),
        )

    companion object {
        const val DEFAULT_UPDATE_INTERVAL_SECONDS = 30L
        const val DEFAULT_MIN_UPDATE_DISTANCE_METERS = 5f
    }
}
