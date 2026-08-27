/*
 *     Copyright (C) 2024 Akane Foundation
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Gramophone is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.akanework.gramophone.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.activityViewModels
import androidx.media3.common.util.UnstableApi
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.MaterialColors
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.applyGeneralMenuItem
import org.akanework.gramophone.logic.enableEdgeToEdgePaddingListener
import org.akanework.gramophone.ui.LibraryViewModel

@androidx.annotation.OptIn(UnstableApi::class)
class FoldersFragment : BaseFragment(null) {
    private val libraryViewModel: LibraryViewModel by activityViewModels()
    lateinit var appBarLayout: AppBarLayout
        private set

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_folders, container, false)
        val topAppBar = rootView.findViewById<MaterialToolbar>(R.id.topAppBar)

        appBarLayout = rootView.findViewById(R.id.appbarlayout)
        appBarLayout.enableEdgeToEdgePaddingListener()
        topAppBar.overflowIcon = AppCompatResources.getDrawable(
            requireContext(), R.drawable.ic_more_vert_bold
        )!!.apply {
            setTint(MaterialColors.getColor(rootView, R.attr.contrast_themeColor))
        }
        topAppBar.applyGeneralMenuItem(this, libraryViewModel)

        if (childFragmentManager.findFragmentById(R.id.folders_container) == null) {
            childFragmentManager.beginTransaction()
                .add(R.id.folders_container, AdapterFragment().apply {
                    arguments = Bundle().apply { putInt("ID", R.id.folders) }
                })
                .commit()
        }

        return rootView
    }
}
