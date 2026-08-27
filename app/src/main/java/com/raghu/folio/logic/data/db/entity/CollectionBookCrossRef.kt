package com.raghu.folio.logic.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

const val COLLECTION_BOOK_CROSS_REF_TABLE_NAME = "collectionBookCrossRef"

@Entity(
    tableName = COLLECTION_BOOK_CROSS_REF_TABLE_NAME,
    primaryKeys = [
        CollectionBookCrossRef.COLLECTION_ID_COLUMN,
        CollectionBookCrossRef.BOOK_ID_COLUMN,
    ],
    foreignKeys = [
        ForeignKey(
            entity = Collection::class,
            parentColumns = [Collection.COLLECTION_ID_COLUMN],
            childColumns = [CollectionBookCrossRef.COLLECTION_ID_COLUMN],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Book::class,
            parentColumns = [Book.BOOK_ID_COLUMN],
            childColumns = [CollectionBookCrossRef.BOOK_ID_COLUMN],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(CollectionBookCrossRef.BOOK_ID_COLUMN)]
)
data class CollectionBookCrossRef(
    @ColumnInfo(name = COLLECTION_ID_COLUMN)
    val collectionId: Long,
    @ColumnInfo(name = BOOK_ID_COLUMN)
    val bookId: Long,
) {
    companion object {
        const val COLLECTION_ID_COLUMN = "collectionId"
        const val BOOK_ID_COLUMN = "bookId"
    }
}
