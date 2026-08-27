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

package org.akanework.gramophone.ui.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.ui.ItemHeightHelper
import org.akanework.gramophone.logic.ui.MyRecyclerView

class SongSearchHeaderAdapter(
    private val context: Context,
    private val onSearch: (String) -> Unit
) : MyRecyclerView.Adapter<SongSearchHeaderAdapter.ViewHolder>(), ItemHeightHelper {

    private val itemHeight = context.resources.getDimensionPixelSize(R.dimen.song_search_header_height)
    private var editTextRef: EditText? = null

    // The adapter, not the view, owns the query and whether the field was focused. Results arriving
    // reshuffle the adapters around this row, and the row can be rebound (or rebuilt) at any point;
    // without this, every search silently wiped the field's text and dropped the caret, so you had
    // to tap back in and retype.
    private var currentQuery = ""
    private var shouldHoldFocus = false

    // Set while we write the text ourselves, so restoring state doesn't look like the user typing
    // and re-trigger a search.
    private var suppressCallback = false

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val editText: EditText = view.findViewById(R.id.search_edit_text)
        val clearButton: MaterialButton = view.findViewById(R.id.clear_search_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(
            R.layout.adapter_song_search_header, parent, false
        )
        val holder = ViewHolder(view)
        holder.editText.addTextChangedListener { text ->
            val value = text?.toString() ?: ""
            holder.clearButton.visibility = if (value.isNotEmpty()) View.VISIBLE else View.GONE
            if (suppressCallback) return@addTextChangedListener
            currentQuery = value
            onSearch(value)
        }
        holder.editText.setOnFocusChangeListener { _, hasFocus ->
            if (!suppressCallback) shouldHoldFocus = hasFocus
        }
        holder.clearButton.setOnClickListener {
            holder.editText.setText("")
            holder.editText.requestFocus()
        }
        return holder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        editTextRef = holder.editText
        if (holder.editText.text.toString() != currentQuery) {
            suppressCallback = true
            holder.editText.setText(currentQuery)
            holder.editText.setSelection(currentQuery.length)
            suppressCallback = false
        }
        holder.clearButton.visibility =
            if (currentQuery.isNotEmpty()) View.VISIBLE else View.GONE
        // Carry the caret back over to whichever view now represents this row.
        if (shouldHoldFocus && !holder.editText.hasFocus()) {
            holder.editText.requestFocus()
            holder.editText.setSelection(holder.editText.text.length)
        }
    }

    fun clearText() {
        currentQuery = ""
        shouldHoldFocus = false
        editTextRef?.takeIf { it.text.isNotEmpty() }?.setText("")
    }

    override fun getItemCount(): Int = 1

    override fun getItemHeightFromZeroTo(to: Int): Int = if (to > 0) itemHeight else 0
}
