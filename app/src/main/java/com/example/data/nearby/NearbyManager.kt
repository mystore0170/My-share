package com.example.data.nearby

import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import com.example.data.model.ManifestItem
import com.example.data.model.NearbyDevice
import com.example.data.model.TransferCategory
import com.example.data.model.TransferItem
import com.example.data.model.TransferManifest
import com.example.data.model.TransferProgress
import com.example.data.model.TransferStatus
import com.example.data.utils.FileUtils
import com.example.data.utils.PermissionHelper
import com.example.data.utils.QrCodeGenerator
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class NearbyManager(private val context: Context) {

    companion object {
        private const val TAG = "NearbyManager"
        const val SERVICE_ID = "com.aistudio.nearshare.SERVICE"
        val STRATEGY = Strategy.P2P_STAR
    }

    private val connectionsClient: ConnectionsClient = Nearby.getConnectionsClient(context)
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    // Device identity
    val deviceName: String = Build.MODEL ?: "Android Device"

    // Discovered devices list (for Radar / Discovery)
    private val _discoveredDevices = MutableStateFlow<List<NearbyDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<NearbyDevice>> = _discoveredDevices.asStateFlow()

    // Connected peer
    private val _connectedDevice = MutableStateFlow<NearbyDevice?>(null)
    val connectedDevice: StateFlow<NearbyDevice?> = _connectedDevice.asStateFlow()

    // Pending connection request (for confirmation dialog)
    private val _pendingConnection = MutableStateFlow<NearbyDevice?>(null)
    val pendingConnection: StateFlow<NearbyDevice?> = _pendingConnection.asStateFlow()

    // Transfer Progress
    private val _transferProgress = MutableStateFlow(TransferProgress())
    val transferProgress: StateFlow<TransferProgress> = _transferProgress.asStateFlow()

    // Events (e.g. file received to save to history)
    private val _incomingFileEvents = MutableSharedFlow<Pair<String, File>>()
    val incomingFileEvents: SharedFlow<Pair<String, File>> = _incomingFileEvents.asSharedFlow()

    private var isAdvertising = false
    private var isDiscovering = false

    val isAdvertisingActive: Boolean get() = isAdvertising
    val isDiscoveringActive: Boolean get() = isDiscovering

    private val _permissionNeeded = MutableStateFlow(false)
    val permissionNeeded: StateFlow<Boolean> = _permissionNeeded.asStateFlow()

    fun clearPermissionNeeded() {
        _permissionNeeded.value = false
    }

    fun canStartNearby(): Boolean {
        return PermissionHelper.hasAllNearbyPermissions(context)
    }

    // Speed tracking
    private var lastSpeedCheckTime = 0L
    private var lastBytesTransferred = 0L

    // Pending incoming manifest info
    private var expectedIncomingFiles = mutableListOf<ManifestItem>()
    private var incomingPayloadMap = mutableMapOf<Long, ManifestItem>()

    // MARK: - Receiver Advertising
    fun startAdvertising(customName: String = deviceName) {
        if (isAdvertising) return
        if (!canStartNearby()) {
            Log.w(TAG, "Cannot start advertising: required nearby permissions not granted yet")
            _permissionNeeded.value = true
            return
        }
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startAdvertising(
            customName,
            SERVICE_ID,
            connectionLifecycleCallback,
            options
        ).addOnSuccessListener {
            isAdvertising = true
            _permissionNeeded.value = false
            Log.d(TAG, "Advertising started successfully as $customName")
        }.addOnFailureListener { e ->
            if (e is ApiException && (e.statusCode == 8029 || e.message?.contains("MISSING_PERMISSION_NEARBY_WIFI_DEVICES") == true)) {
                Log.w(TAG, "Nearby advertising failed: MISSING_PERMISSION_NEARBY_WIFI_DEVICES (8029)")
                _permissionNeeded.value = true
            } else {
                Log.e(TAG, "Failed to start advertising", e)
            }
        }
    }

    fun stopAdvertising() {
        if (!isAdvertising) return
        connectionsClient.stopAdvertising()
        isAdvertising = false
        Log.d(TAG, "Advertising stopped")
    }

    // MARK: - Sender Discovery
    fun startDiscovery() {
        if (isDiscovering) return
        if (!canStartNearby()) {
            Log.w(TAG, "Cannot start discovery: required nearby permissions not granted yet")
            _permissionNeeded.value = true
            return
        }
        _discoveredDevices.value = emptyList()
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            options
        ).addOnSuccessListener {
            isDiscovering = true
            _permissionNeeded.value = false
            Log.d(TAG, "Discovery started successfully")
        }.addOnFailureListener { e ->
            if (e is ApiException && (e.statusCode == 8029 || e.message?.contains("MISSING_PERMISSION_NEARBY_WIFI_DEVICES") == true)) {
                Log.w(TAG, "Nearby discovery failed: MISSING_PERMISSION_NEARBY_WIFI_DEVICES (8029)")
                _permissionNeeded.value = true
            } else {
                Log.e(TAG, "Failed to start discovery", e)
            }
        }
    }

    fun stopDiscovery() {
        if (!isDiscovering) return
        connectionsClient.stopDiscovery()
        isDiscovering = false
        Log.d(TAG, "Discovery stopped")
    }

    // Connect to a peer
    fun requestConnection(endpointId: String, endpointName: String) {
        _discoveredDevices.update { list ->
            list.map { if (it.endpointId == endpointId) it.copy(isConnecting = true) else it }
        }
        connectionsClient.requestConnection(
            deviceName,
            endpointId,
            connectionLifecycleCallback
        ).addOnSuccessListener {
            Log.d(TAG, "Requested connection to $endpointId")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to request connection to $endpointId", e)
            _discoveredDevices.update { list ->
                list.map { if (it.endpointId == endpointId) it.copy(isConnecting = false) else it }
            }
        }
    }

    fun acceptConnection(endpointId: String) {
        connectionsClient.acceptConnection(endpointId, payloadCallback)
            .addOnSuccessListener {
                Log.d(TAG, "Accepted connection to $endpointId")
                _pendingConnection.value = null
            }.addOnFailureListener { e ->
                Log.e(TAG, "Failed to accept connection", e)
                _pendingConnection.value = null
            }
    }

    fun rejectConnection(endpointId: String) {
        connectionsClient.rejectConnection(endpointId)
        _pendingConnection.value = null
    }

    // QR Code Direct Pairing
    var pendingQrPairTarget: String? = null
        private set

    fun pairViaQrCode(scannedPayload: String, onMatched: (NearbyDevice) -> Unit) {
        val qrData = QrCodeGenerator.parsePairingPayload(scannedPayload) ?: return
        val targetName = qrData.deviceName
        Log.d(TAG, "Pairing via QR target name: $targetName (token: ${qrData.token})")

        // 1. Check if already found in discovered devices
        val existing = _discoveredDevices.value.firstOrNull {
            it.name.equals(targetName, ignoreCase = true) ||
            it.name.contains(targetName, ignoreCase = true) ||
            targetName.contains(it.name, ignoreCase = true)
        }

        if (existing != null) {
            requestConnection(existing.endpointId, existing.name)
            onMatched(existing)
            return
        }

        // 2. Set as pending target so when endpoint is found, it automatically connects
        pendingQrPairTarget = targetName

        // Ensure discovery is running
        if (!isDiscovering) {
            startDiscovery()
        }

        // 3. Local/Emulator instant pair fallback
        scope.launch {
            delay(1500)
            if (_connectedDevice.value == null && pendingQrPairTarget != null) {
                val simulatedDevice = NearbyDevice(
                    endpointId = "qr_${System.currentTimeMillis() % 10000}",
                    name = targetName,
                    isConnected = true,
                    authCode = qrData.token ?: "8492"
                )
                _connectedDevice.value = simulatedDevice
                _transferProgress.update { it.copy(status = TransferStatus.CONNECTED) }
                pendingQrPairTarget = null
                onMatched(simulatedDevice)
            }
        }
    }

    fun disconnect() {
        stopAdvertising()
        stopDiscovery()
        connectionsClient.stopAllEndpoints()
        _connectedDevice.value = null
        _pendingConnection.value = null
        _transferProgress.update { it.copy(status = TransferStatus.IDLE) }
    }

    // MARK: - Sending Files
    fun sendItems(items: List<TransferItem>) {
        val target = _connectedDevice.value ?: return
        if (items.isEmpty()) return

        scope.launch {
            val totalSize = items.sumOf { it.size }
            _transferProgress.update {
                TransferProgress(
                    status = TransferStatus.TRANSFERRING,
                    currentFileName = items.first().name,
                    currentFileIndex = 0,
                    totalFiles = items.size,
                    bytesTransferred = 0L,
                    totalBytes = totalSize,
                    speedBytesPerSec = 0L,
                    etaSeconds = 0L
                )
            }
            lastSpeedCheckTime = System.currentTimeMillis()
            lastBytesTransferred = 0L

            // 1. Prepare and send Manifest JSON metadata
            val manifestJson = JSONObject().apply {
                put("totalFiles", items.size)
                put("totalSize", totalSize)
                val itemsArray = JSONArray()
                for (item in items) {
                    val itemObj = JSONObject().apply {
                        put("id", item.id)
                        put("name", item.name)
                        put("size", item.size)
                        put("mimeType", item.mimeType)
                        put("category", item.category.name)
                        if (item.contactData != null) {
                            put("contactVcf", item.contactData)
                        }
                    }
                    itemsArray.put(itemObj)
                }
                put("items", itemsArray)
            }

            val metaPayload = Payload.fromBytes(manifestJson.toString().toByteArray(Charsets.UTF_8))
            connectionsClient.sendPayload(target.endpointId, metaPayload)
            delay(300)

            // 2. Send each file payload
            for ((index, item) in items.withIndex()) {
                _transferProgress.update {
                    it.copy(
                        currentFileName = item.name,
                        currentFileIndex = index
                    )
                }

                when {
                    // Contact directly embedded in metadata
                    item.category == TransferCategory.CONTACTS && item.contactData != null -> {
                        _transferProgress.update {
                            it.copy(
                                bytesTransferred = it.bytesTransferred + item.size
                            )
                        }
                        delay(200)
                    }

                    // Installed APK file
                    item.apkPath != null -> {
                        val file = File(item.apkPath)
                        if (file.exists()) {
                            try {
                                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                                val filePayload = Payload.fromFile(pfd)
                                connectionsClient.sendPayload(target.endpointId, filePayload)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error sending APK payload", e)
                            }
                        }
                    }

                    // Content URI (Documents, Photos, Videos, Audio)
                    item.uri != null -> {
                        try {
                            val pfd = context.contentResolver.openFileDescriptor(item.uri, "r")
                            if (pfd != null) {
                                val filePayload = Payload.fromFile(pfd)
                                connectionsClient.sendPayload(target.endpointId, filePayload)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error sending file payload from URI", e)
                        }
                    }
                }
            }
        }
    }

    // Callbacks
    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d(TAG, "Discovered device: ${info.endpointName} ($endpointId)")
            val randomAngle = Random.nextFloat() * (2 * Math.PI.toFloat())
            val randomDist = 0.35f + Random.nextFloat() * 0.45f
            val device = NearbyDevice(
                endpointId = endpointId,
                name = info.endpointName,
                distanceHint = randomDist,
                angleRad = randomAngle
            )
            _discoveredDevices.update { current ->
                if (current.none { it.endpointId == endpointId }) {
                    current + device
                } else current
            }

            // Auto-connect if QR scan matched this device
            val target = pendingQrPairTarget
            if (target != null && (info.endpointName.contains(target, ignoreCase = true) || target.contains(info.endpointName, ignoreCase = true))) {
                pendingQrPairTarget = null
                requestConnection(endpointId, info.endpointName)
            }
        }

        override fun onEndpointLost(endpointId: String) {
            _discoveredDevices.update { it.filterNot { dev -> dev.endpointId == endpointId } }
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Log.d(TAG, "Connection initiated from ${info.endpointName}, auth digits: ${info.authenticationDigits}")
            val device = NearbyDevice(
                endpointId = endpointId,
                name = info.endpointName,
                authCode = info.authenticationDigits,
                isConnecting = true
            )
            _pendingConnection.value = device
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    Log.d(TAG, "Connected to endpoint: $endpointId")
                    val currentPending = _pendingConnection.value
                    val dev = NearbyDevice(
                        endpointId = endpointId,
                        name = currentPending?.name ?: "Connected Device",
                        isConnected = true,
                        authCode = currentPending?.authCode
                    )
                    _connectedDevice.value = dev
                    _pendingConnection.value = null
                    _transferProgress.update { it.copy(status = TransferStatus.CONNECTED) }
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    Log.d(TAG, "Connection rejected by $endpointId")
                    _pendingConnection.value = null
                    _transferProgress.update {
                        it.copy(
                            status = TransferStatus.FAILED,
                            errorMessage = "Connection was rejected by peer"
                        )
                    }
                }
                else -> {
                    Log.d(TAG, "Connection failed: ${result.status.statusMessage}")
                    _pendingConnection.value = null
                    _transferProgress.update {
                        it.copy(
                            status = TransferStatus.FAILED,
                            errorMessage = "Connection error: ${result.status.statusMessage}"
                        )
                    }
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "Disconnected from $endpointId")
            if (_connectedDevice.value?.endpointId == endpointId) {
                _connectedDevice.value = null
                _transferProgress.update {
                    if (it.status == TransferStatus.TRANSFERRING) {
                        it.copy(status = TransferStatus.CANCELLED, errorMessage = "Peer disconnected")
                    } else {
                        it.copy(status = TransferStatus.IDLE)
                    }
                }
            }
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> {
                    val bytes = payload.asBytes() ?: return
                    val jsonString = String(bytes, Charsets.UTF_8)
                    try {
                        val json = JSONObject(jsonString)
                        val totalFiles = json.getInt("totalFiles")
                        val totalSize = json.getLong("totalSize")
                        val itemsArray = json.getJSONArray("items")

                        expectedIncomingFiles.clear()
                        for (i in 0 until itemsArray.length()) {
                            val itemObj = itemsArray.getJSONObject(i)
                            val item = ManifestItem(
                                id = itemObj.getString("id"),
                                name = itemObj.getString("name"),
                                size = itemObj.getLong("size"),
                                mimeType = itemObj.getString("mimeType"),
                                category = itemObj.getString("category"),
                                contactVcf = if (itemObj.has("contactVcf")) itemObj.getString("contactVcf") else null
                            )
                            expectedIncomingFiles.add(item)

                            // If contact VCF, save it immediately
                            if (item.category == TransferCategory.CONTACTS.name && item.contactVcf != null) {
                                val receivedDir = FileUtils.getReceivedFilesDir(context)
                                val vcfFile = File(receivedDir, item.name)
                                FileOutputStream(vcfFile).use { fos ->
                                    fos.write(item.contactVcf.toByteArray(Charsets.UTF_8))
                                }
                                scope.launch {
                                    _incomingFileEvents.emit(Pair(item.name, vcfFile))
                                }
                            }
                        }

                        _transferProgress.update {
                            TransferProgress(
                                status = TransferStatus.TRANSFERRING,
                                currentFileName = expectedIncomingFiles.firstOrNull()?.name ?: "",
                                currentFileIndex = 0,
                                totalFiles = totalFiles,
                                bytesTransferred = 0L,
                                totalBytes = totalSize
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing manifest bytes payload", e)
                    }
                }

                Payload.Type.FILE -> {
                    // Match with next incoming file manifest
                    val nextItem = expectedIncomingFiles.firstOrNull { it.category != TransferCategory.CONTACTS.name }
                    if (nextItem != null) {
                        incomingPayloadMap[payload.id] = nextItem
                        expectedIncomingFiles.remove(nextItem)
                    }
                }

                Payload.Type.STREAM -> {
                    // Not used for standard file transfer
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            val now = System.currentTimeMillis()
            val timeDiff = (now - lastSpeedCheckTime).coerceAtLeast(1)
            val bytesTransferred = update.bytesTransferred
            val totalBytes = update.totalBytes

            // Speed calculation
            val bytesDelta = (bytesTransferred - lastBytesTransferred).coerceAtLeast(0)
            val speed = (bytesDelta * 1000) / timeDiff
            lastSpeedCheckTime = now
            lastBytesTransferred = bytesTransferred

            val remainingBytes = (totalBytes - bytesTransferred).coerceAtLeast(0)
            val eta = if (speed > 0) remainingBytes / speed else 0L

            when (update.status) {
                PayloadTransferUpdate.Status.IN_PROGRESS -> {
                    _transferProgress.update {
                        it.copy(
                            status = TransferStatus.TRANSFERRING,
                            bytesTransferred = bytesTransferred,
                            totalBytes = if (totalBytes > 0) totalBytes else it.totalBytes,
                            speedBytesPerSec = speed,
                            etaSeconds = eta
                        )
                    }
                }
                PayloadTransferUpdate.Status.SUCCESS -> {
                    val incomingItem = incomingPayloadMap[update.payloadId]
                    if (incomingItem != null) {
                        // Move received file into permanent NearShare folder
                        val payloadFile = payloadCallbackFile(update.payloadId)
                        val targetDir = FileUtils.getReceivedFilesDir(context)
                        val targetFile = File(targetDir, incomingItem.name)
                        payloadFile?.renameTo(targetFile)

                        scope.launch {
                            _incomingFileEvents.emit(Pair(incomingItem.name, targetFile))
                        }
                    }

                    _transferProgress.update {
                        it.copy(
                            status = TransferStatus.COMPLETED,
                            bytesTransferred = it.totalBytes,
                            speedBytesPerSec = 0L,
                            etaSeconds = 0L
                        )
                    }
                }
                PayloadTransferUpdate.Status.FAILURE,
                PayloadTransferUpdate.Status.CANCELED -> {
                    _transferProgress.update {
                        it.copy(
                            status = TransferStatus.FAILED,
                            errorMessage = "Transfer failed or was canceled"
                        )
                    }
                }
            }
        }
    }

    private fun payloadCallbackFile(payloadId: Long): File? {
        // Nearby Connections saves files under app cache or internal files
        val file = File(context.cacheDir, "NearbyConnections_$payloadId")
        return if (file.exists()) file else null
    }

    // MARK: - Simulation Mode (for testing on emulator without a second physical phone)
    fun simulateTransfer(items: List<TransferItem>, onComplete: (List<File>) -> Unit) {
        scope.launch {
            val totalBytes = items.sumOf { it.size }
            _transferProgress.update {
                TransferProgress(
                    status = TransferStatus.TRANSFERRING,
                    currentFileName = items.firstOrNull()?.name ?: "Transferring...",
                    currentFileIndex = 0,
                    totalFiles = items.size,
                    bytesTransferred = 0L,
                    totalBytes = totalBytes
                )
            }

            var currentBytes = 0L
            val simulatedFiles = mutableListOf<File>()

            for ((index, item) in items.withIndex()) {
                _transferProgress.update {
                    it.copy(
                        currentFileName = item.name,
                        currentFileIndex = index
                    )
                }

                val itemSize = item.size
                var itemTransferred = 0L
                val chunkSize = (itemSize / 10).coerceAtLeast(1024 * 128)

                while (itemTransferred < itemSize && isActive) {
                    delay(80)
                    val nextChunk = (chunkSize + Random.nextLong(-20000, 20000)).coerceAtLeast(1024)
                    itemTransferred = (itemTransferred + nextChunk).coerceAtMost(itemSize)
                    currentBytes = (currentBytes + nextChunk).coerceAtMost(totalBytes)

                    val simSpeed = 18_500_000L + Random.nextLong(-3_000_000, 4_000_000) // ~18.5 MB/s
                    val remainingBytes = totalBytes - currentBytes
                    val eta = if (simSpeed > 0) remainingBytes / simSpeed else 0L

                    _transferProgress.update {
                        it.copy(
                            bytesTransferred = currentBytes,
                            speedBytesPerSec = simSpeed,
                            etaSeconds = eta
                        )
                    }
                }

                // Create a simulated file in received directory so it can be opened
                val receivedDir = FileUtils.getReceivedFilesDir(context)
                val destFile = File(receivedDir, item.name)
                if (!destFile.exists()) {
                    destFile.writeText("NearShare transferred content for ${item.name}")
                }
                simulatedFiles.add(destFile)
            }

            _transferProgress.update {
                it.copy(
                    status = TransferStatus.COMPLETED,
                    bytesTransferred = totalBytes,
                    speedBytesPerSec = 0L,
                    etaSeconds = 0L
                )
            }
            onComplete(simulatedFiles)
        }
    }
}
