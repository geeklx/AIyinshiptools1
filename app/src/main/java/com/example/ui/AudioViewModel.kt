package com.example.ui

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AudioRecord
import com.example.data.AudioRepository
import com.example.utils.AudioExtractor
import com.example.utils.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

sealed class ExtractionState {
    object Idle : ExtractionState()
    object Processing : ExtractionState()
    data class Success(val record: AudioRecord) : ExtractionState()
    data class Error(val message: String) : ExtractionState()
}

sealed class PlaybackState {
    object Stopped : PlaybackState()
    object Playing : PlaybackState()
    object Paused : PlaybackState()
}

class AudioViewModel(private val repository: AudioRepository) : ViewModel() {

    private val _selectedVideoInfo = MutableStateFlow<VideoInfo?>(null)
    val selectedVideoInfo: StateFlow<VideoInfo?> = _selectedVideoInfo.asStateFlow()

    private val _customFileName = MutableStateFlow("")
    val customFileName: StateFlow<String> = _customFileName.asStateFlow()

    private val _selectedFormat = MutableStateFlow("MP3") // "MP3" or "M4A"
    val selectedFormat: StateFlow<String> = _selectedFormat.asStateFlow()

    private val _extractionState = MutableStateFlow<ExtractionState>(ExtractionState.Idle)
    val extractionState: StateFlow<ExtractionState> = _extractionState.asStateFlow()

    private val _extractionProgress = MutableStateFlow(0f)
    val extractionProgress: StateFlow<Float> = _extractionProgress.asStateFlow()

