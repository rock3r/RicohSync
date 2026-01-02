package dev.sebastiano.ricohsync.devices

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sebastiano.ricohsync.devicesync.formatElapsedTimeSince
import dev.sebastiano.ricohsync.domain.model.DeviceConnectionState
import dev.sebastiano.ricohsync.domain.model.GpsLocation
import dev.sebastiano.ricohsync.domain.model.PairedDeviceWithState
import java.time.ZonedDateTime
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.location.Location
import org.maplibre.compose.location.LocationProvider
import org.maplibre.compose.location.LocationPuck
import org.maplibre.compose.location.rememberUserLocationState
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.OrnamentOptions
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position

/** Main screen showing the list of paired devices with their sync status. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesListScreen(viewModel: DevicesListViewModel, onAddDeviceClick: () -> Unit) {
    val state by viewModel.state
    var deviceToUnpair by remember { mutableStateOf<PairedDeviceWithState?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("RicohSync") },
                actions = {
                    if (state is DevicesListState.HasDevices) {
                        IconButton(onClick = { viewModel.refreshConnections() }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh connections")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddDeviceClick) {
                Icon(Icons.Rounded.Add, contentDescription = "Add device")
            }
        },
    ) { innerPadding ->
        when (val currentState = state) {
            is DevicesListState.Loading -> {
                LoadingContent(Modifier.padding(innerPadding))
            }

            is DevicesListState.Empty -> {
                EmptyContent(
                    modifier = Modifier.padding(innerPadding),
                    onAddDeviceClick = onAddDeviceClick,
                )
            }

            is DevicesListState.HasDevices -> {
                Column(
                    modifier = Modifier.padding(innerPadding),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (currentState.isScanning) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    LocationCard(
                        location = currentState.currentLocation,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )

                    if (!currentState.isSyncEnabled) {
                        SyncStoppedWarning(
                            onRefreshClick = { viewModel.refreshConnections() },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        )
                    }

                    DevicesList(
                        devices = currentState.devices,
                        onDeviceEnabledChange = { device, enabled ->
                            viewModel.setDeviceEnabled(device.device.macAddress, enabled)
                        },
                        onUnpairClick = { device -> deviceToUnpair = device },
                        onRetryClick = { device ->
                            viewModel.retryConnection(device.device.macAddress)
                        },
                    )
                }
            }
        }
    }

    // Unpair confirmation dialog
    deviceToUnpair?.let { device ->
        UnpairConfirmationDialog(
            deviceName = device.device.name ?: device.device.macAddress,
            onConfirm = {
                viewModel.unpairDevice(device.device.macAddress)
                deviceToUnpair = null
            },
            onDismiss = { deviceToUnpair = null },
        )
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Loading devices...", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun EmptyContent(modifier: Modifier = Modifier, onAddDeviceClick: () -> Unit) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                Icons.Rounded.CameraAlt,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )

            Spacer(Modifier.height(16.dp))

            Text(
                "No cameras paired",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Tap the + button to pair a camera",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DevicesList(
    devices: List<PairedDeviceWithState>,
    onDeviceEnabledChange: (PairedDeviceWithState, Boolean) -> Unit,
    onUnpairClick: (PairedDeviceWithState) -> Unit,
    onRetryClick: (PairedDeviceWithState) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = 16.dp,
                top = 8.dp,
                end = 16.dp,
                bottom = 80.dp, // Room for FAB
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(devices, key = { it.device.macAddress }) { deviceWithState ->
            DeviceCard(
                deviceWithState = deviceWithState,
                onEnabledChange = { enabled -> onDeviceEnabledChange(deviceWithState, enabled) },
                onUnpairClick = { onUnpairClick(deviceWithState) },
                onRetryClick = { onRetryClick(deviceWithState) },
            )
        }
    }
}

@Composable
private fun DeviceCard(
    deviceWithState: PairedDeviceWithState,
    onEnabledChange: (Boolean) -> Unit,
    onUnpairClick: () -> Unit,
    onRetryClick: () -> Unit,
) {
    var isExpanded by remember { mutableStateOf(false) }
    val device = deviceWithState.device
    val connectionState = deviceWithState.connectionState

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            // Main row
            Row(
                modifier =
                    Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Status indicator
                ConnectionStatusIcon(connectionState)

                Spacer(Modifier.width(16.dp))

                // Device info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.name ?: "Unknown Camera",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(Modifier.height(2.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ConnectionStatusText(connectionState)

                        if (device.lastSyncedAt != null) {
                            Text(
                                text = " • ${formatElapsedTimeSince(device.lastSyncedAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color =
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Enable/disable switch
                Switch(checked = device.isEnabled, onCheckedChange = onEnabledChange)

                // Expand indicator
                Icon(
                    if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier.alpha(0.6f),
                )
            }

            // Expanded content
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(
                    modifier =
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    // Device details
                    DeviceDetailRow("MAC Address", device.macAddress)
                    DeviceDetailRow("Vendor", device.vendorId.replaceFirstChar { it.uppercase() })

                    if (device.lastSyncedAt != null) {
                        DeviceDetailRow("Last sync", formatElapsedTimeSince(device.lastSyncedAt))
                    }

                    if (connectionState is DeviceConnectionState.Syncing) {
                        connectionState.firmwareVersion?.let { version ->
                            DeviceDetailRow("Firmware", version)
                        }
                    }

                    if (connectionState is DeviceConnectionState.Error) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = connectionState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )

                        if (connectionState.isRecoverable) {
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = onRetryClick) { Text("Retry connection") }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Unpair action
                    TextButton(onClick = onUnpairClick, modifier = Modifier.align(Alignment.End)) {
                        Text("Unpair device", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatusIcon(state: DeviceConnectionState) {
    val (icon, color, shouldAnimate) =
        when (state) {
            is DeviceConnectionState.Disabled ->
                Triple(
                    Icons.Rounded.BluetoothDisabled,
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    false,
                )

            is DeviceConnectionState.Disconnected ->
                Triple(Icons.Rounded.Bluetooth, MaterialTheme.colorScheme.onSurfaceVariant, false)

            is DeviceConnectionState.Connecting ->
                Triple(Icons.Rounded.Bluetooth, MaterialTheme.colorScheme.primary, true)

            is DeviceConnectionState.Connected ->
                Triple(Icons.Rounded.BluetoothConnected, MaterialTheme.colorScheme.primary, false)

            is DeviceConnectionState.Error ->
                Triple(Icons.Rounded.Error, MaterialTheme.colorScheme.error, false)

            is DeviceConnectionState.Syncing ->
                Triple(Icons.Rounded.Sync, MaterialTheme.colorScheme.primary, true)
        }

    val animatedColor by animateColorAsState(targetValue = color, label = "status_color")

    Box(
        modifier =
            Modifier.size(40.dp).clip(CircleShape).background(animatedColor.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        if (shouldAnimate) {
            val infiniteTransition = rememberInfiniteTransition(label = "icon_animation")
            val rotation by
                infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = if (state is DeviceConnectionState.Syncing) -360f else 360f,
                    animationSpec =
                        InfiniteRepeatableSpec(
                            animation = tween(1500, easing = EaseInOut),
                            repeatMode = RepeatMode.Restart,
                        ),
                    label = "rotation",
                )

            Icon(
                icon,
                contentDescription = null,
                tint = animatedColor,
                modifier = Modifier.rotate(rotation),
            )
        } else {
            Icon(icon, contentDescription = null, tint = animatedColor)
        }
    }
}

@Composable
private fun ConnectionStatusText(state: DeviceConnectionState) {
    val (text, color) =
        when (state) {
            is DeviceConnectionState.Disabled ->
                "Disabled" to MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)

            is DeviceConnectionState.Disconnected ->
                "Disconnected" to MaterialTheme.colorScheme.onSurfaceVariant

            is DeviceConnectionState.Connecting ->
                "Connecting..." to MaterialTheme.colorScheme.primary

            is DeviceConnectionState.Connected -> "Connected" to MaterialTheme.colorScheme.primary
            is DeviceConnectionState.Error -> "Error" to MaterialTheme.colorScheme.error
            is DeviceConnectionState.Syncing -> "Syncing" to MaterialTheme.colorScheme.primary
        }

    Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
}

@Composable
private fun DeviceDetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun UnpairConfirmationDialog(
    deviceName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Unpair device?") },
        text = {
            Text(
                "Are you sure you want to unpair \"$deviceName\"? You'll need to pair it again to sync data."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Unpair", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SyncStoppedWarning(onRefreshClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.BluetoothDisabled,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Searching stopped", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "Tap to resume searching for cameras",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            IconButton(onClick = onRefreshClick) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Resume searching")
            }
        }
    }
}

@Composable
private fun LocationCard(location: GpsLocation?, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().height(200.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        if (location == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Acquiring location...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            val isDarkTheme = isSystemInDarkTheme()
            val mapStyle =
                if (isDarkTheme) "https://tiles.openfreemap.org/styles/dark"
                else "https://tiles.openfreemap.org/styles/positron"

            val cameraState =
                rememberCameraState(
                    CameraPosition(
                        target = Position(location.longitude, location.latitude),
                        zoom = 14.0,
                    )
                )

            // Update camera when location changes
            val locationFlow = remember { MutableStateFlow(location.toMapBoxLocation()) }
            LaunchedEffect(location) {
                cameraState.position =
                    CameraPosition(
                        target = Position(location.longitude, location.latitude),
                        zoom = cameraState.position.zoom, // Keep current zoom
                    )
                locationFlow.value = location.toMapBoxLocation()
            }

            val userState =
                rememberUserLocationState(
                    object : LocationProvider {
                        override val location: StateFlow<Location?> = locationFlow
                    }
                )
            MaplibreMap(
                modifier = Modifier.fillMaxSize(),
                baseStyle = BaseStyle.Uri(mapStyle),
                cameraState = cameraState,
                options =
                    MapOptions(
                        ornamentOptions =
                            OrnamentOptions(
                                isLogoEnabled = false,
                                isCompassEnabled = true,
                                isScaleBarEnabled = false,
                            )
                    ),
            ) {
                LocationPuck(
                    idPrefix = "user-location",
                    cameraState = cameraState,
                    locationState = userState,
                )
            }
        }
    }
}

private fun GpsLocation.toMapBoxLocation() =
    Location(
        Position(longitude, latitude),
        accuracy.toDouble(),
        bearing = null,
        bearingAccuracy = null,
        speed = null,
        speedAccuracy = null,
        timestamp.toTimeMark(),
    )

private fun ZonedDateTime.toTimeMark(): TimeMark {
    val now = ZonedDateTime.now()
    // Calculate the difference in milliseconds
    val diffMillis = toInstant().toEpochMilli() - now.toInstant().toEpochMilli()

    // Create a mark that is 'diff' away from the current monotonic 'now'
    return TimeSource.Monotonic.markNow() + diffMillis.milliseconds
}

private fun Double.format(decimals: Int): String = "%.${decimals}f".format(this)
