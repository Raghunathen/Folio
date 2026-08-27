package com.raghu.folio.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.raghu.folio.R
import com.raghu.folio.logic.data.db.entity.Book
import com.raghu.folio.logic.utils.audiobook.AudiobookLibraryPrefs
import com.raghu.folio.ui.LibraryViewModel
import com.raghu.folio.ui.MainActivity
import com.raghu.folio.ui.adapters.BookListAdapter
import com.raghu.folio.ui.adapters.HomeListItem
import com.raghu.folio.ui.fragments.settings.MainSettingsFragment

/**
 * Bare-bones placeholder home screen: lets the user pick the Audiobooks SAF folder, shows a
 * "Continue Listening" shelf plus books grouped by author, and starts playback of a tapped book
 * via [MainActivity.playBook]. This is intentionally minimal - full grid/cover UI comes later.
 */
class HomeFragment : BaseFragment() {

    private val libraryViewModel: LibraryViewModel by activityViewModels()
    private lateinit var adapter: BookListAdapter
    private lateinit var emptyText: TextView

    private val pickFolder = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri == null) return@registerForActivityResult
        AudiobookLibraryPrefs.setRootUri(requireContext(), treeUri)
        (requireActivity() as MainActivity).updateLibrary()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        emptyText = view.findViewById(R.id.empty_text)
        val recyclerView = view.findViewById<RecyclerView>(R.id.book_list)
        adapter = BookListAdapter { book -> playBook(book) }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        view.findViewById<MaterialButton>(R.id.choose_folder_button).setOnClickListener {
            pickFolder.launch(AudiobookLibraryPrefs.getRootUri(requireContext()))
        }

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.inflateMenu(R.menu.home_menu)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.refresh -> {
                    (requireActivity() as MainActivity).updateLibrary()
                    true
                }
                R.id.collections -> {
                    requireActivity().supportFragmentManager
                        .beginTransaction()
                        .addToBackStack(System.currentTimeMillis().toString())
                        .hide(this)
                        .add(R.id.container, CollectionsFragment())
                        .commit()
                    true
                }
                R.id.settings -> {
                    requireActivity().supportFragmentManager
                        .beginTransaction()
                        .addToBackStack(System.currentTimeMillis().toString())
                        .hide(this)
                        .add(R.id.container, MainSettingsFragment())
                        .commit()
                    true
                }
                else -> false
            }
        }

        libraryViewModel.authorsWithBooks.observe(viewLifecycleOwner) { rebuildList() }
        libraryViewModel.allBooksWithProgress.observe(viewLifecycleOwner) { rebuildList() }
        libraryViewModel.continueListening.observe(viewLifecycleOwner) { rebuildList() }
    }

    private fun rebuildList() {
        val authors = libraryViewModel.authorsWithBooks.value ?: return
        val progressByBookId = (libraryViewModel.allBooksWithProgress.value ?: emptyList())
            .associate { it.book.bookId to it.progress }
        val authorNameByBookId = authors
            .flatMap { authorWithBooks -> authorWithBooks.books.map { it.bookId to authorWithBooks.author.name } }
            .toMap()

        fun progressFraction(book: Book): Float? {
            val progress = progressByBookId[book.bookId] ?: return null
            return if (book.durationMs > 0) progress.positionMs.toFloat() / book.durationMs else null
        }

        val rows = mutableListOf<HomeListItem>()
        val continuing = libraryViewModel.continueListening.value ?: emptyList()
        if (continuing.isNotEmpty()) {
            rows += HomeListItem.Header(getString(R.string.continue_listening))
            rows += HomeListItem.ContinueShelf(
                continuing.map { bookWithProgress ->
                    val book = bookWithProgress.book
                    HomeListItem.BookRow(
                        book, authorNameByBookId[book.bookId] ?: "", progressFraction(book)
                    )
                }
            )
        }
        authors.forEach { authorWithBooks ->
            if (authorWithBooks.books.isEmpty()) return@forEach
            rows += HomeListItem.Header(authorWithBooks.author.name)
            rows += HomeListItem.AuthorGrid(
                authorWithBooks.books.map { book ->
                    HomeListItem.BookRow(book, authorWithBooks.author.name, progressFraction(book))
                }
            )
        }
        adapter.submitList(rows)
        emptyText.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun playBook(book: Book) {
        (requireActivity() as MainActivity).playBook(book.bookId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }
}
