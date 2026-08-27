package com.raghu.folio.logic.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

const val BOOKMARK_TABLE_NAME = "bookmarkTable"

// A user-named timestamp within a book's overall timeline.
@Entity(
    tableName = BOOKMARK_TABLE_NAME,
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = [Book.BOOK_ID_COLUMN],
            childColumns = [Bookmark.BOOK_ID_COLUMN],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(Bookmark.BOOK_ID_COLUMN)]
)
data class Bookmark(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = BOOKMARK_ID_COLUMN)
    val bookmarkId: Long = 0,
    @ColumnInfo(name = BOOK_ID_COLUMN)
    val bookId: Long,
    @ColumnInfo(name = POSITION_MS_COLUMN)
    val positionMs: Long,
    @ColumnInfo(name = LABEL_COLUMN)
    val label: String?,
    @ColumnInfo(name = CREATED_AT_COLUMN)
    val createdAt: Long,
) {
    companion object {
        const val BOOKMARK_ID_COLUMN = "bookmarkId"
        const val BOOK_ID_COLUMN = "bookId"
        const val POSITION_MS_COLUMN = "positionMs"
        const val LABEL_COLUMN = "label"
        const val CREATED_AT_COLUMN = "createdAt"
    }
}
