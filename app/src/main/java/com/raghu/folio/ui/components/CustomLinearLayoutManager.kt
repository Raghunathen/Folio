package com.raghu.folio.ui.components

import android.content.Context
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.raghu.folio.logic.dpToPx

class CustomLinearLayoutManager(private val context: Context) : LinearLayoutManager(context) {
    override fun calculateExtraLayoutSpace(state: RecyclerView.State, extraLayoutSpace: IntArray) {
        extraLayoutSpace[0] = 512.dpToPx(context)
        extraLayoutSpace[1] = 512.dpToPx(context)
    }
}