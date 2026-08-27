package com.raghu.folio.logic.ui

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.media3.common.MediaItem
import coil3.load
import coil3.request.error
import coil3.request.placeholder
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.raghu.folio.R

/**
 * BottomSheetDialog opens collapsed at a peek height derived from the screen height. Landscape has
 * so little height to go around that this leaves these sheets as a barely-visible sliver along the
 * bottom edge - from the user's side it just looks like tapping the button did nothing. Open them
 * expanded there instead. Portrait already shows them in full, so it is deliberately left alone.
 */
internal fun BottomSheetDialog.expandIfLandscape(context: Context) {
    if (context.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE) return
    setOnShowListener {
        behavior.skipCollapsed = true
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }
}

private class SongOptionRow(@param:IdRes val id: Int, @param:DrawableRes val icon: Int, val label: Int)

private val songOptionRows = listOf(
    SongOptionRow(R.id.play_next, R.drawable.ic_queue_btn, R.string.play_next),
    SongOptionRow(R.id.album, R.drawable.ic_album, R.string.go_to_album),
    SongOptionRow(R.id.artist, R.drawable.ic_groups, R.string.go_to_artist),
    SongOptionRow(R.id.details, R.drawable.ic_info, R.string.details),
    SongOptionRow(R.id.sleep_timer, R.drawable.ic_timer_24dp, R.string.sleep_timer),
)

// The ids in songOptionRows, in order - the default full set shown by the three-dot button on a
// song row (Songs list, Album/Genre/Date/Playlist/Artist song lists). Deliberately excludes
// sleep_timer - a playback-level setting that only makes sense from the full player, not from an
// arbitrary song in a list - see fullPlayerOptionIds.
val allSongOptionIds = songOptionRows.map { it.id }.filterNot { it == R.id.sleep_timer }

// The full player's three-dot menu additionally offers the sleep timer.
val fullPlayerOptionIds = allSongOptionIds + R.id.sleep_timer

// Just navigation - reused by tapping the title/artist text in the full player, where "queue"
// and "details" don't make sense.
val goToAlbumOrArtistOptionIds = listOf(R.id.album, R.id.artist)

/**
 * Apple-Music-style modal sheet for a song's context menu: cover/title/artist/album header
 * followed by a flat list of actions, replacing the old anchor-positioned PopupMenu.
 * [options] picks which rows to show (and in what order) from [allSongOptionIds] - defaults to
 * all of them, the full three-dot menu; pass a subset (eg. [goToAlbumOrArtistOptionIds]) to reuse
 * the same sheet for a narrower action, like tapping the title/artist text.
 * [sleepTimerValue] is a short trailing label (eg. "14 min") shown on the Sleep Timer row when a
 * timer is currently running - null/blank hides it. Computed once when the sheet opens, not
 * ticked live while it's on screen.
 * [onOptionSelected] receives the tapped row's id after the sheet has been dismissed.
 */
fun showSongOptionsSheet(
    context: Context,
    item: MediaItem,
    options: List<Int> = allSongOptionIds,
    sleepTimerValue: String? = null,
    onOptionSelected: (Int) -> Unit
) {
    val dialog = BottomSheetDialog(context)
    val root = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_song_options, null)

    root.findViewById<ImageView>(R.id.cover).load(item.mediaMetadata.artworkUri) {
        placeholder(R.drawable.ic_default_cover)
        error(R.drawable.ic_default_cover)
    }
    root.findViewById<TextView>(R.id.title).text = item.mediaMetadata.title
    root.findViewById<TextView>(R.id.artist).text = item.mediaMetadata.artist
    root.findViewById<TextView>(R.id.album).text = item.mediaMetadata.albumTitle

    val optionsContainer = root.findViewById<LinearLayout>(R.id.optionsContainer)
    val rowsById = songOptionRows.associateBy { it.id }
    for (id in options) {
        val row = rowsById[id] ?: continue
        val rowView: View = LayoutInflater.from(context)
            .inflate(R.layout.item_song_option, optionsContainer, false)
        rowView.findViewById<ImageView>(R.id.icon).setImageResource(row.icon)
        rowView.findViewById<TextView>(R.id.label).setText(row.label)
        if (row.id == R.id.sleep_timer && !sleepTimerValue.isNullOrBlank()) {
            rowView.findViewById<TextView>(R.id.value).apply {
                text = sleepTimerValue
                visibility = View.VISIBLE
            }
        }
        rowView.setOnClickListener {
            // Defer the action until the dialog has actually finished dismissing (not just
            // requested to) - some actions (eg. sleep_timer) open another dialog of their own,
            // and showing one while this one is still mid dismiss-animation/window-teardown is
            // an Android footgun that can leave the new dialog broken or invisible.
            dialog.setOnDismissListener { onOptionSelected(row.id) }
            dialog.dismiss()
        }
        optionsContainer.addView(rowView)
    }

    dialog.setContentView(root)
    dialog.expandIfLandscape(context)
    dialog.show()
}

/**
 * Same sheet chrome as [showSongOptionsSheet], for an album's three-dot menu instead of a single
 * song's - just cover/title/artist header plus a single Play Next row (queues every song on the
 * album). The header's third line is repurposed to show the song count instead of an album title,
 * since the album itself is the thing being shown.
 */
fun showAlbumOptionsSheet(
    context: Context,
    coverUri: Uri?,
    title: String?,
    artist: String?,
    songCountLabel: String?,
    onPlayNext: () -> Unit
) {
    val dialog = BottomSheetDialog(context)
    val root = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_song_options, null)

    root.findViewById<ImageView>(R.id.cover).load(coverUri) {
        placeholder(R.drawable.ic_default_cover)
        error(R.drawable.ic_default_cover)
    }
    root.findViewById<TextView>(R.id.title).text = title
    root.findViewById<TextView>(R.id.artist).text = artist
    root.findViewById<TextView>(R.id.album).text = songCountLabel

    val optionsContainer = root.findViewById<LinearLayout>(R.id.optionsContainer)
    val rowView: View = LayoutInflater.from(context)
        .inflate(R.layout.item_song_option, optionsContainer, false)
    rowView.findViewById<ImageView>(R.id.icon).setImageResource(R.drawable.ic_queue_btn)
    rowView.findViewById<TextView>(R.id.label).setText(R.string.play_next)
    rowView.setOnClickListener {
        dialog.setOnDismissListener { onPlayNext() }
        dialog.dismiss()
    }
    optionsContainer.addView(rowView)

    dialog.setContentView(root)
    dialog.expandIfLandscape(context)
    dialog.show()
}
