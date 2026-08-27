package com.raghu.folio.logic.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.raghu.folio.logic.data.db.AppDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "ListeningStatsBackup"
private const val PREF_BACKUP_TREE = "listening_stats_backup_tree"
private const val BACKUP_MIME_TYPE = "application/json"

const val BACKUP_FILE_PREFIX = "Music_ListeningStats_"
const val LISTENING_STATS_BACKUP_DIR = "Music/stats"

/**
 * Backs the listening-stats table up to timestamped JSON files in a folder the user picks (we
 * point the picker at Music/stats), and restores from the newest one. The folder sits beside the
 * music library rather than in app storage, so backups survive an uninstall or a "clear data" -
 * exactly the events that wipe the stats database.
 *
 * This goes through the Storage Access Framework rather than MediaStore for a concrete reason:
 * MediaStore refuses to create non-media files outside Download/ and Documents/, so writing a
 * .json into Music/ fails outright no matter how it is phrased. Scoped storage likewise blocks
 * plain file writes there. A persisted tree permission is the only route that actually lands the
 * file in Music/stats, and it costs the user a single one-time folder confirmation.
 *
 * Each backup is a new timestamped file rather than one overwritten file, so a bad restore is
 * never a one-way door.
 */
object ListeningStatsBackupUtils {

    /** Where the picker should open, so confirming the folder is one tap. */
    fun initialPickerUri(): Uri = DocumentsContract.buildDocumentUri(
        "com.android.externalstorage.documents", "primary:$LISTENING_STATS_BACKUP_DIR"
    )

    /** The folder previously chosen, if we still hold permission for it. */
    fun savedFolder(context: Context): Uri? {
        val raw = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(PREF_BACKUP_TREE, null) ?: return null
        val uri = raw.toUri()
        val held = context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isWritePermission
        }
        return if (held) uri else null
    }

    /** Remembers the folder and keeps access to it across restarts. */
    fun rememberFolder(context: Context, treeUri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: Exception) {
            Log.e(TAG, "Could not persist folder permission", e)
        }
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            putString(PREF_BACKUP_TREE, treeUri.toString())
        }
    }

    private fun childrenUri(treeUri: Uri): Uri = DocumentsContract.buildChildDocumentsUriUsingTree(
        treeUri, DocumentsContract.getTreeDocumentId(treeUri)
    )

    /**
     * Clears every recorded minute. Files already written to the backup folder are deliberately
     * left alone, so a reset can still be undone by loading one - but only when the user explicitly
     * asks for it: nothing restores on its own.
     */
    suspend fun resetStats(context: Context) = withContext(Dispatchers.IO) {
        AppDatabase.getInstance(context).listeningStatDao().deleteAll()
    }

    /** True when there is nothing recorded - used to skip pointless automatic backups. */
    suspend fun hasNoStats(context: Context): Boolean = withContext(Dispatchers.IO) {
        AppDatabase.getInstance(context).listeningStatDao().getAllRows().isEmpty()
    }

    /** Writes a new timestamped backup into [treeUri]. */
    suspend fun backupTo(context: Context, treeUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val rows = AppDatabase.getInstance(context).listeningStatDao().getAllRows()
            val json = JSONArray()
            for (row in rows) {
                json.put(JSONObject().apply {
                    put("mediaItemId", row.mediaItemId)
                    put("dayEpoch", row.dayEpoch)
                    put("msPlayed", row.msPlayed)
                })
            }
            val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
            val parent = DocumentsContract.buildDocumentUriUsingTree(
                treeUri, DocumentsContract.getTreeDocumentId(treeUri)
            )
            val file = DocumentsContract.createDocument(
                context.contentResolver, parent, BACKUP_MIME_TYPE, "$BACKUP_FILE_PREFIX$stamp"
            ) ?: return@withContext false
            context.contentResolver.openOutputStream(file, "w")?.use {
                it.write(json.toString().toByteArray())
            } ?: return@withContext false
            true
        } catch (e: Exception) {
            Log.e(TAG, "Backup failed", e)
            false
        }
    }

    /** The newest backup in [treeUri], or null if the folder holds none. */
    private fun newestBackupIn(context: Context, treeUri: Uri): Uri? {
        try {
            context.contentResolver.query(
                childrenUri(treeUri),
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED
                ),
                null, null, null
            )?.use { cursor ->
                var bestId: String? = null
                var bestTime = Long.MIN_VALUE
                while (cursor.moveToNext()) {
                    val name = cursor.getString(1) ?: continue
                    if (!name.startsWith(BACKUP_FILE_PREFIX)) continue
                    val modified = cursor.getLong(2)
                    if (modified >= bestTime) {
                        bestTime = modified
                        bestId = cursor.getString(0)
                    }
                }
                return bestId?.let { DocumentsContract.buildDocumentUriUsingTree(treeUri, it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not list backups", e)
        }
        return null
    }

    /**
     * Restores the newest backup in [treeUri]. Returns the number of rows restored, or -1 when the
     * folder holds no readable backup.
     */
    suspend fun restoreLatestFrom(context: Context, treeUri: Uri): Int = withContext(Dispatchers.IO) {
        val uri = newestBackupIn(context, treeUri) ?: return@withContext -1
        restoreFromUri(context, uri)
    }

    /**
     * Restores from any JSON the user picked. Rows replace whatever is currently recorded for the
     * same (song, day) rather than adding to it - restoring is meant to bring back a lost state,
     * not stack on top of live data, so running it twice is harmless.
     */
    suspend fun restoreFromUri(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        try {
            val text = context.contentResolver.openInputStream(uri)
                ?.use { it.readBytes().decodeToString() } ?: return@withContext -1
            val json = JSONArray(text)
            val dao = AppDatabase.getInstance(context).listeningStatDao()
            for (i in 0 until json.length()) {
                val obj = json.getJSONObject(i)
                dao.restoreRow(
                    obj.getLong("mediaItemId"), obj.getLong("dayEpoch"), obj.getLong("msPlayed")
                )
            }
            json.length()
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed", e)
            -1
        }
    }
}
