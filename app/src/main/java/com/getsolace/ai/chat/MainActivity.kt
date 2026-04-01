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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.getsolace.ai.chat.data.AIImageStore
import com.getsolace.ai.chat.data.VaultStore
import com.getsolace.ai.chat.ui.screens.*
import com.getsolace.ai.chat.ui.screens.ai.CreateAIScreen
import com.getsolace.ai.chat.ui.screens.me.MeScreen
import com.getsolace.ai.chat.ui.screens.vault.VaultScreen
import com.getsolace.ai.chat.ui.theme.*

// ─── Tab Definition ───────────────────────────────────────────────────────────

enum class MainTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    CREATE(
        route          = "create",
        label          = "创作",
        selectedIcon   = Icons.Filled.AutoAwesome,
        unselectedIcon = Icons.Outlined.AutoAwesome
    ),
    GALLERY(
        route          = "gallery",
        label          = "相册",
        selectedIcon   = Icons.Filled.PhotoLibrary,
        unselectedIcon = Icons.Outlined.PhotoLibrary
    ),
    VAULT(
        route          = "vault",
        label          = "保险箱",
        selectedIcon   = Icons.Filled.Lock,
        unselectedIcon = Icons.Outlined.Lock
    ),
    ME(
        route          = "me",
        label          = "我的",
        selectedIcon   = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person
    )
}

// ─── MainActivity ─────────────────────────────────────────────────────────────

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AIImageStore.init(applicationContext)
        VaultStore.init(applicationContext)
        setContent {
            SolaceTheme {
                MainScaffold()
            }
        }
    }
}

// ─── Main Scaffold with Bottom Tabs ──────────────────────────────────────────

@Composable
fun MainScaffold() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val tabs = MainTab.entries
    val rootRoutes = tabs.map { it.route }.toSet()
    val showBottomBar = currentRoute in rootRoutes || currentRoute == null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(listOf(BgGradientStart, BgGradientEnd))
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                if (showBottomBar) {
                    RunMateBottomBar(
                        tabs         = tabs,
                        currentRoute = currentRoute,
                        onTabSelect  = { tab ->
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState    = true
                            }
                        }
                    )
                }
            }
        ) { innerPadding ->
            MainNavHost(
                navController = navController,
                modifier      = Modifier.padding(innerPadding)
            )
        }
    }
}

// ─── Bottom Navigation Bar ────────────────────────────────────────────────────

@Composable
fun RunMateBottomBar(
    tabs: List<MainTab>,
    currentRoute: String?,
    onTabSelect: (MainTab) -> Unit
) {
    NavigationBar(
        containerColor = CardBg.copy(alpha = 0.95f),
        contentColor   = TextPrimary,
        tonalElevation = 0.dp
    ) {
        tabs.forEach { tab ->
            val selected = currentRoute == tab.route
            NavigationBarItem(
                selected = selected,
                onClick  = { onTabSelect(tab) },
                icon = {
                    Icon(
                        imageVector        = if (selected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = tab.label
                    )
                },
                label  = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor   = AccentPrimary,
                    selectedTextColor   = AccentPrimary,
                    unselectedIconColor = TextTertiary,
                    unselectedTextColor = TextTertiary,
                    indicatorColor      = AccentPrimary.copy(alpha = 0.15f)
                )
            )
        }
    }
}

// ─── Main Navigation Host ─────────────────────────────────────────────────────

@Composable
fun MainNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController    = navController,
        startDestination = MainTab.CREATE.route,
        modifier         = modifier
    ) {
        composable(MainTab.CREATE.route)   { CreateAIScreen(navController = navController) }
        composable(MainTab.GALLERY.route)  { GalleryHomeScreen(navController = navController) }
        composable(MainTab.VAULT.route)    { VaultScreen() }
        composable(MainTab.ME.route)       { MeScreen() }

        // Sub-routes from Gallery tab
        composable("stacked_cards") { StackedCardsScreen(navController) }
        composable("mosaic")        { MosaicScreen(navController) }
        composable("timeline")      { TimelineScreen(navController) }
        composable("polaroid")      { PolaroidScreen(navController) }
        composable("radial")        { RadialScreen(navController) }
        composable("story")         { StoryScreen(navController) }
    }
}

// ─── Gallery Home (existing creative modes) ───────────────────────────────────

data class GalleryMode(
    val title: String,
    val subtitle: String,
    val route: String,
    val color: Color
)

@Composable
fun GalleryHomeScreen(navController: NavController) {
    val modes = listOf(
        GalleryMode("叠卡翻阅",  "卡片堆叠 · 拖拽切换",  "stacked_cards", Color(0xFF5B8DEF)),
        GalleryMode("瀑布马赛克", "不等比网格 · 层次感",   "mosaic",        Color(0xFF2DBD9A)),
        GalleryMode("时间轴视图", "按日期分组 · 高低排列",  "timeline",      Color(0xFFF08C4B)),
        GalleryMode("拍立得墙",  "实体感 · 可拖动重排",   "polaroid",      Color(0xFFE05C8A)),
        GalleryMode("星云辐射",  "主角居中 · 相关环绕",   "radial",        Color(0xFF8B63D9)),
        GalleryMode("故事模式",  "大图主导 · 自动播放",   "story",         Color(0xFF4BAFD4)),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.xl)
        ) {
            Text(
                text  = "创意相册",
                style = MaterialTheme.typography.headlineLarge.copy(
                    color      = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize   = AppFontSize.largeTitle
                )
            )
        }

        LazyVerticalGrid(
            columns               = GridCells.Fixed(2),
            contentPadding        = PaddingValues(AppSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
            verticalArrangement   = Arrangement.spacedBy(AppSpacing.md),
            modifier              = Modifier.fillMaxSize()
        ) {
            items(modes) { mode ->
                GalleryModeCard(mode = mode, onClick = { navController.navigate(mode.route) })
            }
        }
    }
}

@Composable
fun GalleryModeCard(mode: GalleryMode, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(AppRadius.lg))
            .background(mode.color.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(AppSpacing.xl),
        contentAlignment = Alignment.BottomStart
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(AppRadius.sm))
                .background(mode.color.copy(alpha = 0.25f))
                .align(Alignment.TopStart)
        )
        Column {
            Text(
                text  = mode.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    color      = mode.color,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = Modifier.height(AppSpacing.xs))
            Text(
                text  = mode.subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = mode.color.copy(alpha = 0.7f)
                )
            )
        }
    }
}
