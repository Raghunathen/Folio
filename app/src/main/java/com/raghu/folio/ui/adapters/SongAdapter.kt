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
import android.os.Handler
import android.os.Looper
import androidx.activity.viewModels
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.ConcatAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.raghu.folio.R
import com.raghu.folio.logic.findBaseWrapperFragment
import com.raghu.folio.logic.queueNext
import com.raghu.folio.logic.ui.DefaultItemHeightHelper
import com.raghu.folio.logic.ui.ItemHeightHelper
import com.raghu.folio.logic.ui.showSongOptionsSheet
import com.raghu.folio.logic.utils.ArtistImageStore
import com.raghu.folio.logic.utils.SearchMatcher
import com.raghu.folio.ui.LibraryViewModel
import com.raghu.folio.ui.fragments.ArtistSubFragment
import com.raghu.folio.ui.fragments.DetailDialogFragment
import com.raghu.folio.ui.fragments.GeneralSubFragment


/**
 * [SongAdapter] is an adapter for displaying songs.
 */
class SongAdapter(
    fragment: Fragment,
    songList: MutableLiveData<List<MediaItem>>?,
    canSort: Boolean,
    helper: Sorter.NaturalOrderHelper<MediaItem>?,
    ownsView: Boolean,
    isSubFragment: Boolean = false,
    allowDiffUtils: Boolean = false,
    rawOrderExposed: Boolean = !isSubFragment,
    fallbackSpans: Int = 1
) : BaseAdapter<MediaItem>
    (
    fragment,
    liveData = songList,
    sortHelper = MediaItemHelper(),
    naturalOrderHelper = if (canSort) helper else null,
    initialSortType = if (canSort)
        (if (helper != null) Sorter.Type.NaturalOrder else
            (if (rawOrderExposed) Sorter.Type.NativeOrder else Sorter.Type.ByTitleAscending))
    else Sorter.Type.None,
    canSort = canSort,
    pluralStr = R.plurals.songs,
    ownsView = ownsView,
    defaultLayoutType = LayoutType.COMPACT_LIST,
    isSubFragment = isSubFragment,
    rawOrderExposed = rawOrderExposed,
    allowDiffUtils = allowDiffUtils,
    fallbackSpans = fallbackSpans
) {

    private val isInlineSearch = ownsView && !isSubFragment

    // Grid crashes for the songs list specifically (GridLayoutManager's extra-layout-space
    // calculation throws "Can't find desired adapter!" here, nested inside the tab ViewPager2).
    override val supportsGridLayout = false

    constructor(
        fragment: Fragment,
        songList: List<MediaItem>,
        canSort: Boolean,
        helper: Sorter.NaturalOrderHelper<MediaItem>?,
        ownsView: Boolean,
        isSubFragment: Boolean = false,
        allowDiffUtils: Boolean = false,
        rawOrderExposed: Boolean = !isSubFragment,
        fallbackSpans: Int = 1
    ) : this(
        fragment,
        null,
        canSort,
        helper,
        ownsView,
        isSubFragment,
        allowDiffUtils,
        rawOrderExposed,
        fallbackSpans
    ) {
        updateList(songList, now = true, false)
    }

    fun getSongList() = list

    fun getActivity() = mainActivity

    // Inline search bar (only in the top-level Songs tab, not sub-fragments)
    private var searchQuery = ""
    private var fullList: List<MediaItem>? = null
    private val searchHandler = Handler(Looper.getMainLooper())
    private var pendingSearchRunnable: Runnable? = null

    private val searchHeaderAdapter: SongSearchHeaderAdapter by lazy {
        SongSearchHeaderAdapter(context) { query ->
            searchQuery = query
            // Debounce rapid typing to avoid lock contention
            pendingSearchRunnable?.let { searchHandler.removeCallbacks(it) }
            val r = Runnable { applySearchOrFull() }
            pendingSearchRunnable = r
            searchHandler.postDelayed(r, 150)
        }
    }

    // Song text pre-squashed for searching (see SearchMatcher), rebuilt only when the library
    // changes. Doing it here instead of inside the filter keeps every keystroke to a plain
    // substring scan over ready-made strings, with no per-song allocation.
    private var searchIndex: List<String> = emptyList()

    private val searchEntityAdapter = SearchEntityAdapter()

    private fun applySearchOrFull() {
        val source = fullList ?: return
        val tokens = SearchMatcher.tokenize(searchQuery)
        val displayList = if (tokens.isEmpty()) source
        else source.filterIndexed { i, _ ->
            SearchMatcher.matches(searchIndex.getOrElse(i) { "" }, tokens)
        }
        if (tokens.isEmpty()) {
            searchEntityAdapter.submit(emptyList(), emptyList(), "", "", null)
        } else {
            searchEntityAdapter.submit(
                matchingArtists(tokens), matchingAlbums(tokens),
                context.getString(R.string.category_artists),
                context.getString(R.string.category_albums),
                if (displayList.isNotEmpty()) context.getString(R.string.category_songs) else null
            )
        }
        // The song count and play/shuffle buttons describe the whole library, not the results, so
        // they only get in the way while searching.
        decorAdapter.hidden = tokens.isNotEmpty()
        updateList(displayList, now = true, canDiff = true)
    }

    /**
     * Artists and albums whose own name matches the query, surfaced above the song results the way
     * Apple Music does. Capped because these are meant to be a shortcut to the right page, not a
     * second list to scroll through.
     */
    private fun matchingArtists(tokens: List<String>): List<SearchEntityAdapter.Entity> {
        return viewModel.albumArtistItemList.value.orEmpty()
            .filter { SearchMatcher.matches(SearchMatcher.squash(it.title ?: ""), tokens) }
            .take(3)
            .map { artist ->
                SearchEntityAdapter.Entity(
                    title = artist.title ?: context.getString(R.string.unknown_artist),
                    subtitle = context.getString(R.string.dialog_artist),
                    imageUri = ArtistImageStore.imageUriFor(
                        context, artist.title ?: "",
                        fallback = artist.songList.firstOrNull()?.mediaMetadata?.artworkUri
                    ),
                    round = true
                ) {
                    val pos = viewModel.albumArtistItemList.value?.indexOf(artist) ?: -1
                    if (pos >= 0) fragment?.findBaseWrapperFragment()
                        ?.replaceFragment(ArtistSubFragment()) {
                            putInt("Position", pos)
                            putInt("Item", R.id.album_artist)
                            // Songs, same as "Go to artist" from a song's menu - the album grid is
                            // only for when that display type is explicitly chosen.
                            putBoolean("SongsOnly", true)
                        }
                }
            }
    }

    private fun matchingAlbums(tokens: List<String>): List<SearchEntityAdapter.Entity> {
        return viewModel.albumItemList.value.orEmpty()
            .filter { SearchMatcher.matches(SearchMatcher.squash(it.title ?: ""), tokens) }
            .take(3)
            .map { album ->
                SearchEntityAdapter.Entity(
                    title = album.title ?: context.getString(R.string.unknown_album),
                    subtitle = context.getString(R.string.dialog_album),
                    imageUri = album.songList.firstOrNull()?.mediaMetadata?.artworkUri,
                    round = false
                ) {
                    val pos = viewModel.albumItemList.value?.indexOf(album) ?: -1
                    if (pos >= 0) fragment?.findBaseWrapperFragment()
                        ?.replaceFragment(GeneralSubFragment()) {
                            putInt("Position", pos)
                            putInt("Item", R.id.album)
                        }
                }
            }
    }

    override fun onChanged(value: List<MediaItem>) {
        if (isInlineSearch) {
            fullList = value
            searchIndex = value.map { SearchMatcher.haystackOf(it) }
            pendingSearchRunnable?.let { searchHandler.removeCallbacks(it) }
            applySearchOrFull()
        } else {
            super.onChanged(value)
        }
    }

    // The search box plus however many artist/album result rows are currently shown - everything
    // sitting above the song rows, which the fast scroller and popup-letter maths offset by.
    override val extraHeaderCount: Int
        get() = if (isInlineSearch) 1 + searchEntityAdapter.itemCount else 0

    override val concatAdapter: ConcatAdapter by lazy {
        if (isInlineSearch)
            ConcatAdapter(searchHeaderAdapter, searchEntityAdapter, decorAdapter, this)
        else ConcatAdapter(decorAdapter, this)
    }

    override val itemHeightHelper: ItemHeightHelper by lazy {
        if (!isInlineSearch) {
            DefaultItemHeightHelper.concatItemHeightHelper(
                decorAdapter, { decorAdapter.itemCount }, this
            )
        } else {
            val combinedHeader = ItemHeightHelper { to ->
                val sh = searchHeaderAdapter.getItemHeightFromZeroTo(to.coerceAtMost(1))
                val dh = if (to > 1) decorAdapter.getItemHeightFromZeroTo(to - 1) else 0
                sh + dh
            }
            DefaultItemHeightHelper.concatItemHeightHelper(combinedHeader, { 2 }, this)
        }
    }

    private val viewModel: LibraryViewModel by mainActivity.viewModels()

    override fun virtualTitleOf(item: MediaItem): String {
        return "null"
    }

    // Long-pressing a row is a shortcut for its three-dot "more" button, same as tapping it.
    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        super.onBindViewHolder(holder, position, payloads)
        val item = list[position]
        holder.itemView.setOnLongClickListener {
            ViewCompat.performHapticFeedback(it, HapticFeedbackConstantsCompat.CONTEXT_CLICK)
            onMenu(item, PopupMenu(it.context, it))
            true
        }
    }

    override fun onClick(item: MediaItem) {
        val mediaController = mainActivity.getPlayer()
        mediaController?.apply {
            // While a search filter is active, getSongList() only holds the filtered subset -
            // queuing that would strand playback with no songs to continue to. Queue against
            // the full, unfiltered library instead so playback continues seamlessly past it.
            val songList = if (isInlineSearch) fullList ?: getSongList() else getSongList()
            setMediaItems(songList, songList.indexOf(item), C.TIME_UNSET)
            prepare()
            play()
        }
    }

    override fun onMenu(item: MediaItem, popupMenu: PopupMenu) {
        showSongOptionsSheet(context, item) { actionId ->
            when (actionId) {
                R.id.play_next -> {
                    mainActivity.getPlayer()?.queueNext(item)
                }

                R.id.album -> {
                    CoroutineScope(Dispatchers.Default).launch {
                        val positionAlbum =
                            viewModel.albumItemList.value?.indexOfFirst {
                                (it.title == item.mediaMetadata.albumTitle) &&
                                        (it.songList.contains(item))
                            }
                        if (positionAlbum != null) {
                            withContext(Dispatchers.Main) {
                                fragment!!.findBaseWrapperFragment()!!
                                    .replaceFragment(GeneralSubFragment()) {
                                        putInt("Position", positionAlbum)
                                        putInt("Item", R.id.album)
                                    }
                            }
                        }
                    }
                }

                R.id.artist -> {
                    // Prefer the album artist (the composer/music director for e.g. film
                    // soundtracks, grouping songs sung by different people under one artist page)
                    // falling back to the track artist when no album artist tag is present.
                    val useAlbumArtist = item.mediaMetadata.albumArtist != null
                    val artistName = if (useAlbumArtist)
                        item.mediaMetadata.albumArtist else item.mediaMetadata.artist
                    val itemType = if (useAlbumArtist) R.id.album_artist else R.id.artist
                    CoroutineScope(Dispatchers.Default).launch {
                        val positionArtist =
                            (if (useAlbumArtist) viewModel.albumArtistItemList
                            else viewModel.artistItemList).value?.indexOfFirst {
                                (it.title == artistName) && (it.songList.contains(item))
                            }
                        if (positionArtist != null && positionArtist != -1) {
                            withContext(Dispatchers.Main) {
                                fragment!!.findBaseWrapperFragment()!!
                                    .replaceFragment(ArtistSubFragment()) {
                                        putInt("Position", positionArtist)
                                        putInt("Item", itemType)
                                        putBoolean("SongsOnly", true)
                                    }
                            }
                        }
                    }
                }

                R.id.details -> {
                    /*
                    val rootView = MaterialAlertDialogBuilder(mainActivity)
                        .setView(R.layout.dialog_info_song)
                        .setBackground(drawable)
                        .setNeutralButton(R.string.dismiss) { dialog, _ ->
                            dialog.dismiss()
                        }
                        .show()
                    rootView.findViewById<TextView>(R.id.title)!!.text = item.mediaMetadata.title
                    rootView.findViewById<TextView>(R.id.artist)!!.text = item.mediaMetadata.artist
                    rootView.findViewById<TextView>(R.id.album)!!.text =
                        item.mediaMetadata.albumTitle
                    if (!item.mediaMetadata.albumArtist.isNullOrBlank()) {
                        rootView.findViewById<TextView>(R.id.album_artist)!!.text =
                            item.mediaMetadata.albumArtist
                    }
                    rootView.findViewById<TextView>(R.id.track_number)!!.text =
                        item.mediaMetadata.trackNumber.toString()
                    rootView.findViewById<TextView>(R.id.disc_number)!!.text =
                        item.mediaMetadata.discNumber.toString()
                    val year = item.mediaMetadata.releaseYear?.toString()
                    if (year != null) {
                        rootView.findViewById<TextView>(R.id.year)!!.text = year
                    }
                    val genre = item.mediaMetadata.genre?.toString()
                    if (genre != null) {
                        rootView.findViewById<TextView>(R.id.genre)!!.text = genre
                    }
                    rootView.findViewById<TextView>(R.id.path)!!.text =
                        item.getFile()?.path
                    rootView.findViewById<TextView>(R.id.mime)!!.text =
                        item.mediaMetadata.extras!!.getString("MimeType")
                    rootView.findViewById<TextView>(R.id.duration)!!.text =
                        convertDurationToTimeStamp(item.mediaMetadata.extras!!.getLong("Duration"))

                     */
                    val position = viewModel.mediaItemList.value?.indexOfFirst {
                        it.mediaId == item.mediaId
                    }
                    fragment!!.findBaseWrapperFragment()!!.replaceFragment(DetailDialogFragment()) {
                        putInt("Position", position!!)
                    }
                }

                /*R.id.delete -> {
                    val doDelete: (() -> (() -> Pair<IntentSender?, () -> Boolean>)) -> Unit = { r ->
                        val res = r()()
                        if (res.first == null) {
                            res.second()
                        } else {
                            if (mainActivity.intentSenderAction == null) {
                                mainActivity.intentSenderAction = res.second
                                mainActivity.intentSender.launch(
                                    IntentSenderRequest.Builder(res.first!!).build()
                                )
                            } else {
                                Toast.makeText(
                                    context, context.getString(
                                        R.string.delete_in_progress
                                    ), Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                    val res = MediaStoreUtils.deleteSong(context, item)
                    if (res.first) {
                        AlertDialog.Builder(context)
                            .setTitle(R.string.delete)
                            .setMessage(item.mediaMetadata.title)
                            .setPositiveButton(R.string.yes) { _, _ ->
                                doDelete(res.second)
                            }
                            .setNegativeButton(R.string.no) { _, _ -> }
                            .show()
                    } else {
                        doDelete(res.second)
                    }
                    true
                }*/

                /*
				R.id.share -> {
					val builder = ShareCompat.IntentBuilder(mainActivity)
					val mimeTypes = mutableSetOf<String>()
					builder.addStream(viewModel.fileUriList.value?.get(songList[holder.bindingAdapterPosition].mediaId.toLong())!!)
					mimeTypes.add(viewModel.mimeTypeList.value?.get(songList[holder.bindingAdapterPosition].mediaId.toLong())!!)
					builder.setType(mimeTypes.singleOrNull() ?: "audio/*").startChooser()
				 } */
				 */
                else -> {}
            }
        }
    }

    class MediaItemHelper(
        types: Set<Sorter.Type> = setOf(
            Sorter.Type.ByTitleDescending, Sorter.Type.ByTitleAscending,
            Sorter.Type.ByArtistDescending, Sorter.Type.ByArtistAscending,
            Sorter.Type.ByAlbumTitleDescending, Sorter.Type.ByAlbumTitleAscending,
            Sorter.Type.ByAlbumArtistDescending, Sorter.Type.ByAlbumArtistAscending,
            Sorter.Type.ByAddDateDescending, Sorter.Type.ByAddDateAscending,
            Sorter.Type.ByModifiedDateDescending, Sorter.Type.ByModifiedDateAscending
        )
    ) : Sorter.Helper<MediaItem>(types) {
        override fun getId(item: MediaItem): String {
            return item.mediaId
        }

        override fun getTitle(item: MediaItem): String {
            return item.mediaMetadata.title.toString()
        }

        override fun getArtist(item: MediaItem): String? {
            return item.mediaMetadata.artist?.toString()
        }

        override fun getAlbumTitle(item: MediaItem): String {
            return item.mediaMetadata.albumTitle?.toString() ?: ""
        }

        override fun getAlbumArtist(item: MediaItem): String {
            return item.mediaMetadata.albumArtist?.toString() ?: ""
        }

        override fun getCover(item: MediaItem): Uri? {
            return item.mediaMetadata.artworkUri
        }

        override fun getAddDate(item: MediaItem): Long {
            return item.mediaMetadata.extras!!.getLong("AddDate")
        }

        override fun getModifiedDate(item: MediaItem): Long {
            return item.mediaMetadata.extras!!.getLong("ModifiedDate")
        }
    }
}