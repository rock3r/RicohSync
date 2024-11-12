package dev.sebastiano.ricohsync.devicesync

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeviceSyncScreen(viewModel: DeviceSyncViewModel) {
    val state by viewModel.state
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("Syncing with camera") }) },
    ) { insets ->
        Box(
            Modifier.fillMaxSize().padding(insets).padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (val currentState = state) {
                DeviceSyncState.Starting -> Starting()
                is DeviceSyncState.Connecting -> Connecting(currentState)
                is DeviceSyncState.Syncing -> Syncing(currentState)
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

@Composable
private fun Syncing(state: DeviceSyncState.Syncing) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(64.dp))

        Spacer(Modifier.height(24.dp))

        val name =
            remember(state.peripheral) { state.peripheral.name ?: state.peripheral.identifier }
        Text("Syncing data to $name...", style = MaterialTheme.typography.bodyLarge)

        Spacer(Modifier.height(24.dp))

        Text(
            "Last sync time: ${state.lastSyncTime?.toString() ?: "N/A"}",
            style = MaterialTheme.typography.labelSmall,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Last location: ${state.lastLocation?.toString() ?: "N/A"}",
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
