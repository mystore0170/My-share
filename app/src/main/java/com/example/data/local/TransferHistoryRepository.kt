package com.example.data.local

import kotlinx.coroutines.flow.Flow

class TransferHistoryRepository(private val dao: TransferHistoryDao) {
    val allHistory: Flow<List<TransferHistoryEntity>> = dao.getAllHistory()

    suspend fun insert(entity: TransferHistoryEntity): Long {
        return dao.insert(entity)
    }

    suspend fun deleteById(id: Long) {
        dao.deleteById(id)
    }

    suspend fun clearAll() {
        dao.clearAll()
    }
}
