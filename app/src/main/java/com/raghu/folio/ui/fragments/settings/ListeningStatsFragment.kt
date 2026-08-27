package com.raghu.folio.ui.fragments.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.raghu.folio.R
import com.raghu.folio.logic.data.db.AppDatabase
import com.raghu.folio.logic.data.db.entity.ListeningStat
import com.raghu.folio.logic.notifications.WeeklyStatsWorker
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

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val screen = preferenceManager.createPreferenceScreen(requireContext())
        preferenceScreen = screen

        val weeklyRecapPref = SwitchPreferenceCompat(requireContext()).apply {
            key = WeeklyStatsWorker.PREF_ENABLED
            isIconSpaceReserved = false
            setDefaultValue(true)
            title = getString(R.string.weekly_recap_notifications_title)
            summary = getString(R.string.weekly_recap_notifications_summary)
            setOnPreferenceChangeListener { _, newValue ->
                if (newValue as Boolean) {
                    WeeklyStatsWorker.schedule(requireContext())
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                } else {
                    WeeklyStatsWorker.cancel(requireContext())
                }
                true
            }
        }
        screen.addPreference(weeklyRecapPref)

        val totalPref = infoPreference("stats_total", R.string.listening_stats_total_time)
        val currentStreakPref = infoPreference("stats_current_streak", R.string.listening_stats_current_streak)
        val longestStreakPref = infoPreference("stats_longest_streak", R.string.listening_stats_longest_streak)
        val daysPref = infoPreference("stats_days", R.string.listening_stats_days_active)
        val pastWeekPref = infoPreference("stats_past_week", R.string.listening_stats_past_week)
        val pastMonthPref = infoPreference("stats_past_month", R.string.listening_stats_past_month)
        val past6MonthsPref = infoPreference("stats_past_6_months", R.string.listening_stats_past_6_months)
        val pastYearPref = infoPreference("stats_past_year", R.string.listening_stats_past_year)
        screen.addPreference(totalPref)
        screen.addPreference(currentStreakPref)
        screen.addPreference(longestStreakPref)
        screen.addPreference(daysPref)
        screen.addPreference(pastWeekPref)
        screen.addPreference(pastMonthPref)
        screen.addPreference(past6MonthsPref)
        screen.addPreference(pastYearPref)

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
            val today = LocalDate.now()

            totalPref.summary = formatDuration(totalMs)
            currentStreakPref.summary = getString(R.string.listening_stats_streak_days_format, currentStreak)
            longestStreakPref.summary = getString(R.string.listening_stats_streak_days_format, longestStreak)
            daysPref.summary = getString(R.string.listening_stats_streak_days_format, listenedDates.size)
            pastWeekPref.summary = formatDuration(sumSince(stats, today.minusDays(6)))
            pastMonthPref.summary = formatDuration(sumSince(stats, today.minusDays(29)))
            past6MonthsPref.summary = formatDuration(sumSince(stats, today.minusMonths(6)))
            pastYearPref.summary = formatDuration(sumSince(stats, today.minusYears(1)))
        }
    }

    private fun sumSince(stats: List<ListeningStat>, since: LocalDate): Long = stats.filter { stat ->
        runCatching { LocalDate.parse(stat.date) }.getOrNull()?.let { it >= since } == true
    }.sumOf { it.msListened }

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
