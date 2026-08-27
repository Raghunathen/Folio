package com.raghu.folio.logic.utils.audiobook

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.raghu.folio.logic.comparators.AlphaNumericComparator
import com.raghu.folio.logic.data.db.AppDatabase
import com.raghu.folio.logic.data.db.dao.AuthorDao
import com.raghu.folio.logic.data.db.dao.BookDao
import com.raghu.folio.logic.data.db.entity.Author
import com.raghu.folio.logic.data.db.entity.Book
import com.raghu.folio.logic.data.db.entity.BookPart
import com.raghu.folio.logic.data.db.entity.Chapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private val AUDIO_EXTENSIONS = setOf("mp4", "m4b", "flac", "wav")
private val M4B_EXTENSIONS = setOf("mp4", "m4b")

data class ScanSummary(val authors: Int, val books: Int, val parts: Int)

/**
 * Walks the user-picked `Audiobooks/` SAF tree exactly two levels deep (`Author/` -> `Book/`),
 * reads embedded audio duration + any sidecar metadata for each book, detects series from sibling
 * folder names, and upserts everything into [AppDatabase]. Safe to re-run - existing
 * authors/books are matched by their (stable) folder URI and updated in place, and anything no
 * longer present on disk is removed.
 */
object AudiobookScanner {
    private val partNameComparator = AlphaNumericComparator()

    suspend fun scanLibrary(context: Context): ScanSummary = withContext(Dispatchers.IO) {
        val rootUri = AudiobookLibraryPrefs.getRootUri(context)
            ?: return@withContext ScanSummary(0, 0, 0)
        val root = DocumentFile.fromTreeUri(context, rootUri)?.takeIf { it.isDirectory }
            ?: return@withContext ScanSummary(0, 0, 0)

        val db = AppDatabase.getInstance(context)
        val authorDao = db.authorDao()
        val bookDao = db.bookDao()
        val partDao = db.bookPartDao()
        val chapterDao = db.chapterDao()
        val contentResolver = context.contentResolver

        val now = System.currentTimeMillis()
        val keptAuthorIds = mutableListOf<Long>()
        val keptBookIds = mutableListOf<Long>()
        var bookCount = 0
        var partCount = 0

        for (authorFolder in root.listFiles().filter { it.isDirectory }) {
            val authorName = authorFolder.name?.trim().takeUnless { it.isNullOrBlank() } ?: continue
            val authorId = upsertAuthor(authorDao, authorFolder.uri.toString(), authorName, now)
            keptAuthorIds += authorId

            // Same DocumentFile instances are reused as map keys below (identity-based), so parse
            // all sibling book names up-front - that's what lets us tell a real series ("Mistborn
            // 1", "Mistborn 2", ...) apart from a single book whose title just contains a number.
            val bookFolders = authorFolder.listFiles().filter { it.isDirectory }
            val parsedByFolder = bookFolders.associateWith { SeriesDetector.parse(it.name.orEmpty()) }
            val seriesByFolder = SeriesDetector.detectSeries(parsedByFolder)

            for (bookFolder in bookFolders) {
                val audioFiles = bookFolder.listFiles()
                    .filter { it.isFile && isAudioFile(it.name) }
                    .sortedWith { a, b -> partNameComparator.compare(a.name, b.name) }
                if (audioFiles.isEmpty()) continue

                val sidecar = SidecarMetadataReader.read(contentResolver, bookFolder)
                val parsed = parsedByFolder.getValue(bookFolder)
                val series = seriesByFolder[bookFolder]

                val title = sidecar.title ?: parsed.cleanTitle
                val seriesName = sidecar.seriesName ?: series?.first
                val seriesIndex = sidecar.seriesIndex ?: series?.second

                var offsetMs = 0L
                val parts = audioFiles.mapIndexed { index, file ->
                    val durationMs = readDurationMs(context, file.uri)
                    BookPart(
                        bookId = 0L, // filled in once the owning Book row has an id
                        fileUri = file.uri.toString(),
                        partIndex = index,
                        title = file.name?.substringBeforeLast('.') ?: "Part ${index + 1}",
                        durationMs = durationMs,
                        startOffsetMs = offsetMs,
                    ).also { offsetMs += durationMs }
                }

                val book = Book(
                    authorId = authorId,
                    title = title,
                    sortTitle = sortKey(title),
                    narrator = sidecar.narrator,
                    seriesName = seriesName,
                    seriesIndex = seriesIndex,
                    description = sidecar.description,
                    coverUri = sidecar.coverUri,
                    folderUri = bookFolder.uri.toString(),
                    durationMs = offsetMs,
                    dateAdded = now,
                    dateModified = now,
                )
                val bookId = upsertBook(bookDao, book)
                keptBookIds += bookId
                bookCount++

                // NOTE(v1 limitation): parts are fully replaced on every scan rather than
                // diffed-in-place, so a BookPart's id (and anything referencing it, like
                // PlaybackProgress.currentPartId) is not stable across rescans of an already
                // in-progress book. Acceptable for now since rescans only happen on explicit user
                // action; revisit once the scanner is wired into a background refresh flow.
                partDao.deletePartsForBook(bookId)
                chapterDao.deleteChaptersForBook(bookId)
                val chapters = mutableListOf<Chapter>()
                if (parts.size == 1 && isM4bLike(audioFiles[0].name)) {
                    val firstPart = parts[0].copy(bookId = bookId)
                    val partId = partDao.insertPart(firstPart)
                    val entries = M4bChapterParser.parseChapters(contentResolver, audioFiles[0].uri)
                    entries.forEachIndexed { index, entry ->
                        val end = entries.getOrNull(index + 1)?.startMs ?: offsetMs
                        if (end > entry.startMs) {
                            chapters += Chapter(
                                bookId = bookId,
                                partId = partId,
                                title = entry.title.ifBlank { "Chapter ${index + 1}" },
                                startMs = entry.startMs,
                                endMs = end,
                            )
                        }
                    }
                } else {
                    partDao.insertParts(parts.map { it.copy(bookId = bookId) })
                }
                partCount += parts.size
                if (chapters.isNotEmpty()) chapterDao.insertChapters(chapters)
            }
        }

        authorDao.deleteAuthorsNotIn(keptAuthorIds)
        bookDao.deleteBooksNotIn(keptBookIds)

        ScanSummary(keptAuthorIds.size, bookCount, partCount)
    }

