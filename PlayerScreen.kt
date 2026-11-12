package app.tempo.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(t: Track, onClose: () -> Unit) {
    val cover = LocalCover.current
    val tone = LocalTone.current
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
    val bd = rememberBackdrop()

    LaunchedEffect(t.id) {
        while (true) {
            if (!seeking) pos = p.currentPosition
            dur = if (p.duration > 0) p.duration else t.durationMs
            delay(200)
        }
    }

    // cover "breathes": full size while playing, slightly smaller when paused
    val coverScale by animateFloatAsState(if (playing) 1f else .90f, spring(dampingRatio = .6f, stiffness = 220f), label = "coverScale")

    Box(Modifier.fillMaxSize().clickable(enabled = false) {}) {
        Box(Modifier.fillMaxSize().recordBackdrop(bd)) {
            AuroraBackground(cover, Modifier.fillMaxSize(), artUrl = t.artworkUrl)
        }

        CompositionLocalProvider(LocalBackdrop provides bd) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                    .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // top bar
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassCircle(46.dp, onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Close", Modifier.size(28.dp)) }
                    Spacer(Modifier.weight(1f))
                    Text("NOW PLAYING", fontSize = 12.sp, letterSpacing = 2.5.sp, color = tone.dim, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    GlassCircle(46.dp, { uri.openUri(t.url) }) { ScLogo(14.dp) }
                }

                Spacer(Modifier.height(22.dp))

                // cover
                val coverShape = RoundedCornerShape(36.dp)
                Crossfade(targetState = t.id, animationSpec = tween(450), label = "cover") { _ ->
                    Box(
                        Modifier.fillMaxWidth(.86f).widthIn(max = 360.dp).aspectRatio(1f)
                            .graphicsLayer { scaleX = coverScale; scaleY = coverScale }
                            .shadow(40.dp, coverShape, ambientColor = cover.a, spotColor = cover.a)
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
                Text(t.artist, fontSize = 17.sp, color = tone.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(12.dp))
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
                        thumbColor = tone.on, activeTrackColor = tone.on, inactiveTrackColor = tone.on.copy(alpha = .22f)
                    )
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                    Text(fmt(if (seeking) (seekVal * dur).toLong() else pos), fontSize = 12.sp, color = tone.dim)
                    Spacer(Modifier.weight(1f))
                    Text(fmt(dur), fontSize = 12.sp, color = tone.dim)
                }

                // transport (all liquid glass)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassCircle(48.dp, { PlaybackManager.toggleShuffle() }, selected = shuffle) { Icon(Icons.Rounded.Shuffle, "Shuffle") }
                    GlassCircle(58.dp, { PlaybackManager.prev() }) { Icon(Icons.Rounded.SkipPrevious, "Previous", Modifier.size(34.dp)) }
                    GlassCircle(82.dp, { PlaybackManager.togglePlay() }, strength = 1.25f) {
                        if (loading) CircularProgressIndicator(Modifier.size(32.dp), color = tone.on, strokeWidth = 3.dp)
                        else Crossfade(playing, animationSpec = tween(220), label = "pp") { pl ->
                            Icon(if (pl) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (pl) "Pause" else "Play", Modifier.size(44.dp))
                        }
                    }
                    GlassCircle(58.dp, { PlaybackManager.next() }) { Icon(Icons.Rounded.SkipNext, "Next", Modifier.size(34.dp)) }
                    GlassCircle(48.dp, { PlaybackManager.cycleRepeat() }, selected = repeat != RepeatMode.OFF) {
                        Icon(if (repeat == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Repeat")
                    }
                }

                // extras
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    GlassAction("Queue", onClick = { showQueue = true }) { Icon(Icons.AutoMirrored.Rounded.QueueMusic, "Queue") }
                    Box {
                        val rem = sleepAt?.let { (((it - System.currentTimeMillis()) / 60000) + 1).coerceAtLeast(1) }
                        GlassAction(if (rem != null) "$rem min" else "Sleep", onClick = { sleepMenu = true }, selected = rem != null) {
                            Icon(Icons.Rounded.Bedtime, "Sleep timer")
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
    }

    if (showQueue) {
        ModalBottomSheet(
            onDismissRequest = { showQueue = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color(0xFF0B1220).copy(alpha = .94f), contentColor = Color.White
        ) {
            CompositionLocalProvider(LocalTone provides DarkTone, LocalBackdrop provides null) {
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
}

@Composable
private fun Chip(text: String) {
    Text(text, Modifier.glass(CircleShape, .9f, blurDp = 10f).padding(horizontal = 16.dp, vertical = 7.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun GlassAction(label: String, onClick: () -> Unit, selected: Boolean = false, content: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GlassCircle(56.dp, onClick, selected = selected, content = content)
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 11.sp, color = LocalTone.current.dim)
    }
}

/** Tempo panel: always smoked liquid glass with white text, independent of the cover colours. */
@Composable
fun TempoCard(speed: Float) {
    CompositionLocalProvider(LocalTone provides DarkTone, LocalContentColor provides Color.White) {
        val dim = DarkTone.dim
        Column(
            Modifier.fillMaxWidth().glass(RoundedCornerShape(30.dp), 1.1f, smoke = .34f, blurDp = 18f)
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Speed, null)
                Spacer(Modifier.width(8.dp))
                Text("Tempo", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(fmtSpeed(speed), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            }
            Slider(
                value = speed,
                onValueChange = { PlaybackManager.setSpeed(it) },
                valueRange = MIN_SPEED..MAX_SPEED,
                steps = 89, // 0.45 → 1.35 in 0.01 increments
                colors = SliderDefaults.colors(
                    thumbColor = Color.White, activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = .22f),
                    activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent
                )
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("0.45x deep", fontSize = 11.sp, color = dim)
                Spacer(Modifier.weight(1f))
                GlassCircle(44.dp, { PlaybackManager.setSpeed(speed - 0.01f) }) { Icon(Icons.Rounded.Remove, "-0.01x") }
                Spacer(Modifier.width(10.dp))
                GlassCircle(44.dp, { PlaybackManager.setSpeed(speed + 0.01f) }) { Icon(Icons.Rounded.Add, "+0.01x") }
                Spacer(Modifier.weight(1f))
                Text("high 1.35x", fontSize = 11.sp, color = dim)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Slowed" to 0.80f, "Normal" to 1.00f, "Nightcore" to 1.25f).forEach { (label, v) ->
                    val sel = abs(speed - v) < .004f
                    Box(
                        Modifier.weight(1f).bounceClick { PlaybackManager.setSpeed(v) }
                            .glass(CircleShape, .9f, selected = sel, blurDp = 8f).padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("$label ${fmtSpeed(v)}", fontSize = 12.sp, fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.SemiBold) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Pitch follows speed · saved for this song", fontSize = 11.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = dim)
        }
    }
}
