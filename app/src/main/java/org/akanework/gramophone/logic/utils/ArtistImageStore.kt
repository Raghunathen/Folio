package org.akanework.gramophone.logic.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.SystemClock
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import java.io.File
import java.util.Locale

/**
 * Resolves a real artist photo from local storage, so artist pages can show actual artist imagery
 * (eg. downloaded from fanart.tv or MusicBrainz) instead of falling back to one of their album
 * covers. Nothing is ever fetched here - the user supplies the files, this only finds them.
 *
 * Drop images named after the artist ("A.R. Rahman.jpg") into Music/artists, and nowhere else, so
 * there is exactly one place to manage them.
 *
 * There are two ways in, and the folder grant is the one that always works:
 *
 * - A folder grant (see [rememberTree]) reads the folder through the documents provider, which
 *   answers from the filesystem and neither knows nor cares what MediaStore has indexed.
 * - Failing that, plain file reads, which need the images permission.
 *
 * The plain-file route quietly stops working once the folder holds a .nomedia file - the usual way
 * to keep these photos out of gallery apps. MediaProvider drops the folder's contents from its
 * index and then filters what an app is shown down to what it has indexed, so the listing comes
 * back empty and even opening a known filename is refused. Nothing the app can do about that from
 * this side, hence the grant.
 *
 * Either way the listing is cached as a name -> uri map, so opening an artist page is a hash lookup
 * rather than I/O, and re-read when the folder changes. Swapping an image out is picked up without
 * restarting the app.
 */
object ArtistImageStore {
    private const val TAG = "ArtistImageStore"
    private const val PREF_TREE = "artist_images_tree"
    private val extensions = listOf("jpg", "jpeg", "png", "webp")

    /** Music/artists, as the documents provider names it - where the folder picker opens. */
    const val ARTIST_IMAGES_DIR = "Music/artists"

    @Volatile
    private var index: Map<String, Uri>? = null

    // The folder's own timestamp when the index was built. Adding, removing or renaming a file
    // changes it, so comparing against it picks up new artist images without a restart while
    // still costing one stat() per lookup rather than a fresh directory listing. Only meaningful
    // for the plain-file route; a granted folder is re-read on the timer below instead, there
    // being no equally cheap way to ask a documents provider "has anything changed".
    @Volatile
    private var indexedAt = Long.MIN_VALUE

    // Throttles how often the folder is re-read - see imageUriFor.
    private const val FOLDER_RECHECK_MS = 1000L
    private const val TREE_RECHECK_MS = 5000L

    @Volatile
    private var lastCheckedAt = 0L

    // Music/artists rather than Music itself: it keeps the music folder clean, and it keeps these
    // images away from the album-cover scanner, which treats any image sitting in a song's own
    // folder as a candidate cover. It also survives reinstalls, unlike /Android/data.
    private fun artistsDir(): File {
        @Suppress("DEPRECATION")
        val music = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        return File(music, ARTIST_IMAGES_DIR.substringAfter('/'))
    }

    private fun normalize(name: String) = name.trim().lowercase(Locale.ROOT)

    /** Where the folder picker should open, so the user isn't left to find Music/artists. */
    fun initialPickerUri(): Uri = DocumentsContract.buildDocumentUri(
        "com.android.externalstorage.documents", "primary:$ARTIST_IMAGES_DIR"
    )

    /**
     * The granted folder, or null if there isn't one. Checked against the permissions actually
     * held rather than trusted from preferences - a grant can be revoked from system settings, or
     * lost when the app's data is cleared, and a stale one would silently resolve nothing.
     */
    fun savedTree(context: Context): Uri? {
        val stored = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(PREF_TREE, null)?.toUri() ?: return null
        val held = try {
            context.contentResolver.persistedUriPermissions.any {
                it.isReadPermission && it.uri == stored
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cannot read persisted permissions", e)
            false
        }
        return if (held) stored else null
    }

    /** Records the folder the user picked, and takes read access that survives a reboot. */
    fun rememberTree(context: Context, tree: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                tree, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot persist $tree", e)
            return
        }
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit { putString(PREF_TREE, tree.toString()) }
        invalidate()
    }

    /** Human-readable name of the granted folder, for the settings entry. */
    fun savedTreeLabel(context: Context): String? =
        savedTree(context)?.let { DocumentsContract.getTreeDocumentId(it).substringAfter(':') }

    private fun buildTreeIndex(context: Context, tree: Uri): Map<String, Uri> {
        val map = HashMap<String, Uri>()
        try {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(
                tree, DocumentsContract.getTreeDocumentId(tree)
            )
            context.contentResolver.query(
                children,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                ), null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(1) ?: continue
                    val dot = name.lastIndexOf('.')
                    if (dot <= 0) continue
                    if (name.substring(dot + 1).lowercase(Locale.ROOT) !in extensions) continue
                    val uri = DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0))
                        // See below - the timestamp rides along so a replaced image re-decodes.
                        .buildUpon().fragment(cursor.getLong(2).toString()).build()
                    map.putIfAbsent(normalize(name.substring(0, dot)), uri)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed listing $tree", e)
        }
        return map
    }

    private fun buildFileIndex(): Map<String, Uri> {
        val map = HashMap<String, Uri>()
        val dir = artistsDir()
        try {
            if (!dir.isDirectory) return map
            dir.listFiles()?.forEach { file ->
                if (!file.isFile) return@forEach
                if (file.extension.lowercase(Locale.ROOT) !in extensions) return@forEach
                map.putIfAbsent(
                    normalize(file.nameWithoutExtension),
                    file.toUri().buildUpon().fragment(file.lastModified().toString()).build()
                )
            }
        } catch (e: SecurityException) {
            // Expected when the images permission isn't granted; callers just fall back to album
            // art, so this isn't worth surfacing to the user.
            Log.d(TAG, "Cannot read $dir", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed listing $dir", e)
        }
        return map
    }

    /**
     * A local photo for [artistName] if one has been placed on the device, otherwise [fallback]
     * (normally one of the artist's album covers).
     *
     * The returned uri carries the file's timestamp as a fragment. Replacing an image in place
     * leaves its path identical, so without that the image loader would keep serving the bitmap it
     * already cached under that path and the new picture would never appear. A fragment is ignored
     * when resolving the file itself but does change the cache key.
     */
    fun imageUriFor(context: Context, artistName: String, fallback: Uri?): Uri? {
        // These lookups happen while binding list rows on the main thread, so the folder is only
        // re-read on a timer. Without the throttle a fast-rebinding list (search results re-query
        // on every keystroke) would hit storage for every single row.
        var idx = index
        val now = SystemClock.uptimeMillis()
        val tree = savedTree(context)
        val interval = if (tree != null) TREE_RECHECK_MS else FOLDER_RECHECK_MS
        if (idx == null || now - lastCheckedAt > interval) {
            val stamp = if (tree != null) Long.MIN_VALUE + 1 else
                try { artistsDir().lastModified() } catch (_: Exception) { 0L }
            synchronized(this) {
                if (index == null || indexedAt != stamp || tree != null) {
                    idx = (if (tree != null) buildTreeIndex(context, tree) else buildFileIndex())
                        .also { index = it }
                    indexedAt = stamp
                } else {
                    idx = index
                }
                lastCheckedAt = now
            }
        }
        return idx?.get(normalize(artistName)) ?: fallback
    }

    /** Forces the next lookup to re-scan, after the user adds or removes artist images. */
    fun invalidate() {
        lastCheckedAt = 0L
        index = null
        indexedAt = Long.MIN_VALUE
    }
}
