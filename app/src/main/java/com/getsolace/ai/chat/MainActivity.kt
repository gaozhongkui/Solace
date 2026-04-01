package com.getsolace.ai.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.*
import com.getsolace.ai.chat.ui.screens.MosaicScreen
import com.getsolace.ai.chat.ui.screens.PolaroidScreen
import com.getsolace.ai.chat.ui.screens.RadialScreen
import com.getsolace.ai.chat.ui.screens.StackedCardsScreen
import com.getsolace.ai.chat.ui.screens.StoryScreen
import com.getsolace.ai.chat.ui.screens.TimelineScreen
import com.getsolace.ai.chat.ui.theme.SolaceTheme


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SolaceTheme {
                GalleryNavHost()
            }
        }
    }
}

@Composable
fun GalleryNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "home") {
        composable("home") { HomeScreen(navController) }
        composable("stacked_cards") { StackedCardsScreen(navController) }
        composable("mosaic") { MosaicScreen(navController) }
        composable("timeline") { TimelineScreen(navController) }
        composable("polaroid") { PolaroidScreen(navController) }
        composable("radial") { RadialScreen(navController) }
        composable("story") { StoryScreen(navController) }
    }
}

data class GalleryMode(
    val title: String,
    val subtitle: String,
    val route: String,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    val modes = listOf(
        GalleryMode("叠卡翻阅", "卡片堆叠 · 拖拽切换", "stacked_cards", Color(0xFF5B8DEF)),
        GalleryMode("瀑布马赛克", "不等比网格 · 层次感", "mosaic", Color(0xFF2DBD9A)),
        GalleryMode("时间轴视图", "按日期分组 · 高低排列", "timeline", Color(0xFFF08C4B)),
        GalleryMode("拍立得墙", "实体感 · 可拖动重排", "polaroid", Color(0xFFE05C8A)),
        GalleryMode("星云辐射", "主角居中 · 相关环绕", "radial", Color(0xFF8B63D9)),
        GalleryMode("故事模式", "大图主导 · 自动播放", "story", Color(0xFF4BAFD4)),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("创意相册", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            items(modes) { mode ->
                ModeCard(mode = mode, onClick = { navController.navigate(mode.route) })
            }
        }
    }
}

@Composable
fun ModeCard(mode: GalleryMode, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(20.dp))
            .background(mode.color.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(20.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(mode.color.copy(alpha = 0.25f))
                .align(Alignment.TopStart)
        )
        Column {
            Text(
                text = mode.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = mode.color
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = mode.subtitle,
                fontSize = 12.sp,
                color = mode.color.copy(alpha = 0.7f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(title: String, navController: NavController, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding -> content(padding) }
}
