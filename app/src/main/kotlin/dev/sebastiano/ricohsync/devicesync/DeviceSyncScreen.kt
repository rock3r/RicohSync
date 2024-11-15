package dev.sebastiano.ricohsync.devicesync

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import com.juul.kable.ExperimentalApi
import java.util.Locale
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeviceSyncScreen(viewModel: DeviceSyncViewModel) {
    val state by viewModel.state
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Syncing with camera") },
                actions = {
                    if (state is DeviceSyncState.Syncing) {
                        IconButton(viewModel::stopSyncAndDisconnect) {
                            Icon(Icons.Rounded.Stop, contentDescription = null)
                        }
                    }
                },
            )
        },
    ) { insets ->
        Box(
            Modifier.fillMaxSize().padding(insets).padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (val currentState = state) {
                DeviceSyncState.Starting -> Starting()
                is DeviceSyncState.Connecting -> Connecting(currentState)
                is DeviceSyncState.Syncing -> Syncing(currentState)
                is DeviceSyncState.Disconnected -> Reconnecting(currentState)
                DeviceSyncState.Stopped -> Stopped { viewModel.connectAndSync() }
            }
        }
    }
}

@Composable
private fun Starting() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()

        Spacer(Modifier.height(8.dp))

        Text("Starting service...")
    }
}

@Composable
private fun Connecting(state: DeviceSyncState.Connecting) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()

        Spacer(Modifier.height(8.dp))

        val name =
            remember(state.advertisement) {
                state.advertisement.name
                    ?: state.advertisement.peripheralName
                    ?: state.advertisement.identifier
            }

        Text("Connecting to $name...")
    }
}

@OptIn(ExperimentalApi::class)
@Composable
private fun Syncing(state: DeviceSyncState.Syncing) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val rotateAnimation = rememberInfiniteTransition("sync_rotation")
        val rotation by
            rotateAnimation.animateFloat(
                0f,
                -1 * 360f,
                animationSpec =
                    InfiniteRepeatableSpec(
                        tween(durationMillis = 1000, delayMillis = 800, easing = EaseInOut),
                        repeatMode = RepeatMode.Restart,
                    ),
            )
        Icon(
            Icons.Rounded.Sync,
            contentDescription = null,
            modifier = Modifier.size(64.dp).rotate(rotation),
        )

        Spacer(Modifier.height(24.dp))

        val name =
            remember(state.peripheral) { state.peripheral.name ?: state.peripheral.identifier }
        Text("Syncing data to $name...", style = MaterialTheme.typography.bodyLarge)

        if (state.firmwareVersion != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Firmware version: ${state.firmwareVersion}",
                style = MaterialTheme.typography.labelSmall,
            )
        }

        Spacer(Modifier.height(24.dp))

        var lastUpdate by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(state.syncInfo) {
            while (true) {
                lastUpdate = formatElapsedTimeSince(state.syncInfo?.dateTime)
                delay(1.seconds)
            }
        }

        if (!lastUpdate.isNullOrEmpty()) {
            Text("Last sync time: $lastUpdate", style = MaterialTheme.typography.labelSmall)
        }

        Spacer(Modifier.height(8.dp))

        val location =
            remember(state.syncInfo) {
                val location = state.syncInfo?.location
                if (location != null) {
                    buildString {
                        append(location.latitude.toString(decimals = 6))
                        append(", ")
                        append(location.longitude.toString(decimals = 6))
                    }
                } else null
            }

        if (location != null) {
            Text("Last location: $location", style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun Double.toString(decimals: Int): String =
    String.format(Locale.getDefault(), "%.${decimals}f", this)

@OptIn(ExperimentalApi::class)
@Composable
private fun Reconnecting(state: DeviceSyncState.Disconnected) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()

        Spacer(Modifier.height(8.dp))

        val name =
            remember(state.peripheral) { state.peripheral.name ?: state.peripheral.identifier }

        Text("Reconnecting to $name...")
    }
}

@Composable
private fun Stopped(onReconnectClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Disconnected by user", style = MaterialTheme.typography.bodyLarge)

        Spacer(Modifier.height(16.dp))

        Button(onClick = onReconnectClick) { Text("Reconnect") }
    }
}
