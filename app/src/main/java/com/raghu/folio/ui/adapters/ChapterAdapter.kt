package com.raghu.folio.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.raghu.folio.R
import com.raghu.folio.logic.data.db.entity.Chapter
import java.util.Locale
import java.util.concurrent.TimeUnit

class ChapterAdapter(
    private val onChapterClick: (Chapter) -> Unit,
) : RecyclerView.Adapter<ChapterAdapter.ViewHolder>() {

    private var chapters: List<Chapter> = emptyList()

    fun submitList(newChapters: List<Chapter>) {
        chapters = newChapters
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chapter_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(chapters[position], onChapterClick)
    }

    override fun getItemCount(): Int = chapters.size

    class ViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.chapter_title)
        private val startTime: TextView = itemView.findViewById(R.id.chapter_start_time)

        fun bind(chapter: Chapter, onClick: (Chapter) -> Unit) {
            title.text = chapter.title
            startTime.text = formatMs(chapter.startMs)
            itemView.setOnClickListener { onClick(chapter) }
        }

        private fun formatMs(ms: Long): String {
            val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms)
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) {
                String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
            }
        }
    }
}
