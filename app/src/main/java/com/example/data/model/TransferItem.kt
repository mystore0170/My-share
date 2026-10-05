package com.example.data.model

import android.net.Uri

enum class TransferCategory(val title: String) {
    FILES("Files"),
    PHOTOS("Photos"),
    VIDEOS("Videos"),
    AUDIO("Audio"),
    APPS("Apps"),
    CONTACTS("Contacts")
}

data class TransferItem(
    val id: String,
    val name: String,
    val size: Long,
    val uri: Uri? = null,
    val mimeType: String = "*/*",
    val category: TransferCategory = TransferCategory.FILES,
    val contactData: String? = null, // VCF string payload for contacts
    val apkPath: String? = null,
    val apkPackageName: String? = null,
    val isSelected: Boolean = false
)

data class ContactItem(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val email: String? = null,
    val isSelected: Boolean = false
)

data class AppItem(
    val appName: String,
    val packageName: String,
    val apkPath: String,
    val sizeBytes: Long,
    val versionName: String,
    val isSelected: Boolean = false
)

data class NearbyDevice(
    val endpointId: String,
    val name: String,
    val authCode: String? = null,
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val distanceHint: Float = 0.5f,
    val angleRad: Float = 0f
)

enum class TransferStatus {
    IDLE,
    CONNECTING,
    CONNECTED,
    TRANSFERRING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class TransferProgress(
    val status: TransferStatus = TransferStatus.IDLE,
    val currentFileName: String = "",
    val currentFileIndex: Int = 0,
    val totalFiles: Int = 0,
    val bytesTransferred: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val etaSeconds: Long = 0L,
    val errorMessage: String? = null
)

data class ManifestItem(
    val id: String,
    val name: String,
    val size: Long,
    val mimeType: String,
    val category: String,
    val contactVcf: String? = null
)

data class TransferManifest(
    val totalFiles: Int,
    val totalSize: Long,
    val items: List<ManifestItem>
)
