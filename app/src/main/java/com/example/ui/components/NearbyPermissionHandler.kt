package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.utils.NearbyPermissionType
import com.example.data.utils.PermissionHelper
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.TealAccent
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.shouldShowRationale

/**
 * State holder for Nearby Connections API runtime permissions managed via Accompanist.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Stable
class NearbyPermissionsState(
    val accompanistState: MultiplePermissionsState,
    private val context: Context
) {
    val allPermissionsGranted: Boolean
        get() = accompanistState.allPermissionsGranted

    val shouldShowRationale: Boolean
        get() = accompanistState.shouldShowRationale

    val permissions: List<PermissionState>
        get() = accompanistState.permissions

    val revokedPermissions: List<PermissionState>
        get() = accompanistState.revokedPermissions

    /**
     * Checks if all Bluetooth-related runtime permissions are granted.
     */
    val isBluetoothGranted: Boolean
        get() {
            val btPerms = PermissionHelper.getBluetoothPermissions()
            if (btPerms.isEmpty()) return true
            return btPerms.all { perm ->
                accompanistState.permissions.find { it.permission == perm }?.status?.isGranted ?: false
            }
        }

    /**
     * Checks if Wi-Fi / Nearby Wi-Fi Devices runtime permissions are granted.
     */
    val isWifiGranted: Boolean
        get() {
            val wifiPerms = PermissionHelper.getWifiPermissions()
            if (wifiPerms.isEmpty()) return true
            return wifiPerms.all { perm ->
                accompanistState.permissions.find { it.permission == perm }?.status?.isGranted ?: false
            }
        }

    /**
     * Checks if Location runtime permissions are granted.
     */
    val isLocationGranted: Boolean
        get() {
            val locPerms = PermissionHelper.getLocationPermissions()
            if (locPerms.isEmpty()) return true
            return locPerms.any { perm ->
                accompanistState.permissions.find { it.permission == perm }?.status?.isGranted ?: false
            }
        }

    /**
     * Launch the Accompanist system permission dialog.
     */
    fun launchMultiplePermissionRequest() {
        accompanistState.launchMultiplePermissionRequest()
    }

    /**
     * Directs user to the system app settings page if permissions were permanently denied.
     */
    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}

