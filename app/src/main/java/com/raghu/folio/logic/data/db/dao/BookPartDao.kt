package com.raghu.folio.logic.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.raghu.folio.logic.data.db.entity.BOOK_PART_TABLE_NAME
import com.raghu.folio.logic.data.db.entity.BookPart

@Dao
interface BookPartDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertPart(part: BookPart): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertParts(parts: List<BookPart>)

    @Query("SELECT * FROM `$BOOK_PART_TABLE_NAME` WHERE `${BookPart.BOOK_ID_COLUMN}` = :bookId ORDER BY `${BookPart.PART_INDEX_COLUMN}` ASC")
    fun getPartsForBook(bookId: Long): List<BookPart>

    @Query("SELECT * FROM `$BOOK_PART_TABLE_NAME` WHERE `${BookPart.PART_ID_COLUMN}` = :partId")
    fun getPartById(partId: Long): BookPart?

    @Query("DELETE FROM `$BOOK_PART_TABLE_NAME` WHERE `${BookPart.BOOK_ID_COLUMN}` = :bookId")
    fun deletePartsForBook(bookId: Long)
}
