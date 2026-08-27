/*
 *     Copyright (C) 2024 Akane Foundation
 *
 *     Folio is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Folio is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.raghu.folio.ui.fragments

import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import androidx.core.graphics.drawable.toDrawable
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.raghu.folio.R
import com.raghu.folio.logic.allowDiskAccessInStrictMode
import com.raghu.folio.logic.dpToPx
import com.raghu.folio.logic.enableEdgeToEdgePaddingListener

/**
 * BasePreferenceFragment:
 *   A base fragment for all SettingsTopFragment. It
 * is used to make overlapping color easier.
 *
 * @author AkaneTan
 */
abstract class BasePreferenceFragment : PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setBackgroundColor(
            MaterialColors.getColor(
                view,
                R.attr.contrast_colorBackground
            )
        )
        view.findViewById<RecyclerView>(androidx.preference.R.id.recycler_view).apply {
            setPadding(paddingLeft, paddingTop + 4.dpToPx(context), paddingRight, paddingBottom)
            enableEdgeToEdgePaddingListener()
        }
    }

    override fun setPreferencesFromResource(preferencesResId: Int, key: String?) {
        allowDiskAccessInStrictMode { super.setPreferencesFromResource(preferencesResId, key) }
    }

    override fun setDivider(divider: Drawable?) {
        super.setDivider(Color.TRANSPARENT.toDrawable())
    }

    override fun setDividerHeight(height: Int) {
        super.setDividerHeight(0)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
    }

    override fun onStart() {
        super.onStart()
        preferenceScreen.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onStop() {
        preferenceScreen.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        super.onStop()
    }

    override fun onDestroy() {
        // Work around b/331383944: PreferenceFragmentCompat permanently mutates activity theme (enables vertical scrollbars)
        requireContext().theme.applyStyle(R.style.Theme_Folio, true)
        super.onDestroy()
    }

}