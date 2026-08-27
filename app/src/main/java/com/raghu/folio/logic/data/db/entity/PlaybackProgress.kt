package com.raghu.folio.logic.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

const val PLAYBACK_PROGRESS_TABLE_NAME = "playbackProgressTable"

// One row per book that has ever been played. [isFinished]/[finishedAt] are set once the user
// confirms (or auto-confirms) the "Finished?" prompt shown near the end of a book.
@Entity(
    tableName = PLAYBACK_PROGRESS_TABLE_NAME,
    primaryKeys = [PlaybackProgress.BOOK_ID_COLUMN],
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = [Book.BOOK_ID_COLUMN],
            childColumns = [PlaybackProgress.BOOK_ID_COLUMN],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PlaybackProgress(
    @ColumnInfo(name = BOOK_ID_COLUMN)
    val bookId: Long,
    @ColumnInfo(name = POSITION_MS_COLUMN)
    val positionMs: Long,
    @ColumnInfo(name = CURRENT_PART_ID_COLUMN)
    val currentPartId: Long?,
    @ColumnInfo(name = PLAYBACK_SPEED_COLUMN)
    val playbackSpeed: Float,
    @ColumnInfo(name = LAST_PLAYED_AT_COLUMN)
    val lastPlayedAt: Long,
    @ColumnInfo(name = IS_FINISHED_COLUMN)
    val isFinished: Boolean,
    @ColumnInfo(name = FINISHED_AT_COLUMN)
    val finishedAt: Long?,
) {
    companion object {
        const val BOOK_ID_COLUMN = "bookId"
        const val POSITION_MS_COLUMN = "positionMs"
        const val CURRENT_PART_ID_COLUMN = "currentPartId"
        const val PLAYBACK_SPEED_COLUMN = "playbackSpeed"
        const val LAST_PLAYED_AT_COLUMN = "lastPlayedAt"
        const val IS_FINISHED_COLUMN = "isFinished"
        const val FINISHED_AT_COLUMN = "finishedAt"
    }
}
