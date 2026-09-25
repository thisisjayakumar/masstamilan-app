package com.masstamilan.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.masstamilan.app.core.di.AppModule
import com.masstamilan.app.feature.home.HomeScreen
import com.masstamilan.app.feature.player.PlayerScreen
import com.masstamilan.app.feature.search.SearchScreen
import com.masstamilan.app.feature.songdetail.SongDetailScreen
import com.masstamilan.app.feature.downloads.DownloadsScreen
import com.masstamilan.app.ui.theme.MasstamilanAppTheme
import dagger.hilt.android.AndroidEntryPoint

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
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") { HomeScreen(navController) }
                        composable("search") { SearchScreen(navController) }
                        composable("search/{query}") { backStackEntry ->
                            val query = backStackEntry.arguments?.getString("query") ?: ""
                            SearchScreen(navController, initialQuery = query)
                        }
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
        }
    }
}
