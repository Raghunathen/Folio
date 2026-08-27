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

package com.raghu.folio.ui.fragments.settings

import android.os.Bundle
import androidx.preference.Preference
import com.raghu.folio.R
import com.raghu.folio.ui.MainActivity
import com.raghu.folio.ui.fragments.BasePreferenceFragment
import com.raghu.folio.ui.fragments.BaseSettingFragment

class MainSettingsFragment : BaseSettingFragment(R.string.home_menu_settings, { MainSettingsTopFragment() })

class MainSettingsTopFragment : BasePreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_top, rootKey)
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        when (preference.key) {
            "appearance" -> {
                val supportFragmentManager = requireActivity().supportFragmentManager
                supportFragmentManager
                    .beginTransaction()
                    .addToBackStack(System.currentTimeMillis().toString())
                    .hide(supportFragmentManager.fragments.let { it[it.size - 1] })
                    .add(R.id.container, AppearanceSettingsFragment())
                    .commit()
            }

            "behavior" -> {
                val supportFragmentManager = requireActivity().supportFragmentManager
                supportFragmentManager
                    .beginTransaction()
                    .addToBackStack(System.currentTimeMillis().toString())
                    .hide(supportFragmentManager.fragments.let { it[it.size - 1] })
                    .add(R.id.container, BehaviorSettingsFragment())
                    .commit()
            }

            "audio" -> {
                val supportFragmentManager = requireActivity().supportFragmentManager
                supportFragmentManager
                    .beginTransaction()
                    .addToBackStack(System.currentTimeMillis().toString())
                    .hide(supportFragmentManager.fragments.let { it[it.size - 1] })
                    .add(R.id.container, AudioSettingsFragment())
                    .commit()
            }

            "listening_stats" -> {
                val supportFragmentManager = requireActivity().supportFragmentManager
                supportFragmentManager
                    .beginTransaction()
                    .addToBackStack(System.currentTimeMillis().toString())
                    .hide(supportFragmentManager.fragments.let { it[it.size - 1] })
                    .add(R.id.container, ListeningStatsFragment())
                    .commit()
            }
        }
        return super.onPreferenceTreeClick(preference)
    }

    override fun onDestroy() {
        (activity as MainActivity).playerBottomSheet.shouldRetractBottomNavigation(false)
        super.onDestroy()
    }

}
