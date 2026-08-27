/*
 *     Copyright (C) 2024 Akane Foundation
 *
 *     Folio is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Folio is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.raghu.folio.logic

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.preference.PreferenceManager
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.raghu.folio.R
import com.raghu.folio.logic.data.db.AppDatabase
import com.raghu.folio.logic.utils.audiobook.AudiobookPlayerController
import com.raghu.folio.logic.utils.exoplayer.FolioMediaSourceFactory
import com.raghu.folio.logic.utils.exoplayer.FolioRenderFactory
import com.raghu.folio.ui.MainActivity
import com.raghu.folio.widget.AudiobookWidgetProvider
import com.raghu.folio.widget.WidgetStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Audiobook-specific playback service, built fresh around [AudiobookPlayerController] rather than
 * adapting the old music-oriented [FolioPlaybackService]. Both services are registered in
 * AndroidManifest.xml for now - this one is inert until UI binds to it (no UI does yet).
 * [FolioPlaybackService] and the music UI it serves will be deleted together once the new UI
 * (see docs/PIVOT_NOTES.md) replaces them; keeping both compiling in the meantime avoids a
 * big-bang rewrite that breaks the whole app at once.
 */
@OptIn(UnstableApi::class)
class AudiobookPlaybackService : MediaSessionService() {

    companion object {
        const val COMMAND_LOAD_BOOK = "audiobook_load_book"
        const val COMMAND_SKIP_FORWARD = "audiobook_skip_forward"
        const val COMMAND_SKIP_BACKWARD = "audiobook_skip_backward"
        const val COMMAND_SET_SPEED = "audiobook_set_speed"
        const val COMMAND_SEEK_CHAPTER = "audiobook_seek_chapter"
        const val COMMAND_SLEEP_TIMER_START = "audiobook_sleep_timer_start"
        const val COMMAND_SLEEP_TIMER_CANCEL = "audiobook_sleep_timer_cancel"
        const val COMMAND_SET_SKIP_SILENCE = "audiobook_set_skip_silence"
        const val COMMAND_SEEK_ABSOLUTE = "audiobook_seek_absolute"

        const val EXTRA_BOOK_ID = "book_id"
        const val EXTRA_SECONDS = "seconds"
        const val EXTRA_SPEED = "speed"
        const val EXTRA_CHAPTER_ID = "chapter_id"
        const val EXTRA_MINUTES = "minutes"
        const val EXTRA_ENABLED = "enabled"
        const val EXTRA_POSITION_MS = "position_ms"
    }

    private lateinit var player: ExoPlayer
    private lateinit var controller: AudiobookPlayerController
    private var mediaSession: MediaSession? = null

    private val widgetProgressHandler = Handler(Looper.getMainLooper())
    private val widgetProgressRunnable = object : Runnable {
        override fun run() {
            if (::player.isInitialized && player.isPlaying) {
                WidgetStateStore.writePlaybackState(this@AudiobookPlaybackService, true, player.currentPosition)
                AudiobookWidgetProvider.updateAllWidgets(this@AudiobookPlaybackService)
            }
            widgetProgressHandler.postDelayed(this, 20_000)
        }
    }

