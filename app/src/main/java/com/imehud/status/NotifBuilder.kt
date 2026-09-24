package com.imehud.status

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface

object NotifBuilder {

    /**
     * ساخت Bitmap از ۳ رقم اول قیمت — حداکثر بزرگ و خوانا.
     *
     * بهینه‌سازی‌های نهایی:
     * - Canvas 256px
     * - getTextBounds (به‌جای fontMetrics) → ۱۵-۲۵٪ بزرگ‌تر
     * - ۳ رقم کامل
     * - Stroke ۷٪ (تعادل ضخامت و خوانایی)
     */
    fun makePriceBitmap(price: Double, color: Int = Color.WHITE): Bitmap {
        val digits = firstDigits(price, 3)
        val size = 256

        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.TRANSPARENT)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.LEFT  // ★ LEFT برای محاسبه دقیق
            isFakeBoldText = true
            style = Paint.Style.FILL_AND_STROKE
        }

        // ─── محاسبه با getTextBounds ───
        val bounds = Rect()
        paint.textSize = 100f
        paint.getTextBounds(digits, 0, digits.length, bounds)

        // اندازه‌ی مورد نیاز برای پر کردن ۱۰۰٪
        val targetW = size.toFloat() * 1.0f
        val targetH = size.toFloat() * 1.0f

        val scaleW = targetW / bounds.width().toFloat()
        val scaleH = targetH / bounds.height().toFloat()
        val scale = minOf(scaleW, scaleH)

        paint.textSize = 100f * scale

        // Stroke متناسب
        paint.strokeWidth = paint.textSize * 0.07f

        // محاسبه مجدد bounds با اندازه نهایی (با احتساب stroke)
        val finalBounds = Rect()
        paint.getTextBounds(digits, 0, digits.length, finalBounds)

        // محاسبه موقعیت برای مرکز دقیق
        val drawX = (size - finalBounds.width()) / 2f - finalBounds.left
        val drawY = (size - finalBounds.height()) / 2f - finalBounds.top

        canvas.drawText(digits, drawX, drawY, paint)

        return bmp
    }

    private fun firstDigits(v: Double, n: Int): String {
        if (v.isNaN() || v == 0.0) return "--"
        val s = kotlin.math.abs(v).toLong().toString()
        return if (s.length <= n) s else s.substring(0, n)
    }
}
