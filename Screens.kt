package app.tempo.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

@Composable
fun Header(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 22.dp, vertical = 16.dp)) {
        Text(title, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
        Text(subtitle, color = LocalTone.current.dim, fontSize = 14.sp)
    }
}

@Composable
fun EmptyState(title: String, body: String, onImport: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 70.dp, start = 32.dp, end = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(110.dp).glass(CircleShape, 1.2f), contentAlignment = Alignment.Center) { ScLogo(30.dp) }
        Spacer(Modifier.height(22.dp))
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(body, color = LocalTone.current.dim, textAlign = TextAlign.Center)
        Spacer(Modifier.height(22.dp))
        GlassPill(onClick = onImport) { Icon(Icons.Rounded.Add, null, Modifier.size(20.dp)); PillText("Import from SoundCloud") }
    }
}

/** Little animated equaliser shown on the song that is playing. */
@Composable
fun Equalizer(playing: Boolean, color: Color) {
    val inf = rememberInfiniteTransition(label = "eq")
    val h1 by inf.animateFloat(.25f, 1f, infiniteRepeatable(tween(520), RepeatMode.Reverse), label = "h1")
    val h2 by inf.animateFloat(1f, .3f, infiniteRepeatable(tween(430), RepeatMode.Reverse), label = "h2")
    val h3 by inf.animateFloat(.4f, .9f, infiniteRepeatable(tween(610), RepeatMode.Reverse), label = "h3")
    Row(Modifier.height(16.dp).width(18.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        listOf(h1, h2, h3).forEach { h ->
            Box(Modifier.width(4.dp).fillMaxHeight(if (playing) h else .25f).clip(CircleShape).background(color))
        }
    }
}

// ═════════════════════ rows ═════════════════════

@Composable
fun SongRow(
    t: Track, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {}
) {
    val tone = LocalTone.current
    val playing by PlaybackManager.isPlaying.collectAsState()
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier.fillMaxWidth().padding(vertical = 3.dp).bounceClick(onClick = onClick)
            .then(if (active) Modifier.glass(shape, .9f, selected = true) else Modifier)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Art(t.artworkUrl, Modifier.size(58.dp), 17.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold)
            Text(t.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = tone.dim, fontSize = 13.sp)
        }
        if (active) { Equalizer(playing, tone.on); Spacer(Modifier.width(10.dp)) }
        Text(fmt(t.durationMs), color = tone.dim, fontSize = 12.sp)
        trailing()
    }
}

@Composable
fun SongMenu(onAdd: () -> Unit, onOpen: () -> Unit, onRemove: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Rounded.MoreVert, "More", tint = LocalTone.current.dim) }
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

// ═════════════════════ songs (everything you imported) ═════════════════════

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
                GlassPill(onClick = { PlaybackManager.playQueue(list, 0, false) }, enabled = list.isNotEmpty()) {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(20.dp)); PillText("Play all")
                }
                GlassPill(onClick = { if (list.isNotEmpty()) PlaybackManager.playQueue(list, Random.nextInt(list.size), true) }, enabled = list.isNotEmpty()) {
                    Icon(Icons.Rounded.Shuffle, null, Modifier.size(20.dp)); PillText("Shuffle")
                }
            }
            LazyColumn(contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 210.dp)) {
                itemsIndexed(list, key = { _, t -> t.id }) { i, t ->
                    SongRow(t, t.id == playingId, onClick = { PlaybackManager.playQueue(list, i) }, modifier = Modifier.animateItem()) {
                        SongMenu({ onAddToPlaylist(t) }, { uri.openUri(t.url) }, { onRemove(t) })
                    }
                }
                if (list.isEmpty()) item {
                    Text("No matches", Modifier.fillMaxWidth().padding(40.dp), textAlign = TextAlign.Center, color = LocalTone.current.dim)
                }
            }
        }
    }
}

// ═════════════════════ playlists ═════════════════════

