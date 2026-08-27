package com.raghu.folio.ui.fragments.settings

import android.animation.ObjectAnimator
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import coil3.load
import coil3.request.error
import coil3.request.placeholder
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.raghu.folio.R
import com.raghu.folio.logic.dpToPx
import com.raghu.folio.logic.enableEdgeToEdgePaddingListener
import com.raghu.folio.logic.utils.ArtistImageStore
import com.raghu.folio.logic.utils.DurationUnit
import com.raghu.folio.logic.utils.LISTENING_STATS_BACKUP_DIR
import com.raghu.folio.logic.utils.ListeningStatsBackupUtils
import com.raghu.folio.logic.utils.ListeningStatsResult
import com.raghu.folio.logic.utils.ListeningStatsUtils
import com.raghu.folio.logic.utils.StatsPeriod
import com.raghu.folio.logic.utils.StatsShareCard
import com.raghu.folio.logic.utils.formatDurationAs
import com.raghu.folio.ui.LibraryViewModel
import com.raghu.folio.ui.fragments.BaseFragment
import java.time.Year

/**
 * Apple-Music-style "Listening Minutes Stats" screen: a big total for the selected time period
 * (minutes by default, tap the header to cycle through hours/days/weeks/months/years), plus a
 * ranked list switched between Top Songs and Top Artists (the latter grouped by album artist) via
 * a segmented control instead of stacking both as one long scroll. Reads whatever
 * FolioPlaybackService has accumulated in the listening-stats table (see
 * ListeningStatsUtils) - a handful of rows even for a heavy library, so this just does one query
 * per period switch; switching units or the songs/artists list reuses that same cached result, no
 * re-querying and no polling or background work while the screen is open.
 */
class ListeningStatsFragment : BaseFragment(false) {
    private val libraryViewModel: LibraryViewModel by activityViewModels()
    private var skeletonAnimator: ObjectAnimator? = null
    private var hasLoadedOnce = false

    // Set while the view exists so the file-picker result has something to refresh/report into.
    private var onBackupPicked: ((Int) -> Unit)? = null

