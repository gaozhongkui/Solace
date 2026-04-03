package com.getsolace.ai.chat.ui.screens.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
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
import com.getsolace.ai.chat.data.formatDateLabel
import com.getsolace.ai.chat.data.groupByDate
import com.getsolace.ai.chat.ui.screens.ScreenScaffold
import com.getsolace.ai.chat.data.rememberGalleryPhotos

@Composable
fun TimelineScreen(navController: NavController) {
    ScreenScaffold(title = "时间轴视图", navController = navController) { padding ->
        val photos = rememberGalleryPhotos()
        if (photos.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val grouped = groupByDate(photos)
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                grouped.forEach { (date, datePhotos) ->
                    item {
                        TimelineDateHeader(date = formatDateLabel(date))
                    }
                    item {
                        TimelinePhotoRow(photos = datePhotos)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun TimelineDateHeader(date: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        Spacer(Modifier.width(10.dp))
        Text(text = date, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
    }
}

@Composable
fun TimelinePhotoRow(photos: List<Photo>) {
    val heights = remember(photos) {
        photos.map { listOf(100, 130, 110, 90, 120).random() }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 36.dp, end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        photos.take(5).forEachIndexed { index, photo ->
            TimelinePhotoItem(photo = photo, height = heights.getOrElse(index) { 110 })
        }
    }
}

@Composable
fun RowScope.TimelinePhotoItem(photo: Photo, height: Int) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(height.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { }
    ) {
        SolaceAsyncImage(
            model = photo.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
            shape = RoundedCornerShape(10.dp)
        )
    }
}