@Composable
fun PlaylistsScreen(data: LibraryData, onOpen: (String) -> Unit, onNew: () -> Unit, onDelete: (String) -> Unit) {
    val byId = remember(data.tracks) { data.tracks.associateBy { it.id } }
    val tone = LocalTone.current
    Column(Modifier.fillMaxSize()) {
        Header("Playlists", "${data.playlists.size} playlist${if (data.playlists.size == 1) "" else "s"}")
        LazyVerticalGrid(
            GridCells.Fixed(2),
            contentPadding = PaddingValues(18.dp, 6.dp, 18.dp, 210.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(Modifier.fillMaxWidth().bounceClick(onClick = onNew).glass(RoundedCornerShape(26.dp)).padding(10.dp)) {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1f).glass(RoundedCornerShape(18.dp), .7f, blurDp = 8f),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.Add, null, Modifier.size(40.dp)) }
                    Spacer(Modifier.height(8.dp))
                    Text("New playlist", fontWeight = FontWeight.SemiBold)
                    Text("Create one", color = tone.dim, fontSize = 12.sp)
                }
            }
            gridItems(data.playlists, key = { it.id }) { p ->
                val covers = p.trackIds.mapNotNull { byId[it]?.artworkUrl }
                Box(Modifier.animateItem().fillMaxWidth().bounceClick { onOpen(p.id) }.glass(RoundedCornerShape(26.dp))) {
                    Column(Modifier.padding(10.dp)) {
                        Mosaic(covers, Modifier.fillMaxWidth().aspectRatio(1f), 18.dp)
                        Spacer(Modifier.height(8.dp))
                        Text(p.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${p.trackIds.size} song${if (p.trackIds.size == 1) "" else "s"}", color = tone.dim, fontSize = 12.sp)
                    }
                    var menu by remember { mutableStateOf(false) }
                    Box(Modifier.align(Alignment.TopEnd).padding(14.dp)) {
                        GlassCircle(34.dp, { menu = true }, strength = .7f) { Icon(Icons.Rounded.MoreVert, "More", Modifier.size(20.dp)) }
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
                Text("${tracks.size} song${if (tracks.size == 1) "" else "s"} · $mins min", color = LocalTone.current.dim)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassPill(onClick = { PlaybackManager.playQueue(tracks, 0, false) }, enabled = tracks.isNotEmpty()) {
                        Icon(Icons.Rounded.PlayArrow, null, Modifier.size(20.dp)); PillText("Play")
                    }
                    GlassPill(onClick = { PlaybackManager.playQueue(tracks, Random.nextInt(tracks.size), true) }, enabled = tracks.isNotEmpty()) {
                        Icon(Icons.Rounded.Shuffle, null, Modifier.size(20.dp)); PillText("Shuffle")
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassPill(onClick = onPick) { Icon(Icons.Rounded.Add, null, Modifier.size(20.dp)); PillText("Add songs") }
                    GlassPill(onClick = onImport) { ScLogo(16.dp); PillText("Import") }
                }
                Spacer(Modifier.height(18.dp))
            }
        }
        itemsIndexed(tracks, key = { _, t -> t.id }) { i, t ->
            SongRow(t, t.id == playingId, onClick = { PlaybackManager.playQueue(tracks, i) }, modifier = Modifier.animateItem()) {
                IconButton(onClick = { onRemove(t) }) { Icon(Icons.Rounded.Close, "Remove from playlist", tint = LocalTone.current.dim) }
            }
        }
        if (tracks.isEmpty()) item {
            Text("Empty. Add songs from your library or import a SoundCloud link.",
                Modifier.fillMaxWidth().padding(40.dp), textAlign = TextAlign.Center, color = LocalTone.current.dim)
        }
    }
}

// ═════════════════════ floating bottom bar ═════════════════════

@Composable
fun MiniPlayer(t: Track, onOpen: () -> Unit) {
    val tone = LocalTone.current
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
    val animProg by animateFloatAsState(prog.coerceIn(0f, 1f), tween(450, easing = LinearEasing), label = "mp")
    Box(Modifier.fillMaxWidth().bounceClick(onClick = onOpen).glass(RoundedCornerShape(30.dp), 1.2f, blurDp = 18f)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Art(t.artworkUrl, Modifier.size(50.dp), 16.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(t.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = tone.dim)
            }
            if (loading) CircularProgressIndicator(Modifier.size(26.dp).padding(2.dp), color = tone.on, strokeWidth = 2.5.dp)
            else IconButton(onClick = { PlaybackManager.togglePlay() }) {
                Crossfade(playing, label = "mini-pp") { p -> Icon(if (p) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, Modifier.size(32.dp)) }
            }
            IconButton(onClick = { PlaybackManager.next() }) { Icon(Icons.Rounded.SkipNext, "Next", Modifier.size(28.dp)) }
        }
        LinearProgressIndicator(
            progress = { animProg },
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 26.dp).height(2.dp),
            color = tone.on, trackColor = tone.on.copy(alpha = .15f)
        )
    }
}

