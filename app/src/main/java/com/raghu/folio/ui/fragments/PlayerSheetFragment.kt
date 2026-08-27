package com.raghu.folio.ui.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil3.asImage
import coil3.load
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.raghu.folio.R
import com.raghu.folio.logic.data.db.AppDatabase
import com.raghu.folio.logic.data.db.entity.Bookmark
import com.raghu.folio.ui.PlayerViewModel
import com.raghu.folio.ui.adapters.ChapterAdapter
import com.raghu.folio.ui.util.CoverPlaceholderGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Full playback controls (transport, speed, sleep timer, bookmarks, chapters) for one book. */
class PlayerSheetFragment : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_BOOK_ID = "book_id"
        private val SPEEDS = floatArrayOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)

        fun newInstance(bookId: Long) = PlayerSheetFragment().apply {
            arguments = Bundle().apply { putLong(ARG_BOOK_ID, bookId) }
        }
    }

    private val playerViewModel: PlayerViewModel by activityViewModels()
    private val bookId: Long by lazy { requireArguments().getLong(ARG_BOOK_ID) }
    private var speedIndex = 1
    private lateinit var chapterAdapter: ChapterAdapter
    private var userIsSeeking = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_player_sheet, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val titleView: TextView = view.findViewById(R.id.player_title)
        val authorView: TextView = view.findViewById(R.id.player_author)
        val descriptionView: TextView = view.findViewById(R.id.player_description)
        val coverView: ImageView = view.findViewById(R.id.player_cover)
        val seekBar: SeekBar = view.findViewById(R.id.player_seekbar)
        val positionText: TextView = view.findViewById(R.id.player_position_text)
        val durationText: TextView = view.findViewById(R.id.player_duration_text)
        val playPauseButton: ImageButton = view.findViewById(R.id.player_play_pause)
        val skipBackward: ImageButton = view.findViewById(R.id.player_skip_backward)
        val skipForward: ImageButton = view.findViewById(R.id.player_skip_forward)
        val speedButton: MaterialButton = view.findViewById(R.id.player_speed_button)
        val sleepTimerButton: MaterialButton = view.findViewById(R.id.player_sleep_timer_button)
        val bookmarkButton: MaterialButton = view.findViewById(R.id.player_bookmark_button)
        val chaptersList: RecyclerView = view.findViewById(R.id.chapters_list)

        chapterAdapter = ChapterAdapter { chapter ->
            playerViewModel.seekAbsolute(chapter.startMs)
        }
        chaptersList.layoutManager = LinearLayoutManager(requireContext())
        chaptersList.adapter = chapterAdapter

        playerViewModel.title.observe(viewLifecycleOwner) { titleView.text = it ?: "" }
        playerViewModel.author.observe(viewLifecycleOwner) { authorView.text = it ?: "" }
        playerViewModel.isPlaying.observe(viewLifecycleOwner) { playing ->
            playPauseButton.setImageResource(
                if (playing) R.drawable.ic_apple_pause else R.drawable.ic_apple_play
            )
            playPauseButton.contentDescription = getString(if (playing) R.string.pause else R.string.play)
        }
        playerViewModel.durationMs.observe(viewLifecycleOwner) { duration ->
            seekBar.max = duration.toInt().coerceAtLeast(0)
            durationText.text = formatMs(duration)
        }
        playerViewModel.positionMs.observe(viewLifecycleOwner) { position ->
            if (!userIsSeeking) {
                seekBar.progress = position.toInt().coerceAtLeast(0)
                positionText.text = formatMs(position)
            }
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) positionText.text = formatMs(progress.toLong())
            }

            override fun onStartTrackingTouch(bar: SeekBar?) {
                userIsSeeking = true
            }

            override fun onStopTrackingTouch(bar: SeekBar?) {
                userIsSeeking = false
                playerViewModel.seekAbsolute(bar?.progress?.toLong() ?: 0L)
            }
        })

        playPauseButton.setOnClickListener { playerViewModel.playPause() }
        skipForward.setOnClickListener { playerViewModel.skipForward() }
        skipBackward.setOnClickListener { playerViewModel.skipBackward() }

        speedButton.text = getString(R.string.playback_speed_value, SPEEDS[speedIndex])
        speedButton.setOnClickListener {
            speedIndex = (speedIndex + 1) % SPEEDS.size
            val speed = SPEEDS[speedIndex]
            speedButton.text = getString(R.string.playback_speed_value, speed)
            playerViewModel.setSpeed(speed)
        }

        sleepTimerButton.setOnClickListener { showSleepTimerDialog() }
        bookmarkButton.setOnClickListener { showAddBookmarkDialog() }

        loadChapters()
        loadBookDetails(coverView, descriptionView)
    }

    private fun loadBookDetails(coverView: ImageView, descriptionView: TextView) {
        viewLifecycleOwner.lifecycleScope.launch {
            val book = withContext(Dispatchers.IO) {
                AppDatabase.getInstance(requireContext()).bookDao().getBookById(bookId)
            } ?: return@launch
            val placeholder = CoverPlaceholderGenerator.generate(
                coverView.context, book.title,
                resources.getDimensionPixelSize(R.dimen.book_cover_size_large)
            )
            if (book.coverUri != null) {
                coverView.load(book.coverUri) {
                    placeholder(placeholder.asImage())
                    error(placeholder.asImage())
                }
            } else {
                coverView.setImageDrawable(placeholder)
            }
            if (!book.description.isNullOrBlank()) {
                descriptionView.text = book.description
                descriptionView.visibility = View.VISIBLE
            }
        }
    }

    private fun loadChapters() {
        viewLifecycleOwner.lifecycleScope.launch {
            val chapters = withContext(Dispatchers.IO) {
                AppDatabase.getInstance(requireContext()).chapterDao().getChaptersForBook(bookId)
            }
            chapterAdapter.submitList(chapters)
        }
    }

    private fun showSleepTimerDialog() {
        val options = arrayOf(
            getString(R.string.sleep_timer_off),
            getString(R.string.sleep_timer_15_min),
            getString(R.string.sleep_timer_30_min),
            getString(R.string.sleep_timer_45_min),
            getString(R.string.sleep_timer_1_hour),
        )
        val minutes = intArrayOf(0, 15, 30, 45, 60)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.sleep_timer)
            .setItems(options) { _, which ->
                if (minutes[which] == 0) {
                    playerViewModel.cancelSleepTimer()
                } else {
                    playerViewModel.startSleepTimer(minutes[which])
                }
            }
            .show()
    }

    private fun showAddBookmarkDialog() {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.bookmark_label_hint)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.add_bookmark)
            .setView(input)
            .setPositiveButton(R.string.add_bookmark) { _, _ ->
                val label = input.text?.toString()?.trim().takeUnless { it.isNullOrEmpty() }
                val position = playerViewModel.positionMs.value ?: 0L
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    AppDatabase.getInstance(requireContext()).bookmarkDao().addBookmark(
                        Bookmark(
                            bookId = bookId,
                            positionMs = position,
                            label = label,
                            createdAt = System.currentTimeMillis(),
                        )
                    )
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun formatMs(ms: Long): String {
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
        }
    }
}
