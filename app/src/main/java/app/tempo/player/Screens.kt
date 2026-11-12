package app.tempo.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

private val Dim = Color.White.copy(alpha = .68f)

@Composable
fun Header(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 22.dp, vertical = 16.dp)) {
        Text(title, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
        Text(subtitle, color = Dim, fontSize = 14.sp)
    }
}

@Composable
fun EmptyState(title: String, body: String, onImport: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 70.dp, start = 32.dp, end = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(110.dp).glass(CircleShape), contentAlignment = Alignment.Center) { ScLogo(30.dp) }
        Spacer(Modifier.height(22.dp))
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(body, color = Dim, textAlign = TextAlign.Center)
        Spacer(Modifier.height(22.dp))
        SolidPill("Import from SoundCloud", Icons.Rounded.Add, onClick = onImport)
    }
}

// ───────────────────────── rows ─────────────────────────

@Composable
fun SongRow(t: Track, active: Boolean, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    val accent = LocalCover.current.accent
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(shape)
            .then(if (active) Modifier.glass(shape, .9f) else Modifier)
            .clickable(onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Art(t.artworkUrl, Modifier.size(58.dp), 17.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold,
                color = if (active) accent else Color.White)
            Text(t.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Dim, fontSize = 13.sp)
        }
        Text(fmt(t.durationMs), color = Dim, fontSize = 12.sp)
        trailing()
    }
}

@Composable
fun SongMenu(onAdd: () -> Unit, onOpen: () -> Unit, onRemove: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Rounded.MoreVert, "More", tint = Dim) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Add to playlist") }, leadingIcon = { Icon(Icons.Rounded.Add, null) },
                onClick = { open = false; onAdd() })
            DropdownMenuItem(text = { Text("Open on SoundCloud") }, leadingIcon = { Icon(Icons.Rounded.Link, null) },
                onClick = { open = false; onOpen() })
            DropdownMenuItem(text = { Text("Remove from library") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                onClick = { open = false; onRemove() })
        }
    }
}

// ───────────────────────── songs (everything you imported) ─────────────────────────

