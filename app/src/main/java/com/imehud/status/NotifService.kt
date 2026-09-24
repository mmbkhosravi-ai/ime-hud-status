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
        const val DEFAULT_INTERVAL_MS = 5000L
        const val RETRY_MS = 10000L
    }

    private val scope = CoroutineScope(Dispatchers.Default)
    private var job: Job? = null
    private var portfolioIndex = 0
    private var marketIndex = 0
    private var consecutiveFailures = 0

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotif("در حال اتصال...", Color.GRAY))
        startLoop()
        return START_STICKY
    }

    private fun startLoop() {
        job?.cancel()
        job = scope.launch {
            while (true) {
                var waitMs = DEFAULT_INTERVAL_MS
                try {
                    waitMs = updateNotif()
                    consecutiveFailures = 0
                } catch (e: Exception) {
                    Log.e(TAG, "loop error", e)
                    consecutiveFailures++
                    waitMs = if (consecutiveFailures > 5) RETRY_MS else DEFAULT_INTERVAL_MS
                }
                delay(waitMs)
            }
        }
    }

    private suspend fun updateNotif(): Long {
        // ── ۱. وضعیت بازار (endpoint سبک) ──
        val marketOpen = try { PriceFetcher.fetchMarketStatus() } catch (e: Exception) { false }

        // ── ۲. نمادهای کاربر (از Web UI) ──
        val symbols = try { PriceFetcher.fetchNotifSymbols() } catch (e: Exception) { null } ?: emptyList()

        // ── ۳. اگر بازار بسته: دلار/طلا/سکه ──
        val marketList: List<MarketItem> = if (!marketOpen) {
            try { PriceFetcher.fetchMarket() ?: emptyList() } catch (e: Exception) { emptyList() }
        } else emptyList()

        // ── ۴. ساخت متن نوتیف ──
        val sb = StringBuilder()

        if (marketOpen && symbols.isNotEmpty()) {
            // بازار باز → نمادهای کاربر
            for (s in symbols) {
                sb.append("📊 ").append(s.key).append(" ").append(s.alias).append(": ")
                sb.append(formatPrice(s.price))
                s.changePct?.let { sb.append("  ").append(String.format("%+.2f%%", it)) }
                s.bubble?.let { sb.append("  حباب ").append(String.format("%+.2f%%", it)) }
                sb.append("\n")
            }
        } else if (!marketOpen && marketList.isNotEmpty()) {
            // بازار بسته → دلار/طلا/سکه
            for (m in marketList) {
                sb.append("💵 ").append(m.key).append(" ").append(m.alias).append(": ")
                sb.append(formatPrice(m.price))
                m.changePct?.let { sb.append("  ").append(String.format("%+.2f%%", it)) }
                sb.append("\n")
            }
        } else {
            sb.append(if (marketOpen) "⚠️ نمادی در لیست نیست" else "🔴 بازار بسته")
            sb.append("\n")
        }

        // ── ۵. زمان + وضعیت ──
        val now = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date())
        val statusLine = if (marketOpen) "🟢 بازار باز" else "🔴 بازار بسته"
        sb.append("━━━━━━━━━━━━━━━━━━\n")
        sb.append("⏰ ").append(now).append("  ·  ").append(statusLine)

        val fullText = sb.toString().trimEnd()

        // ── ۶. انتخاب آیکون کوچک (چرخش) — از تنظیمات کاربر ──
        val settings = OverlayPrefs.load(this)
        val usePrefix = settings.showPrefix

        var digits = "---"
        var color = Color.GRAY
        var iconPrice = 0.0
        var iconPrefix = ""

        if (marketOpen && symbols.isNotEmpty()) {
            val s = symbols[portfolioIndex % symbols.size]
            portfolioIndex++
            digits = first3(s.price)
            color = colorFor(s.changePct)
            iconPrice = s.price
            iconPrefix = if (usePrefix) s.key else ""
        } else if (!marketOpen && marketList.isNotEmpty()) {
            val m = marketList[marketIndex % marketList.size]
            marketIndex++
            digits = first3(m.price)
            color = colorFor(m.changePct)
            iconPrice = m.price
            iconPrefix = if (usePrefix) m.key else ""
        }

        // ── ۷. نمایش ──
        showNotif(fullText, color, iconPrice, iconPrefix)
        Log.d(TAG, "[notif] icon=$digits prefix=$iconPrefix, open=$marketOpen, sym=${symbols.size}, mkt=${marketList.size}")

        // ── ۸. زمان چرخش از تنظیمات کاربر ──
        val sec = settings.rotateSeconds.coerceIn(2, 60)
        return sec * 1000L
    }

    private fun colorFor(pct: Double?): Int = when {
        pct == null -> Color.rgb(212, 160, 23)
        pct >= 0 -> Color.rgb(34, 197, 94)
        else -> Color.rgb(220, 38, 38)
    }

    private fun showNotif(content: String, color: Int, price: Double, prefix: String = "") {
        val bmp = NotifBuilder.makePriceBitmap(price, color, prefix)
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

    private fun buildNotif(content: String, color: Int): Notification {
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
                description = "نمایش قیمت نمادها در نوار وضعیت"
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
