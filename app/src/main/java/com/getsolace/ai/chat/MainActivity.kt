@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.getsolace.ai.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.getsolace.ai.chat.data.AIImageStore
import com.getsolace.ai.chat.data.AppStrategy
import com.getsolace.ai.chat.data.VaultStore
import com.getsolace.ai.chat.ui.screens.ai.AILabFeedScreen
import com.getsolace.ai.chat.ui.screens.gallery.ImageGalaxyScreen
import com.getsolace.ai.chat.ui.screens.gallery.PhotoDetailScreen
import com.getsolace.ai.chat.ui.screens.gallery.MosaicScreen
import com.getsolace.ai.chat.ui.screens.gallery.PolaroidScreen
import com.getsolace.ai.chat.ui.screens.gallery.RadialScreen
import com.getsolace.ai.chat.ui.screens.gallery.StackedCardsScreen
import com.getsolace.ai.chat.ui.screens.gallery.StoryScreen
import com.getsolace.ai.chat.ui.screens.gallery.TimelineScreen
import com.getsolace.ai.chat.ui.screens.home.HomeScreen
import com.getsolace.ai.chat.ui.screens.me.MeScreen
import com.getsolace.ai.chat.ui.screens.vault.VaultScreen
import com.getsolace.ai.chat.ui.screens.video.VideoListScreen
import com.getsolace.ai.chat.ui.screens.video.VideoPlayerScreen
import com.getsolace.ai.chat.ui.theme.*

// ─── Tab Definition ───────────────────────────────────────────────────────────

enum class MainTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME(
        route          = "home",
        label          = "首页",
        selectedIcon   = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    ),
    CREATE(
        route          = "create",
        label          = "创作",
        selectedIcon   = Icons.Filled.AutoAwesome,
        unselectedIcon = Icons.Outlined.AutoAwesome
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
        // dark 样式 = 深色背景 → 状态栏图标/文字显示为白色
        enableEdgeToEdge(
            statusBarStyle     = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        AIImageStore.init(applicationContext)
        VaultStore.init(applicationContext)
        setContent {
            SolaceTheme {
                MainScaffold()
            }
        }
    }
}

// ─── Main Scaffold ────────────────────────────────────────────────────────────

@Composable
fun MainScaffold() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val tabs = MainTab.entries
    val rootRoutes = tabs.map { it.route }.toSet()
    val showBottomBar = currentRoute in rootRoutes

    // ── 策略观察 ─────────────────────────────────────────────────────────────
    val strategy by SolaceApplication.strategyFlow.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    // ── Me tab 红点 ───────────────────────────────────────────────────────────
    val hasNewImage by AIImageStore.hasNewImage.collectAsStateWithLifecycle()

    // 强制更新弹窗：服务端 minVersionCode > 当前 versionCode
    val needForceUpdate = strategy != null &&
            strategy!!.forceUpdate &&
            strategy!!.minVersionCode > BuildConfig.VERSION_CODE

    // 维护模式弹窗
    val inMaintenance = strategy?.maintenance == true


    Log.d("gzk", "MainScaffold() called"+strategy?.featureFlags?.get("name"))

    if (needForceUpdate) {
        StrategyDialog(
            title   = "发现新版本",
            message = "当前版本过低，请更新后继续使用",
            confirmText = "立即更新",
            dismissible = false,
            onConfirm = {
                val url = strategy!!.updateUrl.ifBlank { "market://details?id=${context.packageName}" }
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }
            }
        )
        return
    }

    if (inMaintenance) {
        StrategyDialog(
            title   = "系统维护中",
            message = strategy!!.maintenanceMsg.ifBlank { "服务维护中，请稍后再试" },
            confirmText = "知道了",
            dismissible = false,
            onConfirm = {}
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep)
    ) {
        Scaffold(
            containerColor      = Color.Transparent,
            contentWindowInsets = WindowInsets(0),   // 内容铺满全屏，各屏自行处理系统栏
            bottomBar = {
                if (showBottomBar) {
                    RunMateBottomBar(
                        tabs         = tabs,
                        currentRoute = currentRoute,
                        hasNewImage  = hasNewImage,
                        onTabSelect  = { tab ->
                            if (tab == MainTab.ME) AIImageStore.clearNewImageBadge()
                            navController.navigate(tab.route) {
                                popUpTo(MainTab.HOME.route) {
                                    inclusive = (tab == MainTab.HOME)
                                    saveState = (tab != MainTab.HOME)
                                }
                                launchSingleTop = true
                                restoreState    = (tab != MainTab.HOME)
                            }
                        }
                    )
                }
            }
        ) { innerPadding ->
            MainNavHost(
                navController = navController,
                // 只保留底部 padding（底栏高度），顶部由各屏幕自行处理
                modifier      = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
            )
        }
    }
}

// ─── Bottom Navigation Bar (Floating Glass Design) ───────────────────────────

