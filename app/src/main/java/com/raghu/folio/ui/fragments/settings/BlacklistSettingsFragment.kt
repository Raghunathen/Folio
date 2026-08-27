package com.raghu.folio.ui.fragments.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.raghu.folio.R
import com.raghu.folio.logic.enableEdgeToEdgePaddingListener
import com.raghu.folio.ui.LibraryViewModel
import com.raghu.folio.ui.adapters.BlacklistFolderAdapter
import com.raghu.folio.ui.fragments.BaseFragment

class BlacklistSettingsFragment : BaseFragment() {

    private val libraryViewModel: LibraryViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_blacklist_settings, container, false)
        val topAppBar = rootView.findViewById<MaterialToolbar>(R.id.topAppBar)
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())

        rootView.findViewById<AppBarLayout>(R.id.appbarlayout).enableEdgeToEdgePaddingListener()
        val folderArray = libraryViewModel.allFolderSet.value?.toMutableList() ?: mutableListOf()
        folderArray.sort()

        topAppBar.setNavigationOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        val recyclerView = rootView.findViewById<RecyclerView>(R.id.recyclerview)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = BlacklistFolderAdapter(this, folderArray, prefs)

        return rootView
    }
}