@Composable
fun NavBar(tab: Int, onTab: (Int) -> Unit, onImport: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().glass(CircleShape, 1.1f, blurDp = 18f).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavItem("Songs", Icons.Rounded.MusicNote, tab == 0, Modifier.weight(1f)) { onTab(0) }
        Box(Modifier.padding(horizontal = 6.dp)) { GlassCircle(54.dp, onImport) { ScLogo(17.dp) } }
        NavItem("Playlists", Icons.Rounded.LibraryMusic, tab == 1, Modifier.weight(1f)) { onTab(1) }
    }
}

@Composable
private fun NavItem(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val tone = LocalTone.current
    val c by animateColorAsState(if (selected) tone.on else tone.dim, tween(300), label = "nav")
    val shape = CircleShape
    Row(
        modifier.height(54.dp).bounceClick(onClick = onClick)
            .then(if (selected) Modifier.glass(shape, .8f, selected = true, blurDp = 8f) else Modifier),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = c)
        Spacer(Modifier.width(7.dp))
        Text(label, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = c)
    }
}

// ═════════════════════ dialogs (always dark glass, white text) ═════════════════════

@Composable
fun ImportDialog(target: String?, onImport: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val clip = LocalClipboardManager.current
    GlassDialog(onDismiss) {
        val dim = LocalTone.current.dim
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScLogo(22.dp); Spacer(Modifier.width(10.dp))
            Text("Import", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (target != null) "Adding to “$target”. Paste one or more SoundCloud links."
            else "Paste one or more SoundCloud links (soundcloud.com or on.soundcloud.com).",
            color = dim, fontSize = 13.sp
        )
        Spacer(Modifier.height(14.dp))
        TextField(
            text, { text = it }, Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp)),
            placeholder = { Text("https://on.soundcloud.com/…") }, minLines = 2, maxLines = 5, colors = glassFieldColors(),
            trailingIcon = { IconButton(onClick = { clip.getText()?.text?.let { text = it } }) { Icon(Icons.Rounded.ContentPaste, "Paste") } }
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDismiss) { Text("Cancel", color = dim) }
            Spacer(Modifier.width(8.dp))
            GlassPill(onClick = { onImport(text) }, enabled = text.isNotBlank()) { PillText("Import") }
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
            GlassPill(onClick = onDismiss) { PillText("Close") }
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
            TextButton(onClick = onDismiss) { Text("Cancel", color = LocalTone.current.dim) }
            Spacer(Modifier.width(8.dp))
            GlassPill(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { PillText("Create") }
        }
    }
}

@Composable
fun AddToPlaylistDialog(track: Track, playlists: List<Playlist>, onPick: (String) -> Unit, onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    GlassDialog(onDismiss) {
        val dim = LocalTone.current.dim
        Text("Add to playlist", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Text(track.title, color = dim, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.heightIn(max = 260.dp)) {
            items(playlists, key = { it.id }) { p ->
                val has = track.id in p.trackIds
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(enabled = !has) { onPick(p.id) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.LibraryMusic, null, tint = dim)
                    Spacer(Modifier.width(12.dp))
                    Text(p.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (has) Icon(Icons.Rounded.Check, "Already added")
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        TextField(name, { name = it }, Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp)),
            placeholder = { Text("…or create a new playlist") }, singleLine = true, colors = glassFieldColors(),
            trailingIcon = { if (name.isNotBlank()) IconButton(onClick = { onCreate(name) }) { Icon(Icons.Rounded.Check, "Create") } })
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = onDismiss) { Text("Close", color = dim) } }
    }
}

@Composable
fun SongPickerDialog(all: List<Track>, existing: Set<String>, onConfirm: (List<String>) -> Unit, onDismiss: () -> Unit) {
    var sel by remember { mutableStateOf(setOf<String>()) }
    val avail = remember(all, existing) { all.filter { it.id !in existing } }
    GlassDialog(onDismiss) {
        val dim = LocalTone.current.dim
        Text("Add songs", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(10.dp))
        if (avail.isEmpty()) Text("Every song in your library is already in this playlist. Import more with the SoundCloud button.", color = dim)
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
                        Text(t.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = dim, fontSize = 12.sp)
                    }
                    Icon(if (on) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, tint = if (on) Color.White else dim)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDismiss) { Text("Cancel", color = dim) }
            Spacer(Modifier.width(8.dp))
            GlassPill(onClick = { onConfirm(sel.toList()) }, enabled = sel.isNotEmpty()) { PillText(if (sel.isEmpty()) "Add" else "Add ${sel.size}") }
        }
    }
}
