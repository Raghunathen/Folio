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

package com.raghu.folio.logic.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.raghu.folio.R
import com.raghu.folio.logic.data.db.AppDatabase
import com.raghu.folio.ui.MainActivity
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Periodic worker that posts a "here's what you listened to this week" notification, summarizing
 * the last 7 days from [com.raghu.folio.logic.data.db.entity.ListeningStat] (which is kept
 * forever regardless of whether the underlying audiobook files are later deleted/rescanned).
 * Scheduling is controlled by the "weekly_recap_notifications" preference (default on),
 * toggled from the Listening Stats settings screen.
 */
class WeeklyStatsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val stats = AppDatabase.getInstance(applicationContext).listeningStatDao().getAll()
        val since = LocalDate.now().minusDays(6)
        val weekMs = stats.filter { stat ->
            runCatching { LocalDate.parse(stat.date) }.getOrNull()?.let { it >= since } == true
        }.sumOf { it.msListened }
        // Nothing listened to this week - skip the notification rather than nag with a zero.
        if (weekMs <= 0L) return Result.success()

        showNotification(formatDuration(weekMs))
        return Result.success()
    }

    private fun formatDuration(ms: Long): String {
        val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(ms)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) {
            applicationContext.getString(R.string.listening_stats_duration_hm, hours, minutes)
        } else {
            applicationContext.getString(R.string.listening_stats_duration_m, minutes)
        }
    }

    private fun showNotification(durationText: String) {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.weekly_recap_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            )
        }
        val contentIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_vinyl)
            .setContentTitle(context.getString(R.string.weekly_recap_title))
            .setContentText(context.getString(R.string.weekly_recap_body, durationText))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "weekly_recap"
        const val NOTIFICATION_ID = 9001
        const val PREF_ENABLED = "weekly_recap_notifications"
        private const val WORK_NAME = "weekly_stats_recap"

        /** Schedules or cancels the periodic worker to match the persisted setting - call on
         *  app start so scheduling always agrees with the user's current preference. */
        fun scheduleIfEnabled(context: Context) {
            val enabled = PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(PREF_ENABLED, true)
            if (enabled) schedule(context) else cancel(context)
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WeeklyStatsWorker>(7, TimeUnit.DAYS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
