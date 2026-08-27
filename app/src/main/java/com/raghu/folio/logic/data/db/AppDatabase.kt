package com.raghu.folio.logic.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.raghu.folio.logic.data.db.dao.ListeningStatDao
import com.raghu.folio.logic.data.db.dao.MediaItemDao
import com.raghu.folio.logic.data.db.dao.PlaylistDao
import com.raghu.folio.logic.data.db.entity.LISTENING_STAT_TABLE_NAME
import com.raghu.folio.logic.data.db.entity.ListeningStat
import com.raghu.folio.logic.data.db.entity.MediaItem
import com.raghu.folio.logic.data.db.entity.Playlist
import com.raghu.folio.logic.data.db.entity.PlaylistMediaItemCrossRef

const val APP_DATABASE_FILE_NAME = "app.db"

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `$LISTENING_STAT_TABLE_NAME` (
                `${ListeningStat.MEDIA_ITEM_ID_COLUMN}` INTEGER NOT NULL,
                `${ListeningStat.DAY_EPOCH_COLUMN}` INTEGER NOT NULL,
                `${ListeningStat.MS_PLAYED_COLUMN}` INTEGER NOT NULL,
                PRIMARY KEY(`${ListeningStat.MEDIA_ITEM_ID_COLUMN}`, `${ListeningStat.DAY_EPOCH_COLUMN}`)
            )
            """
        )
    }
}

@Database(
    entities = [
        Playlist::class,
        MediaItem::class,
        PlaylistMediaItemCrossRef::class,
        ListeningStat::class,
    ],
    version = 2,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun playlistDao(): PlaylistDao
    abstract fun mediaItemDao(): MediaItemDao
    abstract fun listeningStatDao(): ListeningStatDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    APP_DATABASE_FILE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .apply { instance = this }
            }
        }
    }
}
