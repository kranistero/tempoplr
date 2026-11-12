package app.tempo.player

data class Track(
    val id: String,
    val url: String,          // canonical SoundCloud URL (stream URL is resolved at play time, it expires)
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val durationMs: Long
)

data class Playlist(val id: String, val name: String, val trackIds: List<String>)

/** Every imported song lives in [tracks]; playlists only reference them by id. */
data class LibraryData(val tracks: List<Track>, val playlists: List<Playlist>)
