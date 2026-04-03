package com.getsolace.ai.chat.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Storage manager for encrypted vault images.
 * Mirrors iOS StorageManager.swift
 */
object VaultStore {

    private lateinit var appContext: Context
    private val gson = Gson()
    private const val KEY_METADATA = "vault_metadata"

    private val _images = MutableStateFlow<List<EncryptedImage>>(emptyList())
    val images: StateFlow<List<EncryptedImage>> = _images

    fun init(context: Context) {
        appContext = context.applicationContext
        loadMetadata()
    }

    // ── Encrypt & Save ────────────────────────────────────────────────────────

    suspend fun encryptAndSave(bitmap: Bitmap): EncryptedImage = withContext(Dispatchers.IO) {
        val id       = java.util.UUID.randomUUID().toString()
        val fileName = "vault_$id.enc"

        // Compress to JPEG bytes
        val jpegBytes = ByteArrayOutputStream().also {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
        }.toByteArray()

        // Encrypt
        val encrypted = EncryptionManager.encrypt(jpegBytes)

        // Write encrypted file
        File(vaultDir(), fileName).writeBytes(encrypted)

        // 不存储缩略图，保护隐私
        val thumbBase64 = ""

        val record = EncryptedImage(
            id             = id,
            fileName       = fileName,
            thumbnailBase64 = thumbBase64,
            createdAt      = System.currentTimeMillis()
        )
        val updated = _images.value.toMutableList().also { it.add(0, record) }
        _images.value = updated
        saveMetadata(updated)

        record
    }

    // ── Decrypt & Load ────────────────────────────────────────────────────────

    suspend fun decrypt(image: EncryptedImage): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val file   = File(vaultDir(), image.fileName)
            if (!file.exists()) return@withContext null
            val bytes  = EncryptionManager.decrypt(file.readBytes())
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val image = _images.value.find { it.id == id } ?: return@withContext
        File(vaultDir(), image.fileName).delete()
        val updated = _images.value.filter { it.id != id }
        _images.value = updated
        saveMetadata(updated)
    }

    // ── Thumbnail helper ──────────────────────────────────────────────────────

    fun thumbnailBitmap(image: EncryptedImage): Bitmap? = try {
        val bytes = android.util.Base64.decode(image.thumbnailBase64, android.util.Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) { null }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun vaultDir(): File {
        return File(appContext.filesDir, "vault").also { it.mkdirs() }
    }

    private fun loadMetadata() {
        val prefs = appContext.getSharedPreferences("vault_store", Context.MODE_PRIVATE)
        val json  = prefs.getString(KEY_METADATA, null) ?: return
        val type  = object : TypeToken<List<EncryptedImage>>() {}.type
        _images.value = gson.fromJson(json, type) ?: emptyList()
    }

    private fun saveMetadata(images: List<EncryptedImage>) {
        val prefs = appContext.getSharedPreferences("vault_store", Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_METADATA, gson.toJson(images)).apply()
    }
}
