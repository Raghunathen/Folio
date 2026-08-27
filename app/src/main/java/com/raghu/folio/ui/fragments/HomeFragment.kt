package com.raghu.folio.ui.fragments

import android.content.ComponentName
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.activityViewModels
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.common.util.concurrent.MoreExecutors
import com.raghu.folio.R
import com.raghu.folio.logic.AudiobookPlaybackService
import com.raghu.folio.logic.data.db.entity.Book
import com.raghu.folio.logic.utils.audiobook.AudiobookLibraryPrefs
import com.raghu.folio.ui.LibraryViewModel
import com.raghu.folio.ui.MainActivity
import com.raghu.folio.ui.adapters.BookListAdapter

/**
 * Bare-bones placeholder home screen: lets the user pick the Audiobooks SAF folder, shows a flat
 * list of scanned books, and starts playback of a tapped book via [AudiobookPlaybackService].
 * This is intentionally minimal - full Author/Book browsing UI comes later.
 */
class HomeFragment : BaseFragment() {

    private val libraryViewModel: LibraryViewModel by activityViewModels()
    private var mediaController: MediaController? = null

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

        val emptyText = view.findViewById<TextView>(R.id.empty_text)
        val recyclerView = view.findViewById<RecyclerView>(R.id.book_list)
        val adapter = BookListAdapter { book -> playBook(book) }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        view.findViewById<MaterialButton>(R.id.choose_folder_button).setOnClickListener {
            pickFolder.launch(AudiobookLibraryPrefs.getRootUri(requireContext()))
        }

        libraryViewModel.authorsWithBooks.observe(viewLifecycleOwner) { authors ->
            val rows = authors.flatMap { authorWithBooks ->
                authorWithBooks.books.map { it to authorWithBooks.author.name }
            }
            adapter.submitList(rows)
            emptyText.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun playBook(book: Book) {
        val context = requireContext().applicationContext
        val token = SessionToken(
            context, ComponentName(context, AudiobookPlaybackService::class.java)
        )
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            val controller = future.get()
            mediaController = controller
            controller.sendCustomCommand(
                SessionCommand(AudiobookPlaybackService.COMMAND_LOAD_BOOK, Bundle.EMPTY),
                Bundle().apply { putLong(AudiobookPlaybackService.EXTRA_BOOK_ID, book.bookId) },
            )
        }, MoreExecutors.directExecutor())
    }

    override fun onDestroyView() {
        mediaController?.release()
        mediaController = null
        super.onDestroyView()
    }
}
