package com.raghu.folio.ui.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.raghu.folio.R
import com.raghu.folio.logic.data.db.AppDatabase
import com.raghu.folio.logic.data.db.entity.Collection
import com.raghu.folio.ui.LibraryViewModel
import com.raghu.folio.ui.adapters.CollectionAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lists user-created [Collection]s (e.g. Favorites, custom shelves) and lets the user create new ones. */
class CollectionsFragment : BaseFragment() {

    private val libraryViewModel: LibraryViewModel by activityViewModels()
    private lateinit var adapter: CollectionAdapter
    private lateinit var emptyText: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_collections, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        emptyText = view.findViewById(R.id.empty_text)
        val recyclerView = view.findViewById<RecyclerView>(R.id.collections_list)
        adapter = CollectionAdapter { collectionWithBooks ->
            requireActivity().supportFragmentManager
                .beginTransaction()
                .addToBackStack(System.currentTimeMillis().toString())
                .hide(this)
                .add(
                    R.id.container,
                    CollectionDetailFragment.newInstance(collectionWithBooks.collection.collectionId)
                )
                .commit()
        }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        view.findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }
        view.findViewById<FloatingActionButton>(R.id.add_collection_fab).setOnClickListener {
            showCreateCollectionDialog()
        }

        libraryViewModel.collections.observe(viewLifecycleOwner) { collections ->
            adapter.submitList(collections)
            emptyText.visibility = if (collections.isEmpty()) View.VISIBLE else View.GONE
        }
        refreshCollections()
    }

    private fun showCreateCollectionDialog() {
        val input = EditText(requireContext()).apply { hint = getString(R.string.new_collection_hint) }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.new_collection)
            .setView(input)
            .setPositiveButton(R.string.new_collection) { _, _ ->
                val name = input.text?.toString()?.trim().takeUnless { it.isNullOrEmpty() }
                    ?: return@setPositiveButton
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        AppDatabase.getInstance(requireContext()).collectionDao().addCollection(
                            Collection(name = name, coverUri = null, createdAt = System.currentTimeMillis())
                        )
                    }
                    refreshCollections()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun refreshCollections() {
        viewLifecycleOwner.lifecycleScope.launch {
            val collections = withContext(Dispatchers.IO) {
                AppDatabase.getInstance(requireContext()).collectionDao().getAllCollections()
            }
            libraryViewModel.collections.value = collections
        }
    }
}
