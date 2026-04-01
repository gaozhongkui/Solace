package com.getsolace.ai.chat.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.TimeUnit

// ─── Media Item Models (mirrors iOS MediaManager) ─────────────────────────────

enum class MediaCategory(val title: String, val icon: String) {
    ALL_VIDEOS("全部视频", "▶"),
    SHORT_VIDEOS("短视频", "⚡"),
    SCREEN_RECORDINGS("屏幕录制", "📱"),
    SCREENSHOTS("截图", "📷")
}

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val size: Long,               // bytes
    val durationMs: Long,         // 0 for images
    val dateAdded: Long,          // epoch seconds
    val width: Int,
    val height: Int,
    val mimeType: String,
    val category: MediaCategory
) {
    val isVideo: Boolean get() = mimeType.startsWith("video/")
    val isImage: Boolean get() = mimeType.startsWith("image/")
    val durationSeconds: Long get() = durationMs / 1000

    fun formattedSize(): String {
        val kb = size / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0  -> "%.1f GB".format(gb)
            mb >= 1.0  -> "%.1f MB".format(mb)
            else       -> "%.0f KB".format(kb)
        }
    }

    fun formattedDuration(): String {
        val secs = durationSeconds
        val m    = secs / 60
        val s    = secs % 60
        return "%d:%02d".format(m, s)
    }
}

data class MediaScanResult(
    val allVideos: List<MediaItem>        = emptyList(),
    val shortVideos: List<MediaItem>      = emptyList(),   // ≤ 6s (iOS rule)
    val screenRecordings: List<MediaItem> = emptyList(),
    val screenshots: List<MediaItem>      = emptyList(),
    val totalSizeBytes: Long              = 0L
) {
    fun formattedTotalSize(): String {
        val mb = totalSizeBytes / 1024.0 / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0  -> "%.1f GB".format(gb)
            else       -> "%.0f MB".format(mb)
        }
    }
}

// ─── Delegate interface (mirrors iOS MediaManagerDelegate) ────────────────────

interface MediaManagerDelegate {
    fun onScanProgress(progress: Float, scannedSizeBytes: Long)
    fun onScanComplete(result: MediaScanResult)
    fun onError(message: String)
}

// ─── MediaManager singleton (mirrors iOS MediaManager.swift) ─────────────────

object MediaManager {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var scanJob: Job? = null
    private var delegate: MediaManagerDelegate? = null

    private val _scanResult = MutableStateFlow(MediaScanResult())
    val scanResult: StateFlow<MediaScanResult> = _scanResult

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _scanProgress = MutableStateFlow(0f)
    val scanProgress: StateFlow<Float> = _scanProgress

    fun setDelegate(d: MediaManagerDelegate?) { delegate = d }

    // ── Scan ──────────────────────────────────────────────────────────────────

