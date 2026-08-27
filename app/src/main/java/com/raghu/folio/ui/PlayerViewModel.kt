package com.raghu.folio.ui

import android.app.Application
import android.content.ComponentName
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.raghu.folio.logic.AudiobookPlaybackService
import com.raghu.folio.logic.AudiobookPlaybackService.Companion.COMMAND_LOAD_BOOK
import com.raghu.folio.logic.AudiobookPlaybackService.Companion.COMMAND_SEEK_ABSOLUTE
import com.raghu.folio.logic.AudiobookPlaybackService.Companion.COMMAND_SET_SKIP_SILENCE
import com.raghu.folio.logic.AudiobookPlaybackService.Companion.COMMAND_SET_SPEED
import com.raghu.folio.logic.AudiobookPlaybackService.Companion.COMMAND_SKIP_BACKWARD
import com.raghu.folio.logic.AudiobookPlaybackService.Companion.COMMAND_SKIP_FORWARD
import com.raghu.folio.logic.AudiobookPlaybackService.Companion.COMMAND_SLEEP_TIMER_CANCEL
import com.raghu.folio.logic.AudiobookPlaybackService.Companion.COMMAND_SLEEP_TIMER_START
import com.raghu.folio.logic.AudiobookPlaybackService.Companion.EXTRA_BOOK_ID
import com.raghu.folio.logic.AudiobookPlaybackService.Companion.EXTRA_ENABLED
import com.raghu.folio.logic.AudiobookPlaybackService.Companion.EXTRA_MINUTES
import com.raghu.folio.logic.AudiobookPlaybackService.Companion.EXTRA_POSITION_MS
import com.raghu.folio.logic.AudiobookPlaybackService.Companion.EXTRA_SECONDS
import com.raghu.folio.logic.AudiobookPlaybackService.Companion.EXTRA_SPEED

/**
 * Activity-scoped connection to [AudiobookPlaybackService], shared by the mini-player bar and the
 * full player bottom sheet so both reflect the same playback state without each managing their
 * own [MediaController].
 */
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private var controller: MediaController? = null

    var currentBookId: Long? = null
        private set

    val isConnected = MutableLiveData(false)
    val isPlaying = MutableLiveData(false)
    val title = MutableLiveData<String?>(null)
    val author = MutableLiveData<String?>(null)
    val positionMs = MutableLiveData(0L)
    val durationMs = MutableLiveData(0L)

    private val positionHandler = Handler(Looper.getMainLooper())
    private val positionRunnable = object : Runnable {
        override fun run() {
            controller?.let {
                positionMs.value = it.currentPosition.coerceAtLeast(0)
                durationMs.value = it.duration.coerceAtLeast(0)
            }
            positionHandler.postDelayed(this, 500)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying.value = playing
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            title.value = mediaMetadata.title?.toString()
            author.value = mediaMetadata.artist?.toString()
        }
    }

    fun connect() {
        if (controller != null) return
        val context = getApplication<Application>()
        val token = SessionToken(context, ComponentName(context, AudiobookPlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            val c = future.get()
            controller = c
            c.addListener(playerListener)
            isPlaying.value = c.isPlaying
            title.value = c.mediaMetadata.title?.toString()
            author.value = c.mediaMetadata.artist?.toString()
            isConnected.value = true
            positionHandler.post(positionRunnable)
        }, MoreExecutors.directExecutor())
    }

    fun loadBook(bookId: Long) {
        currentBookId = bookId
        sendCommand(COMMAND_LOAD_BOOK, Bundle().apply { putLong(EXTRA_BOOK_ID, bookId) })
    }

    fun playPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun skipForward(seconds: Int = 30) =
        sendCommand(COMMAND_SKIP_FORWARD, Bundle().apply { putInt(EXTRA_SECONDS, seconds) })

    fun skipBackward(seconds: Int = 15) =
        sendCommand(COMMAND_SKIP_BACKWARD, Bundle().apply { putInt(EXTRA_SECONDS, seconds) })

    fun seekAbsolute(positionMs: Long) =
        sendCommand(COMMAND_SEEK_ABSOLUTE, Bundle().apply { putLong(EXTRA_POSITION_MS, positionMs) })

    fun setSpeed(speed: Float) =
        sendCommand(COMMAND_SET_SPEED, Bundle().apply { putFloat(EXTRA_SPEED, speed) })

    fun setSkipSilenceEnabled(enabled: Boolean) =
        sendCommand(COMMAND_SET_SKIP_SILENCE, Bundle().apply { putBoolean(EXTRA_ENABLED, enabled) })

    fun startSleepTimer(minutes: Int) =
        sendCommand(COMMAND_SLEEP_TIMER_START, Bundle().apply { putInt(EXTRA_MINUTES, minutes) })

    fun cancelSleepTimer() = sendCommand(COMMAND_SLEEP_TIMER_CANCEL, Bundle.EMPTY)

    private fun sendCommand(action: String, args: Bundle) {
        controller?.sendCustomCommand(SessionCommand(action, Bundle.EMPTY), args)
    }

    override fun onCleared() {
        positionHandler.removeCallbacks(positionRunnable)
        controller?.removeListener(playerListener)
        controller?.release()
        controller = null
        super.onCleared()
    }
}