@Composable
fun SongsScreen(
    tracks: List<Track>, playingId: String?, onImport: () -> Unit,
    onAddToPlaylist: (Track) -> Unit, onRemove: (Track) -> Unit
) {
    var q by remember { mutableStateOf("") }
    val list = remember(tracks, q) {
        if (q.isBlank()) tracks else tracks.filter { it.title.contains(q, true) || it.artist.contains(q, true) }
    }
    val uri = LocalUriHandler.current
    Column(Modifier.fillMaxSize()) {
        Header("Songs", "${tracks.size} saved from SoundCloud")
        if (tracks.isEmpty()) {
            EmptyState("Nothing here yet", "Paste a SoundCloud link and the song appears here with its cover.", onImport)
        } else {
            TextField(
                q, { q = it }, Modifier.padding(horizontal = 18.dp).fillMaxWidth().glass(CircleShape),
                placeholder = { Text("Search songs") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true, colors = glassFieldColors()
            )
            Row(Modifier.padding(18.dp, 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SolidPill("Play all", Icons.Rounded.PlayArrow) { PlaybackManager.playQueue(list, 0, false) }
                GlassPill(onClick = { if (list.isNotEmpty()) PlaybackManager.playQueue(list, Random.nextInt(list.size), true) }) {
                    Icon(Icons.Rounded.Shuffle, null, Modifier.size(20.dp)); Text("Shuffle", fontWeight = FontWeight.SemiBold)
                }
            }
            LazyColumn(contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 210.dp)) {
                itemsIndexed(list, key = { _, t -> t.id }) { i, t ->
                    SongRow(t, t.id == playingId, onClick = { PlaybackManager.playQueue(list, i) }) {
                        SongMenu({ onAddToPlaylist(t) }, { uri.openUri(t.url) }, { onRemove(t) })
                    }
                }
                if (list.isEmpty()) item {
                    Text("No matches", Modifier.fillMaxWidth().padding(40.dp), textAlign = TextAlign.Center, color = Dim)
                }
            }
        }
    }
}

// ───────────────────────── playlists ─────────────────────────

@Composable
fun PlaylistsScreen(data: LibraryData, onOpen: (String) -> Unit, onNew: () -> Unit, onDelete: (String) -> Unit) {
    val byId = remember(data.tracks) { data.tracks.associateBy { it.id } }
    Column(Modifier.fillMaxSize()) {
        Header("Playlists", "${data.playlists.size} playlist${if (data.playlists.size == 1) "" else "s"}")
        LazyVerticalGrid(
            GridCells.Fixed(2),
            contentPadding = PaddingValues(18.dp, 6.dp, 18.dp, 210.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(26.dp)).clickable(onClick = onNew).padding(10.dp)) {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = .08f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.Add, null, Modifier.size(40.dp)) }
                    Spacer(Modifier.height(8.dp))
                    Text("New playlist", fontWeight = FontWeight.SemiBold)
                    Text("Create one", color = Dim, fontSize = 12.sp)
                }
            }
            gridItems(data.playlists, key = { it.id }) { p ->
                val covers = p.trackIds.mapNotNull { byId[it]?.artworkUrl }
                Box(Modifier.fillMaxWidth().glass(RoundedCornerShape(26.dp)).clickable { onOpen(p.id) }) {
                    Column(Modifier.padding(10.dp)) {
                        Mosaic(covers, Modifier.fillMaxWidth().aspectRatio(1f), 18.dp)
                        Spacer(Modifier.height(8.dp))
                        Text(p.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${p.trackIds.size} song${if (p.trackIds.size == 1) "" else "s"}", color = Dim, fontSize = 12.sp)
                    }
                    var menu by remember { mutableStateOf(false) }
                    Box(Modifier.align(Alignment.TopEnd).padding(14.dp)) {
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(Color.Black.copy(alpha = .35f)).clickable { menu = true },
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Rounded.MoreVert, "More", Modifier.size(20.dp)) }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("Delete playlist") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                                onClick = { menu = false; onDelete(p.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistDetail(
    pl: Playlist, tracks: List<Track>, playingId: String?, onBack: () -> Unit,
    onImport: () -> Unit, onPick: () -> Unit, onRemove: (Track) -> Unit
) {
    val mins = tracks.sumOf { it.durationMs } / 60000
    LazyColumn(contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 210.dp)) {
        item {
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                    GlassCircle(44.dp, onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                }
                Spacer(Modifier.height(10.dp))
                Mosaic(tracks.map { it.artworkUrl }, Modifier.size(190.dp), 32.dp)
                Spacer(Modifier.height(16.dp))
                Text(pl.name, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
                Text("${tracks.size} song${if (tracks.size == 1) "" else "s"} · $mins min", color = Dim)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SolidPill("Play", Icons.Rounded.PlayArrow, enabled = tracks.isNotEmpty()) { PlaybackManager.playQueue(tracks, 0, false) }
                    GlassPill(onClick = { if (tracks.isNotEmpty()) PlaybackManager.playQueue(tracks, Random.nextInt(tracks.size), true) }) {
                        Icon(Icons.Rounded.Shuffle, null, Modifier.size(20.dp)); Text("Shuffle", fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassPill(onClick = onPick) { Icon(Icons.Rounded.Add, null, Modifier.size(20.dp)); Text("Add songs") }
                    GlassPill(onClick = onImport) { ScLogo(16.dp); Text("Import") }
                }
                Spacer(Modifier.height(18.dp))
            }
        }
        itemsIndexed(tracks, key = { _, t -> t.id }) { i, t ->
            SongRow(t, t.id == playingId, onClick = { PlaybackManager.playQueue(tracks, i) }) {
                IconButton(onClick = { onRemove(t) }) { Icon(Icons.Rounded.Close, "Remove from playlist", tint = Dim) }
            }
        }
        if (tracks.isEmpty()) item {
            Text("Empty. Add songs from your library or import a SoundCloud link.",
                Modifier.fillMaxWidth().padding(40.dp), textAlign = TextAlign.Center, color = Dim)
        }
    }
}

// ───────────────────────── floating bottom bar ─────────────────────────

@Composable
fun MiniPlayer(t: Track, onOpen: () -> Unit) {
    val cover = LocalCover.current
    val playing by PlaybackManager.isPlaying.collectAsState()
    val loading by PlaybackManager.loading.collectAsState()
    var prog by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(t.id) {
        while (true) {
            val d = PlaybackManager.player.duration
            prog = if (d > 0) PlaybackManager.player.currentPosition.toFloat() / d else 0f
            kotlinx.coroutines.delay(500)
        }
    }
    Box(Modifier.fillMaxWidth().glass(RoundedCornerShape(30.dp), 1.3f).clickable(onClick = onOpen)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Art(t.artworkUrl, Modifier.size(50.dp), 16.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(t.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = Dim)
            }
            if (loading) CircularProgressIndicator(Modifier.size(26.dp).padding(2.dp), color = cover.accent, strokeWidth = 2.5.dp)
            else IconButton(onClick = { PlaybackManager.togglePlay() }) {
                Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, Modifier.size(32.dp))
            }
            IconButton(onClick = { PlaybackManager.next() }) { Icon(Icons.Rounded.SkipNext, "Next", Modifier.size(28.dp)) }
        }
        LinearProgressIndicator(
            progress = { prog.coerceIn(0f, 1f) },
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 26.dp).height(2.dp),
            color = cover.accent, trackColor = Color.White.copy(alpha = .12f)
        )
    }
}

@Composable
fun NavBar(tab: Int, onTab: (Int) -> Unit, onImport: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().glass(CircleShape, 1.3f).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavItem("Songs", Icons.Rounded.MusicNote, tab == 0, Modifier.weight(1f)) { onTab(0) }
        Box(
            Modifier.padding(horizontal = 6.dp).size(54.dp).clip(CircleShape).background(Color.White.copy(alpha = .94f)).clickable(onClick = onImport),
            contentAlignment = Alignment.Center
        ) {
            Image(painterResource(R.drawable.sc_logo), "Import from SoundCloud", Modifier.height(18.dp), colorFilter = ColorFilter.tint(Color.Black))
        }
        NavItem("Playlists", Icons.Rounded.LibraryMusic, tab == 1, Modifier.weight(1f)) { onTab(1) }
    }
}

