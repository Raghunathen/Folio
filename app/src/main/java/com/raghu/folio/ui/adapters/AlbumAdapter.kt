/*
 *     Copyright (C) 2024 Akane Foundation
 *
 *     Folio is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Folio is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.raghu.folio.ui.adapters

import android.net.Uri
import androidx.activity.viewModels
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import com.raghu.folio.R
import com.raghu.folio.logic.findBaseWrapperFragment
import com.raghu.folio.logic.queueNext
import com.raghu.folio.logic.utils.MediaStoreUtils
import com.raghu.folio.ui.LibraryViewModel
import com.raghu.folio.ui.fragments.GeneralSubFragment

class AlbumAdapter(
    fragment: Fragment,
    albumList: MutableLiveData<List<MediaStoreUtils.Album>>?,
    ownsView: Boolean = true,
    isSubFragment: Boolean = false,
    fallbackSpans: Int = 1
) : BaseAdapter<MediaStoreUtils.Album>
    (
    fragment,
    liveData = albumList,
    sortHelper = StoreAlbumHelper(),
    naturalOrderHelper = null,
    initialSortType = Sorter.Type.ByTitleAscending,
    pluralStr = R.plurals.albums,
    ownsView = ownsView,
    defaultLayoutType = LayoutType.GRID,
    isSubFragment = isSubFragment,
    fallbackSpans = fallbackSpans
) {

    private val libraryViewModel: LibraryViewModel by mainActivity.viewModels()

    constructor(
        fragment: Fragment,
        albumList: List<MediaStoreUtils.Album>,
        isSubFragment: Boolean = false,
        fallbackSpans: Int = 1
    ) : this(
        fragment,
        null,
        false,
        isSubFragment = isSubFragment,
        fallbackSpans = fallbackSpans
    ) {
        updateList(albumList, now = true, false)
    }

    override fun virtualTitleOf(item: MediaStoreUtils.Album): String {
        return context.getString(R.string.unknown_album)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        super.onBindViewHolder(holder, position, payloads)
        if (layoutType == LayoutType.GRID) {
            val item = list[position]
            holder.itemView.setOnLongClickListener {
                val popupMenu = PopupMenu(it.context, it)
                onMenu(item, popupMenu)
                popupMenu.show()
                true
            }
        }
    }

    override fun onClick(item: MediaStoreUtils.Album) {
        fragment!!.findBaseWrapperFragment()!!.replaceFragment(GeneralSubFragment()) {
            putInt("Position", item.let {
                if (ownsView) toRawPos(it) else {
                    libraryViewModel.albumItemList.value!!.indexOf(it)
                }
            })
            putInt("Item", R.id.album)
        }
    }

    override fun onMenu(item: MediaStoreUtils.Album, popupMenu: PopupMenu) {
        popupMenu.inflate(R.menu.more_menu_less)

        popupMenu.setOnMenuItemClickListener { it1 ->
            when (it1.itemId) {
                R.id.play_next -> {
                    mainActivity.getPlayer()?.queueNext(item.songList)
                }

                /*
				R.id.share -> {
					val builder = ShareCompat.IntentBuilder(mainActivity)
					val mimeTypes = mutableSetOf<String>()
					builder.addStream(viewModel.fileUriList.value?.get(songList[holder.bindingAdapterPosition].mediaId.toLong())!!)
					mimeTypes.add(viewModel.mimeTypeList.value?.get(songList[holder.bindingAdapterPosition].mediaId.toLong())!!)
					builder.setType(mimeTypes.singleOrNull() ?: "audio/*").startChooser()
				 } */
				 */
            }
            true
        }
    }

    class StoreAlbumHelper : StoreItemHelper<MediaStoreUtils.Album>(
        setOf(
            Sorter.Type.ByTitleDescending, Sorter.Type.ByTitleAscending,
            Sorter.Type.ByArtistDescending, Sorter.Type.ByArtistAscending,
            Sorter.Type.BySizeDescending, Sorter.Type.BySizeAscending
        )
    ) {
        override fun getArtist(item: MediaStoreUtils.Album): String? {
            return item.artist
        }

        override fun getCover(item: MediaStoreUtils.Album): Uri? {
            return item.cover ?: super.getCover(item)
        }
    }
}
