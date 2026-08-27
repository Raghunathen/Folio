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

package com.raghu.folio.ui

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.raghu.folio.logic.data.db.entity.AuthorWithBooks
import com.raghu.folio.logic.data.db.entity.BookWithProgress
import com.raghu.folio.logic.data.db.entity.CollectionWithBooks

/**
 * LibraryViewModel:
 *   A ViewModel that contains audiobook library information (authors/books/collections).
 * Used across the application. Replaces the old music-oriented (MediaStoreUtils-based) version.
 */
class LibraryViewModel : ViewModel() {
    val authorsWithBooks: MutableLiveData<List<AuthorWithBooks>> = MutableLiveData()
    val allBooksWithProgress: MutableLiveData<List<BookWithProgress>> = MutableLiveData()
    val continueListening: MutableLiveData<List<BookWithProgress>> = MutableLiveData()
    val collections: MutableLiveData<List<CollectionWithBooks>> = MutableLiveData()
    /** True while [com.raghu.folio.logic.utils.audiobook.AudiobookScanner] is walking the SAF tree. */
    val isScanning: MutableLiveData<Boolean> = MutableLiveData(false)
}