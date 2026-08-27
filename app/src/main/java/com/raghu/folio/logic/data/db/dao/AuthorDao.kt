package com.raghu.folio.logic.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.raghu.folio.logic.data.db.entity.AUTHOR_TABLE_NAME
import com.raghu.folio.logic.data.db.entity.Author
import com.raghu.folio.logic.data.db.entity.AuthorWithBooks

@Dao
interface AuthorDao {
    /**
     * Insert a newly scanned author folder. Ignored if [Author.folderUri] already exists.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAuthor(author: Author): Long

    @Query("SELECT * FROM `$AUTHOR_TABLE_NAME` WHERE `${Author.FOLDER_URI_COLUMN}` = :folderUri")
    fun getAuthorByFolderUri(folderUri: String): Author?

    @Query("SELECT * FROM `$AUTHOR_TABLE_NAME` WHERE `${Author.AUTHOR_ID_COLUMN}` = :authorId")
    fun getAuthorById(authorId: Long): Author?

    @Query("SELECT * FROM `$AUTHOR_TABLE_NAME` ORDER BY `${Author.SORT_NAME_COLUMN}` ASC")
    fun getAllAuthors(): List<Author>

    @Transaction
    @Query("SELECT * FROM `$AUTHOR_TABLE_NAME` ORDER BY `${Author.SORT_NAME_COLUMN}` ASC")
    fun getAllAuthorsWithBooks(): List<AuthorWithBooks>

    @Transaction
    @Query("SELECT * FROM `$AUTHOR_TABLE_NAME` WHERE `${Author.AUTHOR_ID_COLUMN}` = :authorId")
    fun getAuthorWithBooks(authorId: Long): AuthorWithBooks?

    /**
     * Removes authors whose folder is no longer present after a rescan (their books/parts/etc.
     * cascade-delete via foreign keys).
     */
    @Query("DELETE FROM `$AUTHOR_TABLE_NAME` WHERE `${Author.AUTHOR_ID_COLUMN}` NOT IN (:keepIds)")
    fun deleteAuthorsNotIn(keepIds: List<Long>)
}
