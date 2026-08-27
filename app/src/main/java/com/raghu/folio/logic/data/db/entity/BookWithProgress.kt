package com.raghu.folio.logic.data.db.entity

import androidx.room.Embedded

// Produced by a raw LEFT JOIN query (see BookDao) rather than a Room @Relation, since a book has
// at most one progress row and we want it nullable (never-played books have no row at all).
data class BookWithProgress(
    @Embedded
    val book: Book,
    @Embedded(prefix = "progress_")
    val progress: PlaybackProgress?,
)