@Composable
private fun NavItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(54.dp).clip(CircleShape)
            .background(if (selected) Color.White.copy(alpha = .16f) else Color.Transparent)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = if (selected) Color.White else Dim)
        Spacer(Modifier.width(7.dp))
        Text(label, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) Color.White else Dim)
    }
}

// ───────────────────────── dialogs ─────────────────────────

@Composable
fun ImportDialog(target: String?, onImport: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val clip = LocalClipboardManager.current
    GlassDialog(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScLogo(22.dp); Spacer(Modifier.width(10.dp))
            Text("Import", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (target != null) "Adding to “$target”. Paste one or more SoundCloud links."
            else "Paste one or more SoundCloud links (soundcloud.com or on.soundcloud.com).",
            color = Dim, fontSize = 13.sp
        )
        Spacer(Modifier.height(14.dp))
        TextField(
            text, { text = it }, Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp)),
            placeholder = { Text("https://on.soundcloud.com/…") }, minLines = 2, maxLines = 5, colors = glassFieldColors(),
            trailingIcon = { IconButton(onClick = { clip.getText()?.text?.let { text = it } }) { Icon(Icons.Rounded.ContentPaste, "Paste") } }
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Dim) }
            Spacer(Modifier.width(8.dp))
            SolidPill("Import", enabled = text.isNotBlank()) { onImport(text) }
        }
    }
}

@Composable
fun ErrorDialog(text: String, onDismiss: () -> Unit) {
    val clip = LocalClipboardManager.current
    GlassDialog(onDismiss) {
        Text("Something went wrong", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(10.dp))
        Box(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
            SelectionContainer { Text(text, fontSize = 13.sp, color = Color.White.copy(alpha = .85f)) }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { clip.setText(AnnotatedString(text)) }) { Text("Copy error") }
            Spacer(Modifier.weight(1f))
            SolidPill("Close") { onDismiss() }
        }
    }
}

@Composable
fun NewPlaylistDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    GlassDialog(onDismiss) {
        Text("New playlist", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(14.dp))
        TextField(name, { name = it }, Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp)),
            placeholder = { Text("Playlist name") }, singleLine = true, colors = glassFieldColors())
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Dim) }
            Spacer(Modifier.width(8.dp))
            SolidPill("Create", enabled = name.isNotBlank()) { onCreate(name) }
        }
    }
}

@Composable
fun AddToPlaylistDialog(track: Track, playlists: List<Playlist>, onPick: (String) -> Unit, onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    GlassDialog(onDismiss) {
        Text("Add to playlist", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Text(track.title, color = Dim, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.heightIn(max = 260.dp)) {
            items(playlists, key = { it.id }) { p ->
                val has = track.id in p.trackIds
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(enabled = !has) { onPick(p.id) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.LibraryMusic, null, tint = Dim)
                    Spacer(Modifier.width(12.dp))
                    Text(p.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (has) Icon(Icons.Rounded.Check, "Already added", tint = LocalCover.current.accent)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        TextField(name, { name = it }, Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp)),
            placeholder = { Text("…or create a new playlist") }, singleLine = true, colors = glassFieldColors(),
            trailingIcon = { if (name.isNotBlank()) IconButton(onClick = { onCreate(name) }) { Icon(Icons.Rounded.Check, "Create") } })
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = onDismiss) { Text("Close", color = Dim) } }
    }
}

@Composable
fun SongPickerDialog(all: List<Track>, existing: Set<String>, onConfirm: (List<String>) -> Unit, onDismiss: () -> Unit) {
    var sel by remember { mutableStateOf(setOf<String>()) }
    val avail = remember(all, existing) { all.filter { it.id !in existing } }
    GlassDialog(onDismiss) {
        Text("Add songs", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(10.dp))
        if (avail.isEmpty()) Text("Every song in your library is already in this playlist. Import more with the SoundCloud button.", color = Dim)
        LazyColumn(Modifier.heightIn(max = 380.dp)) {
            items(avail, key = { it.id }) { t ->
                val on = t.id in sel
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .clickable { sel = if (on) sel - t.id else sel + t.id }.padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Art(t.artworkUrl, Modifier.size(46.dp), 13.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text(t.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Dim, fontSize = 12.sp)
                    }
                    Icon(if (on) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
                        tint = if (on) LocalCover.current.accent else Dim)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Dim) }
            Spacer(Modifier.width(8.dp))
            SolidPill(if (sel.isEmpty()) "Add" else "Add ${sel.size}", enabled = sel.isNotEmpty()) { onConfirm(sel.toList()) }
        }
    }
}
