package org.akanework.gramophone.logic.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.text.TextPaint
import android.text.TextUtils
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Draws the shareable cards for the listening-stats screen: a summary (the year down one side, the
 * top artist's photo, then top artists and top songs over the total listening time), or a single
 * ranked rundown of just the artists or just the songs.
 *
 * Flat colour and type, nothing else. The looks differ by colour way alone - patterned backdrops
 * were tried and dropped: behind five rows of text they fought every one of them for attention,
 * and the card stopped reading as a stats card.
 *
 * Painted straight onto a Canvas rather than inflated from a layout, because the output has to be
 * a fixed pixel size no matter what screen or font scale the device uses - a layout would come out
 * a different shape on every phone. Every coordinate below is in a 1080x1920 design space and the
 * canvas is scaled once to whatever size is being rendered, so text and shapes are re-rasterised
 * at the output resolution and stay sharp however large the card is asked for.
 */
object StatsShareCard {
    private const val TAG = "StatsShareCard"

    // Design space. Anything drawn below is in these units.
    private const val W = 1080f
    private const val H = 1920f
    private const val CARD_RADIUS = 44f
    private const val PAD = 52f

    /** Which card is being made. */
    enum class CardType { SUMMARY, ARTISTS, SONGS }

    /**
     * A colour way. [ink] is text on [card], [muted] the small labels, and [accent] the year - the
     * one place a colour other than the card's own two appears.
     */
    data class Style(
        val name: String,
        val page: Int,
        val card: Int,
        val ink: Int,
        val muted: Int,
        val accent: Int,
    )

    /** Everything the sheet lets the user choose. */
    data class Options(
        val type: CardType = CardType.SUMMARY,
        val style: Style,
    )

    // Light and dark cards alternate, so the row of swatches reads as genuinely separate looks
    // rather than as one look in twelve tints.
    val styles = listOf(
        Style("Sunbeam", 0xFF6C63FF.toInt(), 0xFFF4F1EA.toInt(), 0xFF101014.toInt(),
            0xFF6B6875.toInt(), 0xFFF2B705.toInt()),
        Style("Midnight", 0xFFE9E4DA.toInt(), 0xFF121212.toInt(), 0xFFF5F3EE.toInt(),
            0xFF97938C.toInt(), 0xFFA78BFA.toInt()),
        Style("Ember", 0xFFFF6B57.toInt(), 0xFFFFF6F0.toInt(), 0xFF2A140E.toInt(),
            0xFF8A6659.toInt(), 0xFFE1341E.toInt()),
        Style("Moss", 0xFFC8F250.toInt(), 0xFF14170F.toInt(), 0xFFF2F5EC.toInt(),
            0xFF9AA189.toInt(), 0xFFB6E24A.toInt()),
        Style("Tide", 0xFF0E4C5C.toInt(), 0xFFEAF3F2.toInt(), 0xFF0B2B33.toInt(),
            0xFF5E7E85.toInt(), 0xFF14B8A6.toInt()),
        Style("Dune", 0xFFE8DCC8.toInt(), 0xFF1B1A17.toInt(), 0xFFF3EDE1.toInt(),
            0xFFA49B88.toInt(), 0xFFE9A23B.toInt()),
        Style("Blush", 0xFFF5C7D8.toInt(), 0xFF1A1116.toInt(), 0xFFFDF2F7.toInt(),
            0xFFA98D99.toInt(), 0xFFFF7BAC.toInt()),
        Style("Slate", 0xFF94A3B8.toInt(), 0xFFF8FAFC.toInt(), 0xFF0F172A.toInt(),
            0xFF64748B.toInt(), 0xFF334155.toInt()),
        Style("Citrus", 0xFFFF9F1C.toInt(), 0xFFFFFBF2.toInt(), 0xFF2B1B00.toInt(),
            0xFF8A6E3C.toInt(), 0xFFFF4E00.toInt()),
        Style("Void", 0xFF111827.toInt(), 0xFFF9FAFB.toInt(), 0xFF030712.toInt(),
            0xFF6B7280.toInt(), 0xFF6366F1.toInt()),
        Style("Fern", 0xFF14532D.toInt(), 0xFFF1F8F2.toInt(), 0xFF06240F.toInt(),
            0xFF5B7C63.toInt(), 0xFF22C55E.toInt()),
        Style("Cocoa", 0xFFD8C3A5.toInt(), 0xFF231A14.toInt(), 0xFFF6EDE3.toInt(),
            0xFFA08D79.toInt(), 0xFFCB7B4B.toInt()),
        Style("Grape", 0xFF7C3AED.toInt(), 0xFFF6F2FF.toInt(), 0xFF1E1033.toInt(),
            0xFF7A6B94.toInt(), 0xFF9333EA.toInt()),
        Style("Ice", 0xFFBAE6FD.toInt(), 0xFF0B2233.toInt(), 0xFFEFF9FF.toInt(),
            0xFF7FA3B8.toInt(), 0xFF38BDF8.toInt()),
        Style("Rust", 0xFFB45309.toInt(), 0xFFFFF8EF.toInt(), 0xFF2E1A05.toInt(),
            0xFF8A7053.toInt(), 0xFFF59E0B.toInt()),
        Style("Olive", 0xFF3F6212.toInt(), 0xFFF7FAF0.toInt(), 0xFF1A2408.toInt(),
            0xFF6E7C58.toInt(), 0xFF84CC16.toInt()),
        Style("Sky", 0xFF0284C7.toInt(), 0xFFF3FAFF.toInt(), 0xFF06283A.toInt(),
            0xFF5D7E92.toInt(), 0xFF0EA5E9.toInt()),
        Style("Plum", 0xFF831843.toInt(), 0xFFFFF3F8.toInt(), 0xFF2B0715.toInt(),
            0xFF94687C.toInt(), 0xFFEC4899.toInt()),
        Style("Mint", 0xFF99F6E4.toInt(), 0xFF07231F.toInt(), 0xFFEFFFFB.toInt(),
            0xFF7FA79F.toInt(), 0xFF2DD4BF.toInt()),
        Style("Charcoal", 0xFF3F3F46.toInt(), 0xFFFAFAFA.toInt(), 0xFF18181B.toInt(),
            0xFF71717A.toInt(), 0xFFA1A1AA.toInt()),
        Style("Coral", 0xFFFDA4AF.toInt(), 0xFF2B0B12.toInt(), 0xFFFFF1F3.toInt(),
            0xFFB08D93.toInt(), 0xFFFB7185.toInt()),
        Style("Steel", 0xFF1E293B.toInt(), 0xFFEEF2F7.toInt(), 0xFF0B1220.toInt(),
            0xFF62748E.toInt(), 0xFF60A5FA.toInt()),
    )

    /** One ranked row: the name, how long it was played, and its artwork. */
    data class Entry(val name: String, val detail: String, val image: Bitmap?)

    /** Everything the cards show, resolved by the caller so rendering stays free of app lookups. */
    data class Data(
        val artistsLabel: String,
        val songsLabel: String,
        val artists: List<Entry>,
        val songs: List<Entry>,
        val totalLabel: String,
        val totalValue: String,
        val topArtistLabel: String,
        val topArtist: String,
        val hero: Bitmap?,
    )

    private val black = Typeface.create("sans-serif-black", Typeface.NORMAL)
    private val bold = Typeface.create("sans-serif", Typeface.BOLD)
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val regular = Typeface.create("sans-serif", Typeface.NORMAL)

    private fun textPaint(size: Float, color: Int, face: Typeface) = TextPaint(
        Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG
    ).apply {
        textSize = size
        this.color = color
        typeface = face
    }

    /**
     * The card at [width] x [height] px.
     *
     * The default is deliberately far larger than a phone screen: messaging apps decide whether to
     * offer their "HD"/original-quality option from the resolution handed to them, and anything
     * around screen size gets recompressed down instead. Callers render small for the on-screen
     * preview and full size only when actually sharing, so browsing colours never allocates a
     * bitmap that isn't going anywhere.
     */
    fun render(
        data: Data,
        options: Options,
        width: Int = 2160,
        height: Int = 3840,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(width / W, height / H)

        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        fill.color = options.style.page
        canvas.drawRect(0f, 0f, W, H, fill)

        val card = RectF(56f, 84f, W - 56f, H - 84f)
        fill.color = options.style.card
        canvas.drawRoundRect(card, CARD_RADIUS, CARD_RADIUS, fill)

        when (options.type) {
            CardType.SUMMARY -> drawSummary(canvas, card, data, options)
            CardType.ARTISTS ->
                drawRanked(canvas, card, data, options, data.artistsLabel, data.artists)

            CardType.SONGS ->
                drawRanked(canvas, card, data, options, data.songsLabel, data.songs)
        }
        return bitmap
    }

    // ---------------------------------------------------------------- summary

    private fun drawSummary(canvas: Canvas, card: RectF, data: Data, options: Options) {
        val style = options.style
        val left = card.left + PAD
        val right = card.right - PAD

        // Laid out from the bottom up. The blocks below the photo have a natural size, and pinning
        // them to the bottom edge lets the photo absorb whatever is left over - rather than
        // flowing everything downwards from the top and leaving a pool of dead space at the
        // bottom, which is what a fixed photo height kept producing.
        val statValueBaseline = card.bottom - 78f
        val statLabelBaseline = statValueBaseline - 104f
        val lineHeight = 62f
        val lastEntryBaseline = statLabelBaseline - 110f
        val firstEntryBaseline = lastEntryBaseline - 4 * lineHeight
        val listLabelBaseline = firstEntryBaseline - 60f

        // Square, centred, as wide as the card allows. Nothing above it - no heading strip, no
        // year down the side - so the photo is simply the top of the card.
        val panel = RectF(card.left, card.top + 56f, card.right, listLabelBaseline - 96f)
        // Capped rather than simply filling the width: at full width the photo dominated the card
        // and the numbers below it read as an afterthought.
        val size = min(min(card.width() - 2 * PAD, panel.height()), card.width() * 0.62f)
        val photoLeft = card.left + (card.width() - size) / 2f
        val top = panel.top + (panel.height() - size) / 2f
        drawImage(
            canvas, RectF(photoLeft, top, photoLeft + size, top + size),
            data.hero, data.topArtist, style, 18f
        )

        // The two ranked columns. A fixed split rather than an even one: song titles run much
        // longer than artist names, so the right column gets the extra room.
        val artistsWidth = (right - left) * 0.44f
        val songsLeft = left + artistsWidth + 28f

        val labelPaint = textPaint(29f, style.muted, medium).apply { letterSpacing = 0.06f }
        canvas.drawText(data.artistsLabel, left, listLabelBaseline, labelPaint)
        canvas.drawText(data.songsLabel, songsLeft, listLabelBaseline, labelPaint)

        val rankPaint = textPaint(32f, style.muted, regular)
        val entryPaint = textPaint(32f, style.ink, bold)
        for (i in 0 until 5) {
            val y = firstEntryBaseline + i * lineHeight
            drawColumnEntry(
                canvas, i, data.artists.getOrNull(i)?.name, left, y, artistsWidth,
                rankPaint, entryPaint
            )
            drawColumnEntry(
                canvas, i, data.songs.getOrNull(i)?.name, songsLeft, y, right - songsLeft,
                rankPaint, entryPaint
            )
        }

        // Headline figure, with the top artist beside it so the lower half isn't one lone number.
        canvas.drawText(data.totalLabel, left, statLabelBaseline, labelPaint)
        canvas.drawText(data.topArtistLabel, songsLeft, statLabelBaseline, labelPaint)
        canvas.drawText(data.totalValue, left, statValueBaseline, textPaint(88f, style.ink, black))
        val topArtistPaint = textPaint(60f, style.ink, black)
        canvas.drawText(
            ellipsize(data.topArtist, topArtistPaint, right - songsLeft),
            songsLeft, statValueBaseline, topArtistPaint
        )
    }

    // ----------------------------------------------------------- ranked cards

    /** A single rundown - just the artists, or just the songs - with artwork against each row. */
    private fun drawRanked(
        canvas: Canvas,
        card: RectF,
        data: Data,
        options: Options,
        title: String,
        entries: List<Entry>,
    ) {
        val style = options.style
        val left = card.left + PAD
        val right = card.right - PAD

        val titlePaint = textPaint(76f, style.ink, black)
        val titleBaseline = card.top + 150f
        canvas.drawText(ellipsize(title, titlePaint, right - left), left, titleBaseline, titlePaint)
        val headerBottom = titleBaseline

        // Anchored from both ends: the title takes its natural space at the top, and the rows
        // share out the whole of the rest. Fixed row heights left the fifth one crowding whatever
        // sat under it.
        val rowsTop = headerBottom + 70f
        val rowsBottom = card.bottom - 78f
        val rowHeight = min((rowsBottom - rowsTop) / 5f, 210f)
        val thumb = min(rowHeight - 44f, 148f)
        val rankPaint = textPaint(64f, style.ink, black)
        val namePaint = textPaint(42f, style.ink, bold)
        val detailPaint = textPaint(31f, style.muted, regular)
        val rankWidth = rankPaint.measureText("5") + 34f

        for (i in 0 until 5) {
            val entry = entries.getOrNull(i) ?: continue
            // Centre each row's contents on the row, so the rank, the artwork and the two lines
            // of text share one middle instead of all hanging off the row's top edge.
            val middle = rowsTop + i * rowHeight + rowHeight / 2f
            canvas.drawText("${i + 1}", left, middle + rankPaint.textSize * 0.36f, rankPaint)
            var textLeft = left + rankWidth
            val box = RectF(textLeft, middle - thumb / 2f, textLeft + thumb, middle + thumb / 2f)
            drawImage(canvas, box, entry.image, entry.name, style, 12f)
            textLeft += thumb + 32f
            val width = right - textLeft
            canvas.drawText(
                ellipsize(entry.name, namePaint, width), textLeft, middle + 4f, namePaint
            )
            canvas.drawText(
                ellipsize(entry.detail, detailPaint, width), textLeft, middle + 52f, detailPaint
            )
        }
    }

    // ------------------------------------------------------------------ parts

    private fun drawColumnEntry(
        canvas: Canvas,
        index: Int,
        text: String?,
        x: Float,
        y: Float,
        width: Float,
        rankPaint: TextPaint,
        entryPaint: TextPaint,
    ) {
        if (text == null) return
        canvas.drawText("${index + 1}", x, y, rankPaint)
        val offset = rankPaint.measureText("8") + 18f
        canvas.drawText(ellipsize(text, entryPaint, width - offset), x + offset, y, entryPaint)
    }

    private fun drawImage(
        canvas: Canvas,
        dest: RectF,
        bitmap: Bitmap?,
        fallbackText: String,
        style: Style,
        radius: Float,
    ) {
        if (bitmap != null && !bitmap.isRecycled) {
            // Centre-crop: scale by whichever axis needs the most to cover, then centre the
            // overflow, so a non-square image fills the frame instead of being squashed into it.
            val scale = max(dest.width() / bitmap.width, dest.height() / bitmap.height)
            val matrix = Matrix().apply {
                setScale(scale, scale)
                postTranslate(
                    dest.left + (dest.width() - bitmap.width * scale) / 2f,
                    dest.top + (dest.height() - bitmap.height * scale) / 2f
                )
            }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            paint.shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                .apply { setLocalMatrix(matrix) }
            canvas.drawRoundRect(dest, radius, radius, paint)
            return
        }
        // Nothing to show: the initial, large, rather than an empty grey box.
        canvas.drawRoundRect(
            dest, radius, radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = blend(style.ink, style.card, 0.10f) }
        )
        val initial = fallbackText.trim().firstOrNull()?.uppercase(Locale.getDefault()) ?: "♪"
        val paint = textPaint(dest.height() * 0.6f, blend(style.ink, style.card, 0.4f), black)
        val bounds = Rect()
        paint.getTextBounds(initial, 0, initial.length, bounds)
        canvas.drawText(
            initial,
            dest.centerX() - bounds.width() / 2f - bounds.left,
            dest.centerY() + bounds.height() / 2f,
            paint
        )
    }

    private fun blend(fg: Int, bg: Int, ratio: Float): Int {
        fun mix(shift: Int): Int {
            val f = (fg shr shift) and 0xFF
            val b = (bg shr shift) and 0xFF
            return ((b + (f - b) * ratio).toInt() and 0xFF) shl shift
        }
        return (0xFF shl 24) or mix(16) or mix(8) or mix(0)
    }

    private fun ellipsize(text: String, paint: TextPaint, width: Float) =
        TextUtils.ellipsize(text, paint, width, TextUtils.TruncateAt.END).toString()

    /**
     * Decodes [uri] down to roughly [target] px on its longest side - nothing on the card shows an
     * image larger than that, so a full-size cover would be a needless allocation on the way.
     */
    fun loadBitmap(context: Context, uri: Uri?, target: Int = 1200): Bitmap? {
        if (uri == null) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)
                .use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > target * 2) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)
                .use { BitmapFactory.decodeStream(it, null, options) }
        } catch (e: Exception) {
            Log.d(TAG, "Cannot decode $uri", e)
            null
        }
    }

    /**
     * Renders at share resolution and writes the result into the cache, returning a content:// uri
     * other apps may read. Falls back to a smaller card if the full-size bitmap won't allocate.
     */
    fun renderAndWrite(context: Context, data: Data, options: Options): Uri? {
        val bitmap = try {
            render(data, options)
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Full-size card did not fit in memory, halving", e)
            try {
                render(data, options, 1080, 1920)
            } catch (e2: OutOfMemoryError) {
                Log.e(TAG, "Cannot render share card", e2)
                return null
            }
        }
        return try {
            val dir = File(context.cacheDir, "shared_stats").apply { mkdirs() }
            // One fixed name, overwritten each time, so sharing repeatedly can't fill the cache.
            val file = File(dir, "listening-stats.png")
            // PNG rather than JPEG: the card is flat colour and crisp text, which JPEG rings badly
            // around, and which PNG happens to compress well anyway.
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            Log.e(TAG, "Failed writing share image", e)
            null
        } finally {
            bitmap.recycle()
        }
    }

    /** A chooser for [uri], with read permission granted to whichever app the user picks. */
    fun shareIntent(uri: Uri, title: String): Intent = Intent.createChooser(
        Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        },
        title
    ).apply { addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
}
