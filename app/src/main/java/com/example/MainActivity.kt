package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.TransferCategory
import com.example.data.utils.FileUtils
import com.example.data.utils.PermissionHelper
import com.example.ui.components.GoogleSignInScreen
import com.example.ui.components.NearbyPermissionRationaleDialog
import com.example.ui.components.rememberNearbyPermissionsState
import com.example.ui.screens.DiscoveryScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.QrScannerScreen
import com.example.ui.screens.ReceiveScreen
import com.example.ui.screens.SendScreen
import com.example.ui.screens.TransferScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.NearShareViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: NearShareViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
                val isAuthLoading by viewModel.isAuthLoading.collectAsStateWithLifecycle()
                val authError by viewModel.authError.collectAsStateWithLifecycle()
                val context = LocalContext.current

                if (currentUser == null) {
                    GoogleSignInScreen(
                        isLoading = isAuthLoading,
                        errorMessage = authError,
                        onSignInClick = { viewModel.signInWithGoogle(context) }
                    )
                } else {
                    NearShareApp(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun NearShareApp(
    viewModel: NearShareViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val activeCategory by viewModel.activeCategory.collectAsStateWithLifecycle()
    val selectedItems by viewModel.selectedItems.collectAsStateWithLifecycle()
    val pickedFiles by viewModel.pickedFiles.collectAsStateWithLifecycle()
    val contacts by viewModel.contactsList.collectAsStateWithLifecycle()
    val apps by viewModel.installedApps.collectAsStateWithLifecycle()
    val isLoadingContent by viewModel.isLoadingContent.collectAsStateWithLifecycle()

    val discoveredDevices by viewModel.discoveredDevices.collectAsStateWithLifecycle()
    val pendingConnection by viewModel.pendingConnection.collectAsStateWithLifecycle()
    val connectedDevice by viewModel.connectedDevice.collectAsStateWithLifecycle()
    val transferProgress by viewModel.transferProgress.collectAsStateWithLifecycle()
    val historyList by viewModel.historyList.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()

    LaunchedEffect(userMessage) {
        userMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearUserMessage()
        }
    }

    // Accompanist Runtime Permission Handler for Bluetooth, Wi-Fi, and Location
    var onPermissionsGrantedAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showPermissionRationaleDialog by remember { mutableStateOf(false) }

    val nearbyPermissionsState = rememberNearbyPermissionsState { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            Toast.makeText(context, "All Nearby permissions granted", Toast.LENGTH_SHORT).show()
            viewModel.nearbyManager.clearPermissionNeeded()
            onPermissionsGrantedAction?.invoke()
            onPermissionsGrantedAction = null
        } else {
            Toast.makeText(
                context,
                "Nearby permissions are required for offline P2P transfer",
                Toast.LENGTH_SHORT
            ).show()
            onPermissionsGrantedAction = null
        }
    }

    // Auto-prompt if NearbyManager indicates permissions are needed
    val permissionNeeded by viewModel.nearbyManager.permissionNeeded.collectAsStateWithLifecycle()
    LaunchedEffect(permissionNeeded) {
        if (permissionNeeded && !nearbyPermissionsState.allPermissionsGranted) {
            if (nearbyPermissionsState.shouldShowRationale) {
                showPermissionRationaleDialog = true
            } else {
                nearbyPermissionsState.launchMultiplePermissionRequest()
            }
        }
    }

    // If permissions become granted and user is currently on Discovery or Receive screen, start the radio
    LaunchedEffect(currentScreen, nearbyPermissionsState.allPermissionsGranted) {
        if (nearbyPermissionsState.allPermissionsGranted) {
            viewModel.nearbyManager.clearPermissionNeeded()
            if (currentScreen == "DISCOVERY_RADAR" && !viewModel.nearbyManager.isDiscoveringActive) {
                viewModel.nearbyManager.startDiscovery()
            } else if (currentScreen == "RECEIVE_BEACON" && !viewModel.nearbyManager.isAdvertisingActive) {
                viewModel.nearbyManager.startAdvertising()
            }
        }
    }

    if (showPermissionRationaleDialog) {
        NearbyPermissionRationaleDialog(
            permissionsState = nearbyPermissionsState,
            onDismissRequest = {
                showPermissionRationaleDialog = false
                onPermissionsGrantedAction = null
            },
            onGrantPermissions = {
                showPermissionRationaleDialog = false
            },
            onContinueAnyway = {
                showPermissionRationaleDialog = false
                onPermissionsGrantedAction = null
            }
        )
    }

    fun checkAndRequestPermissions(action: () -> Unit) {
        if (nearbyPermissionsState.allPermissionsGranted) {
            action()
        } else {
            onPermissionsGrantedAction = action
            if (nearbyPermissionsState.shouldShowRationale) {
                showPermissionRationaleDialog = true
            } else {
                nearbyPermissionsState.launchMultiplePermissionRequest()
            }
        }
    }

    // Handle Back Press
    BackHandler(enabled = currentScreen != "HOME") {
        when (currentScreen) {
            "SEND_SELECT" -> viewModel.navigateTo("HOME")
            "QR_SCANNER" -> viewModel.navigateTo("HOME")
            "DISCOVERY_RADAR" -> {
                viewModel.nearbyManager.stopDiscovery()
                viewModel.navigateTo("SEND_SELECT")
            }
            "RECEIVE_BEACON" -> {
                viewModel.nearbyManager.stopAdvertising()
                viewModel.navigateTo("HOME")
            }
            "TRANSFERRING" -> viewModel.cancelTransfer()
            "HISTORY" -> viewModel.navigateTo("HOME")
            else -> viewModel.navigateTo("HOME")
        }
    }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "ScreenTransition",
        modifier = modifier.fillMaxSize()
    ) { screen ->
        when (screen) {
            "HOME" -> {
                HomeScreen(
                    deviceName = viewModel.nearbyManager.deviceName,
                    recentHistory = historyList,
                    permissionsState = nearbyPermissionsState,
                    onManagePermissionsClick = { showPermissionRationaleDialog = true },
                    onSendClick = {
                        checkAndRequestPermissions {
                            viewModel.setCategory(TransferCategory.FILES)
                            viewModel.navigateTo("SEND_SELECT")
                        }
                    },
                    onReceiveClick = {
                        checkAndRequestPermissions {
                            viewModel.startReceiverFlow()
                        }
                    },
                    onScanQrClick = {
                        checkAndRequestPermissions {
                            viewModel.openQrScanner()
                        }
                    },
                    onCategoryClick = { category ->
                        checkAndRequestPermissions {
                            viewModel.setCategory(category)
                            viewModel.navigateTo("SEND_SELECT")
                        }
                    },
                    onHistoryClick = { viewModel.navigateTo("HISTORY") },
                    onOpenFile = { path -> FileUtils.openFile(context, path) },
                    onShareFile = { path -> FileUtils.shareFile(context, path) },
                    onQuickFilePicked = { uri, category ->
                        viewModel.addPickedFile(uri, category)
                        viewModel.navigateTo("SEND_SELECT")
                    },
                    userProfile = userProfile,
                    onSignOutClick = { viewModel.signOut() }
                )
            }

            "QR_SCANNER" -> {
                QrScannerScreen(
                    onQrScanned = { payload ->
                        viewModel.onQrCodeScanned(payload)
                    },
                    onBack = {
                        viewModel.navigateTo("HOME")
                    }
                )
            }

            "SEND_SELECT" -> {
                SendScreen(
                    activeCategory = activeCategory,
                    selectedItems = selectedItems,
                    pickedFiles = pickedFiles,
                    contacts = contacts,
                    apps = apps,
                    isLoading = isLoadingContent,
                    onCategorySelected = { viewModel.setCategory(it) },
                    onPickFiles = { uris, cat -> viewModel.addPickedFiles(uris, cat) },
                    onPickSingleFile = { uri, cat -> viewModel.addPickedFile(uri, cat) },
                    onToggleContact = { viewModel.toggleContact(it) },
                    onToggleApp = { viewModel.toggleApp(it) },
                    onRemoveItem = { viewModel.removeSelectedItem(it) },
                    onClearAll = { viewModel.clearAllSelected() },
                    onProceedToSend = {
                        if (selectedItems.isNotEmpty()) {
                            checkAndRequestPermissions {
                                viewModel.startSenderFlow()
                            }
                        }
                    },
                    onBack = { viewModel.navigateTo("HOME") }
                )
            }

            "DISCOVERY_RADAR" -> {
                DiscoveryScreen(
                    userDeviceName = viewModel.nearbyManager.deviceName,
                    discoveredDevices = discoveredDevices,
                    pendingConnection = pendingConnection,
                    permissionsState = nearbyPermissionsState,
                    onRequestPermissions = {
                        checkAndRequestPermissions {
                            if (!viewModel.nearbyManager.isDiscoveringActive) {
                                viewModel.nearbyManager.startDiscovery()
                            }
                        }
                    },
                    onDeviceSelected = { device ->
                        viewModel.connectToDevice(device)
                    },
                    onAcceptConnection = { endpointId ->
                        viewModel.acceptConnection(endpointId)
                        viewModel.startTransfer()
                    },
                    onRejectConnection = { endpointId ->
                        viewModel.rejectConnection(endpointId)
                    },
                    onStartSimulationTransfer = {
                        viewModel.startTransfer()
                    },
                    onOpenQrScanner = {
                        checkAndRequestPermissions {
                            viewModel.openQrScanner()
                        }
                    },
                    onBack = {
                        viewModel.nearbyManager.stopDiscovery()
                        viewModel.navigateTo("SEND_SELECT")
                    }
                )
            }

            "RECEIVE_BEACON" -> {
                ReceiveScreen(
                    deviceName = viewModel.nearbyManager.deviceName,
                    pendingConnection = pendingConnection,
                    permissionsState = nearbyPermissionsState,
                    onRequestPermissions = {
                        checkAndRequestPermissions {
                            if (!viewModel.nearbyManager.isAdvertisingActive) {
                                viewModel.nearbyManager.startAdvertising()
                            }
                        }
                    },
                    onAcceptConnection = { endpointId ->
                        viewModel.acceptConnection(endpointId)
                        viewModel.navigateTo("TRANSFERRING")
                    },
                    onRejectConnection = { endpointId ->
                        viewModel.rejectConnection(endpointId)
                    },
                    onHistoryClick = { viewModel.navigateTo("HISTORY") },
                    onBack = {
                        viewModel.nearbyManager.stopAdvertising()
                        viewModel.navigateTo("HOME")
                    }
                )
            }

            "TRANSFERRING" -> {
                TransferScreen(
                    progress = transferProgress,
                    peerDeviceName = connectedDevice?.name ?: "Paired Android Device",
                    items = selectedItems,
                    onCancel = { viewModel.cancelTransfer() },
                    onFinish = { viewModel.finishTransfer() }
                )
            }

            "HISTORY" -> {
                HistoryScreen(
                    historyList = historyList,
                    onOpenFile = { path -> FileUtils.openFile(context, path) },
                    onShareFile = { path -> FileUtils.shareFile(context, path) },
                    onDeleteItem = { id -> viewModel.deleteHistoryItem(id) },
                    onClearAll = { viewModel.clearAllHistory() },
                    onBack = { viewModel.navigateTo("HOME") }
                )
            }
        }
    }
}
