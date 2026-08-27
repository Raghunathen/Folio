package com.raghu.folio.logic.utils.audiobook

import java.util.Locale

/** Result of parsing a single book folder name, before we know its siblings. */
data class ParsedBookName(
    val seriesCandidate: String?,
    val index: Float?,
    val cleanTitle: String,
)

/**
 * Best-effort series detection from book folder names alone (the folder structure is strictly
 * `Author/Book`, there is no dedicated Series level). A name is only treated as part of a series
 * once we see at least one *other* sibling book under the same author sharing the same detected
 * series text - a single book whose title happens to contain a number is not a series.
 */
object SeriesDetector {
    // "Mistborn 1 - The Final Empire", "Mistborn, Book 2: The Well of Ascension",
    // "Mistborn #3 The Hero of Ages"
    private val SERIES_NUM_TITLE = Regex(
        """^(?<series>.+?)[,\s]+(?:book|vol\.?|volume|#)?\s*0*(?<num>\d{1,3})(?:\s*[-:.]\s*(?<title>.+))?$""",
        RegexOption.IGNORE_CASE
    )

    // "01 - Title", "1. Title", "3 Title" (no series text, just a leading index)
    private val LEADING_NUM_TITLE = Regex("""^0*(?<num>\d{1,3})[\s._\-]+(?<title>.+)$""")

    fun parse(folderName: String): ParsedBookName {
        val trimmed = folderName.trim()

        SERIES_NUM_TITLE.find(trimmed)?.let { m ->
            val series = m.groups["series"]?.value?.trim()?.trimEnd(',', '-', ':')
            val num = m.groups["num"]?.value?.toFloatOrNull()
            val title = m.groups["title"]?.value?.trim()
            if (!series.isNullOrBlank() && num != null) {
                return ParsedBookName(series, num, title.takeUnless { it.isNullOrBlank() } ?: trimmed)
            }
        }

        LEADING_NUM_TITLE.find(trimmed)?.let { m ->
            val num = m.groups["num"]?.value?.toFloatOrNull()
            val title = m.groups["title"]?.value?.trim()
            if (num != null && !title.isNullOrBlank()) {
                return ParsedBookName(null, num, title)
            }
        }

        return ParsedBookName(null, null, trimmed)
    }

    /**
     * Groups already-[parse]d sibling book names (scoped to one author) into detected series.
     * Returns, for each input key, the (seriesName, seriesIndex) to use, or null if that book
     * isn't part of a detected series.
     */
    fun <K> detectSeries(parsedByKey: Map<K, ParsedBookName>): Map<K, Pair<String, Float>?> {
        val bySeriesName = parsedByKey.entries
            .filter { it.value.seriesCandidate != null }
            .groupBy { it.value.seriesCandidate!!.lowercase(Locale.ROOT) }

        return parsedByKey.mapValues { (_, parsed) ->
            val key = parsed.seriesCandidate?.lowercase(Locale.ROOT)
            val group = key?.let { bySeriesName[it] }
            if (group != null && group.size >= 2 && parsed.index != null) {
                parsed.seriesCandidate!! to parsed.index
            } else {
                null
            }
        }
    }
}
