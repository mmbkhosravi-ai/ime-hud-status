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
    private var rotationItems: List<PriceData> = emptyList()
    private var marketItems: List<MarketItem> = emptyList()
    private var currentIndex = 0
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
                try {
                    val s = OverlayPrefs.load(this@OverlayService)
                    if (s != lastSettings) {
                        applySettings(s)
                        lastSettings = s
                    }
                    updateOverlay(s)
                } catch (e: Exception) {
                    Log.e(TAG, "loop err", e)
                }
                delay(INTERVAL_MS)
            }
        }
    }

    private suspend fun updateOverlay(s: OverlaySettings) {
        val rot = try { PriceFetcher.fetchRotation() } catch (e: Exception) { null }
        val marketIsOpen = rot?.marketOpen ?: false
        if (rot != null && rot.items.isNotEmpty()) rotationItems = rot.items

        val mkt: List<MarketItem> = if (!marketIsOpen) {
            try { PriceFetcher.fetchMarket() ?: emptyList() } catch (e: Exception) { emptyList() }
        } else emptyList()
        if (mkt.isNotEmpty()) marketItems = mkt

        data class Item(val alias: String, val key: String, val price: Double, val pct: Double?)

        // ★ اولویت: نمادهای پورتفو → بعد market
        val source: List<Item> = if (rotationItems.isNotEmpty() && marketIsOpen) {
            // بازار باز → فقط پورتفو
            rotationItems.map { Item(it.alias, "", it.price, it.changePct) }
        } else if (marketItems.isNotEmpty()) {
            // بازار بسته یا پورتفو خالی → market
            marketItems.map { Item(it.alias, it.key, it.price, it.changePct) }
        } else {
            // fallback: پورتفو (اگر market هم خالی بود)
            rotationItems.map { Item(it.alias, "", it.price, it.changePct) }
        }

        if (source.isEmpty()) {
            overlayView?.apply {
                text = "---"
                setTextColor(Color.GRAY)
            }
            return
        }

        val idx = currentIndex % source.size
        val cur = source[idx]
        currentIndex++

        val digits = firstDigits(cur.price, 3)
        val prefix = if (s.showPrefix) getPrefix(cur.alias, cur.key) else ""
        val display = if (prefix.isNotEmpty()) "$prefix $digits" else digits

        val color = when {
            cur.pct == null -> Color.rgb(230, 180, 40)
            cur.pct >= 0 -> Color.rgb(60, 220, 120)
            else -> Color.rgb(255, 80, 80)
        }

        overlayView?.apply {
            text = display
            setTextColor(color)
        }
        Log.d(TAG, "overlay=$display ($idx/${source.size}) open=$marketIsOpen")
    }

    // ★ حروف اختصاصی
    private fun getPrefix(alias: String, key: String): String {
        return when {
            key == "usd" -> "D"
            key == "coin" -> "C"
            key == "gold" -> "G"
            alias.contains("عیار") -> "A"
            alias.contains("دلار") -> "D"
            alias.contains("سکه") -> "C"
            alias.contains("طلا") -> "G"
            alias.contains("انس") -> "O"
            else -> ""
        }
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
