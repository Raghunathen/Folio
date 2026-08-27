package com.raghu.folio.logic.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

const val BOOK_TABLE_NAME = "bookTable"

// One row per book folder under an author (Audiobooks/<Author>/<BookTitle>/). seriesName and
// seriesIndex are auto-detected from filename/title numeric patterns within the author, not from
// a folder level (the folder structure is strictly Author/Book, no Series level).
@Entity(
    tableName = BOOK_TABLE_NAME,
    foreignKeys = [
        ForeignKey(
            entity = Author::class,
            parentColumns = [Author.AUTHOR_ID_COLUMN],
            childColumns = [Book.AUTHOR_ID_COLUMN],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(Book.AUTHOR_ID_COLUMN),
        Index(value = [Book.FOLDER_URI_COLUMN], unique = true),
    ]
)
data class Book(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = BOOK_ID_COLUMN)
    val bookId: Long = 0,
    @ColumnInfo(name = AUTHOR_ID_COLUMN)
    val authorId: Long,
    @ColumnInfo(name = TITLE_COLUMN)
    val title: String,
    @ColumnInfo(name = SORT_TITLE_COLUMN)
    val sortTitle: String,
    @ColumnInfo(name = NARRATOR_COLUMN)
    val narrator: String?,
    @ColumnInfo(name = SERIES_NAME_COLUMN)
    val seriesName: String?,
    @ColumnInfo(name = SERIES_INDEX_COLUMN)
    val seriesIndex: Float?,
    @ColumnInfo(name = DESCRIPTION_COLUMN)
    val description: String?,
    @ColumnInfo(name = COVER_URI_COLUMN)
    val coverUri: String?,
    @ColumnInfo(name = FOLDER_URI_COLUMN)
    val folderUri: String,
    @ColumnInfo(name = DURATION_MS_COLUMN)
    val durationMs: Long,
    @ColumnInfo(name = DATE_ADDED_COLUMN)
    val dateAdded: Long,
    @ColumnInfo(name = DATE_MODIFIED_COLUMN)
    val dateModified: Long,
) {
    companion object {
        const val BOOK_ID_COLUMN = "bookId"
        const val AUTHOR_ID_COLUMN = "authorId"
        const val TITLE_COLUMN = "title"
        const val SORT_TITLE_COLUMN = "sortTitle"
        const val NARRATOR_COLUMN = "narrator"
        const val SERIES_NAME_COLUMN = "seriesName"
        const val SERIES_INDEX_COLUMN = "seriesIndex"
        const val DESCRIPTION_COLUMN = "description"
        const val COVER_URI_COLUMN = "coverUri"
        const val FOLDER_URI_COLUMN = "folderUri"
        const val DURATION_MS_COLUMN = "durationMs"
        const val DATE_ADDED_COLUMN = "dateAdded"
        const val DATE_MODIFIED_COLUMN = "dateModified"
    }
}