    private val pickBackupFile = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        viewLifecycleOwner.lifecycleScope.launch {
            val restored = ListeningStatsBackupUtils.restoreFromUri(requireContext(), uri)
            onBackupPicked?.invoke(restored)
        }
    }

    // What to do once the user has confirmed the backup folder - the folder prompt only appears
    // the first time, after which both actions run straight away.
    private var afterFolderChosen: ((Uri) -> Unit)? = null

    private val pickBackupFolder = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri == null) return@registerForActivityResult
        ListeningStatsBackupUtils.rememberFolder(requireContext(), treeUri)
        afterFolderChosen?.invoke(treeUri)
    }

    /** Runs [action] against the saved backup folder, asking for one first if we don't have it. */
    private fun withBackupFolder(action: (Uri) -> Unit) {
        val saved = ListeningStatsBackupUtils.savedFolder(requireContext())
        if (saved != null) {
            action(saved)
        } else {
            afterFolderChosen = action
            pickBackupFolder.launch(ListeningStatsBackupUtils.initialPickerUri())
        }
    }

    private fun onRestored(root: View, restored: Int, refresh: () -> Unit) {
        if (restored >= 0) {
            Snackbar.make(
                root, getString(R.string.listening_stats_restore_success, restored),
                Snackbar.LENGTH_LONG
            ).show()
            refresh()
        } else {
            Snackbar.make(
                root, getString(R.string.listening_stats_restore_not_found),
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_listening_stats, container, false)
        val topAppBar = rootView.findViewById<MaterialToolbar>(R.id.topAppBar)
        rootView.findViewById<AppBarLayout>(R.id.appbarlayout).enableEdgeToEdgePaddingListener()
        topAppBar.setNavigationOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }
        topAppBar.overflowIcon = AppCompatResources.getDrawable(
            requireContext(), R.drawable.ic_more_vert_bold
        )!!.apply {
            setTint(MaterialColors.getColor(rootView, R.attr.contrast_themeColor))
        }

        val hoursTapTarget = rootView.findViewById<LinearLayout>(R.id.hoursTapTarget)
        val hoursLabel = rootView.findViewById<TextView>(R.id.hoursLabel)
        val hoursValue = rootView.findViewById<TextView>(R.id.hoursValue)
        val hoursSkeleton = rootView.findViewById<View>(R.id.hoursSkeleton)
        val toggleGroup = rootView.findViewById<MaterialButtonToggleGroup>(R.id.periodToggleGroup)
        val topListToggleGroup =
            rootView.findViewById<MaterialButtonToggleGroup>(R.id.topListToggleGroup)
        val topListContainer = rootView.findViewById<LinearLayout>(R.id.topListContainer)
        val emptyState = rootView.findViewById<TextView>(R.id.emptyState)

        skeletonAnimator = ObjectAnimator.ofFloat(hoursSkeleton, "alpha", 0.35f, 1f).apply {
            duration = 700
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
        }

        // Always starts each period's total off as minutes (reset in refresh() below) - tapping
        // the header cycles it through the other units, converting the same already-fetched total
        // in place rather than re-querying, so switching units is instant either way.
        var currentTotalMs = 0L
        var currentUnit = DurationUnit.MINUTES
        var lastResult: ListeningStatsResult? = null
        var showingArtists = false
        var currentPeriod = StatsPeriod.ALL

        fun labelFor(unit: DurationUnit) = when (unit) {
            DurationUnit.MINUTES -> R.string.listening_stats_minutes_played
            DurationUnit.HOURS -> R.string.listening_stats_hours_played
            DurationUnit.DAYS -> R.string.listening_stats_days_played
            DurationUnit.WEEKS -> R.string.listening_stats_weeks_played
            DurationUnit.MONTHS -> R.string.listening_stats_months_played
            DurationUnit.YEARS -> R.string.listening_stats_years_played
        }

        fun updateHoursDisplay() {
            hoursLabel.setText(labelFor(currentUnit))
            hoursValue.text = formatDurationAs(currentTotalMs, currentUnit).toString()
        }

        hoursTapTarget.setOnClickListener {
            val units = DurationUnit.entries
            currentUnit = units[(currentUnit.ordinal + 1) % units.size]
            updateHoursDisplay()
        }

        fun showSkeleton() {
            hoursValue.isVisible = false
            hoursSkeleton.isVisible = true
            skeletonAnimator?.start()
        }

        fun hideSkeleton() {
            skeletonAnimator?.cancel()
            hoursSkeleton.alpha = 1f
            hoursSkeleton.isVisible = false
            hoursValue.isVisible = true
        }

        fun periodFor(checkedId: Int) = when (checkedId) {
            R.id.periodYear -> StatsPeriod.YEAR
            R.id.periodMonth -> StatsPeriod.MONTH
            R.id.periodWeek -> StatsPeriod.WEEK
            else -> StatsPeriod.ALL
        }

        // Re-renders whichever list (songs or artists) is currently selected from the cached
        // result - swapping the switch never touches the database, it's the same data just shown
        // differently, so this is effectively instant.
        fun bindTopList() {
            val result = lastResult ?: return
            topListContainer.removeAllViews()
            if (showingArtists) {
                for ((index, stat) in result.topArtists.withIndex()) {
                    val row = LayoutInflater.from(requireContext())
                        .inflate(R.layout.item_top_artist, topListContainer, false)
                    // Same local artist photo the artist page uses, falling back to album art.
                    val artistUri = ArtistImageStore.imageUriFor(
                        requireContext(),
                        stat.artist.title ?: "",
                        fallback = stat.artist.songList.firstOrNull()
                            ?.mediaMetadata?.artworkUri
                    )
                    row.findViewById<ImageView>(R.id.cover)
                        .load(artistUri) {
                            placeholder(R.drawable.ic_default_cover_artist)
                            error(R.drawable.ic_default_cover_artist)
                            // See ArtistSubFragment - pins the key so a replaced photo re-decodes.
                            artistUri?.toString()?.let { memoryCacheKey(it); diskCacheKey(it) }
                        }
                    row.findViewById<TextView>(R.id.name).text =
                        stat.artist.title ?: getString(R.string.unknown_artist)
                    row.findViewById<TextView>(R.id.minutes).text = getString(
                        R.string.listening_stats_minutes_played_row, stat.msPlayed / 60_000
                    )
                    row.findViewById<TextView>(R.id.rank).text = (index + 1).toString()
                    topListContainer.addView(row)
                }
                emptyState.isVisible = result.topArtists.isEmpty()
            } else {
                for ((index, stat) in result.topSongs.withIndex()) {
                    val row = LayoutInflater.from(requireContext())
                        .inflate(R.layout.item_top_song, topListContainer, false)
                    row.findViewById<ImageView>(R.id.cover).load(stat.song.mediaMetadata.artworkUri) {
                        placeholder(R.drawable.ic_default_cover)
                        error(R.drawable.ic_default_cover)
                    }
                    row.findViewById<TextView>(R.id.title).text = stat.song.mediaMetadata.title
                    row.findViewById<TextView>(R.id.subtitle).text = getString(
                        R.string.listening_stats_minutes_played_row, stat.msPlayed / 60_000
                    )
                    row.findViewById<TextView>(R.id.rank).text = (index + 1).toString()
                    topListContainer.addView(row)
                }
                emptyState.isVisible = result.topSongs.isEmpty()
            }
        }

        topListToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                showingArtists = checkedId == R.id.topListArtists
                bindTopList()
            }
        }

        fun refresh(period: StatsPeriod) {
            currentPeriod = period
            val songs = libraryViewModel.mediaItemList.value ?: emptyList()
            val albumArtists = libraryViewModel.albumArtistItemList.value ?: emptyList()
            // Only the very first load (nothing on screen yet) shows the skeleton - the query is
            // a single indexed SQLite read, fast enough that re-showing it on every period switch
            // (when a value is already visible) just reads as an unwanted blink instead of a
            // loading state, so later switches update the number in place once the result lands.
            if (!hasLoadedOnce) showSkeleton()
            viewLifecycleOwner.lifecycleScope.launch {
                val result = ListeningStatsUtils.getStats(
                    requireContext(), period, songs, albumArtists
                )

                currentTotalMs = result.totalMs
                currentUnit = DurationUnit.MINUTES
                updateHoursDisplay()
                hideSkeleton()
                hasLoadedOnce = true

                lastResult = result
                bindTopList()
            }
        }

        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) refresh(periodFor(checkedId))
        }
        refresh(StatsPeriod.ALL)

        onBackupPicked = { restored -> onRestored(rootView, restored) { refresh(currentPeriod) } }

        topAppBar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.refresh_stats -> refresh(currentPeriod)

                R.id.share_stats -> showShareCard(rootView, lastResult, currentPeriod)

                R.id.backup_stats -> withBackupFolder { tree ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        val ok = ListeningStatsBackupUtils.backupTo(requireContext(), tree)
                        Snackbar.make(
                            rootView,
                            if (ok) getString(
                                R.string.listening_stats_backup_success,
                                LISTENING_STATS_BACKUP_DIR
                            ) else getString(R.string.listening_stats_backup_failure),
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                }

                R.id.reset_stats -> MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.listening_stats_reset_confirm_title)
                    .setMessage(R.string.listening_stats_reset_confirm_body)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.listening_stats_reset) { _, _ ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            ListeningStatsBackupUtils.resetStats(requireContext())
                            Snackbar.make(
                                rootView, getString(R.string.listening_stats_reset_done),
                                Snackbar.LENGTH_LONG
                            ).show()
                            refresh(currentPeriod)
                        }
                    }
                    .show()

                R.id.restore_stats -> withBackupFolder { tree ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        // Newest backup in the chosen folder; if it holds none, let the user point
                        // at a file directly.
                        val restored =
                            ListeningStatsBackupUtils.restoreLatestFrom(requireContext(), tree)
                        if (restored >= 0) {
                            onRestored(rootView, restored) { refresh(currentPeriod) }
                        } else {
                            pickBackupFile.launch(arrayOf("application/json", "text/plain", "*/*"))
                        }
                    }
                }
            }
            true
        }

        return rootView
    }


    /**
     * The share card, previewed in a sheet: which card (a summary, or a rundown of just the
     * artists or just the songs), which colour way, which background effect, and which parts to
     * leave off entirely.
     *
     * Artwork is decoded once up front, off the main thread, and reused for every combination -
     * so changing anything in the sheet is a redraw and nothing more. Only the preview is
     * rendered while the sheet is open; the full-size card is rendered on the way out to the
     * share sheet, so browsing looks never allocates a bitmap that isn't going anywhere.
     */
    private fun showShareCard(root: View, result: ListeningStatsResult?, period: StatsPeriod) {
        val stats = result
        if (stats == null || stats.totalMs <= 0L) {
            Snackbar.make(
                root, getString(R.string.listening_stats_share_empty), Snackbar.LENGTH_LONG
            ).show()
            return
        }
        val context = requireContext()
        val view = layoutInflater.inflate(R.layout.dialog_share_stats, null)
        val preview = view.findViewById<ImageView>(R.id.preview)
        val swatches = view.findViewById<LinearLayout>(R.id.swatches)
        val cardTypeGroup = view.findViewById<MaterialButtonToggleGroup>(R.id.cardTypeGroup)
        val sheet = BottomSheetDialog(context)
        sheet.setContentView(view)
        // Opened all the way rather than at the collapsed peek height, so the Share button is
        // reachable without dragging the sheet up first - and it can't slip back to peek either.
        sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        sheet.behavior.skipCollapsed = true

        fun minutesOf(ms: Long) =
            getString(R.string.listening_stats_minutes_played_row, ms / 60_000L)

        val topArtist = stats.topArtists.firstOrNull()
        var data = StatsShareCard.Data(
            artistsLabel = getString(R.string.listening_stats_top_artists),
            songsLabel = getString(R.string.listening_stats_top_songs),
            artists = stats.topArtists.take(5).map {
                StatsShareCard.Entry(
                    it.artist.title ?: getString(R.string.unknown_artist),
                    minutesOf(it.msPlayed), null
                )
            },
            songs = stats.topSongs.take(5).map {
                StatsShareCard.Entry(
                    it.song.mediaMetadata.title?.toString() ?: "",
                    minutesOf(it.msPlayed), null
                )
            },
            totalLabel = getString(R.string.listening_stats_share_minutes),
            totalValue = "%,d".format(stats.totalMs / 60_000L),
            topArtistLabel = getString(R.string.listening_stats_share_top_artist),
            topArtist = topArtist?.artist?.title ?: getString(R.string.unknown_artist),
            hero = null,
        )

        var options = StatsShareCard.Options(style = StatsShareCard.styles.first())
        val swatchViews = mutableListOf<Pair<View, StatsShareCard.Style>>()


        fun redraw() {
            preview.setImageBitmap(StatsShareCard.render(data, options, 540, 960))
            // The chosen colour is the opaque one; the rest sit back, so the row reads as a
            // choice already made rather than twelve equal options.
            for ((dot, style) in swatchViews) dot.alpha = if (style == options.style) 1f else 0.45f
        }

        for (style in StatsShareCard.styles) {
            val size = 40.dpToPx(context)
            val dot = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = 12.dpToPx(context)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(style.page)
                    // A ring in the card's own colour, so each swatch previews both halves of
                    // the colour way rather than just its background.
                    setStroke(4.dpToPx(context), style.card)
                }
                contentDescription = style.name
                setOnClickListener {
                    options = options.copy(style = style)
                    redraw()
                }
            }
            swatchViews.add(dot to style)
            swatches.addView(dot)
        }


        cardTypeGroup.check(R.id.cardSummary)
        cardTypeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            options = options.copy(
                type = when (checkedId) {
                    R.id.cardArtists -> StatsShareCard.CardType.ARTISTS
                    R.id.cardSongs -> StatsShareCard.CardType.SONGS
                    else -> StatsShareCard.CardType.SUMMARY
                }
            )
            redraw()
        }

        redraw()
        viewLifecycleOwner.lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                // The folder index is cached and only re-read when the folder's own timestamp
                // changes; a scan that happened while the images permission was still missing
                // sticks around as an empty map, and every artist then silently falls back to
                // album art. One forced re-scan here costs a single directory listing.
                ArtistImageStore.invalidate()

                fun artistUri(stat: com.raghu.folio.logic.utils.ArtistPlayStat) =
                    ArtistImageStore.imageUriFor(
                        context, stat.artist.title ?: "",
                        fallback = stat.artist.songList.firstOrNull()?.mediaMetadata?.artworkUri
                    )
                val heroUri = topArtist?.let { artistUri(it) }
                    ?: stats.topSongs.firstOrNull()?.song?.mediaMetadata?.artworkUri
                Triple(
                    StatsShareCard.loadBitmap(context, heroUri),
                    // Row artwork is small on the card, so it is decoded small too.
                    stats.topArtists.take(5)
                        .map { StatsShareCard.loadBitmap(context, artistUri(it), 400) },
                    stats.topSongs.take(5).map {
                        StatsShareCard.loadBitmap(context, it.song.mediaMetadata.artworkUri, 400)
                    }
                )
            }
            data = data.copy(
                hero = loaded.first,
                artists = data.artists.mapIndexed { i, e ->
                    e.copy(image = loaded.second.getOrNull(i))
                },
                songs = data.songs.mapIndexed { i, e ->
                    e.copy(image = loaded.third.getOrNull(i))
                },
            )
            redraw()
        }

        view.findViewById<MaterialButton>(R.id.shareButton).setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val uri = withContext(Dispatchers.IO) {
                    StatsShareCard.renderAndWrite(context, data, options)
                }
                if (uri == null) {
                    Snackbar.make(
                        root, getString(R.string.listening_stats_share_failed),
                        Snackbar.LENGTH_LONG
                    ).show()
                    return@launch
                }
                sheet.dismiss()
                startActivity(
                    StatsShareCard.shareIntent(uri, getString(R.string.listening_stats_share))
                )
            }
        }
        sheet.show()
    }



    override fun onDestroyView() {
        skeletonAnimator?.cancel()
        skeletonAnimator = null
        super.onDestroyView()
    }
}
