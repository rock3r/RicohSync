package dev.sebastiano.ricohsync

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.BLUETOOTH_SCAN
import android.Manifest.permission.POST_NOTIFICATIONS
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionsRequester(
    onPermissionsGranted: () -> Unit,
    content:
        @Composable
        (
            isLocationPermissionGranted: Boolean,
            isBluetoothScanPermissionGranted: Boolean,
            isBluetoothConnectPermissionGranted: Boolean,
            isNotificationsPermissionGranted: Boolean,
            onRequestPermissions: () -> Unit,
        ) -> Unit,
) {
    val locationPermissionState = rememberPermissionState(permission = ACCESS_FINE_LOCATION) {}
    val bluetoothScanPermissionState = rememberPermissionState(permission = BLUETOOTH_SCAN) {}
    val bluetoothConnectPermissionState = rememberPermissionState(permission = BLUETOOTH_CONNECT) {}
    val notificationsPermissionsState: PermissionState =
        rememberPermissionState(permission = POST_NOTIFICATIONS) {}

    LaunchedEffect(
        locationPermissionState,
        bluetoothScanPermissionState,
        bluetoothConnectPermissionState,
        notificationsPermissionsState,
    ) {
        if (
            locationPermissionState.status.isGranted &&
                bluetoothScanPermissionState.status.isGranted &&
                bluetoothConnectPermissionState.status.isGranted &&
                notificationsPermissionsState.status.isGranted
        ) {
            onPermissionsGranted()
        }
    }

    content(
        /* isLocationPermissionGranted */ locationPermissionState.status.isGranted,
        /* isBluetoothScanPermissionGranted */ bluetoothScanPermissionState.status.isGranted,
        /* isBluetoothConnectPermissionGranted */ bluetoothConnectPermissionState.status.isGranted,
        /* isNotificationsPermissionGranted */ notificationsPermissionsState.status.isGranted,
    ) /* onRequestPermissions */ {
        onRequestPermissions(
            locationPermissionState,
            bluetoothScanPermissionState,
            bluetoothConnectPermissionState,
            notificationsPermissionsState,
            onPermissionsGranted,
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
private fun onRequestPermissions(
    bluetoothScanPermissionState: PermissionState,
    bluetoothConnectPermissionState: PermissionState,
    locationPermissionState: PermissionState,
    notificationsPermissionState: PermissionState,
    onPermissionsGranted: () -> Unit,
) {
    when {
        !locationPermissionState.status.isGranted ->
            locationPermissionState.launchPermissionRequest()
        !bluetoothScanPermissionState.status.isGranted ->
            bluetoothScanPermissionState.launchPermissionRequest()
        !bluetoothConnectPermissionState.status.isGranted ->
            bluetoothConnectPermissionState.launchPermissionRequest()
        !notificationsPermissionState.status.isGranted ->
            notificationsPermissionState.launchPermissionRequest()
        else -> onPermissionsGranted()
    }
}