private val NavBg      = Color(0xCC0E1120)  // 80% dark glass
private val NavBorder  = Color(0x1AFFFFFF)  // 10% white border
private val NavViolet  = Color(0xFF9B7AFF)
private val NavGlow    = Color(0x339B7AFF)

@Composable
fun RunMateBottomBar(
    tabs: List<MainTab>,
    currentRoute: String?,
    hasNewImage: Boolean = false,
    onTabSelect: (MainTab) -> Unit
) {
    // Outer safe-area background — 延伸至系统导航栏底部
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgDeep)
            .navigationBarsPadding()
    ) {
        // Floating pill bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(NavBg)
                .border(1.dp, NavBorder, RoundedCornerShape(28.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                val selected = currentRoute == tab.route
                NavBarItem(
                    tab         = tab,
                    selected    = selected,
                    showBadge   = tab == MainTab.ME && hasNewImage && !selected,
                    onClick     = { onTabSelect(tab) }
                )
            }
        }
    }
}

@Composable
private fun NavBarItem(
    tab: MainTab,
    selected: Boolean,
    showBadge: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .then(
                if (selected) Modifier
                    .background(
                        Brush.linearGradient(
                            listOf(NavViolet.copy(alpha = 0.20f), NavViolet.copy(alpha = 0.10f))
                        )
                    )
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(NavGlow, Color.Transparent),
                                center = Offset(size.width / 2, size.height),
                                radius = size.width * 0.8f
                            ),
                            center = Offset(size.width / 2, size.height),
                            radius = size.width * 0.8f
                        )
                    }
                else Modifier
            )
            .padding(horizontal = if (selected) 18.dp else 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector        = tab.selectedIcon,
                    contentDescription = tab.label,
                    tint               = NavViolet,
                    modifier           = Modifier.size(19.dp)
                )
                Text(
                    tab.label,
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = NavViolet
                )
            }
        } else {
            // Unselected: icon，带红点
            Box(contentAlignment = Alignment.TopEnd) {
                Icon(
                    imageVector        = tab.unselectedIcon,
                    contentDescription = tab.label,
                    tint               = Color(0x73FFFFFF),
                    modifier           = Modifier.size(22.dp)
                )
                if (showBadge) {
                    Box(
                        modifier = Modifier
                            .offset(x = 3.dp, y = (-3).dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF3B30))
                    )
                }
            }
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
        startDestination = MainTab.HOME.route,
        modifier         = modifier
    ) {
        // ── Tab roots ────────────────────────────────────────────────────────
        composable(MainTab.HOME.route)   { HomeScreen(navController = navController) }
        composable(MainTab.CREATE.route) { AILabFeedScreen() }
        composable(MainTab.VAULT.route)  { VaultScreen() }
        composable(MainTab.ME.route)     { MeScreen() }

        // ── Home sub-routes ───────────────────────────────────────────────────
        composable("video_list/{category}") { backStack ->
            val category = backStack.arguments?.getString("category") ?: "ALL_VIDEOS"
            VideoListScreen(categoryName = category, navController = navController)
        }
        // ── Video Player ──────────────────────────────────────────────────────
        composable("video_player/{encodedUri}/{encodedTitle}") { backStack ->
            val encodedUri   = backStack.arguments?.getString("encodedUri") ?: return@composable
            val encodedTitle = backStack.arguments?.getString("encodedTitle") ?: ""
            val videoUri     = android.net.Uri.parse(java.net.URLDecoder.decode(encodedUri, "UTF-8"))
            val title        = java.net.URLDecoder.decode(encodedTitle, "UTF-8")
            VideoPlayerScreen(
                videoUri      = videoUri,
                title         = title,
                navController = navController
            )
        }
        composable("galaxy") {
            ImageGalaxyScreen(navController = navController)
        }
        // ── Photo Detail ──────────────────────────────────────────────────────
        composable("photo_detail/{encodedUri}") { backStack ->
            val encodedUri = backStack.arguments?.getString("encodedUri") ?: return@composable
            PhotoDetailScreen(encodedUri = encodedUri, navController = navController)
        }

        // ── Legacy gallery routes (kept for Create tab sub-navigation) ────────
        composable("stacked_cards") { StackedCardsScreen(navController) }
        composable("mosaic")        { MosaicScreen(navController) }
        composable("timeline")      { TimelineScreen(navController) }
        composable("polaroid")      { PolaroidScreen(navController) }
        composable("radial")        { RadialScreen(navController) }
        composable("story")         { StoryScreen(navController) }
    }
}

// ─── Strategy Dialog ──────────────────────────────────────────────────────────

@Composable
private fun StrategyDialog(
    title: String,
    message: String,
    confirmText: String,
    dismissible: Boolean,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (dismissible) { /* allow */ } },
        containerColor   = CardBgAlt,
        titleContentColor   = TextPrimary,
        textContentColor    = TextSecondary,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text  = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = AccentPrimary, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}
