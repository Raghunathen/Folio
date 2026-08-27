package com.raghu.folio.logic.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

const val CHAPTER_TABLE_NAME = "chapterTable"

// Chapter markers for a book, in the book's overall (cross-part) timeline coordinates. Populated
// either from a single file's embedded chapter markers (e.g. .m4b) - in which case [partId] points
// at that part - or synthesized one-per-part for multi-file books without embedded markers.
@Entity(
    tableName = CHAPTER_TABLE_NAME,
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = [Book.BOOK_ID_COLUMN],
            childColumns = [Chapter.BOOK_ID_COLUMN],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = BookPart::class,
            parentColumns = [BookPart.PART_ID_COLUMN],
            childColumns = [Chapter.PART_ID_COLUMN],
            onDelete = ForeignKey.CASCADE
        ),
    ],
    indices = [
        Index(Chapter.BOOK_ID_COLUMN),
        Index(Chapter.PART_ID_COLUMN),
    ]
)
data class Chapter(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = CHAPTER_ID_COLUMN)
    val chapterId: Long = 0,
    @ColumnInfo(name = BOOK_ID_COLUMN)
    val bookId: Long,
    @ColumnInfo(name = PART_ID_COLUMN)
    val partId: Long?,
    @ColumnInfo(name = TITLE_COLUMN)
    val title: String,
    @ColumnInfo(name = START_MS_COLUMN)
    val startMs: Long,
    @ColumnInfo(name = END_MS_COLUMN)
    val endMs: Long,
) {
    companion object {
        const val CHAPTER_ID_COLUMN = "chapterId"
        const val BOOK_ID_COLUMN = "bookId"
        const val PART_ID_COLUMN = "partId"
        const val TITLE_COLUMN = "title"
        const val START_MS_COLUMN = "startMs"
        const val END_MS_COLUMN = "endMs"
    }
}
