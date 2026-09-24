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
            textSize = 16f
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            setShadowLayer(4f, 0f, 0f, Color.BLACK)
            text = "---"
            setPadding(20, 2, 20, 2)
            setBackgroundColor(Color.parseColor("#80000000"))
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // x را بعد از ساعت تنظیم می‌کنیم — برای Samsung One UI ساعت چپ است
            x = 260
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
        val rot = try { PriceFetcher.fetchRotation() } catch (e: Exception) { null }
        if (rot != null && rot.items.isNotEmpty()) rotationItems = rot.items

        val mkt = try { PriceFetcher.fetchMarket() } catch (e: Exception) { null }
        if (mkt != null && mkt.isNotEmpty()) marketItems = mkt

        data class Item(val alias: String, val price: Double, val pct: Double?)

        val all = buildList {
            rotationItems.forEach { add(Item(it.alias, it.price, it.changePct)) }
            marketItems.forEach { add(Item(it.alias, it.price, it.changePct)) }
        }

        if (all.isEmpty()) {
            overlayView?.text = "---"
            return
        }

        val idx = currentIndex % all.size
        val cur = all[idx]
        currentIndex++

        val digits = firstDigits(cur.price, 3)
        val color = when {
            cur.pct == null -> Color.rgb(230, 180, 40)
            cur.pct >= 0 -> Color.rgb(60, 220, 120)
            else -> Color.rgb(255, 80, 80)
        }

        overlayView?.apply {
            text = digits
            setTextColor(color)
        }
        Log.d(TAG, "overlay=$digits ($idx/${all.size})")
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
