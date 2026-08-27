/*
 *     Copyright (C) 2024 Akane Foundation
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Gramophone is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.akanework.gramophone.ui.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.ConcatAdapter
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.enableEdgeToEdgePaddingListener
import org.akanework.gramophone.logic.queueNext
import org.akanework.gramophone.logic.ui.MyRecyclerView
import org.akanework.gramophone.logic.ui.attachSwipeToQueueGesture
import org.akanework.gramophone.logic.ui.showAlbumOptionsSheet
import org.akanework.gramophone.logic.utils.MediaStoreUtils
import org.akanework.gramophone.ui.LibraryViewModel
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.adapters.AlbumHeaderAdapter
import org.akanework.gramophone.ui.adapters.SongAdapter
import org.akanework.gramophone.ui.adapters.Sorter
import kotlin.random.Random

/**
 * GeneralSubFragment:
 *   Inherited from [BaseFragment]. Sub fragment of all
 * possible item types. TODO: Artist / AlbumArtist
 *
 * @see BaseFragment
 * @author AkaneTan, nift4
 */
@androidx.annotation.OptIn(UnstableApi::class)
class GeneralSubFragment : BaseFragment(true) {
    private val libraryViewModel: LibraryViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {

        lateinit var itemList: List<MediaItem>

        val rootView = inflater.inflate(R.layout.fragment_general_sub, container, false)
        val topAppBar = rootView.findViewById<MaterialToolbar>(R.id.topAppBar)
        val moreButton = rootView.findViewById<MaterialButton>(R.id.moreButton)
        val collapsingToolbarLayout =
            rootView.findViewById<CollapsingToolbarLayout>(R.id.collapsingtoolbar)
        val recyclerView = rootView.findViewById<MyRecyclerView>(R.id.recyclerview)
        val appBarLayout = rootView.findViewById<AppBarLayout>(R.id.appbarlayout)
        appBarLayout.enableEdgeToEdgePaddingListener()

        if (libraryViewModel.albumItemList.value == null) {
            // TODO make it wait for lib load instead of breaking state restore
            // (still better than crashing, though)
            requireParentFragment().childFragmentManager.popBackStack()
            return null
        }
        val bundle = requireArguments()
        val itemType = bundle.getInt("Item")
        val position = bundle.getInt("Position")

        val title: String?

        var helper: Sorter.NaturalOrderHelper<MediaItem>? = null

        // Only populated for R.id.album / R.id.special_album - drives the rich Apple-Music-style
        // header (cover/title/artist/meta/play/shuffle) shown above the song list on those pages.
        // Genre/date/playlist pages (which share this same fragment) keep the plain title-only bar.
        var albumForHeader: MediaStoreUtils.Album? = null

        when (itemType) {
            R.id.album -> {
                val item = libraryViewModel.albumItemList.value!![position]
                title = item.title ?: requireContext().getString(R.string.unknown_album)
                itemList = item.songList
                albumForHeader = item
                helper =
                    Sorter.NaturalOrderHelper {
                        it.mediaMetadata.trackNumber?.plus(
                            it.mediaMetadata.discNumber?.times(1000) ?: 0
                        ) ?: 0
                    }
            }

            R.id.special_album -> {
                if (libraryViewModel.privateAlbumList.isNotEmpty()) {
                    val item = libraryViewModel.privateAlbumList[position]
                    title = item.title ?: requireContext().getString(R.string.unknown_album)
                    itemList = item.songList
                    albumForHeader = item
                    helper =
                        Sorter.NaturalOrderHelper {
                            it.mediaMetadata.trackNumber?.plus(
                                it.mediaMetadata.discNumber?.times(1000) ?: 0
                            ) ?: 0
                        }
                } else {
                    requireParentFragment().childFragmentManager.popBackStack()
                    return null
                }
            }

            /*R.id.artist -> {
                val item = libraryViewModel.artistItemList.value!![position]
                title = item.title ?: requireContext().getString(R.string.unknown_artist)
                itemList = item.songList
            } TODO */

            R.id.genres -> {
                // Genres
                val item = libraryViewModel.genreItemList.value!![position]
                title = item.title ?: requireContext().getString(R.string.unknown_genre)
                itemList = item.songList
            }

            R.id.dates -> {
                // Dates
                val item = libraryViewModel.dateItemList.value!![position]
                title = item.title ?: requireContext().getString(R.string.unknown_year)
                itemList = item.songList
            }

            /*R.id.album_artist -> {
                // Album artists
                val item = libraryViewModel.albumArtistItemList.value!![position]
                title = item.title ?: requireContext().getString(R.string.unknown_artist)
                itemList = item.songList
            } TODO */

            R.id.playlist -> {
                // Playlists
                val item = libraryViewModel.playlistList.value!![position]
                title = if (item is MediaStoreUtils.RecentlyAdded) {
                    requireContext().getString(R.string.recently_added)
                } else {
                    item.title ?: requireContext().getString(R.string.unknown_playlist)
                }
                itemList = item.songList
                helper = Sorter.NaturalOrderHelper { itemList.indexOf(it) }
            }

            else -> throw IllegalArgumentException()
        }

        // Show title text - except on album pages, where the header below already shows the
        // title itself, so the collapsing toolbar's title would just be a redundant duplicate.
        if (albumForHeader == null) {
            collapsingToolbarLayout.title = title
        } else {
            // top_app_bar_height (132dp) reserves room below the pinned toolbar for the medium
            // collapsing title text - with no title set on album pages, that space is just an
            // empty gap pushing the header's cover down. Shrink it to wrap just the toolbar
            // itself so the cover sits right below the back/more bar.
            collapsingToolbarLayout.layoutParams = collapsingToolbarLayout.layoutParams.apply {
                height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
        }

        val songAdapter =
            SongAdapter(
                this,
                itemList,
                true,
                helper,
                true,
                isSubFragment = true
            )

        recyclerView.enableEdgeToEdgePaddingListener()
        recyclerView.setAppBar(appBarLayout)
        attachSwipeToQueueGesture(requireContext(), recyclerView, songAdapter)

        if (albumForHeader != null) {
            val mainActivity = requireActivity() as MainActivity
            val coverUri = itemList.firstOrNull()?.mediaMetadata?.artworkUri
            val mimeType = itemList.firstOrNull()?.localConfiguration?.mimeType
            val isHiRes = mimeType?.contains("flac") == true
            val format = if (isHiRes) {
                requireContext().getString(R.string.dialog_format_lossless)
            } else {
                mimeType?.substringAfterLast('/')?.uppercase()
            }
            val songCountLabel = resources.getQuantityString(
                R.plurals.songs, itemList.size, itemList.size
            )
            val metaMain = listOfNotNull(
                songCountLabel, albumForHeader.albumYear?.toString()
            ).joinToString(" • ") + if (format != null) " • " else ""

            recyclerView.adapter = ConcatAdapter(
                AlbumHeaderAdapter(
                    coverUri = coverUri,
                    title = title,
                    artist = albumForHeader.artist,
                    metaMain = metaMain,
                    format = format,
                    isHiRes = isHiRes,
                    onPlay = {
                        mainActivity.getPlayer()?.apply {
                            shuffleModeEnabled = false
                            repeatMode = REPEAT_MODE_OFF
                            setMediaItems(itemList, 0, C.TIME_UNSET)
                            if (itemList.isNotEmpty()) {
                                prepare()
                                play()
                            }
                        }
                    },
                    onShuffle = {
                        mainActivity.getPlayer()?.apply {
                            shuffleModeEnabled = true
                            if (itemList.isNotEmpty()) {
                                setMediaItems(itemList)
                                seekToDefaultPosition(Random.nextInt(0, itemList.size))
                                prepare()
                                play()
                            }
                        }
                    }
                ),
                // songAdapter itself, not songAdapter.concatAdapter - skips its built-in decor
                // row (song count + play/shuffle/sort icons), which would just duplicate the
                // count, Play and Shuffle already in the header above.
                songAdapter
            )

            // Same Apple-Music-style sheet as everywhere else in the app, scoped to just the one
            // action that makes sense for a whole album: queue every song on it to play next.
            moreButton.isVisible = true
            moreButton.setOnClickListener {
                showAlbumOptionsSheet(
                    requireContext(), coverUri, title, albumForHeader.artist, songCountLabel
                ) {
                    mainActivity.getPlayer()?.queueNext(itemList)
                }
            }
        } else {
            recyclerView.adapter = songAdapter.concatAdapter
        }

        // Build FastScroller.
        recyclerView.fastScroll(songAdapter, songAdapter.itemHeightHelper)

        // No elastic pull past the ends: the list scrolling is what drives the bar, so its own
        // overscroll stretch just makes the page feel loose and detached from the header.
        recyclerView.overScrollMode = View.OVER_SCROLL_NEVER

        // The bar should only move as a consequence of scrolling the list - grabbing the header
        // itself and dragging it is the other half of that same loose feeling.
        val appBarParams = appBarLayout.layoutParams as CoordinatorLayout.LayoutParams
        val behavior = (appBarParams.behavior as? AppBarLayout.Behavior)
            ?: AppBarLayout.Behavior().also { appBarParams.behavior = it }
        behavior.setDragCallback(object : AppBarLayout.Behavior.DragCallback() {
            override fun canDrag(appBarLayout: AppBarLayout) = false
        })

        // Short track lists have nothing to scroll to, so the bar shouldn't slide away and let the
        // page be dragged around over empty space - same treatment as the artist page.
        //
        // Measured against the viewport *while expanded*, once, off the layout pass. Reacting to
        // canScrollVertically() from a layout listener feeds back on itself: pinning the bar
        // shrinks the viewport, which can make the list scrollable, which unpins it, which grows
        // the viewport again - an oscillation that shows up as a scroll glitch.
        recyclerView.post {
            if (!isAdded) return@post
            val contentHeight = recyclerView.computeVerticalScrollRange()
            val viewportWhenExpanded = rootView.height - appBarLayout.height
            val wanted = if (contentHeight > viewportWhenExpanded)
                AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or
                        AppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED or
                        AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP
            else 0
            val lp = collapsingToolbarLayout.layoutParams as AppBarLayout.LayoutParams
            if (lp.scrollFlags != wanted) {
                lp.scrollFlags = wanted
                collapsingToolbarLayout.layoutParams = lp
                if (wanted == 0) appBarLayout.setExpanded(true, false)
            }
        }

        topAppBar.setNavigationOnClickListener {
            Log.d("TAG", "ok${requireParentFragment().childFragmentManager.fragments.size}")
            (requireParentFragment() as BaseWrapperFragment).childFragmentManager.popBackStack()
        }

        return rootView
    }
}
