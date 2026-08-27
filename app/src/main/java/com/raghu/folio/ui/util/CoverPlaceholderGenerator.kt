package com.raghu.folio.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import kotlin.math.abs

/** Generates an Apple-Books-style solid-color placeholder cover (title initials on a color tile)
 *  for books that have no embedded/sidecar cover image. */
object CoverPlaceholderGenerator {

    private val PALETTE = intArrayOf(
        0xFF5C6BC0.toInt(), 0xFFEF5350.toInt(), 0xFF26A69A.toInt(), 0xFFFFA726.toInt(),
        0xFF8D6E63.toInt(), 0xFF7E57C2.toInt(), 0xFF29B6F6.toInt(), 0xFF66BB6A.toInt(),
        0xFFEC407A.toInt(), 0xFF78909C.toInt(),
    )

    fun generate(context: Context, title: String, sizePx: Int): Drawable {
        val color = PALETTE[abs(title.hashCode()) % PALETTE.size]
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(color)

        val initials = initialsOf(title)
        if (initials.isNotEmpty()) {
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = Color.WHITE
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
                textSize = sizePx * 0.36f
            }
            val y = sizePx / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(initials, sizePx / 2f, y, textPaint)
        }
        return BitmapDrawable(context.resources, bitmap)
    }

    private fun initialsOf(title: String): String {
        val words = title.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return words.take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
    }
}
