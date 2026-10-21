package io.github.sapog3090.opprice.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.sapog3090.opprice.AppContainer
import io.github.sapog3090.opprice.container
import io.github.sapog3090.opprice.ui.search.SearchScreen
import io.github.sapog3090.opprice.ui.search.SearchViewModel
import io.github.sapog3090.opprice.ui.settings.SettingsScreen
import io.github.sapog3090.opprice.ui.theme.OpTheme
import io.github.sapog3090.opprice.ui.track.TrackDetailScreen
import io.github.sapog3090.opprice.ui.track.TrackDetailViewModel
import io.github.sapog3090.opprice.ui.track.TrackListScreen
import io.github.sapog3090.opprice.ui.track.TrackListViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val c = container
        setContent { OpTheme { AppRoot(c) } }
    }
}

private enum class Tab(val title: String, val icon: ImageVector) {
    SEARCH("Поиск", Icons.Filled.Search),
    TRACKED("Слежу", Icons.Filled.Star),
    SETTINGS("Настройки", Icons.Filled.Settings),
}

@Composable
private fun AppRoot(c: AppContainer) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var openTrack by rememberSaveable { mutableStateOf<Long?>(null) }
    val interval by c.settings.intervalHours.collectAsStateWithLifecycle()
    val searchVm = viewModel { SearchViewModel(c) }
    val listVm = viewModel { TrackListViewModel(c.tracks) }

    val openUrl: (String) -> Unit = { url ->
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "Нет приложения для открытия ссылок", Toast.LENGTH_SHORT).show()
        }
    }

    BackHandler(enabled = openTrack != null) { openTrack = null }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (openTrack == null) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Tab.entries.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.title) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            val trackId = openTrack
            when {
                trackId != null -> {
                    val vm = viewModel(key = "track-$trackId") { TrackDetailViewModel(trackId, c.tracks) }
                    TrackDetailScreen(vm, onBack = { openTrack = null }, openUrl = openUrl)
                }
                tab == Tab.SEARCH.ordinal -> SearchScreen(searchVm, openUrl)
                tab == Tab.TRACKED.ordinal -> TrackListScreen(listVm, interval, onOpen = { openTrack = it })
                else -> SettingsScreen(c)
            }
        }
    }
}
