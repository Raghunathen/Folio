package com.raghu.folio.logic.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

const val BOOK_PART_TABLE_NAME = "bookPartTable"

// One row per audio file inside a book folder. When a book has multiple parts, [startOffsetMs] is
// this part's offset within the book's single continuous timeline, which is what lets multi-file
// books play back seamlessly with one unified progress bar while still being chapter-jumpable.
@Entity(
    tableName = BOOK_PART_TABLE_NAME,
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = [Book.BOOK_ID_COLUMN],
            childColumns = [BookPart.BOOK_ID_COLUMN],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(BookPart.BOOK_ID_COLUMN),
        Index(value = [BookPart.FILE_URI_COLUMN], unique = true),
    ]
)
data class BookPart(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = PART_ID_COLUMN)
    val partId: Long = 0,
    @ColumnInfo(name = BOOK_ID_COLUMN)
    val bookId: Long,
    @ColumnInfo(name = FILE_URI_COLUMN)
    val fileUri: String,
    @ColumnInfo(name = PART_INDEX_COLUMN)
    val partIndex: Int,
    @ColumnInfo(name = TITLE_COLUMN)
    val title: String,
    @ColumnInfo(name = DURATION_MS_COLUMN)
    val durationMs: Long,
    @ColumnInfo(name = START_OFFSET_MS_COLUMN)
    val startOffsetMs: Long,
) {
    companion object {
        const val PART_ID_COLUMN = "partId"
        const val BOOK_ID_COLUMN = "bookId"
        const val FILE_URI_COLUMN = "fileUri"
        const val PART_INDEX_COLUMN = "partIndex"
        const val TITLE_COLUMN = "title"
        const val DURATION_MS_COLUMN = "durationMs"
        const val START_OFFSET_MS_COLUMN = "startOffsetMs"
    }
}
