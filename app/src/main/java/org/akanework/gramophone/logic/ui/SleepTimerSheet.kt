package org.akanework.gramophone.logic.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.GramophonePlaybackService.Companion.TIMER_PAUSE_ON_SONG_END
import org.akanework.gramophone.logic.utils.CalculationUtils.convertDurationToTimeStamp

private val sleepTimerPresets = listOf(
    R.string.sleep_timer_15_min to 15,
    R.string.sleep_timer_30_min to 30,
    R.string.sleep_timer_45_min to 45,
    R.string.sleep_timer_1_hour to 60,
)

/**
 * Apple-Music-style sleep timer picker: a flat list of presets, with a checkmark on whichever one
 * is currently active. Reuses the same sheet chrome (drag handle, background, row style) as
 * [showSongOptionsSheet].
 *
 * [currentDurationMs] is the currently scheduled timer's original total duration - 0 if none is
 * running, or [TIMER_PAUSE_ON_SONG_END] for "When Current Song Ends" - used to decide which row
 * gets the checkmark.
 * [remainingMs] is how much of a positive-duration timer is left (ignored for the other two
 * states); shown as a live "Pauses in X" countdown under the title, ticking down once a second
 * while the sheet is open. Purely local - one Handler.postDelayed chain computed off a single
 * elapsed-realtime snapshot, not a repeated IPC round-trip to the service - so it's cheap to keep
 * running for however long the sheet stays open, and stops itself the moment it's dismissed.
 *
 * [onSelect] receives the chosen duration in milliseconds (0 to turn the timer off), or null for
 * "When Current Song Ends" - resolving that further is left to the caller (see
 * FullBottomSheet.openSleepTimerSheet()).
 */
fun showSleepTimerSheet(
    context: Context,
    currentDurationMs: Int,
    remainingMs: Int,
    onSelect: (Int?) -> Unit
) {
    val dialog = BottomSheetDialog(context)
    val root = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_sleep_timer, null)
    val optionsContainer = root.findViewById<LinearLayout>(R.id.optionsContainer)

    val pendingTimeView = root.findViewById<TextView>(R.id.pendingTime)
    val handler = Handler(Looper.getMainLooper())
    var tickRunnable: Runnable? = null
    when {
        currentDurationMs == TIMER_PAUSE_ON_SONG_END -> {
            pendingTimeView.text = context.getString(R.string.sleep_timer_pending_song_end)
            pendingTimeView.visibility = View.VISIBLE
        }
        currentDurationMs > 0 -> {
            pendingTimeView.visibility = View.VISIBLE
            val startElapsedRealtime = SystemClock.elapsedRealtime()
            val totalRemaining = remainingMs.toLong()
            val r = object : Runnable {
                override fun run() {
                    val left = (totalRemaining -
                            (SystemClock.elapsedRealtime() - startElapsedRealtime))
                        .coerceAtLeast(0)
                    pendingTimeView.text = context.getString(
                        R.string.sleep_timer_pending, convertDurationToTimeStamp(left)
                    )
                    if (left > 0) handler.postDelayed(this, 1000)
                }
            }
            tickRunnable = r
            r.run()
        }
        else -> pendingTimeView.visibility = View.GONE
    }
    dialog.setOnDismissListener { tickRunnable?.let { handler.removeCallbacks(it) } }

    fun addRow(label: String, checked: Boolean, onClick: () -> Unit) {
        val rowView: View = LayoutInflater.from(context)
            .inflate(R.layout.item_sleep_timer_option, optionsContainer, false)
        rowView.findViewById<TextView>(R.id.label).text = label
        rowView.findViewById<ImageView>(R.id.check).visibility =
            if (checked) View.VISIBLE else View.INVISIBLE
        rowView.setOnClickListener {
            dialog.dismiss()
            onClick()
        }
        optionsContainer.addView(rowView)
    }

    addRow(context.getString(R.string.sleep_timer_off), currentDurationMs == 0) {
        onSelect(0)
    }
    for ((labelRes, minutes) in sleepTimerPresets) {
        val durationMs = minutes * 60_000
        addRow(context.getString(labelRes), currentDurationMs == durationMs) {
            onSelect(durationMs)
        }
    }
    addRow(
        context.getString(R.string.sleep_timer_when_song_ends),
        currentDurationMs == TIMER_PAUSE_ON_SONG_END
    ) {
        onSelect(null)
    }

    dialog.setContentView(root)
    dialog.expandIfLandscape(context)
    dialog.show()
}
