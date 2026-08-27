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
import com.raghu.folio.R
import com.raghu.folio.ui.fragments.BasePreferenceFragment
import com.raghu.folio.ui.fragments.BaseSettingFragment

class AudioSettingsFragment : BaseSettingFragment(R.string.settings_audio,
    { AudioSettingsTopFragment() })

class AudioSettingsTopFragment : BasePreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_audio, rootKey)
    }
}
