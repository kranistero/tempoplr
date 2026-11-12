package app.tempo.player

import android.Manifest
import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

class MainActivity : ComponentActivity() {
    private var controller: ListenableFuture<MediaController>? = null
    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        // Connecting a controller starts + binds PlaybackService (background playback & notification)
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controller = MediaController.Builder(this, token).buildAsync()
        setContent { TempoTheme { App() } }
    }

    override fun onDestroy() {
        controller?.let { MediaController.releaseFuture(it) }
        super.onDestroy()
    }
}
