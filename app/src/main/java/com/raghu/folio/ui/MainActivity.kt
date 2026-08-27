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

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import coil3.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.raghu.folio.R
import com.raghu.folio.logic.data.db.AppDatabase
import com.raghu.folio.logic.enableEdgeToEdgeProperly
import com.raghu.folio.logic.postAtFrontOfQueueAsync
import com.raghu.folio.logic.utils.audiobook.AudiobookScanner

/**
 * MainActivity:
 *   Core of Folio, one and the only activity used across the application.
 *
 * A minimal placeholder shell for the audiobook pivot: hosts [HomeFragment.kt], scans the
 * SAF-picked Audiobooks folder into the Room database, and exposes [libraryViewModel] with the
 * result. Full multi-tab UI (Authors/Collections/Player) is built up from here incrementally.
 */
class MainActivity : AppCompatActivity() {

    val libraryViewModel: LibraryViewModel by viewModels()

    private val handler = Handler(Looper.getMainLooper())
    private val reportFullyDrawnRunnable = Runnable { if (!ready) reportFullyDrawn() }
    private var ready = false
    private var reportFullyDrawnCalled = false

    /** Rescans the SAF Audiobooks folder (no-op if none is set yet) and refreshes [libraryViewModel]. */
    fun updateLibrary(then: (() -> Unit)? = null) {
        // If library load takes more than 3s, exit splash to avoid ANR.
        if (!ready) handler.postDelayed(reportFullyDrawnRunnable, 3000)
        lifecycleScope.launch {
            AudiobookScanner.scanLibrary(this@MainActivity)
            refreshLibraryViewModel()
            if (!ready) reportFullyDrawn()
            then?.invoke()
        }
    }

    private suspend fun refreshLibraryViewModel() {
        val db = AppDatabase.getInstance(this)
        val authors = withContext(Dispatchers.IO) { db.authorDao().getAllAuthorsWithBooks() }
        val books = withContext(Dispatchers.IO) { db.bookDao().getAllBooksWithProgress() }
        libraryViewModel.authorsWithBooks.value = authors
        libraryViewModel.allBooksWithProgress.value = books
        libraryViewModel.continueListening.value = books.filter {
            it.progress != null && !it.progress.isFinished
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { !ready }
        enableEdgeToEdgeProperly()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        updateLibrary()
    }

    // https://twitter.com/Piwai/status/1529510076196630528
    override fun reportFullyDrawn() {
        handler.removeCallbacks(reportFullyDrawnRunnable)
        if (reportFullyDrawnCalled) return
        reportFullyDrawnCalled = true
        Choreographer.getInstance().postFrameCallback {
            Choreographer.getInstance().postFrameCallback {
                ready = true
                handler.postAtFrontOfQueueAsync {
                    super.reportFullyDrawn()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // we don't ever want covers to be the cause of service being killed by too high mem usage
        imageLoader.memoryCache?.clear()
    }
}
