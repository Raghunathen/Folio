package com.raghu.folio.logic.utils.audiobook

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import androidx.preference.PreferenceManager

/** Persists which SAF tree the user picked as their `Audiobooks/` root folder. */
object AudiobookLibraryPrefs {
    private const val KEY_ROOT_URI = "audiobooks_root_uri"

    fun getRootUri(context: Context): Uri? =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getString(KEY_ROOT_URI, null)?.let { Uri.parse(it) }

    /** Call after receiving a tree [treeUri] back from an `ACTION_OPEN_DOCUMENT_TREE` request. */
    fun setRootUri(context: Context, treeUri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            putString(KEY_ROOT_URI, treeUri.toString())
        }
    }

    fun clearRootUri(context: Context) {
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            remove(KEY_ROOT_URI)
        }
    }
}
