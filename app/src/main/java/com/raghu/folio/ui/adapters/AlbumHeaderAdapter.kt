package com.raghu.folio.ui.adapters

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.error
import coil3.request.placeholder
import com.google.android.material.button.MaterialButton
import com.raghu.folio.R

/**
 * Single-item header for the album detail page: cover art, title, artist, a short metadata line
 * (song count / year / format, with the same hi-res badge glyph used in the full player when the
 * format is lossless) and Shuffle/Play buttons - shown above the song list, matching the app's
 * other Apple-Music-style pages. Prepended to the song list's own ConcatAdapter rather than built
 * into SongAdapter, since this is specific to the album page (not genre/date/playlist pages that
 * reuse the same fragment).
 */
class AlbumHeaderAdapter(
    private val coverUri: Uri?,
    private val title: String,
    private val artist: String?,
    private val metaMain: String?,
    private val format: String?,
    private val isHiRes: Boolean,
    private val onPlay: () -> Unit,
    private val onShuffle: () -> Unit
) : RecyclerView.Adapter<AlbumHeaderAdapter.ViewHolder>() {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = -2L

    override fun getItemCount() = 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.header_album_detail, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.cover.load(coverUri) {
            placeholder(R.drawable.ic_default_cover)
            error(R.drawable.ic_default_cover)
        }
        holder.title.text = title
        holder.artist.text = artist
        holder.artist.visibility = if (artist.isNullOrBlank()) View.GONE else View.VISIBLE
        holder.meta.text = metaMain
        holder.meta.visibility = if (metaMain.isNullOrBlank()) View.GONE else View.VISIBLE
        holder.hiResIcon.visibility = if (isHiRes) View.VISIBLE else View.GONE
        holder.format.text = format
        holder.format.visibility = if (format.isNullOrBlank()) View.GONE else View.VISIBLE
        holder.playButton.setOnClickListener { onPlay() }
        holder.shuffleButton.setOnClickListener { onShuffle() }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cover: ImageView = view.findViewById(R.id.albumCover)
        val title: TextView = view.findViewById(R.id.albumTitle)
        val artist: TextView = view.findViewById(R.id.albumArtist)
        val meta: TextView = view.findViewById(R.id.albumMeta)
        val hiResIcon: ImageView = view.findViewById(R.id.hiResIcon)
        val format: TextView = view.findViewById(R.id.albumFormat)
        val playButton: MaterialButton = view.findViewById(R.id.playButton)
        val shuffleButton: MaterialButton = view.findViewById(R.id.shuffleButton)
    }
}
