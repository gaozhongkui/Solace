package com.getsolace.ai.chat.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.getsolace.ai.chat.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * ViewModel for HomeScreen.
 * Mirrors iOS HomeViewModel.swift:
 *  - cardLeftItems / cardRightItems (2-column staggered grid)
 *  - isScanning / scanProgress / scannedSize
 *  - MediaManagerDelegate implementation
 */
class HomeViewModel : ViewModel(), MediaManagerDelegate {

    // ── Scan state ─────────────────────────────────────────────────────────

    private val _isScanning     = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _scanProgress   = MutableStateFlow(0f)
    val scanProgress: StateFlow<Float> = _scanProgress

    private val _scannedSize    = MutableStateFlow(0L)   // bytes
    val scannedSize: StateFlow<Long> = _scannedSize

    private val _scanResult     = MutableStateFlow(MediaScanResult())
    val scanResult: StateFlow<MediaScanResult> = _scanResult

    // ── Category sizes (for cards) ─────────────────────────────────────────

    val allVideosSize: StateFlow<Long>      = _scanResult.map {
        it.allVideos.sumOf { v -> v.size }
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0L)

    val shortVideosSize: StateFlow<Long>    = _scanResult.map {
        it.shortVideos.sumOf { v -> v.size }
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0L)

    val recordingsSize: StateFlow<Long>     = _scanResult.map {
        it.screenRecordings.sumOf { v -> v.size }
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0L)

    val screenshotsSize: StateFlow<Long>    = _scanResult.map {
        it.screenshots.sumOf { v -> v.size }
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0L)

    // ── Staggered grid (left/right columns like iOS cardLeftItems) ──────────

    val cardLeftItems: StateFlow<List<HomeCardItem>> = _scanResult.map { result ->
        buildCardItems(result).filterIndexed { idx, _ -> idx % 2 == 0 }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val cardRightItems: StateFlow<List<HomeCardItem>> = _scanResult.map { result ->
        buildCardItems(result).filterIndexed { idx, _ -> idx % 2 == 1 }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        MediaManager.setDelegate(this)

        // Mirror scan result from MediaManager
        viewModelScope.launch {
            MediaManager.scanResult.collect { result ->
                _scanResult.value = result
            }
        }
        viewModelScope.launch {
            MediaManager.isScanning.collect { scanning ->
                _isScanning.value = scanning
            }
        }
        viewModelScope.launch {
            MediaManager.scanProgress.collect { p ->
                _scanProgress.value = p
            }
        }
    }

    // ── Actions ────────────────────────────────────────────────────────────

    fun startScan(context: Context) {
        MediaManager.startScan(context)
    }

    // ── MediaManagerDelegate ───────────────────────────────────────────────

    override fun onScanProgress(progress: Float, scannedSizeBytes: Long) {
        _scanProgress.value = progress
        _scannedSize.value  = scannedSizeBytes
    }

    override fun onScanComplete(result: MediaScanResult) {
        _scanResult.value = result
        _scannedSize.value = result.totalSizeBytes
    }

    override fun onError(message: String) { /* handle */ }

    override fun onCleared() {
        super.onCleared()
        MediaManager.setDelegate(null)
        MediaManager.cancelScan()
    }

    // ── Build card items (mirrors iOS HomeItem) ────────────────────────────

    private fun buildCardItems(result: MediaScanResult): List<HomeCardItem> {
        return listOf(
            HomeCardItem(
                category    = MediaCategory.ALL_VIDEOS,
                count       = result.allVideos.size,
                sizeBytes   = result.allVideos.sumOf { it.size },
                thumbnail   = result.allVideos.firstOrNull()?.uri
            ),
            HomeCardItem(
                category    = MediaCategory.SHORT_VIDEOS,
                count       = result.shortVideos.size,
                sizeBytes   = result.shortVideos.sumOf { it.size },
                thumbnail   = result.shortVideos.firstOrNull()?.uri
            ),
            HomeCardItem(
                category    = MediaCategory.SCREEN_RECORDINGS,
                count       = result.screenRecordings.size,
                sizeBytes   = result.screenRecordings.sumOf { it.size },
                thumbnail   = result.screenRecordings.firstOrNull()?.uri
            ),
            HomeCardItem(
                category    = MediaCategory.SCREENSHOTS,
                count       = result.screenshots.size,
                sizeBytes   = result.screenshots.sumOf { it.size },
                thumbnail   = result.screenshots.firstOrNull()?.uri
            )
        )
    }
}

// ─── Home Card Item (mirrors iOS HomeItem) ────────────────────────────────────

data class HomeCardItem(
    val category: MediaCategory,
    val count: Int,
    val sizeBytes: Long,
    val thumbnail: android.net.Uri? = null
) {
    fun formattedSize(): String {
        val mb = sizeBytes / 1024.0 / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0  -> "%.1f GB".format(gb)
            mb >= 0.1  -> "%.1f MB".format(mb)
            else       -> "${sizeBytes / 1024} KB"
        }
    }
}
