/*
 *     Copyright (C) 2024 Akane Foundation
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Gramophone is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.akanework.gramophone.logic

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.IllegalSeekPositionException
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.common.util.Util.isBitmapFactorySupportedMimeType
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import androidx.preference.PreferenceManager
import coil3.BitmapImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import org.akanework.gramophone.BuildConfig
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.utils.CircularShuffleOrder
import org.akanework.gramophone.logic.utils.LastPlayedManager
import org.akanework.gramophone.logic.utils.LrcUtils.extractAndParseLyrics
import org.akanework.gramophone.logic.utils.LrcUtils.loadAndParseLyricsFile
import org.akanework.gramophone.logic.utils.MediaStoreUtils
import org.akanework.gramophone.logic.utils.exoplayer.EndedWorkaroundPlayer
import org.akanework.gramophone.logic.utils.exoplayer.GramophoneMediaSourceFactory
import org.akanework.gramophone.logic.utils.exoplayer.GramophoneRenderFactory
import org.akanework.gramophone.ui.MainActivity
import kotlin.random.Random


/**
 * [GramophonePlaybackService] is a server service.
 * It's using exoplayer2 as its player backend.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class GramophonePlaybackService : MediaLibraryService(), MediaSessionService.Listener,
    MediaLibraryService.MediaLibrarySession.Callback, Player.Listener {

    companion object {
        private const val TAG = "GramoPlaybackService"
        private const val NOTIFY_CHANNEL_ID = "serviceFgsError"
        private const val NOTIFY_ID = 1
        private const val PENDING_INTENT_SESSION_ID = 0
        private const val PENDING_INTENT_NOTIFY_ID = 1
        private const val PLAYBACK_SHUFFLE_ACTION_ON = "shuffle_on"
        private const val PLAYBACK_SHUFFLE_ACTION_OFF = "shuffle_off"
        private const val PLAYBACK_REPEAT_OFF = "repeat_off"
        private const val PLAYBACK_REPEAT_ALL = "repeat_all"
        private const val PLAYBACK_REPEAT_ONE = "repeat_one"
        const val SERVICE_SET_TIMER = "set_timer"
        const val SERVICE_QUERY_TIMER = "query_timer"
        const val SERVICE_GET_LYRICS = "get_lyrics"
        const val SERVICE_GET_SESSION = "get_session"
        const val SERVICE_TIMER_CHANGED = "changed_timer"
        // Sentinel timerDuration value for "pause when the current song ends". Computing a fixed
        // millisecond delay meant to land exactly at the song's natural end and racing it against
        // ExoPlayer's own auto-advance-to-next-track handling is unreliable - whichever wins the
        // race, the loser's effect is masked (usually the natural transition wins and playback
        // just continues into the next song). Instead this is handled as its own event-driven
        // state: armed here, and resolved reactively in onMediaItemTransition() below.
        const val TIMER_PAUSE_ON_SONG_END = -1

        // How often actively-playing time gets flushed to the listening-stats table. Batching
        // like this (instead of a DB write per second) keeps tracking effectively free - one
        // small upsert a minute at most, only while something is actually playing.
        private const val STATS_FLUSH_INTERVAL_MS = 60_000L

        var instanceForWidgetAndLyricsOnly: GramophonePlaybackService? = null
    }

    private var lastSessionId = 0
    private var mediaSession: MediaLibrarySession? = null
    val endedWorkaroundPlayer
        get() = mediaSession?.player as EndedWorkaroundPlayer?
    private var controller: MediaController? = null
    private var lyrics: MutableList<MediaStoreUtils.Lyric>? = null
    private var shuffleFactory:
            ((Int) -> ((CircularShuffleOrder) -> Unit) -> CircularShuffleOrder)? = null
    private lateinit var customCommands: List<CommandButton>
    private lateinit var handler: Handler
    private lateinit var nm: NotificationManagerCompat
    private lateinit var lastPlayedManager: LastPlayedManager
    private val lyricsLock = Semaphore(1)
    private lateinit var prefs: SharedPreferences

    private fun getRepeatCommand() =
        when (controller!!.repeatMode) {
            Player.REPEAT_MODE_OFF -> customCommands[2]
            Player.REPEAT_MODE_ALL -> customCommands[3]
            Player.REPEAT_MODE_ONE -> customCommands[4]
            else -> throw IllegalArgumentException()
        }

    private fun getShufflingCommand() =
        if (controller!!.shuffleModeEnabled)
            customCommands[1]
        else
            customCommands[0]

    private val timer: Runnable = Runnable {
        controller!!.pause()
        timerDuration = 0
    }

    // Set only while timerDuration == TIMER_PAUSE_ON_SONG_END - checked/consumed reactively in
    // onMediaItemTransition() instead of via a scheduled delay (see TIMER_PAUSE_ON_SONG_END).
    private var pauseOnSongEndArmed = false

    // elapsedRealtime() (monotonic, unaffected by wall-clock/timezone changes) at the moment a
    // positive-duration timer was armed - lets a query compute remaining time on demand
    // (timerSetAtElapsedRealtime + timerDuration - elapsedRealtime()) without any periodic
    // ticking/polling to keep it updated.
    private var timerSetAtElapsedRealtime = 0L

    private var timerDuration = 0
        set(value) {
            field = value
            // Only one timer may ever be pending - always clear whatever was scheduled/armed
            // before (if anything) rather than only when explicitly turning the timer off,
            // otherwise setting a new duration while one is already running leaves the old
            // callback armed too and it fires later as an unwanted extra pause.
            handler.removeCallbacks(timer)
            pauseOnSongEndArmed = false
            when {
                value == TIMER_PAUSE_ON_SONG_END -> pauseOnSongEndArmed = true
                value > 0 -> {
                    timerSetAtElapsedRealtime = android.os.SystemClock.elapsedRealtime()
                    handler.postDelayed(timer, value.toLong())
                }
            }
            mediaSession!!.broadcastCustomCommand(
                SessionCommand(SERVICE_TIMER_CHANGED, Bundle.EMPTY),
                Bundle.EMPTY
            )
        }

    // Listening-time tracking for the stats screen: while a song is playing, the elapsed wall
    // time since statsAccumStartElapsed is credited to statsAccumMediaId, flushed to the database
    // in STATS_FLUSH_INTERVAL_MS batches (or immediately on pause/song change) rather than on
    // every position update - no polling, just one Runnable rescheduled while actually playing.
    private var statsAccumMediaId: Long? = null
    private var statsAccumStartElapsed = 0L
    private val statsFlushRunnable = Runnable { flushListeningStats(reschedule = true) }

    private fun flushListeningStats(reschedule: Boolean) {
        val mediaId = statsAccumMediaId
        val now = android.os.SystemClock.elapsedRealtime()
        if (mediaId != null) {
            val elapsedMs = now - statsAccumStartElapsed
            if (elapsedMs > 0) {
                val dayEpoch = java.time.LocalDate.now().toEpochDay()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        org.akanework.gramophone.logic.data.db.AppDatabase.getInstance(applicationContext)
                            .listeningStatDao().addListeningTime(mediaId, dayEpoch, elapsedMs)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to record listening stat", e)
                    }
                }
            }
        }
        statsAccumStartElapsed = now
        if (reschedule && mediaId != null) {
            handler.postDelayed(statsFlushRunnable, STATS_FLUSH_INTERVAL_MS)
        }
    }

    // Occasional (at most once a day) snapshot of the whole listening-stats table into the backup
    // folder, so the data survives an uninstall/reinstall or "clear data" - the app's own database
    // file doesn't. Piggybacks on service teardown (which happens naturally through normal use -
    // closing the app, etc.) rather than a dedicated scheduler, gated by a stored date so it isn't
    // writing a backup every single time.
    //
    // Silently does nothing until the user has picked a backup folder in the stats screen: writing
    // anywhere in shared storage needs that grant, and a background service is the wrong place to
    // start asking for one.
    private fun maybeBackupListeningStats() {
        val today = java.time.LocalDate.now().toEpochDay()
        if (prefs.getLong("listening_stats_last_backup_day", -1L) == today) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Resolved here rather than at the call site: looking the folder up asks the
                // system for our persisted permissions, and that binder round-trip was happening
                // on the main thread during onDestroy(). Service callbacks share that thread with
                // startForeground(), so stalling it delays the media notification and can trip the
                // "did not then call startForeground()" ANR.
                val folder = org.akanework.gramophone.logic.utils.ListeningStatsBackupUtils
                    .savedFolder(applicationContext) ?: return@launch
                // Never auto-write an empty snapshot. After a reset the table is empty, and an
                // automatic backup would quietly become the newest file - so the next "Load From
                // Backup" would restore nothing instead of the history the user still has.
                if (org.akanework.gramophone.logic.utils.ListeningStatsBackupUtils
                        .hasNoStats(applicationContext)
                ) return@launch
                if (org.akanework.gramophone.logic.utils.ListeningStatsBackupUtils
                        .backupTo(applicationContext, folder)
                ) {
                    prefs.edit().putLong("listening_stats_last_backup_day", today).apply()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to auto-backup listening stats", e)
            }
        }
    }

    private fun switchStatsTracking(newMediaId: Long?) {
        flushListeningStats(reschedule = false)
        handler.removeCallbacks(statsFlushRunnable)
        statsAccumMediaId = newMediaId
        statsAccumStartElapsed = android.os.SystemClock.elapsedRealtime()
        if (newMediaId != null) {
            handler.postDelayed(statsFlushRunnable, STATS_FLUSH_INTERVAL_MS)
        }
    }

    private val headSetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action.equals(AudioManager.ACTION_AUDIO_BECOMING_NOISY)) {
                controller?.pause()
            }
        }
    }

    override fun onCreate() {
        instanceForWidgetAndLyricsOnly = this
        handler = Handler(Looper.getMainLooper())
        super.onCreate()
        nm = NotificationManagerCompat.from(this)
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        setListener(this)
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply {
                setSmallIcon(R.drawable.ic_notification_vinyl)
            }
        )
        if (mayThrowForegroundServiceStartNotAllowed()) {
            // we don't need notification permission because this only is run on S/S_V2
            nm.createNotificationChannel(NotificationChannelCompat.Builder(
                NOTIFY_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH
            ).apply {
                setName(getString(R.string.fgs_failed_channel))
                setVibrationEnabled(true)
                setVibrationPattern(longArrayOf(0L, 200L))
                setLightsEnabled(false)
                setShowBadge(false)
                setSound(null, null)
            }.build()
            )
        } else if (nm.getNotificationChannel(NOTIFY_CHANNEL_ID) != null) {
            // for people who upgraded from S/S_V2 to newer version
            nm.deleteNotificationChannel(NOTIFY_CHANNEL_ID)
        }

        customCommands =
            listOf(
                CommandButton.Builder(CommandButton.ICON_SHUFFLE_OFF) // shuffle currently disabled, click will enable
                    .setDisplayName(getString(R.string.shuffle))
                    .setSessionCommand(
                        SessionCommand(PLAYBACK_SHUFFLE_ACTION_ON, Bundle.EMPTY)
                    )
                    .setCustomIconResId(R.drawable.ic_shuffle_off)
                    .build(),
                CommandButton.Builder(CommandButton.ICON_SHUFFLE_ON) // shuffle currently enabled, click will disable
                    .setDisplayName(getString(R.string.shuffle))
                    .setSessionCommand(
                        SessionCommand(PLAYBACK_SHUFFLE_ACTION_OFF, Bundle.EMPTY)
                    )
                    .setCustomIconResId(R.drawable.ic_shuffle)
                    .build(),
                CommandButton.Builder(CommandButton.ICON_REPEAT_OFF) // repeat currently disabled, click will repeat all
                    .setDisplayName(getString(R.string.repeat_mode))
                    .setSessionCommand(
                        SessionCommand(PLAYBACK_REPEAT_ALL, Bundle.EMPTY)
                    )
                    .setCustomIconResId(R.drawable.ic_repeat_off)
                    .build(),
                CommandButton.Builder(CommandButton.ICON_REPEAT_ALL) // repeat all currently enabled, click will repeat one
                    .setDisplayName(getString(R.string.repeat_mode))
                    .setSessionCommand(
                        SessionCommand(PLAYBACK_REPEAT_ONE, Bundle.EMPTY)
                    )
                    .setCustomIconResId(R.drawable.ic_repeat)
                    .build(),
                CommandButton.Builder(CommandButton.ICON_REPEAT_ONE) // repeat one currently enabled, click will disable
                    .setDisplayName(getString(R.string.repeat_mode))
                    .setSessionCommand(
                        SessionCommand(PLAYBACK_REPEAT_OFF, Bundle.EMPTY)
                    )
                    .setCustomIconResId(R.drawable.ic_repeat_one)
                    .build(),
            )

        val player = EndedWorkaroundPlayer(
            ExoPlayer.Builder(
                this,
                GramophoneRenderFactory(this)
                    .setEnableAudioFloatOutput(
                        prefs.getBooleanStrict("floatoutput", false)
                    )
                    .setEnableDecoderFallback(true)
                    .setEnableAudioTrackPlaybackParams( // hardware/system-accelerated playback speed
                        prefs.getBooleanStrict("ps_hardware_acc", true)
                    )
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER),
                GramophoneMediaSourceFactory(this)
                /* .setMp3ExtractorFlags(Mp3Extractor.FLAG_ENABLE_INDEX_SEEKING))
            TODO flag breaks playback of AcousticGuitar.mp3, report exo bug + add UI toggle*/
            )
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .setSkipSilenceEnabled(prefs.getBooleanStrict("skip_silence", false))
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(), true
                )
                .build()
        )
        if (BuildConfig.DEBUG) {
            player.exoPlayer.addAnalyticsListener(EventLogger())
        }
        player.exoPlayer.audioSessionId = Util.generateAudioSessionIdV21(this)
        lastSessionId = player.exoPlayer.audioSessionId
        broadcastAudioSession()
        lastPlayedManager = LastPlayedManager(this, player)
        lastPlayedManager.allowSavingState = false

        mediaSession =
            MediaLibrarySession
                .Builder(this, player, this)
                .setBitmapLoader(object : BitmapLoader {
                    // Coil-based bitmap loader to reuse Coil's caching and to make sure we use
                    // the same cover art as the rest of the app, ie MediaStore's cover

                    override fun decodeBitmap(data: ByteArray) =
                        throw UnsupportedOperationException("decodeBitmap() not supported")

                    override fun loadBitmap(
                        uri: Uri
                    ): ListenableFuture<Bitmap> {
                        return CallbackToFutureAdapter.getFuture { completer ->
                            imageLoader.enqueue(
                                ImageRequest.Builder(this@GramophonePlaybackService)
                                    .data(uri)
                                    .allowHardware(false)
                                    .target(
                                        onStart = { _ ->
                                            // We don't need or want a placeholder.
                                        },
                                        onSuccess = { result ->
                                            completer.set((result as BitmapImage).bitmap)
                                        },
                                        onError = { _ ->
                                            completer.setException(Exception("coil onError called"))
                                        }
                                    )
                                    .build())
                                .also {
                                    completer.addCancellationListener(
                                        { it.dispose() },
                                        ContextCompat.getMainExecutor(
                                            this@GramophonePlaybackService
                                        )
                                    )
                                }
                            "coil load for $uri"
                        }
                    }

                    override fun supportsMimeType(mimeType: String): Boolean {
                        return isBitmapFactorySupportedMimeType(mimeType)
                    }

                    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap>? {
                        return metadata.artworkUri?.let { loadBitmap(it) }
                    }
                })
                .setSessionActivity(
                    PendingIntent.getActivity(
                        this,
                        PENDING_INTENT_SESSION_ID,
                        Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                )
                .build()
        controller = MediaController.Builder(this, mediaSession!!.token).buildAsync().get()
        handler.post {
            if (mediaSession == null) return@post
            lastPlayedManager.restore { items, factory ->
                if (mediaSession == null) return@restore
                applyShuffleSeed(true, factory.toFactory(controller!!))
                if (items != null) {
                    try {
                        mediaSession?.player?.setMediaItems(
                            items.mediaItems, items.startIndex, items.startPositionMs
                        )
                    } catch (e: IllegalSeekPositionException) {
                        Log.e(TAG, "failed to restore: " + Log.getStackTraceString(e))
                        // song was edited to be shorter and playback position doesn't exist anymore
                    }
                    // Prepare Player after UI thread is less busy (loads tracks, required for lyric)
                    handler.post {
                        controller?.prepare()
                    }
                }
                lastPlayedManager.allowSavingState = true
            }
        }
        onShuffleModeEnabledChanged(controller!!.shuffleModeEnabled) // refresh custom commands
        controller!!.addListener(this)
        registerReceiver(
            headSetReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        )
    }

    // MediaSessionService deliberately does NOT stop itself when the app's task is swiped away
    // from Recents by default - that's what lets most music apps keep playing in the background
    // after being "closed" that way. This app wants the opposite: swiping it away should close it
    // entirely, playback included, not leave it running in the background.
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        mediaSession?.player?.pause()
        stopSelf()
    }

    // When destroying, we should release server side player
    // alongside with the mediaSession.
    override fun onDestroy() {
        instanceForWidgetAndLyricsOnly = null
        flushListeningStats(reschedule = false)
        handler.removeCallbacks(statsFlushRunnable)
        maybeBackupListeningStats()
        // Important: this must happen before sending stop() as that changes state ENDED -> IDLE
        lastPlayedManager.save()
        mediaSession!!.player.stop()
        broadcastAudioSessionClose()
        controller!!.release()
        controller = null
        mediaSession!!.release()
        mediaSession!!.player.release()
        mediaSession = null
        lyrics = null
        unregisterReceiver(headSetReceiver)
        super.onDestroy()
    }

    // This onGetSession is a necessary method override needed by
    // MediaSessionService.
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaSession

    override fun onAudioSessionIdChanged(audioSessionId: Int) {
        super.onAudioSessionIdChanged(audioSessionId)
        broadcastAudioSessionClose()
        lastSessionId = audioSessionId
        broadcastAudioSession()
    }

    private fun broadcastAudioSession() {
        if (lastSessionId != 0) {
            sendBroadcast(Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, lastSessionId)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            })
        }
    }

    private fun broadcastAudioSessionClose() {
        if (lastSessionId != 0) {
            sendBroadcast(Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, lastSessionId)
            })
        }
    }

    // Configure commands available to the controller in onConnect()
    override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo)
            : MediaSession.ConnectionResult {
        val availableSessionCommands =
            MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
        if (session.isMediaNotificationController(controller)
            || session.isAutoCompanionController(controller)
            || session.isAutomotiveController(controller)
        ) {
            // currently, all custom actions are only useful when used by notification
            // other clients hopefully have repeat/shuffle buttons like MCT does
            for (commandButton in customCommands) {
                // Add custom command to available session commands.
                commandButton.sessionCommand?.let { availableSessionCommands.add(it) }
            }
        }
        availableSessionCommands.add(SessionCommand(SERVICE_SET_TIMER, Bundle.EMPTY))
        availableSessionCommands.add(SessionCommand(SERVICE_GET_SESSION, Bundle.EMPTY))
        availableSessionCommands.add(SessionCommand(SERVICE_QUERY_TIMER, Bundle.EMPTY))
        availableSessionCommands.add(SessionCommand(SERVICE_GET_LYRICS, Bundle.EMPTY))
        handler.post {
            session.sendCustomCommand(
                controller,
                SessionCommand(SERVICE_GET_LYRICS, Bundle.EMPTY),
                Bundle.EMPTY
            )
        }
        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailableSessionCommands(availableSessionCommands.build())
            .build()
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle
    ): ListenableFuture<SessionResult> {
        return Futures.immediateFuture(when (customCommand.customAction) {
            PLAYBACK_SHUFFLE_ACTION_ON -> {
                this.controller!!.shuffleModeEnabled = true
                SessionResult(SessionResult.RESULT_SUCCESS)
            }

            PLAYBACK_SHUFFLE_ACTION_OFF -> {
                this.controller!!.shuffleModeEnabled = false
                SessionResult(SessionResult.RESULT_SUCCESS)
            }

            SERVICE_SET_TIMER -> {
                // 0 = clear timer
                timerDuration = customCommand.customExtras.getInt("duration")
                SessionResult(SessionResult.RESULT_SUCCESS)
            }

            SERVICE_QUERY_TIMER -> {
                SessionResult(SessionResult.RESULT_SUCCESS).also {
                    it.extras.putInt("duration", timerDuration)
                    val remaining = if (timerDuration > 0) {
                        (timerSetAtElapsedRealtime + timerDuration - android.os.SystemClock.elapsedRealtime())
                            .coerceAtLeast(0L).toInt()
                    } else 0
                    it.extras.putInt("remaining", remaining)
                }
            }

            SERVICE_GET_LYRICS -> {
                SessionResult(SessionResult.RESULT_SUCCESS).also {
                    it.extras.putParcelableArray("lyrics", lyrics?.toTypedArray())
                }
            }

            SERVICE_GET_SESSION -> {
                SessionResult(SessionResult.RESULT_SUCCESS).also {
                    it.extras.putInt("session", lastSessionId)
                }
            }

            PLAYBACK_REPEAT_OFF -> {
                this.controller!!.repeatMode = Player.REPEAT_MODE_OFF
                SessionResult(SessionResult.RESULT_SUCCESS)
            }

            PLAYBACK_REPEAT_ONE -> {
                this.controller!!.repeatMode = Player.REPEAT_MODE_ONE
                SessionResult(SessionResult.RESULT_SUCCESS)
            }

            PLAYBACK_REPEAT_ALL -> {
                this.controller!!.repeatMode = Player.REPEAT_MODE_ALL
                SessionResult(SessionResult.RESULT_SUCCESS)
            }

            else -> {
                SessionResult(SessionError.ERROR_BAD_VALUE)
            }
        })
    }

    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo
    ): ListenableFuture<MediaItemsWithStartPosition> {
        val settable = SettableFuture.create<MediaItemsWithStartPosition>()
        lastPlayedManager.restore { items, factory ->
            applyShuffleSeed(true, factory.toFactory(this.controller!!))
            if (items == null) {
                settable.setException(
                    NullPointerException(
                        "null MediaItemsWithStartPosition, see former logs for root cause"
                    )
                )
            } else if (items.mediaItems.isNotEmpty()) {
                settable.set(items)
            } else {
                settable.setException(
                    IndexOutOfBoundsException(
                        "LastPlayedManager restored empty MediaItemsWithStartPosition"
                    )
                )
            }
        }
        return settable
    }

    override fun onTracksChanged(tracks: Tracks) {
        val mediaItem = controller!!.currentMediaItem
        lyricsLock.runInBg {
            val trim = prefs.getBoolean("trim_lyrics", false)
            var lrc = loadAndParseLyricsFile(mediaItem?.getFile(), trim)
            if (lrc == null) {
                loop@ for (i in tracks.groups) {
                    for (j in 0 until i.length) {
                        if (!i.isTrackSelected(j)) continue
                        // note: wav files can have null metadata
                        val trackMetadata = i.getTrackFormat(j).metadata ?: continue
                        lrc = extractAndParseLyrics(trackMetadata, trim) ?: continue
                        // add empty element at the beginning
                        lrc.add(0, MediaStoreUtils.Lyric())
                        break@loop
                    }
                }
            } else {
                // add empty element at the beginning
                lrc.add(0, MediaStoreUtils.Lyric())
            }
            CoroutineScope(Dispatchers.Main).launch {
                mediaSession?.let {
                    lyrics = lrc
                    it.broadcastCustomCommand(
                        SessionCommand(SERVICE_GET_LYRICS, Bundle.EMPTY),
                        Bundle.EMPTY
                    )
                }
            }.join()
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        lyrics = null
        lastPlayedManager.save()
        if (pauseOnSongEndArmed) {
            controller!!.pause()
            timerDuration = 0
        }
        switchStatsTracking(
            if (controller?.isPlaying == true) mediaItem?.mediaId?.toLongOrNull() else null
        )
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        lastPlayedManager.save()
        switchStatsTracking(
            if (isPlaying) controller?.currentMediaItem?.mediaId?.toLongOrNull() else null
        )
    }

    override fun onEvents(player: Player, events: Player.Events) {
        super.onEvents(player, events)
        // if timeline changed, handle shuffle update in onTimelineChanged() instead
        // (onTimelineChanged() runs before both this callback and onShuffleModeEnabledChanged(),
        // which means shuffleFactory != null is not a valid check)
        if (events.contains(EVENT_SHUFFLE_MODE_ENABLED_CHANGED) &&
            shuffleFactory == null && !events.contains(Player.EVENT_TIMELINE_CHANGED)
        ) {
            // when enabling shuffle, re-shuffle lists so that the first index is up to date
            applyShuffleSeed(false) { c -> { CircularShuffleOrder(
                it, c, controller!!.mediaItemCount, Random.nextLong()) } }
        }
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        super.onShuffleModeEnabledChanged(shuffleModeEnabled)
        mediaSession!!.setCustomLayout(ImmutableList.of(getRepeatCommand(), getShufflingCommand()))
        if (needsMissingOnDestroyCallWorkarounds()) {
            handler.post { lastPlayedManager.save() }
        }
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        super.onTimelineChanged(timeline, reason)
        if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
            shuffleFactory?.let {
                applyShuffleSeed(false, it)
                shuffleFactory = null
            }
        }
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        super.onRepeatModeChanged(repeatMode)
        mediaSession!!.setCustomLayout(ImmutableList.of(getRepeatCommand(), getShufflingCommand()))
        if (needsMissingOnDestroyCallWorkarounds()) {
            handler.post { lastPlayedManager.save() }
        }
    }

    @SuppressLint("MissingPermission", "NotificationPermission") // only used on S/S_V2
    override fun onForegroundServiceStartNotAllowedException() {
        Log.w(TAG, "Failed to resume playback :/")
        if (mayThrowForegroundServiceStartNotAllowed()) {
            nm.notify(NOTIFY_ID, NotificationCompat.Builder(this, NOTIFY_CHANNEL_ID).apply {
                setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                setAutoCancel(true)
                setCategory(NotificationCompat.CATEGORY_ERROR)
                setSmallIcon(R.drawable.ic_warning)
                setContentTitle(this@GramophonePlaybackService.getString(R.string.fgs_failed_title))
                setContentText(this@GramophonePlaybackService.getString(R.string.fgs_failed_text))
                setContentIntent(
                    PendingIntent.getActivity(
                        this@GramophonePlaybackService,
                        PENDING_INTENT_NOTIFY_ID,
                        Intent(this@GramophonePlaybackService, MainActivity::class.java)
                            .putExtra(MainActivity.PLAYBACK_AUTO_START_FOR_FGS, true),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                )
                setVibrate(longArrayOf(0L, 200L))
                setLights(0, 0, 0)
                setBadgeIconType(NotificationCompat.BADGE_ICON_NONE)
                setSound(null)
            }.build())
        } else {
            handler.post {
                throw IllegalStateException("onForegroundServiceStartNotAllowedException shouldn't be called on T+")
            }
        }
    }

    private fun applyShuffleSeed(lazy: Boolean, factory:
        (Int) -> ((CircularShuffleOrder) -> Unit) -> CircularShuffleOrder) {
        if (lazy) {
            shuffleFactory = factory
        } else {
            (mediaSession?.player as EndedWorkaroundPlayer?)?.let {
                it.setShuffleOrder(factory(it.currentMediaItemIndex))
            }
        }
    }
}