package com.raghu.folio.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.raghu.folio.R
import com.raghu.folio.logic.data.db.entity.Book

/**
 * Minimal RecyclerView adapter listing scanned audiobooks (title + author name). Placeholder for
 * the full library UI - covers/progress/series grouping are not shown yet.
 */
class BookListAdapter(
    private val onBookClick: (Book) -> Unit,
) : RecyclerView.Adapter<BookListAdapter.ViewHolder>() {

    private var items: List<Pair<Book, String>> = emptyList()

    fun submitList(newItems: List<Pair<Book, String>>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_book_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (book, authorName) = items[position]
        holder.title.text = book.title
        holder.author.text = authorName
        holder.itemView.setOnClickListener { onBookClick(book) }
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.book_title)
        val author: TextView = itemView.findViewById(R.id.book_author)
    }
}