    fun startScan(context: Context) {
        if (_isScanning.value) return
        scanJob?.cancel()
        scanJob = scope.launch {
            _isScanning.value  = true
            _scanProgress.value = 0f
            try {
                val result = scanMediaStore(context)
                _scanResult.value  = result
                _isScanning.value  = false
                _scanProgress.value = 1f
                withContext(Dispatchers.Main) {
                    delegate?.onScanComplete(result)
                }
            } catch (e: CancellationException) {
                _isScanning.value = false
            } catch (e: Exception) {
                _isScanning.value = false
                withContext(Dispatchers.Main) {
                    delegate?.onError(e.message ?: "扫描失败")
                }
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        _isScanning.value = false
    }

    // ── MediaStore query ──────────────────────────────────────────────────────

    private suspend fun scanMediaStore(context: Context): MediaScanResult = withContext(Dispatchers.IO) {
        val allVideos        = mutableListOf<MediaItem>()
        val shortVideos      = mutableListOf<MediaItem>()
        val screenRecordings = mutableListOf<MediaItem>()
        val screenshots      = mutableListOf<MediaItem>()
        var totalSize        = 0L

        // ── Query Videos ─────────────────────────────────────────────────────
        val videoProjection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.MIME_TYPE
        )

        val videoUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        context.contentResolver.query(
            videoUri,
            videoProjection,
            null, null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val total = cursor.count
            var processed = 0

            val idCol       = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol     = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val sizeCol     = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val durCol      = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val dateCol     = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val widthCol    = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightCol   = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val mimeCol     = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)

            while (cursor.moveToNext()) {
                val id          = cursor.getLong(idCol)
                val name        = cursor.getString(nameCol) ?: ""
                val size        = cursor.getLong(sizeCol)
                val duration    = cursor.getLong(durCol)
                val dateAdded   = cursor.getLong(dateCol)
                val width       = cursor.getInt(widthCol)
                val height      = cursor.getInt(heightCol)
                val mime        = cursor.getString(mimeCol) ?: "video/mp4"
                val contentUri  = ContentUris.withAppendedId(videoUri, id)

                totalSize += size
                processed++

                // Classify
                val isShort    = duration <= 6000            // ≤ 6 seconds
                val isRecording = name.contains("screen", ignoreCase = true) ||
                                  name.contains("record", ignoreCase = true) ||
                                  name.lowercase().startsWith("screenrecord")

                val cat = when {
                    isRecording -> MediaCategory.SCREEN_RECORDINGS
                    isShort     -> MediaCategory.SHORT_VIDEOS
                    else        -> MediaCategory.ALL_VIDEOS
                }

                val item = MediaItem(
                    id          = id,
                    uri         = contentUri,
                    displayName = name,
                    size        = size,
                    durationMs  = duration,
                    dateAdded   = dateAdded,
                    width       = width,
                    height      = height,
                    mimeType    = mime,
                    category    = cat
                )
                allVideos.add(item)
                when (cat) {
                    MediaCategory.SHORT_VIDEOS      -> shortVideos.add(item)
                    MediaCategory.SCREEN_RECORDINGS -> screenRecordings.add(item)
                    else                            -> Unit
                }

                // Progress callback every 10 items
                if (processed % 10 == 0 && total > 0) {
                    val progress = processed.toFloat() / total.toFloat() * 0.7f
                    _scanProgress.value = progress
                    withContext(Dispatchers.Main) {
                        delegate?.onScanProgress(progress, totalSize)
                    }
                }
            }
        }

        // ── Query Images (screenshots) ────────────────────────────────────────
        val imgProjection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.MIME_TYPE
        )

        val imgUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        // Screenshots: in Pictures/Screenshots or DCIM/Screenshots
        val screenshotSelection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        } else {
            null
        }
        val screenshotArgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf("%Screenshots%")
        } else {
            null
        }

        context.contentResolver.query(
            imgUri, imgProjection, screenshotSelection, screenshotArgs,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol    = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol  = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeCol  = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val dateCol  = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightCol= cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val mimeCol  = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)

            while (cursor.moveToNext()) {
                val id         = cursor.getLong(idCol)
                val name       = cursor.getString(nameCol) ?: ""
                val size       = cursor.getLong(sizeCol)
                val dateAdded  = cursor.getLong(dateCol)
                val width      = cursor.getInt(widthCol)
                val height     = cursor.getInt(heightCol)
                val mime       = cursor.getString(mimeCol) ?: "image/jpeg"
                val contentUri = ContentUris.withAppendedId(imgUri, id)

                // Android < Q: detect by name
                val isScreenshot = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
                    name.contains("screenshot", ignoreCase = true) ||
                    name.lowercase().startsWith("screenshot")

                if (isScreenshot) {
                    totalSize += size
                    screenshots.add(
                        MediaItem(
                            id          = id,
                            uri         = contentUri,
                            displayName = name,
                            size        = size,
                            durationMs  = 0,
                            dateAdded   = dateAdded,
                            width       = width,
                            height      = height,
                            mimeType    = mime,
                            category    = MediaCategory.SCREENSHOTS
                        )
                    )
                }
            }
        }

        _scanProgress.value = 1f
        withContext(Dispatchers.Main) {
            delegate?.onScanProgress(1f, totalSize)
        }

        MediaScanResult(
            allVideos        = allVideos,
            shortVideos      = shortVideos,
            screenRecordings = screenRecordings,
            screenshots      = screenshots,
            totalSizeBytes   = totalSize
        )
    }
}
