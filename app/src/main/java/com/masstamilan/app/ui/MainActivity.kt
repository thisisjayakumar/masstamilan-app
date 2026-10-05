package com.masstamilan.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.masstamilan.app.core.di.AppEntryPoint
import com.masstamilan.app.feature.downloads.DownloadsScreen
import com.masstamilan.app.feature.home.HomeScreen
import com.masstamilan.app.feature.library.LibraryScreen
import com.masstamilan.app.feature.library.PlaylistDetailScreen
import com.masstamilan.app.feature.player.PlayerScreen
import com.masstamilan.app.feature.search.SearchScreen
import com.masstamilan.app.feature.settings.SettingsScreen
import com.masstamilan.app.feature.songdetail.SongDetailScreen
import com.masstamilan.app.ui.theme.MasstamilanAppTheme
import com.masstamilan.app.ui.theme.Surface as SurfaceColor
import com.masstamilan.app.ui.theme.TextPrimary
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("home", "Home", Icons.Default.Home),
    Tab("search", "Search", Icons.Default.Search),
    Tab("library", "Library", Icons.Default.LibraryMusic),
    Tab("settings", "Settings", Icons.Default.Settings)
)

/** Full-player routes hide the bottom shell for an immersive player. */
private fun isPlayerRoute(route: String?): Boolean = route?.startsWith("player/") == true

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MasstamilanAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppShell()
                }
            }
        }
    }
}

@Composable
private fun AppShell() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val context = LocalContext.current
    val playbackManager = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext, AppEntryPoint::class.java
        ).playbackManager()
    }

    Scaffold(
        bottomBar = {
            if (!isPlayerRoute(route)) {
                Column {
                    MiniPlayer(navController, playbackManager)
                    BottomTabs(navController, route)
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding)
        ) {
            composable("home") { HomeScreen(navController) }
            composable("search") { SearchScreen(navController) }
            composable("search/{query}") { backStackEntry ->
                val query = backStackEntry.arguments?.getString("query") ?: ""
                SearchScreen(navController, initialQuery = query)
            }
            composable("library") { LibraryScreen(navController) }
            composable("playlist/{pid}") { backStackEntry ->
                val pid = backStackEntry.arguments?.getString("pid")?.toLongOrNull() ?: 0L
                PlaylistDetailScreen(navController, pid)
            }
            composable("settings") { SettingsScreen(navController) }
            composable("song_detail/{songId}") { backStackEntry ->
                val songId = backStackEntry.arguments?.getString("songId") ?: ""
                SongDetailScreen(navController, songId)
            }
            composable("downloads") { DownloadsScreen(navController) }
            composable("player/{songId}") { backStackEntry ->
                val songId = backStackEntry.arguments?.getString("songId") ?: ""
                PlayerScreen(navController, songId)
            }
        }
    }
}

@Composable
private fun BottomTabs(navController: NavController, route: String?) {
    NavigationBar(containerColor = SurfaceColor) {
        TABS.forEach { tab ->
            val selected = route == tab.route || (tab.route == "search" && route?.startsWith("search/") == true)
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(tab.route) {
                        popUpTo("home")
                        launchSingleTop = true
                    }
                },
                label = { Text(tab.label) },
                alwaysShowLabel = true,
                icon = { Icon(tab.icon, tab.label) }
            )
        }
    }
}
