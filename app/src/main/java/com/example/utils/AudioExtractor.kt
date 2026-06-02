package com.example.utils

import android.content.Context
import android.database.Cursor
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.util.Locale

data class VideoInfo(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val sizeText: String,
    val durationMs: Long,
    val durationText: String,
    val mimeType: String?,
    val audioCodec: String?
)

object AudioExtractor {
    private const val TAG = "AudioExtractor"

    fun formatDuration(ms: Long): String {
        if (ms <= 0) return "00:00"
        val totalSeconds = ms / 1000
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0.00 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var size = bytes.toDouble()
        var unitIndex = 0
        while (size >= 1024.0 && unitIndex < units.lastIndex) {
            size /= 1024.0
            unitIndex++
        }
        return String.format(Locale.getDefault(), "%.2f %s", size, units[unitIndex])
    }

    fun getVideoMetadata(context: Context, videoUri: Uri): VideoInfo? {
        val retriever = MediaMetadataRetriever()
        var sizeBytes: Long = 0
        var displayName = "未知视频"

        // Use contentResolver to get size and filename
        try {
            val cursor: Cursor? = context.contentResolver.query(videoUri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        displayName = it.getString(nameIndex) ?: displayName
                    }
                    val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1) {
                        sizeBytes = it.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying uri metadata", e)
        }

        try {
            retriever.setDataSource(context, videoUri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L
            val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)

            // Try to extract audio track info via MediaExtractor
            var audioCodec: String? = null
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, videoUri, null)
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME)
                    if (mime != null && mime.startsWith("audio/")) {
                        audioCodec = mime.substringAfter("audio/")
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Extractor failed to probe codec", e)
            } finally {
                extractor.release()
            }

            if (sizeBytes == 0L) {
                // Fallback size estimation if querying content resolver failed
                try {
                    context.contentResolver.openAssetFileDescriptor(videoUri, "r")?.use { afd ->
                        sizeBytes = afd.length
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting content length", e)
                }
            }

            return VideoInfo(
                uri = videoUri,
                name = displayName,
                sizeBytes = sizeBytes,
                sizeText = formatFileSize(sizeBytes),
                durationMs = durationMs,
                durationText = formatDuration(durationMs),
                mimeType = mimeType,
                audioCodec = audioCodec?.uppercase(Locale.getDefault()) ?: "AAC"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error retriever metadata for video", e)
            return null
        } finally {
            retriever.release()
        }
    }

    /**
     * Extracts absolute audio track from the media source file with progress callbacks.
     * Uses MediaExtractor and MediaMuxer (extracting directly without re-compression, which is lossless and ultra fast).
     */
    fun extractAudio(
        context: Context,
        videoUri: Uri,
        outputFile: File,
        onProgress: (Float) -> Unit
    ) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, videoUri, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setDataSource", e)
            throw e
        }

        var audioTrackIndex = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val trackFormat = extractor.getTrackFormat(i)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME)
            if (mime != null && mime.startsWith("audio/")) {
                audioTrackIndex = i
                format = trackFormat
                break
            }
        }

        if (audioTrackIndex == -1 || format == null) {
            extractor.release()
            throw IllegalArgumentException("在该视频中未找到音频轨道！")
        }

        extractor.selectTrack(audioTrackIndex)

        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
            format.getLong(MediaFormat.KEY_DURATION)
        } else {
            0L
        }

        var muxer: MediaMuxer? = null
        try {
            // Check output file parent directory
            val parent = outputFile.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }

            // Using MPEG-4 output multiplexer, ideal for raw AAC packing
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val writeTrackIndex = muxer.addTrack(format)
            muxer.start()

            val maxBufferSize = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            } else {
                1024 * 512
            }

            val buffer = ByteBuffer.allocateDirect(maxBufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            var count = 0
            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) {
                    bufferInfo.size = 0
                    break
                }
                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = extractor.sampleFlags

                muxer.writeSampleData(writeTrackIndex, buffer, bufferInfo)

                count++
                if (count % 10 == 0 && durationUs > 0) {
                    val progress = bufferInfo.presentationTimeUs.toFloat() / durationUs.toFloat()
                    onProgress(progress.coerceIn(0.0f, 1.0f))
                }

                if (!extractor.advance()) {
                    break
                }
            }

            onProgress(1.0f)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing muxed media sample data", e)
            throw e
        } finally {
            try {
                muxer?.stop()
                muxer?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Muxer release error", e)
            }
            extractor.release()
        }
    }
}