    override fun onCreate() {
        super.onCreate()

        player = ExoPlayer.Builder(
            this,
            FolioRenderFactory(this)
                .setEnableDecoderFallback(true)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER),
            FolioMediaSourceFactory(this),
        )
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    // Speech (not music) audio focus behavior suits narrated audiobooks better.
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                true,
            )
            .build()
        controller = AudiobookPlayerController(this, player)

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                WidgetStateStore.writePlaybackState(this@AudiobookPlaybackService, isPlaying, player.currentPosition)
                AudiobookWidgetProvider.updateAllWidgets(this@AudiobookPlaybackService)
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                WidgetStateStore.writeMetadata(
                    this@AudiobookPlaybackService,
                    mediaMetadata.title?.toString(),
                    mediaMetadata.artist?.toString(),
                )
                AudiobookWidgetProvider.updateAllWidgets(this@AudiobookPlaybackService)
            }
        })
        widgetProgressHandler.postDelayed(widgetProgressRunnable, 20_000)

        val sessionActivity = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .setCallback(SessionCallback())
            .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply {
                setSmallIcon(R.drawable.ic_notification_vinyl)
            }
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /** Loads [bookId] and starts playback from its saved progress (or the start if none). */
    fun loadBook(bookId: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getInstance(this@AudiobookPlaybackService)
            val bookWithParts = db.bookDao().getBookWithParts(bookId) ?: return@launch
            val chapters = db.chapterDao().getChaptersForBook(bookId)
            val progress = db.playbackProgressDao().getProgress(bookId)
            val authorName = db.authorDao().getAuthorById(bookWithParts.book.authorId)?.name
            withContext(Dispatchers.Main) {
                controller.load(
                    book = bookWithParts.book,
                    authorName = authorName,
                    parts = bookWithParts.parts.sortedBy { it.partIndex },
                    chapters = chapters,
                    startPositionMs = progress?.positionMs ?: 0L,
                    speed = progress?.playbackSpeed ?: 1f,
                )
                controller.setSkipSilenceEnabled(
                    PreferenceManager.getDefaultSharedPreferences(this@AudiobookPlaybackService)
                        .getBoolean("skip_silence", false)
                )
                controller.play()
                WidgetStateStore.writeBookInfo(
                    this@AudiobookPlaybackService,
                    bookWithParts.book.bookId,
                    bookWithParts.book.coverUri,
                    bookWithParts.book.durationMs,
                )
                AudiobookWidgetProvider.updateAllWidgets(this@AudiobookPlaybackService)
            }
        }
    }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val availableCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon()
                .add(SessionCommand(COMMAND_LOAD_BOOK, Bundle.EMPTY))
                .add(SessionCommand(COMMAND_SKIP_FORWARD, Bundle.EMPTY))
                .add(SessionCommand(COMMAND_SKIP_BACKWARD, Bundle.EMPTY))
                .add(SessionCommand(COMMAND_SET_SPEED, Bundle.EMPTY))
                .add(SessionCommand(COMMAND_SEEK_CHAPTER, Bundle.EMPTY))
                .add(SessionCommand(COMMAND_SLEEP_TIMER_START, Bundle.EMPTY))
                .add(SessionCommand(COMMAND_SLEEP_TIMER_CANCEL, Bundle.EMPTY))
                .add(SessionCommand(COMMAND_SET_SKIP_SILENCE, Bundle.EMPTY))
                .add(SessionCommand(COMMAND_SEEK_ABSOLUTE, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(availableCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                COMMAND_LOAD_BOOK -> loadBook(args.getLong(EXTRA_BOOK_ID, -1L))
                COMMAND_SKIP_FORWARD -> this@AudiobookPlaybackService.controller
                    .skipForward(args.getInt(EXTRA_SECONDS, 30))
                COMMAND_SKIP_BACKWARD -> this@AudiobookPlaybackService.controller
                    .skipBackward(args.getInt(EXTRA_SECONDS, 15))
                COMMAND_SET_SPEED -> this@AudiobookPlaybackService.controller
                    .setPlaybackSpeed(args.getFloat(EXTRA_SPEED, 1f))
                COMMAND_SEEK_CHAPTER -> {
                    val chapterId = args.getLong(EXTRA_CHAPTER_ID, -1L)
                    this@AudiobookPlaybackService.controller.currentChapters()
                        .firstOrNull { it.chapterId == chapterId }
                        ?.let { this@AudiobookPlaybackService.controller.seekToChapter(it) }
                }
                COMMAND_SLEEP_TIMER_START -> this@AudiobookPlaybackService.controller
                    .startSleepTimer(args.getInt(EXTRA_MINUTES, 30))
                COMMAND_SLEEP_TIMER_CANCEL -> this@AudiobookPlaybackService.controller.cancelSleepTimer()
                COMMAND_SET_SKIP_SILENCE -> this@AudiobookPlaybackService.controller
                    .setSkipSilenceEnabled(args.getBoolean(EXTRA_ENABLED, false))
                COMMAND_SEEK_ABSOLUTE -> this@AudiobookPlaybackService.controller
                    .seekToAbsoluteMs(args.getLong(EXTRA_POSITION_MS, 0L))
                else -> return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    override fun onDestroy() {
        widgetProgressHandler.removeCallbacks(widgetProgressRunnable)
        controller.release()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
