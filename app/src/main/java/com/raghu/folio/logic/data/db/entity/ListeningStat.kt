package com.raghu.folio.logic.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

const val LISTENING_STAT_TABLE_NAME = "listeningStatTable"

// One row per (song, day) - msPlayed accumulates across however many times that song was played
// that day. Bucketing by day (instead of one row per playback session) keeps the table small and
// the period queries (week/month/year/all) a single indexed SUM() instead of scanning every
// session ever recorded.
@Entity(
    tableName = LISTENING_STAT_TABLE_NAME,
    primaryKeys = [
        ListeningStat.MEDIA_ITEM_ID_COLUMN,
        ListeningStat.DAY_EPOCH_COLUMN,
    ]
)
data class ListeningStat(
    @ColumnInfo(name = MEDIA_ITEM_ID_COLUMN)
    val mediaItemId: Long,
    @ColumnInfo(name = DAY_EPOCH_COLUMN)
    val dayEpoch: Long,
    @ColumnInfo(name = MS_PLAYED_COLUMN)
    val msPlayed: Long,
) {
    companion object {
        const val MEDIA_ITEM_ID_COLUMN = "mediaItemId"
        const val DAY_EPOCH_COLUMN = "dayEpoch"
        const val MS_PLAYED_COLUMN = "msPlayed"
    }
}
