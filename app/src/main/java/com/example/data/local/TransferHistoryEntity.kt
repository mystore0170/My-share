package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TransferDirection {
    SENT,
    RECEIVED
}

@Entity(tableName = "transfer_history")
data class TransferHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val fileName: String,
    val fileSize: Long,
    val fileType: String,
    val direction: String, // SENT or RECEIVED
    val peerDeviceName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val filePath: String? = null,
    val isSuccess: Boolean = true
)