/**
 * Creates and remembers a [NearbyPermissionsState] using Accompanist permissions.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun rememberNearbyPermissionsState(
    onPermissionsResult: ((Map<String, Boolean>) -> Unit)? = null
): NearbyPermissionsState {
    val context = LocalContext.current
    val requiredPermissions = remember { PermissionHelper.getNearbyConnectionsPermissions() }

    val accompanistMultipleState = rememberMultiplePermissionsState(
        permissions = requiredPermissions,
        onPermissionsResult = { results ->
            onPermissionsResult?.invoke(results)
        }
    )

    return remember(accompanistMultipleState, context) {
        NearbyPermissionsState(accompanistMultipleState, context)
    }
}

/**
 * Material 3 Rationale Dialog explaining why Bluetooth, Wi-Fi, and Location
 * are required by the Nearby Connections API.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun NearbyPermissionRationaleDialog(
    permissionsState: NearbyPermissionsState,
    onDismissRequest: () -> Unit,
    onContinueAnyway: (() -> Unit)? = null,
    onGrantPermissions: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier.testTag("nearby_permission_dialog"),
        icon = {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                color = ElectricBlue.copy(alpha = 0.14f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.WifiTethering,
                        contentDescription = "Nearby Permissions",
                        tint = ElectricBlue,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        },
        title = {
            Text(
                text = "Permissions Required for Transfer",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Google Nearby Connections operates offline using a hybrid mesh of Bluetooth, local Wi-Fi Direct, and radio scanning:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Bluetooth Group
                PermissionGroupRow(
                    icon = Icons.Default.Bluetooth,
                    title = "Bluetooth Radios",
                    description = "Broadcasts beacon presence and discovers nearby senders/receivers without internet.",
                    isGranted = permissionsState.isBluetoothGranted,
                    accentColor = ElectricBlue
                )

                // Wi-Fi Group
                PermissionGroupRow(
                    icon = Icons.Default.Wifi,
                    title = "Wi-Fi & Direct Channels",
                    description = "Creates high-speed offline peer-to-peer data pipes for high-speed file streaming.",
                    isGranted = permissionsState.isWifiGranted,
                    accentColor = TealAccent
                )

                // Location Group (Only on Android 12 and below)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    PermissionGroupRow(
                        icon = Icons.Default.LocationOn,
                        title = "Location Scanning",
                        description = "Required by Android OS to scan for local Bluetooth/Wi-Fi beacons. NearShare never tracks your GPS location.",
                        isGranted = permissionsState.isLocationGranted,
                        accentColor = Color(0xFFFF9800)
                    )
                }

                if (permissionsState.shouldShowRationale) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Denied",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Some permissions were denied. Nearby transfers cannot discover devices until granted.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    permissionsState.launchMultiplePermissionRequest()
                    onGrantPermissions?.invoke() ?: onDismissRequest()
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("grant_permissions_button")
            ) {
                Text(
                    text = "Grant Permissions",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!permissionsState.allPermissionsGranted && !permissionsState.shouldShowRationale && permissionsState.revokedPermissions.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            permissionsState.openAppSettings()
                            onDismissRequest()
                        },
                        modifier = Modifier.testTag("open_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "Settings",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("App Settings")
                    }
                }

                TextButton(
                    onClick = {
                        onDismissRequest()
                        onContinueAnyway?.invoke()
                    },
                    modifier = Modifier.testTag("dismiss_permissions_button")
                ) {
                    Text("Dismiss")
                }
            }
        }
    )
}

/**
 * Individual permission group status item displaying an icon, title, description, and status tag.
 */
@Composable
private fun PermissionGroupRow(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) {
                Color(0xFFE8F5E9)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = CircleShape,
                color = if (isGranted) Color(0xFF4CAF50).copy(alpha = 0.15f) else accentColor.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = if (isGranted) Color(0xFF2E7D32) else accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isGranted) Color(0xFFC8E6C9) else MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Info,
                                contentDescription = null,
                                tint = if (isGranted) Color(0xFF2E7D32) else ElectricBlue,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (isGranted) "Granted" else "Required",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isGranted) Color(0xFF2E7D32) else ElectricBlue
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

/**
 * Top warning banner to notify the user if any Nearby Connections permissions are missing.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun NearbyPermissionStatusBanner(
    permissionsState: NearbyPermissionsState,
    onManagePermissionsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = !permissionsState.allPermissionsGranted,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("nearby_permission_status_banner"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Permission Alert",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Nearby Permissions Incomplete",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = "Bluetooth, Wi-Fi & Location needed for P2P",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }

                Button(
                    onClick = onManagePermissionsClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    ),
                    modifier = Modifier.testTag("manage_permissions_banner_button")
                ) {
                    Text(
                        text = "Grant",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onTertiary
                    )
                }
            }
        }
    }
}

/**
 * A composable wrapper that checks for Nearby Connections permissions before allowing
 * an action to proceed, launching Accompanist's permission dialog and rationale if needed.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun NearbyPermissionGate(
    permissionsState: NearbyPermissionsState,
    onActionPermitted: () -> Unit,
    content: @Composable (triggerActionWithPermissions: () -> Unit) -> Unit
) {
    var showRationaleDialog by remember { mutableStateOf(false) }

    if (showRationaleDialog) {
        NearbyPermissionRationaleDialog(
            permissionsState = permissionsState,
            onDismissRequest = { showRationaleDialog = false },
            onContinueAnyway = onActionPermitted
        )
    }

    content {
        if (permissionsState.allPermissionsGranted) {
            onActionPermitted()
        } else {
            if (permissionsState.shouldShowRationale) {
                showRationaleDialog = true
            } else {
                permissionsState.launchMultiplePermissionRequest()
            }
        }
    }
}
