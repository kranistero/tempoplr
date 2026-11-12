package app.tempo.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay

@Composable
fun App(vm: LibraryViewModel = viewModel()) {
    val data by vm.data.collectAsState()
    val busy by vm.busy.collectAsState()
    val msg by vm.message.collectAsState()
    val importError by vm.error.collectAsState()
    val playError by PlaybackManager.error.collectAsState()
    val queue by PlaybackManager.queue.collectAsState()
    val idx by PlaybackManager.index.collectAsState()
    val current = queue.getOrNull(idx)
    val cover = rememberCoverColors(current?.artworkUrl)

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var openPlaylist by rememberSaveable { mutableStateOf<String?>(null) }
    var showPlayer by remember { mutableStateOf(false) }
    var importOpen by remember { mutableStateOf(false) }
    var importTarget by remember { mutableStateOf<String?>(null) }
    var addToPl by remember { mutableStateOf<Track?>(null) }
    var pickFor by remember { mutableStateOf<String?>(null) }
    var newPlaylist by remember { mutableStateOf(false) }

    LaunchedEffect(msg) { if (msg != null) { delay(2600); vm.message.value = null } }

    BackHandler(showPlayer) { showPlayer = false }
    BackHandler(!showPlayer && openPlaylist != null) { openPlaylist = null }

    val byId = remember(data.tracks) { data.tracks.associateBy { it.id } }
    val playingId = current?.id

    CompositionLocalProvider(LocalCover provides cover) {
        Box(Modifier.fillMaxSize()) {
            AuroraBackground(cover, Modifier.fillMaxSize(), intensity = .8f)

            val pl = data.playlists.firstOrNull { it.id == openPlaylist }
            when {
                pl != null -> PlaylistDetail(
                    pl, pl.trackIds.mapNotNull { byId[it] }, playingId,
                    onBack = { openPlaylist = null },
                    onImport = { importTarget = pl.id; importOpen = true },
                    onPick = { pickFor = pl.id },
                    onRemove = { vm.removeFromPlaylist(pl.id, it.id) }
                )
                tab == 0 -> SongsScreen(
                    data.tracks, playingId,
                    onImport = { importTarget = null; importOpen = true },
                    onAddToPlaylist = { addToPl = it },
                    onRemove = { vm.deleteTrack(it.id) }
                )
                else -> PlaylistsScreen(data, { openPlaylist = it }, { newPlaylist = true }, { vm.deletePlaylist(it) })
            }

            Column(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (current != null) MiniPlayer(current) { showPlayer = true }
                NavBar(
                    tab, { tab = it; openPlaylist = null },
                    onImport = { importTarget = pl?.id; importOpen = true }
                )
            }

            // status pill (importing / added)
            val status = if (busy) "Importing…" else msg
            if (status != null) {
                Row(
                    Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp)
                        .glass(CircleShape, 1.5f).padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    Text(status, fontSize = 14.sp)
                }
            }

            AnimatedVisibility(
                showPlayer && current != null,
                enter = slideInVertically { it }, exit = slideOutVertically { it }
            ) { current?.let { PlayerScreen(it) { showPlayer = false } } }
        }

        if (importOpen) ImportDialog(
            target = importTarget?.let { id -> data.playlists.firstOrNull { it.id == id }?.name },
            onImport = { vm.importLinks(it, importTarget); importOpen = false },
            onDismiss = { importOpen = false }
        )
        if (newPlaylist) NewPlaylistDialog(
            onCreate = { vm.createPlaylist(it); newPlaylist = false },
            onDismiss = { newPlaylist = false }
        )
        addToPl?.let { t ->
            AddToPlaylistDialog(
                t, data.playlists,
                onPick = { vm.addToPlaylist(it, listOf(t.id)); addToPl = null; vm.message.value = "Added to playlist" },
                onCreate = { name -> vm.createPlaylist(name, listOf(t.id)); addToPl = null; vm.message.value = "Playlist created" },
                onDismiss = { addToPl = null }
            )
        }
        pickFor?.let { pid ->
            val existing = data.playlists.firstOrNull { it.id == pid }?.trackIds?.toSet() ?: emptySet()
            SongPickerDialog(data.tracks, existing,
                onConfirm = { vm.addToPlaylist(pid, it); pickFor = null },
                onDismiss = { pickFor = null })
        }
        val shownError = importError ?: playError
        if (shownError != null) ErrorDialog(shownError) {
            vm.error.value = null; PlaybackManager.error.value = null
        }
    }
}
