package com.raghu.folio.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil3.asImage
import coil3.load
import com.raghu.folio.R
import com.raghu.folio.logic.data.db.entity.Book
import com.raghu.folio.ui.util.CoverPlaceholderGenerator

/** Horizontal cover-card adapter used inside the Home screen's "Continue Listening" shelf. */
class ContinueListeningAdapter(
    private val onBookClick: (Book) -> Unit,
) : RecyclerView.Adapter<ContinueListeningAdapter.CardViewHolder>() {

    private var items: List<HomeListItem.BookRow> = emptyList()

    fun submitList(newItems: List<HomeListItem.BookRow>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_continue_card, parent, false)
        return CardViewHolder(view)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) = holder.bind(items[position], onBookClick)

    override fun getItemCount(): Int = items.size

    class CardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cover: ImageView = itemView.findViewById(R.id.card_cover)
        private val title: TextView = itemView.findViewById(R.id.card_title)
        private val author: TextView = itemView.findViewById(R.id.card_author)
        private val progress: ProgressBar = itemView.findViewById(R.id.card_progress)

        fun bind(item: HomeListItem.BookRow, onBookClick: (Book) -> Unit) {
            title.text = item.book.title
            author.text = item.authorName
            val placeholder = CoverPlaceholderGenerator.generate(
                cover.context, item.book.title, cover.context.resources.getDimensionPixelSize(R.dimen.book_cover_size_large)
            )
            if (item.book.coverUri != null) {
                cover.load(item.book.coverUri) {
                    placeholder(placeholder.asImage())
                    error(placeholder.asImage())
                }
            } else {
                cover.setImageDrawable(placeholder)
            }
            if (item.progress != null) {
                progress.visibility = View.VISIBLE
                progress.progress = (item.progress * 100).toInt()
            } else {
                progress.visibility = View.GONE
            }
            itemView.setOnClickListener { onBookClick(item.book) }
        }
    }
}
