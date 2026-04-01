package com.getsolace.ai.chat.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Singleton store for AI generated image history.
 * Mirrors iOS AIImageStore.swift
 */
object AIImageStore {

    private lateinit var prefs: SharedPreferences
    private val gson = Gson()
    private const val KEY_IMAGES = "ai_generated_images"

    private val _images = MutableStateFlow<List<AIGeneratedImage>>(emptyList())
    val images: StateFlow<List<AIGeneratedImage>> = _images

    fun init(context: Context) {
        prefs = context.getSharedPreferences("ai_image_store", Context.MODE_PRIVATE)
        loadFromPrefs()
    }

    fun saveImage(image: AIGeneratedImage) {
        val current = _images.value.toMutableList()
        current.add(0, image)
        if (current.size > 200) current.dropLast(current.size - 200)
        _images.value = current
        persistToPrefs(current)
    }

    fun deleteImage(id: String) {
        val updated = _images.value.filter { it.id != id }
        _images.value = updated
        persistToPrefs(updated)
    }

    private fun loadFromPrefs() {
        val json = prefs.getString(KEY_IMAGES, null) ?: return
        val type = object : TypeToken<List<AIGeneratedImage>>() {}.type
        _images.value = gson.fromJson(json, type) ?: emptyList()
    }

    private fun persistToPrefs(images: List<AIGeneratedImage>) {
        prefs.edit().putString(KEY_IMAGES, gson.toJson(images)).apply()
    }
}
