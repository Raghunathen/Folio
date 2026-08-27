package com.raghu.folio.logic.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.raghu.folio.logic.data.db.entity.BOOKMARK_TABLE_NAME
import com.raghu.folio.logic.data.db.entity.Bookmark

@Dao
interface BookmarkDao {
    @Insert
    fun addBookmark(bookmark: Bookmark): Long

    @Delete
    fun removeBookmark(bookmark: Bookmark): Int

    @Query("SELECT * FROM `$BOOKMARK_TABLE_NAME` WHERE `${Bookmark.BOOK_ID_COLUMN}` = :bookId ORDER BY `${Bookmark.POSITION_MS_COLUMN}` ASC")
    fun getBookmarksForBook(bookId: Long): List<Bookmark>
}
