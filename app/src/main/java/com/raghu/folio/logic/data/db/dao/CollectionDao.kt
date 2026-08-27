package com.raghu.folio.logic.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.raghu.folio.logic.data.db.entity.COLLECTION_BOOK_CROSS_REF_TABLE_NAME
import com.raghu.folio.logic.data.db.entity.COLLECTION_TABLE_NAME
import com.raghu.folio.logic.data.db.entity.Collection
import com.raghu.folio.logic.data.db.entity.CollectionBookCrossRef
import com.raghu.folio.logic.data.db.entity.CollectionWithBooks

@Dao
interface CollectionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun addCollection(collection: Collection): Long

    @Delete
    fun removeCollection(collection: Collection): Int

    @Query("DELETE FROM `$COLLECTION_TABLE_NAME` WHERE `${Collection.COLLECTION_ID_COLUMN}` = :collectionId")
    fun removeCollectionById(collectionId: Long): Int

    @Transaction
    @Query("SELECT * FROM `$COLLECTION_TABLE_NAME`")
    fun getAllCollections(): List<CollectionWithBooks>

    @Transaction
    @Query("SELECT * FROM `$COLLECTION_TABLE_NAME` WHERE `${Collection.COLLECTION_ID_COLUMN}` = :collectionId")
    fun getCollectionWithBooks(collectionId: Long): CollectionWithBooks?

    @Query(
        """
        INSERT OR REPLACE INTO `$COLLECTION_BOOK_CROSS_REF_TABLE_NAME`
            (`${CollectionBookCrossRef.COLLECTION_ID_COLUMN}`, `${CollectionBookCrossRef.BOOK_ID_COLUMN}`)
        VALUES (:collectionId, :bookId)
        """
    )
    fun addBookToCollection(collectionId: Long, bookId: Long)

    @Query(
        """
        DELETE FROM `$COLLECTION_BOOK_CROSS_REF_TABLE_NAME`
        WHERE `${CollectionBookCrossRef.COLLECTION_ID_COLUMN}` = :collectionId
        AND `${CollectionBookCrossRef.BOOK_ID_COLUMN}` = :bookId
        """
    )
    fun removeBookFromCollection(collectionId: Long, bookId: Long)
}
