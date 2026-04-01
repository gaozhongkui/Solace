package com.getsolace.ai.chat

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

data class Photo(
    val id: Long,
    val uri: Uri,
    val date: String = "",
    val aspectRatio: Float = 1f
)

fun groupByDate(photos: List<Photo>): Map<String, List<Photo>> = photos.groupBy { it.date }

fun formatDateLabel(dateStr: String): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val photoDate = sdf.parse(dateStr) ?: return dateStr
        val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.time
        val diffMs = today.time - photoDate.time
        val diffDays = TimeUnit.MILLISECONDS.toDays(diffMs)
        when {
            diffDays == 0L -> "今天"
            diffDays == 1L -> "昨天"
            diffDays < 7L -> "${diffDays}天前"
            diffDays < 30L -> "${diffDays / 7}周前"
            else -> dateStr
        }
    } catch (e: Exception) {
        dateStr
    }
}

private fun readPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Manifest.permission.READ_MEDIA_IMAGES
    else
        Manifest.permission.READ_EXTERNAL_STORAGE

suspend fun loadGalleryPhotos(context: Context, max: Int = 100): List<Photo> =
    withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT
        )
        val photos = mutableListOf<Photo>()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, null, null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val wCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val hCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            while (cursor.moveToNext() && photos.size < max) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                val dateMs = cursor.getLong(dateCol) * 1000L
                val date = sdf.format(Date(dateMs))
                val w = cursor.getInt(wCol).takeIf { it > 0 } ?: 1
                val h = cursor.getInt(hCol).takeIf { it > 0 } ?: 1
                photos.add(Photo(id = id, uri = uri, date = date, aspectRatio = w.toFloat() / h))
            }
        }
        photos
    }

@Composable
fun rememberGalleryPhotos(max: Int = 60): List<Photo> {
    val context = LocalContext.current
    var photos by remember { mutableStateOf(emptyList<Photo>()) }
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, readPermission()) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
    }
    LaunchedEffect(permissionGranted) {
        if (permissionGranted) {
            photos = loadGalleryPhotos(context, max)
        } else {
            launcher.launch(readPermission())
        }
    }
    return photos
}
