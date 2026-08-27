package com.raghu.folio.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.raghu.folio.R
import com.raghu.folio.logic.data.db.entity.CollectionWithBooks

/** RecyclerView adapter for the Collections list screen: one row per collection (name + book count). */
class CollectionAdapter(
    private val onClick: (CollectionWithBooks) -> Unit,
) : RecyclerView.Adapter<CollectionAdapter.ViewHolder>() {

    private var items: List<CollectionWithBooks> = emptyList()

    fun submitList(newItems: List<CollectionWithBooks>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_collection_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position], onClick)

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        private val name: TextView = itemView.findViewById(R.id.collection_name)
        private val count: TextView = itemView.findViewById(R.id.collection_count)

        fun bind(item: CollectionWithBooks, onClick: (CollectionWithBooks) -> Unit) {
            name.text = item.collection.name
            count.text = count.context.getString(R.string.collection_book_count, item.books.size)
            itemView.setOnClickListener { onClick(item) }
        }
    }
}