    // History of extracted files
    val audioRecords: StateFlow<List<AudioRecord>> = repository.allRecords
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Player State
    private val _activePlayingRecord = MutableStateFlow<AudioRecord?>(null)
    val activePlayingRecord: StateFlow<AudioRecord?> = _activePlayingRecord.asStateFlow()

    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Stopped)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _playbackPosition = MutableStateFlow(0L)
    val playbackPosition: StateFlow<Long> = _playbackPosition.asStateFlow()

    private val _playbackDuration = MutableStateFlow(1L)
    val playbackDuration: StateFlow<Long> = _playbackDuration.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private var extractionJob: Job? = null

    fun selectVideo(context: Context, uri: Uri) {
        viewModelScope.launch {
            _extractionState.value = ExtractionState.Idle
            _extractionProgress.value = 0f
            val info = withContext(Dispatchers.IO) {
                AudioExtractor.getVideoMetadata(context, uri)
            }
            _selectedVideoInfo.value = info
            if (info != null) {
                // Initialize custom title with video file name minus extension
                val dotIndex = info.name.lastIndexOf('.')
                val rawName = if (dotIndex != -1) info.name.substring(0, dotIndex) else info.name
                _customFileName.value = rawName
            }
        }
    }

    fun setCustomFileName(name: String) {
        _customFileName.value = name
    }

    fun setSelectedFormat(format: String) {
        _selectedFormat.value = format
    }

    fun startExtraction(context: Context) {
        val videoInfo = _selectedVideoInfo.value ?: return
        val currentFormat = _selectedFormat.value
        val nameInput = _customFileName.value.trim().ifEmpty { "audio_extracted" }

        _extractionState.value = ExtractionState.Processing
        _extractionProgress.value = 0f

        extractionJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val outputSuffix = if (currentFormat == "MP3") ".mp3" else ".m4a"
                val outputFileName = "$nameInput$outputSuffix"
                
                val musicDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                    ?: throw Exception("无法访问外部存储音频目录")
                
                val outputFile = File(musicDir, outputFileName)
                
                // If it already exists, append unique number
                var finalFile = outputFile
                var counter = 1
                while (finalFile.exists()) {
                    finalFile = File(musicDir, "${nameInput}_$counter$outputSuffix")
                    counter++
                }

                // Extract audio natively
                AudioExtractor.extractAudio(
                    context = context,
                    videoUri = videoInfo.uri,
                    outputFile = finalFile,
                    onProgress = { progress ->
                        _extractionProgress.value = progress
                    }
                )

                // Read extracted file info to ensure validity and update DB
                if (finalFile.exists() && finalFile.length() > 0) {
                    val record = AudioRecord(
                        title = finalFile.name,
                        filePath = finalFile.absolutePath,
                        fileSize = AudioExtractor.formatFileSize(finalFile.length()),
                        durationText = videoInfo.durationText,
                        durationMs = videoInfo.durationMs,
                        format = currentFormat,
                        originalVideoName = videoInfo.name
                    )
                    
                    val newId = repository.insert(record)
                    val insertedRecord = record.copy(id = newId.toInt())

                    withContext(Dispatchers.Main) {
                        _extractionState.value = ExtractionState.Success(insertedRecord)
                        _extractionProgress.value = 1.0f
                        _selectedVideoInfo.value = null // clear screen active extraction selection
                    }
                } else {
                    throw Exception("生成的文件为空或损坏")
                }
            } catch (e: Exception) {
                Log.e("AudioViewModel", "Extraction failed", e)
                withContext(Dispatchers.Main) {
                    _extractionState.value = ExtractionState.Error(e.localizedMessage ?: "提取失败")
                }
            }
        }
    }

    fun cancelExtraction() {
        extractionJob?.cancel()
        _extractionState.value = ExtractionState.Idle
        _extractionProgress.value = 0f
    }

    fun clearActiveSelection() {
        _selectedVideoInfo.value = null
        _extractionState.value = ExtractionState.Idle
        _extractionProgress.value = 0f
    }

    // Media Player controls
    fun playAudio(record: AudioRecord) {
        viewModelScope.launch {
            if (_activePlayingRecord.value?.id == record.id && _playbackState.value == PlaybackState.Paused) {
                // Resume
                resumeAudio()
                return@launch
            }

            // Otherwise, play from scratch
            stopAudio()

            val file = File(record.filePath)
            if (!file.exists()) {
                Log.e("AudioViewModel", "File not found: ${record.filePath}")
                return@launch
            }

            try {
                _activePlayingRecord.value = record
                _playbackDuration.value = if (record.durationMs > 0) record.durationMs else 1L
                _playbackPosition.value = 0L

                val mp = MediaPlayer().apply {
                    setDataSource(record.filePath)
                    setOnErrorListener { _, what, extra ->
                        Log.e("AudioViewModel", "MediaPlayer error: what=$what, extra=$extra")
                        stopAudio()
                        true
                    }
                    prepare()
                    start()
                }
                mediaPlayer = mp
                _playbackState.value = PlaybackState.Playing

                mp.setOnCompletionListener {
                    _playbackState.value = PlaybackState.Stopped
                    _playbackPosition.value = 0L
                    progressJob?.cancel()
                }

                startPositionTracker()
            } catch (e: Exception) {
                Log.e("AudioViewModel", "Failed to play audio", e)
                _playbackState.value = PlaybackState.Stopped
                _activePlayingRecord.value = null
            }
        }
    }

    fun pauseAudio() {
        mediaPlayer?.let { mp ->
            if (mp.isPlaying) {
                mp.pause()
                _playbackState.value = PlaybackState.Paused
                progressJob?.cancel()
            }
        }
    }

    fun resumeAudio() {
        mediaPlayer?.let { mp ->
            mp.start()
            _playbackState.value = PlaybackState.Playing
            startPositionTracker()
        }
    }

    fun stopAudio() {
        progressJob?.cancel()
        mediaPlayer?.let { mp ->
            try {
                if (mp.isPlaying) {
                    mp.stop()
                }
                mp.release()
            } catch (e: Exception) {
                Log.e("AudioViewModel", "Error stopping player", e)
            }
        }
        mediaPlayer = null
        _playbackState.value = PlaybackState.Stopped
        _playbackPosition.value = 0L
        _activePlayingRecord.value = null
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer?.let { mp ->
            try {
                mp.seekTo(positionMs.toInt())
                _playbackPosition.value = positionMs
            } catch (e: Exception) {
                Log.e("AudioViewModel", "Error seeking", e)
            }
        }
    }

    private fun startPositionTracker() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (_playbackState.value == PlaybackState.Playing) {
                mediaPlayer?.let { mp ->
                    try {
                        _playbackPosition.value = mp.currentPosition.toLong()
                    } catch (e: Exception) {
                        Log.e("AudioViewModel", "Tracker read error", e)
                    }
                }
                delay(250)
            }
        }
    }

    fun deleteRecord(context: Context, record: AudioRecord) {
        viewModelScope.launch {
            // Stop if active item is playing
            if (_activePlayingRecord.value?.id == record.id) {
                stopAudio()
                _activePlayingRecord.value = null
            }

            // Delete file from disk
            withContext(Dispatchers.IO) {
                try {
                    val file = File(record.filePath)
                    if (file.exists()) {
                        file.delete()
                    }
                } catch (e: Exception) {
                    Log.e("AudioViewModel", "Failed to delete file", e)
                }
                repository.deleteById(record.id)
            }
        }
    }

    fun shareFile(context: Context, record: AudioRecord) {
        try {
            val file = File(record.filePath)
            if (!file.exists()) return

            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = if (record.format == "MP3") "audio/mpeg" else "audio/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "分享音频文件")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e("AudioViewModel", "Failed to share file", e)
        }
    }

    /**
     * Saves file to Public Downloads folder using modern ContentResolver/MediaStore API.
     * Permission-free, stable.
     */
    fun saveToDownloads(context: Context, record: AudioRecord, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sourceFile = File(record.filePath)
                if (!sourceFile.exists()) {
                    withContext(Dispatchers.Main) {
                        onResult(false, "源音频文件不存在！")
                    }
                    return@launch
                }

                val filename = sourceFile.name
                val resolver = context.contentResolver
                
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val contentValues = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, if (record.format == "MP3") "audio/mpeg" else "audio/mp4")
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    
                    val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { outputStream ->
                            sourceFile.inputStream().use { inputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                        withContext(Dispatchers.Main) {
                            onResult(true, "保存成功：已导出至系统 (Downloads) 下载文件夹！")
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            onResult(false, "无法在 Downloads 目录下创建文件。")
                        }
                    }
                } else {
                    // Pre-Q: standard files dir using Environment
                    @Suppress("DEPRECATION")
                    val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (!publicDir.exists()) {
                        publicDir.mkdirs()
                    }
                    val destFile = File(publicDir, filename)
                    sourceFile.inputStream().use { inputStream ->
                        destFile.outputStream().use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        onResult(true, "保存成功：已导出至系统 Downloads文件夹: ${destFile.name}")
                    }
                }
            } catch (e: Exception) {
                Log.e("AudioViewModel", "Failed to copy to downloads", e)
                withContext(Dispatchers.Main) {
                    onResult(false, "导出失败: ${e.localizedMessage}")
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopAudio()
    }
}

class AudioViewModelFactory(private val repository: AudioRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AudioViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AudioViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
