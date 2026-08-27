package com.raghu.folio.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.raghu.folio.R
import com.raghu.folio.logic.data.db.AppDatabase
import com.raghu.folio.ui.LibraryViewModel
import com.raghu.folio.ui.MainActivity
import com.raghu.folio.ui.adapters.ContinueListeningAdapter
import com.raghu.folio.ui.adapters.HomeListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shows the books inside one [com.raghu.folio.logic.data.db.entity.Collection] as a cover grid. */
class CollectionDetailFragment : BaseFragment() {

    companion object {
        private const val ARG_COLLECTION_ID = "collection_id"
        private const val GRID_SPAN_COUNT = 3

        fun newInstance(collectionId: Long) = CollectionDetailFragment().apply {
            arguments = Bundle().apply { putLong(ARG_COLLECTION_ID, collectionId) }
        }
    }

    private val libraryViewModel: LibraryViewModel by activityViewModels()
    private val collectionId: Long by lazy { requireArguments().getLong(ARG_COLLECTION_ID) }
    private lateinit var adapter: ContinueListeningAdapter
    private lateinit var emptyText: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_collection_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        emptyText = view.findViewById(R.id.empty_text)
        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { requireActivity().supportFragmentManager.popBackStack() }

        val recyclerView = view.findViewById<RecyclerView>(R.id.collection_books_grid)
        adapter = ContinueListeningAdapter { book -> (requireActivity() as MainActivity).playBook(book.bookId) }
        recyclerView.layoutManager = GridLayoutManager(requireContext(), GRID_SPAN_COUNT)
        recyclerView.adapter = adapter

        loadCollection(toolbar)
    }

    private fun loadCollection(toolbar: MaterialToolbar) {
        viewLifecycleOwner.lifecycleScope.launch {
            val collectionWithBooks = withContext(Dispatchers.IO) {
                AppDatabase.getInstance(requireContext()).collectionDao().getCollectionWithBooks(collectionId)
            } ?: return@launch
            toolbar.title = collectionWithBooks.collection.name

            val authorNameByBookId = (libraryViewModel.authorsWithBooks.value ?: emptyList())
                .flatMap { authorWithBooks -> authorWithBooks.books.map { it.bookId to authorWithBooks.author.name } }
                .toMap()
            val progressByBookId = (libraryViewModel.allBooksWithProgress.value ?: emptyList())
                .associate { it.book.bookId to it.progress }

            val rows = collectionWithBooks.books.map { book ->
                val progress = progressByBookId[book.bookId]
                val fraction = if (progress != null && book.durationMs > 0) {
                    progress.positionMs.toFloat() / book.durationMs
                } else null
                HomeListItem.BookRow(book, authorNameByBookId[book.bookId] ?: "", fraction)
            }
            adapter.submitList(rows)
            emptyText.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        }
    }
}
