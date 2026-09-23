package com.imehud.status

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface

object NotifBuilder {

    /**
     * ساخت Bitmap از ۳ رقم اول قیمت برای small icon در استاتوس بار.
     *
     * نکته: Small icon در استاتوس بار حدود 24dp قطر دارد (72x72 px در xxhdpi).
     * برای دیده‌شدن بهتر عدد، از canvas بزرگ‌تر (144x144) استفاده می‌کنیم و
     * تمام فضا را با متن پر می‌کنیم (padding صفر).
     */
    fun makePriceBitmap(price: Double, color: Int = Color.WHITE): Bitmap {
        val digits = firstDigits(price, 3)

        // Canvas بزرگ‌تر — سیستم خودش کوچک می‌کند
        val size = 144
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.TRANSPARENT)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        // ★ محاسبه دقیق اندازه فونت که متن کامل پر کند
        // اندازه شروع بزرگتر
        paint.textSize = size.toFloat() * 1.5f

        // تنظیم عرض
        val maxW = size.toFloat() * 1.0f
        val w = paint.measureText(digits)
        if (w > 0f) {
            paint.textSize *= (maxW / w)
        }

        // تنظیم ارتفاع (اگر هنوز بلندتر از canvas است)
        val metrics = paint.fontMetrics
        val textHeight = metrics.descent - metrics.ascent
        val maxH = size.toFloat() * 1.0f
        if (textHeight > maxH) {
            paint.textSize *= (maxH / textHeight)
        }

        // رسم در مرکز دقیق
        val fm = paint.fontMetrics
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
