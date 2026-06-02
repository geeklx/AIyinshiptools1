package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audio_records")
data class AudioRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val filePath: String,
    val fileSize: String,
    val durationText: String,
    val durationMs: Long,
    val format: String, // "MP3" or "M4A"
    val timestamp: Long = System.currentTimeMillis(),
    val originalVideoName: String
)
