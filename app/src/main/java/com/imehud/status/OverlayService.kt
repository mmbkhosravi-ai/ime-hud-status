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

        val digits = firstDigits(cur.price, 3)
        val prefix = if (s.showPrefix) cur.key else ""
        val baseText = if (prefix.isNotEmpty()) "$prefix $digits" else digits

        val color = when {
            cur.changePct == null -> Color.rgb(230, 180, 40)
            cur.changePct >= 0 -> Color.rgb(60, 220, 120)
            else -> Color.rgb(255, 80, 80)
        }

        // ★ ساخت SpannableString: عدد با رنگ اصلی، B + درصد با فونت کوچک‌تر و رنگ طلایی
        val full = if (cur.bubble != null) {
            String.format("%s  B %+.2f%%", baseText, cur.bubble)
        } else {
            baseText
        }

        val sp = android.text.SpannableString(full)
        // رنگ پایه (کل متن) → رنگ عدد
        sp.setSpan(
            android.text.style.ForegroundColorSpan(color),
            0, baseText.length,
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        // بخش B + درصد → رنگ طلایی
        if (cur.bubble != null) {
            val bubbleStart = baseText.length
            val bubbleEnd = full.length
            sp.setSpan(
                android.text.style.ForegroundColorSpan(Color.rgb(230, 180, 40)),
                bubbleStart, bubbleEnd,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            // فونت کوچک‌تر (۷۵٪)
            sp.setSpan(
                android.text.style.RelativeSizeSpan(0.75f),
                bubbleStart, bubbleEnd,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        overlayView?.apply {
            text = sp
        }
        Log.d(TAG, "overlay=$full ($idx/${symbols.size})")
    }

    private fun firstDigits(v: Double, n: Int): String {
        if (v.isNaN() || v == 0.0) return "---"
        val s = kotlin.math.abs(v).toLong().toString()
        return if (s.length <= n) s else s.substring(0, n)
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
