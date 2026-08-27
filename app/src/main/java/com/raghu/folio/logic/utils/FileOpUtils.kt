package com.raghu.folio.logic.utils

import android.content.SharedPreferences
import androidx.core.content.edit
import com.raghu.folio.ui.adapters.AlbumAdapter
import com.raghu.folio.ui.adapters.ArtistAdapter
import com.raghu.folio.ui.adapters.BaseAdapter
import com.raghu.folio.ui.adapters.DateAdapter
import com.raghu.folio.ui.adapters.GenreAdapter
import com.raghu.folio.ui.adapters.PlaylistAdapter
import com.raghu.folio.ui.adapters.SongAdapter

object FileOpUtils {
    fun getAdapterType(adapter: BaseAdapter<*>) =
        when (adapter) {
            is AlbumAdapter -> {
                0
            }

            is ArtistAdapter -> {
                1
            }

            is DateAdapter -> {
                2
            }

            is GenreAdapter -> {
                3
            }

            is PlaylistAdapter -> {
                4
            }

            is SongAdapter -> {
                5
            }

            else -> {
                throw IllegalArgumentException()
            }
        }

    fun readHashMapFromSharedPreferences(
        sharedPreferences: SharedPreferences,
        key: String
    ): HashMap<String, Boolean> {
        val stringSet = sharedPreferences.getStringSet(key, HashSet()) ?: HashSet()
        val hashMap = HashMap<String, Boolean>()
        for (item in stringSet) {
            val keyValue = item.split(":")
            if (keyValue.size == 2) {
                hashMap[keyValue[0]] = keyValue[1].toBoolean()
            }
        }
        return hashMap
    }

    fun writeHashMapToSharedPreferences(
        sharedPreferences: SharedPreferences,
        key: String,
        hashMap: HashMap<String, Boolean>
    ) {
        val stringSet = HashSet<String>()
        for (entry in hashMap.entries) {
            stringSet.add("${entry.key}:${entry.value}")
        }
        sharedPreferences.edit { putStringSet(key, stringSet) }
    }
}