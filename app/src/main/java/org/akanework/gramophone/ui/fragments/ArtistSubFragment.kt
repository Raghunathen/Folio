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

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.activityViewModels
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup
import androidx.recyclerview.widget.LinearLayoutManager
import coil3.load
import coil3.request.error
import coil3.request.placeholder
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar
import me.zhanghai.android.fastscroll.PopupTextProvider
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.enableEdgeToEdgePaddingListener
import org.akanework.gramophone.logic.ui.DefaultItemHeightHelper
import org.akanework.gramophone.logic.ui.MyRecyclerView
import org.akanework.gramophone.logic.ui.attachSwipeToQueueGesture
import org.akanework.gramophone.logic.utils.ArtistImageStore
import org.akanework.gramophone.ui.LibraryViewModel
import org.akanework.gramophone.ui.adapters.AlbumAdapter
import org.akanework.gramophone.ui.adapters.SongAdapter
import org.akanework.gramophone.ui.components.GridPaddingDecoration
import kotlin.properties.Delegates

/**
 * ArtistSubFragment:
 *   Separated from GeneralSubFragment and will be
 * merged into it in future development.
 *
 * @author nift4
 * @see BaseFragment
 * @see GeneralSubFragment
 */
@androidx.annotation.OptIn(UnstableApi::class)
class ArtistSubFragment : BaseFragment(true), PopupTextProvider {
    private val libraryViewModel: LibraryViewModel by activityViewModels()

