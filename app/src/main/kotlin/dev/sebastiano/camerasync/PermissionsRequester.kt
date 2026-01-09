package dev.sebastiano.camerasync

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.BLUETOOTH_SCAN
import android.Manifest.permission.POST_NOTIFICATIONS
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState

data class PermissionInfo(val name: String, val description: String, val isGranted: Boolean)

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionsRequester(
    onPermissionsGranted: () -> Unit,
    content:
        @Composable
        (permissions: List<PermissionInfo>, onRequestAllPermissions: () -> Unit) -> Unit,
) {
    val allPermissions =
        listOf(ACCESS_FINE_LOCATION, BLUETOOTH_SCAN, BLUETOOTH_CONNECT, POST_NOTIFICATIONS)

    val multiplePermissionsState = rememberMultiplePermissionsState(permissions = allPermissions)

    val permissionNames =
        mapOf(
            ACCESS_FINE_LOCATION to "Location",
            BLUETOOTH_SCAN to "Bluetooth Scan",
            BLUETOOTH_CONNECT to "Bluetooth Connect",
            POST_NOTIFICATIONS to "Notifications",
        )

    val permissionDescriptions =
        mapOf(
            ACCESS_FINE_LOCATION to "Get GPS coordinates for your photos",
            BLUETOOTH_SCAN to "Find nearby cameras",
            BLUETOOTH_CONNECT to "Connect to your camera",
            POST_NOTIFICATIONS to "Show sync status updates",
        )

    val permissions =
        allPermissions.map { permission ->
            val permissionState =
                multiplePermissionsState.permissions.find { it.permission == permission }
            PermissionInfo(
                name = permissionNames[permission] ?: permission,
                description = permissionDescriptions[permission] ?: "",
                isGranted = permissionState?.status?.isGranted ?: false,
            )
        }

    LaunchedEffect(multiplePermissionsState) {
        if (multiplePermissionsState.allPermissionsGranted) {
            onPermissionsGranted()
        }
    }

    content(
        permissions,
        {
            val missingPermissions =
                multiplePermissionsState.permissions
                    .filter { !it.status.isGranted }
                    .map { it.permission }
            if (missingPermissions.isNotEmpty()) {
                multiplePermissionsState.launchMultiplePermissionRequest()
            }
        },
    )
}
