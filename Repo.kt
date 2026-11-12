package app.tempo.player

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class Repo(ctx: Context) {
    private val file = File(ctx.filesDir, "library.json")

    fun load(): LibraryData {
        if (!file.exists()) return LibraryData(emptyList(), emptyList())
        return try {
            val root = JSONObject(file.readText())
            val ts = root.getJSONArray("tracks")
            val ps = root.getJSONArray("playlists")
            LibraryData(
                (0 until ts.length()).map { i ->
                    val x = ts.getJSONObject(i)
                    Track(
                        x.getString("id"), x.getString("url"), x.getString("title"),
                        x.optString("artist", ""), x.optString("art", "").ifEmpty { null },
                        x.optLong("dur", 0L)
                    )
                },
                (0 until ps.length()).map { i ->
                    val o = ps.getJSONObject(i)
                    val ids = o.getJSONArray("ids")
                    Playlist(o.getString("id"), o.getString("name"), (0 until ids.length()).map { ids.getString(it) })
                }
            )
        } catch (e: Exception) { LibraryData(emptyList(), emptyList()) }
    }

    fun save(d: LibraryData) {
        val ts = JSONArray()
        d.tracks.forEach { t ->
            ts.put(JSONObject().put("id", t.id).put("url", t.url).put("title", t.title)
                .put("artist", t.artist).put("art", t.artworkUrl ?: "").put("dur", t.durationMs))
        }
        val ps = JSONArray()
        d.playlists.forEach { p ->
            ps.put(JSONObject().put("id", p.id).put("name", p.name).put("ids", JSONArray(p.trackIds)))
        }
        file.writeText(JSONObject().put("tracks", ts).put("playlists", ps).toString())
    }
}