    private fun isAudioFile(name: String?): Boolean =
        name?.substringAfterLast('.', "")?.lowercase(Locale.ROOT) in AUDIO_EXTENSIONS

    private fun isM4bLike(name: String?): Boolean =
        name?.substringAfterLast('.', "")?.lowercase(Locale.ROOT) in M4B_EXTENSIONS

    private fun upsertAuthor(dao: AuthorDao, folderUri: String, name: String, now: Long): Long {
        dao.getAuthorByFolderUri(folderUri)?.let { return it.authorId }
        val author = Author(
            name = name,
            sortName = sortKey(name),
            imageUri = null,
            folderUri = folderUri,
            createdAt = now,
        )
        return dao.insertAuthor(author)
    }

    private fun upsertBook(dao: BookDao, book: Book): Long {
        val existing = dao.getBookByFolderUri(book.folderUri)
        return if (existing != null) {
            dao.updateBook(book.copy(bookId = existing.bookId, dateAdded = existing.dateAdded))
            existing.bookId
        } else {
            dao.insertBook(book)
        }
    }

    /** Lowercased, with a leading English article stripped, for shelf/list ordering. */
    private fun sortKey(name: String): String =
        name.trim()
            .replace(Regex("^(the|a|an)\\s+", RegexOption.IGNORE_CASE), "")
            .lowercase(Locale.getDefault())

    private fun readDurationMs(context: Context, uri: Uri): Long = runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        }
    }.getOrNull() ?: 0L
}
