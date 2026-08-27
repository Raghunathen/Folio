package com.raghu.folio.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil3.asImage
import coil3.load
import com.raghu.folio.R
import com.raghu.folio.logic.data.db.entity.Book
import com.raghu.folio.ui.util.CoverPlaceholderGenerator

/** A row in the home list: a section header, a horizontal "Continue Listening" cover-card shelf,
 *  a wrapping cover-card grid for one author's books, or a flat book row (author name + optional
 *  playback progress 0f-1f, null if never played) - kept for compatibility with older call sites. */
sealed class HomeListItem {
    data class Header(val title: String) : HomeListItem()
    data class ContinueShelf(val books: List<BookRow>) : HomeListItem()
    data class AuthorGrid(val books: List<BookRow>) : HomeListItem()
    data class BookRow(val book: Book, val authorName: String, val progress: Float?) : HomeListItem()
}

private const val VIEW_TYPE_HEADER = 0
private const val VIEW_TYPE_BOOK = 1
private const val VIEW_TYPE_CONTINUE_SHELF = 2
private const val VIEW_TYPE_AUTHOR_GRID = 3
private const val AUTHOR_GRID_SPAN_COUNT = 3

/**
 * RecyclerView adapter for the Home screen: "Continue Listening" + per-author book sections.
 * Placeholder for the full library UI - covers/series grouping are not shown yet.
 */
class BookListAdapter(
    private val onBookClick: (Book) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items: List<HomeListItem> = emptyList()

    fun submitList(newItems: List<HomeListItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is HomeListItem.Header -> VIEW_TYPE_HEADER
        is HomeListItem.BookRow -> VIEW_TYPE_BOOK
        is HomeListItem.ContinueShelf -> VIEW_TYPE_CONTINUE_SHELF
        is HomeListItem.AuthorGrid -> VIEW_TYPE_AUTHOR_GRID
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> HeaderViewHolder(inflater.inflate(R.layout.item_home_header, parent, false))
            VIEW_TYPE_CONTINUE_SHELF ->
                ShelfViewHolder(inflater.inflate(R.layout.item_home_continue_shelf, parent, false), onBookClick)
            VIEW_TYPE_AUTHOR_GRID ->
                AuthorGridViewHolder(inflater.inflate(R.layout.item_home_author_grid, parent, false), onBookClick)
            else -> BookViewHolder(inflater.inflate(R.layout.item_book_row, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is HomeListItem.Header -> (holder as HeaderViewHolder).text.text = item.title
            is HomeListItem.BookRow -> (holder as BookViewHolder).bind(item, onBookClick)
            is HomeListItem.ContinueShelf -> (holder as ShelfViewHolder).bind(item)
            is HomeListItem.AuthorGrid -> (holder as AuthorGridViewHolder).bind(item)
        }
    }

    override fun getItemCount(): Int = items.size

    private class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val text: TextView = itemView.findViewById(R.id.section_header_text)
    }

    private class ShelfViewHolder(
        itemView: View,
        onBookClick: (Book) -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {
        private val shelfAdapter = ContinueListeningAdapter(onBookClick)

        init {
            val list = itemView.findViewById<RecyclerView>(R.id.continue_shelf_list)
            list.layoutManager = LinearLayoutManager(itemView.context, LinearLayoutManager.HORIZONTAL, false)
            list.adapter = shelfAdapter
        }

        fun bind(item: HomeListItem.ContinueShelf) {
            shelfAdapter.submitList(item.books)
        }
    }

    private class AuthorGridViewHolder(
        itemView: View,
        onBookClick: (Book) -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {
        private val gridAdapter = ContinueListeningAdapter(onBookClick)

        init {
            val list = itemView.findViewById<RecyclerView>(R.id.author_grid_list)
            list.layoutManager = GridLayoutManager(itemView.context, AUTHOR_GRID_SPAN_COUNT)
            list.isNestedScrollingEnabled = false
            list.adapter = gridAdapter
        }

        fun bind(item: HomeListItem.AuthorGrid) {
            gridAdapter.submitList(item.books)
        }
    }

    private class BookViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cover: ImageView = itemView.findViewById(R.id.book_cover)
        private val title: TextView = itemView.findViewById(R.id.book_title)
        private val author: TextView = itemView.findViewById(R.id.book_author)
        private val progress: ProgressBar = itemView.findViewById(R.id.book_progress)

        fun bind(item: HomeListItem.BookRow, onBookClick: (Book) -> Unit) {
            title.text = item.book.title
            author.text = item.authorName
            val placeholder = CoverPlaceholderGenerator.generate(
                cover.context, item.book.title, cover.context.resources.getDimensionPixelSize(R.dimen.book_cover_size)
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


