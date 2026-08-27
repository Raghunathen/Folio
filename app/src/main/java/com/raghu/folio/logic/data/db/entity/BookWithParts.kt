package com.raghu.folio.logic.data.db.entity

import androidx.room.Embedded
import androidx.room.Relation

data class BookWithParts(
    @Embedded
    val book: Book,
    @Relation(
        parentColumn = Book.BOOK_ID_COLUMN,
        entityColumn = BookPart.BOOK_ID_COLUMN,
    )
    val parts: List<BookPart>,
)
