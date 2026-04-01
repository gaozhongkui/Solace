package com.getsolace.ai.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.getsolace.ai.chat.data.AIImageStore
import com.getsolace.ai.chat.data.VaultStore
import com.getsolace.ai.chat.ui.screens.ai.CreateAIScreen
import com.getsolace.ai.chat.ui.screens.gallery.ImageGalaxyScreen
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
    // Also show bottom bar on galaxy screen
    val showBottomBar = currentRoute in rootRoutes || currentRoute == "galaxy"

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
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
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
        startDestination = MainTab.HOME.route,
        modifier         = modifier
    ) {
        // ── Tab roots ────────────────────────────────────────────────────────
        composable(MainTab.HOME.route)   { HomeScreen(navController = navController) }
        composable(MainTab.CREATE.route) { CreateAIScreen(navController = navController) }
        composable(MainTab.VAULT.route)  { VaultScreen() }
        composable(MainTab.ME.route)     { MeScreen() }

        // ── Home sub-routes ───────────────────────────────────────────────────
        composable("video_list/{category}") { backStack ->
            val category = backStack.arguments?.getString("category") ?: "ALL_VIDEOS"
            VideoListScreen(categoryName = category, navController = navController)
        }
        composable("galaxy") {
            ImageGalaxyScreen(navController = navController)
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
