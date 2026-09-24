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
    private val scope = CoroutineScope(Dispatchers.Main)
    private var job: Job? = null
    private var rotationItems: List<PriceData> = emptyList()
    private var marketItems: List<MarketItem> = emptyList()
    private var currentIndex = 0

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
        createOverlay()
        startUpdateLoop()
        return START_STICKY
    }

    private fun createOverlay() {
        if (overlayView != null) return

        overlayView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 26f
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            setShadowLayer(4f, 0f, 0f, Color.BLACK)
            text = "---"
            setPadding(28, 4, 28, 4)
            setBackgroundColor(Color.parseColor("#CC000000"))
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // x را بعد از ساعت تنظیم می‌کنیم — برای Samsung One UI ساعت چپ است
            x = 0
            y = 0
        }

        try {
            windowManager.addView(overlayView, params)
            Log.d(TAG, "Overlay added at x=260 y=0")
        } catch (e: Exception) {
            Log.e(TAG, "addView failed", e)
            stopSelf()
        }
    }

    private fun startUpdateLoop() {
        job?.cancel()
        job = scope.launch {
            while (true) {
                try {
                    updateOverlay()
                } catch (e: Exception) {
                    Log.e(TAG, "loop err", e)
                }
                delay(INTERVAL_MS)
            }
        }
    }

    private suspend fun updateOverlay() {
        // ── ۱. تشخیص وضعیت بازار ──
        val rot = try { PriceFetcher.fetchRotation() } catch (e: Exception) { null }
        val marketIsOpen = rot?.marketOpen ?: false
        if (rot != null && rot.items.isNotEmpty()) rotationItems = rot.items

        // ── ۲. اگر بازار بسته، market بگیر ──
        val mkt: List<MarketItem> = if (!marketIsOpen) {
            try { PriceFetcher.fetchMarket() ?: emptyList() } catch (e: Exception) { emptyList() }
        } else emptyList()
        if (mkt.isNotEmpty()) marketItems = mkt

        // ── ۳. ساخت لیست بر اساس شرط ──
        val source: List<Triple<String, Double, Double?>> = if (marketIsOpen) {
            rotationItems.map { Triple(it.alias, it.price, it.changePct) }
        } else {
            marketItems.map { Triple(it.alias, it.price, it.changePct) }
        }

        if (source.isEmpty()) {
            overlayView?.apply {
                text = "---"
                setTextColor(Color.GRAY)
            }
            return
        }

        // ── ۴. انتخاب آیتم و رنگ ──
        val idx = currentIndex % source.size
        val (alias, price, pct) = source[idx]
        currentIndex++

        val digits = firstDigits(price, 3)
        val color = when {
            pct == null -> Color.rgb(230, 180, 40)
            pct >= 0 -> Color.rgb(60, 220, 120)
            else -> Color.rgb(255, 80, 80)
        }

        overlayView?.apply {
            text = digits
            setTextColor(color)
        }
        Log.d(TAG, "overlay=$digits ($idx/${source.size}) open=$marketIsOpen alias=$alias")
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
