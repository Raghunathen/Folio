package com.raghu.folio.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.RemoteViews
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import androidx.preference.PreferenceManager
import com.google.common.util.concurrent.MoreExecutors
import com.raghu.folio.R
import com.raghu.folio.logic.AudiobookPlaybackService
import com.raghu.folio.ui.MainActivity

/**
 * Home screen widget showing the current audiobook (cover, title, author, progress) with
 * play/pause and skip buttons. Rendered from [WidgetStateStore], which the playback service keeps
 * up to date; button taps open a short-lived [MediaController] connection just long enough to
 * send the corresponding command, then release it.
 */
class AudiobookWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val ACTION_PLAY_PAUSE = "com.raghu.folio.widget.ACTION_PLAY_PAUSE"
        private const val ACTION_SKIP_FORWARD = "com.raghu.folio.widget.ACTION_SKIP_FORWARD"
        private const val ACTION_SKIP_BACKWARD = "com.raghu.folio.widget.ACTION_SKIP_BACKWARD"
        private const val COVER_TARGET_PX = 200

        /** Re-renders every placed instance of this widget from the latest [WidgetStateStore] state. */
        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, AudiobookWidgetProvider::class.java))
            if (ids.isEmpty()) return
            manager.updateAppWidget(ids, buildRemoteViews(context))
        }

        private fun buildRemoteViews(context: Context): RemoteViews {
            val state = WidgetStateStore.read(context)
            val views = RemoteViews(context.packageName, R.layout.widget_audiobook)

            views.setTextViewText(R.id.widget_title, state.title ?: context.getString(R.string.app_name))
            views.setTextViewText(R.id.widget_author, state.author ?: "")

            val progress = if (state.durationMs > 0) {
                ((state.positionMs.coerceIn(0, state.durationMs) * 1000) / state.durationMs).toInt()
            } else 0
            views.setProgressBar(R.id.widget_progress, 1000, progress, false)

            views.setImageViewResource(
                R.id.widget_play_pause,
                if (state.isPlaying) R.drawable.ic_apple_pause else R.drawable.ic_apple_play,
            )

            val cover = state.coverUri?.let { loadCoverBitmap(context, it) }
            if (cover != null) {
                views.setImageViewBitmap(R.id.widget_cover, cover)
            } else {
                views.setImageViewResource(R.id.widget_cover, R.drawable.ic_default_cover_fixed)
            }

            views.setOnClickPendingIntent(R.id.widget_play_pause, actionPendingIntent(context, ACTION_PLAY_PAUSE))
            views.setOnClickPendingIntent(R.id.widget_skip_forward, actionPendingIntent(context, ACTION_SKIP_FORWARD))
            views.setOnClickPendingIntent(R.id.widget_skip_backward, actionPendingIntent(context, ACTION_SKIP_BACKWARD))

            val openApp = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.widget_cover, openApp)
            views.setOnClickPendingIntent(R.id.widget_title, openApp)

            return views
        }

        private fun loadCoverBitmap(context: Context, uriString: String): Bitmap? = try {
            val uri = Uri.parse(uriString)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > COVER_TARGET_PX * 2 || bounds.outHeight / sample > COVER_TARGET_PX * 2) {
                sample *= 2
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        } catch (e: Exception) {
            null
        }

        private fun actionPendingIntent(context: Context, action: String): PendingIntent {
            val intent = Intent(context, AudiobookWidgetProvider::class.java).setAction(action)
            return PendingIntent.getBroadcast(
                context, action.hashCode(), intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val views = buildRemoteViews(context)
        appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, views) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_PLAY_PAUSE, ACTION_SKIP_FORWARD, ACTION_SKIP_BACKWARD ->
                handleControlAction(context, intent.action!!)
        }
    }

    private fun handleControlAction(context: Context, action: String) {
        val pendingResult = goAsync()
        val token = SessionToken(context, ComponentName(context, AudiobookPlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            try {
                val controller = future.get()
                when (action) {
                    ACTION_PLAY_PAUSE -> if (controller.isPlaying) controller.pause() else controller.play()
                    ACTION_SKIP_FORWARD -> controller.sendCustomCommand(
                        SessionCommand(AudiobookPlaybackService.COMMAND_SKIP_FORWARD, Bundle.EMPTY),
                        Bundle().apply {
                            putInt(
                                AudiobookPlaybackService.EXTRA_SECONDS,
                                PreferenceManager.getDefaultSharedPreferences(context)
                                    .getInt("skip_forward_seconds", 30),
                            )
                        },
                    )
                    ACTION_SKIP_BACKWARD -> controller.sendCustomCommand(
                        SessionCommand(AudiobookPlaybackService.COMMAND_SKIP_BACKWARD, Bundle.EMPTY),
                        Bundle().apply {
                            putInt(
                                AudiobookPlaybackService.EXTRA_SECONDS,
                                PreferenceManager.getDefaultSharedPreferences(context)
                                    .getInt("skip_backward_seconds", 15),
                            )
                        },
                    )
                }
                controller.release()
            } finally {
                pendingResult.finish()
            }
        }, MoreExecutors.directExecutor())
    }
}
