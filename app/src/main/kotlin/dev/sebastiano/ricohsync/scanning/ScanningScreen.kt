package dev.sebastiano.ricohsync.scanning

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.juul.kable.Advertisement

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScanningScreen(
    viewModel: ScanningViewModel,
    onDeviceSelected: (Advertisement) -> Unit,
) {
    val state by viewModel.state
    val currentState = state
    val isScanning = currentState is PairingState.Scanning

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = { Text(if (isScanning) "Scanning..." else "Discovered cameras") })
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { if (isScanning) viewModel.stopScan() else viewModel.doScan() }
            ) {
                Icon(
                    if (isScanning) Icons.Rounded.Stop else Icons.Rounded.Refresh,
                    if (isScanning) "Stop scanning" else "Start scanning",
                )
            }
        },
    ) { innerPadding ->
        if (currentState is PairingState.WithResults && currentState.found.isNotEmpty()) {
            val found = (currentState as PairingState.WithResults).found
            val foundDevices = found.values.sortedByDescending { it.peripheralName ?: it.name }

            LazyColumn(contentPadding = innerPadding) {
                items(foundDevices, key = { it.identifier }) { advertisement ->
                    DiscoveredDevice(
                        advertisement,
                        Modifier.fillMaxWidth().heightIn(48.dp).clickable {
                            onDeviceSelected(advertisement)
                        },
                    )
                }
            }
        } else {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun DiscoveredDevice(advertisement: Advertisement, modifier: Modifier) {
    val name = advertisement.name ?: advertisement.peripheralName
    if (name != null) {
        Column(modifier.padding(horizontal = 16.dp)) {
            Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text(advertisement.identifier, style = MaterialTheme.typography.labelSmall)
        }
    } else {
        Row(modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(advertisement.identifier, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
