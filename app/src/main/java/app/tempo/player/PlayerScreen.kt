package app.tempo.player

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs

private val Dim = Color.White.copy(alpha = .68f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(t: Track, onClose: () -> Unit) {
    val cover = LocalCover.current
    val p = PlaybackManager.player
    val playing by PlaybackManager.isPlaying.collectAsState()
    val loading by PlaybackManager.loading.collectAsState()
    val speed by PlaybackManager.speed.collectAsState()
    val shuffle by PlaybackManager.shuffle.collectAsState()
    val repeat by PlaybackManager.repeat.collectAsState()
    val sleepAt by PlaybackManager.sleepAt.collectAsState()
    val queue by PlaybackManager.queue.collectAsState()
    val idx by PlaybackManager.index.collectAsState()
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(t.durationMs) }
    var seeking by remember { mutableStateOf(false) }
    var seekVal by remember { mutableFloatStateOf(0f) }
    var showQueue by remember { mutableStateOf(false) }
    var sleepMenu by remember { mutableStateOf(false) }
    val uri = LocalUriHandler.current

    LaunchedEffect(t.id) {
        while (true) {
            if (!seeking) pos = p.currentPosition
            dur = if (p.duration > 0) p.duration else t.durationMs
            delay(200)
        }
    }

    Box(Modifier.fillMaxSize().background(Ink).clickable(enabled = false) {}) {
        AuroraBackground(cover, Modifier.fillMaxSize(), artUrl = t.artworkUrl, intensity = 1f)

        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // top bar
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassCircle(44.dp, onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Close", Modifier.size(28.dp)) }
                Spacer(Modifier.weight(1f))
                Text("NOW PLAYING", fontSize = 12.sp, letterSpacing = 2.5.sp, color = Dim, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                GlassCircle(44.dp, { uri.openUri(t.url) }) { ScLogo(14.dp) }
            }

            Spacer(Modifier.height(22.dp))

            // cover
            val coverShape = RoundedCornerShape(36.dp)
            Crossfade(targetState = t.id, label = "cover") { _ ->
                Box(
                    Modifier.fillMaxWidth(.86f).widthIn(max = 360.dp).aspectRatio(1f)
                        .shadow(40.dp, coverShape, ambientColor = cover.accent, spotColor = cover.accent)
                ) {
                    Art(t.artworkUrl, Modifier.fillMaxSize(), 36.dp)
                    Box(
                        Modifier.fillMaxSize().border(
                            1.2.dp,
                            Brush.linearGradient(listOf(Color.White.copy(alpha = .7f), Color.White.copy(alpha = .05f), Color.White.copy(alpha = .3f))),
                            coverShape
                        )
                    )
                }
            }

            Spacer(Modifier.height(26.dp))
            Text(t.title, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 30.sp)
            Spacer(Modifier.height(4.dp))
            Text(t.artist, fontSize = 17.sp, color = Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(fmt(dur))
                Chip(fmtSpeed(speed))
            }

            // seek
            Spacer(Modifier.height(10.dp))
            val progress = if (seeking) seekVal else if (dur > 0) pos.toFloat() / dur else 0f
            Slider(
                value = progress.coerceIn(0f, 1f),
                onValueChange = { seeking = true; seekVal = it },
                onValueChangeFinished = {
                    val to = (seekVal * dur).toLong(); p.seekTo(to); pos = to; seeking = false
                },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White, activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = .22f)
                )
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                Text(fmt(if (seeking) (seekVal * dur).toLong() else pos), fontSize = 12.sp, color = Dim)
                Spacer(Modifier.weight(1f))
                Text(fmt(dur), fontSize = 12.sp, color = Dim)
            }

            // transport
            Row(
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton({ PlaybackManager.toggleShuffle() }, Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Shuffle, "Shuffle", tint = if (shuffle) cover.accent else Dim)
                }
                IconButton({ PlaybackManager.prev() }, Modifier.size(56.dp)) { Icon(Icons.Rounded.SkipPrevious, "Previous", Modifier.size(38.dp)) }
                Box(
                    Modifier.size(78.dp).clip(CircleShape).background(Color.White).clickable { PlaybackManager.togglePlay() },
                    contentAlignment = Alignment.Center
                ) {
                    if (loading) CircularProgressIndicator(Modifier.size(32.dp), color = Color.Black, strokeWidth = 3.dp)
                    else Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, Modifier.size(44.dp), tint = Color.Black)
                }
                IconButton({ PlaybackManager.next() }, Modifier.size(56.dp)) { Icon(Icons.Rounded.SkipNext, "Next", Modifier.size(38.dp)) }
                IconButton({ PlaybackManager.cycleRepeat() }, Modifier.size(48.dp)) {
                    Icon(
                        if (repeat == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Repeat",
                        tint = if (repeat == RepeatMode.OFF) Dim else cover.accent
                    )
                }
            }

            // extras
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                GlassAction("Queue", onClick = { showQueue = true }) { Icon(Icons.AutoMirrored.Rounded.QueueMusic, "Queue") }
                Box {
                    val rem = sleepAt?.let { (((it - System.currentTimeMillis()) / 60000) + 1).coerceAtLeast(1) }
                    GlassAction(if (rem != null) "${rem} min" else "Sleep", onClick = { sleepMenu = true }) {
                        Icon(Icons.Rounded.Bedtime, "Sleep timer", tint = if (rem != null) cover.accent else Color.White)
                    }
                    DropdownMenu(sleepMenu, { sleepMenu = false }) {
                        listOf<Pair<Int?, String>>(null to "Off", 15 to "15 minutes", 30 to "30 minutes", 45 to "45 minutes", 60 to "1 hour")
                            .forEach { (m, label) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = { PlaybackManager.setSleep(m); sleepMenu = false })
                            }
                    }
                }
                GlassAction("SoundCloud", onClick = { uri.openUri(t.url) }) { ScLogo(13.dp) }
            }

            Spacer(Modifier.height(16.dp))
            TempoCard(speed)
            Spacer(Modifier.height(28.dp))
        }
    }

    if (showQueue) {
        ModalBottomSheet(
            onDismissRequest = { showQueue = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color(0xFF0B1220).copy(alpha = .94f), contentColor = Color.White
        ) {
            Text("Up next", Modifier.padding(horizontal = 24.dp, vertical = 6.dp), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            LazyColumn(Modifier.heightIn(max = 520.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                itemsIndexed(queue, key = { i, tr -> "${tr.id}-$i" }) { i, tr ->
                    SongRow(tr, i == idx, onClick = { PlaybackManager.playIndex(i) })
                }
            }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun Chip(text: String) {
    Text(text, Modifier.glass(CircleShape, .9f).padding(horizontal = 14.dp, vertical = 6.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun GlassAction(label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GlassCircle(54.dp, onClick, content)
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 11.sp, color = Dim)
    }
}

@Composable
fun TempoCard(speed: Float) {
    val cover = LocalCover.current
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(30.dp), 1.15f).padding(horizontal = 18.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Speed, null, tint = cover.accent)
            Spacer(Modifier.width(8.dp))
            Text("Tempo", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(fmtSpeed(speed), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = cover.accent)
        }
        Slider(
            value = speed,
            onValueChange = { PlaybackManager.setSpeed(it) },
            valueRange = MIN_SPEED..MAX_SPEED,
            steps = 89, // 0.45 → 1.35 in 0.01 increments
            colors = SliderDefaults.colors(
                thumbColor = cover.accent, activeTrackColor = cover.accent,
                inactiveTrackColor = Color.White.copy(alpha = .18f),
                activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent
            )
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("0.45x deep", fontSize = 11.sp, color = Dim)
            Spacer(Modifier.weight(1f))
            FilledTonalIconButton(
                { PlaybackManager.setSpeed(speed - 0.01f) },
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.White.copy(alpha = .12f), contentColor = Color.White)
            ) { Icon(Icons.Rounded.Remove, "-0.01x") }
            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(
                { PlaybackManager.setSpeed(speed + 0.01f) },
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.White.copy(alpha = .12f), contentColor = Color.White)
            ) { Icon(Icons.Rounded.Add, "+0.01x") }
            Spacer(Modifier.weight(1f))
            Text("high 1.35x", fontSize = 11.sp, color = Dim)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Slowed" to 0.80f, "Normal" to 1.00f, "Nightcore" to 1.25f).forEach { (label, v) ->
                val sel = abs(speed - v) < .004f
                Box(
                    Modifier.weight(1f).clip(CircleShape)
                        .background(if (sel) Color.White.copy(alpha = .94f) else Color.White.copy(alpha = .10f))
                        .clickable { PlaybackManager.setSpeed(v) }.padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("$label ${fmtSpeed(v)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (sel) Color.Black else Color.White)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Pitch follows speed", fontSize = 11.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = Dim)
    }
}
