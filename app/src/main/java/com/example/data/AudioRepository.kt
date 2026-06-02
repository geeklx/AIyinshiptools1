package com.example.data

import kotlinx.coroutines.flow.Flow

class AudioRepository(private val audioDao: AudioDao) {
    val allRecords: Flow<List<AudioRecord>> = audioDao.getAllRecords()

    suspend fun insert(record: AudioRecord): Long {
        return audioDao.insertRecord(record)
    }

    suspend fun deleteById(id: Int) {
        audioDao.deleteRecordById(id)
    }

    suspend fun getById(id: Int): AudioRecord? {
        return audioDao.getRecordById(id)
    }
}
