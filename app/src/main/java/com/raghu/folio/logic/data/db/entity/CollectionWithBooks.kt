package com.raghu.folio.logic.data.db.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class CollectionWithBooks(
    @Embedded
    val collection: Collection,
    @Relation(
        parentColumn = Collection.COLLECTION_ID_COLUMN,
        entityColumn = Book.BOOK_ID_COLUMN,
        associateBy = Junction(CollectionBookCrossRef::class)
    )
    val books: List<Book>,
)