    // Non-null only when the album section is shown (see "SongsOnly" argument).
    private var albumAdapter: AlbumAdapter? = null
    private lateinit var songAdapter: SongAdapter
    private var gridPaddingDecoration: GridPaddingDecoration? = null
    private lateinit var recyclerView: MyRecyclerView
    private var spans by Delegates.notNull<Int>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_artist_sub, container, false)
        val topAppBar = rootView.findViewById<MaterialToolbar>(R.id.topAppBar)
        val appBarLayout = rootView.findViewById<AppBarLayout>(R.id.appbarlayout)
        appBarLayout.enableEdgeToEdgePaddingListener()

        val position = requireArguments().getInt("Position")
        val itemType = requireArguments().getInt("Item")
        // Set when navigating here from a song's "Go to artist" menu action: that action is
        // about finding other songs by the same artist, not browsing their albums.
        val songsOnly = requireArguments().getBoolean("SongsOnly", false)
        recyclerView = rootView.findViewById(R.id.recyclerview)

        val item = libraryViewModel.let {
            if (itemType == R.id.album_artist)
                it.albumArtistItemList else it.artistItemList
        }.value!![position]
        spans = if (requireContext().resources.configuration.orientation
            == Configuration.ORIENTATION_PORTRAIT
        ) 2 else 4

        val artistName = item.title ?: requireContext().getString(R.string.unknown_artist)
        // The collapsing bar shows this large over the photo while expanded and shrinks it into
        // the toolbar as the list scrolls up, so no separate header row is needed at all.
        rootView.findViewById<CollapsingToolbarLayout>(R.id.collapsingtoolbar).title = artistName
        val heroUri = ArtistImageStore.imageUriFor(
            requireContext(), artistName,
            // Artists with no local photo still get something on-brand rather than a blank
            // bar: one of their own album covers.
            fallback = item.songList.firstOrNull()?.mediaMetadata?.artworkUri
        )
        rootView.findViewById<ImageView>(R.id.artistImage).load(heroUri) {
            placeholder(R.drawable.ic_default_cover_artist)
            error(R.drawable.ic_default_cover_artist)
            // Keyed on the full URI, which carries the file's timestamp for artist photos (see
            // ArtistImageStore). Without pinning the key the loader maps the URI down to a plain
            // path first, and a replaced image would keep serving the previously cached bitmap.
            heroUri?.toString()?.let { memoryCacheKey(it); diskCacheKey(it) }
        }

        if (songsOnly) {
            songAdapter = SongAdapter(
                this,
                item.songList, true, null, false,
                isSubFragment = true, fallbackSpans = 1
            )
            recyclerView.layoutManager = LinearLayoutManager(context)
            recyclerView.adapter = songAdapter.concatAdapter
            recyclerView.fastScroll(this, songAdapter.itemHeightHelper)
            attachSwipeToQueueGesture(requireContext(), recyclerView, songAdapter)
        } else {
            gridPaddingDecoration = GridPaddingDecoration(requireContext())
            val albumAdapter = AlbumAdapter(
                this, item.albumList.toMutableList(), true,
                fallbackSpans = spans
            )
            this.albumAdapter = albumAdapter
            songAdapter = SongAdapter(
                this,
                item.songList, true, null, false,
                isSubFragment = true, fallbackSpans = spans / 2 // one song takes 2 spans
            )
            recyclerView.layoutManager = GridLayoutManager(context, spans).apply {
                spanSizeLookup = object : SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        // BaseDecorAdapter always is full width
                        return if (position == 0 || position == albumAdapter.concatAdapter.itemCount) spans
                        // One album takes 1 span, one song takes 2 spans
                        else if (position > 0 && position < albumAdapter.concatAdapter.itemCount) 1 else 2
                    }
                }
            }
            val ih = DefaultItemHeightHelper.concatItemHeightHelper(albumAdapter.itemHeightHelper,
                { albumAdapter.concatAdapter.itemCount }, songAdapter.itemHeightHelper
            )
            recyclerView.adapter = ConcatAdapter(albumAdapter.concatAdapter, songAdapter.concatAdapter)
            recyclerView.addItemDecoration(gridPaddingDecoration!!)
            recyclerView.fastScroll(this, ih)
        }

        recyclerView.enableEdgeToEdgePaddingListener()
        recyclerView.setAppBar(appBarLayout)
        // Pulling the list past its top used to stretch it away from the photo, leaving a gap
        // between the artist name and the first song. The list scrolling is what drives the
        // header, so it doesn't need an overscroll effect of its own.
        recyclerView.overScrollMode = View.OVER_SCROLL_NEVER

        // The header must only move as a consequence of scrolling the list - grabbing the photo
        // itself and dragging it is what pulled the name away from the songs.
        val appBarParams = appBarLayout.layoutParams as CoordinatorLayout.LayoutParams
        val behavior = (appBarParams.behavior as? AppBarLayout.Behavior)
            ?: AppBarLayout.Behavior().also { appBarParams.behavior = it }
        behavior.setDragCallback(object : AppBarLayout.Behavior.DragCallback() {
            override fun canDrag(appBarLayout: AppBarLayout) = false
        })

        // With only a handful of songs there is nothing to scroll to, so the header should stay
        // put instead of letting the page be dragged around over empty space.
        //
        // The test deliberately compares the content against the viewport *while expanded*, and
        // runs once off the layout pass rather than from a layout listener. Reacting to
        // canScrollVertically() instead is self-defeating: locking the header open shrinks the
        // list's viewport, which can make it scrollable again, which unlocks the header, which
        // grows the viewport... an oscillation that shows up as exactly the scroll glitch this is
        // meant to prevent. Comparing against the expanded viewport can't feed back on itself.
        val collapsingToolbar =
            rootView.findViewById<CollapsingToolbarLayout>(R.id.collapsingtoolbar)
        recyclerView.post {
            if (!isAdded) return@post
            // Open on the photo. Adapters that measure themselves after the first layout (the
            // album grid sizing itself, for one) can nudge the list down before anything is on
            // screen, which drags the app bar shut and hides the hero the page is built around.
            appBarLayout.setExpanded(true, false)
            recyclerView.scrollToPosition(0)
            val contentHeight = recyclerView.computeVerticalScrollRange()
            val viewportWhenExpanded = rootView.height - appBarLayout.height
            val needsScroll = contentHeight > viewportWhenExpanded
            val wanted = if (needsScroll)
                AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or
                        AppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED
            else 0
            val lp = collapsingToolbar.layoutParams as AppBarLayout.LayoutParams
            if (lp.scrollFlags != wanted) {
                lp.scrollFlags = wanted
                collapsingToolbar.layoutParams = lp
                if (!needsScroll) appBarLayout.setExpanded(true, false)
            }
        }

        topAppBar.setNavigationOnClickListener {
            (requireParentFragment() as BaseWrapperFragment).childFragmentManager.popBackStack()
        }

        return rootView
    }

    override fun getPopupText(view: View, position: Int): CharSequence {
        val albumAdapter = albumAdapter ?: return songAdapter.getPopupText(view, position)
        return if (position < albumAdapter.concatAdapter.itemCount) {
            albumAdapter.getPopupText(view, position)
        } else {
            songAdapter.getPopupText(view, position - albumAdapter.concatAdapter.itemCount)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        gridPaddingDecoration?.let { recyclerView.removeItemDecoration(it) }
    }
}
