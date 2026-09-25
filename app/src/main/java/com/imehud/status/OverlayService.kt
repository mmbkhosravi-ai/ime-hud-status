package com.imehud.status

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class OverlayService : Service() {

    companion object {
        const val TAG = "IMEHUD-Overlay"
        const val INTERVAL_MS = 5000L
        var isRunning = false
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: TextView? = null
    private var currentIndex = 0  // index مشترک برای چرخش روی symbols
    private var lastSettings: OverlaySettings? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            Log.e(TAG, "SYSTEM_ALERT_WINDOW not granted")
            stopSelf()
            return START_NOT_STICKY
        }
        val s = OverlayPrefs.load(this)
        lastSettings = s
        if (overlayView == null) createOverlay(s)
        startUpdateLoop()
        return START_STICKY
    }

    private fun buildParams(s: OverlaySettings): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED

        val gravity = when (s.position) {
            "left" -> Gravity.TOP or Gravity.START
            "right" -> Gravity.TOP or Gravity.END
            else -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }

        val density = resources.displayMetrics.density
        val offsetPx = (s.offsetX * density).toInt()

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type, flags, PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity
            this.x = offsetPx
            this.y = 0
        }
    }

    private fun createOverlay(s: OverlaySettings) {
        overlayView = TextView(this).apply {
            textSize = s.textSizeSp
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            setShadowLayer(6f, 0f, 0f, Color.BLACK)
            setTextColor(Color.WHITE)
            text = "..."
            setPadding(24, 2, 24, 2)
            if (s.showBackground) {
                setBackgroundColor(Color.parseColor("#AA000000"))
            } else {
                setBackgroundColor(Color.TRANSPARENT)
            }
        }

        try {
            windowManager.addView(overlayView, buildParams(s))
            Log.d(TAG, "overlay added: pos=${s.position} size=${s.textSizeSp}")
        } catch (e: Exception) {
            Log.e(TAG, "addView failed", e)
            stopSelf()
        }
    }

    private fun applySettings(s: OverlaySettings) {
        val v = overlayView ?: return
        v.textSize = s.textSizeSp
        if (s.showBackground) {
            v.setBackgroundColor(Color.parseColor("#AA000000"))
        } else {
            v.setBackgroundColor(Color.TRANSPARENT)
        }
        try {
            windowManager.updateViewLayout(v, buildParams(s))
        } catch (e: Exception) {
            Log.e(TAG, "updateViewLayout err", e)
        }
    }

    private fun startUpdateLoop() {
        job?.cancel()
        job = scope.launch {
            while (true) {
                var waitMs = INTERVAL_MS
                try {
                    val s = OverlayPrefs.load(this@OverlayService)
                    if (s != lastSettings) {
                        applySettings(s)
                        lastSettings = s
                    }
                    updateOverlay(s)
                    // ★ از تنظیمات کاربر (2..60 ثانیه)
                    waitMs = s.rotateSeconds.coerceIn(2, 60) * 1000L
                } catch (e: Exception) {
                    Log.e(TAG, "loop err", e)
                }
                delay(waitMs)
            }
        }
    }

    private suspend fun updateOverlay(s: OverlaySettings) {
        // ★ از منبع واحد: /api/notif/symbols
        val symbols = try { PriceFetcher.fetchNotifSymbols() } catch (e: Exception) { null }

        if (symbols.isNullOrEmpty()) {
            overlayView?.apply {
                text = "---"
                setTextColor(Color.GRAY)
            }
            return
        }

        val idx = currentIndex % symbols.size
        val cur = symbols[idx]
        currentIndex++

        // ★ Overlay: قیمت کامل با کاما (مثل 240,010,000)
        val digits = String.format("%,d", cur.price.toLong())
        val prefix = if (s.showPrefix) cur.key else ""
        val baseText = if (prefix.isNotEmpty()) "$prefix $digits" else digits

        val color = when {
            cur.changePct == null -> Color.rgb(230, 180, 40)
            cur.changePct >= 0 -> Color.rgb(60, 220, 120)
            else -> Color.rgb(255, 80, 80)
        }

        // ★ متن: base + B + R
        var full = baseText
        val sp = android.text.SpannableString(full)

        // رنگ عدد اصلی
        sp.setSpan(
            android.text.style.ForegroundColorSpan(color),
            0, baseText.length,
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        // ★ B برای حباب
        if (cur.bubble != null) {
            val bText = String.format("  B %+.2f%%", cur.bubble)
            val start = sp.length
            val sp2 = android.text.SpannableString(full + bText)
            sp2.setSpan(
                android.text.style.ForegroundColorSpan(color),
                0, baseText.length,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            sp2.setSpan(
                android.text.style.ForegroundColorSpan(Color.rgb(230, 180, 40)),
                start, sp2.length,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            sp2.setSpan(
                android.text.style.RelativeSizeSpan(0.75f),
                start, sp2.length,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            full = sp2.toString()
            overlayView?.text = sp2
        }

        // ★ R (نسبت به مبنای کاربر)
        if (cur.rPct != null) {
            val rText = String.format("  R %+.2f%%", cur.rPct)
            val current = overlayView?.text ?: baseText
            val spR = android.text.SpannableString(current.toString() + rText)
            val oldLen = current.length
            // رنگ پیش‌فرض قبلی (نیاز نیست رنگ‌ها را تکرار کنیم، صرفاً اضافه)
            spR.setSpan(
                android.text.style.ForegroundColorSpan(color),
                0, baseText.length,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            if (cur.bubble != null) {
                val bubbleStart = baseText.length
                val bubbleEnd = oldLen
                spR.setSpan(
                    android.text.style.ForegroundColorSpan(Color.rgb(230, 180, 40)),
                    bubbleStart, bubbleEnd,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spR.setSpan(
                    android.text.style.RelativeSizeSpan(0.75f),
                    bubbleStart, bubbleEnd,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            // رنگ R
            val rColor = if (cur.rPct >= 0) Color.rgb(60, 220, 120) else Color.rgb(255, 80, 80)
            spR.setSpan(
                android.text.style.ForegroundColorSpan(rColor),
                oldLen, spR.length,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spR.setSpan(
                android.text.style.RelativeSizeSpan(0.75f),
                oldLen, spR.length,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            overlayView?.text = spR
        } else {
            overlayView?.text = if (cur.bubble != null) overlayView?.text else sp
        }

        Log.d(TAG, "overlay=$full R=${cur.rPct} ($idx/${symbols.size})")
    }

    override fun onDestroy() {
        isRunning = false
        job?.cancel()
        overlayView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) {}
        }
        overlayView = null
        super.onDestroy()
    }
}
