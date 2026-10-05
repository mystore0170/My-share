package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.NearbyDevice
import com.example.ui.components.NearbyPermissionStatusBanner
import com.example.ui.components.NearbyPermissionsState
import com.example.ui.components.QrPairingDialog
import com.example.ui.components.RadarScannerView
import com.example.ui.components.SecurityPairingDialog
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.TealAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryScreen(
    userDeviceName: String,
    discoveredDevices: List<NearbyDevice>,
    pendingConnection: NearbyDevice?,
    onDeviceSelected: (NearbyDevice) -> Unit,
    onAcceptConnection: (String) -> Unit,
    onRejectConnection: (String) -> Unit,
    onStartSimulationTransfer: () -> Unit,
    onOpenQrScanner: () -> Unit,
    onBack: () -> Unit,
    permissionsState: NearbyPermissionsState? = null,
    onRequestPermissions: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showQrDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Find Nearby Devices",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Nearby Connections • High Speed",
                            style = MaterialTheme.typography.labelSmall,
                            color = TealAccent
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("discovery_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenQrScanner,
                        modifier = Modifier.testTag("discovery_qr_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Scan QR Code",
                            tint = ElectricBlue
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // QR Scanner Quick Button
                Button(
                    onClick = onOpenQrScanner,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("discovery_scan_qr_bottom_button"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Scan QR Code",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Scan Receiver's QR Code",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Testing simulation button (crucial for single-emulator interactive testing)
                OutlinedButton(
                    onClick = onStartSimulationTransfer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("start_demo_transfer_button"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Demo Transfer",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Demo / Standalone High-Speed Transfer",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Tap a detected peer in the radar above or scan a QR code to pair.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Radar UI
            RadarScannerView(
                userDeviceName = userDeviceName,
                discoveredDevices = discoveredDevices,
                onDeviceClick = onDeviceSelected,
                modifier = Modifier.fillMaxSize()
            )

            // Permissions warning banner if any permission is missing
            if (permissionsState != null && !permissionsState.allPermissionsGranted) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .align(Alignment.TopCenter)
                ) {
                    NearbyPermissionStatusBanner(
                        permissionsState = permissionsState,
                        onManagePermissionsClick = { onRequestPermissions?.invoke() }
                    )
                }
            }

            // Status Badge at top of radar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(10.dp),
                            shape = CircleShape,
                            color = ElectricBlue
                        ) {}
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (discoveredDevices.isEmpty()) {
                                "Pulsing for nearby receivers..."
                            } else {
                                "Found ${discoveredDevices.size} ${if (discoveredDevices.size == 1) "device" else "devices"} • Tap to connect"
                            },
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // QR Code Dialog
    if (showQrDialog) {
        QrPairingDialog(
            deviceName = userDeviceName,
            onDismiss = { showQrDialog = false }
        )
    }

    // Security Verification Code Dialog
    pendingConnection?.let { dev ->
        SecurityPairingDialog(
            deviceName = dev.name,
            authCode = dev.authCode,
            onAccept = { onAcceptConnection(dev.endpointId) },
            onReject = { onRejectConnection(dev.endpointId) }
        )
    }
}
