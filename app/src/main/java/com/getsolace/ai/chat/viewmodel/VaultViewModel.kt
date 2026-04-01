package com.getsolace.ai.chat.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.getsolace.ai.chat.data.EncryptedImage
import com.getsolace.ai.chat.data.VaultStore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for the encrypted vault screen.
 * Mirrors iOS StorageManager + ImageEncryptionView logic.
 */
class VaultViewModel : ViewModel() {

    val images: StateFlow<List<EncryptedImage>> = VaultStore.images

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage

    private val _decryptedBitmap = MutableStateFlow<Bitmap?>(null)
    val decryptedBitmap: StateFlow<Bitmap?> = _decryptedBitmap

    // ── Actions ───────────────────────────────────────────────────────────────

    fun encryptAndSave(bitmap: Bitmap) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                VaultStore.encryptAndSave(bitmap)
                showToast("图片已加密保存")
            } catch (e: Exception) {
                showToast("保存失败：${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun decryptImage(image: EncryptedImage) {
        viewModelScope.launch {
            _isLoading.value = true
            _decryptedBitmap.value = null
            try {
                _decryptedBitmap.value = VaultStore.decrypt(image)
            } catch (e: Exception) {
                showToast("解密失败")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearDecryptedBitmap() {
        _decryptedBitmap.value = null
    }

    fun deleteImage(id: String) {
        viewModelScope.launch {
            VaultStore.delete(id)
            showToast("已删除")
        }
    }

    fun clearToast() { _toastMessage.value = null }

    private fun showToast(msg: String) { _toastMessage.value = msg }
}
