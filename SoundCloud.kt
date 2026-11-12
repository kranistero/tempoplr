package app.tempo.player

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.UUID
import java.util.concurrent.TimeUnit

private const val UA = "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0"

fun Throwable.pretty(): String {
    val sb = StringBuilder("${this::class.java.simpleName}: ${message ?: "(no message)"}")
    var c = cause
    var n = 0
    while (c != null && n < 3) {
        sb.append("\n   caused by ${c::class.java.simpleName}: ${c.message ?: "(no message)"}")
        c = c.cause; n++
    }
    return sb.toString()
}

class AppDownloader : Downloader() {
    private val client = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build()

    override fun execute(request: Request): Response {
        val body = request.dataToSend()?.toRequestBody()
        val b = okhttp3.Request.Builder()
            .method(request.httpMethod(), body)
            .url(request.url())
            .addHeader("User-Agent", UA)
        request.headers().forEach { (name, values) ->
            b.removeHeader(name); values.forEach { b.addHeader(name, it) }
        }
        val resp = client.newCall(b.build()).execute()
        if (resp.code == 429) { resp.close(); throw ReCaptchaException("Rate limited", request.url()) }
        val text = resp.body?.string()
        return Response(resp.code, resp.message, resp.headers.toMultimap(), text, resp.request.url.toString())
    }
}

data class Stream(val url: String, val hls: Boolean)

object SoundCloud {
    private var ready = false
    private fun init() { if (!ready) { NewPipe.init(AppDownloader()); ready = true } }

    private val http = OkHttpClient.Builder()
        .followRedirects(true).followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()

    /** Cleans a pasted link and expands short links (on.soundcloud.com/xxxx) to the full track URL. */
    private fun normalize(raw: String): String {
        var u = raw.trim().trim('<', '>', '"', '\'', ',', '(', ')')
        if (!u.startsWith("http", ignoreCase = true)) u = "https://$u"
        val host = Uri.parse(u).host?.lowercase() ?: ""
        if (host == "on.soundcloud.com" || host == "soundcloud.app.goo.gl" || host == "snd.sc") {
            http.newCall(okhttp3.Request.Builder().url(u).header("User-Agent", UA).build())
                .execute().use { u = it.request.url.toString() }
        }
        u = u.replace("://m.soundcloud.com", "://soundcloud.com")
        return u.substringBefore('?').substringBefore('#')
    }

    private fun bigArt(url: String?): String? =
        url?.replace(Regex("-(large|t\\d+x\\d+|crop|badge|small|tiny)\\."), "-t500x500.")

    /** Title, artist, cover and duration from a SoundCloud link. */
    suspend fun fetchTrack(link: String): Track = withContext(Dispatchers.IO) {
        init()
        val url = normalize(link)
        val info = StreamInfo.getInfo(ServiceList.SoundCloud, url)
        val art = info.thumbnails.maxByOrNull { it.height }?.url
        Track(UUID.randomUUID().toString(), info.url ?: url, info.name,
            info.uploaderName ?: "", bigArt(art), info.duration * 1000)
    }

    /** Fresh playable audio URL (resolved on every play because they expire). */
    suspend fun stream(link: String): Stream = withContext(Dispatchers.IO) {
        init()
        val info = StreamInfo.getInfo(ServiceList.SoundCloud, link)
        val all = info.audioStreams
        val prog = all.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .maxByOrNull { it.averageBitrate }
        val chosen = prog ?: all.firstOrNull() ?: error("SoundCloud returned no audio stream for this track")
        Stream(chosen.content, chosen.deliveryMethod == DeliveryMethod.HLS)
    }
}
