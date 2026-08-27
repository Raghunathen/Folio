package com.raghu.folio.ui.adapters

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.error
import coil3.request.placeholder
import com.google.android.material.imageview.ShapeableImageView
import com.raghu.folio.R

/**
 * The artist/album matches shown above the song results while searching, grouped under small
 * "Artists" / "Albums" headings like Apple Music. The trailing "Songs" heading is emitted here too
 * (as the last row) so it sits directly above the song list, which a different adapter renders.
 *
 * Empty - and therefore invisible - whenever no search is active, so it costs nothing on the
 * normal Songs list.
 */
class SearchEntityAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    /** [round] distinguishes artists (circular photo) from albums (rounded cover). */
    data class Entity(
        val title: String,
        val subtitle: String,
        val imageUri: Uri?,
        val round: Boolean,
        val onClick: () -> Unit
    )

    private sealed interface Row {
        data class Section(val label: String) : Row
        data class Item(val entity: Entity) : Row
    }

    private var rows: List<Row> = emptyList()

    fun submit(
        artists: List<Entity>,
        albums: List<Entity>,
        artistsLabel: String,
        albumsLabel: String,
        songsLabel: String?
    ) {
        val next = buildList {
            if (artists.isNotEmpty()) {
                add(Row.Section(artistsLabel))
                artists.forEach { add(Row.Item(it)) }
            }
            if (albums.isNotEmpty()) {
                add(Row.Section(albumsLabel))
                albums.forEach { add(Row.Item(it)) }
            }
            if (songsLabel != null) add(Row.Section(songsLabel))
        }
        if (rows == next) return
        val oldSize = rows.size
        rows = next
        // Deliberately NOT notifyDataSetChanged(): inside a ConcatAdapter that is forwarded as a
        // full invalidation of every sibling adapter too, which rebinds the search field and -
        // with no stable IDs to restore focus from - drops the keyboard and caret on every
        // keystroke. Range updates stay scoped to this adapter.
        if (oldSize > 0) notifyItemRangeRemoved(0, oldSize)
        if (next.isNotEmpty()) notifyItemRangeInserted(0, next.size)
    }

    override fun getItemCount() = rows.size

    override fun getItemViewType(position: Int) =
        if (rows[position] is Row.Section) TYPE_SECTION else TYPE_ITEM

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_SECTION)
            SectionViewHolder(inflater.inflate(R.layout.adapter_search_section, parent, false))
        else
            ViewHolder(inflater.inflate(R.layout.adapter_search_entity, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Section -> (holder as SectionViewHolder).label.text = row.label
            is Row.Item -> {
                val entity = row.entity
                holder as ViewHolder
                holder.title.text = entity.title
                holder.subtitle.text = entity.subtitle
                holder.cover.shapeAppearanceModel = holder.cover.shapeAppearanceModel
                    .toBuilder()
                    .setAllCornerSizes(
                        holder.itemView.resources.getDimension(
                            if (entity.round) R.dimen.search_entity_round
                            else R.dimen.search_entity_square
                        )
                    )
                    .build()
                val fallback = if (entity.round) R.drawable.ic_default_cover_artist
                else R.drawable.ic_default_cover
                holder.cover.load(entity.imageUri) {
                    placeholder(fallback)
                    error(fallback)
                    // See ArtistSubFragment - the artist photo URI carries the file's timestamp,
                    // so pinning the key is what makes a replaced image actually re-decode.
                    entity.imageUri?.toString()?.let { memoryCacheKey(it); diskCacheKey(it) }
                }
                holder.itemView.setOnClickListener { entity.onClick() }
            }
        }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cover: ShapeableImageView = view.findViewById(R.id.cover)
        val title: TextView = view.findViewById(R.id.title)
        val subtitle: TextView = view.findViewById(R.id.subtitle)
    }

    class SectionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val label: TextView = view as TextView
    }

    private companion object {
        const val TYPE_SECTION = 0
        const val TYPE_ITEM = 1
    }
}
