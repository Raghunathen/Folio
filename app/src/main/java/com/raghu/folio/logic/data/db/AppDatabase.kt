package com.raghu.folio.logic.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.raghu.folio.logic.data.db.dao.AuthorDao
import com.raghu.folio.logic.data.db.dao.BookDao
import com.raghu.folio.logic.data.db.dao.BookPartDao
import com.raghu.folio.logic.data.db.dao.BookmarkDao
import com.raghu.folio.logic.data.db.dao.ChapterDao
import com.raghu.folio.logic.data.db.dao.CollectionDao
import com.raghu.folio.logic.data.db.dao.ListeningStatDao
import com.raghu.folio.logic.data.db.dao.MediaItemDao
import com.raghu.folio.logic.data.db.dao.PlaybackProgressDao
import com.raghu.folio.logic.data.db.dao.PlaylistDao
import com.raghu.folio.logic.data.db.entity.Author
import com.raghu.folio.logic.data.db.entity.Book
import com.raghu.folio.logic.data.db.entity.BookPart
import com.raghu.folio.logic.data.db.entity.Bookmark
import com.raghu.folio.logic.data.db.entity.Chapter
import com.raghu.folio.logic.data.db.entity.Collection
import com.raghu.folio.logic.data.db.entity.CollectionBookCrossRef
import com.raghu.folio.logic.data.db.entity.LISTENING_STAT_TABLE_NAME
import com.raghu.folio.logic.data.db.entity.ListeningStat
import com.raghu.folio.logic.data.db.entity.MediaItem
import com.raghu.folio.logic.data.db.entity.PlaybackProgress
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

// TODO(pivot): MediaItem/Playlist/PlaylistMediaItemCrossRef (and their DAOs) are the old
// MediaStore-id-based music tables. They're kept alongside the new audiobook tables for now
// because the UI/adapters still reference them; they'll be deleted once the library/player UI is
// rewired to the Author/Book/BookPart model (see docs/PIVOT_NOTES.md).
@Database(
    entities = [
        Playlist::class,
        MediaItem::class,
        PlaylistMediaItemCrossRef::class,
        ListeningStat::class,
        Author::class,
        Book::class,
        BookPart::class,
        Chapter::class,
        PlaybackProgress::class,
        Bookmark::class,
        Collection::class,
        CollectionBookCrossRef::class,
    ],
    version = 3,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun playlistDao(): PlaylistDao
    abstract fun mediaItemDao(): MediaItemDao
    abstract fun listeningStatDao(): ListeningStatDao
    abstract fun authorDao(): AuthorDao
    abstract fun bookDao(): BookDao
    abstract fun bookPartDao(): BookPartDao
    abstract fun chapterDao(): ChapterDao
    abstract fun playbackProgressDao(): PlaybackProgressDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun collectionDao(): CollectionDao

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
                    // Pre-release app (no installs to preserve yet) - avoid hand-writing a
                    // migration for every schema tweak while the audiobook data model is still
                    // being designed.
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .apply { instance = this }
            }
        }
    }
}

