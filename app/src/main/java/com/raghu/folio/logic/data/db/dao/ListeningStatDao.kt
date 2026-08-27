package com.raghu.folio.logic.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.raghu.folio.logic.data.db.entity.LISTENING_STAT_TABLE_NAME
import com.raghu.folio.logic.data.db.entity.ListeningStat

@Dao
interface ListeningStatDao {
    @Query(
        """
        INSERT INTO `$LISTENING_STAT_TABLE_NAME`
            (`${ListeningStat.DATE_COLUMN}`, `${ListeningStat.MS_LISTENED_COLUMN}`)
        VALUES (:date, :additionalMs)
        ON CONFLICT(`${ListeningStat.DATE_COLUMN}`) DO UPDATE SET
            `${ListeningStat.MS_LISTENED_COLUMN}` = `${ListeningStat.MS_LISTENED_COLUMN}` + :additionalMs
        """
    )
    fun addListenedMs(date: String, additionalMs: Long)

    @Query("SELECT * FROM `$LISTENING_STAT_TABLE_NAME` ORDER BY `${ListeningStat.DATE_COLUMN}` DESC")
    fun getAll(): List<ListeningStat>

    @Query("SELECT COALESCE(SUM(`${ListeningStat.MS_LISTENED_COLUMN}`), 0) FROM `$LISTENING_STAT_TABLE_NAME`")
    fun getTotalMs(): Long
}
