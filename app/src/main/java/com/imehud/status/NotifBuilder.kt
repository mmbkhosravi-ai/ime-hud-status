package com.imehud.status

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

object NotifBuilder {

    // Canvas 256px, 3 digits full, stroke 8%, full width
    fun makePriceBitmap(price: Double, color: Int = Color.WHITE): Bitmap {
        val digits = firstDigits(price, 3)
        val size = 256
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.TRANSPARENT)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            style = Paint.Style.FILL_AND_STROKE
        }

        paint.textSize = size.toFloat() * 1.6f
        val maxW = size.toFloat() * 1.0f
        val w = paint.measureText(digits)
        if (w > 0f) paint.textSize *= (maxW / w)

        var fm = paint.fontMetrics
        val textHeight = fm.descent - fm.ascent
        val maxH = size.toFloat() * 1.0f
        if (textHeight > maxH) paint.textSize *= (maxH / textHeight)

        paint.strokeWidth = paint.textSize * 0.08f

        fm = paint.fontMetrics
        val centerY = size / 2f - (fm.ascent + fm.descent) / 2f
        canvas.drawText(digits, size / 2f, centerY, paint)
        return bmp
    }

    private fun firstDigits(v: Double, n: Int): String {
        if (v.isNaN() || v == 0.0) return "--"
        val s = kotlin.math.abs(v).toLong().toString()
        return if (s.length <= n) s else s.substring(0, n)
    }
}
