package org.akanework.gramophone.logic.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import org.akanework.gramophone.logic.data.db.entity.LISTENING_STAT_TABLE_NAME
import org.akanework.gramophone.logic.data.db.entity.ListeningStat

data class SongPlayTotal(val mediaItemId: Long, val msPlayed: Long)

@Dao
interface ListeningStatDao {
    /**
     * Adds [msPlayed] to whatever's already recorded for this song on this day (0 if first time).
     */
    @Query(
        """
        INSERT INTO `$LISTENING_STAT_TABLE_NAME`
            (`${ListeningStat.MEDIA_ITEM_ID_COLUMN}`, `${ListeningStat.DAY_EPOCH_COLUMN}`, `${ListeningStat.MS_PLAYED_COLUMN}`)
        VALUES (:mediaItemId, :dayEpoch, :msPlayed)
        ON CONFLICT(`${ListeningStat.MEDIA_ITEM_ID_COLUMN}`, `${ListeningStat.DAY_EPOCH_COLUMN}`)
        DO UPDATE SET `${ListeningStat.MS_PLAYED_COLUMN}` = `${ListeningStat.MS_PLAYED_COLUMN}` + :msPlayed
        """
    )
    fun addListeningTime(mediaItemId: Long, dayEpoch: Long, msPlayed: Long)

    /**
     * Per-song totals for every day at or after [fromDayEpoch] - used to attribute listening time
     * to artists (the library, not this table, knows which songs belong to which artist).
     */
    @Query(
        """
        SELECT `${ListeningStat.MEDIA_ITEM_ID_COLUMN}` AS mediaItemId, SUM(`${ListeningStat.MS_PLAYED_COLUMN}`) AS msPlayed
        FROM `$LISTENING_STAT_TABLE_NAME`
        WHERE `${ListeningStat.DAY_EPOCH_COLUMN}` >= :fromDayEpoch
        GROUP BY `${ListeningStat.MEDIA_ITEM_ID_COLUMN}`
        """
    )
    fun getPerSongTotals(fromDayEpoch: Long): List<SongPlayTotal>

    /**
     * Sets (not adds to) the value for this (song, day) - used only by restoreFromBackup, where
     * the backup's snapshot should become authoritative for that row rather than stacking on top
     * of whatever's already there (which would double-count if a restore is ever run more than
     * once against a table that already has data).
     */
    @Query(
        """
        INSERT OR REPLACE INTO `$LISTENING_STAT_TABLE_NAME`
            (`${ListeningStat.MEDIA_ITEM_ID_COLUMN}`, `${ListeningStat.DAY_EPOCH_COLUMN}`, `${ListeningStat.MS_PLAYED_COLUMN}`)
        VALUES (:mediaItemId, :dayEpoch, :msPlayed)
        """
    )
    fun restoreRow(mediaItemId: Long, dayEpoch: Long, msPlayed: Long)

    /**
     * Every row, unfiltered - used only for exporting a backup snapshot (see
     * ListeningStatsBackupUtils), not by the stats screen itself.
     */
    @Query("SELECT * FROM `$LISTENING_STAT_TABLE_NAME`")
    fun getAllRows(): List<ListeningStat>

    /** Wipes all recorded listening time. Backups on disk are left untouched. */
    @Query("DELETE FROM `$LISTENING_STAT_TABLE_NAME`")
    fun deleteAll()
}
