package com.raghu.folio.logic.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

const val LISTENING_STAT_TABLE_NAME = "listeningStatTable"

// One row per calendar day (device-local, "yyyy-MM-dd") that had any listening, accumulating
// wall-clock milliseconds spent with the player in a playing state that day. Used for simple
// "time listened" / streak stats - not tied to any particular book.
@Entity(
    tableName = LISTENING_STAT_TABLE_NAME,
    primaryKeys = [ListeningStat.DATE_COLUMN],
)
data class ListeningStat(
    @ColumnInfo(name = DATE_COLUMN)
    val date: String,
    @ColumnInfo(name = MS_LISTENED_COLUMN)
    val msListened: Long,
) {
    companion object {
        const val DATE_COLUMN = "date"
        const val MS_LISTENED_COLUMN = "msListened"
    }
}
