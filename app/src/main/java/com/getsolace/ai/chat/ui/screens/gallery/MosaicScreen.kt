package com.getsolace.ai.chat.ui.screens.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.getsolace.ai.chat.ui.components.SolaceAsyncImage
import com.getsolace.ai.chat.data.Photo
import com.getsolace.ai.chat.ui.screens.ScreenScaffold
import com.getsolace.ai.chat.data.rememberGalleryPhotos

@Composable
fun MosaicScreen(navController: NavController) {
    ScreenScaffold(title = "瀑布马赛克", navController = navController) { padding ->
        val photos = rememberGalleryPhotos()
        if (photos.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val chunks = photos.chunked(3)
                items(chunks.indices.toList()) { index ->
                    val chunk = chunks[index]
                    val onPhotoClick: (android.net.Uri) -> Unit = { uri ->
                        val encoded = java.net.URLEncoder.encode(uri.toString(), "UTF-8")
                        navController.navigate("photo_detail/$encoded")
                    }
                    MosaicRow(photos = chunk, pattern = index % 3, onPhotoClick = onPhotoClick)
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
fun MosaicRow(photos: List<Photo>, pattern: Int, onPhotoClick: (android.net.Uri) -> Unit = {}) {
    when (pattern) {
        0 -> LayoutOneLeftTwoRight(photos, onPhotoClick)
        1 -> LayoutOneLargeBottom(photos, onPhotoClick)
        else -> LayoutThreeEqual(photos, onPhotoClick)
    }
}

@Composable
fun LayoutOneLeftTwoRight(photos: List<Photo>, onPhotoClick: (android.net.Uri) -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().height(200.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        PhotoTile(photo = photos.getOrNull(0), modifier = Modifier.weight(0.6f).fillMaxHeight(), onPhotoClick = onPhotoClick)
        Column(
            modifier = Modifier.weight(0.4f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            PhotoTile(photo = photos.getOrNull(1), modifier = Modifier.weight(1f).fillMaxWidth(), onPhotoClick = onPhotoClick)
            PhotoTile(photo = photos.getOrNull(2), modifier = Modifier.weight(1f).fillMaxWidth(), onPhotoClick = onPhotoClick)
        }
    }
}

@Composable
fun LayoutOneLargeBottom(photos: List<Photo>, onPhotoClick: (android.net.Uri) -> Unit = {}) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(120.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            PhotoTile(photo = photos.getOrNull(0), modifier = Modifier.weight(1f).fillMaxHeight(), onPhotoClick = onPhotoClick)
            PhotoTile(photo = photos.getOrNull(1), modifier = Modifier.weight(1f).fillMaxHeight(), onPhotoClick = onPhotoClick)
        }
        PhotoTile(photo = photos.getOrNull(2), modifier = Modifier.fillMaxWidth().height(160.dp), onPhotoClick = onPhotoClick)
    }
}

@Composable
fun LayoutThreeEqual(photos: List<Photo>, onPhotoClick: (android.net.Uri) -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().height(140.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        photos.take(3).forEach { photo ->
            PhotoTile(photo = photo, modifier = Modifier.weight(1f).fillMaxHeight(), onPhotoClick = onPhotoClick)
        }
    }
}

@Composable
fun PhotoTile(photo: Photo?, modifier: Modifier = Modifier, onPhotoClick: (android.net.Uri) -> Unit = {}) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { photo?.uri?.let(onPhotoClick) }
    ) {
        SolaceAsyncImage(
            model = photo?.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
            shape = RoundedCornerShape(8.dp)
        )
    }
}
