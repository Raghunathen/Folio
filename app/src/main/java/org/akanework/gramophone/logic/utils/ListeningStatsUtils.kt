package org.akanework.gramophone.logic.utils

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.akanework.gramophone.logic.data.db.AppDatabase
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class StatsPeriod { WEEK, MONTH, YEAR, ALL }

enum class DurationUnit { MINUTES, HOURS, DAYS, WEEKS, MONTHS, YEARS }

data class ArtistPlayStat(val artist: MediaStoreUtils.Artist, val msPlayed: Long)
data class SongPlayStat(val song: MediaItem, val msPlayed: Long)

data class ListeningStatsResult(
    val totalMs: Long,
    val topSongs: List<SongPlayStat>,
    val topArtists: List<ArtistPlayStat>,
)

/**
 * Converts a total into a specific unit - always minutes by default, cycled through the other
 * units by tapping the stats screen's header (see ListeningStatsFragment). Plain integer
 * division, no calendar math needed since these are just magnitudes, not calendar-aligned
 * buckets.
 */
fun formatDurationAs(totalMs: Long, unit: DurationUnit): Long = when (unit) {
    DurationUnit.MINUTES -> totalMs / 60_000L
    DurationUnit.HOURS -> totalMs / 3_600_000L
    DurationUnit.DAYS -> totalMs / 86_400_000L
    DurationUnit.WEEKS -> totalMs / (86_400_000L * 7)
    DurationUnit.MONTHS -> totalMs / (86_400_000L * 30)
    DurationUnit.YEARS -> totalMs / (86_400_000L * 365)
}

/**
 * Reads the listening-time totals GramophonePlaybackService records (see its statsFlushRunnable)
 * and aggregates them for the stats screen. Song/artist attribution is done here rather than
 * stored in the database - the library (already loaded in memory) already knows which songs
 * belong to which artist, so there's no need to duplicate that mapping in a table. One query
 * (getPerSongTotals) feeds both rankings. [artists] is whichever grouping the caller wants shown
 * as "Top Artists" (the stats screen uses album artists).
 */
object ListeningStatsUtils {
    private fun fromDayEpoch(period: StatsPeriod): Long {
        val today = LocalDate.now()
        return when (period) {
            StatsPeriod.WEEK -> today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).toEpochDay()
            StatsPeriod.MONTH -> today.withDayOfMonth(1).toEpochDay()
            StatsPeriod.YEAR -> today.withDayOfYear(1).toEpochDay()
            StatsPeriod.ALL -> Long.MIN_VALUE
        }
    }

    suspend fun getStats(
        context: Context,
        period: StatsPeriod,
        songs: List<MediaItem>,
        artists: List<MediaStoreUtils.Artist>,
        topLimit: Int = 10,
    ): ListeningStatsResult = withContext(Dispatchers.IO) {
        val totals = try {
            AppDatabase.getInstance(context).listeningStatDao()
                .getPerSongTotals(fromDayEpoch(period))
        } catch (e: Exception) {
            Log.e("ListeningStatsUtils", "Failed to query listening stats", e)
            emptyList()
        }
        val perSongMs = HashMap<Long, Long>(totals.size)
        var total = 0L
        for (t in totals) {
            perSongMs[t.mediaItemId] = t.msPlayed
            total += t.msPlayed
        }

        val songById = songs.associateBy { it.mediaId.toLongOrNull() }
        val topSongs = totals.asSequence()
            .sortedByDescending { it.msPlayed }
            .mapNotNull { t -> songById[t.mediaItemId]?.let { SongPlayStat(it, t.msPlayed) } }
            .take(topLimit)
            .toList()

        val topArtists = artists.mapNotNull { artist ->
            var sum = 0L
            for (song in artist.songList) {
                val id = song.mediaId.toLongOrNull() ?: continue
                sum += perSongMs[id] ?: 0L
            }
            if (sum > 0) ArtistPlayStat(artist, sum) else null
        }.sortedByDescending { it.msPlayed }.take(topLimit)

        ListeningStatsResult(total, topSongs, topArtists)
    }
}
