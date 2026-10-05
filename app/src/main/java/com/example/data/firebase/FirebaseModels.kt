package com.example.data.firebase

import com.google.firebase.Timestamp

data class UserProfile(
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val deviceName: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

data class TransferRecord(
    val id: String = "",
    val userId: String = "",
    val fileName: String = "",
    val fileSize: Long = 0L,
    val fileType: String = "FILES",
    val direction: String = "SEND",
    val peerName: String = "",
    val status: String = "COMPLETED",
    val createdAt: Timestamp? = null
)

data class UserSettings(
    val userId: String = "",
    val autoAccept: Boolean = false,
    val customDeviceName: String = "",
    val updatedAt: Timestamp? = null
)
