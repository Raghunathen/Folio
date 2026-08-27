package com.raghu.folio.logic.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.raghu.folio.logic.data.db.entity.PLAYBACK_PROGRESS_TABLE_NAME
import com.raghu.folio.logic.data.db.entity.PlaybackProgress

@Dao
interface PlaybackProgressDao {
    @Query(
        """
        INSERT INTO `$PLAYBACK_PROGRESS_TABLE_NAME`
            (`${PlaybackProgress.BOOK_ID_COLUMN}`, `${PlaybackProgress.POSITION_MS_COLUMN}`,
             `${PlaybackProgress.CURRENT_PART_ID_COLUMN}`, `${PlaybackProgress.PLAYBACK_SPEED_COLUMN}`,
             `${PlaybackProgress.LAST_PLAYED_AT_COLUMN}`, `${PlaybackProgress.IS_FINISHED_COLUMN}`,
             `${PlaybackProgress.FINISHED_AT_COLUMN}`)
        VALUES (:bookId, :positionMs, :currentPartId, :playbackSpeed, :lastPlayedAt, :isFinished, :finishedAt)
        ON CONFLICT(`${PlaybackProgress.BOOK_ID_COLUMN}`) DO UPDATE SET
            `${PlaybackProgress.POSITION_MS_COLUMN}` = :positionMs,
            `${PlaybackProgress.CURRENT_PART_ID_COLUMN}` = :currentPartId,
            `${PlaybackProgress.PLAYBACK_SPEED_COLUMN}` = :playbackSpeed,
            `${PlaybackProgress.LAST_PLAYED_AT_COLUMN}` = :lastPlayedAt,
            `${PlaybackProgress.IS_FINISHED_COLUMN}` = :isFinished,
            `${PlaybackProgress.FINISHED_AT_COLUMN}` = :finishedAt
        """
    )
    fun upsertProgress(
        bookId: Long,
        positionMs: Long,
        currentPartId: Long?,
        playbackSpeed: Float,
        lastPlayedAt: Long,
        isFinished: Boolean,
        finishedAt: Long?,
    )

    @Query("SELECT * FROM `$PLAYBACK_PROGRESS_TABLE_NAME` WHERE `${PlaybackProgress.BOOK_ID_COLUMN}` = :bookId")
    fun getProgress(bookId: Long): PlaybackProgress?

    @Query(
        """
        UPDATE `$PLAYBACK_PROGRESS_TABLE_NAME`
        SET `${PlaybackProgress.IS_FINISHED_COLUMN}` = :isFinished, `${PlaybackProgress.FINISHED_AT_COLUMN}` = :finishedAt
        WHERE `${PlaybackProgress.BOOK_ID_COLUMN}` = :bookId
        """
    )
    fun setFinished(bookId: Long, isFinished: Boolean, finishedAt: Long?)
}
