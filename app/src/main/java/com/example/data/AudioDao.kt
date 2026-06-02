package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioDao {
    @Query("SELECT * FROM audio_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<AudioRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: AudioRecord): Long

    @Query("DELETE FROM audio_records WHERE id = :id")
    suspend fun deleteRecordById(id: Int)

    @Query("SELECT * FROM audio_records WHERE id = :id")
    suspend fun getRecordById(id: Int): AudioRecord?
}
