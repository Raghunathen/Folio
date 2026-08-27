package com.raghu.folio.logic.utils.audiobook

import com.raghu.folio.logic.data.db.entity.BookPart
import com.raghu.folio.logic.data.db.entity.Chapter

/**
 * Pure helpers for converting between a book's overall (cross-part) timeline and an individual
 * part index + in-part position, and for looking up chapters by absolute position. [parts] must
 * be sorted by [BookPart.partIndex] ascending (as returned by `BookPartDao.getPartsForBook`).
 */
object AudiobookTimeline {

    data class PartPosition(val partIndex: Int, val positionInPartMs: Long)

    /** Total duration of the book across all parts. */
    fun totalDurationMs(parts: List<BookPart>): Long =
        parts.lastOrNull()?.let { it.startOffsetMs + it.durationMs } ?: 0L

    /** Absolute position for the part at [partIndex] (0-based index into [parts]) plus
     * [positionInPartMs] within that part. */
    fun toAbsoluteMs(parts: List<BookPart>, partIndex: Int, positionInPartMs: Long): Long {
        val part = parts.getOrNull(partIndex) ?: return 0L
        return part.startOffsetMs + positionInPartMs
    }

    /** Finds which part [absoluteMs] falls into, and the position within that part. Clamps into
     * range if [absoluteMs] is negative or past the end of the book. */
    fun fromAbsoluteMs(parts: List<BookPart>, absoluteMs: Long): PartPosition {
        if (parts.isEmpty()) return PartPosition(0, 0L)
        val clamped = absoluteMs.coerceIn(0, totalDurationMs(parts))
        val index = parts.indexOfLast { it.startOffsetMs <= clamped }.coerceAtLeast(0)
        return PartPosition(index, clamped - parts[index].startOffsetMs)
    }

    /** The chapter (if any) that contains [absoluteMs], in book-overall-timeline coordinates. */
    fun chapterAt(chapters: List<Chapter>, absoluteMs: Long): Chapter? =
        chapters.filter { absoluteMs >= it.startMs && absoluteMs < it.endMs }
            .maxByOrNull { it.startMs }
}
