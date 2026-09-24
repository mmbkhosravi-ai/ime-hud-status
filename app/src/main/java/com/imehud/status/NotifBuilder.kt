package com.imehud.status

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface

object NotifBuilder {

    /**
     * ساخت Bitmap از ۳ رقم اول قیمت (+ prefix اختیاری).
     */
    fun makePriceBitmap(
        price: Double,
        color: Int = Color.WHITE,
        prefix: String = ""
    ): Bitmap {
        val digits = firstDigits(price, 3)
        val text = if (prefix.isNotEmpty()) "$prefix $digits" else digits

        val size = 256
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.TRANSPARENT)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            textAlign = Paint.Align.LEFT
            isFakeBoldText = true
            style = Paint.Style.FILL_AND_STROKE
        }

        val bounds = Rect()
        paint.textSize = 100f
        paint.getTextBounds(text, 0, text.length, bounds)

        val targetW = size.toFloat() * 1.0f
        val targetH = size.toFloat() * 1.0f

        val scaleW = targetW / bounds.width().toFloat()
        val scaleH = targetH / bounds.height().toFloat()
        val scale = minOf(scaleW, scaleH)

        paint.textSize = 100f * scale
        paint.strokeWidth = paint.textSize * 0.07f

        val finalBounds = Rect()
        paint.getTextBounds(text, 0, text.length, finalBounds)

        val drawX = (size - finalBounds.width()) / 2f - finalBounds.left
        val drawY = (size - finalBounds.height()) / 2f - finalBounds.top

        canvas.drawText(text, drawX, drawY, paint)

        return bmp
    }

    private fun firstDigits(v: Double, n: Int): String {
        if (v.isNaN() || v == 0.0) return "--"
        val s = kotlin.math.abs(v).toLong().toString()
        return if (s.length <= n) s else s.substring(0, n)
    }
}
