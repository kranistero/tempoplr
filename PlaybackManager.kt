package app.tempo.player

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

const val MIN_SPEED = 0.45f
const val MAX_SPEED = 1.35f

enum class RepeatMode { OFF, ALL, ONE }

object PlaybackManager {
    lateinit var player: ExoPlayer
        private set
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var sleepJob: Job? = null
    private var order: List<Int> = emptyList()
    private var prefs: SharedPreferences? = null

    val queue = MutableStateFlow<List<Track>>(emptyList())
    val index = MutableStateFlow(-1)
    val isPlaying = MutableStateFlow(false)
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val speed = MutableStateFlow(1.00f)
    val shuffle = MutableStateFlow(false)
    val repeat = MutableStateFlow(RepeatMode.OFF)
    val sleepAt = MutableStateFlow<Long?>(null)

    fun init(ctx: Context) {
        if (::player.isInitialized) return
        prefs = ctx.applicationContext.getSharedPreferences("tempo_speeds", Context.MODE_PRIVATE)
        player = ExoPlayer.Builder(ctx.applicationContext)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(p: Boolean) { isPlaying.value = p }
            override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_ENDED) next(auto = true) }
            override fun onPlayerError(e: PlaybackException) { error.value = e.pretty() }
        })
    }

    private fun rebuildOrder(current: Int) {
        val all = queue.value.indices.toList()
        order = if (shuffle.value && current in all) listOf(current) + all.filter { it != current }.shuffled() else all
    }

    fun playQueue(tracks: List<Track>, start: Int, shuffleOn: Boolean? = null) {
        if (tracks.isEmpty()) return
        queue.value = tracks
        if (shuffleOn != null) shuffle.value = shuffleOn
        rebuildOrder(start)
        playIndex(start)
    }

    fun toggleShuffle() { shuffle.value = !shuffle.value; rebuildOrder(index.value) }
    fun cycleRepeat() {
        repeat.value = when (repeat.value) { RepeatMode.OFF -> RepeatMode.ALL; RepeatMode.ALL -> RepeatMode.ONE; RepeatMode.ONE -> RepeatMode.OFF }
    }

    fun playIndex(i: Int) {
        val q = queue.value
        if (i !in q.indices) return
        index.value = i
        applySpeed(savedSpeed(q[i]))          // each song remembers its own tempo
        job?.cancel()
        job = scope.launch {
            loading.value = true; error.value = null
            val t = q[i]
            try {
                val s = SoundCloud.stream(t.url)
                val item = MediaItem.Builder()
                    .setUri(s.url)
                    .apply { if (s.hls) setMimeType(MimeTypes.APPLICATION_M3U8) }
                    .setMediaMetadata(
                        MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist)
                            .setArtworkUri(t.artworkUrl?.let { Uri.parse(it) }).build()
                    ).build()
                player.setMediaItem(item)
                player.prepare()
                player.play()
            } catch (e: CancellationException) { throw e
            } catch (e: Throwable) { error.value = e.pretty()
            } finally { loading.value = false }
        }
    }

    fun next(auto: Boolean = false) {
        if (queue.value.isEmpty()) return
        if (auto && repeat.value == RepeatMode.ONE) { playIndex(index.value); return }
        val np = order.indexOf(index.value) + 1
        if (np >= order.size) {
            if (auto && repeat.value == RepeatMode.OFF) { player.seekTo(0); player.pause(); return }
            playIndex(order.firstOrNull() ?: 0)
        } else playIndex(order[np])
    }

    fun prev() {
        if (player.currentPosition > 3000 || order.isEmpty()) { player.seekTo(0); return }
        val pos = order.indexOf(index.value)
        playIndex(order[(pos - 1 + order.size) % order.size])
    }

    fun togglePlay() { if (player.isPlaying) player.pause() else player.play() }

    private fun savedSpeed(t: Track): Float = prefs?.getFloat(t.url, 1.00f) ?: 1.00f

    private fun applySpeed(v: Float) {
        val s = ((v * 100).roundToInt() / 100f).coerceIn(MIN_SPEED, MAX_SPEED)
        speed.value = s
        player.playbackParameters = PlaybackParameters(s, s)
    }

    /** Speed AND pitch are set to the same value (slower = deeper, faster = higher) and saved for this song. */
    fun setSpeed(v: Float) {
        applySpeed(v)
        val t = queue.value.getOrNull(index.value) ?: return
        prefs?.edit()?.apply { if (speed.value == 1.00f) remove(t.url) else putFloat(t.url, speed.value) }?.apply()
    }

    fun setSleep(minutes: Int?) {
        sleepJob?.cancel()
        if (minutes == null) { sleepAt.value = null; return }
        sleepAt.value = System.currentTimeMillis() + minutes * 60_000L
        sleepJob = scope.launch { delay(minutes * 60_000L); player.pause(); sleepAt.value = null }
    }
}
