package com.raghu.folio.logic.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

const val COLLECTION_TABLE_NAME = "collectionTable"

// User-defined book grouping (replaces the old music "Playlist" concept), e.g. "Currently
// Listening", "Favorites", or any custom shelf the user creates.
@Entity(tableName = COLLECTION_TABLE_NAME)
data class Collection(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = COLLECTION_ID_COLUMN)
    val collectionId: Long = 0,
    @ColumnInfo(name = NAME_COLUMN)
    val name: String,
    @ColumnInfo(name = COVER_URI_COLUMN)
    val coverUri: String?,
    @ColumnInfo(name = CREATED_AT_COLUMN)
    val createdAt: Long,
) {
    companion object {
        const val COLLECTION_ID_COLUMN = "collectionId"
        const val NAME_COLUMN = "name"
        const val COVER_URI_COLUMN = "coverUri"
        const val CREATED_AT_COLUMN = "createdAt"
    }
}
