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

package org.akanework.gramophone.logic.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.queueNext
import org.akanework.gramophone.ui.adapters.BaseAdapter
import org.akanework.gramophone.ui.adapters.SongAdapter

/**
 * Swipe-right-to-play-next: attaches an orange "queue" reveal gesture to [recyclerView] for
 * [songAdapter]'s rows. Shared between the top-level Songs tab, the artist songs section and
 * album/genre/date/playlist song lists.
 *
 * Like Apple Music's swipe action: the row reveals the queue icon up to a capped distance and
 * stops — it never travels off-screen. Dragging past that cap and releasing fires the action;
 * releasing short of it doesn't. Either way, releasing always plays ItemTouchHelper's own
 * built-in recover-to-rest animation, which reuses the exact same draw code in reverse — so the
 * return is a true mirror of the reveal, not a separate animation we have to fake.
 */
fun attachSwipeToQueueGesture(
    context: Context,
    recyclerView: RecyclerView,
    songAdapter: SongAdapter
) {
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF9500.toInt()  // Apple Music orange
    }
    val icon = ContextCompat.getDrawable(context, R.drawable.ic_queue_btn)
        ?.mutate()?.also { it.setTint(Color.WHITE) }

    // How far the row can be dragged before it hits a "wall" and stops revealing further —
    // matches the fixed-width reveal of a real swipe action button instead of dragging the row
    // fully across (and off) the screen.
    val maxRevealPx = 100f * context.resources.displayMetrics.density
    // Fraction of maxRevealPx that counts as "revealed enough" to confirm the action on release.
    val confirmFraction = 0.7f

    ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {

        // Deepest point reached during the *active* drag (already clamped), so clearView() can
        // tell whether the user revealed enough to confirm.
        var swipeDx = 0f

        override fun getMovementFlags(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
        ): Int {
            // The gesture (and bindingAdapter lookups below) assume a linear list; Grid uses
            // a GridLayoutManager where swipe-to-dismiss-style gestures aren't supported and
            // querying bindingAdapter here has been observed to crash mid-layout.
            if (songAdapter.layoutType == BaseAdapter.LayoutType.GRID)
                return makeMovementFlags(0, 0)
            return if (viewHolder.bindingAdapter is SongAdapter)
                makeMovementFlags(0, ItemTouchHelper.RIGHT)
            else
                makeMovementFlags(0, 0)
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ) = false

        // Never invoked — the swipe is never allowed to "complete" (see the threshold/velocity
        // overrides below), which is what keeps every release going through the normal
        // recover-to-rest path instead of the fully-off-screen dismiss path.
        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

        override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder) = 2f

        override fun getSwipeEscapeVelocity(defaultValue: Float) = Float.MAX_VALUE

        override fun getSwipeVelocityThreshold(defaultValue: Float) = Float.MAX_VALUE

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            val revealed = swipeDx
            swipeDx = 0f
            if (revealed > maxRevealPx * confirmFraction) {
                val pos = viewHolder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    songAdapter.getSongList().getOrNull(pos)?.let { song ->
                        songAdapter.getActivity().getPlayer()?.queueNext(song)
                    }
                }
            }
        }

        override fun onChildDraw(
            c: Canvas,
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            dX: Float, dY: Float,
            actionState: Int,
            isCurrentlyActive: Boolean
        ) {
            // dX from ItemTouchHelper is unclamped (raw finger movement, or the raw recover
            // interpolation on the way back) — clamp it ourselves so the row's translation
            // never exceeds the reveal cap in either direction.
            val v = viewHolder.itemView
            val clamped = dX.coerceIn(0f, maxRevealPx)
            if (isCurrentlyActive) swipeDx = clamped

            if (clamped > 0) {
                // Orange background grows with the swipe
                c.drawRect(
                    v.left.toFloat(), v.top.toFloat(),
                    v.left.toFloat() + clamped, v.bottom.toFloat(),
                    bgPaint
                )
                // Icon trails right behind the row's leading edge, moving 1:1 with the swipe
                // like a tail instead of sitting static at a fixed spot.
                icon?.let {
                    val size = v.height / 2
                    val top = v.top + (v.height - size) / 2
                    val gap = v.height / 8
                    val right = (v.left + clamped - gap).toInt()
                    it.setBounds(right - size, top, right, top + size)
                    it.draw(c)
                }
            }
            // Applied manually (instead of calling super.onChildDraw) since we need the clamped
            // value, not ItemTouchHelper's raw one.
            v.translationX = clamped
        }
    }).attachToRecyclerView(recyclerView)
}