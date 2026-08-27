package com.raghu.folio.logic.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.raghu.folio.logic.data.db.dao.AuthorDao
import com.raghu.folio.logic.data.db.dao.BookDao
import com.raghu.folio.logic.data.db.dao.BookPartDao
import com.raghu.folio.logic.data.db.dao.BookmarkDao
import com.raghu.folio.logic.data.db.dao.ChapterDao
import com.raghu.folio.logic.data.db.dao.CollectionDao
import com.raghu.folio.logic.data.db.dao.ListeningStatDao
import com.raghu.folio.logic.data.db.dao.PlaybackProgressDao
import com.raghu.folio.logic.data.db.entity.Author
import com.raghu.folio.logic.data.db.entity.Book
import com.raghu.folio.logic.data.db.entity.BookPart
import com.raghu.folio.logic.data.db.entity.Bookmark
import com.raghu.folio.logic.data.db.entity.Chapter
import com.raghu.folio.logic.data.db.entity.Collection
import com.raghu.folio.logic.data.db.entity.CollectionBookCrossRef
import com.raghu.folio.logic.data.db.entity.ListeningStat
import com.raghu.folio.logic.data.db.entity.PlaybackProgress

const val APP_DATABASE_FILE_NAME = "app.db"

@Database(
    entities = [
        Author::class,
        Book::class,
        BookPart::class,
        Chapter::class,
        PlaybackProgress::class,
        Bookmark::class,
        Collection::class,
        CollectionBookCrossRef::class,
        ListeningStat::class,
    ],
    version = 5,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun authorDao(): AuthorDao
    abstract fun bookDao(): BookDao
    abstract fun bookPartDao(): BookPartDao
    abstract fun chapterDao(): ChapterDao
    abstract fun playbackProgressDao(): PlaybackProgressDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun collectionDao(): CollectionDao
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
                    // Pre-release app (no installs to preserve yet) - avoid hand-writing a
                    // migration for every schema tweak while the audiobook data model is still
                    // being designed. Also drops the old music-era tables (Playlist/MediaItem/
                    // ListeningStat), which have no audiobook equivalent to migrate into.
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .apply { instance = this }
            }
        }
    }
}

