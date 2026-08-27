package com.raghu.folio.logic.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.raghu.folio.logic.data.db.entity.CHAPTER_TABLE_NAME
import com.raghu.folio.logic.data.db.entity.Chapter

@Dao
interface ChapterDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertChapters(chapters: List<Chapter>)

    @Query("SELECT * FROM `$CHAPTER_TABLE_NAME` WHERE `${Chapter.BOOK_ID_COLUMN}` = :bookId ORDER BY `${Chapter.START_MS_COLUMN}` ASC")
    fun getChaptersForBook(bookId: Long): List<Chapter>

    @Query("DELETE FROM `$CHAPTER_TABLE_NAME` WHERE `${Chapter.BOOK_ID_COLUMN}` = :bookId")
    fun deleteChaptersForBook(bookId: Long)
}
