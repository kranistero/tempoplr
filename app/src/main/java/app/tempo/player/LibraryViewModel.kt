package app.tempo.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repo(app)
    val data = MutableStateFlow(repo.load())
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val error = MutableStateFlow<String?>(null)

    private fun update(f: (LibraryData) -> LibraryData) {
        data.value = f(data.value); repo.save(data.value)
    }

    fun createPlaylist(name: String, ids: List<String> = emptyList()): String {
        val id = UUID.randomUUID().toString()
        update { it.copy(playlists = it.playlists + Playlist(id, name.trim(), ids)) }
        return id
    }

    fun deletePlaylist(id: String) = update { d -> d.copy(playlists = d.playlists.filter { it.id != id }) }

    fun addToPlaylist(pid: String, ids: List<String>) = update { d ->
        d.copy(playlists = d.playlists.map { if (it.id == pid) it.copy(trackIds = (it.trackIds + ids).distinct()) else it })
    }

    fun removeFromPlaylist(pid: String, tid: String) = update { d ->
        d.copy(playlists = d.playlists.map { if (it.id == pid) it.copy(trackIds = it.trackIds.filter { x -> x != tid }) else it })
    }

    fun deleteTrack(tid: String) = update { d ->
        LibraryData(d.tracks.filter { it.id != tid }, d.playlists.map { it.copy(trackIds = it.trackIds.filter { x -> x != tid }) })
    }

    /** One or several links (space / newline separated). Optionally also drops them into a playlist. */
    fun importLinks(raw: String, playlistId: String?) {
        val links = raw.split(Regex("\\s+")).map { it.trim() }
            .filter { it.contains("soundcloud.com") || it.contains("snd.sc") || it.contains("soundcloud.app.goo.gl") }
            .distinct()
        if (links.isEmpty()) {
            error.value = "No SoundCloud link found in what you pasted.\n\nPaste links like:\nhttps://soundcloud.com/artist/track\nhttps://on.soundcloud.com/xxxxxxxx"
            return
        }
        viewModelScope.launch {
            busy.value = true
            var ok = 0
            val fails = mutableListOf<String>()
            for (l in links) {
                try {
                    val t = SoundCloud.fetchTrack(l)
                    val existing = data.value.tracks.firstOrNull { it.url == t.url }
                    val track = existing ?: t
                    update { d ->
                        d.copy(
                            tracks = if (existing == null) d.tracks + track else d.tracks,
                            playlists = if (playlistId == null) d.playlists else d.playlists.map {
                                if (it.id == playlistId) it.copy(trackIds = (it.trackIds + track.id).distinct()) else it
                            }
                        )
                    }
                    ok++
                } catch (e: CancellationException) { throw e
                } catch (e: Throwable) { fails += "$l\n→ ${e.pretty()}" }
            }
            busy.value = false
            if (ok > 0) message.value = "Added $ok song${if (ok == 1) "" else "s"}"
            if (fails.isNotEmpty()) error.value = "Couldn't import ${fails.size} link${if (fails.size == 1) "" else "s"}:\n\n" + fails.joinToString("\n\n")
        }
    }
}
