package com.raghu.folio.ui.fragments.settings

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import com.raghu.folio.R
import com.raghu.folio.logic.data.db.AppDatabase
import com.raghu.folio.ui.fragments.BasePreferenceFragment
import com.raghu.folio.ui.fragments.BaseSettingFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class ListeningStatsFragment : BaseSettingFragment(R.string.settings_listening_stats,
    { ListeningStatsTopFragment() })

/**
 * Simple, read-only stats screen (total time listened, current/longest streak, days active) built
 * from [com.raghu.folio.logic.data.db.entity.ListeningStat] rows that
 * [com.raghu.folio.logic.utils.audiobook.AudiobookPlayerController] accumulates while playing.
 * Uses non-selectable [Preference] rows purely to reuse the existing settings-screen chrome
 * (toolbar/back nav via [BaseSettingFragment]) rather than a bespoke layout.
 */
class ListeningStatsTopFragment : BasePreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val screen = preferenceManager.createPreferenceScreen(requireContext())
        preferenceScreen = screen

        val totalPref = infoPreference("stats_total", R.string.listening_stats_total_time)
        val currentStreakPref = infoPreference("stats_current_streak", R.string.listening_stats_current_streak)
        val longestStreakPref = infoPreference("stats_longest_streak", R.string.listening_stats_longest_streak)
        val daysPref = infoPreference("stats_days", R.string.listening_stats_days_active)
        screen.addPreference(totalPref)
        screen.addPreference(currentStreakPref)
        screen.addPreference(longestStreakPref)
        screen.addPreference(daysPref)

        lifecycleScope.launch {
            val db = AppDatabase.getInstance(requireContext().applicationContext)
            val (totalMs, stats) = withContext(Dispatchers.IO) {
                val dao = db.listeningStatDao()
                dao.getTotalMs() to dao.getAll()
            }
            if (!isAdded) return@launch

            val listenedDates = stats.mapNotNull { stat ->
                if (stat.msListened <= 0) null else runCatching { LocalDate.parse(stat.date) }.getOrNull()
            }.toSet()
            val (currentStreak, longestStreak) = computeStreaks(listenedDates)

            totalPref.summary = formatDuration(totalMs)
            currentStreakPref.summary = getString(R.string.listening_stats_streak_days_format, currentStreak)
            longestStreakPref.summary = getString(R.string.listening_stats_streak_days_format, longestStreak)
            daysPref.summary = getString(R.string.listening_stats_streak_days_format, listenedDates.size)
        }
    }

    private fun infoPreference(prefKey: String, titleRes: Int) = Preference(requireContext()).apply {
        key = prefKey
        isSelectable = false
        title = getString(titleRes)
    }

    /** Returns (current streak ending today or yesterday, longest streak ever) in days. */
    private fun computeStreaks(dates: Set<LocalDate>): Pair<Int, Int> {
        if (dates.isEmpty()) return 0 to 0

        val today = LocalDate.now()
        var current = 0
        var cursor = if (dates.contains(today)) today else today.minusDays(1)
        while (dates.contains(cursor)) {
            current++
            cursor = cursor.minusDays(1)
        }

        val sorted = dates.sorted()
        var longest = 1
        var run = 1
        for (i in 1 until sorted.size) {
            run = if (sorted[i] == sorted[i - 1].plusDays(1)) run + 1 else 1
            if (run > longest) longest = run
        }
        return current to longest
    }

    private fun formatDuration(ms: Long): String {
        val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(ms)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) getString(R.string.listening_stats_duration_hm, hours, minutes)
        else getString(R.string.listening_stats_duration_m, minutes)
    }
}
