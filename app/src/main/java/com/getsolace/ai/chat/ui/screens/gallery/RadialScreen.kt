package com.getsolace.ai.chat.ui.screens.gallery

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.getsolace.ai.chat.data.Photo
import com.getsolace.ai.chat.ui.screens.ScreenScaffold
import com.getsolace.ai.chat.data.rememberGalleryPhotos
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun RadialScreen(navController: NavController) {
    ScreenScaffold(title = "星云辐射", navController = navController) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center
        ) {
            val photos = rememberGalleryPhotos()
            if (photos.isEmpty()) {
                CircularProgressIndicator()
            } else {
                RadialGallery(photos = photos)
            }
        }
    }
}

@Composable
fun RadialGallery(photos: List<Photo>) {
    var selectedIndex by remember { mutableIntStateOf(0) }
    val centerPhoto = photos[selectedIndex]
    val orbitPhotos = photos.filterIndexed { i, _ -> i != selectedIndex }.take(8)

    val infiniteTransition = rememberInfiniteTransition(label = "orbit")
    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit_angle"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "点击周边照片切换主角",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )

        Box(modifier = Modifier.size(340.dp), contentAlignment = Alignment.Center) {
            // 轨道圈
            Box(modifier = Modifier.size(300.dp).border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), CircleShape))
            Box(modifier = Modifier.size(200.dp).border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), CircleShape))

            // 轨道卫星照片
            orbitPhotos.forEachIndexed { index, photo ->
                val baseAngle = (360f / orbitPhotos.size) * index
                val angle = Math.toRadians((baseAngle + orbitAngle).toDouble())
                val radius = 130f
                val x = (cos(angle) * radius).toFloat()
                val y = (sin(angle) * radius).toFloat()

                Box(
                    modifier = Modifier
                        .offset(x = x.dp, y = y.dp)
                        .size(52.dp)
                        .clip(CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                        .clickable { selectedIndex = photos.indexOf(photo) }
                        .zIndex(2f)
                ) {
                    AsyncImage(
                        model = photo.uri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                }
            }

            // 中心主照片
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .border(4.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    .zIndex(5f)
            ) {
                AsyncImage(
                    model = centerPhoto.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            }
        }

        // 日期信息
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${photos.size}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    Text("张照片", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
                Box(Modifier.width(1.dp).height(36.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(centerPhoto.date, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Text("拍摄日期", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }
        }
    }
}
