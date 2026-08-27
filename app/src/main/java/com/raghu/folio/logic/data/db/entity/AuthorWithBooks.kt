package com.raghu.folio.logic.data.db.entity

import androidx.room.Embedded
import androidx.room.Relation

data class AuthorWithBooks(
    @Embedded
    val author: Author,
    @Relation(
        parentColumn = Author.AUTHOR_ID_COLUMN,
        entityColumn = Book.AUTHOR_ID_COLUMN,
    )
    val books: List<Book>,
)
