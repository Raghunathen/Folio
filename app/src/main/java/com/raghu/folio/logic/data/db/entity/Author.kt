package com.raghu.folio.logic.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

const val AUTHOR_TABLE_NAME = "authorTable"

// One row per author folder under the Audiobooks root (Audiobooks/<Author>/).
@Entity(
    tableName = AUTHOR_TABLE_NAME,
    indices = [Index(value = [Author.FOLDER_URI_COLUMN], unique = true)]
)
data class Author(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = AUTHOR_ID_COLUMN)
    val authorId: Long = 0,
    @ColumnInfo(name = NAME_COLUMN)
    val name: String,
    @ColumnInfo(name = SORT_NAME_COLUMN)
    val sortName: String,
    @ColumnInfo(name = IMAGE_URI_COLUMN)
    val imageUri: String?,
    @ColumnInfo(name = FOLDER_URI_COLUMN)
    val folderUri: String,
    @ColumnInfo(name = CREATED_AT_COLUMN)
    val createdAt: Long,
) {
    companion object {
        const val AUTHOR_ID_COLUMN = "authorId"
        const val NAME_COLUMN = "name"
        const val SORT_NAME_COLUMN = "sortName"
        const val IMAGE_URI_COLUMN = "imageUri"
        const val FOLDER_URI_COLUMN = "folderUri"
        const val CREATED_AT_COLUMN = "createdAt"
    }
}
