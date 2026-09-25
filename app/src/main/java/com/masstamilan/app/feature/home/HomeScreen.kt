package com.masstamilan.app.feature.home

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.masstamilan.app.core.media.PlaybackManager
import com.masstamilan.app.core.network.NetworkHelper
import com.masstamilan.app.data.model.SongResult
import com.masstamilan.app.data.remote.MasstamilanApi
import com.masstamilan.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController, api: MasstamilanApi? = null) {
    val scope = rememberCoroutineScope()
    var movies by remember { mutableStateOf<List<MovieItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            val html = api?.getHomePage() ?: ""
            movies = parseHomePage(html)
        } catch (e: Exception) {
            Toast.makeText(navController.context, "Error loading", Toast.LENGTH_SHORT).show()
        } finally {
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MassTamilan", color = TextPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Surface) {
                NavigationBarItem(
                    selected = true,
                    onClick = {},
                    label = { Text("Home") },
                    icon = { Icon(Icons.Default.Search, "Home") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate("search") },
                    label = { Text("Search") },
                    icon = { Icon(Icons.Default.Search, "Search") }
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp)) {
                    items(movies) { movie ->
                        MovieCard(movie, navController)
                    }
                }
            }
        }
    }
}

data class MovieItem(
    val name: String,
    val slug: String,
    val posterUrl: String = "",
    val starring: String = ""
)

fun parseHomePage(html: String): List<MovieItem> {
    val results = mutableListOf<MovieItem>()
    val pattern = Regex("""<a href="(/[^"]+\\.songs)"[^>]*>\s*<img[^>]+src="([^"]+)"[^>]+>\s*<h2[^>]*>([^<]+)""")
    val starPattern = Regex("""Starring:\s*([^<]+)""")
    pattern.findAll(html).forEach { match ->
        val slug = match.groupValues[1].replace("-songs", "")
        val poster = match.groupValues[2]
        val name = match.groupValues[3]
        results.add(MovieItem(name = name, slug = slug, posterUrl = poster))
    }
    return results.take(10)
}

@Composable
fun MovieCard(movie: MovieItem, navController: NavController) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Card)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            AsyncImage(
                model = movie.posterUrl,
                contentDescription = movie.name,
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(movie.name, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(movie.starring, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, maxLines = 2)
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { navController.navigate("song_detail/${movie.slug}") },
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Text("Listen", color = TextPrimary)
                }
            }
        }
    }
}
