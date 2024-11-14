package dev.sebastiano.ricohsync.devicesync

import android.Manifest
import android.annotation.SuppressLint
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Service
import android.content.Context
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
import com.google.protobuf.kotlin.toByteString
import com.juul.kable.Advertisement
import com.juul.kable.ExperimentalApi
import com.juul.kable.Peripheral
import com.juul.kable.WriteType
import com.juul.kable.logs.Logging
import com.juul.kable.logs.SystemLogEngine
import java.nio.ByteOrder
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.CancellationException
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
import kotlinx.io.IOException
import okio.Buffer

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

                    is State.Disconnected -> DeviceSyncState.Disconnected(currentState.peripheral)
                    is State.Stopped -> DeviceSyncState.Stopped
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

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (intent.action == STOP_INTENT_ACTION) {
            Log.i("RicohSync", "Disconnecting and stopping...")
            launch {
                closeConnection()
                stopSelf()
            }
        } else {
            if (!checkPermissions()) return START_NOT_STICKY
            startForeground()
        }

        return super.onStartCommand(intent, flags, startId)
    }

    private fun startForeground() {
        try {
            ServiceCompat.startForeground(
                /* service = */ this,
                /* id = */ NOTIFICATION_ID, // Cannot be 0
                /* notification = */ createForegroundServiceNotification(this, _state.value),
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
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, TimeUnit.SECONDS.toMillis(10))
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
                Peripheral(advertisement) {
                    logging {
                        level = Logging.Level.Events
                        engine = SystemLogEngine
                        identifier = "RicohCamera"
                    }
                }

            try {
                Log.i("RicohSync", "Connecting to ${advertisement.peripheralName}...")
                peripheral
                    .connect()
                    .launch {
                        Log.i("RicohSync", "Connected to ${advertisement.peripheralName}")
                        readFirmwareVersion(peripheral)
                        setPairedDeviceName(peripheral)

                        _state.value = State.Syncing(peripheral, null, null)
                        updateNotification()

                        toggleGeoTagging(peripheral, enabled = true)
                        try {
                            syncLocationWith(peripheral)
                        } catch (e: CancellationException) {
                            Log.e("RicohSync", "Syncing cancelled: INNER disconnected/cancelled", e)
                            if (_state.value != State.Stopped) {
                                _state.value = State.Disconnected(peripheral)
                                updateNotification()
                            }
                        }
                    }
                    .join()
            } catch (e: IOException) {
                Log.e("RicohSync", "Error:", e)
                _state.value = State.Disconnected(peripheral)
                updateNotification()
            }
        }
        _state.value = State.Connecting(advertisement)
        updateNotification()
    }

    private suspend fun setPairedDeviceName(peripheral: Peripheral) {
        val service =
            peripheral.services.value.orEmpty().first {
                it.serviceUuid.toString() == "0f291746-0c80-4726-87a7-3c501fd3b4b6"
            }

        val char =
            service.characteristics.first {
                it.characteristicUuid.toString() == "fe3a32f8-a189-42de-a391-bc81ae4daa76"
            }

        Log.i("RicohSync", "Setting paired device name...")
        peripheral.write(char, "${Build.MODEL} RicohSync".toByteArray())
    }

    private suspend fun readFirmwareVersion(peripheral: Peripheral) {
        val service =
            peripheral.services.value.orEmpty().first {
                it.serviceUuid.toString() == "9a5ed1c5-74cc-4c50-b5b6-66a48e7ccff1"
            }

        val char =
            service.characteristics.first {
                it.characteristicUuid.toString() == "b4eb8905-7411-40a6-a367-2834c2157ea7"
            }

        val firmwareVersion = peripheral.read(char)
        val versionString = firmwareVersion.toString(Charsets.UTF_8).trimEnd(Char(0))
        Log.i("RicohSync", "Paired device firmware version: $versionString")
    }

    private suspend fun toggleGeoTagging(peripheral: Peripheral, enabled: Boolean) {
        val service =
            peripheral.services.value.orEmpty().first {
                it.serviceUuid.toString() == "4b445988-caa0-4dd3-941d-37b4f52aca86"
            }

        val char =
            service.characteristics.first {
                it.characteristicUuid.toString() == "a36afdcf-6b67-4046-9be7-28fb67dbc071"
            }

        val geoTagEnabled = peripheral.read(char).first()
        Log.i("RicohSync", "Geo-tagging enabled check: ${geoTagEnabled == 1.toByte()}")

        Log.i("RicohSync", "${if (enabled) "Enabling" else "Disabling"} geo-tagging")
        peripheral.write(
            characteristic = char,
            data = ByteArray(1).also { it[0] = if (enabled) 1 else 0 },
            writeType = WriteType.WithResponse,
        )
    }

    private suspend fun syncLocationWith(peripheral: Peripheral) {
        val service =
            peripheral.services.value.orEmpty().first {
                it.serviceUuid.toString() == "84a0dd62-e8aa-4d0f-91db-819b6724c69e"
            }

        val char =
            service.characteristics.first {
                it.characteristicUuid.toString() == "28f59d60-8b8e-4fcd-a81f-61bdb46595a9"
            }

        lastLocation.collect { location ->
            if (location == null) return@collect

            val dateTime =
                ZonedDateTime.ofInstant(Instant.ofEpochMilli(location.time), ZoneId.of("UTC"))
            val dataToWrite = computeLocationDataBytes(location, dateTime)

            Log.i("RicohSync", "Sending latest GPS location...")
            peripheral.write(char, dataToWrite, WriteType.WithResponse)

            _state.update { currentState ->
                val syncingState =
                    currentState as? State.Syncing ?: State.Syncing(peripheral, dateTime, location)

                syncingState.copy(lastSyncTime = ZonedDateTime.now(), lastLocation = location)
            }
            updateNotification()
        }
    }

    private fun computeLocationDataBytes(location: Location, dateTime: ZonedDateTime): ByteArray {
        val buffer =
            Buffer()
                .writeLong(location.latitude.toBits()) // 8 bytes (0)
                .writeLong(location.longitude.toBits()) // 8 bytes (8)
                .writeLong(location.altitude.toBits()) // 8 bytes (16)
                .writeShortLe(dateTime.year) // 2 bytes (24)
                .writeByte(dateTime.monthValue) // 1 byte  (26)
                .writeByte(dateTime.dayOfMonth) // 1 byte  (27)
                .writeByte(dateTime.hour) // 1 byte  (28)
                .writeByte(dateTime.minute) // 1 byte  (29)
                .writeByte(dateTime.second) // 1 byte  (30)
                .writeByte(0) // 1 byte  (31)

        return buffer.readByteArray()
    }

    @OptIn(ExperimentalApi::class)
    private suspend fun closeConnection() {
        val currentState = _state.value
        if (syncJob == null || currentState !is State.Syncing) return

        Log.i("RicohSync", "Setting state to stopped")
        _state.value = State.Stopped

        currentState.peripheral.disconnect()
        currentState.peripheral.cancel("Disconnecting...")
        Log.i("RicohSync", "Disconnected from ${currentState.peripheral.name}")

        syncJob?.cancel("Disconnecting...")
        syncJob = null
    }

    private fun updateNotification() {
        val notification = createForegroundServiceNotification(this, _state.value)
        startForeground(NOTIFICATION_ID, notification)
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

    internal sealed interface State {
        data object Idle : State

        data class Connecting(val advertisement: Advertisement) : State

        data class Syncing(
            val peripheral: Peripheral,
            val lastSyncTime: ZonedDateTime?,
            val lastLocation: Location?,
        ) : State

        data class Disconnected(val peripheral: Peripheral) : State

        data object Stopped : State
    }

    companion object {
        private const val NOTIFICATION_ID = 111
        const val STOP_REQUEST_CODE = 666
        const val STOP_INTENT_ACTION = "disconnect_camera"

        fun getInstanceFrom(binder: Binder) = (binder as DeviceSyncServiceBinder).getService()

        fun createDisconnectIntent(context: Context) =
            Intent(context, DeviceSyncService::class.java).apply {
                action = STOP_INTENT_ACTION
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

        fun asHumanReadableString(bytes: ByteArray): String {
            val buffer = bytes.toByteString().asReadOnlyByteBuffer().order(ByteOrder.BIG_ENDIAN)

            val lat = Double.fromBits(buffer.getLong())
            val lon = Double.fromBits(buffer.getLong())
            val alt = Double.fromBits(buffer.getLong())
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            val year = buffer.getShort().toString().padStart(4, '0')
            buffer.order(ByteOrder.BIG_ENDIAN)
            val month = buffer.get().toString().padStart(2, '0')
            val day = buffer.get().toString().padStart(2, '0')
            val hour = buffer.get().toString().padStart(2, '0')
            val minute = buffer.get().toString().padStart(2, '0')
            val second = buffer.get().toString().padStart(2, '0')

            return "BLE Location data: ($lat, $lon), altitude: $alt. Time: $year-$month-$day $hour:$minute:$second"
        }

        @OptIn(ExperimentalStdlibApi::class)
        fun prettyPrint(bytes: ByteArray) = buildString {
            append(bytes.sliceArray(0..7).toHexString())
            append("_")
            append(bytes.sliceArray(8..15).toHexString())
            append("_")
            append(bytes.sliceArray(16..23).toHexString())
            append("_")
            append(bytes.sliceArray(24..25).toHexString())
            append("_")
            append(bytes[26].toHexString())
            append("_")
            append(bytes[27].toHexString())
            append("_")
            append(bytes[28].toHexString())
            append("_")
            append(bytes[29].toHexString())
            append("_")
            append(bytes[30].toHexString())
            append("_")
            append(bytes[31].toHexString())
        }
    }
}
