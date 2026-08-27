package com.raghu.folio.logic.data.db.entity

import androidx.room.Embedded
import androidx.room.Relation

data class BookWithChapters(
    @Embedded
    val book: Book,
    @Relation(
        parentColumn = Book.BOOK_ID_COLUMN,
        entityColumn = Chapter.BOOK_ID_COLUMN,
    )
    val chapters: List<Chapter>,
)
