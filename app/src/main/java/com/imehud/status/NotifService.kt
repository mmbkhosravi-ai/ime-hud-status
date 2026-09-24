package com.imehud.status

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NotifService : Service() {

    companion object {
        const val TAG = "IMEHUD"
        const val CHANNEL_ID = "imehud-price"
        const val NOTIF_ID = 8888
        const val INTERVAL_MS = 5000L
        const val RETRY_MS = 10000L
    }

    private val scope = CoroutineScope(Dispatchers.Default)
    private var job: Job? = null

    // ★ state چرخش
    private var rotationItems: List<PriceData> = emptyList()
    private var currentIndex = 0
    private var lastMarketOpen = false
    private var consecutiveFailures = 0

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotif("---", "در حال اتصال...", Color.GRAY))
        startLoop()
        return START_STICKY
    }

    private fun startLoop() {
        job?.cancel()
        job = scope.launch {
            // اول یک بار لیست نمادها را بگیر
            refreshRotation()

            while (true) {
                try {
                    // ★ اگر بازار بسته است → دلار نشان بده
                    if (!lastMarketOpen) {
                        val usd = PriceFetcher.fetchUsd()
                        if (usd != null) {
                            val digits = first3(usd.price)
                            val pct = usd.changePct
                            val color = when {
                                pct == null -> Color.rgb(212, 160, 23)  // طلایی
                                pct >= 0 -> Color.rgb(34, 197, 94)      // سبز
                                else -> Color.rgb(220, 38, 38)          // قرمز
                            }
                            val content = buildUsdContent(usd)
                            showNotif(digits, content, color, usd.price)
                            Log.d(TAG, "[USD] $digits | $content")
                            consecutiveFailures = 0
                            delay(INTERVAL_MS)
                            continue
                        }
                        // اگر دلار هم نبود → پیام بسته
                        showNotif("---", "🔴 بازار بسته", Color.GRAY, 0.0)
                        delay(INTERVAL_MS)
                        continue
                    }

                    // ─── بازار باز → چرخش پورتفو ───
                    if (rotationItems.isEmpty() || currentIndex >= rotationItems.size) {
                        refreshRotation()
                        currentIndex = 0
                    }

                    if (rotationItems.isEmpty()) {
                        // ★ هیچ نمادی نداریم → دلار نشان بده
                        val usd = PriceFetcher.fetchUsd()
                        if (usd != null) {
                            val digits = first3(usd.price)
                            val pct = usd.changePct
                            val color = when {
                                pct == null -> Color.rgb(212, 160, 23)
                                pct >= 0 -> Color.rgb(34, 197, 94)
                                else -> Color.rgb(220, 38, 38)
                            }
                            showNotif(digits, buildUsdContent(usd), color, usd.price)
                            Log.d(TAG, "[USD-no-rotation] $digits")
                        } else {
                            showNotif("---", "⚠️ داده‌ای در دسترس نیست", Color.GRAY, 0.0)
                        }
                        consecutiveFailures++
                        delay(if (consecutiveFailures > 5) RETRY_MS else INTERVAL_MS)
                        continue
                    }

                    val data = rotationItems[currentIndex]
                    val digits = first3(data.price)
                    val color = if ((data.changePct ?: 0.0) >= 0)
                        Color.rgb(34, 197, 94) else Color.rgb(220, 38, 38)

                    val content = buildContent(data)
                    showNotif(digits, content, color, data.price)
                    Log.d(TAG, "[$currentIndex/${rotationItems.size}] $digits | $content")

                    consecutiveFailures = 0
                    currentIndex++

                    delay(INTERVAL_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "loop error", e)
                    consecutiveFailures++
                    delay(if (consecutiveFailures > 5) RETRY_MS else INTERVAL_MS)
                }
            }
        }
    }

    private suspend fun refreshRotation() {
        try {
            val result = PriceFetcher.fetchRotation()
            if (result != null && result.items.isNotEmpty()) {
                rotationItems = result.items
                lastMarketOpen = result.marketOpen
                Log.d(TAG, "rotation refreshed: ${rotationItems.size} items")
            } else {
                Log.w(TAG, "rotation fetch failed or empty")
            }
        } catch (e: Exception) {
            Log.e(TAG, "refresh error", e)
        }
    }

    private fun buildContent(d: PriceData): String {
        val sb = StringBuilder()
        sb.append(formatPrice(d.price))
        d.changePct?.let { sb.append("  ").append(String.format("%+.2f%%", it)) }
        d.bubble?.let { sb.append("  حباب ").append(String.format("%+.2f%%", it)) }
        sb.append("\n")
        sb.append(if (d.marketOpen) "🟢 بازار باز" else "🔴 بازار بسته")
        sb.append("  ·  ").append(d.alias)
        return sb.toString()
    }

    private fun buildUsdContent(d: UsdData): String {
        val sb = StringBuilder()
        sb.append(formatPrice(d.price))
        val pct = d.changePct
        if (pct != null) {
            sb.append("  ").append(String.format("%+.2f%%", pct))
        } else {
            sb.append("  (بدون مقایسه)")
        }
        sb.append("\n")
        sb.append("💵 دلار تهران")
        if (d.prev != null) {
            sb.append("  ·  دیروز ").append(formatPrice(d.prev))
        }
        return sb.toString()
    }

    private fun showNotif(digits: String, content: String, color: Int, price: Double) {
        val bmp = NotifBuilder.makePriceBitmap(price, color)
        val smallIcon = Icon.createWithBitmap(bmp)

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, CHANNEL_ID)
        else
            Notification.Builder(this)

        val notif = builder
            .setSmallIcon(smallIcon)
            .setContentTitle("IME-HUD")
            .setContentText(content)
            .setStyle(Notification.BigTextStyle().bigText(content))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, notif)
    }

    private fun buildNotif(digits: String, content: String, color: Int): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, CHANNEL_ID)
        else
            Notification.Builder(this)

        return builder
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("IME-HUD")
            .setContentText(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(
                CHANNEL_ID,
                "IME-HUD قیمت",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "نمایش قیمت صندوق عیار در نوار وضعیت"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            nm.createNotificationChannel(ch)
        }
    }

    private fun first3(v: Double): String {
        if (v.isNaN() || v == 0.0) return "---"
        val s = kotlin.math.abs(v).toLong().toString()
        return if (s.length <= 3) s else s.substring(0, 3)
    }

    private fun formatPrice(v: Double): String {
        return if (v >= 1000) String.format("%,.0f", v) else String.format("%.2f", v)
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
