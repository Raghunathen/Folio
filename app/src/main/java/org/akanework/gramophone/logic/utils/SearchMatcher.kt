package org.akanework.gramophone.logic.utils

import androidx.media3.common.MediaItem

/**
 * Forgiving text matching for the library search boxes.
 *
 * Punctuation and spacing are thrown away on both sides before comparing, so the way an artist
 * happens to be spelled in the tags stops mattering: "AR rahman", "a.r.rahman" and "A R Rahman"
 * all find "A. R. Rahman". The query is split into words and every word has to appear somewhere in
 * the song's text, which keeps multi-word queries meaningful ("rahman bombay") without forcing the
 * user to type things in the same order the tags use.
 */
object SearchMatcher {

    /** Lowercased, letters and digits only - "A. R. Rahman" becomes "arrahman". */
    fun squash(text: String): String = buildString(text.length) {
        for (c in text) if (c.isLetterOrDigit()) append(c.lowercaseChar())
    }

    /**
     * Everything worth matching a song against - title, artist, album and album artist - squashed
     * into one string. Built once per song when the library changes rather than per keystroke, so
     * typing only costs a substring scan.
     */
    fun haystackOf(item: MediaItem): String {
        val m = item.mediaMetadata
        return squash(
            listOfNotNull(m.title, m.artist, m.albumTitle, m.albumArtist).joinToString(" ")
        )
    }

    /** The query's words, squashed; empty when the query has nothing searchable in it. */
    fun tokenize(query: String): List<String> =
        query.split(' ', '\t', '\n').mapNotNull { squash(it).ifEmpty { null } }

    /** True when every query word appears in [haystack]. */
    fun matches(haystack: String, tokens: List<String>): Boolean {
        for (t in tokens) if (!haystack.contains(t)) return false
        return true
    }
}
