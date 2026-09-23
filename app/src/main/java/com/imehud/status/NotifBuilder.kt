package com.imehud.status

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

object NotifBuilder {

    /**
     * ساخت Bitmap از ۳ رقم اول قیمت — بسیار درشت و پر.
     *
     * تکنیک‌ها برای حداکثر خوانایی:
     * - Canvas بزرگ 192x192 (سیستم خودش به اندازه small icon کوچک می‌کند)
     * - متن را ۱۰۰٪ عرض پر می‌کنیم (بدون padding)
     * - Fake Bold + Stroke برای ضخامت
     * - انتخاب تک‌رقمی به جای ۳ رقم در صورت نیاز برای درشتی بیشتر
     */
    fun makePriceBitmap(price: Double, color: Int = Color.WHITE): Bitmap {
        val digits = firstDigits(price, 3)

        val size = 192
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

        // ★ شروع با اندازه بزرگ
        paint.textSize = size.toFloat() * 1.8f

        // تنظیم عرض — متن کامل پر کند (۹۵٪ عرض)
        val maxW = size.toFloat() * 0.95f
        val w = paint.measureText(digits)
        if (w > 0f) {
            paint.textSize *= (maxW / w)
        }

        // تنظیم ارتفاع — با احتساب stroke
        var fm = paint.fontMetrics
        val textHeight = fm.descent - fm.ascent
        val maxH = size.toFloat() * 0.95f
        if (textHeight > maxH) {
            paint.textSize *= (maxH / textHeight)
        }

        // ★ ضخامت Stroke ~ 8% اندازه فونت
        paint.strokeWidth = paint.textSize * 0.08f

        // رسم در مرکز دقیق
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
