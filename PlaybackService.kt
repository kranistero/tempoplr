package app.tempo.player

import android.app.Application
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class TempoApp : Application() {
    override fun onCreate() { super.onCreate(); PlaybackManager.init(this) }
}

/** Keeps playback alive in the background and provides the notification / lock-screen controls. */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        PlaybackManager.init(this)
        val wrapped = object : ForwardingPlayer(PlaybackManager.player) {
            override fun getAvailableCommands(): Player.Commands =
                super.getAvailableCommands().buildUpon()
                    .add(COMMAND_SEEK_TO_NEXT).add(COMMAND_SEEK_TO_PREVIOUS).build()
            override fun isCommandAvailable(command: Int) =
                command == COMMAND_SEEK_TO_NEXT || command == COMMAND_SEEK_TO_PREVIOUS ||
                    super.isCommandAvailable(command)
            override fun seekToNext() = PlaybackManager.next()
            override fun seekToPrevious() = PlaybackManager.prev()
            override fun seekToNextMediaItem() = PlaybackManager.next()
            override fun seekToPreviousMediaItem() = PlaybackManager.prev()
        }
        session = MediaSession.Builder(this, wrapped).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onDestroy() { session?.release(); session = null; super.onDestroy() }
}
