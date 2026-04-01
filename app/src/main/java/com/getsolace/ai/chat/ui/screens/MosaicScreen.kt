package com.getsolace.ai.chat.ui.screens

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.getsolace.ai.chat.Photo
import com.getsolace.ai.chat.ScreenScaffold
import com.getsolace.ai.chat.rememberGalleryPhotos

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
                    MosaicRow(photos = chunk, pattern = index % 3)
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
fun MosaicRow(photos: List<Photo>, pattern: Int) {
    when (pattern) {
        0 -> LayoutOneLeftTwoRight(photos)
        1 -> LayoutOneLargeBottom(photos)
        else -> LayoutThreeEqual(photos)
    }
}

@Composable
fun LayoutOneLeftTwoRight(photos: List<Photo>) {
    Row(
        modifier = Modifier.fillMaxWidth().height(200.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        PhotoTile(photo = photos.getOrNull(0), modifier = Modifier.weight(0.6f).fillMaxHeight())
        Column(
            modifier = Modifier.weight(0.4f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            PhotoTile(photo = photos.getOrNull(1), modifier = Modifier.weight(1f).fillMaxWidth())
            PhotoTile(photo = photos.getOrNull(2), modifier = Modifier.weight(1f).fillMaxWidth())
        }
    }
}

@Composable
fun LayoutOneLargeBottom(photos: List<Photo>) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(120.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            PhotoTile(photo = photos.getOrNull(0), modifier = Modifier.weight(1f).fillMaxHeight())
            PhotoTile(photo = photos.getOrNull(1), modifier = Modifier.weight(1f).fillMaxHeight())
        }
        PhotoTile(photo = photos.getOrNull(2), modifier = Modifier.fillMaxWidth().height(160.dp))
    }
}

@Composable
fun LayoutThreeEqual(photos: List<Photo>) {
    Row(
        modifier = Modifier.fillMaxWidth().height(140.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        photos.take(3).forEach { photo ->
            PhotoTile(photo = photo, modifier = Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
fun PhotoTile(photo: Photo?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Gray.copy(alpha = 0.15f))
            .clickable { }
    ) {
        if (photo != null) {
            AsyncImage(
                model = photo.uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}
