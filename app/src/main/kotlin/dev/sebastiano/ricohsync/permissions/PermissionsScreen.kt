package dev.sebastiano.ricohsync.permissions

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.sebastiano.ricohsync.PermissionInfo
import dev.sebastiano.ricohsync.PermissionsRequester

@Composable
fun PermissionsScreen(onPermissionsGranted: () -> Unit) {
    PermissionsRequester(onPermissionsGranted) { permissions, requestAll ->
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    // Header
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Welcome to RicohSync",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "We need a few permissions to sync GPS data to your camera",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // Permissions checklist
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            permissions.forEachIndexed { index, permission ->
                                PermissionChecklistItem(
                                    permission = permission,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                if (index < permissions.size - 1) {
                                    Spacer(Modifier.height(4.dp))
                                }
                            }
                        }
                    }

                    // Request button
                    val hasMissingPermissions = permissions.any { !it.isGranted }
                    val buttonText =
                        if (hasMissingPermissions) {
                            "Grant All Permissions"
                        } else {
                            "All Set! Continue"
                        }

                    ElevatedButton(
                        onClick = {
                            if (hasMissingPermissions) {
                                requestAll()
                            } else {
                                onPermissionsGranted()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(
                            text = buttonText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionChecklistItem(permission: PermissionInfo, modifier: Modifier = Modifier) {
    val iconColor by
        animateColorAsState(
            targetValue =
                if (permission.isGranted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                },
            label = "icon_color",
            animationSpec = tween(durationMillis = 300),
        )

    val checkmarkScale by
        animateFloatAsState(
            targetValue = if (permission.isGranted) 1f else 0f,
            label = "checkmark_scale",
            animationSpec = tween(durationMillis = 300),
        )

    val itemAlpha by
        animateFloatAsState(
            targetValue = if (permission.isGranted) 0.7f else 1f,
            label = "item_alpha",
            animationSpec = tween(durationMillis = 300),
        )

    Row(
        modifier = modifier.alpha(itemAlpha),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Permission icon
        Box(
            modifier =
                Modifier.size(48.dp).clip(CircleShape).background(iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            val icon =
                when (permission.name) {
                    "Location" -> Icons.Default.LocationOn
                    "Bluetooth Scan",
                    "Bluetooth Connect" -> Icons.Rounded.Bluetooth
                    "Notifications" -> Icons.Default.Notifications
                    else -> Icons.Rounded.Bluetooth
                }
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
        }

        // Permission info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = permission.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = permission.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Status indicator
        Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            Crossfade(
                targetState = permission.isGranted,
                animationSpec = tween(durationMillis = 300),
                label = "permission_status",
            ) { isGranted ->
                if (isGranted) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Granted",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.scale(checkmarkScale),
                    )
                } else {
                    Box(
                        modifier =
                            Modifier.size(20.dp)
                                .clip(CircleShape)
                                .background(
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                )
                    )
                }
            }
        }
    }
}
