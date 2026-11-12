package app.tempo.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val (cover, tone) = rememberCover(current?.artworkUrl)

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
    val bdA = rememberBackdrop()   // aurora only  → glass that lives inside the screens
    val bdB = rememberBackdrop()   // aurora + screen → floating bars (they blur the list scrolling under them)

    CompositionLocalProvider(LocalCover provides cover, LocalTone provides tone, LocalContentColor provides tone.on) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().recordBackdrop(bdB)) {
                Box(Modifier.fillMaxSize().recordBackdrop(bdA)) { AuroraBackground(cover, Modifier.fillMaxSize()) }

                CompositionLocalProvider(LocalBackdrop provides bdA) {
                    val screenKey = openPlaylist ?: if (tab == 0) "songs" else "playlists"
                    AnimatedContent(
                        targetState = screenKey,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = {
                            (fadeIn(tween(300, delayMillis = 80)) + scaleIn(tween(300, delayMillis = 80), initialScale = .95f)) togetherWith
                                fadeOut(tween(180))
                        },
                        label = "screens"
                    ) { key ->
                        when (key) {
                            "songs" -> SongsScreen(
                                data.tracks, playingId,
                                onImport = { importTarget = null; importOpen = true },
                                onAddToPlaylist = { addToPl = it },
                                onRemove = { vm.deleteTrack(it.id) }
                            )
                            "playlists" -> PlaylistsScreen(data, { openPlaylist = it }, { newPlaylist = true }, { vm.deletePlaylist(it) })
                            else -> {
                                val pl = data.playlists.firstOrNull { it.id == key }
                                if (pl != null) PlaylistDetail(
                                    pl, pl.trackIds.mapNotNull { byId[it] }, playingId,
                                    onBack = { openPlaylist = null },
                                    onImport = { importTarget = pl.id; importOpen = true },
                                    onPick = { pickFor = pl.id },
                                    onRemove = { vm.removeFromPlaylist(pl.id, it.id) }
                                )
                            }
                        }
                    }
                }
            }

            CompositionLocalProvider(LocalBackdrop provides bdB) {
                Column(
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AnimatedVisibility(
                        current != null,
                        enter = slideInVertically(tween(450, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(350)),
                        exit = slideOutVertically { it } + fadeOut()
                    ) { current?.let { MiniPlayer(it) { showPlayer = true } } }
                    NavBar(
                        tab, { tab = it; openPlaylist = null },
                        onImport = { importTarget = openPlaylist; importOpen = true }
                    )
                }

                // status pill (importing / added)
                val status = if (busy) "Importing…" else msg
                Box(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp)) {
                    AnimatedVisibility(
                        status != null,
                        enter = slideInVertically(tween(350)) { -it } + fadeIn(tween(300)),
                        exit = slideOutVertically(tween(250)) { -it } + fadeOut(tween(200))
                    ) {
                        Row(
                            Modifier.glass(CircleShape, 1.2f, blurDp = 16f).padding(horizontal = 18.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (busy) CircularProgressIndicator(Modifier.size(16.dp), color = tone.on, strokeWidth = 2.dp)
                            Text(status ?: "", fontSize = 14.sp)
                        }
                    }
                }
            }

            AnimatedVisibility(
                showPlayer && current != null,
                enter = slideInVertically(tween(480, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(300)),
                exit = slideOutVertically(tween(360)) { it } + fadeOut(tween(260))
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
