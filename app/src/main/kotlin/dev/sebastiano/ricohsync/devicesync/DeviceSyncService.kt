package dev.sebastiano.ricohsync.devicesync

import android.Manifest
import android.annotation.SuppressLint
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.PermissionChecker
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.juul.kable.Advertisement
import com.juul.kable.Peripheral
import com.juul.kable.WriteType
import com.juul.kable.characteristicOf
import com.juul.kable.logs.Logging
import com.juul.kable.logs.SystemLogEngine
import com.juul.kable.peripheral
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class DeviceSyncService : Service(), CoroutineScope {
    override val coroutineContext: CoroutineContext =
        Dispatchers.IO + CoroutineName("DeviceSyncService") + SupervisorJob()

    private val binder by lazy { DeviceSyncServiceBinder() }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: Flow<DeviceSyncState> =
        _state
            .map { currentState ->
                when (currentState) {
                    State.Idle -> DeviceSyncState.Starting
                    is State.Connecting -> DeviceSyncState.Connecting(currentState.advertisement)
                    is State.Syncing ->
                        DeviceSyncState.Syncing(
                            currentState.peripheral,
                            currentState.lastSyncTime,
                            currentState.lastLocation,
                        )
                }
            }
            .shareIn(this, SharingStarted.WhileSubscribed())

    private var syncJob: Job? = null
    private var locationCallback: LocationCallback? = null
    private val lastLocation = MutableStateFlow<Location?>(null)

    override fun onBind(intent: Intent): IBinder {
        if (!checkPermissions()) throw RuntimeException("Some permission missing")
        startCollectingLocation()
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!checkPermissions()) return START_NOT_STICKY
        startForeground()
        return super.onStartCommand(intent, flags, startId)
    }

    private fun startForeground() {
        try {
            ServiceCompat.startForeground(
                /* service = */ this,
                /* id = */ 100, // Cannot be 0
                /* notification = */ createForegroundServiceNotification(this),
                /* foregroundServiceType = */
                // ktfmt
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } catch (e: Exception) {
            if (e is ForegroundServiceStartNotAllowedException) {
                Log.e("RicohSync", "Can't run foreground services")
            }
        }
    }

    private fun checkPermissions(): Boolean {
        val locationPermission =
            PermissionChecker.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)

        if (locationPermission != PermissionChecker.PERMISSION_GRANTED) {
            stopAndNotify(Manifest.permission.ACCESS_FINE_LOCATION)
            return false
        }

        val bluetoothScanPermission =
            PermissionChecker.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
        if (bluetoothScanPermission != PermissionChecker.PERMISSION_GRANTED) {
            stopAndNotify(Manifest.permission.BLUETOOTH_SCAN)
            return false
        }

        val bluetoothConnectPermission =
            PermissionChecker.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
        if (bluetoothConnectPermission != PermissionChecker.PERMISSION_GRANTED) {
            stopAndNotify(Manifest.permission.BLUETOOTH_CONNECT)
            return false
        }

        val notificationsPermission =
            PermissionChecker.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
        if (notificationsPermission != PermissionChecker.PERMISSION_GRANTED) {
            stopAndNotify(Manifest.permission.POST_NOTIFICATIONS)
            return false
        }

        return true
    }

    private fun stopAndNotify(missingPermission: String) {
        stopSelf()

        val notification =
            createErrorNotificationBuilder(this)
                .setContentTitle("Missing permission")
                .setContentText("Can't sync with camera because $missingPermission is missing")
                .build()

        val notificationsPermission =
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)

        if (notificationsPermission != PackageManager.PERMISSION_GRANTED) {
            Log.e(
                /* tag = */ "RicohSync",
                /* msg = */ "Can't show error for missing permission:$missingPermission",
            )
            return
        }
        NotificationManagerCompat.from(this).notify(123, notification)
    }

    @SuppressLint("MissingPermission") // Checked before calling this
    private fun startCollectingLocation() {
        if (locationCallback != null) {
            return
        }

        val locationClient = LocationServices.getFusedLocationProviderClient(this)
        val request =
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, TimeUnit.SECONDS.toMillis(30))
                .setMinUpdateDistanceMeters(5f)
                .build()

        val callback: LocationCallback =
            object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    Log.d("RicohSync", "Location updated: ${result.lastLocation}")
                    lastLocation.value = result.lastLocation
                }
            }

        locationClient
            .requestLocationUpdates(request, callback, Looper.myLooper())
            .addOnCanceledListener { locationCallback = null }
            .addOnFailureListener { locationCallback = null }

        locationCallback = callback
    }

    fun connectAndSync(advertisement: Advertisement) {
        syncJob = launch {
            val peripheral =
                peripheral(advertisement) {
                    logging {
                        level = Logging.Level.Events
                        engine = SystemLogEngine
                        identifier = "RicohCamera"
                    }
                }

            try {
                peripheral.connect()
                setPairedDeviceName(peripheral)
                readFirmwareVersion(peripheral)

                _state.value = State.Syncing(peripheral, null, null)
                syncLocationAndTimeWith(peripheral)
            } catch (e: Exception) {
                Log.e("Ricooola", "Error:", e)
            }
        }
        _state.value = State.Connecting(advertisement)
    }

    private suspend fun setPairedDeviceName(peripheral: Peripheral) {
        val service =
            peripheral.services.orEmpty().first {
                it.serviceUuid.toString() == "0f291746-0c80-4726-87a7-3c501fd3b4b6"
            }

        val char =
            service.characteristics.first {
                it.characteristicUuid.toString() == "fe3a32f8-a189-42de-a391-bc81ae4daa76"
            }

        peripheral.write(char, "${Build.MODEL} RicohSync".toByteArray())
    }

    private suspend fun readFirmwareVersion(peripheral: Peripheral) {
        val service =
            peripheral.services.orEmpty().first {
                it.serviceUuid.toString() == "9a5ed1c5-74cc-4c50-b5b6-66a48e7ccff1"
            }

        val char =
            service.characteristics.first {
                it.characteristicUuid.toString() == "b4eb8905-7411-40a6-a367-2834c2157ea7"
            }

        val firmwareVersion = peripheral.read(char)
        println("Firmware version: ${firmwareVersion.toString(Charsets.UTF_8).trimEnd(Char(0))}")
    }

    private suspend fun syncLocationAndTimeWith(peripheral: Peripheral) {
        lastLocation.collect { location ->
            if (location == null) return@collect

            val dateTime = ZonedDateTime.now()
            val dataToWrite = computeSyncDataBytes(location, dateTime)
            peripheral.write(SyncServiceCharacteristic, dataToWrite, WriteType.WithResponse)
            _state.update { currentState ->
                val syncingState =
                    currentState as? State.Syncing ?: State.Syncing(peripheral, dateTime, location)

                syncingState.copy(lastSyncTime = dateTime, lastLocation = location)
            }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun computeSyncDataBytes(location: Location, dateTime: ZonedDateTime): ByteArray {
        val buffer =
            ByteBuffer.allocate(80).order(ByteOrder.LITTLE_ENDIAN) // Assuming little-endianness

        // Latitude and Longitude (Float64, 8 bytes each)
        buffer.putFloat(location.latitude.toFloat())
        buffer.putFloat(location.longitude.toFloat())

        // Altitude (Float64, 8 bytes) with range check
        buffer.putFloat(location.altitude.coerceIn(-9999.0, 9999.0).toFloat())

        // Year (Short, 2 bytes)
        buffer.putShort(dateTime.year.toShort())

        // Month, Day, Hours, Minutes, Seconds (Byte, 1 byte each)
        buffer.put(dateTime.monthValue.toByte())
        buffer.put(dateTime.dayOfMonth.toByte())
        buffer.put(dateTime.hour.toByte())
        buffer.put(dateTime.minute.toByte())
        buffer.put(dateTime.second.toByte())

        // Time Zone (String, 6 bytes + null terminator)
        val timeZone = formatZoneOffset(dateTime.offset)
        buffer.put(timeZone.toByteArray(Charsets.UTF_8))

        // Datum (Byte, 1 byte)
        buffer.put(0.toByte()) // Assuming WGS84

        Log.i("RicohSync", "Computed data (${buffer.array().size} bytes): ${buffer.array()}")

        return buffer.array()
    }

    private fun formatZoneOffset(offset: ZoneOffset): String {
        val formatter = DateTimeFormatter.ofPattern("xxx") // "xxx" pattern for ZoneOffset
        return formatter.format(offset)
    }

    fun disconnect() {
        val currentState = _state.value
        if (syncJob == null || currentState !is State.Syncing) return
        syncJob?.cancel("Disconnecting...")
        syncJob = null

        launch {
            currentState.peripheral.disconnect()
            _state.value = State.Idle
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        val callback = locationCallback
        if (callback != null) {
            val locationClient = LocationServices.getFusedLocationProviderClient(this)
            locationClient.removeLocationUpdates(callback)
            locationCallback = null
        }
        return super.onUnbind(intent)
    }

    inner class DeviceSyncServiceBinder : Binder() {
        fun getService() = this@DeviceSyncService
    }

    private sealed interface State {
        data object Idle : State

        data class Connecting(val advertisement: Advertisement) : State

        data class Syncing(
            val peripheral: Peripheral,
            val lastSyncTime: ZonedDateTime?,
            val lastLocation: Location?,
        ) : State
    }

    companion object {
        fun getInstanceFrom(binder: Binder) = (binder as DeviceSyncServiceBinder).getService()

        private val SyncServiceCharacteristic =
            characteristicOf(
                "84A0DD62-E8AA-4D0F-91DB-819B6724C69E",
                "28F59D60-8B8E-4FCD-A81F-61BDB46595A9",
            )
    }
}
