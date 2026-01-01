package dev.sebastiano.ricohsync.pairing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sebastiano.ricohsync.domain.model.Camera

/**
 * Screen for discovering and pairing new camera devices.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    viewModel: PairingViewModel,
    onNavigateBack: () -> Unit,
    onDevicePaired: () -> Unit,
) {
    val state by viewModel.state
    var showPairingErrorDialog by remember { mutableStateOf<PairingError?>(null) }

    // Handle successful pairing
    val currentState = state
    if (currentState is PairingScreenState.Pairing && currentState.success) {
        onDevicePaired()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Add Camera") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            val isScanning = currentState is PairingScreenState.Scanning
            FloatingActionButton(
                onClick = {
                    if (isScanning) viewModel.stopScanning() else viewModel.startScanning()
                },
            ) {
                Icon(
                    if (isScanning) Icons.Rounded.Stop else Icons.Rounded.Refresh,
                    contentDescription = if (isScanning) "Stop scanning" else "Start scanning",
                )
            }
        },
    ) { innerPadding ->
        when (currentState) {
            is PairingScreenState.Idle -> {
                IdleContent(
                    modifier = Modifier.padding(innerPadding),
                    onStartScanning = { viewModel.startScanning() },
                )
            }

            is PairingScreenState.Scanning -> {
                ScanningContent(
                    modifier = Modifier.padding(innerPadding),
                    discoveredDevices = currentState.discoveredDevices,
                    onDeviceClick = { camera ->
                        viewModel.pairDevice(camera)
                    },
                )
            }

            is PairingScreenState.Pairing -> {
                PairingContent(
                    modifier = Modifier.padding(innerPadding),
                    deviceName = currentState.camera.name ?: currentState.camera.macAddress,
                    error = currentState.error,
                    onRetry = { viewModel.pairDevice(currentState.camera) },
                    onCancel = { viewModel.cancelPairing() },
                )
            }
        }
    }

    // Pairing error dialog
    showPairingErrorDialog?.let { error ->
        PairingErrorDialog(
            error = error,
            onDismiss = { showPairingErrorDialog = null },
            onRetry = {
                showPairingErrorDialog = null
                // Retry logic would go here
            },
        )
    }
}

@Composable
private fun IdleContent(
    modifier: Modifier = Modifier,
    onStartScanning: () -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                Icons.Rounded.Bluetooth,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )

            Spacer(Modifier.height(16.dp))

            Text(
                "Ready to scan",
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Make sure your camera has Bluetooth pairing enabled, then tap the scan button.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ScanningContent(
    modifier: Modifier = Modifier,
    discoveredDevices: List<Camera>,
    onDeviceClick: (Camera) -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Scanning indicator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "scan_animation")
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(2000)),
                label = "rotation",
            )

            Icon(
                Icons.Rounded.Bluetooth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.rotate(rotation),
            )

            Spacer(Modifier.width(12.dp))

            Text(
                "Scanning for cameras...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (discoveredDevices.isEmpty()) {
            // No devices found yet
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp),
                ) {
                    CircularProgressIndicator()

                    Spacer(Modifier.height(16.dp))

                    Text(
                        "Looking for cameras...",
                        style = MaterialTheme.typography.bodyLarge,
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        "Make sure your camera is nearby with Bluetooth enabled.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            // Device list
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(discoveredDevices, key = { it.macAddress }) { camera ->
                    DiscoveredDeviceCard(
                        camera = camera,
                        onClick = { onDeviceClick(camera) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoveredDeviceCard(
    camera: Camera,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.CameraAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = camera.name ?: "Unknown Camera",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(2.dp))

                Text(
                    text = camera.vendor.vendorName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = camera.macAddress,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }

            Text(
                "Tap to pair",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun PairingContent(
    modifier: Modifier = Modifier,
    deviceName: String,
    error: PairingError?,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            if (error != null) {
                // Error state
                Icon(
                    Icons.Rounded.Error,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.error,
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    "Pairing failed",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.error,
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = when (error) {
                        PairingError.REJECTED -> "The camera rejected the pairing request. Make sure Bluetooth pairing is enabled on your camera."
                        PairingError.TIMEOUT -> "Connection timed out. Make sure the camera is nearby and Bluetooth is enabled."
                        PairingError.UNKNOWN -> "An unexpected error occurred. Please try again."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(24.dp))

                Row {
                    TextButton(onClick = onCancel) {
                        Text("Cancel")
                    }

                    Spacer(Modifier.width(16.dp))

                    TextButton(onClick = onRetry) {
                        Text("Retry")
                    }
                }
            } else {
                // Pairing in progress
                CircularProgressIndicator(
                    modifier = Modifier.size(64.dp),
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    "Pairing with $deviceName...",
                    style = MaterialTheme.typography.titleLarge,
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    "Please wait while we connect to your camera.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(24.dp))

                TextButton(onClick = onCancel) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun PairingErrorDialog(
    error: PairingError,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pairing failed") },
        text = {
            Text(
                when (error) {
                    PairingError.REJECTED -> "The camera rejected the pairing request. Please enable Bluetooth pairing on your camera and try again."
                    PairingError.TIMEOUT -> "Connection timed out. Make sure the camera is nearby and has Bluetooth enabled."
                    PairingError.UNKNOWN -> "An unexpected error occurred. Please try again."
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onRetry) {
                Text("Retry")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

/**
 * Errors that can occur during pairing.
 */
enum class PairingError {
    /** Camera rejected the pairing request. */
    REJECTED,

    /** Connection timed out. */
    TIMEOUT,

    /** Unknown error. */
    UNKNOWN,
}

