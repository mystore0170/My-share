package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.FirestoreRepository
import com.example.data.firebase.GoogleAuthManager
import com.example.data.firebase.TransferRecord
import com.example.data.firebase.UserProfile
import com.example.data.local.NearShareDatabase
import com.example.data.local.TransferDirection
import com.example.data.local.TransferHistoryEntity
import com.example.data.local.TransferHistoryRepository
import com.example.data.model.AppItem
import com.example.data.model.ContactItem
import com.example.data.model.NearbyDevice
import com.example.data.model.TransferCategory
import com.example.data.model.TransferItem
import com.example.data.model.TransferProgress
import com.example.data.model.TransferStatus
import com.example.data.nearby.NearbyManager
import com.example.data.utils.AppHelper
import com.example.data.utils.ContactHelper
import com.example.data.utils.FileUtils
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class NearShareViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication<Application>().applicationContext
    val nearbyManager = NearbyManager(context)
    private val database = NearShareDatabase.getInstance(context)
    private val repository = TransferHistoryRepository(database.transferHistoryDao())

    val authManager = GoogleAuthManager(context)
    val firestoreRepository = FirestoreRepository(context)

    val currentUser: StateFlow<FirebaseUser?> = authManager.authStateFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = authManager.currentUser
        )

    private val _isAuthLoading = MutableStateFlow(false)
    val isAuthLoading: StateFlow<Boolean> = _isAuthLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    private val _cloudTransfers = MutableStateFlow<List<TransferRecord>>(emptyList())
    val cloudTransfers: StateFlow<List<TransferRecord>> = _cloudTransfers.asStateFlow()

    // Transfer History from Room DB
    val historyList: StateFlow<List<TransferHistoryEntity>> = repository.allHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Current screen role: "HOME", "SEND_SELECT", "DISCOVERY_RADAR", "RECEIVE_BEACON", "TRANSFERRING", "HISTORY"
    private val _currentScreen = MutableStateFlow("HOME")
    val currentScreen: StateFlow<String> = _currentScreen.asStateFlow()

    // Active Category Tab in SendScreen
    private val _activeCategory = MutableStateFlow(TransferCategory.FILES)
    val activeCategory: StateFlow<TransferCategory> = _activeCategory.asStateFlow()

    // Selected Items for transmission
    private val _selectedItems = MutableStateFlow<List<TransferItem>>(emptyList())
    val selectedItems: StateFlow<List<TransferItem>> = _selectedItems.asStateFlow()

    // Contacts available on device
    private val _contactsList = MutableStateFlow<List<ContactItem>>(emptyList())
    val contactsList: StateFlow<List<ContactItem>> = _contactsList.asStateFlow()

    // Installed Apps on device
    private val _installedApps = MutableStateFlow<List<AppItem>>(emptyList())
    val installedApps: StateFlow<List<AppItem>> = _installedApps.asStateFlow()

    // Custom files picked from storage
    private val _pickedFiles = MutableStateFlow<List<TransferItem>>(emptyList())
    val pickedFiles: StateFlow<List<TransferItem>> = _pickedFiles.asStateFlow()

    // Search query for contacts / apps / files
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Loading states
    private val _isLoadingContent = MutableStateFlow(false)
    val isLoadingContent: StateFlow<Boolean> = _isLoadingContent.asStateFlow()

    // Nearby States delegating to manager
    val discoveredDevices: StateFlow<List<NearbyDevice>> = nearbyManager.discoveredDevices
    val connectedDevice: StateFlow<NearbyDevice?> = nearbyManager.connectedDevice
    val pendingConnection: StateFlow<NearbyDevice?> = nearbyManager.pendingConnection
    val transferProgress: StateFlow<TransferProgress> = nearbyManager.transferProgress

    init {
        // Collect incoming file events and record them in Room Database & Firestore
        viewModelScope.launch {
            nearbyManager.incomingFileEvents.collect { (name, file) ->
                val entity = TransferHistoryEntity(
                    fileName = name,
                    fileSize = file.length(),
                    fileType = file.extension.uppercase(),
                    direction = TransferDirection.RECEIVED.name,
                    peerDeviceName = nearbyManager.connectedDevice.value?.name ?: "Nearby Sender",
                    timestamp = System.currentTimeMillis(),
                    filePath = file.absolutePath,
                    isSuccess = true
                )
                repository.insert(entity)

                val uid = authManager.currentUser?.uid
                if (uid != null) {
                    val record = TransferRecord(
                        id = UUID.randomUUID().toString(),
                        userId = uid,
                        fileName = name,
                        fileSize = file.length(),
                        fileType = file.extension.uppercase().ifEmpty { "FILES" },
                        direction = "RECEIVE",
                        peerName = nearbyManager.connectedDevice.value?.name ?: "Nearby Sender",
                        status = "COMPLETED"
                    )
                    firestoreRepository.recordTransfer(record)
                }
            }
        }

        // Observe Auth State: sync profile and cloud transfers when user is signed in
        viewModelScope.launch {
            currentUser.collect { user ->
                if (user != null) {
                    val profile = UserProfile(
                        userId = user.uid,
                        displayName = user.displayName ?: "NearShare User",
                        email = user.email ?: "",
                        photoUrl = user.photoUrl?.toString(),
                        deviceName = nearbyManager.deviceName
                    )
                    try {
                        firestoreRepository.saveUserProfile(profile)
                    } catch (e: Exception) {
                        android.util.Log.e("NearShareVM", "Failed to save profile", e)
                    }

                    launch {
                        firestoreRepository.observeUserProfile(user.uid).collect {
                            _userProfile.value = it
                        }
                    }
                    launch {
                        firestoreRepository.observeTransferRecords(user.uid).collect {
                            _cloudTransfers.value = it
                        }
                    }
                } else {
                    _userProfile.value = null
                    _cloudTransfers.value = emptyList()
                }
            }
        }
    }

    fun navigateTo(screen: String) {
        _currentScreen.value = screen
    }

    fun setCategory(category: TransferCategory) {
        _activeCategory.value = category
        _searchQuery.value = ""
        loadCategoryContentIfNeeded(category)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun loadCategoryContentIfNeeded(category: TransferCategory) {
        viewModelScope.launch(Dispatchers.IO) {
            when (category) {
                TransferCategory.CONTACTS -> {
                    if (_contactsList.value.isEmpty()) {
                        _isLoadingContent.value = true
                        try {
                            val contacts = ContactHelper.fetchContacts(context)
                            _contactsList.value = contacts
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            _isLoadingContent.value = false
                        }
                    }
                }
                TransferCategory.APPS -> {
                    if (_installedApps.value.isEmpty()) {
                        _isLoadingContent.value = true
                        try {
                            val apps = AppHelper.getInstalledApps(context)
                            _installedApps.value = apps
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            _isLoadingContent.value = false
                        }
                    }
                }
                else -> {}
            }
        }
    }

    // Toggle contact selection
    fun toggleContact(contact: ContactItem) {
        _contactsList.update { list ->
            list.map { if (it.id == contact.id) it.copy(isSelected = !it.isSelected) else it }
        }

        val updated = _contactsList.value.firstOrNull { it.id == contact.id }
        if (updated?.isSelected == true) {
            val vcfString = ContactHelper.generateVCard(updated.name, updated.phoneNumber, updated.email)
            val transferItem = TransferItem(
                id = "contact_${updated.id}",
                name = "${updated.name}.vcf",
                size = vcfString.toByteArray().size.toLong(),
                mimeType = "text/x-vcard",
                category = TransferCategory.CONTACTS,
                contactData = vcfString,
                isSelected = true
            )
            _selectedItems.update { it + transferItem }
        } else {
            _selectedItems.update { list -> list.filterNot { it.id == "contact_${contact.id}" } }
        }
    }

    // Toggle app selection
    fun toggleApp(app: AppItem) {
        _installedApps.update { list ->
            list.map { if (it.packageName == app.packageName) it.copy(isSelected = !it.isSelected) else it }
        }

        val updated = _installedApps.value.firstOrNull { it.packageName == app.packageName }
        if (updated?.isSelected == true) {
            val transferItem = TransferItem(
                id = "app_${updated.packageName}",
                name = "${updated.appName}.apk",
                size = updated.sizeBytes,
                mimeType = "application/vnd.android.package-archive",
                category = TransferCategory.APPS,
                apkPath = updated.apkPath,
                apkPackageName = updated.packageName,
                isSelected = true
            )
            _selectedItems.update { it + transferItem }
        } else {
            _selectedItems.update { list -> list.filterNot { it.id == "app_${app.packageName}" } }
        }
    }

    // Add files picked from SAF
    fun addPickedFiles(uris: List<Uri>, category: TransferCategory) {
        viewModelScope.launch(Dispatchers.IO) {
            val newItems = mutableListOf<TransferItem>()
            for (uri in uris) {
                var displayName = "File_${System.currentTimeMillis()}"
                var size = 0L

                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIndex >= 0) displayName = cursor.getString(nameIndex) ?: displayName
                        if (sizeIndex >= 0) size = cursor.getLong(sizeIndex)
                    }
                }

                val mimeType = context.contentResolver.getType(uri) ?: "*/*"
                val item = TransferItem(
                    id = UUID.randomUUID().toString(),
                    name = displayName,
                    size = if (size > 0) size else 1024L * 256,
                    uri = uri,
                    mimeType = mimeType,
                    category = category,
                    isSelected = true
                )
                newItems.add(item)
            }

            _pickedFiles.update { it + newItems }
            _selectedItems.update { it + newItems }
        }
    }

    // Add a single file picked using ActivityResultContracts.GetContent()
    fun addPickedFile(uri: Uri, preferredCategory: TransferCategory? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            var displayName = "File_${System.currentTimeMillis()}"
            var size = 0L

            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex >= 0) displayName = cursor.getString(nameIndex) ?: displayName
                    if (sizeIndex >= 0) size = cursor.getLong(sizeIndex)
                }
            }

            val mimeType = context.contentResolver.getType(uri) ?: "*/*"
            val category = preferredCategory ?: FileUtils.getCategoryFromMimeType(mimeType)
            val item = TransferItem(
                id = UUID.randomUUID().toString(),
                name = displayName,
                size = if (size > 0) size else 1024L * 256,
                uri = uri,
                mimeType = mimeType,
                category = category,
                isSelected = true
            )

            _pickedFiles.update { it + item }
            _selectedItems.update { it + item }
            _userMessage.value = "Selected \"$displayName\" for transfer"
        }
    }

    fun removeSelectedItem(itemId: String) {
        _selectedItems.update { list -> list.filterNot { it.id == itemId } }
        _pickedFiles.update { list -> list.filterNot { it.id == itemId } }

        if (itemId.startsWith("contact_")) {
            val contactId = itemId.removePrefix("contact_")
            _contactsList.update { list -> list.map { if (it.id == contactId) it.copy(isSelected = false) else it } }
        } else if (itemId.startsWith("app_")) {
            val pkg = itemId.removePrefix("app_")
            _installedApps.update { list -> list.map { if (it.packageName == pkg) it.copy(isSelected = false) else it } }
        }
    }

    fun clearAllSelected() {
        _selectedItems.value = emptyList()
        _pickedFiles.value = emptyList()
        _contactsList.update { list -> list.map { it.copy(isSelected = false) } }
        _installedApps.update { list -> list.map { it.copy(isSelected = false) } }
    }

    // MARK: - Sender Flow
    fun startSenderFlow() {
        _currentScreen.value = "DISCOVERY_RADAR"
        nearbyManager.startDiscovery()
    }

    fun connectToDevice(device: NearbyDevice) {
        nearbyManager.requestConnection(device.endpointId, device.name)
    }

    // MARK: - Receiver Flow
    fun startReceiverFlow() {
        _currentScreen.value = "RECEIVE_BEACON"
        nearbyManager.startAdvertising()
    }

    fun acceptConnection(endpointId: String) {
        nearbyManager.acceptConnection(endpointId)
    }

    fun rejectConnection(endpointId: String) {
        nearbyManager.rejectConnection(endpointId)
    }

    // MARK: - Transfer Trigger
    fun startTransfer() {
        _currentScreen.value = "TRANSFERRING"
        val itemsToSend = _selectedItems.value

        if (nearbyManager.connectedDevice.value != null) {
            // Real Nearby Connections transfer
            nearbyManager.sendItems(itemsToSend)
            // Log to History & Cloud Firestore
            viewModelScope.launch {
                val uid = authManager.currentUser?.uid
                for (item in itemsToSend) {
                    repository.insert(
                        TransferHistoryEntity(
                            fileName = item.name,
                            fileSize = item.size,
                            fileType = item.category.name,
                            direction = TransferDirection.SENT.name,
                            peerDeviceName = nearbyManager.connectedDevice.value?.name ?: "Nearby Receiver",
                            timestamp = System.currentTimeMillis(),
                            filePath = item.apkPath ?: item.uri?.toString(),
                            isSuccess = true
                        )
                    )
                    if (uid != null) {
                        val record = TransferRecord(
                            id = UUID.randomUUID().toString(),
                            userId = uid,
                            fileName = item.name,
                            fileSize = item.size,
                            fileType = item.category.name,
                            direction = "SEND",
                            peerName = nearbyManager.connectedDevice.value?.name ?: "Nearby Receiver",
                            status = "COMPLETED"
                        )
                        firestoreRepository.recordTransfer(record)
                    }
                }
            }
        } else {
            // Simulation Mode (for testing on emulator or standalone demonstration)
            nearbyManager.simulateTransfer(itemsToSend) { completedFiles ->
                viewModelScope.launch {
                    val uid = authManager.currentUser?.uid
                    for ((index, item) in itemsToSend.withIndex()) {
                        val file = completedFiles.getOrNull(index)
                        repository.insert(
                            TransferHistoryEntity(
                                fileName = item.name,
                                fileSize = item.size,
                                fileType = item.category.name,
                                direction = TransferDirection.SENT.name,
                                peerDeviceName = "Paired Android Device",
                                timestamp = System.currentTimeMillis(),
                                filePath = file?.absolutePath,
                                isSuccess = true
                            )
                        )
                        if (uid != null) {
                            val record = TransferRecord(
                                id = UUID.randomUUID().toString(),
                                userId = uid,
                                fileName = item.name,
                                fileSize = item.size,
                                fileType = item.category.name,
                                direction = "SEND",
                                peerName = "Paired Android Device",
                                status = "COMPLETED"
                            )
                            firestoreRepository.recordTransfer(record)
                        }
                    }
                }
            }
        }
    }

    fun cancelTransfer() {
        nearbyManager.disconnect()
        _currentScreen.value = "HOME"
    }

    fun finishTransfer() {
        nearbyManager.disconnect()
        clearAllSelected()
        _currentScreen.value = "HOME"
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch {
            repository.deleteById(id)
        }
    }

    // Status message for QR scan or UI toasts
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun openQrScanner() {
        _currentScreen.value = "QR_SCANNER"
        nearbyManager.startDiscovery()
    }

    fun onQrCodeScanned(payload: String) {
        val qrData = com.example.data.utils.QrCodeGenerator.parsePairingPayload(payload)
        val devName = qrData?.deviceName ?: "Nearby Device"
        _userMessage.value = "Pairing with $devName..."

        nearbyManager.pairViaQrCode(payload) { matchedDevice ->
            _userMessage.value = "Connected to ${matchedDevice.name}!"
        }

        if (_selectedItems.value.isNotEmpty()) {
            _currentScreen.value = "DISCOVERY_RADAR"
        } else {
            _currentScreen.value = "SEND_SELECT"
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    fun signInWithGoogle(activityContext: Context) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            val result = authManager.signInWithGoogle(activityContext)
            result.onSuccess { user ->
                _isAuthLoading.value = false
                _userMessage.value = "Welcome, ${user.displayName ?: user.email}!"
            }.onFailure { e ->
                _isAuthLoading.value = false
                if (e !is androidx.credentials.exceptions.GetCredentialCancellationException) {
                    _authError.value = e.localizedMessage ?: "Failed to sign in with Google"
                }
            }
        }
    }

    fun signOut() {
        authManager.signOut()
        _userProfile.value = null
        _cloudTransfers.value = emptyList()
        _userMessage.value = "Signed out"
    }

    override fun onCleared() {
        super.onCleared()
        nearbyManager.disconnect()
    }
}
