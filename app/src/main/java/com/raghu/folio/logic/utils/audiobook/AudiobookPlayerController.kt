package com.raghu.folio.logic.utils.audiobook

import android.content.Context
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.raghu.folio.logic.data.db.AppDatabase
import com.raghu.folio.logic.data.db.entity.Book
import com.raghu.folio.logic.data.db.entity.BookPart
import com.raghu.folio.logic.data.db.entity.Chapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Wraps a single [ExoPlayer] instance to play one audiobook's parts back-to-back as a single
 * continuous, chapter-seekable timeline, and persists progress as it plays.
 *
 * This is business logic only - it is not yet wired into a `MediaLibraryService`/notification/
 * lock-screen session. That wiring happens once the old music-oriented `FolioPlaybackService` is
 * replaced during the UI rewrite step (see docs/PIVOT_NOTES.md); building it here first keeps the
 * tricky continuous-timeline/chapter/speed/sleep-timer logic testable and decoupled from that
 * larger, UI-breaking change.
 */
class AudiobookPlayerController(
    private val context: Context,
    private val player: ExoPlayer,
) {
    private val db by lazy { AppDatabase.getInstance(context) }
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private var book: Book? = null
    private var parts: List<BookPart> = emptyList()
    private var chapters: List<Chapter> = emptyList()
    private var progressSaveJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var listeningAnchorElapsedMs: Long? = null

    /** Invoked (on the main thread) when an active sleep timer elapses and pauses playback. */
    var onSleepTimerElapsed: (() -> Unit)? = null

    private var finishedNotified = false

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                listeningAnchorElapsedMs = SystemClock.elapsedRealtime()
                startProgressSaveLoop()
            } else {
                progressSaveJob?.cancel()
                flushListeningTime()
                saveProgressNow()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED && !finishedNotified) {
                finishedNotified = true
                markFinished()
            }
        }
    }

    init {
        player.addListener(playerListener)
    }

    /**
     * Loads [book]'s [parts] (sorted by `partIndex`) and [chapters] into the player and seeks to
     * [startPositionMs] (absolute, cross-part position - e.g. from a saved `PlaybackProgress`).
     */
    fun load(
        book: Book,
        authorName: String?,
        parts: List<BookPart>,
        chapters: List<Chapter>,
        startPositionMs: Long,
        speed: Float,
    ) {
        this.book = book
        this.parts = parts
        this.chapters = chapters
        this.finishedNotified = false

        val metadata = MediaMetadata.Builder()
            .setTitle(book.title)
            .setArtist(authorName)
            .build()
        player.setMediaItems(parts.map {
            MediaItem.Builder().setUri(it.fileUri).setMediaMetadata(metadata).build()
        })
        player.setPlaybackSpeed(speed)
        player.prepare()
        seekToAbsoluteMs(startPositionMs)
    }

    fun play() = player.play()
    fun pause() = player.pause()

    fun currentAbsolutePositionMs(): Long {
        val currentParts = parts
        if (currentParts.isEmpty()) return 0L
        val index = player.currentMediaItemIndex.coerceIn(0, currentParts.lastIndex)
        return AudiobookTimeline.toAbsoluteMs(currentParts, index, player.currentPosition)
    }

    fun currentChapter(): Chapter? = AudiobookTimeline.chapterAt(chapters, currentAbsolutePositionMs())

    fun currentChapters(): List<Chapter> = chapters

    fun totalDurationMs(): Long = AudiobookTimeline.totalDurationMs(parts)

    fun seekToAbsoluteMs(absoluteMs: Long) {
        val pos = AudiobookTimeline.fromAbsoluteMs(parts, absoluteMs)
        player.seekTo(pos.partIndex, pos.positionInPartMs)
    }

    fun skipForward(seconds: Int) =
        seekToAbsoluteMs(currentAbsolutePositionMs() + seconds * 1000L)

    fun skipBackward(seconds: Int) =
        seekToAbsoluteMs((currentAbsolutePositionMs() - seconds * 1000L).coerceAtLeast(0))

    fun seekToChapter(chapter: Chapter) = seekToAbsoluteMs(chapter.startMs)

    fun setPlaybackSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
        saveProgressNow()
    }

    /** Native ExoPlayer silence-skipping: speeds through quiet passages without changing pitch. */
    fun setSkipSilenceEnabled(enabled: Boolean) {
        player.skipSilenceEnabled = enabled
    }

    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        sleepTimerJob = scope.launch {
            delay(minutes * 60_000L)
            pause()
            onSleepTimerElapsed?.invoke()
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
    }

    fun isSleepTimerActive(): Boolean = sleepTimerJob?.isActive == true

    /** Call when this controller is no longer needed (e.g. service destroyed). Does not release
     * the underlying [player] - the caller owns its lifecycle. */
    fun release() {
        player.removeListener(playerListener)
        progressSaveJob?.cancel()
        sleepTimerJob?.cancel()
        flushListeningTime()
        saveProgressNow()
        scope.cancel()
    }

    private fun startProgressSaveLoop() {
        progressSaveJob?.cancel()
        progressSaveJob = scope.launch {
            while (true) {
                delay(5_000L)
                saveProgressNow()
                flushListeningTime()
            }
        }
    }

    /** Flushes wall-clock listening time accumulated since the last flush into today's
     * [com.raghu.folio.logic.data.db.entity.ListeningStat] row. Not tied to playback speed - this
     * is real-world time spent with the player active, matching how most listening-stats UIs work. */
    private fun flushListeningTime() {
        val anchor = listeningAnchorElapsedMs ?: return
        val now = SystemClock.elapsedRealtime()
        listeningAnchorElapsedMs = now
        val deltaMs = now - anchor
        if (deltaMs <= 0) return
        val date = LocalDate.now().toString()
        scope.launch(Dispatchers.IO) {
            db.listeningStatDao().addListenedMs(date, deltaMs)
        }
    }

    private fun saveProgressNow() {
        val currentBook = book ?: return
        val currentParts = parts
        if (currentParts.isEmpty()) return
        val absoluteMs = currentAbsolutePositionMs()
        val partId = currentParts.getOrNull(player.currentMediaItemIndex)?.partId
        val speed = player.playbackParameters.speed
        val now = System.currentTimeMillis()
        scope.launch(Dispatchers.IO) {
            db.playbackProgressDao().upsertProgress(
                bookId = currentBook.bookId,
                positionMs = absoluteMs,
                currentPartId = partId,
                playbackSpeed = speed,
                lastPlayedAt = now,
                isFinished = false,
                finishedAt = null,
            )
        }
    }

    private fun markFinished() {
        val currentBook = book ?: return
        val currentParts = parts
        val partId = currentParts.lastOrNull()?.partId
        val speed = player.playbackParameters.speed
        val now = System.currentTimeMillis()
        scope.launch(Dispatchers.IO) {
            db.playbackProgressDao().upsertProgress(
                bookId = currentBook.bookId,
                positionMs = totalDurationMs(),
                currentPartId = partId,
                playbackSpeed = speed,
                lastPlayedAt = now,
                isFinished = true,
                finishedAt = now,
            )
        }
    }
}
