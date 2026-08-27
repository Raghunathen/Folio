package com.raghu.folio.widget

import android.content.Context

private const val PREFS_NAME = "audiobook_widget_state"
private const val KEY_BOOK_ID = "book_id"
private const val KEY_TITLE = "title"
private const val KEY_AUTHOR = "author"
private const val KEY_COVER_URI = "cover_uri"
private const val KEY_IS_PLAYING = "is_playing"
private const val KEY_POSITION_MS = "position_ms"
private const val KEY_DURATION_MS = "duration_ms"

data class WidgetState(
    val bookId: Long,
    val title: String?,
    val author: String?,
    val coverUri: String?,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
)

/**
 * Small SharedPreferences-backed store letting [com.raghu.folio.logic.AudiobookPlaybackService]
 * push the latest playback state for [AudiobookWidgetProvider] to render, without the widget
 * needing its own persistent [androidx.media3.session.MediaController] connection just to draw.
 */
object WidgetStateStore {

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(context: Context): WidgetState {
        val p = prefs(context)
        return WidgetState(
            bookId = p.getLong(KEY_BOOK_ID, -1L),
            title = p.getString(KEY_TITLE, null),
            author = p.getString(KEY_AUTHOR, null),
            coverUri = p.getString(KEY_COVER_URI, null),
            isPlaying = p.getBoolean(KEY_IS_PLAYING, false),
            positionMs = p.getLong(KEY_POSITION_MS, 0L),
            durationMs = p.getLong(KEY_DURATION_MS, 0L),
        )
    }

    fun writeBookInfo(context: Context, bookId: Long, coverUri: String?, durationMs: Long) {
        prefs(context).edit()
            .putLong(KEY_BOOK_ID, bookId)
            .putString(KEY_COVER_URI, coverUri)
            .putLong(KEY_DURATION_MS, durationMs)
            .apply()
    }

    fun writeMetadata(context: Context, title: String?, author: String?) {
        prefs(context).edit()
            .putString(KEY_TITLE, title)
            .putString(KEY_AUTHOR, author)
            .apply()
    }

    fun writePlaybackState(context: Context, isPlaying: Boolean, positionMs: Long) {
        prefs(context).edit()
            .putBoolean(KEY_IS_PLAYING, isPlaying)
            .putLong(KEY_POSITION_MS, positionMs)
            .apply()
    }
}
