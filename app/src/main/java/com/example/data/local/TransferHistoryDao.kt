package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferHistoryDao {
    @Query("SELECT * FROM transfer_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<TransferHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TransferHistoryEntity): Long

    @Query("DELETE FROM transfer_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transfer_history")
    suspend fun clearAll()
}
