package org.akanework.gramophone.logic.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.style.ImageSpan
import androidx.core.graphics.withTranslation

class CenteredImageSpan(private val drawable: Drawable) : ImageSpan(drawable) {

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        val drawable = drawable
        val fontMetrics = paint.fontMetricsInt
        val transY = (y + fontMetrics.ascent + y + fontMetrics.descent) / 2 - drawable.bounds.bottom / 2
        canvas.withTranslation(x, transY.toFloat()) {
            drawable.draw(this)
        }
    }
}
