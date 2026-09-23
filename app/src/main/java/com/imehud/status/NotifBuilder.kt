package com.imehud.status

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

object NotifBuilder {

    /**
     * ساخت Bitmap از ۳ رقم اول قیمت برای small icon در استاتوس بار.
     * اندازه توصیه‌شده Android: 24dp (72x72 px در xxhdpi)
     */
    fun makePriceBitmap(price: Double, color: Int = Color.WHITE): Bitmap {
        val digits = firstDigits(price, 3)

        val size = 96 // px
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.TRANSPARENT)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = if (digits.length >= 3) size * 0.58f else size * 0.70f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        // اندازه‌ی متن را تنظیم کن تا در بوم جا شود
        val maxW = size * 0.98f
        var w = paint.measureText(digits)
        if (w > maxW) {
            paint.textSize *= (maxW / w)
        }

        val metrics = paint.fontMetrics
        val centerY = size / 2f - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(digits, size / 2f, centerY, paint)

        return bmp
    }

    private fun firstDigits(v: Double, n: Int): String {
        if (v.isNaN() || v == 0.0) return "--"
        val s = kotlin.math.abs(v).toLong().toString()
        return if (s.length <= n) s else s.substring(0, n)
    }
}
