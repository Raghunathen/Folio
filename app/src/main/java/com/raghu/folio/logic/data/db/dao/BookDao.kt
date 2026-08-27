package com.raghu.folio.logic.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.raghu.folio.logic.data.db.entity.BOOK_TABLE_NAME
import com.raghu.folio.logic.data.db.entity.Book
import com.raghu.folio.logic.data.db.entity.BookWithChapters
import com.raghu.folio.logic.data.db.entity.BookWithParts
import com.raghu.folio.logic.data.db.entity.BookWithProgress
import com.raghu.folio.logic.data.db.entity.PLAYBACK_PROGRESS_TABLE_NAME
import com.raghu.folio.logic.data.db.entity.PlaybackProgress

private const val BOOK_WITH_PROGRESS_SELECT = """
    SELECT b.*,
        p.`${PlaybackProgress.BOOK_ID_COLUMN}` AS `progress_${PlaybackProgress.BOOK_ID_COLUMN}`,
        p.`${PlaybackProgress.POSITION_MS_COLUMN}` AS `progress_${PlaybackProgress.POSITION_MS_COLUMN}`,
        p.`${PlaybackProgress.CURRENT_PART_ID_COLUMN}` AS `progress_${PlaybackProgress.CURRENT_PART_ID_COLUMN}`,
        p.`${PlaybackProgress.PLAYBACK_SPEED_COLUMN}` AS `progress_${PlaybackProgress.PLAYBACK_SPEED_COLUMN}`,
        p.`${PlaybackProgress.LAST_PLAYED_AT_COLUMN}` AS `progress_${PlaybackProgress.LAST_PLAYED_AT_COLUMN}`,
        p.`${PlaybackProgress.IS_FINISHED_COLUMN}` AS `progress_${PlaybackProgress.IS_FINISHED_COLUMN}`,
        p.`${PlaybackProgress.FINISHED_AT_COLUMN}` AS `progress_${PlaybackProgress.FINISHED_AT_COLUMN}`
    FROM `$BOOK_TABLE_NAME` b LEFT JOIN `$PLAYBACK_PROGRESS_TABLE_NAME` p
        ON b.`${Book.BOOK_ID_COLUMN}` = p.`${PlaybackProgress.BOOK_ID_COLUMN}`
"""

@Dao
interface BookDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertBook(book: Book): Long

    @Update
    fun updateBook(book: Book)

    @Query("SELECT * FROM `$BOOK_TABLE_NAME` WHERE `${Book.FOLDER_URI_COLUMN}` = :folderUri")
    fun getBookByFolderUri(folderUri: String): Book?

    @Query("SELECT * FROM `$BOOK_TABLE_NAME` WHERE `${Book.BOOK_ID_COLUMN}` = :bookId")
    fun getBookById(bookId: Long): Book?

    @Query("SELECT * FROM `$BOOK_TABLE_NAME` WHERE `${Book.AUTHOR_ID_COLUMN}` = :authorId ORDER BY `${Book.SERIES_INDEX_COLUMN}` ASC, `${Book.SORT_TITLE_COLUMN}` ASC")
    fun getBooksByAuthor(authorId: Long): List<Book>

    /** The next book in the same series (lowest [Book.SERIES_INDEX_COLUMN] greater than [seriesIndex]),
     *  used to auto-offer continuing a series once the current book finishes. */
    @Query(
        """
        SELECT * FROM `$BOOK_TABLE_NAME`
        WHERE `${Book.AUTHOR_ID_COLUMN}` = :authorId AND `${Book.SERIES_NAME_COLUMN}` = :seriesName
            AND `${Book.SERIES_INDEX_COLUMN}` > :seriesIndex
        ORDER BY `${Book.SERIES_INDEX_COLUMN}` ASC
        LIMIT 1
        """
    )
    fun getNextInSeries(authorId: Long, seriesName: String, seriesIndex: Float): Book?

    @Transaction
    @Query("SELECT * FROM `$BOOK_TABLE_NAME` WHERE `${Book.BOOK_ID_COLUMN}` = :bookId")
    fun getBookWithParts(bookId: Long): BookWithParts?

    @Transaction
    @Query("SELECT * FROM `$BOOK_TABLE_NAME` WHERE `${Book.BOOK_ID_COLUMN}` = :bookId")
    fun getBookWithChapters(bookId: Long): BookWithChapters?

    /** All books, each left-joined with its progress row (null if never played). */
    @Query(BOOK_WITH_PROGRESS_SELECT)
    fun getAllBooksWithProgress(): List<BookWithProgress>

    /** Books with some progress recorded but not finished yet - the "Continue Listening" shelf. */
    @Query(
        """
        $BOOK_WITH_PROGRESS_SELECT
        WHERE p.`${PlaybackProgress.POSITION_MS_COLUMN}` > 0 AND p.`${PlaybackProgress.IS_FINISHED_COLUMN}` = 0
        ORDER BY p.`${PlaybackProgress.LAST_PLAYED_AT_COLUMN}` DESC
        """
    )
    fun getContinueListening(): List<BookWithProgress>

    @Query("SELECT * FROM `$BOOK_TABLE_NAME` ORDER BY `${Book.DATE_ADDED_COLUMN}` DESC LIMIT :limit")
    fun getRecentlyAdded(limit: Int): List<Book>

    @Query("SELECT * FROM `$BOOK_TABLE_NAME` ORDER BY `${Book.SORT_TITLE_COLUMN}` ASC")
    fun getAllBooks(): List<Book>

    @Query(
        """
        SELECT * FROM `$BOOK_TABLE_NAME`
        WHERE `${Book.TITLE_COLUMN}` LIKE '%' || :query || '%'
        ORDER BY `${Book.SORT_TITLE_COLUMN}` ASC
        """
    )
    fun searchBooksByTitle(query: String): List<Book>

    @Query("DELETE FROM `$BOOK_TABLE_NAME` WHERE `${Book.BOOK_ID_COLUMN}` NOT IN (:keepIds)")
    fun deleteBooksNotIn(keepIds: List<Long>)
}
